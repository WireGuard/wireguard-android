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
import com.wireguard.android.util.DownloadsFileSaver
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.util.FragmentUtils
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Preference implementing a button that asynchronously exports logs.
 */
class LogExporterPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var exportedFilePath: String? = null
    private fun exportLog() {
        Application.getAsyncWorker().supplyAsync {
            val outputFile = DownloadsFileSaver.save(context, "wireguard-log.txt", "text/plain", true)
            try {
                val process = Runtime.getRuntime().exec(arrayOf(
                        "logcat", "-b", "all", "-d", "-v", "threadtime", "*:V"))
                BufferedReader(InputStreamReader(process.inputStream)).use { stdout ->
                    BufferedReader(InputStreamReader(process.errorStream)).use { stderr ->
                        while (true) {
                            val line = stdout.readLine() ?: break
                            outputFile.outputStream.write(line.toByteArray())
                            outputFile.outputStream.write('\n'.toInt())
                        }
                        outputFile.outputStream.close()
                        if (process.waitFor() != 0) {
                            val errors = StringBuilder()
                            errors.append(R.string.logcat_error)
                            while (true) {
                                val line = stderr.readLine() ?: break
                                errors.append(line)
                            }
                            throw Exception(errors.toString())
                        }
                    }
                }
            } catch (e: Exception) {
                outputFile.delete()
                throw e
            }
            outputFile.fileName
        }.whenComplete(this::exportLogComplete)
    }

    private fun exportLogComplete(filePath: String, throwable: Throwable?) {
        if (throwable != null) {
            val error = ErrorMessages.get(throwable)
            val message = context.getString(R.string.log_export_error, error)
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

    override fun getSummary() = if (exportedFilePath == null)
        context.getString(R.string.log_export_summary)
    else
        context.getString(R.string.log_export_success, exportedFilePath)

    override fun getTitle() = context.getString(R.string.log_export_title)

    override fun onClick() {
        FragmentUtils.getPrefActivity(this)
                .ensurePermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)) { _, grantResults ->
                    if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        isEnabled = false
                        exportLog()
                    }
                }
    }

    companion object {
        private val TAG = "WireGuard/" + LogExporterPreference::class.java.simpleName
    }
}
