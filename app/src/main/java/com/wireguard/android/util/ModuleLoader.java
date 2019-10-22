/*
 * Copyright © 2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util;

import android.content.Context;
import android.system.OsConstants;
import android.util.Base64;

import com.wireguard.android.Application;
import com.wireguard.android.util.RootShell.NoRootException;

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec;

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
import java.security.Signature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class ModuleLoader {
    private static final String MODULE_PUBLIC_KEY_BASE64 = "RWRmHuT9PSqtwfsLtEx+QS06BJtLgFYteL9WCNjH7yuyu5Y1DieSN7If";
    private static final String MODULE_LIST_URL = "https://download.wireguard.com/android-module/modules.txt.sig";
    private static final String MODULE_URL = "https://download.wireguard.com/android-module/%s";
    private static final String MODULE_NAME = "wireguard-%s.ko";

    private final File moduleDir;
    private final File tmpDir;

    public ModuleLoader(final Context context) {
        moduleDir = new File(context.getCacheDir(), "kmod");
        tmpDir = new File(context.getCacheDir(), "tmp");
    }

    public boolean moduleMightExist() {
        return moduleDir.exists() && moduleDir.isDirectory();
    }

    public void loadModule() throws IOException, NoRootException {
        Application.getRootShell().run(null, String.format("insmod \"%s/wireguard-$(sha256sum /proc/version|cut -d ' ' -f 1).ko\"", moduleDir.getAbsolutePath()));
    }

    public boolean isModuleLoaded() {
        return new File("/sys/module/wireguard").exists();
    }

    private static final class Sha256Digest {
        private byte[] bytes;
        private Sha256Digest(final String hex) {
            if (hex.length() != 64)
                throw new InvalidParameterException("SHA256 hashes must be 32 bytes long");
            bytes = new byte[32];
            for (int i = 0; i < 32; ++i)
                bytes[i] = (byte)Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
    }

    @Nullable
    private Map<String, Sha256Digest> verifySignedHashes(final String signifyDigest) {
        final byte[] publicKeyBytes = Base64.decode(MODULE_PUBLIC_KEY_BASE64, Base64.DEFAULT);

        if (publicKeyBytes == null || publicKeyBytes.length != 32 + 10 || publicKeyBytes[0] != 'E' || publicKeyBytes[1] != 'd')
            return null;

        final String[] lines = signifyDigest.split("\n", 3);
        if (lines.length != 3)
            return null;
        if (!lines[0].startsWith("untrusted comment: "))
            return null;

        final byte[] signatureBytes = Base64.decode(lines[1], Base64.DEFAULT);
        if (signatureBytes == null || signatureBytes.length != 64 + 10)
            return null;
        for (int i = 0; i < 10; ++i) {
            if (signatureBytes[i] != publicKeyBytes[i])
                return null;
        }

        try {
            EdDSAParameterSpec parameterSpec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
            Signature signature = new EdDSAEngine(MessageDigest.getInstance(parameterSpec.getHashAlgorithm()));
            byte[] rawPublicKeyBytes = new byte[32];
            System.arraycopy(publicKeyBytes, 10, rawPublicKeyBytes, 0, 32);
            signature.initVerify(new EdDSAPublicKey(new EdDSAPublicKeySpec(rawPublicKeyBytes, parameterSpec)));
            signature.update(lines[2].getBytes(StandardCharsets.UTF_8));
            if (!signature.verify(signatureBytes, 10, 64))
                return null;
        } catch (final Exception ignored) {
            return null;
        }

        Map<String, Sha256Digest> hashes = new HashMap<>();
        for (final String line : lines[2].split("\n")) {
            final String[] components = line.split("  ", 2);
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

    public Integer download() throws IOException, NoRootException, NoSuchAlgorithmException {
        final List<String> output = new ArrayList<>();
        Application.getRootShell().run(output, "sha256sum /proc/version|cut -d ' ' -f 1");
        if (output.size() != 1 || output.get(0).length() != 64)
            throw new InvalidParameterException("Invalid sha256 of /proc/version");
        final String moduleName = String.format(MODULE_NAME, output.get(0));
        HttpURLConnection connection = (HttpURLConnection)new URL(MODULE_LIST_URL).openConnection();
        connection.setRequestProperty("User-Agent", Application.USER_AGENT);
        connection.connect();
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
            throw new IOException("Hash list could not be found");
        byte[] input = new byte[1024 * 1024 * 3 /* 3MiB */];
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
        connection = (HttpURLConnection)new URL(String.format(MODULE_URL, moduleName)).openConnection();
        connection.setRequestProperty("User-Agent", Application.USER_AGENT);
        connection.connect();
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
            throw new IOException("Module file could not be found, despite being on hash list");

        tmpDir.mkdirs();
        moduleDir.mkdir();
        File tempFile = null;
        try {
            tempFile = File.createTempFile("UNVERIFIED-", null, tmpDir);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
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
}
