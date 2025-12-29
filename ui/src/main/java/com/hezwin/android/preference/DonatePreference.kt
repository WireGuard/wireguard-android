/*
 * Copyright © 2017-2025 HEZWIN LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.hezwin.android.preference

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hezwin.android.R
import com.hezwin.android.updater.Updater
import com.hezwin.android.util.ErrorMessages
import androidx.core.net.toUri

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
        intent.data = "https://www.hezwin.com/donations/".toUri()
        try {
            context.startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(context, ErrorMessages[e], Toast.LENGTH_SHORT).show()
        }
    }
}
