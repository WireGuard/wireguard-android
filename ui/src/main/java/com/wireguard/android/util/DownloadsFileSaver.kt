/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.wireguard.android.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class DownloadsFileSaver(private val context: ComponentActivity) {
    private lateinit var activityResult: ActivityResultLauncher<String>
    private lateinit var futureGrant: CompletableDeferred<Boolean>

    init {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            futureGrant = CompletableDeferred()
            activityResult = context.registerForActivityResult(ActivityResultContracts.RequestPermission()) { ret -> futureGrant.complete(ret) }
        }
    }

    suspend fun save(name: String, mimeType: String?, overwriteExisting: Boolean) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            if (overwriteExisting)
                contentResolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, String.format("%s = ?", MediaColumns.DISPLAY_NAME), arrayOf(name))
            val contentValues = ContentValues()
            contentValues.put(MediaColumns.DISPLAY_NAME, name)
            contentValues.put(MediaColumns.MIME_TYPE, mimeType)
            val contentUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException(context.getString(R.string.create_downloads_file_error))
            val contentStream = contentResolver.openOutputStream(contentUri)
                ?: throw IOException(context.getString(R.string.create_downloads_file_error))
            @Suppress("DEPRECATION") var cursor = contentResolver.query(contentUri, arrayOf(MediaColumns.DATA), null, null, null)
            var path: String? = null
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst())
                        path = cursor.getString(0)
                } finally {
                    cursor.close()
                }
            }
            if (path == null) {
                path = "Download/"
                cursor = contentResolver.query(contentUri, arrayOf(MediaColumns.DISPLAY_NAME), null, null, null)
                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst())
                            path += cursor.getString(0)
                    } finally {
                        cursor.close()
                    }
                }
            }
            DownloadsFile(context, contentStream, path, contentUri)
        }
    } else {
        withContext(Dispatchers.Main.immediate) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                activityResult.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                val granted = futureGrant.await()
                if (!granted) {
                    futureGrant = CompletableDeferred()
                    return@withContext null
                }
            }
            @Suppress("DEPRECATION") val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            withContext(Dispatchers.IO) {
                val file = File(path, name)
                if (!path.isDirectory && !path.mkdirs())
                    throw IOException(context.getString(R.string.create_output_dir_error))
                DownloadsFile(context, FileOutputStream(file), file.absolutePath, null)
            }
        }
    }

    class DownloadsFile(private val context: Context, val outputStream: OutputStream, val fileName: String, private val uri: Uri?) {
        suspend fun delete() = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                context.contentResolver.delete(uri!!, null, null)
            else
                File(fileName).delete()
        }
    }
}
