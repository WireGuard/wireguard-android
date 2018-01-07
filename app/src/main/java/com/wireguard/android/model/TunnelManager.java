package com.wireguard.android.model;

import android.content.SharedPreferences;

import com.wireguard.android.Application.ApplicationScope;
import com.wireguard.android.backend.Backend;
import com.wireguard.android.configStore.ConfigStore;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.android.util.ObservableKeyedList;
import com.wireguard.android.util.ObservableSortedKeyedArrayList;
import com.wireguard.config.Config;

import java.util.Collections;
import java.util.Set;

import javax.inject.Inject;

import java9.util.concurrent.CompletableFuture;
import java9.util.concurrent.CompletionStage;
import java9.util.stream.Collectors;
import java9.util.stream.StreamSupport;

/**
 * Maintains and mediates changes to the set of available WireGuard tunnels,
 */

@ApplicationScope
public final class TunnelManager {
    public static final String KEY_PRIMARY_TUNNEL = "primary_config";
    public static final String KEY_SELECTED_TUNNEL = "selected_tunnel";
    private static final String KEY_RESTORE_ON_BOOT = "restore_on_boot";
    private static final String KEY_RUNNING_TUNNELS = "enabled_configs";
    private static final String TAG = TunnelManager.class.getSimpleName();

    private final Backend backend;
    private final ConfigStore configStore;
    private final SharedPreferences preferences;
    private final ObservableKeyedList<String, Tunnel> tunnels =
            new ObservableSortedKeyedArrayList<>();

    @Inject
    public TunnelManager(final Backend backend, final ConfigStore configStore,
                         final SharedPreferences preferences) {
        this.backend = backend;
        this.configStore = configStore;
        this.preferences = preferences;
    }

    private Tunnel add(final String name, final Config config, final State state) {
        final Tunnel tunnel = new Tunnel(this, name, config, state);
        tunnels.add(tunnel);
        return tunnel;
    }

    public CompletionStage<Tunnel> create(final String name, final Config config) {
        if (!Tunnel.isNameValid(name))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid name"));
        if (tunnels.containsKey(name)) {
            final String message = "Tunnel " + name + " already exists";
            return CompletableFuture.failedFuture(new IllegalArgumentException(message));
        }
        return configStore.create(name, config).thenApply(cfg -> add(name, cfg, State.DOWN));
    }

    CompletionStage<Void> delete(final Tunnel tunnel) {
        return setTunnelState(tunnel, State.DOWN)
                .thenCompose(x -> configStore.delete(tunnel.getName()))
                .thenAccept(x -> remove(tunnel));
    }

    CompletionStage<Config> getTunnelConfig(final Tunnel tunnel) {
        return configStore.load(tunnel.getName()).thenApply(tunnel::onConfigChanged);
    }

    CompletionStage<State> getTunnelState(final Tunnel tunnel) {
        return backend.getState(tunnel).thenApply(tunnel::onStateChanged);
    }

    CompletionStage<Statistics> getTunnelStatistics(final Tunnel tunnel) {
        return backend.getStatistics(tunnel).thenApply(tunnel::onStatisticsChanged);
    }

    public ObservableKeyedList<String, Tunnel> getTunnels() {
        return tunnels;
    }

    public void onCreate() {
        configStore.enumerate().thenAcceptBoth(backend.enumerate(), (names, running) -> {
            for (final String name : names)
                add(name, null, running.contains(name) ? State.UP : State.DOWN);
        }).whenComplete(ExceptionLoggers.E);
    }

    private void remove(final Tunnel tunnel) {
        if (tunnel.getName().equals(preferences.getString(KEY_PRIMARY_TUNNEL, null)))
            preferences.edit().remove(KEY_PRIMARY_TUNNEL).apply();
        tunnels.remove(tunnel);
    }

    public CompletionStage<Void> restoreState() {
        if (!preferences.getBoolean(KEY_RESTORE_ON_BOOT, false))
            return CompletableFuture.completedFuture(null);
        final Set<String> tunnelsToEnable =
                preferences.getStringSet(KEY_RUNNING_TUNNELS, Collections.emptySet());
        final CompletableFuture[] futures = StreamSupport.stream(tunnelsToEnable)
                .map(tunnels::get)
                .map(tunnel -> tunnel.setState(State.UP))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public CompletionStage<Void> saveState() {
        final Set<String> runningTunnels = StreamSupport.stream(tunnels)
                .filter(tunnel -> tunnel.getState() == State.UP)
                .map(Tunnel::getName)
                .collect(Collectors.toUnmodifiableSet());
        preferences.edit().putStringSet(KEY_RUNNING_TUNNELS, runningTunnels).apply();
        return CompletableFuture.completedFuture(null);
    }

    CompletionStage<Config> setTunnelConfig(final Tunnel tunnel, final Config config) {
        return backend.applyConfig(tunnel, config)
                .thenCompose(cfg -> configStore.save(tunnel.getName(), cfg))
                .thenApply(tunnel::onConfigChanged);
    }

    CompletionStage<State> setTunnelState(final Tunnel tunnel, final State state) {
        return backend.setState(tunnel, state)
                .thenApply(tunnel::onStateChanged);
    }
}
