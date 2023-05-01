/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wireguard.android.activity.MainActivity
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.android.updater.Updater
import com.wireguard.android.util.applicationScope
import kotlinx.coroutines.launch

class BootShutdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (Intent.ACTION_MY_PACKAGE_REPLACED == action && Updater.installer() == context.packageName) {
            /* TODO: does not work because of restrictions placed on broadcast receivers. */
            val start = Intent(context, MainActivity::class.java)
            start.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(start)
            return
        }

        applicationScope.launch {
            if (Application.getBackend() !is WgQuickBackend) return@launch
            val tunnelManager = Application.getTunnelManager()
            if (Intent.ACTION_BOOT_COMPLETED == action) {
                Log.i(TAG, "Broadcast receiver restoring state (boot)")
                tunnelManager.restoreState(false)
            } else if (Intent.ACTION_SHUTDOWN == action) {
                Log.i(TAG, "Broadcast receiver saving state (shutdown)")
                tunnelManager.saveState()
            }
        }
    }

    companion object {
        private const val TAG = "WireGuard/BootShutdownReceiver"
    }
}
