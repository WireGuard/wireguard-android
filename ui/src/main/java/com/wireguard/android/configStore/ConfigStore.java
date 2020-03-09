/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.configStore;

import com.wireguard.config.Config;

import java.util.Set;

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
     * @return The configuration that was actually saved to persistent storage.
     */
    Config create(final String name, final Config config) throws Exception;

    /**
     * Delete a persistent tunnel.
     *
     * @param name The name of the tunnel to delete.
     */
    void delete(final String name) throws Exception;

    /**
     * Enumerate the names of tunnels present in persistent storage.
     *
     * @return The set of present tunnel names.
     */
    Set<String> enumerate();

    /**
     * Load the configuration for the tunnel given by {@code name}.
     *
     * @param name The identifier for the configuration in persistent storage (i.e. the name of the
     *             tunnel).
     * @return An in-memory representation of the configuration loaded from persistent storage.
     */
    Config load(final String name) throws Exception;

    /**
     * Rename the configuration for the tunnel given by {@code name}.
     *
     * @param name        The identifier for the existing configuration in persistent storage.
     * @param replacement The new identifier for the configuration in persistent storage.
     */
    void rename(String name, String replacement) throws Exception;

    /**
     * Save the configuration for an existing tunnel given by {@code name}.
     *
     * @param name   The identifier for the configuration in persistent storage (i.e. the name of
     *               the tunnel).
     * @param config An updated configuration object for the tunnel.
     * @return The configuration that was actually saved to persistent storage.
     */
    Config save(final String name, final Config config) throws Exception;
}
