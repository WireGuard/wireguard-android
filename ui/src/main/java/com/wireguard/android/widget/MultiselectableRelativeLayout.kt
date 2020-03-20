/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import com.wireguard.android.R

class MultiselectableRelativeLayout : RelativeLayout {
    private var multiselected = false

    constructor(context: Context?) : super(context) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {}

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        if (multiselected) {
            val drawableState = super.onCreateDrawableState(extraSpace + 1)
            View.mergeDrawableStates(drawableState, STATE_MULTISELECTED)
            return drawableState
        }
        return super.onCreateDrawableState(extraSpace)
    }

    fun setMultiSelected(on: Boolean) {
        if (!multiselected) {
            multiselected = true
            refreshDrawableState()
        }
        isActivated = on
    }

    fun setSingleSelected(on: Boolean) {
        if (multiselected) {
            multiselected = false
            refreshDrawableState()
        }
        isActivated = on
    }

    companion object {
        private val STATE_MULTISELECTED = intArrayOf(R.attr.state_multiselected)
    }
}
