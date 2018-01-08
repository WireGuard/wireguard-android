package com.wireguard.android.util;

import android.content.Context;
import android.system.OsConstants;

import com.wireguard.android.Application.ApplicationContext;
import com.wireguard.android.Application.ApplicationScope;

import java.io.File;
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

    private final String nativeLibraryDir;
    private final RootShell rootShell;

    @Inject
    public ToolsInstaller(@ApplicationContext final Context context, final RootShell rootShell) {
        nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
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

    public int install() {
        final File installDir = getInstallDir();
        if (installDir == null)
            return OsConstants.ENOENT;
        final StringBuilder script = new StringBuilder("set -ex; ");
        for (final String[] names : EXECUTABLES) {
            script.append(String.format("cmp -s '%s' '%s' && ",
                    new File(nativeLibraryDir, names[0]),
                    new File(installDir, names[1])));
        }
        script.append("exit ").append(OsConstants.EALREADY).append("; ");
        script.append("trap 'mount -o ro,remount /system' EXIT; mount -o rw,remount /system; ");
        for (final String[] names : EXECUTABLES) {
            script.append(String.format("cp %s %s; chmod 755 %s; ",
                    new File(nativeLibraryDir, names[0]),
                    new File(installDir, names[1]),
                    new File(installDir, names[1])));
        }
        return rootShell.run(null, script.toString());
    }
}
