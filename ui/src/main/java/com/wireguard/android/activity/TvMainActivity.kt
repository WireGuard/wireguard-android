/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity

import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.wireguard.android.R
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.util.TunnelImporter
import kotlinx.coroutines.launch

class TvMainActivity : BaseActivity() {
    private val tunnelFileImportResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { data ->
        lifecycleScope.launch {
            TunnelImporter.importTunnel(contentResolver, data) {
                Toast.makeText(this@TvMainActivity, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?, newTunnel: ObservableTunnel?) {
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tv_activity)
        findViewById<MaterialButton>(R.id.import_button).setOnClickListener {
            tunnelFileImportResultLauncher.launch("*/*")
        }
    }

    companion object {
        const val TAG = "WireGuard/TvMainActivity"
    }
}
