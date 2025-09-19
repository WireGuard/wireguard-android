/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util

import android.icu.text.ListFormatter
import android.icu.text.MeasureFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.os.Build
import com.wireguard.android.Application
import com.wireguard.android.R
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

object QuantityFormatter {
    fun formatBytes(bytes: Long): String {
        val context = Application.get().applicationContext
        return when {
            bytes < 1024 -> context.getString(R.string.transfer_bytes, bytes)
            bytes < 1024 * 1024 -> context.getString(R.string.transfer_kibibytes, bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> context.getString(R.string.transfer_mibibytes, bytes / (1024.0 * 1024.0))
            bytes < 1024 * 1024 * 1024 * 1024L -> context.getString(R.string.transfer_gibibytes, bytes / (1024.0 * 1024.0 * 1024.0))
            else -> context.getString(R.string.transfer_tibibytes, bytes / (1024.0 * 1024.0 * 1024.0) / 1024.0)
        }
    }

    fun formatEpochAgo(epochMillis: Long): String {
        var span = (System.currentTimeMillis() - epochMillis) / 1000

        if (span <= 0L)
            return RelativeDateTimeFormatter.getInstance().format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
        val measureFormat = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
        val parts = ArrayList<CharSequence>(4)
        if (span >= 24 * 60 * 60L) {
            val v = span / (24 * 60 * 60L)
            parts.add(measureFormat.format(Measure(v, MeasureUnit.DAY)))
            span -= v * (24 * 60 * 60L)
        }
        if (span >= 60 * 60L) {
            val v = span / (60 * 60L)
            parts.add(measureFormat.format(Measure(v, MeasureUnit.HOUR)))
            span -= v * (60 * 60L)
        }
        if (span >= 60L) {
            val v = span / 60L
            parts.add(measureFormat.format(Measure(v, MeasureUnit.MINUTE)))
            span -= v * 60L
        }
        if (span > 0L)
            parts.add(measureFormat.format(Measure(span, MeasureUnit.SECOND)))

        val joined = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            parts.joinToString()
        else
            ListFormatter.getInstance(Locale.getDefault(), ListFormatter.Type.UNITS, ListFormatter.Width.SHORT).format(parts)

        return Application.get().applicationContext.getString(R.string.latest_handshake_ago, joined)
    }
}