/*
 * Copyright © 2019-2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util;

import android.content.Context;
import android.system.OsConstants;
import android.util.Base64;

import com.wireguard.android.util.RootShell.RootShellException;
import com.wireguard.crypto.Ed25519;
import com.wireguard.util.NonNullForAll;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;

/**
 * Class that implements the logic for downloading and loading signed, prebuilt modules for
 * WireGuard into the running kernel.
 */
@NonNullForAll
@SuppressWarnings("MagicNumber")
public class ModuleLoader {
    private static final String MODULE_LIST_URL = "https://download.wireguard.com/android-module/modules.txt.sig";
    private static final String MODULE_NAME = "wireguard-%s.ko";
    private static final String MODULE_PUBLIC_KEY_BASE64 = "RWRmHuT9PSqtwfsLtEx+QS06BJtLgFYteL9WCNjH7yuyu5Y1DieSN7If";
    private static final String MODULE_URL = "https://download.wireguard.com/android-module/%s";
    private final File moduleDir;
    private final RootShell rootShell;
    private final File tmpDir;
    private final String userAgent;

    /**
     * Public constructor for ModuleLoader
     *
     * @param context   A {@link Context} instance.
     * @param rootShell A {@link RootShell} instance used to run elevated commands required for module
     *                  loading.
     * @param userAgent A {@link String} that represents the User-Agent string used for connections
     *                  to the upstream server.
     */
    public ModuleLoader(final Context context, final RootShell rootShell, final String userAgent) {
        moduleDir = new File(context.getCacheDir(), "kmod");
        tmpDir = new File(context.getCacheDir(), "tmp");
        this.rootShell = rootShell;
        this.userAgent = userAgent;
    }

    /**
     * Check whether a WireGuard module is already loaded into the kernel.
     *
     * @return boolean indicating if WireGuard is already enabled in the kernel.
     */
    public static boolean isModuleLoaded() {
        return new File("/sys/module/wireguard").exists();
    }

    /**
     * Download the correct WireGuard module for the device
     *
     * @return {@link OsConstants}.EXIT_SUCCESS if everything succeeds, ENOENT otherwise.
     * @throws IOException              if the remote hash list was not found or empty.
     * @throws RootShellException       if {@link RootShell} has a failure executing elevated commands.
     * @throws NoSuchAlgorithmException if SHA256 algorithm is not available in device JDK.
     */
    public Integer download() throws IOException, RootShellException, NoSuchAlgorithmException {
        final List<String> output = new ArrayList<>();
        rootShell.run(output, "sha256sum /proc/version|cut -d ' ' -f 1");
        if (output.size() != 1 || output.get(0).length() != 64)
            throw new InvalidParameterException("Invalid sha256 of /proc/version");
        final String moduleName = String.format(MODULE_NAME, output.get(0));
        HttpURLConnection connection = (HttpURLConnection) new URL(MODULE_LIST_URL).openConnection();
        connection.setRequestProperty("User-Agent", userAgent);
        connection.connect();
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
            throw new IOException("Hash list could not be found");
        final byte[] input = new byte[1024 * 1024 * 3 /* 3MiB */];
        int len;
        try (final InputStream inputStream = connection.getInputStream()) {
            len = inputStream.read(input);
        }
        if (len <= 0)
            throw new IOException("Hash list was empty");
        final Map<String, Sha256Digest> modules = verifySignedHashes(new String(input, 0, len, StandardCharsets.UTF_8));
        if (modules == null)
            throw new InvalidParameterException("The signature did not verify or invalid hash list format");
        if (!modules.containsKey(moduleName))
            return OsConstants.ENOENT;
        connection = (HttpURLConnection) new URL(String.format(MODULE_URL, moduleName)).openConnection();
        connection.setRequestProperty("User-Agent", userAgent);
        connection.connect();
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
            throw new IOException("Module file could not be found, despite being on hash list");

        tmpDir.mkdirs();
        moduleDir.mkdir();
        File tempFile = null;
        try {
            tempFile = File.createTempFile("UNVERIFIED-", null, tmpDir);
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (final InputStream inputStream = connection.getInputStream();
                 final FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                int total = 0;
                while ((len = inputStream.read(input)) > 0) {
                    total += len;
                    if (total > 1024 * 1024 * 15 /* 15 MiB */)
                        throw new IOException("File too big");
                    outputStream.write(input, 0, len);
                    digest.update(input, 0, len);
                }
                outputStream.getFD().sync();
            }
            if (!Arrays.equals(digest.digest(), modules.get(moduleName).bytes))
                throw new IOException("Incorrect file hash");

            if (!tempFile.renameTo(new File(moduleDir, moduleName)))
                throw new IOException("Unable to rename to final destination");
        } finally {
            if (tempFile != null)
                tempFile.delete();
        }
        return OsConstants.EXIT_SUCCESS;
    }

    /**
     * Load the downloaded module. ModuleLoader#download must be called before this.
     *
     * @throws IOException        if {@link RootShell} has a failure executing elevated commands.
     * @throws RootShellException if {@link RootShell} has a failure executing elevated commands.
     */
    public void loadModule() throws IOException, RootShellException {
        rootShell.run(null, String.format("insmod \"%s/wireguard-$(sha256sum /proc/version|cut -d ' ' -f 1).ko\"", moduleDir.getAbsolutePath()));
    }

    /**
     * Check if the module might already exist in the app's data.
     *
     * @return boolean indicating whether downloadable module might exist already.
     */
    public boolean moduleMightExist() {
        return moduleDir.exists() && moduleDir.isDirectory();
    }

    @Nullable
    private Map<String, Sha256Digest> verifySignedHashes(final String signifyDigest) {
        byte[] publicKeyBytes = Base64.decode(MODULE_PUBLIC_KEY_BASE64, Base64.DEFAULT);

        if (publicKeyBytes == null || publicKeyBytes.length != 32 + 10 || publicKeyBytes[0] != 'E' || publicKeyBytes[1] != 'd')
            return null;

        final String[] lines = signifyDigest.split("\n", 3);
        if (lines.length != 3)
            return null;
        if (!lines[0].startsWith("untrusted comment: "))
            return null;

        byte[] signatureBytes = Base64.decode(lines[1], Base64.DEFAULT);
        if (signatureBytes == null || signatureBytes.length != 64 + 10)
            return null;
        for (int i = 0; i < 10; ++i) {
            if (signatureBytes[i] != publicKeyBytes[i])
                return null;
        }
        publicKeyBytes = Arrays.copyOfRange(publicKeyBytes, 10, 10 + 32);
        signatureBytes = Arrays.copyOfRange(signatureBytes, 10, 10 + 64);
        if (!Ed25519.verify(lines[2].getBytes(StandardCharsets.UTF_8), signatureBytes, publicKeyBytes))
            return null;

        final Map<String, Sha256Digest> hashes = new HashMap<>();
        for (final String line : lines[2].split("\n")) {
            final String[] components = line.split(" {2}", 2);
            if (components.length != 2)
                return null;
            try {
                hashes.put(components[1], new Sha256Digest(components[0]));
            } catch (final Exception ignored) {
                return null;
            }
        }
        return hashes;
    }

    private static final class Sha256Digest {
        private final byte[] bytes;

        private Sha256Digest(final String hex) {
            if (hex.length() != 64)
                throw new InvalidParameterException("SHA256 hashes must be 32 bytes long");
            bytes = new byte[32];
            for (int i = 0; i < 32; ++i)
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
    }
}
