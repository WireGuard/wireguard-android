package com.wireguard.android.backend;

import android.content.Context;
import android.util.Log;

import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.android.util.RootShell;
import com.wireguard.config.Config;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import java9.util.stream.Collectors;
import java9.util.stream.Stream;

/**
 * WireGuard backend that uses {@code wg-quick} to implement tunnel configuration.
 */

public final class WgQuickBackend implements Backend {
    private static final String TAG = WgQuickBackend.class.getSimpleName();

    private final Context context;
    private final RootShell rootShell;

    public WgQuickBackend(final Context context, final RootShell rootShell) {
        this.context = context;
        this.rootShell = rootShell;
    }

    private static State resolveState(final State currentState, State requestedState) {
        if (requestedState == State.UNKNOWN)
            throw new IllegalArgumentException("Requested unknown state");
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
        final List<String> output = new LinkedList<>();
        // Don't throw an exception here or nothing will show up in the UI.
        if (rootShell.run(output, "wg show interfaces") != 0 || output.isEmpty())
            return Collections.emptySet();
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
    public State setState(final Tunnel tunnel, final State state) throws IOException {
        Log.v(TAG, "Requested state change to " + state + " for tunnel " + tunnel.getName());
        final State originalState = getState(tunnel);
        final State resolvedState = resolveState(originalState, state);
        if (resolvedState == State.UP) {
            // FIXME: Assumes file layout from FileConfigStore. Use a temporary file.
            final File file = new File(context.getFilesDir(), tunnel.getName() + ".conf");
            if (rootShell.run(null, String.format("wg-quick up '%s'", file.getAbsolutePath())) != 0)
                throw new IOException("wg-quick failed");
        } else {
            if (rootShell.run(null, String.format("wg-quick down '%s'", tunnel.getName())) != 0)
                throw new IOException("wg-quick failed");
        }
        return getState(tunnel);
    }
}
