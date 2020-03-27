/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar

/**
 * Standalone utilities for interacting with the system clipboard.
 */
object ClipboardUtils {
    @JvmStatic
    fun copyTextView(view: View) {
        if (view !is TextView)
            return
        val text = view.text
        if (text == null || text.length == 0) return
        val service = view.getContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return
        val description = view.getContentDescription()
        service.setPrimaryClip(ClipData.newPlainText(description, text))
        Snackbar.make(view, "$description copied to clipboard", Snackbar.LENGTH_LONG).show()
    }
}
