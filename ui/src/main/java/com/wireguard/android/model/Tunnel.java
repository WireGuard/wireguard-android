/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.model;

import android.os.SystemClock;
import android.util.Pair;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.annotation.Nullable;

import com.wireguard.android.BR;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.config.Config;
import com.wireguard.crypto.Key;
import com.wireguard.util.Keyed;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import java9.util.concurrent.CompletableFuture;
import java9.util.concurrent.CompletionStage;

/**
 * Encapsulates the volatile and nonvolatile state of a WireGuard tunnel.
 */

public class Tunnel extends BaseObservable implements Keyed<String> {
    public static final int NAME_MAX_LENGTH = 15;
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_=+.-]{1,15}");

    private final TunnelManager manager;
    @Nullable private Config config;
    private String name;
    private State state;
    @Nullable private Statistics statistics;

    Tunnel(final TunnelManager manager, final String name,
           @Nullable final Config config, final State state) {
        this.manager = manager;
        this.name = name;
        this.config = config;
        this.state = state;
    }

    public static boolean isNameInvalid(final CharSequence name) {
        return !NAME_PATTERN.matcher(name).matches();
    }

    public CompletionStage<Void> delete() {
        return manager.delete(this);
    }

    @Bindable
    @Nullable
    public Config getConfig() {
        if (config == null)
            manager.getTunnelConfig(this).whenComplete(ExceptionLoggers.E);
        return config;
    }

    public CompletionStage<Config> getConfigAsync() {
        if (config == null)
            return manager.getTunnelConfig(this);
        return CompletableFuture.completedFuture(config);
    }

    @Override
    public String getKey() {
        return name;
    }

    @Bindable
    public String getName() {
        return name;
    }

    @Bindable
    public State getState() {
        return state;
    }

    public CompletionStage<State> getStateAsync() {
        return TunnelManager.getTunnelState(this);
    }

    @Bindable
    @Nullable
    public Statistics getStatistics() {
        if (statistics == null || statistics.isStale())
            TunnelManager.getTunnelStatistics(this).whenComplete(ExceptionLoggers.E);
        return statistics;
    }

    public CompletionStage<Statistics> getStatisticsAsync() {
        if (statistics == null || statistics.isStale())
            return TunnelManager.getTunnelStatistics(this);
        return CompletableFuture.completedFuture(statistics);
    }

    Config onConfigChanged(final Config config) {
        this.config = config;
        notifyPropertyChanged(BR.config);
        return config;
    }

    public String onNameChanged(final String name) {
        this.name = name;
        notifyPropertyChanged(BR.name);
        return name;
    }

    State onStateChanged(final State state) {
        if (state != State.UP)
            onStatisticsChanged(null);
        this.state = state;
        notifyPropertyChanged(BR.state);
        return state;
    }

    @Nullable
    Statistics onStatisticsChanged(@Nullable final Statistics statistics) {
        this.statistics = statistics;
        notifyPropertyChanged(BR.statistics);
        return statistics;
    }

    public CompletionStage<Config> setConfig(final Config config) {
        if (!config.equals(this.config))
            return manager.setTunnelConfig(this, config);
        return CompletableFuture.completedFuture(this.config);
    }

    public CompletionStage<String> setName(final String name) {
        if (!name.equals(this.name))
            return manager.setTunnelName(this, name);
        return CompletableFuture.completedFuture(this.name);
    }

    public CompletionStage<State> setState(final State state) {
        if (state != this.state)
            return manager.setTunnelState(this, state);
        return CompletableFuture.completedFuture(this.state);
    }

    public enum State {
        DOWN,
        TOGGLE,
        UP;

        public static State of(final boolean running) {
            return running ? UP : DOWN;
        }
    }

    public static class Statistics extends BaseObservable {
        private long lastTouched = SystemClock.elapsedRealtime();
        private final Map<Key, Pair<Long, Long>> peerBytes = new HashMap<>();

        public void add(final Key key, final long rx, final long tx) {
            peerBytes.put(key, Pair.create(rx, tx));
            lastTouched = SystemClock.elapsedRealtime();
        }

        private boolean isStale() {
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
}
