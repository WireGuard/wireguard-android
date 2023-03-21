/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util;

import android.content.Context;
import android.system.OsConstants;
import android.util.Log;

import com.wireguard.android.util.RootShell.RootShellException;
import com.wireguard.util.NonNullForAll;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;

/**
 * Helper to install WireGuard tools to the system partition.
 */

@NonNullForAll
public final class ToolsInstaller {
    public static final int ERROR = 0x0;
    public static final int MAGISK = 0x4;
    public static final int NO = 0x2;
    public static final int SYSTEM = 0x8;
    public static final int YES = 0x1;
    private static final String[] EXECUTABLES = {"wg", "wg-quick"};
    private static final File[] INSTALL_DIRS = {
            new File("/system/xbin"),
            new File("/system/bin"),
    };
    @Nullable private static final File INSTALL_DIR = getInstallDir();
    private static final String TAG = "WireGuard/ToolsInstaller";

    private final Context context;
    private final File localBinaryDir;
    private final Object lock = new Object();
    private final RootShell rootShell;
    @Nullable private Boolean areToolsAvailable;
    @Nullable private Boolean installAsMagiskModule;

    public ToolsInstaller(final Context context, final RootShell rootShell) {
        localBinaryDir = new File(context.getCodeCacheDir(), "bin");
        this.context = context;
        this.rootShell = rootShell;
    }

    @Nullable
    private static File getInstallDir() {
        final String path = System.getenv("PATH");
        if (path == null)
            return INSTALL_DIRS[0];
        final List<String> paths = Arrays.asList(path.split(":"));
        for (final File dir : INSTALL_DIRS) {
            if (paths.contains(dir.getPath()) && dir.isDirectory())
                return dir;
        }
        return null;
    }

    public int areInstalled() throws RootShellException {
        if (INSTALL_DIR == null)
            return ERROR;
        final StringBuilder script = new StringBuilder();
        for (final String name : EXECUTABLES) {
            script.append(String.format("cmp -s '%s' '%s' && ",
                    new File(localBinaryDir, name).getAbsolutePath(),
                    new File(INSTALL_DIR, name).getAbsolutePath()));
        }
        script.append("exit ").append(OsConstants.EALREADY).append(';');
        try {
            final int ret = rootShell.run(null, script.toString());
            if (ret == OsConstants.EALREADY)
                return willInstallAsMagiskModule() ? YES | MAGISK : YES | SYSTEM;
            else
                return willInstallAsMagiskModule() ? NO | MAGISK : NO | SYSTEM;
        } catch (final IOException ignored) {
            return ERROR;
        } catch (final RootShellException e) {
            if (e.isIORelated())
                return ERROR;
            throw e;
        }
    }

    public void ensureToolsAvailable() throws FileNotFoundException {
        synchronized (lock) {
            if (areToolsAvailable == null) {
                try {
                    Log.d(TAG, extract() ? "Tools are now extracted into our private binary dir" :
                            "Tools were already extracted into our private binary dir");
                    areToolsAvailable = true;
                } catch (final IOException e) {
                    Log.e(TAG, "The wg and wg-quick tools are not available", e);
                    areToolsAvailable = false;
                }
            }
            if (!areToolsAvailable)
                throw new FileNotFoundException("Required tools unavailable");
        }
    }

    public boolean extract() throws IOException {
        localBinaryDir.mkdirs();
        final File[] files = new File[EXECUTABLES.length];
        final File[] tempFiles = new File[EXECUTABLES.length];
        boolean allExist = true;
        for (int i = 0; i < files.length; ++i) {
            files[i] = new File(localBinaryDir, EXECUTABLES[i]);
            tempFiles[i] = new File(localBinaryDir, EXECUTABLES[i] + ".tmp");
            allExist &= files[i].exists();
        }
        if (allExist)
            return false;
        for (int i = 0; i < files.length; ++i) {
            if (!SharedLibraryLoader.extractLibrary(context, EXECUTABLES[i], tempFiles[i]))
                throw new FileNotFoundException("Unable to find " + EXECUTABLES[i]);
            if (!tempFiles[i].setExecutable(true, false))
                throw new IOException("Unable to mark " + tempFiles[i].getAbsolutePath() + " as executable");
            if (!tempFiles[i].renameTo(files[i]))
                throw new IOException("Unable to rename " + tempFiles[i].getAbsolutePath() + " to " + files[i].getAbsolutePath());
        }
        return true;
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    public int install() throws RootShellException, IOException {
        if (!context.getPackageName().startsWith("com.wireguard."))
            throw new SecurityException("The tools may only be installed system-wide from the main WireGuard app.");
        return willInstallAsMagiskModule() ? installMagisk() : installSystem();
    }

    private int installMagisk() throws RootShellException, IOException {
        extract();
        final StringBuilder script = new StringBuilder("set -ex; ");

        script.append("trap 'rm -rf /data/adb/modules/wireguard' INT TERM EXIT; ");
        script.append(String.format("rm -rf /data/adb/modules/wireguard/; mkdir -p /data/adb/modules/wireguard%s; ", INSTALL_DIR));
        script.append("printf 'id=wireguard\nname=WireGuard Command Line Tools\nversion=1.0\nversionCode=1\nauthor=zx2c4\ndescription=Command line tools for WireGuard\nminMagisk=1500\n' > /data/adb/modules/wireguard/module.prop; ");
        script.append("touch /data/adb/modules/wireguard/auto_mount; ");
        for (final String name : EXECUTABLES) {
            final File destination = new File("/data/adb/modules/wireguard" + INSTALL_DIR, name);
            script.append(String.format("cp '%s' '%s'; chmod 755 '%s'; chcon 'u:object_r:system_file:s0' '%s' || true; ",
                    new File(localBinaryDir, name), destination, destination, destination));
        }
        script.append("trap - INT TERM EXIT;");

        try {
            return rootShell.run(null, script.toString()) == 0 ? YES | MAGISK : ERROR;
        } catch (final IOException ignored) {
            return ERROR;
        } catch (final RootShellException e) {
            if (e.isIORelated())
                return ERROR;
            throw e;
        }
    }

    private int installSystem() throws RootShellException, IOException {
        if (INSTALL_DIR == null)
            return OsConstants.ENOENT;
        extract();
        final StringBuilder script = new StringBuilder("set -ex; ");
        script.append("trap 'mount -o ro,remount /system' EXIT; mount -o rw,remount /system; ");
        for (final String name : EXECUTABLES) {
            final File destination = new File(INSTALL_DIR, name);
            script.append(String.format("cp '%s' '%s'; chmod 755 '%s'; restorecon '%s' || true; ",
                    new File(localBinaryDir, name), destination, destination, destination));
        }
        try {
            return rootShell.run(null, script.toString()) == 0 ? YES | SYSTEM : ERROR;
        } catch (final IOException ignored) {
            return ERROR;
        } catch (final RootShellException e) {
            if (e.isIORelated())
                return ERROR;
            throw e;
        }
    }

    private boolean willInstallAsMagiskModule() {
        synchronized (lock) {
            if (installAsMagiskModule == null) {
                try {
                    installAsMagiskModule = rootShell.run(null, "[ -d /data/adb/modules -a ! -f /cache/.disable_magisk ]") == OsConstants.EXIT_SUCCESS;
                } catch (final Exception ignored) {
                    installAsMagiskModule = false;
                }
            }
            return installAsMagiskModule;
        }
    }
}
