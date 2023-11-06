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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jimberisolation.android.R
import com.jimberisolation.android.updater.Updater
import com.jimberisolation.android.util.ErrorMessages

class DonatePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getSummary() = context.getString(R.string.donate_summary)

    override fun getTitle() = context.getString(R.string.donate_title)

    override fun onClick() {
        /* Google Play Store forbids links to our donation page. */
        if (Updater.installerIsGooglePlay(context)) {
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
