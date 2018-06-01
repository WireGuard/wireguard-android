/*
 * Copyright © 2014 Jerzy Chalupski
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android.widget.fab;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.Shape;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;

import com.wireguard.android.R;

public class AddFloatingActionButton extends FloatingActionButton {
    int mPlusColor;

    public AddFloatingActionButton(final Context context) {
        this(context, null);
    }

    public AddFloatingActionButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public AddFloatingActionButton(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    void init(final Context context, final AttributeSet attributeSet) {
        final TypedArray attr = context.obtainStyledAttributes(attributeSet, R.styleable.AddFloatingActionButton, 0, 0);
        mPlusColor = attr.getColor(R.styleable.AddFloatingActionButton_fab_plusIconColor, FloatingActionButton.getColorFromTheme(context, android.R.attr.colorBackground, android.R.color.white));
        attr.recycle();

        super.init(context, attributeSet);
    }

    /**
     * @return the current Color of plus icon.
     */
    public int getPlusColor() {
        return mPlusColor;
    }

    public void setPlusColor(final int color) {
        if (mPlusColor != color) {
            mPlusColor = color;
            updateBackground();
        }
    }

    public void setPlusColorResId(@ColorRes final int plusColor) {
        setPlusColor(ContextCompat.getColor(getContext(), plusColor));
    }

    @Override
    public void setIcon(@DrawableRes final int icon) {
        throw new UnsupportedOperationException("Use FloatingActionButton if you want to use custom icon");
    }

    @Override
    Drawable getIconDrawable() {
        final float iconSize = getDimension(R.dimen.fab_icon_size);
        final float iconHalfSize = iconSize / 2f;

        final float plusSize = getDimension(R.dimen.fab_plus_icon_size);
        final float plusHalfStroke = getDimension(R.dimen.fab_plus_icon_stroke) / 2f;
        final float plusOffset = (iconSize - plusSize) / 2f;

        final Shape shape = new Shape() {
            @Override
            public void draw(final Canvas canvas, final Paint paint) {
                canvas.drawRect(plusOffset, iconHalfSize - plusHalfStroke, iconSize - plusOffset, iconHalfSize + plusHalfStroke, paint);
                canvas.drawRect(iconHalfSize - plusHalfStroke, plusOffset, iconHalfSize + plusHalfStroke, iconSize - plusOffset, paint);
            }
        };

        final ShapeDrawable drawable = new ShapeDrawable(shape);

        final Paint paint = drawable.getPaint();
        paint.setColor(mPlusColor);
        paint.setStyle(Style.FILL);
        paint.setAntiAlias(true);

        return drawable;
    }
}
