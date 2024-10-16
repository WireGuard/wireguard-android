/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.jimberisolation.android.preference

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.Preference
import com.jimberisolation.android.Application
import com.jimberisolation.android.BuildConfig
import com.jimberisolation.android.R
import com.jimberisolation.android.activity.SignInActivity
import com.jimberisolation.android.backend.Backend
import com.jimberisolation.android.backend.GoBackend
import com.jimberisolation.android.backend.WgQuickBackend
import com.jimberisolation.android.model.TunnelManager
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.ErrorMessages
import com.jimberisolation.android.util.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logout

class SignOutPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getTitle() = "Sign out"

    override fun onClick() {
        lifecycleScope.launch {
            try {
                logout()

                val tunnels = Application.getTunnelManager().getTunnels()
                tunnels.forEach { tunnel ->
                    tunnel.deleteAsync()
                }

                val intent = Intent(context, SignInActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)

                SharedStorage.getInstance().clearAll()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Logout failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    init {
        lifecycleScope.launch {
        }
    }
}
