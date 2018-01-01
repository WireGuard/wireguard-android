package com.wireguard.android.configStore;

import com.wireguard.config.Config;

import java.util.Set;

import java9.util.concurrent.CompletionStage;

/**
 * Interface for persistent storage providers for WireGuard configurations.
 */

public interface ConfigStore {
    /**
     * Create a persistent tunnel, which must have a unique name within the persistent storage
     * medium.
     *
     * @param name   The name of the tunnel to create.
     * @param config Configuration for the new tunnel.
     * @return A future completed when the tunnel and its configuration have been saved to
     * persistent storage. This future encapsulates the configuration that was actually saved to
     * persistent storage. This future will always be completed on the main thread.
     */
    CompletionStage<Config> create(final String name, final Config config);

    /**
     * Delete a persistent tunnel.
     *
     * @param name The name of the tunnel to delete.
     * @return A future completed when the tunnel and its configuration have been deleted. This
     * future will always be completed on the main thread.
     */
    CompletionStage<Void> delete(final String name);

    /**
     * Enumerate the names of tunnels present in persistent storage.
     *
     * @return A future completed when the set of present tunnel names is available. This future
     * will always be completed on the main thread.
     */
    CompletionStage<Set<String>> enumerate();

    /**
     * Load the configuration for the tunnel given by {@code name}.
     *
     * @param name The identifier for the configuration in persistent storage (i.e. the name of the
     *             tunnel).
     * @return A future completed when an in-memory representation of the configuration is
     * available. This future encapsulates the configuration loaded from persistent storage. This
     * future will always be completed on the main thread.
     */
    CompletionStage<Config> load(final String name);

    /**
     * Save the configuration for an existing tunnel given by {@code name}.
     *
     * @param name   The identifier for the configuration in persistent storage (i.e. the name of
     *               the tunnel).
     * @param config An updated configuration object for the tunnel.
     * @return A future completed when the configuration has been saved to persistent storage. This
     * future encapsulates the configuration that was actually saved to persistent storage. This
     * future will always be completed on the main thread.
     */
    CompletionStage<Config> save(final String name, final Config config);
}
