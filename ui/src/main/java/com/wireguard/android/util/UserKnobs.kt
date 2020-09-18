/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util

import androidx.datastore.preferences.edit
import androidx.datastore.preferences.preferencesKey
import androidx.datastore.preferences.preferencesSetKey
import androidx.datastore.preferences.remove
import com.wireguard.android.Application
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object UserKnobs {
    private val DISABLE_KERNEL_MODULE = preferencesKey<Boolean>("disable_kernel_module")
    val disableKernelModule: Flow<Boolean>
        get() = Application.getPreferencesDataStore().data.map {
            it[DISABLE_KERNEL_MODULE] ?: false
        }

    suspend fun setDisableKernelModule(disable: Boolean?) {
        Application.getPreferencesDataStore().edit {
            if (disable == null)
                it.remove(DISABLE_KERNEL_MODULE)
            else
                it[DISABLE_KERNEL_MODULE] = disable
        }
    }

    private val MULTIPLE_TUNNELS = preferencesKey<Boolean>("multiple_tunnels")
    val multipleTunnels: Flow<Boolean>
        get() = Application.getPreferencesDataStore().data.map {
            it[MULTIPLE_TUNNELS] ?: false
        }

    private val DARK_THEME = preferencesKey<Boolean>("dark_theme")
    val darkTheme: Flow<Boolean>
        get() = Application.getPreferencesDataStore().data.map {
            it[DARK_THEME] ?: false
        }

    private val ALLOW_REMOTE_CONTROL_INTENTS = preferencesKey<Boolean>("allow_remote_control_intents")
    val allowRemoteControlIntents: Flow<Boolean>
        get() = Application.getPreferencesDataStore().data.map {
            it[ALLOW_REMOTE_CONTROL_INTENTS] ?: false
        }

    private val RESTORE_ON_BOOT = preferencesKey<Boolean>("restore_on_boot")
    val restoreOnBoot: Flow<Boolean>
        get() = Application.getPreferencesDataStore().data.map {
            it[RESTORE_ON_BOOT] ?: false
        }

    private val LAST_USED_TUNNEL = preferencesKey<String>("last_used_tunnel")
    val lastUsedTunnel: Flow<String?>
        get() = Application.getPreferencesDataStore().data.map {
            it[LAST_USED_TUNNEL]
        }

    suspend fun setLastUsedTunnel(lastUsedTunnel: String?) {
        Application.getPreferencesDataStore().edit {
            if (lastUsedTunnel == null)
                it.remove(LAST_USED_TUNNEL)
            else
                it[LAST_USED_TUNNEL] = lastUsedTunnel
        }
    }

    private val RUNNING_TUNNELS = preferencesSetKey<String>("enabled_configs")
    val runningTunnels: Flow<Set<String>>
        get() = Application.getPreferencesDataStore().data.map {
            it[RUNNING_TUNNELS] ?: emptySet()
        }

    suspend fun setRunningTunnels(runningTunnels: Set<String>) {
        Application.getPreferencesDataStore().edit {
            if (runningTunnels.isEmpty())
                it.remove(RUNNING_TUNNELS)
            else
                it[RUNNING_TUNNELS] = runningTunnels
        }
    }
}
