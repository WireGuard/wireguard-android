/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.util

import android.view.ContextThemeWrapper
import androidx.preference.Preference
import com.wireguard.android.activity.SettingsActivity

object FragmentUtils {
    fun getPrefActivity(preference: Preference): SettingsActivity {
        val context = preference.context
        if (context is ContextThemeWrapper) {
            if (context is SettingsActivity) {
                return context
            }
        }
        throw IllegalStateException("Failed to resolve SettingsActivity")
    }
}
