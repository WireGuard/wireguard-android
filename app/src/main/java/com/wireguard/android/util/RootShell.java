package com.wireguard.android.util;

import android.content.Context;
import android.system.OsConstants;
import android.util.Log;

import com.wireguard.android.Application.ApplicationContext;
import com.wireguard.android.Application.ApplicationScope;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

/**
 * Helper class for running commands as root.
 */

@ApplicationScope
public class RootShell {
    private static final Pattern ERRNO_EXTRACTOR = Pattern.compile("error=(\\d+)");
    /**
     * Setup commands that are run at the beginning of each root shell. The trap command ensures
     * access to the return value of the last command, since su itself always exits with 0.
     */
    private static final String TAG = "WireGuard/RootShell";
    private static final String[][] libraryNamedExecutables = {
            {"libwg.so", "wg"},
            {"libwg-quick.so", "wg-quick"}
    };

    private final String preamble;

    @Inject
    public RootShell(@ApplicationContext final Context context) {
        final String binDir = context.getCacheDir().getPath() + "/bin";
        final String tmpDir = context.getCacheDir().getPath() + "/tmp";
        final String libDir = context.getApplicationInfo().nativeLibraryDir;

        new File(binDir).mkdirs();
        new File(tmpDir).mkdirs();

        StringBuilder builder = new StringBuilder();
        for (final String[] libraryNamedExecutable : libraryNamedExecutables) {
            final String arg1 = "'" + libDir + "/" + libraryNamedExecutable[0] + "'";
            final String arg2 = "'" + binDir + "/" + libraryNamedExecutable[1] + "'";
            builder.append(String.format("[ %s -ef %s ] || ln -sf %s %s || exit 31;", arg1, arg2, arg1, arg2));
        }
        builder.append(String.format("export PATH=\"%s:$PATH\" TMPDIR=\"%s\";", binDir, tmpDir));

        preamble = builder.toString();
    }

    /**
     * Run a command in a root shell.
     *
     * @param output  Lines read from stdout are appended to this list. Pass null if the
     *                output from the shell is not important.
     * @param command Command to run as root.
     * @return The exit value of the last command run, or -1 if there was an internal error.
     */
    public int run(final List<String> output, final String command) {
        int exitValue = -1;
        try {
            final ProcessBuilder builder = new ProcessBuilder();
            builder.environment().put("LANG", "C");
            builder.command("su", "-c", preamble + command);
            final Process process = builder.start();
            Log.d(TAG, "Running: " + command);
            final InputStream stdout = process.getInputStream();
            final InputStream stderr = process.getErrorStream();
            final BufferedReader stdoutReader =
                    new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8));
            final BufferedReader stderrReader =
                    new BufferedReader(new InputStreamReader(stderr, StandardCharsets.UTF_8));
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                if (output != null)
                    output.add(line);
                Log.v(TAG, "stdout: " + line);
            }
            int linesOfStderr = 0;
            String stderrLast = null;
            while ((line = stderrReader.readLine()) != null) {
                ++linesOfStderr;
                stderrLast = line;
                Log.v(TAG, "stderr: " + line);
            }
            exitValue = process.waitFor();
            process.destroy();
            if (exitValue == 1 && linesOfStderr == 1 && stderrLast.equals("Permission denied"))
                exitValue = OsConstants.EACCES;
            Log.d(TAG, "Exit status: " + exitValue);
        } catch (IOException | InterruptedException e) {
            Log.w(TAG, "Session failed with exception", e);
            final Matcher match = ERRNO_EXTRACTOR.matcher(e.toString());
            if (match.find())
                exitValue = Integer.valueOf(match.group(1));
        }
        return exitValue;
    }
}
