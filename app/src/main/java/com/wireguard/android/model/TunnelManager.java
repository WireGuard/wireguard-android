/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.model;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.support.annotation.Nullable;

import com.wireguard.android.Application;
import com.wireguard.android.BR;
import com.wireguard.android.R;
import com.wireguard.android.configStore.ConfigStore;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.android.util.ObservableSortedKeyedArrayList;
import com.wireguard.android.util.ObservableSortedKeyedList;
import com.wireguard.config.Config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import java9.util.Comparators;
import java9.util.concurrent.CompletableFuture;
import java9.util.concurrent.CompletionStage;
import java9.util.stream.Collectors;
import java9.util.stream.StreamSupport;

/**
 * Maintains and mediates changes to the set of available WireGuard tunnels,
 */

public final class TunnelManager extends BaseObservable {
    private static final Comparator<String> COMPARATOR = Comparators.<String>thenComparing(
            String.CASE_INSENSITIVE_ORDER, Comparators.naturalOrder());
    private static final String KEY_LAST_USED_TUNNEL = "last_used_tunnel";
    private static final String KEY_RESTORE_ON_BOOT = "restore_on_boot";
    private static final String KEY_RUNNING_TUNNELS = "enabled_configs";

    private final ConfigStore configStore;
    private final Context context = Application.get();
    private final CompletableFuture<ObservableSortedKeyedList<String, Tunnel>> completableTunnels = new CompletableFuture<>();
    private final ObservableSortedKeyedList<String, Tunnel> tunnels = new ObservableSortedKeyedArrayList<>(COMPARATOR);
    @Nullable private Tunnel lastUsedTunnel;
    private boolean haveLoaded;
    private final ArrayList<CompletableFuture<Void>> delayedLoadRestoreTunnels = new ArrayList<>();

    public TunnelManager(final ConfigStore configStore) {
        this.configStore = configStore;
    }

    private Tunnel addToList(final String name, @Nullable final Config config, final State state) {
        final Tunnel tunnel = new Tunnel(this, name, config, state);
        tunnels.add(tunnel);
        return tunnel;
    }

    public CompletionStage<Tunnel> create(final String name, @Nullable final Config config) {
        if (Tunnel.isNameInvalid(name))
            return CompletableFuture.failedFuture(new IllegalArgumentException(context.getString(R.string.tunnel_error_invalid_name)));
        if (tunnels.containsKey(name)) {
            final String message = context.getString(R.string.tunnel_error_already_exists, name);
            return CompletableFuture.failedFuture(new IllegalArgumentException(message));
        }
        return Application.getAsyncWorker().supplyAsync(() -> configStore.create(name, config))
                .thenApply(savedConfig -> addToList(name, savedConfig, State.DOWN));
    }

    CompletionStage<Void> delete(final Tunnel tunnel) {
        final State originalState = tunnel.getState();
        final boolean wasLastUsed = tunnel == lastUsedTunnel;
        // Make sure nothing touches the tunnel.
        if (wasLastUsed)
            setLastUsedTunnel(null);
        tunnels.remove(tunnel);
        return Application.getAsyncWorker().runAsync(() -> {
            if (originalState == State.UP)
                Application.getBackend().setState(tunnel, State.DOWN);
            try {
                configStore.delete(tunnel.getName());
            } catch (final Exception e) {
                if (originalState == State.UP)
                    Application.getBackend().setState(tunnel, State.UP);
                // Re-throw the exception to fail the completion.
                throw e;
            }
        }).whenComplete((x, e) -> {
            if (e == null)
                return;
            // Failure, put the tunnel back.
            tunnels.add(tunnel);
            if (wasLastUsed)
                setLastUsedTunnel(tunnel);
        });
    }

    @Bindable @Nullable
    public Tunnel getLastUsedTunnel() {
        return lastUsedTunnel;
    }

    CompletionStage<Config> getTunnelConfig(final Tunnel tunnel) {
        return Application.getAsyncWorker().supplyAsync(() -> configStore.load(tunnel.getName()))
                .thenApply(tunnel::onConfigChanged);
    }

    static CompletionStage<State> getTunnelState(final Tunnel tunnel) {
        return Application.getAsyncWorker().supplyAsync(() -> Application.getBackend().getState(tunnel))
                .thenApply(tunnel::onStateChanged);
    }

    static CompletionStage<Statistics> getTunnelStatistics(final Tunnel tunnel) {
        return Application.getAsyncWorker().supplyAsync(() -> Application.getBackend().getStatistics(tunnel))
                .thenApply(tunnel::onStatisticsChanged);
    }

    public CompletableFuture<ObservableSortedKeyedList<String, Tunnel>> getTunnels() {
        return completableTunnels;
    }

    public void onCreate() {
        Application.getAsyncWorker().supplyAsync(configStore::enumerate)
                .thenAcceptBoth(Application.getAsyncWorker().supplyAsync(() -> Application.getBackend().enumerate()), this::onTunnelsLoaded)
                .whenComplete(ExceptionLoggers.E);
    }

    @SuppressWarnings("unchecked")
    private void onTunnelsLoaded(final Iterable<String> present, final Collection<String> running) {
        for (final String name : present)
            addToList(name, null, running.contains(name) ? State.UP : State.DOWN);
        final String lastUsedName = Application.getSharedPreferences().getString(KEY_LAST_USED_TUNNEL, null);
        if (lastUsedName != null)
            setLastUsedTunnel(tunnels.get(lastUsedName));
        final CompletableFuture<Void>[] toComplete;
        synchronized (delayedLoadRestoreTunnels) {
            haveLoaded = true;
            toComplete = delayedLoadRestoreTunnels.toArray(new CompletableFuture[delayedLoadRestoreTunnels.size()]);
            delayedLoadRestoreTunnels.clear();
        }
        restoreState(true).whenComplete((v, t) -> {
            for (final CompletableFuture<Void> f : toComplete) {
                if (t == null)
                    f.complete(v);
                else
                    f.completeExceptionally(t);
            }
        });

        completableTunnels.complete(tunnels);
    }

    public void refreshTunnelStates() {
        Application.getAsyncWorker().supplyAsync(() -> Application.getBackend().enumerate())
                .thenAccept(running -> {
                    for (final Tunnel tunnel : tunnels)
                        tunnel.onStateChanged(running.contains(tunnel.getName()) ? State.UP : State.DOWN);
                })
                .whenComplete(ExceptionLoggers.E);
    }

    public CompletionStage<Void> restoreState(final boolean force) {
        if (!force && !Application.getSharedPreferences().getBoolean(KEY_RESTORE_ON_BOOT, false))
            return CompletableFuture.completedFuture(null);
        synchronized (delayedLoadRestoreTunnels) {
            if (!haveLoaded) {
                final CompletableFuture<Void> f = new CompletableFuture<>();
                delayedLoadRestoreTunnels.add(f);
                return f;
            }
        }
        final Set<String> previouslyRunning = Application.getSharedPreferences().getStringSet(KEY_RUNNING_TUNNELS, null);
        if (previouslyRunning == null)
            return CompletableFuture.completedFuture(null);
        return CompletableFuture.allOf(StreamSupport.stream(tunnels)
                .filter(tunnel -> previouslyRunning.contains(tunnel.getName()))
                .map(tunnel -> setTunnelState(tunnel, State.UP))
                .toArray(CompletableFuture[]::new));
    }

    public void saveState() {
        final Set<String> runningTunnels = StreamSupport.stream(tunnels)
                .filter(tunnel -> tunnel.getState() == State.UP)
                .map(Tunnel::getName)
                .collect(Collectors.toUnmodifiableSet());
        Application.getSharedPreferences().edit().putStringSet(KEY_RUNNING_TUNNELS, runningTunnels).apply();
    }

    private void setLastUsedTunnel(@Nullable final Tunnel tunnel) {
        if (tunnel == lastUsedTunnel)
            return;
        lastUsedTunnel = tunnel;
        notifyPropertyChanged(BR.lastUsedTunnel);
        if (tunnel != null)
            Application.getSharedPreferences().edit().putString(KEY_LAST_USED_TUNNEL, tunnel.getName()).apply();
        else
            Application.getSharedPreferences().edit().remove(KEY_LAST_USED_TUNNEL).apply();
    }

    CompletionStage<Config> setTunnelConfig(final Tunnel tunnel, final Config config) {
        return Application.getAsyncWorker().supplyAsync(() -> {
            final Config appliedConfig = Application.getBackend().applyConfig(tunnel, config);
            return configStore.save(tunnel.getName(), appliedConfig);
        }).thenApply(tunnel::onConfigChanged);
    }

    CompletionStage<String> setTunnelName(final Tunnel tunnel, final String name) {
        if (Tunnel.isNameInvalid(name))
            return CompletableFuture.failedFuture(new IllegalArgumentException(context.getString(R.string.tunnel_error_invalid_name)));
        if (tunnels.containsKey(name)) {
            final String message = context.getString(R.string.tunnel_error_already_exists, name);
            return CompletableFuture.failedFuture(new IllegalArgumentException(message));
        }
        final State originalState = tunnel.getState();
        final boolean wasLastUsed = tunnel == lastUsedTunnel;
        // Make sure nothing touches the tunnel.
        if (wasLastUsed)
            setLastUsedTunnel(null);
        tunnels.remove(tunnel);
        return Application.getAsyncWorker().supplyAsync(() -> {
            if (originalState == State.UP)
                Application.getBackend().setState(tunnel, State.DOWN);
            configStore.rename(tunnel.getName(), name);
            final String newName = tunnel.onNameChanged(name);
            if (originalState == State.UP)
                Application.getBackend().setState(tunnel, State.UP);
            return newName;
        }).whenComplete((newName, e) -> {
            // On failure, we don't know what state the tunnel might be in. Fix that.
            if (e != null)
                getTunnelState(tunnel);
            // Add the tunnel back to the manager, under whatever name it thinks it has.
            tunnels.add(tunnel);
            if (wasLastUsed)
                setLastUsedTunnel(tunnel);
        });
    }

    CompletionStage<State> setTunnelState(final Tunnel tunnel, final State state) {
        // Ensure the configuration is loaded before trying to use it.
        return tunnel.getConfigAsync().thenCompose(x ->
                Application.getAsyncWorker().supplyAsync(() -> Application.getBackend().setState(tunnel, state))
        ).whenComplete((newState, e) -> {
            // Ensure onStateChanged is always called (failure or not), and with the correct state.
            tunnel.onStateChanged(e == null ? newState : tunnel.getState());
            if (e == null && newState == State.UP)
                setLastUsedTunnel(tunnel);
            saveState();
        });
    }

    public static final class IntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(final Context context, @Nullable final Intent intent) {
            final TunnelManager manager = Application.getTunnelManager();
            if (intent == null)
                return;
            final String action = intent.getAction();
            if (action == null)
                return;

            if ("com.wireguard.android.action.REFRESH_TUNNEL_STATES".equals(action)) {
                manager.refreshTunnelStates();
                return;
            }

            /* We disable the below, for now, as the security model of allowing this
             * might take a bit more consideration.
             */
            if (true)
                return;

            final State state;
            if ("com.wireguard.android.action.SET_TUNNEL_UP".equals(action))
                state = State.UP;
            else if ("com.wireguard.android.action.SET_TUNNEL_DOWN".equals(action))
                state = State.DOWN;
            else
                return;

            final String tunnelName = intent.getStringExtra("tunnel");
            if (tunnelName == null)
                return;
            manager.getTunnels().thenAccept(tunnels -> {
                final Tunnel tunnel = tunnels.get(tunnelName);
                if (tunnel == null)
                    return;
                manager.setTunnelState(tunnel, state);
            });
        }
    }
}
