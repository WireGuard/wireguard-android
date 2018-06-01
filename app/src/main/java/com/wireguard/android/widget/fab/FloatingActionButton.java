/*
 * Copyright © 2014 Jerzy Chalupski
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android.widget.fab;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.*;
import android.graphics.Paint.Style;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.*;
import android.graphics.drawable.ShapeDrawable.ShaderFactory;
import android.graphics.drawable.shapes.OvalShape;
import android.support.annotation.*;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.AppCompatImageButton;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import com.wireguard.android.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class FloatingActionButton extends AppCompatImageButton {

    public static final int SIZE_NORMAL = 0;
    public static final int SIZE_MINI = 1;
    int mColorNormal;
    int mColorPressed;
    int mColorDisabled;
    String mTitle;
    boolean mStrokeVisible;
    @DrawableRes
    private int mIcon;
    private Drawable mIconDrawable;
    private int mSize;

    private float mCircleSize;
    private float mShadowRadius;
    private float mShadowOffset;
    private int mDrawableSize;
    public FloatingActionButton(final Context context) {
        this(context, null);
    }

    public FloatingActionButton(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FloatingActionButton(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    public static int getColorFromTheme(final Context context, final int themeResource, @ColorRes final int fallback) {
        final TypedValue typedValue = new TypedValue();
        final Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(themeResource, typedValue, true);
        @ColorInt final int color = typedValue.data;
        return color == 0 ? fallback : color;

    }

    void init(final Context context, final AttributeSet attributeSet) {
        final TypedArray attr = context.obtainStyledAttributes(attributeSet,
                R.styleable.FloatingActionButton, 0, 0);
        mColorNormal = attr.getColor(R.styleable.FloatingActionButton_fab_colorNormal,
                getColorFromTheme(context, android.R.attr.colorAccent, android.R.color.holo_blue_bright));
        mColorPressed = attr.getColor(R.styleable.FloatingActionButton_fab_colorPressed,
                darkenOrLightenColor(mColorNormal)); //TODO(msf): use getColorForState on the accent color from theme instead to get darker states
        mColorDisabled = attr.getColor(R.styleable.FloatingActionButton_fab_colorDisabled,
                ContextCompat.getColor(context, android.R.color.darker_gray)); //TODO(msf): load from theme
        mSize = attr.getInt(R.styleable.FloatingActionButton_fab_size, SIZE_NORMAL);
        mIcon = attr.getResourceId(R.styleable.FloatingActionButton_fab_icon, 0);
        mTitle = attr.getString(R.styleable.FloatingActionButton_fab_title);
        mStrokeVisible = attr.getBoolean(R.styleable.FloatingActionButton_fab_stroke_visible, true);
        attr.recycle();

        updateCircleSize();
        mShadowRadius = getDimension(R.dimen.fab_shadow_radius);
        mShadowOffset = getDimension(R.dimen.fab_shadow_offset);
        updateDrawableSize();

        updateBackground();
    }

    private void updateDrawableSize() {
        mDrawableSize = (int) (mCircleSize + 2 * mShadowRadius);
    }

    private void updateCircleSize() {
        mCircleSize = getDimension(mSize == SIZE_NORMAL ? R.dimen.fab_size_normal : R.dimen.fab_size_mini);
    }

    @FAB_SIZE
    public int getSize() {
        return mSize;
    }

    public void setSize(@FAB_SIZE final int size) {
        if (size != SIZE_MINI && size != SIZE_NORMAL) {
            throw new IllegalArgumentException("Use @FAB_SIZE constants only!");
        }

        if (mSize != size) {
            mSize = size;
            updateCircleSize();
            updateDrawableSize();
            updateBackground();
        }
    }

    public void setIcon(@DrawableRes final int icon) {
        if (mIcon != icon) {
            mIcon = icon;
            mIconDrawable = null;
            updateBackground();
        }
    }

    /**
     * @return the current Color for normal state.
     */
    public int getColorNormal() {
        return mColorNormal;
    }

    public void setColorNormal(final int color) {
        if (mColorNormal != color) {
            mColorNormal = color;
            updateBackground();
        }
    }

    public void setColorNormalResId(@ColorRes final int colorNormal) {
        setColorNormal(ContextCompat.getColor(getContext(), colorNormal));
    }

    /**
     * @return the current color for pressed state.
     */
    public int getColorPressed() {
        return mColorPressed;
    }

    public void setColorPressed(final int color) {
        if (mColorPressed != color) {
            mColorPressed = color;
            updateBackground();
        }
    }

    public void setColorPressedResId(@ColorRes final int colorPressed) {
        setColorPressed(ContextCompat.getColor(getContext(), colorPressed));
    }

    /**
     * @return the current color for disabled state.
     */
    public int getColorDisabled() {
        return mColorDisabled;
    }

    public void setColorDisabled(final int color) {
        if (mColorDisabled != color) {
            mColorDisabled = color;
            updateBackground();
        }
    }

    public void setColorDisabledResId(@ColorRes final int colorDisabled) {
        setColorDisabled(ContextCompat.getColor(getContext(), colorDisabled));
    }

    public boolean isStrokeVisible() {
        return mStrokeVisible;
    }

    public void setStrokeVisible(final boolean visible) {
        if (mStrokeVisible != visible) {
            mStrokeVisible = visible;
            updateBackground();
        }
    }

    float getDimension(@DimenRes final int id) {
        return getResources().getDimension(id);
    }

    TextView getLabelView() {
        return (TextView) getTag(R.id.fab_label);
    }

    public String getTitle() {
        return mTitle;
    }

    public void setTitle(final String title) {
        mTitle = title;
        final TextView label = getLabelView();
        if (label != null) {
            label.setText(title);
        }
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(mDrawableSize, mDrawableSize);
    }

    void updateBackground() {
        final float strokeWidth = getDimension(R.dimen.fab_stroke_width);
        final float halfStrokeWidth = strokeWidth / 2f;

        final LayerDrawable layerDrawable = new LayerDrawable(
                new Drawable[]{
                        getResources().getDrawable(mSize == SIZE_NORMAL ? R.drawable.fab_bg_normal : R.drawable.fab_bg_mini, null),
                        createFillDrawable(strokeWidth),
                        createOuterStrokeDrawable(strokeWidth),
                        getIconDrawable()
                });

        final int iconOffset = (int) (mCircleSize - getDimension(R.dimen.fab_icon_size)) / 2;

        final int circleInsetHorizontal = (int) (mShadowRadius);
        final int circleInsetTop = (int) (mShadowRadius - mShadowOffset);
        final int circleInsetBottom = (int) (mShadowRadius + mShadowOffset);

        layerDrawable.setLayerInset(1,
                circleInsetHorizontal,
                circleInsetTop,
                circleInsetHorizontal,
                circleInsetBottom);

        layerDrawable.setLayerInset(2,
                (int) (circleInsetHorizontal - halfStrokeWidth),
                (int) (circleInsetTop - halfStrokeWidth),
                (int) (circleInsetHorizontal - halfStrokeWidth),
                (int) (circleInsetBottom - halfStrokeWidth));

        layerDrawable.setLayerInset(3,
                circleInsetHorizontal + iconOffset,
                circleInsetTop + iconOffset,
                circleInsetHorizontal + iconOffset,
                circleInsetBottom + iconOffset);

        setBackground(layerDrawable);
    }

    Drawable getIconDrawable() {
        if (mIconDrawable != null) {
            return mIconDrawable;
        } else if (mIcon != 0) {
            return ContextCompat.getDrawable(getContext(), mIcon);
        } else {
            return new ColorDrawable(Color.TRANSPARENT);
        }
    }

    public void setIconDrawable(@NonNull final Drawable iconDrawable) {
        if (mIconDrawable != iconDrawable) {
            mIcon = 0;
            mIconDrawable = iconDrawable;
            updateBackground();
        }
    }

    private StateListDrawable createFillDrawable(final float strokeWidth) {
        final StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{-android.R.attr.state_enabled}, createCircleDrawable(mColorDisabled, strokeWidth));
        drawable.addState(new int[]{android.R.attr.state_pressed}, createCircleDrawable(mColorPressed, strokeWidth));
        drawable.addState(new int[]{}, createCircleDrawable(mColorNormal, strokeWidth));
        return drawable;
    }

    private Drawable createCircleDrawable(final int color, final float strokeWidth) {
        final int alpha = Color.alpha(color);
        final int opaqueColor = opaque(color);

        final ShapeDrawable fillDrawable = new ShapeDrawable(new OvalShape());

        final Paint paint = fillDrawable.getPaint();
        paint.setAntiAlias(true);
        paint.setColor(opaqueColor);

        final Drawable[] layers = {
                fillDrawable,
                createInnerStrokesDrawable(opaqueColor, strokeWidth)
        };

        final LayerDrawable drawable = alpha == 255 || !mStrokeVisible
                ? new LayerDrawable(layers)
                : new TranslucentLayerDrawable(alpha, layers);

        final int halfStrokeWidth = (int) (strokeWidth / 2f);
        drawable.setLayerInset(1, halfStrokeWidth, halfStrokeWidth, halfStrokeWidth, halfStrokeWidth);

        return drawable;
    }

    private static Drawable createOuterStrokeDrawable(final float strokeWidth) {
        final ShapeDrawable shapeDrawable = new ShapeDrawable(new OvalShape());

        final Paint paint = shapeDrawable.getPaint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(strokeWidth);
        paint.setStyle(Style.STROKE);
        paint.setColor(Color.BLACK);
        paint.setAlpha(opacityToAlpha(0.02f));

        return shapeDrawable;
    }

    private static int opacityToAlpha(final float opacity) {
        return (int) (255f * opacity);
    }

    private static int darkenColor(final int argb) {
        return adjustColorBrightness(argb, 0.9f);
    }

    private static int lightenColor(final int argb) {
        return adjustColorBrightness(argb, 1.1f);
    }

    public static int darkenOrLightenColor(final int argb) {
        final float[] hsv = new float[3];
        Color.colorToHSV(argb, hsv);
        final float factor;
        if (hsv[2] < 0.2)
            factor = 1.2f;
        else
            factor = 0.8f;

        hsv[2] = Math.min(hsv[2] * factor, 1f);
        return Color.HSVToColor(Color.alpha(argb), hsv);
    }

    private static int adjustColorBrightness(final int argb, final float factor) {
        final float[] hsv = new float[3];
        Color.colorToHSV(argb, hsv);

        hsv[2] = Math.min(hsv[2] * factor, 1f);

        return Color.HSVToColor(Color.alpha(argb), hsv);
    }

    private static int halfTransparent(final int argb) {
        return Color.argb(
                Color.alpha(argb) / 2,
                Color.red(argb),
                Color.green(argb),
                Color.blue(argb)
        );
    }

    private static int opaque(final int argb) {
        return Color.rgb(
                Color.red(argb),
                Color.green(argb),
                Color.blue(argb)
        );
    }

    private Drawable createInnerStrokesDrawable(final int color, final float strokeWidth) {
        if (!mStrokeVisible) {
            return new ColorDrawable(Color.TRANSPARENT);
        }

        final ShapeDrawable shapeDrawable = new ShapeDrawable(new OvalShape());

        final int bottomStrokeColor = darkenColor(color);
        final int bottomStrokeColorHalfTransparent = halfTransparent(bottomStrokeColor);
        final int topStrokeColor = lightenColor(color);
        final int topStrokeColorHalfTransparent = halfTransparent(topStrokeColor);

        final Paint paint = shapeDrawable.getPaint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(strokeWidth);
        paint.setStyle(Style.STROKE);
        shapeDrawable.setShaderFactory(new ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                return new LinearGradient(width / 2, 0, width / 2, height,
                        new int[]{topStrokeColor, topStrokeColorHalfTransparent, color, bottomStrokeColorHalfTransparent, bottomStrokeColor},
                        new float[]{0f, 0.2f, 0.5f, 0.8f, 1f},
                        TileMode.CLAMP
                );
            }
        });

        return shapeDrawable;
    }

    @Override
    public void setVisibility(final int visibility) {
        final TextView label = getLabelView();
        if (label != null) {
            label.setVisibility(visibility);
        }

        super.setVisibility(visibility);
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({SIZE_NORMAL, SIZE_MINI})
    public @interface FAB_SIZE {
    }

    private static final class TranslucentLayerDrawable extends LayerDrawable {
        private final int mAlpha;

        private TranslucentLayerDrawable(final int alpha, final Drawable... layers) {
            super(layers);
            mAlpha = alpha;
        }

        @Override
        public void draw(final Canvas canvas) {
            final Rect bounds = getBounds();
            canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, mAlpha);
            super.draw(canvas);
            canvas.restore();
        }
    }
}
