/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.google.android.material.card.MaterialCardView
import com.wireguard.android.R

class TvCardView(context: Context?, attrs: AttributeSet?) : MaterialCardView(context, attrs) {
    var isUp: Boolean = false
        set(value) {
            field = value
            refreshDrawableState()
        }
    var isDeleting: Boolean = false
        set(value) {
            field = value
            refreshDrawableState()
        }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        if (isUp || isDeleting) {
            val drawableState = super.onCreateDrawableState(extraSpace + (if (isUp) 1 else 0) + (if (isDeleting) 1 else 0))
            if (isUp) {
                View.mergeDrawableStates(drawableState, STATE_IS_UP)
            }
            if (isDeleting) {
                View.mergeDrawableStates(drawableState, STATE_IS_DELETING)
            }
            return drawableState
        }
        return super.onCreateDrawableState(extraSpace)
    }

    companion object {
        private val STATE_IS_UP = intArrayOf(R.attr.state_isUp)
        private val STATE_IS_DELETING = intArrayOf(R.attr.state_isDeleting)
    }
}