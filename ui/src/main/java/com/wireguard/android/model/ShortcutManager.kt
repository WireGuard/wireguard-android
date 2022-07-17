/*
 * Copyright © 2017-2022 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.model

import android.content.Context
import androidx.core.content.pm.ShortcutManagerCompat

class ShortcutManager(private val context: Context) {

    private fun upIdFor(name: String): String = "$name-UP"
    private fun downIdFor(name: String): String = "$name-DOWN"

    fun addShortcuts(name: String) {
    }

    fun removeShortcuts(name: String) {
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(upIdFor(name), downIdFor(name)))
    }

    fun hasShortcut(name: String) =
        ShortcutManagerCompat.getDynamicShortcuts(context).any { it.id.startsWith(name) }
}