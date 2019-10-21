/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class SharedLibraryLoader {
    private static final String TAG = "WireGuard/" + SharedLibraryLoader.class.getSimpleName();

    private SharedLibraryLoader() {
    }

    public static boolean extractLibrary(final Context context, final String libName, final File destination) throws IOException {
        final Collection<String> apks = new HashSet<>();
        if (context.getApplicationInfo().sourceDir != null)
            apks.add(context.getApplicationInfo().sourceDir);
        if (context.getApplicationInfo().splitSourceDirs != null)
            apks.addAll(Arrays.asList(context.getApplicationInfo().splitSourceDirs));

        for (final String abi : Build.SUPPORTED_ABIS) {
            for (final String apk : apks) {
                final ZipFile zipFile;
                try {
                    zipFile = new ZipFile(new File(apk), ZipFile.OPEN_READ);
                } catch (final IOException e) {
                    throw new RuntimeException(e);
                }

                final String mappedLibName = System.mapLibraryName(libName);
                final byte[] buffer = new byte[1024 * 32];
                final String libZipPath = "lib" + File.separatorChar + abi + File.separatorChar + mappedLibName;
                final ZipEntry zipEntry = zipFile.getEntry(libZipPath);
                if (zipEntry == null)
                    continue;
                Log.d(TAG, "Extracting apk:/" + libZipPath + " to " + destination.getAbsolutePath());
                try (final FileOutputStream out = new FileOutputStream(destination);
                     final InputStream in = zipFile.getInputStream(zipEntry)) {
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                    out.getFD().sync();
                }
                return true;
            }
        }
        return false;
    }

    public static void loadSharedLibrary(final Context context, final String libName) {
        Throwable noAbiException;
        try {
            System.loadLibrary(libName);
            return;
        } catch (final UnsatisfiedLinkError e) {
            Log.d(TAG, "Failed to load library normally, so attempting to extract from apk", e);
            noAbiException = e;
        }
        File f = null;
        try {
            f = File.createTempFile("lib", ".so", context.getCodeCacheDir());
            if (extractLibrary(context, libName, f)) {
                System.load(f.getAbsolutePath());
                return;
            }
        } catch (final Exception e) {
            Log.d(TAG, "Failed to load library apk:/" + libName, e);
            noAbiException = e;
        } finally {
            if (f != null)
                // noinspection ResultOfMethodCallIgnored
                f.delete();
        }
        if (noAbiException instanceof RuntimeException)
            throw (RuntimeException) noAbiException;
        throw new RuntimeException(noAbiException);
    }
}
