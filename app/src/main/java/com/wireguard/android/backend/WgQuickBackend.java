package com.wireguard.android.backend;

import android.content.Context;
import android.util.Log;

import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.android.util.RootShell;
import com.wireguard.android.util.ToolsInstaller;
import com.wireguard.config.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import java9.util.stream.Collectors;
import java9.util.stream.Stream;

/**
 * WireGuard backend that uses {@code wg-quick} to implement tunnel configuration.
 */

public final class WgQuickBackend implements Backend {
    private static final String TAG = "WireGuard/" + WgQuickBackend.class.getSimpleName();

    private final File localTemporaryDir;
    private final RootShell rootShell;
    private final ToolsInstaller toolsInstaller;

    public WgQuickBackend(final Context context, final RootShell rootShell,
                          final ToolsInstaller toolsInstaller) {
        localTemporaryDir = new File(context.getCacheDir(), "tmp");
        this.rootShell = rootShell;
        this.toolsInstaller = toolsInstaller;
    }

    @Override
    public Config applyConfig(final Tunnel tunnel, final Config config) throws Exception {
        if (tunnel.getState() == State.UP) {
            // Restart the tunnel to apply the new config.
            setStateInternal(tunnel, tunnel.getConfig(), State.DOWN);
            try {
                setStateInternal(tunnel, config, State.UP);
            } catch (final Exception e) {
                // The new configuration didn't work, so try to go back to the old one.
                setStateInternal(tunnel, tunnel.getConfig(), State.UP);
                throw e;
            }
        }
        return config;
    }

    @Override
    public Set<String> enumerate() {
        final List<String> output = new ArrayList<>();
        // Don't throw an exception here or nothing will show up in the UI.
        try {
            toolsInstaller.ensureToolsAvailable();
            if (rootShell.run(output, "wg show interfaces") != 0 || output.isEmpty())
                return Collections.emptySet();
        } catch (final Exception e) {
            Log.w(TAG, "Unable to enumerate running tunnels", e);
            return Collections.emptySet();
        }
        // wg puts all interface names on the same line. Split them into separate elements.
        return Stream.of(output.get(0).split(" ")).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public State getState(final Tunnel tunnel) {
        return enumerate().contains(tunnel.getName()) ? State.UP : State.DOWN;
    }

    @Override
    public Statistics getStatistics(final Tunnel tunnel) {
        return new Statistics();
    }

    @Override
    public State setState(final Tunnel tunnel, State state) throws Exception {
        final State originalState = getState(tunnel);
        if (state == State.TOGGLE)
            state = originalState == State.UP ? State.DOWN : State.UP;
        if (state == originalState)
            return originalState;
        if (state == State.UP && !new File("/sys/module/wireguard").exists())
            throw new ModuleNotLoadedException("WireGuard module not loaded");
        Log.d(TAG, "Changing tunnel " + tunnel.getName() + " to state " + state);
        toolsInstaller.ensureToolsAvailable();
        setStateInternal(tunnel, tunnel.getConfig(), state);
        return getState(tunnel);
    }

    private void setStateInternal(final Tunnel tunnel, final Config config, final State state)
            throws Exception {
        final File tempFile = new File(localTemporaryDir, tunnel.getName() + ".conf");
        final int result;
        if (state == State.UP) {
            try (FileOutputStream stream = new FileOutputStream(tempFile, false)) {
                stream.write(config.toString().getBytes(StandardCharsets.UTF_8));
            }
            result = rootShell.run(null, "wg-quick up '" + tempFile.getAbsolutePath() + '\'');
        } else {
            result = rootShell.run(null, "wg-quick down '" + tempFile.getAbsolutePath() + '\'');
            if (result == 0 && !tempFile.delete())
                Log.w(TAG, "Couldn't delete temp config after bringing down " + tunnel.getName());
        }
        if (result != 0)
            throw new Exception("Unable to configure tunnel (wg-quick returned " + result + ')');
    }

    public static class ModuleNotLoadedException extends Exception {
        public ModuleNotLoadedException(final String message, final Throwable cause) {
            super(message, cause);
        }

        public ModuleNotLoadedException(final String message) {
            super(message);
        }
    }
}
