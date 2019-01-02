/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget.fab;

import androidx.recyclerview.widget.RecyclerView;

public class FloatingActionsMenuRecyclerViewScrollListener extends RecyclerView.OnScrollListener {
    private static final float SCALE_FACTOR = 1.5f;
    private final FloatingActionsMenu menu;

    public FloatingActionsMenuRecyclerViewScrollListener(final FloatingActionsMenu menu) {
        this.menu = menu;
    }

    private static float bound(final float min, final float proposal, final float max) {
        return Math.min(max, Math.max(min, proposal));
    }

    @Override
    public void onScrolled(final RecyclerView recyclerView, final int dx, final int dy) {
        super.onScrolled(recyclerView, dx, dy);
        menu.setScrollYTranslation(bound(0, menu.getScrollYTranslation() + dy * SCALE_FACTOR, menu.getMeasuredHeight() - menu.getTranslationY()));
    }
}
