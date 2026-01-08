/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.activity

import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wireguard.android.Application
import com.wireguard.android.QuickTileService
import com.wireguard.android.R
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.util.ErrorMessages
import kotlinx.coroutines.launch

class TunnelToggleActivity : AppCompatActivity() {
    private val permissionActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { toggleTunnelWithPermissionsResult() }

    private fun toggleTunnelWithPermissionsResult() {
        lifecycleScope.launch {
            val tunnelAction = when(intent.action) {
                "com.wireguard.android.action.SET_TUNNEL_UP" -> Tunnel.State.UP
                "com.wireguard.android.action.SET_TUNNEL_DOWN" -> Tunnel.State.DOWN
                else -> Tunnel.State.TOGGLE // Implicit toggle to keep previous behaviour
            }

            val tunnel = when(val tunnelName = intent.getStringExtra("tunnel")) {
                null -> Application.getTunnelManager().lastUsedTunnel
                else -> Application.getTunnelManager().getTunnels().find { it.name == tunnelName }
            } ?: return@launch // If we failed to identify the tunnel, just return

            try {
                tunnel.setStateAsync(tunnelAction)
            } catch (e: Throwable) {
                updateTileService()
                val error = ErrorMessages[e]
                val message = getString(R.string.toggle_error, error)
                Log.e(TAG, message, e)
                Toast.makeText(this@TunnelToggleActivity, message, Toast.LENGTH_LONG).show()
                finishAffinity()
                return@launch
            }
            updateTileService()
            finishAffinity()
        }
    }

    /**
     * TileService is only available for API 24+, if it's available it'll be updated,
     * otherwise it's ignored.
     */
    private fun updateTileService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(this@TunnelToggleActivity, ComponentName(this@TunnelToggleActivity, QuickTileService::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (Application.getBackend() is GoBackend) {
                try {
                    val intent = GoBackend.VpnService.prepare(this@TunnelToggleActivity)
                    if (intent != null) {
                        permissionActivityResultLauncher.launch(intent)
                        return@launch
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@TunnelToggleActivity, ErrorMessages[e], Toast.LENGTH_LONG).show()
                }
            }
            toggleTunnelWithPermissionsResult()
        }
    }

    companion object {
        private const val TAG = "WireGuard/TunnelToggleActivity"
    }
}
