/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.util.AttributeSet
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.preference.Preference
import com.wireguard.android.QuickTileService
import com.wireguard.android.R

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class QuickTilePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getSummary() = context.getString(R.string.quick_settings_tile_add_summary)

    override fun getTitle() = context.getString(R.string.quick_settings_tile_add_title)

    override fun onClick() {
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        statusBarManager.requestAddTileService(
            ComponentName(context, QuickTileService::class.java),
            context.getString(R.string.quick_settings_tile_action),
            Icon.createWithResource(context, R.drawable.ic_tile),
            context.mainExecutor
        ) {
            when (it) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {
                    parent?.removePreference(this)
                    --preferenceManager.preferenceScreen.initialExpandedChildrenCount
                }
                StatusBarManager.TILE_ADD_REQUEST_ERROR_MISMATCHED_PACKAGE,
                StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS,
                StatusBarManager.TILE_ADD_REQUEST_ERROR_BAD_COMPONENT,
                StatusBarManager.TILE_ADD_REQUEST_ERROR_NOT_CURRENT_USER,
                StatusBarManager.TILE_ADD_REQUEST_ERROR_APP_NOT_IN_FOREGROUND,
                StatusBarManager.TILE_ADD_REQUEST_ERROR_NO_STATUS_BAR_SERVICE ->
                    Toast.makeText(context, context.getString(R.string.quick_settings_tile_add_failure, it), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
