package com.wireguard.android.model;

import android.content.SharedPreferences;
import android.util.Log;

import com.wireguard.android.Application.ApplicationScope;
import com.wireguard.android.backend.Backend;
import com.wireguard.android.configStore.ConfigStore;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.android.util.KeyedObservableList;
import com.wireguard.android.util.SortedKeyedObservableArrayList;
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
    private final KeyedObservableList<String, Tunnel> tunnels =
            new SortedKeyedObservableArrayList<>();

    @Inject
    public TunnelManager(final Backend backend, final ConfigStore configStore,
                         final SharedPreferences preferences) {
        this.backend = backend;
        this.configStore = configStore;
        this.preferences = preferences;
    }

    private Tunnel add(final String name, final Config config) {
        final Tunnel tunnel = new Tunnel(backend, configStore, name, config);
        tunnels.add(tunnel);
        return tunnel;
    }

    private Tunnel add(final String name) {
        return add(name, null);
    }

    public CompletionStage<Tunnel> create(final String name, final Config config) {
        Log.v(TAG, "Requested create tunnel " + name + " with config\n" + config);
        if (!Tunnel.isNameValid(name))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid name"));
        if (tunnels.containsKey(name)) {
            final String message = "Tunnel " + name + " already exists";
            return CompletableFuture.failedFuture(new IllegalArgumentException(message));
        }
        return configStore.create(name, config).thenApply(savedConfig -> add(name, savedConfig));
    }

    public CompletionStage<Void> delete(final Tunnel tunnel) {
        Log.v(TAG, "Requested delete tunnel " + tunnel.getName() + " state=" + tunnel.getState());
        return backend.setState(tunnel, State.DOWN)
                .thenCompose(x -> configStore.delete(tunnel.getName()))
                .thenAccept(x -> {
                    tunnels.remove(tunnel);
                    if (tunnel.getName().equals(preferences.getString(KEY_PRIMARY_TUNNEL, null)))
                        preferences.edit().remove(KEY_PRIMARY_TUNNEL).apply();
                });
    }

    public KeyedObservableList<String, Tunnel> getTunnels() {
        return tunnels;
    }

    public void onCreate() {
        Log.v(TAG, "onCreate triggered");
        configStore.enumerate()
                .thenApply(names -> StreamSupport.stream(names)
                        .map(this::add)
                        .map(Tunnel::getStateAsync)
                        .toArray(CompletableFuture[]::new))
                .thenCompose(CompletableFuture::allOf)
                .whenComplete(ExceptionLoggers.E);
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
}
