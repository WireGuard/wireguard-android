/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.preference

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import androidx.preference.Preference
import com.wireguard.android.Application
import com.wireguard.android.BuildConfig
import com.wireguard.android.R
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.WgQuickBackend
import java.util.Locale

class VersionPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var versionSummary: String? = null

    override fun getSummary() = versionSummary

    override fun getTitle() = context.getString(R.string.version_title, BuildConfig.VERSION_NAME)

    override fun onClick() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://www.wireguard.com/")
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    companion object {
        private fun getBackendPrettyName(context: Context, backend: Backend) = when (backend) {
            is WgQuickBackend -> context.getString(R.string.type_name_kernel_module)
            is GoBackend -> context.getString(R.string.type_name_go_userspace)
            else -> ""
        }
    }

    init {
        Application.getBackendAsync().thenAccept { backend: Backend ->
            versionSummary = getContext().getString(R.string.version_summary_checking, getBackendPrettyName(context, backend).toLowerCase(Locale.ENGLISH))
            Application.getAsyncWorker().supplyAsync(backend::getVersion).whenComplete { version, exception ->
                versionSummary = if (exception == null)
                    getContext().getString(R.string.version_summary, getBackendPrettyName(context, backend), version)
                else
                    getContext().getString(R.string.version_summary_unknown, getBackendPrettyName(context, backend).toLowerCase(Locale.ENGLISH))
                notifyChanged()
            }
        }
    }
}
