/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.util

import android.util.Log
import com.jimberisolation.android.Application
import com.jimberisolation.android.R
import com.jimberisolation.android.configStore.CreateTunnelData
import com.jimberisolation.android.model.ObservableTunnel
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.config.BadConfigException
import com.jimberisolation.config.Config
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import kotlin.math.min

object TunnelImporter {
    suspend fun importTunnel(configText: String, daemonId: Int, daemonName: String, messageCallback: (CharSequence) -> Unit) {
        try {
            val config = try {
                Config.parse(ByteArrayInputStream(configText.toByteArray(StandardCharsets.UTF_8)))
            }
            catch (e: Throwable) {
                when (e) {
                    is BadConfigException, is IOException -> throw IllegalArgumentException("Invalid config passed to ${javaClass.simpleName}", e)
                    else -> throw e
                }
            }

            val userId = SharedStorage.getInstance().getCurrentUser()?.id;

            val createTunnelData = CreateTunnelData(daemonName, daemonId, userId!!);
            Application.getTunnelManager().create(createTunnelData, config)

        } catch (e: Throwable) {
            onTunnelImportFinished(emptyList(), listOf(e), messageCallback)
        }
    }

    private fun onTunnelImportFinished(tunnels: List<ObservableTunnel>, throwables: Collection<Throwable>, messageCallback: (CharSequence) -> Unit) {
        val context = Application.get().applicationContext
        var message = ""
        for (throwable in throwables) {
            val error = ErrorMessages[throwable]
            message = context.getString(R.string.import_error, error)
            Log.e(TAG, message, throwable)
        }
        if (tunnels.size == 1 && throwables.isEmpty())
            message = context.getString(R.string.import_success, tunnels[0].name)
        else if (tunnels.isEmpty() && throwables.size == 1)
        else if (throwables.isEmpty())
            message = context.resources.getQuantityString(
                R.plurals.import_total_success,
                tunnels.size, tunnels.size
            )
        else if (!throwables.isEmpty())
            message = context.resources.getQuantityString(
                R.plurals.import_partial_success,
                tunnels.size + throwables.size,
                tunnels.size, tunnels.size + throwables.size
            )

        messageCallback(message)
    }

    private const val TAG = "WireGuard/TunnelImporter"
}