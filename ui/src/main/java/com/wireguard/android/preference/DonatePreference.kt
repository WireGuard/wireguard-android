/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.wireguard.android.R
import com.wireguard.android.util.ErrorMessages

class DonatePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getSummary() = context.getString(R.string.donate_summary)

    override fun getTitle() = context.getString(R.string.donate_title)

    override fun onClick() {
        val installer = try {
            val packageName = context.packageName
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName ?: ""
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName) ?: ""
            }
        } catch (_: Throwable) {
            ""
        }

        /* Google Play Store forbids links to our donation page. */
        if (installer == "com.android.vending") {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.donate_title)
                .setMessage(R.string.donate_google_play_disappointment)
                .show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://www.wireguard.com/donations/")
        try {
            context.startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(context, ErrorMessages[e], Toast.LENGTH_SHORT).show()
        }
    }
}
