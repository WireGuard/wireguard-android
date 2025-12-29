/*
 * Copyright © 2017-2025 HEZWIN LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hezwin.android.activity

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
import com.hezwin.android.Application
import com.hezwin.android.QuickTileService
import com.hezwin.android.R
import com.hezwin.android.backend.GoBackend
import com.hezwin.android.backend.Tunnel
import com.hezwin.android.util.ErrorMessages
import kotlinx.coroutines.launch

class TunnelToggleActivity : AppCompatActivity() {
    private val permissionActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { toggleTunnelWithPermissionsResult() }

    private fun toggleTunnelWithPermissionsResult() {
        val tunnel = Application.getTunnelManager().lastUsedTunnel ?: return
        lifecycleScope.launch {
            try {
                tunnel.setStateAsync(Tunnel.State.TOGGLE)
            } catch (e: Throwable) {
                TileService.requestListeningState(this@TunnelToggleActivity, ComponentName(this@TunnelToggleActivity, QuickTileService::class.java))
                val error = ErrorMessages[e]
                val message = getString(R.string.toggle_error, error)
                Log.e(TAG, message, e)
                Toast.makeText(this@TunnelToggleActivity, message, Toast.LENGTH_LONG).show()
                finishAffinity()
                return@launch
            }
            TileService.requestListeningState(this@TunnelToggleActivity, ComponentName(this@TunnelToggleActivity, QuickTileService::class.java))
            finishAffinity()
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
        private const val TAG = "HEZWIN/TunnelToggleActivity"
    }
}
