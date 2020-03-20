/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.activity

import android.os.Bundle
import androidx.databinding.CallbackRegistry
import androidx.databinding.CallbackRegistry.NotifierCallback
import com.wireguard.android.Application
import com.wireguard.android.model.ObservableTunnel

/**
 * Base class for activities that need to remember the currently-selected tunnel.
 */
abstract class BaseActivity : ThemeChangeAwareActivity() {
    private val selectionChangeRegistry = SelectionChangeRegistry()
    var selectedTunnel: ObservableTunnel? = null
        set(value) {
            val oldTunnel = field
            if (oldTunnel == value) return
            field = value
            onSelectedTunnelChanged(oldTunnel, value)
            selectionChangeRegistry.notifyCallbacks(oldTunnel, 0, value)
        }
    fun addOnSelectedTunnelChangedListener(listener: OnSelectedTunnelChangedListener) {
        selectionChangeRegistry.add(listener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore the saved tunnel if there is one; otherwise grab it from the arguments.
        val savedTunnelName = when {
            savedInstanceState != null -> savedInstanceState.getString(KEY_SELECTED_TUNNEL)
            intent != null -> intent.getStringExtra(KEY_SELECTED_TUNNEL)
            else -> null
        }
        if (savedTunnelName != null) {
            Application.getTunnelManager()
                    .tunnels
                    .thenAccept { selectedTunnel = it[savedTunnelName] }
        }

        // The selected tunnel must be set before the superclass method recreates fragments.
        super.onCreate(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (selectedTunnel != null) outState.putString(KEY_SELECTED_TUNNEL, selectedTunnel!!.name)
        super.onSaveInstanceState(outState)
    }

    protected abstract fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?, newTunnel: ObservableTunnel?)
    fun removeOnSelectedTunnelChangedListener(
            listener: OnSelectedTunnelChangedListener) {
        selectionChangeRegistry.remove(listener)
    }

    interface OnSelectedTunnelChangedListener {
        fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?, newTunnel: ObservableTunnel?)
    }

    private class SelectionChangeNotifier : NotifierCallback<OnSelectedTunnelChangedListener, ObservableTunnel, ObservableTunnel>() {
        override fun onNotifyCallback(
                listener: OnSelectedTunnelChangedListener,
                oldTunnel: ObservableTunnel?,
                ignored: Int,
                newTunnel: ObservableTunnel?
        ) {
            listener.onSelectedTunnelChanged(oldTunnel, newTunnel)
        }
    }

    private class SelectionChangeRegistry :
            CallbackRegistry<OnSelectedTunnelChangedListener, ObservableTunnel, ObservableTunnel>(SelectionChangeNotifier())

    companion object {
        private const val KEY_SELECTED_TUNNEL = "selected_tunnel"
    }
}
