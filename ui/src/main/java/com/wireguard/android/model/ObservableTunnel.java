/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.model;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.annotation.Nullable;

import com.wireguard.android.BR;
import com.wireguard.android.backend.Statistics;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.config.Config;
import com.wireguard.util.Keyed;

import java9.util.concurrent.CompletableFuture;
import java9.util.concurrent.CompletionStage;

/**
 * Encapsulates the volatile and nonvolatile state of a WireGuard tunnel.
 */

public class ObservableTunnel extends BaseObservable implements Keyed<String>, Tunnel {
    private final TunnelManager manager;
    @Nullable private Config config;
    private State state;
    private String name;
    @Nullable private Statistics statistics;

    ObservableTunnel(final TunnelManager manager, final String name,
           @Nullable final Config config, final State state) {
        this.name = name;
        this.manager = manager;
        this.config = config;
        this.state = state;
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

    @Override
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

    String onNameChanged(final String name) {
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

    @Override
    public void onStateChange(final State newState) {
        onStateChanged(newState);
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
}
