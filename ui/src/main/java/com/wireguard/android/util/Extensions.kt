/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util

import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.activity.SettingsActivity
import kotlinx.coroutines.CoroutineScope

fun Context.resolveAttribute(@AttrRes attrRes: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attrRes, typedValue, true)
    return typedValue.data
}

fun Context.formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> getString(R.string.transfer_bytes, bytes)
        bytes < 1024 * 1024 -> getString(R.string.transfer_kibibytes, bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> getString(R.string.transfer_mibibytes, bytes / (1024.0 * 1024.0))
        bytes < 1024 * 1024 * 1024 * 1024L -> getString(R.string.transfer_gibibytes, bytes / (1024.0 * 1024.0 * 1024.0))
        else -> getString(R.string.transfer_tibibytes, bytes / (1024.0 * 1024.0 * 1024.0) / 1024.0)
    }
}

val Any.applicationScope: CoroutineScope
    get() = Application.getCoroutineScope()

val Preference.activity: SettingsActivity
    get() = context as? SettingsActivity
            ?: throw IllegalStateException("Failed to resolve SettingsActivity")

val Preference.lifecycleScope: CoroutineScope
    get() = activity.lifecycleScope
