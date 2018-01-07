package com.wireguard.android.model;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.wireguard.android.BR;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.android.util.Keyed;
import com.wireguard.config.Config;

import java.util.regex.Pattern;

import java9.util.concurrent.CompletableFuture;
import java9.util.concurrent.CompletionStage;

/**
 * Encapsulates the volatile and nonvolatile state of a WireGuard tunnel.
 */

public class Tunnel extends BaseObservable implements Keyed<String> {
    public static final int NAME_MAX_LENGTH = 16;
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_=+.-]{1,16}");

    private final TunnelManager manager;
    private final String name;
    private Config config;
    private State state;
    private Statistics statistics;

    Tunnel(@NonNull final TunnelManager manager, @NonNull final String name,
           @Nullable final Config config, @NonNull final State state) {
        this.manager = manager;
        this.name = name;
        this.config = config;
        this.state = state;
    }

    public static boolean isNameValid(@NonNull final CharSequence name) {
        return NAME_PATTERN.matcher(name).matches();
    }

    public CompletionStage<Void> delete() {
        return manager.delete(this);
    }

    @Bindable
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

    public String getName() {
        return name;
    }

    @Bindable
    public State getState() {
        if (state == State.UNKNOWN)
            manager.getTunnelState(this).whenComplete(ExceptionLoggers.E);
        return state;
    }

    public CompletionStage<State> getStateAsync() {
        if (state == State.UNKNOWN)
            return manager.getTunnelState(this);
        return CompletableFuture.completedFuture(state);
    }

    @Bindable
    public Statistics getStatistics() {
        // FIXME: Check age of statistics.
        if (statistics == null)
            manager.getTunnelStatistics(this).whenComplete(ExceptionLoggers.E);
        return statistics;
    }

    public CompletionStage<Statistics> getStatisticsAsync() {
        // FIXME: Check age of statistics.
        if (statistics == null)
            return manager.getTunnelStatistics(this);
        return CompletableFuture.completedFuture(statistics);
    }

    void onConfigChanged(final Config config) {
        this.config = config;
        notifyPropertyChanged(BR.config);
    }

    void onStateChanged(final State state) {
        if (state != State.UP)
            onStatisticsChanged(null);
        this.state = state;
        notifyPropertyChanged(BR.state);
    }

    void onStatisticsChanged(final Statistics statistics) {
        this.statistics = statistics;
        notifyPropertyChanged(BR.statistics);
    }

    public CompletionStage<Config> setConfig(@NonNull final Config config) {
        if (!config.equals(this.config))
            return manager.setTunnelConfig(this, config);
        return CompletableFuture.completedFuture(this.config);
    }

    public CompletionStage<State> setState(@NonNull final State state) {
        if (state != this.state)
            return manager.setTunnelState(this, state);
        return CompletableFuture.completedFuture(this.state);
    }

    public enum State {
        DOWN,
        TOGGLE,
        UNKNOWN,
        UP
    }

    public static class Statistics extends BaseObservable {
    }
}
