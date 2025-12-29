/*
 * Copyright © 2017-2025 HEZWIN LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hezwin.android.util

import android.content.RestrictionsManager
import androidx.core.content.getSystemService
import com.hezwin.android.Application

object AdminKnobs {
    private val restrictions: RestrictionsManager? = Application.get().getSystemService()
    val disableConfigExport: Boolean
        get() = restrictions?.applicationRestrictions?.getBoolean("disable_config_export", false)
            ?: false
}
