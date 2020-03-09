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

@NonNullForAll
public class Statistics {
    private long lastTouched = SystemClock.elapsedRealtime();
    private final Map<Key, Pair<Long, Long>> peerBytes = new HashMap<>();

    Statistics() { }

    void add(final Key key, final long rx, final long tx) {
        peerBytes.put(key, Pair.create(rx, tx));
        lastTouched = SystemClock.elapsedRealtime();
    }

    public boolean isStale() {
        return SystemClock.elapsedRealtime() - lastTouched > 900;
    }

    public Key[] peers() {
        return peerBytes.keySet().toArray(new Key[0]);
    }

    public long peerRx(final Key peer) {
        if (!peerBytes.containsKey(peer))
            return 0;
        return peerBytes.get(peer).first;
    }

    public long peerTx(final Key peer) {
        if (!peerBytes.containsKey(peer))
            return 0;
        return peerBytes.get(peer).second;
    }

    public long totalRx() {
        long rx = 0;
        for (final Pair<Long, Long> val : peerBytes.values()) {
            rx += val.first;
        }
        return rx;
    }

    public long totalTx() {
        long tx = 0;
        for (final Pair<Long, Long> val : peerBytes.values()) {
            tx += val.second;
        }
        return tx;
    }
}
