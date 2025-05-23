/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import com.wireguard.config.Config;
import com.wireguard.util.NonNullForAll;

import java.util.Set;

import androidx.annotation.Nullable;

/**
 * Interface for implementations of the WireGuard secure network tunnel.
 */

@NonNullForAll
public interface Backend {
    /**
     * Enumerate names of currently-running tunnels.
     *
     * @return The set of running tunnel names.
     */
    Set<String> getRunningTunnelNames();

    /**
     * Get the state of a tunnel.
     *
     * @param tunnel The tunnel to examine the state of.
     * @return The state of the tunnel.
     * @throws Exception Exception raised when retrieving tunnel's state.
     */
    Tunnel.State getState(Tunnel tunnel) throws Exception;

    /**
     * Get statistics about traffic and errors on this tunnel. If the tunnel is not running, the
     * statistics object will be filled with zero values.
     *
     * @param tunnel The tunnel to retrieve statistics for.
     * @return The statistics for the tunnel.
     * @throws Exception Exception raised when retrieving statistics.
     */
    Statistics getStatistics(Tunnel tunnel) throws Exception;

    /**
     * Determine version of underlying backend.
     *
     * @return The version of the backend.
     * @throws Exception Exception raised while retrieving version.
     */
    String getVersion() throws Exception;

    /**
     * Determines whether the service is running in always-on VPN mode.
     * In this mode the system ensures that the service is always running by restarting it when necessary,
     * e.g. after reboot.
     *
     * @return A boolean indicating whether the service is running in always-on VPN mode.
     * @throws Exception Exception raised while retrieving the always-on status.
     */

    boolean isAlwaysOn() throws Exception;

    /**
     * Determines whether the service is running in always-on VPN lockdown mode.
     * In this mode the system ensures that the service is always running and that the apps
     * aren't allowed to bypass the VPN.
     *
     * @return A boolean indicating whether the service is running in always-on VPN lockdown mode.
     * @throws Exception Exception raised while retrieving the lockdown status.
     */

    boolean isLockdownEnabled() throws Exception;

    /**
     * Set the state of a tunnel, updating it's configuration. If the tunnel is already up, config
     * may update the running configuration; config may be null when setting the tunnel down.
     *
     * @param tunnel The tunnel to control the state of.
     * @param state  The new state for this tunnel. Must be {@code UP}, {@code DOWN}, or
     *               {@code TOGGLE}.
     * @param config The configuration for this tunnel, may be null if state is {@code DOWN}.
     * @return The updated state of the tunnel.
     * @throws Exception Exception raised while changing state.
     */
    Tunnel.State setState(Tunnel tunnel, Tunnel.State state, @Nullable Config config) throws Exception;
}
