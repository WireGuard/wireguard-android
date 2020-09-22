/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.util.ErrorMessages
import com.wireguard.config.Config
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class TvMainActivity : BaseActivity() {
    private val tunnelFileImportResultLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { data ->
        importTunnel(data)
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

    private fun onTunnelImportFinished(tunnels: List<ObservableTunnel>, throwables: Collection<Throwable>) {
        var message = ""
        for (throwable in throwables) {
            val error = ErrorMessages[throwable]
            message = getString(R.string.import_error, error)
            Log.e(TAG, message, throwable)
        }
        if (tunnels.size == 1 && throwables.isEmpty())
            message = getString(R.string.import_success, tunnels[0].name)
        else if (tunnels.isEmpty() && throwables.size == 1)
        else if (throwables.isEmpty())
            message = resources.getQuantityString(R.plurals.import_total_success,
                    tunnels.size, tunnels.size)
        else if (!throwables.isEmpty())
            message = resources.getQuantityString(R.plurals.import_partial_success,
                    tunnels.size + throwables.size,
                    tunnels.size, tunnels.size + throwables.size)
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }

    private fun importTunnel(uri: Uri?) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (uri == null) {
                    return@withContext
                }
                val futureTunnels = ArrayList<Deferred<ObservableTunnel>>()
                val throwables = ArrayList<Throwable>()
                try {
                    val columns = arrayOf(OpenableColumns.DISPLAY_NAME)
                    var name = ""
                    contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst() && !cursor.isNull(0)) {
                            name = cursor.getString(0)
                        }
                    }
                    if (name.isEmpty()) {
                        name = Uri.decode(uri.lastPathSegment)
                    }
                    var idx = name.lastIndexOf('/')
                    if (idx >= 0) {
                        require(idx < name.length - 1) { resources.getString(R.string.illegal_filename_error, name) }
                        name = name.substring(idx + 1)
                    }
                    val isZip = name.toLowerCase(Locale.ROOT).endsWith(".zip")
                    if (name.toLowerCase(Locale.ROOT).endsWith(".conf")) {
                        name = name.substring(0, name.length - ".conf".length)
                    } else {
                        require(isZip) { resources.getString(R.string.bad_extension_error) }
                    }

                    if (isZip) {
                        ZipInputStream(contentResolver.openInputStream(uri)).use { zip ->
                            val reader = BufferedReader(InputStreamReader(zip, StandardCharsets.UTF_8))
                            var entry: ZipEntry?
                            while (true) {
                                entry = zip.nextEntry ?: break
                                name = entry.name
                                idx = name.lastIndexOf('/')
                                if (idx >= 0) {
                                    if (idx >= name.length - 1) {
                                        continue
                                    }
                                    name = name.substring(name.lastIndexOf('/') + 1)
                                }
                                if (name.toLowerCase(Locale.ROOT).endsWith(".conf")) {
                                    name = name.substring(0, name.length - ".conf".length)
                                } else {
                                    continue
                                }
                                try {
                                    Config.parse(reader)
                                } catch (e: Throwable) {
                                    throwables.add(e)
                                    null
                                }?.let {
                                    val nameCopy = name
                                    futureTunnels.add(async(SupervisorJob()) { Application.getTunnelManager().create(nameCopy, it) })
                                }
                            }
                        }
                    } else {
                        futureTunnels.add(async(SupervisorJob()) { Application.getTunnelManager().create(name, Config.parse(contentResolver.openInputStream(uri)!!)) })
                    }

                    if (futureTunnels.isEmpty()) {
                        if (throwables.size == 1) {
                            throw throwables[0]
                        } else {
                            require(throwables.isNotEmpty()) { resources.getString(R.string.no_configs_error) }
                        }
                    }
                    val tunnels = futureTunnels.mapNotNull {
                        try {
                            it.await()
                        } catch (e: Throwable) {
                            throwables.add(e)
                            null
                        }
                    }
                    withContext(Dispatchers.Main.immediate) { onTunnelImportFinished(tunnels, throwables) }
                } catch (e: Throwable) {
                    withContext(Dispatchers.Main.immediate) { onTunnelImportFinished(emptyList(), listOf(e)) }
                }
            }
        }
    }

    companion object {
        const val TAG = "WireGuard/TvMainActivity"
    }
}
