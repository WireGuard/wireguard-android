/*
 * Copyright © 2018 Harsh Shandilya <msfjarvis@gmail.com>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget;

import android.support.v7.widget.RecyclerView;

public abstract class CustomRecyclerViewScrollListener extends RecyclerView.OnScrollListener {

    private int scrollDist;
    private boolean isVisible = true;
    private static final float FLING_THRESHOLD = 25;

    @Override
    public void onScrolled(final RecyclerView recyclerView, final int dx, final int dy) {
        super.onScrolled(recyclerView, dx, dy);

        if (isVisible && scrollDist > FLING_THRESHOLD) {
            hide();
            scrollDist = 0;
            isVisible = false;
        } else if (!isVisible && scrollDist < -FLING_THRESHOLD) {
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
