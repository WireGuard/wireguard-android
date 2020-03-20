/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.preference

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.AttributeSet
import android.util.Log
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.util.DownloadsFileSaver
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.util.FragmentUtils
import java9.util.concurrent.CompletableFuture
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Preference implementing a button that asynchronously exports config zips.
 */
class ZipExporterPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var exportedFilePath: String? = null

    private fun exportZip() {
        Application.getTunnelManager().tunnels.thenAccept(this::exportZip)
    }

    private fun exportZip(tunnels: List<ObservableTunnel>) {
        val futureConfigs = tunnels.map { it.configAsync.toCompletableFuture() }.toTypedArray()
        if (futureConfigs.isEmpty()) {
            exportZipComplete(null, IllegalArgumentException(
                    context.getString(R.string.no_tunnels_error)))
            return
        }
        CompletableFuture.allOf(*futureConfigs)
                .whenComplete { _, exception ->
                    Application.getAsyncWorker().supplyAsync {
                        if (exception != null) throw exception
                        val outputFile = DownloadsFileSaver.save(context, "wireguard-export.zip", "application/zip", true)
                        try {
                            ZipOutputStream(outputFile.outputStream).use { zip ->
                                for (i in futureConfigs.indices) {
                                    zip.putNextEntry(ZipEntry(tunnels[i].name + ".conf"))
                                    zip.write(futureConfigs[i].getNow(null)!!.toWgQuickString().toByteArray(StandardCharsets.UTF_8))
                                }
                                zip.closeEntry()
                            }
                        } catch (e: Exception) {
                            outputFile.delete()
                            throw e
                        }
                        outputFile.fileName
                    }.whenComplete(this::exportZipComplete)
                }
    }

    private fun exportZipComplete(filePath: String?, throwable: Throwable?) {
        if (throwable != null) {
            val error = ErrorMessages.get(throwable)
            val message = context.getString(R.string.zip_export_error, error)
            Log.e(TAG, message, throwable)
            Snackbar.make(
                    FragmentUtils.getPrefActivity(this).findViewById(android.R.id.content),
                    message, Snackbar.LENGTH_LONG).show()
            isEnabled = true
        } else {
            exportedFilePath = filePath
            notifyChanged()
        }
    }

    override fun getSummary() = if (exportedFilePath == null) context.getString(R.string.zip_export_summary) else context.getString(R.string.zip_export_success, exportedFilePath)

    override fun getTitle() = context.getString(R.string.zip_export_title)

    override fun onClick() {
        FragmentUtils.getPrefActivity(this)
                .ensurePermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { _, grantResults ->
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        isEnabled = false
                        exportZip()
                    }
                }
    }

    companion object {
        private val TAG = "WireGuard/" + ZipExporterPreference::class.java.simpleName
    }
}
