/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.config.Config;

import java.util.Set;

/**
 * Interface for implementations of the WireGuard secure network tunnel.
 */

public interface Backend {
    /**
     * Update the volatile configuration of a running tunnel and return the resulting configuration.
     * If the tunnel is not up, return the configuration that would result (if known), or else
     * simply return the given configuration.
     *
     * @param tunnel The tunnel to apply the configuration to.
     * @param config The new configuration for this tunnel.
     * @return The updated configuration of the tunnel.
     */
    Config applyConfig(Tunnel tunnel, Config config) throws Exception;

    /**
     * Enumerate the names of currently-running tunnels.
     *
     * @return The set of running tunnel names.
     */
    Set<String> enumerate();

    /**
     * Get the actual state of a tunnel.
     *
     * @param tunnel The tunnel to examine the state of.
     * @return The state of the tunnel.
     */
    State getState(Tunnel tunnel) throws Exception;

    /**
     * Get statistics about traffic and errors on this tunnel. If the tunnel is not running, the
     * statistics object will be filled with zero values.
     *
     * @param tunnel The tunnel to retrieve statistics for.
     * @return The statistics for the tunnel.
     */
    Statistics getStatistics(Tunnel tunnel) throws Exception;

    /**
     * Determine type name of underlying backend.
     *
     * @return Type name
     */
    String getTypePrettyName();

    /**
     * Determine version of underlying backend.
     *
     * @return The version of the backend.
     * @throws Exception
     */
    String getVersion() throws Exception;

    /**
     * Set the state of a tunnel.
     *
     * @param tunnel The tunnel to control the state of.
     * @param state  The new state for this tunnel. Must be {@code UP}, {@code DOWN}, or
     *               {@code TOGGLE}.
     * @return The updated state of the tunnel.
     */
    State setState(Tunnel tunnel, State state) throws Exception;
}
