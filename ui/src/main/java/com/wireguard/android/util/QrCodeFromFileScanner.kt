/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Reader
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encapsulates the logic of scanning a barcode from a file,
 * @property contentResolver - Resolver to read the incoming data
 * @property reader - An instance of zxing's [Reader] class to parse the image
 */
class QrCodeFromFileScanner(
    private val contentResolver: ContentResolver,
    private val reader: Reader,
) {
    private fun scanBitmapForResult(source: Bitmap): Result {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val bBitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        return reader.decode(bBitmap, mapOf(DecodeHintType.TRY_HARDER to true))
    }

    private fun doScan(data: Uri): Result {
        Log.d(TAG, "Starting to scan an image: $data")
        contentResolver.openInputStream(data).use { inputStream ->
            var bitmap: Bitmap? = null
            var firstException: Throwable? = null
            for (i in arrayOf(1, 2, 4, 8, 16, 32, 64, 128)) {
                try {
                    val options = BitmapFactory.Options()
                    options.inSampleSize = i
                    bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                        ?: throw IllegalArgumentException("Can't decode stream for bitmap")
                    return scanBitmapForResult(bitmap)
                } catch (e: Throwable) {
                    bitmap?.recycle()
                    System.gc()
                    Log.e(TAG, "Original image scan at scale factor $i finished with error: $e")
                    if (firstException == null)
                        firstException = e
                }
            }
            throw Exception(firstException)
        }
    }

    /**
     * Attempts to parse incoming data
     * @return result of the decoding operation
     * @throws NotFoundException when parser didn't find QR code in the image
     */
    suspend fun scan(data: Uri) = withContext(Dispatchers.Default) { doScan(data) }

    companion object {
        private const val TAG = "QrCodeFromFileScanner"

        /**
         * Given a reference to a file, check if this file could be parsed by this class
         * @return true if the file can be parsed, false if not
         */
        fun validContentType(contentResolver: ContentResolver, data: Uri): Boolean {
            return contentResolver.getType(data)?.startsWith("image/") == true
        }
    }
}
