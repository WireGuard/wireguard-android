/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.widget

import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned

/**
 * InputFilter for entering WireGuard tunnel display names.
 * Allows Unicode characters including emoji. The underlying interface name
 * is auto-generated when the display name contains non-ASCII characters.
 */
class NameInputFilter : InputFilter {
    override fun filter(
        source: CharSequence,
        sStart: Int, sEnd: Int,
        dest: Spanned,
        dStart: Int, dEnd: Int
    ): CharSequence? {
        var replacement: SpannableStringBuilder? = null
        var rIndex = 0
        val dLength = dest.length
        for (sIndex in sStart until sEnd) {
            val c = source[sIndex]
            val dIndex = dStart + (sIndex - sStart)
            // Allow any non-control character. Display name length limit is 80 characters.
            if (dIndex < DISPLAY_NAME_MAX_LENGTH && isAllowed(c) &&
                dLength + (sIndex - sStart) < DISPLAY_NAME_MAX_LENGTH
            ) {
                ++rIndex
            } else {
                if (replacement == null) replacement = SpannableStringBuilder(source, sStart, sEnd)
                replacement.delete(rIndex, rIndex + 1)
            }
        }
        return replacement
    }

    companion object {
        const val DISPLAY_NAME_MAX_LENGTH = 80

        private fun isAllowed(c: Char): Boolean {
            // Allow anything that isn't a control character or the path separators / and \
            if (Character.isISOControl(c)) return false
            if (c == '/' || c == '\\') return false
            return true
        }

        @JvmStatic
        fun newInstance() = NameInputFilter()
    }
}
