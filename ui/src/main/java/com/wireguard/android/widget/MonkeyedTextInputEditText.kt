/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget

import android.content.Context
import android.text.Editable
import android.text.SpannableStringBuilder
import android.util.AttributeSet
import com.google.android.material.R
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/* Is this no longer necessary?
 * https://github.com/material-components/material-components-android/pull/1163 was merged via
 * https://github.com/material-components/material-components-android/commit/22973ff10305397bfc72fed41684175fee8aeefb .
 * It would seem like we can now set expandedHintEnabled=false
 * TODO: when a new material-components library comes out, switch to expandedHintEnabled=false.
 */
class MonkeyedTextInputEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.editTextStyle) : TextInputEditText(context, attrs, defStyleAttr) {
    @Override
    override fun getText(): Editable? {
        val text = super.getText()
        if (!text.isNullOrEmpty())
            return text
        /* We want this expression in TextInputLayout.java to be true if there's a hint set:
         *        final boolean hasText = editText != null && !TextUtils.isEmpty(editText.getText());
         * But for everyone else it should return the real value, so we check the caller.
         */
        if (!hint.isNullOrEmpty() && Thread.currentThread().stackTrace[3].className == TextInputLayout::class.qualifiedName)
            return SpannableStringBuilder(hint)
        return text
    }
}
