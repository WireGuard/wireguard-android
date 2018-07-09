/*
 * Copyright © 2018 The Android Open Source Project
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.FloatProperty;


@TargetApi(Build.VERSION_CODES.N)
public class SlashDrawable extends Drawable {

    private static final float CORNER_RADIUS = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ? 0f : 1f;
    private static final long QS_ANIM_LENGTH = 350;

    private final Path mPath = new Path();
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // These values are derived in un-rotated (vertical) orientation
    private static final float SLASH_WIDTH = 1.8384776f;
    private static final float SLASH_HEIGHT = 28f;
    private static final float CENTER_X = 10.65f;
    private static final float CENTER_Y = 11.869239f;
    private static final float SCALE = 24f;

    // Bottom is derived during animation
    private static final float LEFT = (CENTER_X - (SLASH_WIDTH / 2)) / SCALE;
    private static final float TOP = (CENTER_Y - (SLASH_HEIGHT / 2)) / SCALE;
    private static final float RIGHT = (CENTER_X + (SLASH_WIDTH / 2)) / SCALE;
    // Draw the slash washington-monument style; rotate to no-u-turn style
    private static final float DEFAULT_ROTATION = -45f;

    private Drawable mDrawable;
    private final RectF mSlashRect = new RectF(0, 0, 0, 0);
    private float mRotation;
    private boolean mSlashed;
    private Mode mTintMode;
    private ColorStateList mTintList;
    private boolean mAnimationEnabled = true;

    public SlashDrawable(final Drawable d) {
        setDrawable(d);
    }

    @Override
    public int getIntrinsicHeight() {
        return mDrawable != null ? mDrawable.getIntrinsicHeight(): 0;
    }

    @Override
    public int getIntrinsicWidth() {
        return mDrawable != null ? mDrawable.getIntrinsicWidth(): 0;
    }

    @Override
    protected void onBoundsChange(final Rect bounds) {
        super.onBoundsChange(bounds);
        mDrawable.setBounds(bounds);
    }

    public void setDrawable(final Drawable d) {
        mDrawable = d;
        mDrawable.setCallback(getCallback());
        mDrawable.setBounds(getBounds());
        if (mTintMode != null)
            mDrawable.setTintMode(mTintMode);
        if (mTintList != null)
            mDrawable.setTintList(mTintList);
        invalidateSelf();
    }

    public void setRotation(final float rotation) {
        if (mRotation == rotation)
            return;
        mRotation = rotation;
        invalidateSelf();
    }

    public void setAnimationEnabled(final boolean enabled) {
        mAnimationEnabled = enabled;
    }

    // Animate this value on change
    private float mCurrentSlashLength;
    private static final FloatProperty mSlashLengthProp = new FloatProperty<SlashDrawable>("slashLength") {
        @Override
        public void setValue(final SlashDrawable object, final float value) {
            object.mCurrentSlashLength = value;
        }

        @Override
        public Float get(final SlashDrawable object) {
            return object.mCurrentSlashLength;
        }
    };

    public void setSlashed(final boolean slashed) {
        if (mSlashed == slashed) return;

        mSlashed = slashed;

        final float end = mSlashed ? SLASH_HEIGHT / SCALE : 0f;
        final float start = mSlashed ? 0f : SLASH_HEIGHT / SCALE;

        if (mAnimationEnabled) {
            final ObjectAnimator anim = ObjectAnimator.ofFloat(this, mSlashLengthProp, start, end);
            anim.addUpdateListener((ValueAnimator valueAnimator) -> invalidateSelf());
            anim.setDuration(QS_ANIM_LENGTH);
            anim.start();
        } else {
            mCurrentSlashLength = end;
            invalidateSelf();
        }
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        canvas.save();
        final Matrix m = new Matrix();
        final int width = getBounds().width();
        final int height = getBounds().height();
        final float radiusX = scale(CORNER_RADIUS, width);
        final float radiusY = scale(CORNER_RADIUS, height);
        updateRect(
                scale(LEFT, width),
                scale(TOP, height),
                scale(RIGHT, width),
                scale(TOP + mCurrentSlashLength, height)
        );

        mPath.reset();
        // Draw the slash vertically
        mPath.addRoundRect(mSlashRect, radiusX, radiusY, Direction.CW);
        // Rotate -45 + desired rotation
        m.setRotate(mRotation + DEFAULT_ROTATION, width / 2, height / 2);
        mPath.transform(m);
        canvas.drawPath(mPath, mPaint);

        // Rotate back to vertical
        m.setRotate(-mRotation - DEFAULT_ROTATION, width / 2, height / 2);
        mPath.transform(m);

        // Draw another rect right next to the first, for clipping
        m.setTranslate(mSlashRect.width(), 0);
        mPath.transform(m);
        mPath.addRoundRect(mSlashRect, 1.0f * width, 1.0f * height, Direction.CW);
        m.setRotate(mRotation + DEFAULT_ROTATION, width / 2, height / 2);
        mPath.transform(m);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            canvas.clipPath(mPath, Region.Op.DIFFERENCE);
        else
            canvas.clipOutPath(mPath);

        mDrawable.draw(canvas);
        canvas.restore();
    }

    private float scale(final float frac, final int width) {
        return frac * width;
    }

    private void updateRect(final float left, final float top, final float right, final float bottom) {
        mSlashRect.left = left;
        mSlashRect.top = top;
        mSlashRect.right = right;
        mSlashRect.bottom = bottom;
    }

    @Override
    public void setTint(@ColorInt final int tintColor) {
        super.setTint(tintColor);
        mDrawable.setTint(tintColor);
        mPaint.setColor(tintColor);
    }

    @Override
    public void setTintList(@Nullable final ColorStateList tint) {
        mTintList = tint;
        super.setTintList(tint);
        setDrawableTintList(tint);
        mPaint.setColor(tint == null ? 0 : tint.getDefaultColor());
        invalidateSelf();
    }

    private void setDrawableTintList(@Nullable final ColorStateList tint) {
        mDrawable.setTintList(tint);
    }

    @Override
    public void setTintMode(@NonNull final Mode tintMode) {
        mTintMode = tintMode;
        super.setTintMode(tintMode);
        mDrawable.setTintMode(tintMode);
    }

    @Override
    public void setAlpha(@IntRange(from = 0, to = 255) final int alpha) {
        mDrawable.setAlpha(alpha);
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable final ColorFilter colorFilter) {
        mDrawable.setColorFilter(colorFilter);
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
