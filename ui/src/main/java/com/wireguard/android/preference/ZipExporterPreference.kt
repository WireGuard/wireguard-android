/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.preference

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.util.AdminKnobs
import com.wireguard.android.util.BiometricAuthenticator
import com.wireguard.android.util.DownloadsFileSaver
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.util.activity
import com.wireguard.android.util.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Preference implementing a button that asynchronously exports config zips.
 */
class ZipExporterPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var exportedFilePath: String? = null
    private val downloadsFileSaver = DownloadsFileSaver(activity)

    private fun exportZip() {
        lifecycleScope.launch {
            val tunnels = Application.getTunnelManager().getTunnels()
            try {
                exportedFilePath = withContext(Dispatchers.IO) {
                    val configs = tunnels.map { async(SupervisorJob()) { it.getConfigAsync() } }.awaitAll()
                    if (configs.isEmpty()) {
                        throw IllegalArgumentException(context.getString(R.string.no_tunnels_error))
                    }
                    val outputFile = downloadsFileSaver.save("wireguard-export.zip", "application/zip", true)
                    if (outputFile == null) {
                        withContext(Dispatchers.Main.immediate) {
                            isEnabled = true
                        }
                        return@withContext null
                    }
                    try {
                        ZipOutputStream(outputFile.outputStream).use { zip ->
                            for (i in configs.indices) {
                                zip.putNextEntry(ZipEntry(tunnels[i].name + ".conf"))
                                zip.write(configs[i].toWgQuickString().toByteArray(StandardCharsets.UTF_8))
                            }
                            zip.closeEntry()
                        }
                    } catch (e: Throwable) {
                        outputFile.delete()
                        throw e
                    }
                    outputFile.fileName
                }
                notifyChanged()
            } catch (e: Throwable) {
                val error = ErrorMessages[e]
                val message = context.getString(R.string.zip_export_error, error)
                Log.e(TAG, message, e)
                Snackbar.make(
                    activity.findViewById(android.R.id.content),
                    message, Snackbar.LENGTH_LONG
                ).show()
                isEnabled = true
            }
        }
    }

    override fun getSummary() =
        if (exportedFilePath == null) context.getString(R.string.zip_export_summary) else context.getString(R.string.zip_export_success, exportedFilePath)

    override fun getTitle() = context.getString(R.string.zip_export_title)

    override fun onClick() {
        if (AdminKnobs.disableConfigExport) return
        val fragment = activity.supportFragmentManager.fragments.first()
        BiometricAuthenticator.authenticate(R.string.biometric_prompt_zip_exporter_title, fragment) {
            when (it) {
                // When we have successful authentication, or when there is no biometric hardware available.
                is BiometricAuthenticator.Result.Success, is BiometricAuthenticator.Result.HardwareUnavailableOrDisabled -> {
                    isEnabled = false
                    exportZip()
                }

                is BiometricAuthenticator.Result.Failure -> {
                    Snackbar.make(
                        activity.findViewById(android.R.id.content),
                        it.message,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }

                is BiometricAuthenticator.Result.Cancelled -> {}
            }
        }
    }

    companion object {
        private const val TAG = "WireGuard/ZipExporterPreference"
    }
}
