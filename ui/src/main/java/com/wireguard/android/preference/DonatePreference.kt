/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import androidx.preference.Preference
import com.wireguard.android.BuildConfig
import com.wireguard.android.R

class DonatePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    override fun getSummary() = context.getString(R.string.donate_summary)

    override fun getTitle() = context.getString(R.string.donate_title)

    override fun onClick() {
        if (BuildConfig.IS_GOOGLE_PLAY) {
            AlertDialog.Builder(context)
                .setTitle(R.string.donate_title)
                .setMessage(R.string.donate_google_play_disappointment)
                .show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://www.wireguard.com/donations/")
        try {
            context.startActivity(intent)
        } catch (ignored: ActivityNotFoundException) {
        }
    }
}