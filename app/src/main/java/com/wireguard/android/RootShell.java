package com.wireguard.android;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Helper class for running commands as root.
 */

class RootShell {
    /**
     * Setup commands that are run at the beginning of each root shell. The trap command ensures
     * access to the return value of the last command, since su itself always exits with 0.
     */
    private static final String SETUP_TEMPLATE = "export TMPDIR=%s\ntrap 'echo $?' EXIT\n";
    private static final String TAG = "RootShell";

    private final byte setupCommands[];
    private final String shell;

    RootShell(Context context) {
        this(context, "su");
    }

    RootShell(Context context, String shell) {
        final String tmpdir = context.getCacheDir().getPath();
        setupCommands = String.format(SETUP_TEMPLATE, tmpdir).getBytes(StandardCharsets.UTF_8);
        this.shell = shell;
    }

    /**
     * Run a series of commands in a root shell. These commands are all sent to the same shell
     * process, so they can be considered a shell script.
     *
     * @param output   Lines read from stdout and stderr are appended to this list. Pass null if the
     *                 output from the shell is not important.
     * @param commands One or more commands to run as root (each element is a separate line).
     * @return The exit value of the last command run, or -1 if there was an internal error.
     */
    int run(List<String> output, String... commands) {
        if (commands.length < 1)
            throw new IndexOutOfBoundsException("At least one command must be supplied");
        int exitValue = -1;
        try {
            final ProcessBuilder builder = new ProcessBuilder().redirectErrorStream(true);
            final Process process = builder.command(shell).start();
            final OutputStream stdin = process.getOutputStream();
            stdin.write(setupCommands);
            for (String command : commands)
                stdin.write(command.concat("\n").getBytes(StandardCharsets.UTF_8));
            stdin.close();
            Log.d(TAG, "Sent " + commands.length + " command(s), now reading output");
            final InputStream stdout = process.getInputStream();
            final BufferedReader stdoutReader =
                    new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8));
            String line;
            String lastLine = null;
            while ((line = stdoutReader.readLine()) != null) {
                Log.v(TAG, line);
                lastLine = line;
                if (output != null)
                    output.add(line);
            }
            process.waitFor();
            process.destroy();
            if (lastLine != null) {
                // Remove the exit value line from the output
                if (output != null)
                    output.remove(output.size() - 1);
                exitValue = Integer.parseInt(lastLine);
            }
            Log.d(TAG, "Session completed with exit value " + exitValue);
        } catch (IOException | InterruptedException | NumberFormatException e) {
            Log.w(TAG, "Session failed with exception", e);
        }
        return exitValue;
    }
}
