/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util

import android.icu.text.ListFormatter
import android.icu.text.MeasureFormat
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.os.Build
import androidx.annotation.RequiresApi
import com.wireguard.android.Application
import com.wireguard.android.R
import java.util.Locale
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object QuantityFormatter {
    @RequiresApi(Build.VERSION_CODES.N)
    private fun resolveDigitalMeasureUnit(identifier: String): MeasureUnit {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            MeasureUnit::class.java.getMethod("internalGetInstance", String::class.java, String::class.java).invoke(null, "digital", identifier) as MeasureUnit
        } else {
            MeasureUnit.forIdentifier(identifier)
        }
    }
    private lateinit var KIBIBYTE : MeasureUnit
    private lateinit var MEBIBYTE : MeasureUnit
    private lateinit var GIBIBYTE : MeasureUnit
    private lateinit var TEBIBYTE : MeasureUnit

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            KIBIBYTE = resolveDigitalMeasureUnit("kibibyte")
            MEBIBYTE = resolveDigitalMeasureUnit("mebibyte")
            GIBIBYTE = resolveDigitalMeasureUnit("gibibyte")
            TEBIBYTE = resolveDigitalMeasureUnit("tebibyte")
        }
    }

    fun formatBytes(bytes: Long): String {
        val context = Application.get().applicationContext
        val measureFormat = MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.NARROW)

        return when {
            bytes < 1024 -> measureFormat.format(Measure(bytes, MeasureUnit.BYTE))
            bytes < 1024 * 1024 -> measureFormat.format(Measure(bytes / 1024.0, KIBIBYTE))
            bytes < 1024 * 1024 * 1024 -> measureFormat.format(Measure(bytes / (1024.0 * 1024.0), MEBIBYTE))
            bytes < 1024 * 1024 * 1024 * 1024L -> measureFormat.format(Measure(bytes / (1024.0 * 1024.0 * 1024.0), GIBIBYTE))
            else -> measureFormat.format(Measure(bytes / (1024.0 * 1024.0 * 1024.0) / 1024.0, TEBIBYTE))
        }
    }

    fun formatEpochAgo(epochMillis: Long): String {
        var span = (System.currentTimeMillis() - epochMillis) / 1000

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            return span.toDuration(DurationUnit.SECONDS).toString()

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