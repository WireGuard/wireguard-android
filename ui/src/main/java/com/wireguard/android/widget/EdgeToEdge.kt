/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.widget

import android.view.View
import android.view.ViewGroup
import androidx.core.view.*
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

/**
 * A utility for edge-to-edge display. It provides several features needed to make the app
 * displayed edge-to-edge on Android Q with gestural navigation.
 */

object EdgeToEdge {
    @JvmStatic
    fun setUpRoot(root: ViewGroup) {
        root.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    @JvmStatic
    fun setUpScrollingContent(scrollingContent: ViewGroup, fab: ExtendedFloatingActionButton?) {
        val originalPaddingLeft = scrollingContent.paddingLeft
        val originalPaddingRight = scrollingContent.paddingRight
        val originalPaddingBottom = scrollingContent.paddingBottom

        val fabPaddingBottom = fab?.height ?: 0

        val originalMarginTop = scrollingContent.marginTop

        scrollingContent.setOnApplyWindowInsetsListener { _, windowInsets ->
            scrollingContent.updatePadding(
                left = originalPaddingLeft + windowInsets.systemWindowInsetLeft,
                right = originalPaddingRight + windowInsets.systemWindowInsetRight,
                bottom = originalPaddingBottom + fabPaddingBottom + windowInsets.systemWindowInsetBottom
            )
            scrollingContent.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = originalMarginTop + windowInsets.systemWindowInsetTop
            }
            windowInsets
        }
    }

    @JvmStatic
    fun setUpFAB(fab: ExtendedFloatingActionButton) {
        val originalMarginLeft = fab.marginLeft
        val originalMarginRight = fab.marginRight
        val originalMarginBottom = fab.marginBottom
        fab.setOnApplyWindowInsetsListener { _, windowInsets ->
            fab.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = originalMarginLeft + windowInsets.systemWindowInsetLeft
                rightMargin = originalMarginRight + windowInsets.systemWindowInsetRight
                bottomMargin = originalMarginBottom + windowInsets.systemWindowInsetBottom
            }
            windowInsets
        }
    }
}
