/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import android.util.Log;

import com.wireguard.android.util.RootShell;
import com.wireguard.android.util.RootShell.RootShellException;
import com.wireguard.util.NonNullForAll;

import java.io.IOException;
import java.util.Collection;

/**
 * A {@link TunnelActionHandler} implementation that executes scripts using a root shell.
 * Scripts are executed sequentially. If there is an error executing a script for a given step
 * the remaining scripts in that step are skipped.
 */
@NonNullForAll
public final class RootTunnelActionHandler implements TunnelActionHandler {

    private static final String TAG = "WireGuard/TunnelAction";
    private final RootShell rootShell;

    public RootTunnelActionHandler(final RootShell rootShell) {
        this.rootShell = rootShell;
    }

    @Override
    public void runPreDown(final Collection<String> scripts) {
        if (scripts.isEmpty()) return;
        Log.d(TAG, "Running PreDown scripts");
        runTunnelScripts(scripts);
    }

    @Override
    public void runPostDown(final Collection<String> scripts) {
        if (scripts.isEmpty()) return;
        Log.d(TAG, "Running PostDown scripts");
        runTunnelScripts(scripts);
    }

    @Override
    public void runPreUp(final Collection<String> scripts) {
        if (scripts.isEmpty()) return;
        Log.d(TAG, "Running PreUp scripts");
        runTunnelScripts(scripts);
    }

    @Override
    public void runPostUp(final Collection<String> scripts) {
        if (scripts.isEmpty()) return;
        Log.d(TAG, "Running PostUp scripts");
        runTunnelScripts(scripts);
    }

    private void runTunnelScripts(final Iterable<String> scripts) {
        for (final String script : scripts) {
            if (script.contains("%i")) {
                Log.e(TAG, "'%i' syntax is not supported with the GoBackend. Aborting");
                return;
            }

            try {
                rootShell.run(null, script);
            } catch (final IOException | RootShellException e) {
                Log.e(TAG, "Failed to execute script.", e);
                return;
            }
        }

    }
}
