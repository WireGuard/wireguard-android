/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import android.os.SystemClock;
import android.util.Pair;

import com.wireguard.crypto.Key;
import com.wireguard.util.NonNullForAll;

import java.util.HashMap;
import java.util.Map;

/**
 * Class representing transfer statistics for a {@link Tunnel} instance.
 */
@NonNullForAll
public class Statistics {
    private final Map<Key, Pair<Long, Long>> peerBytes = new HashMap<>();
    private long lastTouched = SystemClock.elapsedRealtime();

    Statistics() {
    }

    /**
     * Add a peer and its current data usage to the internal map.
     *
     * @param key A WireGuard public key bound to a particular peer
     * @param rx  The received traffic for the {@link com.wireguard.config.Peer} referenced by
     *            the provided {@link Key}. This value is in bytes
     * @param tx  The transmitted traffic for the {@link com.wireguard.config.Peer} referenced by
     *            the provided {@link Key}. This value is in bytes.
     */
    void add(final Key key, final long rx, final long tx) {
        peerBytes.put(key, Pair.create(rx, tx));
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
     * Get the received traffic (in bytes) for the {@link com.wireguard.config.Peer} referenced by
     * the provided {@link Key}
     *
     * @param peer A {@link Key} representing a {@link com.wireguard.config.Peer}.
     * @return a long representing the number of bytes received by this peer.
     */
    public long peerRx(final Key peer) {
        final Pair<Long, Long> rxTx = peerBytes.get(peer);
        if (rxTx == null)
            return 0;
        return rxTx.first;
    }

    /**
     * Get the transmitted traffic (in bytes) for the {@link com.wireguard.config.Peer} referenced by
     * the provided {@link Key}
     *
     * @param peer A {@link Key} representing a {@link com.wireguard.config.Peer}.
     * @return a long representing the number of bytes transmitted by this peer.
     */
    public long peerTx(final Key peer) {
        final Pair<Long, Long> rxTx = peerBytes.get(peer);
        if (rxTx == null)
            return 0;
        return rxTx.second;
    }

    /**
     * Get the list of peers being tracked by this instance.
     *
     * @return An array of {@link Key} instances representing WireGuard
     * {@link com.wireguard.config.Peer}s
     */
    public Key[] peers() {
        return peerBytes.keySet().toArray(new Key[0]);
    }

    /**
     * Get the total received traffic by all the peers being tracked by this instance
     *
     * @return a long representing the number of bytes received by the peers being tracked.
     */
    public long totalRx() {
        long rx = 0;
        for (final Pair<Long, Long> val : peerBytes.values()) {
            rx += val.first;
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
        for (final Pair<Long, Long> val : peerBytes.values()) {
            tx += val.second;
        }
        return tx;
    }
}
