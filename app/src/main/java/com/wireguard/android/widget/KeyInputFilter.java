/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget;

import androidx.annotation.Nullable;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import com.wireguard.crypto.Key;

/**
 * InputFilter for entering WireGuard private/public keys encoded with base64.
 */

public class KeyInputFilter implements InputFilter {
    private static boolean isAllowed(final char c) {
        return Character.isLetterOrDigit(c) || c == '+' || c == '/';
    }

    public static InputFilter newInstance() {
        return new KeyInputFilter();
    }

    @Nullable
    @Override
    public CharSequence filter(final CharSequence source,
                               final int sStart, final int sEnd,
                               final Spanned dest,
                               final int dStart, final int dEnd) {
        SpannableStringBuilder replacement = null;
        int rIndex = 0;
        final int dLength = dest.length();
        for (int sIndex = sStart; sIndex < sEnd; ++sIndex) {
            final char c = source.charAt(sIndex);
            final int dIndex = dStart + (sIndex - sStart);
            // Restrict characters to the base64 character set.
            // Ensure adding this character does not push the length over the limit.
            if (((dIndex + 1 < Key.Format.BASE64.getLength() && isAllowed(c)) ||
                    (dIndex + 1 == Key.Format.BASE64.getLength() && c == '=')) &&
                    dLength + (sIndex - sStart) < Key.Format.BASE64.getLength()) {
                ++rIndex;
            } else {
                if (replacement == null)
                    replacement = new SpannableStringBuilder(source, sStart, sEnd);
                replacement.delete(rIndex, rIndex + 1);
            }
        }
        return replacement;
    }
}
