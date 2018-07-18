/*
 * Copyright © 2018 Harsh Shandilya <msfjarvis@gmail.com>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget;

import android.support.v7.widget.RecyclerView;

import com.wireguard.android.R;

public abstract class CustomRecyclerViewScrollListener extends RecyclerView.OnScrollListener {

    private int scrollDist;
    private boolean isVisible = true;
    private static int flingThreshold;

    @Override
    public void onScrolled(final RecyclerView recyclerView, final int dx, final int dy) {
        super.onScrolled(recyclerView, dx, dy);

        if (flingThreshold == 0)
            flingThreshold = recyclerView.getResources().getDimensionPixelSize(R.dimen.design_fab_size_normal) / 2;

        if (isVisible && scrollDist >= flingThreshold) {
            hide();
            scrollDist = 0;
            isVisible = false;
        } else if (!isVisible && scrollDist <= -flingThreshold) {
            show();
            scrollDist = 0;
            isVisible = true;
        }

        if (isVisible ? dy > 0 : dy < 0) {
            scrollDist += dy;
        }
    }

    public abstract void show();
    public abstract void hide();
}
