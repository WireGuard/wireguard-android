/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wireguard.android.Application
import com.wireguard.android.databinding.TvActivityBinding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = TvActivityBinding.inflate(layoutInflater)
        lifecycleScope.launch { binding.tunnels = Application.getTunnelManager().getTunnels() }
        binding.importButton.setOnClickListener {
            tunnelFileImportResultLauncher.launch("*/*")
        }
        binding.executePendingBindings()
        setContentView(binding.root)
    }
}
