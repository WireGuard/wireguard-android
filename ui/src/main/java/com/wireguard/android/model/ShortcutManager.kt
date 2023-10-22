/*
 * Copyright © 2017-2022 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.model

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.wireguard.android.BuildConfig
import com.wireguard.android.R
import com.wireguard.android.activity.TunnelToggleActivity

class ShortcutManager(private val context: Context) {

    private fun upIdFor(name: String): String = "$name-UP"
    private fun downIdFor(name: String): String = "$name-DOWN"

    private fun createShortcutIntent(action: String, tunnelName: String): Intent =
        Intent(context, TunnelToggleActivity::class.java).apply {
        setPackage(BuildConfig.APPLICATION_ID)
        setAction(action)
        putExtra("tunnel", tunnelName)
    }

    fun addShortcuts(name: String) {
        val upIntent = createShortcutIntent("com.wireguard.android.action.SET_TUNNEL_UP", name)
        val shortcutUp = ShortcutInfoCompat.Builder(context, upIdFor(name))
            .setShortLabel(context.getString(R.string.shortcut_label_short_up, name))
            .setLongLabel(context.getString(R.string.shortcut_label_long_up, name))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_baseline_arrow_circle_up_24))
            .setIntent(upIntent)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcutUp)

        val downIntent = createShortcutIntent("com.wireguard.android.action.SET_TUNNEL_DOWN", name)
        val shortcutDown = ShortcutInfoCompat.Builder(context, downIdFor(name))
            .setShortLabel(context.getString(R.string.shortcut_label_short_down, name))
            .setLongLabel(context.getString(R.string.shortcut_label_long_down, name))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_baseline_arrow_circle_down_24))
            .setIntent(downIntent)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcutDown)
    }

    fun removeShortcuts(name: String) {
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(upIdFor(name), downIdFor(name)))
    }

    fun hasShortcut(name: String) =
        ShortcutManagerCompat.getDynamicShortcuts(context).any { it.id.startsWith(name) }
}