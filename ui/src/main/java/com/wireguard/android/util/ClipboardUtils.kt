/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.util

import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.widget.TextView
import androidx.core.content.getSystemService
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.wireguard.android.R

/**
 * Standalone utilities for interacting with the system clipboard.
 */
object ClipboardUtils {
    @JvmStatic
    fun copyTextView(view: View) {
        val data = when (view) {
            is TextInputEditText -> Pair(view.editableText, view.hint)
            is TextView -> Pair(view.text, view.contentDescription)
            else -> return
        }
        if (data.first == null || data.first.isEmpty()) {
            return
        }
        val service = view.context.getSystemService<ClipboardManager>() ?: return
        service.setPrimaryClip(ClipData.newPlainText(data.second, data.first))
        Snackbar.make(view, view.context.getString(R.string.copied_to_clipboard, data.second), Snackbar.LENGTH_LONG).show()
    }
}
