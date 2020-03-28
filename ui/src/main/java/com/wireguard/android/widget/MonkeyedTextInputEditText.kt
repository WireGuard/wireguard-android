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

class MonkeyedTextInputEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.editTextStyle) : TextInputEditText(context, attrs, defStyleAttr) {
    @Override
    override fun getText(): Editable? {
        val text = super.getText()
        if (!text.isNullOrEmpty())
            return text
        /* We want this expression in TextInputLayout.java to be true:
         *        final boolean hasText = editText != null && !TextUtils.isEmpty(editText.getText());
         * But for everyone else it should return the real value, so we check the caller.
         */
        if (Thread.currentThread().stackTrace[3].className == TextInputLayout::class.qualifiedName)
            return SpannableStringBuilder(" ")
        return text
    }
}
