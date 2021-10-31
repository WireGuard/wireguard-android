/*
 * Copyright © 2017-2021 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import com.wireguard.util.NonNullForAll;

import java.time.LocalDateTime;

/**
 * Class representing transfer statistics and last handshake time for a
 * {@link com.wireguard.config.Peer} instance.
 */
@NonNullForAll
public class PeerStatistics {
    private final long rx;
    private final long tx;
    private final LocalDateTime lastHandshakeTime;

    /**
     * Create a peer statistics data object.
     *
     * @param rx                The received traffic for the {@link com.wireguard.config.Peer}.
     *                          This value is in bytes
     * @param tx                The transmitted traffic for the {@link com.wireguard.config.Peer}.
     *                          This value is in bytes.
     * @param lastHandshakeTime The last handshake time for the {@link com.wireguard.config.Peer}.
     *                          This value is in LocalDateTime.
     */
    PeerStatistics(long rx, long tx, LocalDateTime lastHandshakeTime) {
        this.rx = rx;
        this.tx = tx;
        this.lastHandshakeTime = lastHandshakeTime;
    }

    /**
     * Get the received traffic (in bytes) for the {@link com.wireguard.config.Peer}
     *
     * @return a long representing the number of bytes received by this peer.
     */
    public long getRx() {
        return rx;
    }

    /**
     * Get the transmitted traffic (in bytes) for the {@link com.wireguard.config.Peer}
     *
     * @return a long representing the number of bytes transmitted by this peer.
     */
    public long getTx() {
        return tx;
    }

    /**
     * Get last handshake time for the {@link com.wireguard.config.Peer}
     *
     * @return a LocalDateTime.
     */
    public LocalDateTime getLastHandshakeTime() {
        return lastHandshakeTime;
    }
}
