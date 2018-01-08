package com.wireguard.android.util;

import android.content.Context;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;

import com.wireguard.android.Application.ApplicationContext;
import com.wireguard.android.Application.ApplicationScope;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
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
    private static final String TAG = "WireGuard/" + RootShell.class.getSimpleName();
    private static final String[][] libraryNamedExecutables = {
            {"libwg.so", "wg"},
            {"libwg-quick.so", "wg-quick"}
    };

    private final String preamble;

    private BufferedWriter stdin;
    private BufferedReader stdout;
    private BufferedReader stderr;
    private Process process;

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
        builder.append("id;\n");

        preamble = builder.toString();
    }

    private static boolean isExecutable(final String name) {
        final String path = System.getenv("PATH");
        if (path == null)
            return false;
        for (final String dir : path.split(":"))
            if (new File(dir, name).canExecute())
                return true;
        return false;
    }

    private void ensureRoot() throws Exception {
        try {
            if (process != null) {
                process.exitValue();
                process = null;
            }
        } catch (IllegalThreadStateException e) {
            return;
        }

        if (!isExecutable("su"))
            throw new NoRootException();

        try {
            final ProcessBuilder builder = new ProcessBuilder();
            builder.environment().put("LANG", "C");
            builder.command("su");
            process = builder.start();
            stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            stderr = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

            Log.d(TAG, "New root shell, sending preamble: " + preamble);
            stdin.write(preamble);
            stdin.flush();
            final String id = stdout.readLine();

            try {
                int errno = process.exitValue();
                String line;
                while ((line = stderr.readLine()) != null) {
                   if (line.contains("Permission denied"))
                       throw new NoRootException();
                }
                throw new ErrnoException("Unknown error when obtaining root access", errno);
            } catch (IllegalThreadStateException e) {
                // We're alive, so keep executing.
            }

            if (id == null || !id.contains("uid=0"))
                throw new NoRootException();
        } catch (Exception e) {
            Log.w(TAG, "Session failed with exception", e);
            process.destroy();
            process = null;
            final Matcher match = ERRNO_EXTRACTOR.matcher(e.toString());
            if (match.find()) {
                final int errno = Integer.valueOf(match.group(1));
                if (errno == OsConstants.EACCES)
                    throw new NoRootException();
                else
                    throw new ErrnoException("Unknown error when obtaining root access", errno);
            }
            throw e;
        }
    }

    /**
     * Run a command in a root shell.
     *
     * @param output  Lines read from stdout are appended to this list. Pass null if the
     *                output from the shell is not important.
     * @param command Command to run as root.
     * @return The exit value of the last command run, or -1 if there was an internal error.
     */
    public int run(final List<String> output, final String command) throws Exception {
        ensureRoot();

        StringBuilder builder = new StringBuilder();
        final String marker = UUID.randomUUID().toString();
        final String begin = marker + " begin";
        final String end = marker + " end";

        builder.append(String.format("echo '%s';", begin));
        builder.append(String.format("echo '%s' >&2;", begin));

        builder.append('(');
        builder.append(command);
        builder.append(");");

        builder.append("ret=$?;");
        builder.append(String.format("echo '%s' $ret;", end));
        builder.append(String.format("echo '%s' $ret >&2;", end));

        builder.append('\n');

        Log.v(TAG, "executing: " + command);
        stdin.write(builder.toString());
        stdin.flush();

        String line;
        boolean first = true;
        int errnoStdout = -1, errnoStderr = -2;
        int beginEnds = 0;
        while ((line = stdout.readLine()) != null) {
            if (first) {
                first = false;
                if (!line.startsWith(begin))
                    throw new ErrnoException("Could not find begin marker", OsConstants.EBADMSG);
                ++beginEnds;
                continue;
            }
            if (line.startsWith(end) && line.length() > end.length()) {
                errnoStdout = Integer.valueOf(line.substring(end.length() + 1));
                ++beginEnds;
                break;
            }
            if (output != null)
                output.add(line);
            Log.v(TAG, "stdout: " + line);
        }
        first = true;
        while ((line = stderr.readLine()) != null) {
            if (first) {
                first = false;
                if (!line.startsWith(begin))
                    throw new ErrnoException("Could not find begin marker", OsConstants.EBADMSG);
                ++beginEnds;
                continue;
            }
            if (line.startsWith(end) && line.length() > end.length()) {
                errnoStderr = Integer.valueOf(line.substring(end.length() + 1));
                ++beginEnds;
                break;
            }
            Log.v(TAG, "stderr: " + line);
        }
        if (errnoStderr != errnoStdout || beginEnds != 4)
            throw new ErrnoException("Incorrect errno reporting", OsConstants.EBADMSG);

        return errnoStdout;
    }

    public class NoRootException extends Exception {
    }
}
