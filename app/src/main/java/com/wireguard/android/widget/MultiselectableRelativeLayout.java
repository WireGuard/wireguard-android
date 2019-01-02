/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import com.wireguard.android.R;

public class MultiselectableRelativeLayout extends RelativeLayout {
    private static final int[] STATE_MULTISELECTED = {R.attr.state_multiselected};
    private boolean multiselected;

    public MultiselectableRelativeLayout(final Context context) {
        super(context);
    }

    public MultiselectableRelativeLayout(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public MultiselectableRelativeLayout(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MultiselectableRelativeLayout(final Context context, final AttributeSet attrs, final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected int[] onCreateDrawableState(final int extraSpace) {
        if (multiselected) {
            final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
            mergeDrawableStates(drawableState, STATE_MULTISELECTED);
            return drawableState;
        }
        return super.onCreateDrawableState(extraSpace);
    }

    public void setMultiSelected(final boolean on) {
        if (!multiselected) {
            multiselected = true;
            refreshDrawableState();
        }
        setActivated(on);
    }

    public void setSingleSelected(final boolean on) {
        if (multiselected) {
            multiselected = false;
            refreshDrawableState();
        }
        setActivated(on);
    }
}
