package com.wireguard.android.backend;

import android.content.Context;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;

import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.android.util.RootShell;
import com.wireguard.config.Config;

import java.io.File;
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

    private final Context context;
    private final RootShell rootShell;

    public WgQuickBackend(final Context context, final RootShell rootShell) {
        this.context = context;
        this.rootShell = rootShell;
    }

    private static State resolveState(final State currentState, State requestedState) {
        if (requestedState == State.TOGGLE)
            requestedState = currentState == State.UP ? State.DOWN : State.UP;
        return requestedState;
    }

    @Override
    public Config applyConfig(final Tunnel tunnel, final Config config) {
        if (tunnel.getState() == State.UP)
            throw new UnsupportedOperationException("Not implemented");
        return config;
    }

    @Override
    public Set<String> enumerate() {
        final List<String> output = new ArrayList<>();
        // Don't throw an exception here or nothing will show up in the UI.
        try {
            if (rootShell.run(output, "wg show interfaces") != 0 || output.isEmpty())
                return Collections.emptySet();
        } catch (Exception e) {
            return Collections.emptySet();
        }
        // wg puts all interface names on the same line. Split them into separate elements.
        return Stream.of(output.get(0).split(" ")).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public State getState(final Tunnel tunnel) {
        Log.v(TAG, "Requested state for tunnel " + tunnel.getName());
        return enumerate().contains(tunnel.getName()) ? State.UP : State.DOWN;
    }

    @Override
    public Statistics getStatistics(final Tunnel tunnel) {
        return new Statistics();
    }

    @Override
    public State setState(final Tunnel tunnel, final State state) throws Exception {
        Log.v(TAG, "Requested state change to " + state + " for tunnel " + tunnel.getName());
        final State originalState = getState(tunnel);
        final State resolvedState = resolveState(originalState, state);
        if (resolvedState == originalState)
            return originalState;
        final int result;
        if (resolvedState == State.UP) {
            if (!new File("/sys/module/wireguard").exists())
                throw new ErrnoException("WireGuard module not loaded", OsConstants.ENODEV);
            // FIXME: Assumes file layout from FileConfigStore. Use a temporary file.
            final File file = new File(context.getFilesDir(), tunnel.getName() + ".conf");
            result = rootShell.run(null, String.format("wg-quick up '%s'", file.getAbsolutePath()));
        } else {
            result = rootShell.run(null, String.format("wg-quick down '%s'", tunnel.getName()));
        }
        if (result != 0)
            throw new Exception("wg-quick failed");
        return getState(tunnel);
    }
}
