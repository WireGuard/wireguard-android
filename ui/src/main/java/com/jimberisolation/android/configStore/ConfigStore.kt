/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.jimberisolation.android.configStore

import com.jimberisolation.android.model.ObservableTunnel
import com.jimberisolation.config.Config

/**
 * Interface for persistent storage providers for WireGuard configurations.
 */
interface ConfigStore {
    /**
     * Create a persistent tunnel, which must have a unique name within the persistent storage
     * medium.
     *
     * @param name   The name of the tunnel to create.
     * @param config Configuration for the new tunnel.
     * @return The configuration that was actually saved to persistent storage.
     */
    @Throws(Exception::class)
    fun create(createTunnelData: CreateTunnelData, config: Config): Config

    /**
     * Delete a persistent tunnel.
     *
     * @param name The name of the tunnel to delete.
     */
    @Throws(Exception::class)
    fun delete(tunnel: ObservableTunnel)

    /**
     * Enumerate the names of tunnels present in persistent storage.
     *
     * @return The set of present tunnel names.
     */
    fun enumerate(): Set<TunnelInfo>

    /**
     * Load the configuration for the tunnel given by `name`.
     *
     * @param name The identifier for the configuration in persistent storage (i.e. the name of the
     * tunnel).
     * @return An in-memory representation of the configuration loaded from persistent storage.
     */
    @Throws(Exception::class)
    fun load(tunnel: ObservableTunnel): Config

    /**
     * Rename the configuration for the tunnel given by `name`.
     *
     * @param name        The identifier for the existing configuration in persistent storage.
     * @param replacement The new identifier for the configuration in persistent storage.
     */
    @Throws(Exception::class)
    fun rename(tunnel: ObservableTunnel, replacement: String)

    /**
     * Save the configuration for an existing tunnel given by `name`.
     *
     * @param name   The identifier for the configuration in persistent storage (i.e. the name of
     * the tunnel).
     * @param config An updated configuration object for the tunnel.
     * @return The configuration that was actually saved to persistent storage.
     */
    @Throws(Exception::class)
    fun save(tunnel: ObservableTunnel, config: Config): Config
}
