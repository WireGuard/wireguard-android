/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.widget

import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import com.wireguard.crypto.Key

/**
 * InputFilter for entering WireGuard private/public keys encoded with base64.
 */
class KeyInputFilter : InputFilter {
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
            // Restrict characters to the base64 character set.
            // Ensure adding this character does not push the length over the limit.
            if ((dIndex + 1 < Key.Format.BASE64.length && isAllowed(c) ||
                        dIndex + 1 == Key.Format.BASE64.length && c == '=') &&
                dLength + (sIndex - sStart) < Key.Format.BASE64.length
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
        private fun isAllowed(c: Char) = Character.isLetterOrDigit(c) || c == '+' || c == '/'

        @JvmStatic
        fun newInstance() = KeyInputFilter()
    }
}
