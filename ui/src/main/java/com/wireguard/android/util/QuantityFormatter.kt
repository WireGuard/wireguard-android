/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util

import com.wireguard.android.Application
import com.wireguard.android.R

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
}