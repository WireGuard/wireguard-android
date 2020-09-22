/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.databinding.ObservableKeyedRecyclerViewAdapter
import com.wireguard.android.databinding.TvActivityBinding
import com.wireguard.android.databinding.TvTunnelListItemBinding
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.util.TunnelImporter
import kotlinx.coroutines.launch

class TvMainActivity : AppCompatActivity() {
    private val tunnelFileImportResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { data ->
        lifecycleScope.launch {
            TunnelImporter.importTunnel(contentResolver, data) {
                Toast.makeText(this@TvMainActivity, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private var pendingTunnel: ObservableTunnel? = null
    private val permissionActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val tunnel = pendingTunnel
        if (tunnel != null)
            setTunnelStateWithPermissionsResult(tunnel)
        pendingTunnel = null
    }

    private fun setTunnelStateWithPermissionsResult(tunnel: ObservableTunnel) {
        lifecycleScope.launch {
            try {
                tunnel.setStateAsync(Tunnel.State.TOGGLE)
            } catch (e: Throwable) {
                val error = ErrorMessages[e]
                val message = getString(R.string.error_up, error)
                Toast.makeText(this@TvMainActivity, message, Toast.LENGTH_LONG).show()
                Log.e(TAG, message, e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = TvActivityBinding.inflate(layoutInflater)
        lifecycleScope.launch { binding.tunnels = Application.getTunnelManager().getTunnels() }
        binding.rowConfigurationHandler = object : ObservableKeyedRecyclerViewAdapter.RowConfigurationHandler<TvTunnelListItemBinding, ObservableTunnel> {
            override fun onConfigureRow(binding: TvTunnelListItemBinding, item: ObservableTunnel, position: Int) {
                binding.root.setOnClickListener() {
                    lifecycleScope.launch {
                        if (Application.getBackend() is GoBackend) {
                            val intent = GoBackend.VpnService.prepare(binding.root.context)
                            if (intent != null) {
                                pendingTunnel = item
                                permissionActivityResultLauncher.launch(intent)
                                return@launch
                            }
                        }
                        setTunnelStateWithPermissionsResult(item)
                    }
                }
            }
        }
        binding.importButton.setOnClickListener {
            tunnelFileImportResultLauncher.launch("*/*")
        }
        binding.executePendingBindings()
        setContentView(binding.root)
    }

    companion object {
        private const val TAG = "WireGuard/TvMainActivity"
    }
}
