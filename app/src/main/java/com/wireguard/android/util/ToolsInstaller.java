package com.wireguard.android.util;

import android.content.Context;
import android.system.ErrnoException;
import android.system.OsConstants;

import com.wireguard.android.Application.ApplicationContext;
import com.wireguard.android.Application.ApplicationScope;
import com.wireguard.android.util.RootShell.NoRootException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

/**
 * Helper to install WireGuard tools to the system partition.
 */

@ApplicationScope
public final class ToolsInstaller {
    private static final String[][] EXECUTABLES = {
            {"libwg.so", "wg"},
            {"libwg-quick.so", "wg-quick"},
    };
    private static final File[] INSTALL_DIRS = {
            new File("/system/xbin"),
            new File("/system/bin"),
    };
    private static final File INSTALL_DIR = getInstallDir();

    private final File localBinaryDir;
    private final File nativeLibraryDir;
    private final RootShell rootShell;

    @Inject
    public ToolsInstaller(@ApplicationContext final Context context, final RootShell rootShell) {
        localBinaryDir = new File(context.getCacheDir(), "bin");
        nativeLibraryDir = new File(context.getApplicationInfo().nativeLibraryDir);
        this.rootShell = rootShell;
    }

    private static File getInstallDir() {
        final String path = System.getenv("PATH");
        if (path == null)
            return INSTALL_DIRS[0];
        final List<String> paths = Arrays.asList(path.split(":"));
        for (final File dir : INSTALL_DIRS)
            if (paths.contains(dir.getPath()) && dir.isDirectory())
                return dir;
        return null;
    }

    public boolean areInstalled() {
        if (INSTALL_DIR == null)
            return false;
        final StringBuilder script = new StringBuilder();
        for (final String[] names : EXECUTABLES) {
            script.append(String.format("cmp -s '%s' '%s' && ",
                    new File(nativeLibraryDir, names[0]),
                    new File(INSTALL_DIR, names[1])));
        }
        script.append("exit ").append(OsConstants.EALREADY);
        try {
            return rootShell.run(null, script.toString()) == OsConstants.EALREADY;
        } catch (final ErrnoException | IOException | NoRootException ignored) {
            return false;
        }
    }

    public boolean areSymlinked() {
        final StringBuilder script = new StringBuilder();
        for (final String[] names : EXECUTABLES) {
            script.append(String.format("test '%s' -ef '%s' && ",
                    new File(nativeLibraryDir, names[0]),
                    new File(localBinaryDir, names[1])));
        }
        script.append("exit ").append(OsConstants.EALREADY);
        try {
            return rootShell.run(null, script.toString()) == OsConstants.EALREADY;
        } catch (final ErrnoException | IOException | NoRootException ignored) {
            return false;
        }
    }

    public int install() {
        if (INSTALL_DIR == null)
            return OsConstants.ENOENT;
        final StringBuilder script = new StringBuilder("set -ex"
                + "; trap 'mount -o ro,remount /system' EXIT; mount -o rw,remount /system");
        for (final String[] names : EXECUTABLES) {
            final File destination = new File(INSTALL_DIR, names[1]);
            script.append(String.format("; cp '%s' '%s'; chmod 755 '%s'; restorecon '%s' ",
                    new File(nativeLibraryDir, names[0]), destination, destination, destination));
        }
        try {
            return rootShell.run(null, script.toString());
        } catch (final ErrnoException e) {
            return e.errno;
        } catch (final IOException ignored) {
            return OsConstants.EXIT_FAILURE;
        } catch (final NoRootException ignored) {
            return OsConstants.EACCES;
        }
    }

    public int symlink() {
        final StringBuilder script = new StringBuilder("set -ex");
        for (final String[] names : EXECUTABLES) {
            script.append(String.format("; ln -fns '%s' '%s'",
                    new File(nativeLibraryDir, names[0]),
                    new File(localBinaryDir, names[1])));
        }
        try {
            return rootShell.run(null, script.toString());
        } catch (final ErrnoException e) {
            return e.errno;
        } catch (final IOException ignored) {
            return OsConstants.EXIT_FAILURE;
        } catch (final NoRootException ignored) {
            return OsConstants.EACCES;
        }
    }
}
