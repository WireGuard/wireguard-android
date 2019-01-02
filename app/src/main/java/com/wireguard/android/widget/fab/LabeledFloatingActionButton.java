/*
 * Copyright © 2014 Jerzy Chalupski
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget.fab;

import android.content.Context;
import android.content.res.TypedArray;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import android.util.AttributeSet;
import android.widget.TextView;

import com.wireguard.android.R;

public class LabeledFloatingActionButton extends FloatingActionButton {

    @Nullable private final String title;

    public LabeledFloatingActionButton(final Context context) {
        this(context, null);
    }

    public LabeledFloatingActionButton(final Context context, @Nullable final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LabeledFloatingActionButton(final Context context, @Nullable final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray attr = context.obtainStyledAttributes(attrs, R.styleable.LabeledFloatingActionButton, 0, 0);
        title = attr.getString(R.styleable.LabeledFloatingActionButton_fab_title);
        attr.recycle();
    }

    @Nullable
    TextView getLabelView() {
        return (TextView) getTag(R.id.fab_label);
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Override
    public void setVisibility(final int visibility) {
        final TextView label = getLabelView();
        if (label != null) {
            label.setVisibility(visibility);
        }

        super.setVisibility(visibility);
    }

}
