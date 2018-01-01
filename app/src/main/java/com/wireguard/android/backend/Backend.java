package com.wireguard.android.backend;

import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.config.Config;

import java9.util.concurrent.CompletionStage;

/**
 * Interface for implementations of the WireGuard secure network tunnel.
 */

public interface Backend {
    /**
     * Update the volatile configuration of a running tunnel, asynchronously, and return the
     * resulting configuration. If the tunnel is not up, return the configuration that would result
     * (if known), or else simply return the given configuration.
     *
     * @param tunnel The tunnel to apply the configuration to.
     * @param config The new configuration for this tunnel.
     * @return A future completed when the configuration of the tunnel has been updated, and the new
     * volatile configuration has been determined. This future will always be completed on the main
     * thread.
     */
    CompletionStage<Config> applyConfig(Tunnel tunnel, Config config);

    /**
     * Get the actual state of a tunnel, asynchronously.
     *
     * @param tunnel The tunnel to examine the state of.
     * @return A future completed when the state of the tunnel has been determined. This future will
     * always be completed on the main thread.
     */
    CompletionStage<State> getState(Tunnel tunnel);

    /**
     * Get statistics about traffic and errors on this tunnel, asynchronously. If the tunnel is not
     * running, the statistics object will be filled with zero values.
     *
     * @param tunnel The tunnel to retrieve statistics for.
     * @return A future completed when statistics for the tunnel are available. This future will
     * always be completed on the main thread.
     */
    CompletionStage<Statistics> getStatistics(Tunnel tunnel);

    /**
     * Set the state of a tunnel, asynchronously.
     *
     * @param tunnel The tunnel to control the state of.
     * @param state  The new state for this tunnel. Must be {@code UP}, {@code DOWN}, or
     *               {@code TOGGLE}.
     * @return A future completed when the state of the tunnel has changed, containing the new state
     * of the tunnel. This future will always be completed on the main thread.
     */
    CompletionStage<State> setState(Tunnel tunnel, State state);
}
