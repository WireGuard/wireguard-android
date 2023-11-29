/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.activity

import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.os.Handler
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

@RequiresApi(Build.VERSION_CODES.N)
class TunnelToggleActivity : AppCompatActivity() {

    private var mIsVisible = false

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
                exitActivity()
                return@launch
            }
            TileService.requestListeningState(this@TunnelToggleActivity, ComponentName(this@TunnelToggleActivity, QuickTileService::class.java))
            exitActivity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(intent.getBooleanExtra(SHOW_PROGRESS, false)) {
            mIsVisible = true
            title = "" // Otherwise the apptitle will be shown above the spinner.
            setContentView(R.layout.loading_activity)
        }

        lifecycleScope.launch {
            if (Application.getBackend() is GoBackend) {
                val intent = GoBackend.VpnService.prepare(this@TunnelToggleActivity)
                if (intent != null) {
                    permissionActivityResultLauncher.launch(intent)
                    return@launch
                }
            }
            toggleTunnelWithPermissionsResult()
        }
        exitActivity()
    }

    private fun exitActivity() {
        /*
         We add this delay, so that the user gets the impression we are actually doing something.
         This is to make the transition of the closing quicktile-menu more palatable,
         because startActivityAndCollapse() will immediately close that menu.
         This can be jarring, so we show this placeholder spinner and close it after a second.
         */

        if(mIsVisible) {
            Handler().postDelayed({
                finishAffinity()
            }, 1000L)
        } else {
            finishAffinity()
        }

    }

    companion object {
        private const val TAG = "WireGuard/TunnelToggleActivity"
        const val SHOW_PROGRESS = "ShowLoadingbar"
    }
}
