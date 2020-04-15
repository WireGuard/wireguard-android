/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.model

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.wireguard.android.BR
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.databinding.Keyed
import com.wireguard.android.util.ExceptionLoggers
import com.wireguard.config.Config
import java9.util.concurrent.CompletableFuture
import java9.util.concurrent.CompletionStage

/**
 * Encapsulates the volatile and nonvolatile state of a WireGuard tunnel.
 */
class ObservableTunnel internal constructor(
        private val manager: TunnelManager,
        private var name: String,
        config: Config?,
        state: Tunnel.State
) : BaseObservable(), Keyed<String>, Tunnel {
    override val key
        get() = name

    @Bindable
    override fun getName() = name

    fun setNameAsync(name: String): CompletionStage<String> = if (name != this.name)
        manager.setTunnelName(this, name)
    else
        CompletableFuture.completedFuture(this.name)

    fun onNameChanged(name: String): String {
        this.name = name
        notifyPropertyChanged(BR.name)
        return name
    }


    @get:Bindable
    var state = state
        private set

    override fun onStateChange(newState: Tunnel.State) {
        onStateChanged(newState)
    }

    fun onStateChanged(state: Tunnel.State): Tunnel.State {
        if (state != Tunnel.State.UP) onStatisticsChanged(null)
        this.state = state
        notifyPropertyChanged(BR.state)
        return state
    }

    fun setStateAsync(state: Tunnel.State): CompletionStage<Tunnel.State> = if (state != this.state)
        manager.setTunnelState(this, state)
    else
        CompletableFuture.completedFuture(this.state)


    @get:Bindable
    var config = config
        get() {
            if (field == null)
                manager.getTunnelConfig(this).whenComplete(ExceptionLoggers.E)
            return field
        }
        private set

    val configAsync: CompletionStage<Config>
        get() = if (config == null)
            manager.getTunnelConfig(this)
        else
            CompletableFuture.completedFuture(config)

    fun setConfigAsync(config: Config): CompletionStage<Config> = if (config != this.config)
        manager.setTunnelConfig(this, config)
    else
        CompletableFuture.completedFuture(this.config)

    fun onConfigChanged(config: Config?): Config? {
        this.config = config
        notifyPropertyChanged(BR.config)
        return config
    }


    @get:Bindable
    var statistics: Statistics? = null
        get() {
            if (field == null || field?.isStale != false)
                manager.getTunnelStatistics(this).whenComplete(ExceptionLoggers.E)
            return field
        }
        private set

    val statisticsAsync: CompletionStage<Statistics>
        get() = if (statistics == null || statistics?.isStale != false)
            manager.getTunnelStatistics(this)
        else
            CompletableFuture.completedFuture(statistics)

    fun onStatisticsChanged(statistics: Statistics?): Statistics? {
        this.statistics = statistics
        notifyPropertyChanged(BR.statistics)
        return statistics
    }


    fun delete(): CompletionStage<Void> = manager.delete(this)
}
