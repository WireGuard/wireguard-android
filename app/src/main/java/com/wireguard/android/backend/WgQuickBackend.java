/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import android.content.Context;
import androidx.annotation.Nullable;
import android.util.Log;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.config.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import java9.util.stream.Collectors;
import java9.util.stream.Stream;

/**
 * WireGuard backend that uses {@code wg-quick} to implement tunnel configuration.
 */

public final class WgQuickBackend implements Backend {
    private static final String TAG = "WireGuard/" + WgQuickBackend.class.getSimpleName();

    private final File localTemporaryDir;
    private final Context context;

    public WgQuickBackend(final Context context) {
        localTemporaryDir = new File(context.getCacheDir(), "tmp");
        this.context = context;
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
            Application.getToolsInstaller().ensureToolsAvailable();
            if (Application.getRootShell().run(output, "wg show interfaces") != 0 || output.isEmpty())
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
    public String getTypePrettyName() {
        return context.getString(R.string.type_name_kernel_module);
    }

    @Override
    public String getVersion() throws Exception {
        final List<String> output = new ArrayList<>();
        if (Application.getRootShell()
                .run(output, "cat /sys/module/wireguard/version") != 0 || output.isEmpty())
            throw new Exception(context.getString(R.string.module_version_error));
        return output.get(0);
    }

    @Override
    public State setState(final Tunnel tunnel, State state) throws Exception {
        final State originalState = getState(tunnel);
        if (state == State.TOGGLE)
            state = originalState == State.UP ? State.DOWN : State.UP;
        if (state == originalState)
            return originalState;
        Log.d(TAG, "Changing tunnel " + tunnel.getName() + " to state " + state);
        Application.getToolsInstaller().ensureToolsAvailable();
        setStateInternal(tunnel, tunnel.getConfig(), state);
        return getState(tunnel);
    }

    private void setStateInternal(final Tunnel tunnel, @Nullable final Config config, final State state) throws Exception {
        Objects.requireNonNull(config, "Trying to set state with a null config");

        final File tempFile = new File(localTemporaryDir, tunnel.getName() + ".conf");
        try (final FileOutputStream stream = new FileOutputStream(tempFile, false)) {
            stream.write(config.toWgQuickString().getBytes(StandardCharsets.UTF_8));
        }
        String command = String.format("wg-quick %s '%s'",
                state.toString().toLowerCase(Locale.ENGLISH), tempFile.getAbsolutePath());
        if (state == State.UP)
            command = "cat /sys/module/wireguard/version && " + command;
        final int result = Application.getRootShell().run(null, command);
        // noinspection ResultOfMethodCallIgnored
        tempFile.delete();
        if (result != 0)
            throw new Exception(context.getString(R.string.tunnel_config_error, result));
    }
}
