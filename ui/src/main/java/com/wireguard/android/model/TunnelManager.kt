/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.model

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.wireguard.android.Application.Companion.get
import com.wireguard.android.Application.Companion.getAsyncWorker
import com.wireguard.android.Application.Companion.getBackend
import com.wireguard.android.Application.Companion.getSharedPreferences
import com.wireguard.android.Application.Companion.getTunnelManager
import com.wireguard.android.BR
import com.wireguard.android.R
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.configStore.ConfigStore
import com.wireguard.android.util.ExceptionLoggers
import com.wireguard.android.databinding.ObservableSortedKeyedArrayList
import com.wireguard.config.Config
import java9.util.concurrent.CompletableFuture
import java9.util.concurrent.CompletionStage
import java.util.ArrayList

/**
 * Maintains and mediates changes to the set of available WireGuard tunnels,
 */
class TunnelManager(private val configStore: ConfigStore) : BaseObservable() {
    val tunnels = CompletableFuture<ObservableSortedKeyedArrayList<String, ObservableTunnel>>()
    private val context: Context = get()
    private val delayedLoadRestoreTunnels = ArrayList<CompletableFuture<Void>>()
    private val tunnelMap: ObservableSortedKeyedArrayList<String, ObservableTunnel> = ObservableSortedKeyedArrayList(TunnelComparator)
    private var haveLoaded = false

    private fun addToList(name: String, config: Config?, state: Tunnel.State): ObservableTunnel? {
        val tunnel = ObservableTunnel(this, name, config, state)
        tunnelMap.add(tunnel)
        return tunnel
    }

    fun create(name: String, config: Config?): CompletionStage<ObservableTunnel> {
        if (Tunnel.isNameInvalid(name))
            return CompletableFuture.failedFuture(IllegalArgumentException(context.getString(R.string.tunnel_error_invalid_name)))
        if (tunnelMap.containsKey(name))
            return CompletableFuture.failedFuture(IllegalArgumentException(context.getString(R.string.tunnel_error_already_exists, name)))
        return getAsyncWorker().supplyAsync { configStore.create(name, config!!) }.thenApply { addToList(name, it, Tunnel.State.DOWN) }
    }

    fun delete(tunnel: ObservableTunnel): CompletionStage<Void> {
        val originalState = tunnel.state
        val wasLastUsed = tunnel == lastUsedTunnel
        // Make sure nothing touches the tunnel.
        if (wasLastUsed)
            lastUsedTunnel = null
        tunnelMap.remove(tunnel)
        return getAsyncWorker().runAsync {
            if (originalState == Tunnel.State.UP)
                getBackend().setState(tunnel, Tunnel.State.DOWN, null)
            try {
                configStore.delete(tunnel.name)
            } catch (e: Exception) {
                if (originalState == Tunnel.State.UP)
                    getBackend().setState(tunnel, Tunnel.State.UP, tunnel.config)
                throw e
            }
        }.whenComplete { _, e ->
            if (e == null)
                return@whenComplete
            // Failure, put the tunnel back.
            tunnelMap.add(tunnel)
            if (wasLastUsed)
                lastUsedTunnel = tunnel
        }
    }

    @get:Bindable
    @SuppressLint("ApplySharedPref")
    var lastUsedTunnel: ObservableTunnel? = null
        private set(value) {
            if (value == field) return
            field = value
            notifyPropertyChanged(BR.lastUsedTunnel)
            if (value != null)
                getSharedPreferences().edit().putString(KEY_LAST_USED_TUNNEL, value.name).commit()
            else
                getSharedPreferences().edit().remove(KEY_LAST_USED_TUNNEL).commit()
        }

    fun getTunnelConfig(tunnel: ObservableTunnel): CompletionStage<Config> = getAsyncWorker()
            .supplyAsync { configStore.load(tunnel.name) }.thenApply(tunnel::onConfigChanged)


    fun onCreate() {
        getAsyncWorker().supplyAsync { configStore.enumerate() }
                .thenAcceptBoth(getAsyncWorker().supplyAsync { getBackend().runningTunnelNames }, this::onTunnelsLoaded)
                .whenComplete(ExceptionLoggers.E)
    }

    private fun onTunnelsLoaded(present: Iterable<String>, running: Collection<String>) {
        for (name in present)
            addToList(name, null, if (running.contains(name)) Tunnel.State.UP else Tunnel.State.DOWN)
        val lastUsedName = getSharedPreferences().getString(KEY_LAST_USED_TUNNEL, null)
        if (lastUsedName != null)
            lastUsedTunnel = tunnelMap[lastUsedName]
        var toComplete: Array<CompletableFuture<Void>>
        synchronized(delayedLoadRestoreTunnels) {
            haveLoaded = true
            toComplete = delayedLoadRestoreTunnels.toTypedArray()
            delayedLoadRestoreTunnels.clear()
        }
        restoreState(true).whenComplete { v: Void?, t: Throwable? ->
            for (f in toComplete) {
                if (t == null)
                    f.complete(v)
                else
                    f.completeExceptionally(t)
            }
        }
        tunnels.complete(tunnelMap)
    }

    fun refreshTunnelStates() {
        getAsyncWorker().supplyAsync { getBackend().runningTunnelNames }
                .thenAccept { running: Set<String> -> for (tunnel in tunnelMap) tunnel.onStateChanged(if (running.contains(tunnel.name)) Tunnel.State.UP else Tunnel.State.DOWN) }
                .whenComplete(ExceptionLoggers.E)
    }

    fun restoreState(force: Boolean): CompletionStage<Void> {
        if (!force && !getSharedPreferences().getBoolean(KEY_RESTORE_ON_BOOT, false))
            return CompletableFuture.completedFuture(null)
        synchronized(delayedLoadRestoreTunnels) {
            if (!haveLoaded) {
                val f = CompletableFuture<Void>()
                delayedLoadRestoreTunnels.add(f)
                return f
            }
        }
        val previouslyRunning = getSharedPreferences().getStringSet(KEY_RUNNING_TUNNELS, null)
                ?: return CompletableFuture.completedFuture(null)
        return CompletableFuture.allOf(*tunnelMap.filter { previouslyRunning.contains(it.name) }.map { setTunnelState(it, Tunnel.State.UP).toCompletableFuture() }.toTypedArray())
    }

    @SuppressLint("ApplySharedPref")
    fun saveState() {
        getSharedPreferences().edit().putStringSet(KEY_RUNNING_TUNNELS, tunnelMap.filter { it.state == Tunnel.State.UP }.map { it.name }.toSet()).commit()
    }

    fun setTunnelConfig(tunnel: ObservableTunnel, config: Config): CompletionStage<Config> = getAsyncWorker().supplyAsync {
        getBackend().setState(tunnel, tunnel.state, config)
        configStore.save(tunnel.name, config)
    }.thenApply { tunnel.onConfigChanged(it) }

    fun setTunnelName(tunnel: ObservableTunnel, name: String): CompletionStage<String> {
        if (Tunnel.isNameInvalid(name))
            return CompletableFuture.failedFuture(IllegalArgumentException(context.getString(R.string.tunnel_error_invalid_name)))
        if (tunnelMap.containsKey(name)) {
            return CompletableFuture.failedFuture(IllegalArgumentException(context.getString(R.string.tunnel_error_already_exists, name)))
        }
        val originalState = tunnel.state
        val wasLastUsed = tunnel == lastUsedTunnel
        // Make sure nothing touches the tunnel.
        if (wasLastUsed)
            lastUsedTunnel = null
        tunnelMap.remove(tunnel)
        return getAsyncWorker().supplyAsync {
            if (originalState == Tunnel.State.UP)
                getBackend().setState(tunnel, Tunnel.State.DOWN, null)
            configStore.rename(tunnel.name, name)
            val newName = tunnel.onNameChanged(name)
            if (originalState == Tunnel.State.UP)
                getBackend().setState(tunnel, Tunnel.State.UP, tunnel.config)
            newName
        }.whenComplete { _, e ->
            // On failure, we don't know what state the tunnel might be in. Fix that.
            if (e != null)
                getTunnelState(tunnel)
            // Add the tunnel back to the manager, under whatever name it thinks it has.
            tunnelMap.add(tunnel)
            if (wasLastUsed)
                lastUsedTunnel = tunnel
        }
    }

    fun setTunnelState(tunnel: ObservableTunnel, state: Tunnel.State): CompletionStage<Tunnel.State> = tunnel.configAsync
            .thenCompose { getAsyncWorker().supplyAsync { getBackend().setState(tunnel, state, it) } }
            .whenComplete { newState, e ->
                // Ensure onStateChanged is always called (failure or not), and with the correct state.
                tunnel.onStateChanged(if (e == null) newState else tunnel.state)
                if (e == null && newState == Tunnel.State.UP)
                    lastUsedTunnel = tunnel
                saveState()
            }

    class IntentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val manager = getTunnelManager()
            if (intent == null) return
            val action = intent.action ?: return
            if ("com.wireguard.android.action.REFRESH_TUNNEL_STATES" == action) {
                manager.refreshTunnelStates()
                return
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !getSharedPreferences().getBoolean("allow_remote_control_intents", false))
                return
            val state: Tunnel.State
            state = when (action) {
                "com.wireguard.android.action.SET_TUNNEL_UP" -> Tunnel.State.UP
                "com.wireguard.android.action.SET_TUNNEL_DOWN" -> Tunnel.State.DOWN
                else -> return
            }
            val tunnelName = intent.getStringExtra("tunnel") ?: return
            manager.tunnels.thenAccept {
                val tunnel = it[tunnelName] ?: return@thenAccept
                manager.setTunnelState(tunnel, state)
            }
        }
    }

    fun getTunnelState(tunnel: ObservableTunnel): CompletionStage<Tunnel.State> = getAsyncWorker()
            .supplyAsync { getBackend().getState(tunnel) }.thenApply(tunnel::onStateChanged)

    fun getTunnelStatistics(tunnel: ObservableTunnel): CompletionStage<Statistics> = getAsyncWorker()
            .supplyAsync { getBackend().getStatistics(tunnel) }.thenApply(tunnel::onStatisticsChanged)

    companion object {
        private const val KEY_LAST_USED_TUNNEL = "last_used_tunnel"
        private const val KEY_RESTORE_ON_BOOT = "restore_on_boot"
        private const val KEY_RUNNING_TUNNELS = "enabled_configs"
    }
}
