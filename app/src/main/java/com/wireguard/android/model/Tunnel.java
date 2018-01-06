package com.wireguard.android.model;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.wireguard.android.BR;
import com.wireguard.android.backend.Backend;
import com.wireguard.android.configStore.ConfigStore;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.android.util.Keyed;
import com.wireguard.config.Config;

import java.util.Objects;
import java.util.regex.Pattern;

import java9.util.concurrent.CompletableFuture;
import java9.util.concurrent.CompletionStage;

/**
 * Encapsulates the volatile and nonvolatile state of a WireGuard tunnel.
 */

public class Tunnel extends BaseObservable implements Keyed<String> {
    public static final int NAME_MAX_LENGTH = 16;
    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_=+.-]{1,16}");
    private static final String TAG = Tunnel.class.getSimpleName();

    private final Backend backend;
    private final ConfigStore configStore;
    private final String name;
    private Config config;
    private State state;
    private Statistics statistics;

    Tunnel(@NonNull final Backend backend, @NonNull final ConfigStore configStore,
           @NonNull final String name, @Nullable final Config config, @NonNull final State state) {
        this.backend = backend;
        this.configStore = configStore;
        this.name = name;
        this.config = config;
        this.state = state;
    }

    public static boolean isNameValid(final CharSequence name) {
        return name != null && NAME_PATTERN.matcher(name).matches();
    }

    @Bindable
    public Config getConfig() {
        if (config == null)
            getConfigAsync().whenComplete(ExceptionLoggers.D);
        return config;
    }

    public CompletionStage<Config> getConfigAsync() {
        if (config == null)
            return configStore.load(name).thenApply(this::setConfigInternal);
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
        if (state == State.UNKNOWN)
            getStateAsync().whenComplete(ExceptionLoggers.D);
        return state;
    }

    public CompletionStage<State> getStateAsync() {
        if (state == State.UNKNOWN)
            return backend.getState(this).thenApply(this::setStateInternal);
        return CompletableFuture.completedFuture(state);
    }

    @Bindable
    public Statistics getStatistics() {
        // FIXME: Check age of statistics.
        if (statistics == null)
            getStatisticsAsync().whenComplete(ExceptionLoggers.D);
        return statistics;
    }

    public CompletionStage<Statistics> getStatisticsAsync() {
        // FIXME: Check age of statistics.
        if (statistics == null)
            return backend.getStatistics(this).thenApply(this::setStatisticsInternal);
        return CompletableFuture.completedFuture(statistics);
    }

    private void onStateChanged(final State oldState, final State newState) {
        if (newState != State.UP)
            setStatisticsInternal(null);
    }

    public CompletionStage<Config> setConfig(@NonNull final Config config) {
        if (!config.equals(this.config)) {
            return backend.applyConfig(this, config)
                    .thenCompose(cfg -> configStore.save(name, cfg))
                    .thenApply(this::setConfigInternal);
        }
        return CompletableFuture.completedFuture(this.config);
    }

    private Config setConfigInternal(final Config config) {
        if (Objects.equals(this.config, config))
            return config;
        this.config = config;
        notifyPropertyChanged(BR.config);
        return config;
    }

    public CompletionStage<State> setState(@NonNull final State state) {
        if (state != this.state)
            return backend.setState(this, state)
                    .thenApply(this::setStateInternal);
        return CompletableFuture.completedFuture(this.state);
    }

    private State setStateInternal(final State state) {
        if (Objects.equals(this.state, state))
            return state;
        onStateChanged(this.state, state);
        this.state = state;
        notifyPropertyChanged(BR.state);
        return state;
    }

    private Statistics setStatisticsInternal(final Statistics statistics) {
        if (Objects.equals(this.statistics, statistics))
            return statistics;
        this.statistics = statistics;
        notifyPropertyChanged(BR.statistics);
        return statistics;
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
