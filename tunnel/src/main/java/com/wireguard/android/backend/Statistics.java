/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import android.os.SystemClock;

import com.wireguard.crypto.Key;
import com.wireguard.util.NonNullForAll;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.Nullable;

/**
 * Class representing transfer statistics for a {@link Tunnel} instance.
 */
@NonNullForAll
public class Statistics {
    public record PeerStats(long rxBytes, long txBytes, long latestHandshakeEpochMillis) { }
    private final Map<Key, PeerStats> stats = new HashMap<>();
    private long lastTouched = SystemClock.elapsedRealtime();

    Statistics() {
    }

    /**
     * Add a peer and its current stats to the internal map.
     *
     * @param key               A WireGuard public key bound to a particular peer
     * @param rxBytes           The received traffic for the {@link com.wireguard.config.Peer} referenced by
     *                          the provided {@link Key}. This value is in bytes
     * @param txBytes           The transmitted traffic for the {@link com.wireguard.config.Peer} referenced by
     *                          the provided {@link Key}. This value is in bytes.
     * @param latestHandshake   The timestamp of the latest handshake for the {@link com.wireguard.config.Peer}
     *                          referenced by the provided {@link Key}. The value is in epoch milliseconds.
     */
    void add(final Key key, final long rxBytes, final long txBytes, final long latestHandshake) {
        stats.put(key, new PeerStats(rxBytes, txBytes, latestHandshake));
        lastTouched = SystemClock.elapsedRealtime();
    }

    /**
     * Check if the statistics are stale, indicating the need for the {@link Backend} to update them.
     *
     * @return boolean indicating if the current statistics instance has stale values.
     */
    public boolean isStale() {
        return SystemClock.elapsedRealtime() - lastTouched > 900;
    }

    /**
     * Get the statistics for the {@link com.wireguard.config.Peer} referenced by the provided {@link Key}
     *
     * @param peer A {@link Key} representing a {@link com.wireguard.config.Peer}.
     * @return a {@link PeerStats} representing various statistics about this peer.
     */
    @Nullable
    public PeerStats peer(final Key peer) {
        return stats.get(peer);
    }

    /**
     * Get the list of peers being tracked by this instance.
     *
     * @return An array of {@link Key} instances representing WireGuard
     * {@link com.wireguard.config.Peer}s
     */
    public Key[] peers() {
        return stats.keySet().toArray(new Key[0]);
    }

    /**
     * Get the total received traffic by all the peers being tracked by this instance
     *
     * @return a long representing the number of bytes received by the peers being tracked.
     */
    public long totalRx() {
        long rx = 0;
        for (final PeerStats val : stats.values()) {
            rx += val.rxBytes;
        }
        return rx;
    }

    /**
     * Get the total transmitted traffic by all the peers being tracked by this instance
     *
     * @return a long representing the number of bytes transmitted by the peers being tracked.
     */
    public long totalTx() {
        long tx = 0;
        for (final PeerStats val : stats.values()) {
            tx += val.txBytes;
        }
        return tx;
    }
}
