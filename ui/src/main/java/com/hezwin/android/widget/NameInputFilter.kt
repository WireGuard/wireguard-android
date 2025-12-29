/*
 * Copyright © 2017-2025 HEZWIN LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hezwin.android.widget

import android.text.InputFilter
import android.text.SpannableStringBuilder
import android.text.Spanned
import com.hezwin.android.backend.Tunnel

/**
 * InputFilter for entering HEZWIN configuration names (Linux interface names).
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
            // Restrict characters to those valid in interfaces.
            // Ensure adding this character does not push the length over the limit.
            if (dIndex < Tunnel.NAME_MAX_LENGTH && isAllowed(c) &&
                dLength + (sIndex - sStart) < Tunnel.NAME_MAX_LENGTH
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
        private fun isAllowed(c: Char) = Character.isLetterOrDigit(c) || "_=+.-".indexOf(c) >= 0

        @JvmStatic
        fun newInstance() = NameInputFilter()
    }
}
