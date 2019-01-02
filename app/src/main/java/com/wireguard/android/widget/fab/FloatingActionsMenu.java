/*
 * Copyright © 2014 Jerzy Chalupski
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget.fab;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.content.res.ResourcesCompat;
import androidx.appcompat.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import com.wireguard.android.R;

public class FloatingActionsMenu extends ViewGroup {
    public static final int EXPAND_DOWN = 1;
    public static final int EXPAND_LEFT = 2;
    public static final int EXPAND_RIGHT = 3;
    public static final int EXPAND_UP = 0;
    public static final int LABELS_ON_LEFT_SIDE = 0;
    public static final int LABELS_ON_RIGHT_SIDE = 1;
    private static final TimeInterpolator ALPHA_EXPAND_INTERPOLATOR = new DecelerateInterpolator();
    private static final int ANIMATION_DURATION = 300;
    private static final boolean BROKEN_LABEL_STYLE = Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1 && Build.BRAND.equals("ASUS");
    private static final float COLLAPSED_PLUS_ROTATION = 0f;
    private static final TimeInterpolator COLLAPSE_INTERPOLATOR = new DecelerateInterpolator(3f);
    private static final float EXPANDED_PLUS_ROTATION = 90f + 45f;
    private static final TimeInterpolator EXPAND_INTERPOLATOR = new OvershootInterpolator();
    private final AnimatorSet mCollapseAnimation = new AnimatorSet().setDuration(ANIMATION_DURATION);
    private final AnimatorSet mExpandAnimation = new AnimatorSet().setDuration(ANIMATION_DURATION);
    private final Rect touchArea = new Rect(0, 0, 0, 0);
    private float behaviorYTranslation;
    @Nullable private FloatingActionButton mAddButton;
    private int mButtonSpacing;
    private int mButtonsCount;
    private int mExpandDirection;
    private boolean mExpanded;
    private int mLabelsMargin;
    private int mLabelsPosition;
    private int mLabelsStyle;
    private int mLabelsVerticalOffset;
    @Nullable private OnFloatingActionsMenuUpdateListener mListener;
    private int mMaxButtonHeight;
    private int mMaxButtonWidth;
    @Nullable private RotatingDrawable mRotatingDrawable;
    @Nullable private TouchDelegateGroup mTouchDelegateGroup;
    private float scrollYTranslation;

    public FloatingActionsMenu(final Context context) {
        this(context, null);
    }

    public FloatingActionsMenu(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FloatingActionsMenu(final Context context, @Nullable final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private static int adjustForOvershoot(final int dimension) {
        return dimension * 12 / 10;
    }

    public void addButton(final LabeledFloatingActionButton button) {
        addView(button, mButtonsCount - 1);
        mButtonsCount++;

        if (mLabelsStyle != 0) {
            createLabels();
        }
    }

    public void collapse() {
        collapse(false);
    }

    private void collapse(final boolean immediately) {
        if (mExpanded) {
            mExpanded = false;
            mTouchDelegateGroup.setEnabled(false);
            mCollapseAnimation.setDuration(immediately ? 0 : ANIMATION_DURATION);
            mCollapseAnimation.start();
            mExpandAnimation.cancel();

            if (mListener != null) {
                mListener.onMenuCollapsed();
            }
        }
    }

    public void collapseImmediately() {
        collapse(true);
    }

    private void createAddButton(final Context context) {
        final RotatingDrawable rotatingDrawable = new RotatingDrawable(ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_action_add_white, context.getTheme()));
        mRotatingDrawable = rotatingDrawable;

        final TimeInterpolator interpolator = new OvershootInterpolator();

        final ObjectAnimator collapseAnimator = ObjectAnimator.ofFloat(rotatingDrawable, "rotation", EXPANDED_PLUS_ROTATION, COLLAPSED_PLUS_ROTATION);
        final ObjectAnimator expandAnimator = ObjectAnimator.ofFloat(rotatingDrawable, "rotation", COLLAPSED_PLUS_ROTATION, EXPANDED_PLUS_ROTATION);

        collapseAnimator.setInterpolator(interpolator);
        expandAnimator.setInterpolator(interpolator);

        mExpandAnimation.play(expandAnimator);
        mCollapseAnimation.play(collapseAnimator);

        mAddButton = new FloatingActionButton(context);
        mAddButton.setImageDrawable(rotatingDrawable);
        mAddButton.setId(R.id.fab_expand_menu_button);
        mAddButton.setOnClickListener(v -> toggle());

        addView(mAddButton, super.generateDefaultLayoutParams());
        mButtonsCount++;
    }

    private void createLabels() {
        final Context context = BROKEN_LABEL_STYLE ? getContext() : new ContextThemeWrapper(getContext(), mLabelsStyle);

        for (int i = 0; i < mButtonsCount; i++) {
            final FloatingActionButton button = (FloatingActionButton) getChildAt(i);

            if (button instanceof LabeledFloatingActionButton) {
                final String title = ((LabeledFloatingActionButton) button).getTitle();

                final AppCompatTextView label = new AppCompatTextView(context);
                if (!BROKEN_LABEL_STYLE)
                    label.setTextAppearance(context, mLabelsStyle);
                label.setText(title);
                addView(label);

                button.setTag(R.id.fab_label, label);
            }
        }
    }

    public void expand() {
        if (!mExpanded) {
            mExpanded = true;
            mTouchDelegateGroup.setEnabled(true);
            mCollapseAnimation.cancel();
            mExpandAnimation.start();

            if (mListener != null) {
                mListener.onMenuExpanded();
            }
        }
    }

    private boolean expandsHorizontally() {
        return mExpandDirection == EXPAND_LEFT || mExpandDirection == EXPAND_RIGHT;
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(super.generateDefaultLayoutParams());
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(final AttributeSet attrs) {
        return new LayoutParams(super.generateLayoutParams(attrs));
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(final ViewGroup.LayoutParams p) {
        return new LayoutParams(super.generateLayoutParams(p));
    }

    public float getBehaviorYTranslation() {
        return behaviorYTranslation;
    }

    public float getScrollYTranslation() {
        return scrollYTranslation;
    }

    private void init(final Context context, @Nullable final AttributeSet attributeSet) {
        mButtonSpacing = (int) (getResources().getDimension(R.dimen.fab_actions_spacing));
        mLabelsMargin = getResources().getDimensionPixelSize(R.dimen.fab_labels_margin);
        mLabelsVerticalOffset = getResources().getDimensionPixelSize(R.dimen.fab_shadow_offset);

        mTouchDelegateGroup = new TouchDelegateGroup(this);
        setTouchDelegate(mTouchDelegateGroup);

        final TypedArray attr = context.obtainStyledAttributes(attributeSet, R.styleable.FloatingActionsMenu, 0, 0);
        mExpandDirection = attr.getInt(R.styleable.FloatingActionsMenu_fab_expandDirection, EXPAND_UP);
        mLabelsStyle = attr.getResourceId(R.styleable.FloatingActionsMenu_fab_labelStyle, 0);
        mLabelsPosition = attr.getInt(R.styleable.FloatingActionsMenu_fab_labelsPosition, LABELS_ON_LEFT_SIDE);
        attr.recycle();

        if (mLabelsStyle != 0 && expandsHorizontally()) {
            throw new IllegalStateException("Action labels in horizontal expand orientation are not supported");
        }

        createAddButton(context);
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        bringChildToFront(mAddButton);
        mButtonsCount = getChildCount();

        if (mLabelsStyle != 0) {
            createLabels();
        }
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
        switch (mExpandDirection) {
            case EXPAND_UP:
            case EXPAND_DOWN:
                final boolean expandUp = mExpandDirection == EXPAND_UP;

                if (changed) {
                    mTouchDelegateGroup.clearTouchDelegates();
                }

                final int addButtonY = expandUp ? b - t - mAddButton.getMeasuredHeight() : 0;
                // Ensure mAddButton is centered on the line where the buttons should be
                final int buttonsHorizontalCenter = (mLabelsPosition == LABELS_ON_LEFT_SIDE
                        ? r - l - mMaxButtonWidth / 2
                        : mMaxButtonWidth / 2);
                final int addButtonLeft = buttonsHorizontalCenter - mAddButton.getMeasuredWidth() / 2;
                mAddButton.layout(addButtonLeft, addButtonY, addButtonLeft + mAddButton.getMeasuredWidth(), addButtonY + mAddButton.getMeasuredHeight());

                final int labelsOffset = mMaxButtonWidth / 2 + mLabelsMargin;
                final int labelsXNearButton = mLabelsPosition == LABELS_ON_LEFT_SIDE
                        ? buttonsHorizontalCenter - labelsOffset
                        : buttonsHorizontalCenter + labelsOffset;

                int nextY = expandUp ?
                        addButtonY - mButtonSpacing :
                        addButtonY + mAddButton.getMeasuredHeight() + mButtonSpacing;

                for (int i = mButtonsCount - 1; i >= 0; i--) {
                    final View child = getChildAt(i);

                    if (child == mAddButton || child.getVisibility() == GONE) continue;

                    final int childX = buttonsHorizontalCenter - child.getMeasuredWidth() / 2;
                    final int childY = expandUp ? nextY - child.getMeasuredHeight() : nextY;
                    child.layout(childX, childY, childX + child.getMeasuredWidth(), childY + child.getMeasuredHeight());

                    final float collapsedTranslation = addButtonY - childY;
                    final float expandedTranslation = 0f;

                    child.setTranslationY(mExpanded ? expandedTranslation : collapsedTranslation);
                    child.setAlpha(mExpanded ? 1f : 0f);

                    final LayoutParams params = (LayoutParams) child.getLayoutParams();
                    params.mCollapseDir.setFloatValues(expandedTranslation, collapsedTranslation);
                    params.mExpandDir.setFloatValues(collapsedTranslation, expandedTranslation);
                    params.setAnimationsTarget(child);

                    final View label = (View) child.getTag(R.id.fab_label);
                    if (label != null) {
                        final int labelXAwayFromButton = mLabelsPosition == LABELS_ON_LEFT_SIDE
                                ? labelsXNearButton - label.getMeasuredWidth()
                                : labelsXNearButton + label.getMeasuredWidth();

                        final int labelLeft = mLabelsPosition == LABELS_ON_LEFT_SIDE
                                ? labelXAwayFromButton
                                : labelsXNearButton;

                        final int labelRight = mLabelsPosition == LABELS_ON_LEFT_SIDE
                                ? labelsXNearButton
                                : labelXAwayFromButton;

                        final int labelTop = childY - mLabelsVerticalOffset + (child.getMeasuredHeight() - label.getMeasuredHeight()) / 2;

                        label.layout(labelLeft, labelTop, labelRight, labelTop + label.getMeasuredHeight());

                        touchArea.set(Math.min(childX, labelLeft),
                                childY - mButtonSpacing / 2,
                                Math.max(childX + child.getMeasuredWidth(), labelRight),
                                childY + child.getMeasuredHeight() + mButtonSpacing / 2);
                        mTouchDelegateGroup.addTouchDelegate(new TouchDelegate(new Rect(touchArea), child));

                        label.setTranslationY(mExpanded ? expandedTranslation : collapsedTranslation);
                        label.setAlpha(mExpanded ? 1f : 0f);

                        final LayoutParams labelParams = (LayoutParams) label.getLayoutParams();
                        labelParams.mCollapseDir.setFloatValues(expandedTranslation, collapsedTranslation);
                        labelParams.mExpandDir.setFloatValues(collapsedTranslation, expandedTranslation);
                        labelParams.setAnimationsTarget(label);
                    }

                    nextY = expandUp ?
                            childY - mButtonSpacing :
                            childY + child.getMeasuredHeight() + mButtonSpacing;
                }
                break;

            case EXPAND_LEFT:
            case EXPAND_RIGHT:
                final boolean expandLeft = mExpandDirection == EXPAND_LEFT;

                final int addButtonX = expandLeft ? r - l - mAddButton.getMeasuredWidth() : 0;
                // Ensure mAddButton is centered on the line where the buttons should be
                final int addButtonTop = b - t - mMaxButtonHeight + (mMaxButtonHeight - mAddButton.getMeasuredHeight()) / 2;
                mAddButton.layout(addButtonX, addButtonTop, addButtonX + mAddButton.getMeasuredWidth(), addButtonTop + mAddButton.getMeasuredHeight());

                int nextX = expandLeft ?
                        addButtonX - mButtonSpacing :
                        addButtonX + mAddButton.getMeasuredWidth() + mButtonSpacing;

                for (int i = mButtonsCount - 1; i >= 0; i--) {
                    final View child = getChildAt(i);

                    if (child == mAddButton || child.getVisibility() == GONE) continue;

                    final int childX = expandLeft ? nextX - child.getMeasuredWidth() : nextX;
                    final int childY = addButtonTop + (mAddButton.getMeasuredHeight() - child.getMeasuredHeight()) / 2;
                    child.layout(childX, childY, childX + child.getMeasuredWidth(), childY + child.getMeasuredHeight());

                    final float collapsedTranslation = addButtonX - childX;
                    final float expandedTranslation = 0f;

                    child.setTranslationX(mExpanded ? expandedTranslation : collapsedTranslation);
                    child.setAlpha(mExpanded ? 1f : 0f);

                    final LayoutParams params = (LayoutParams) child.getLayoutParams();
                    params.mCollapseDir.setFloatValues(expandedTranslation, collapsedTranslation);
                    params.mExpandDir.setFloatValues(collapsedTranslation, expandedTranslation);
                    params.setAnimationsTarget(child);

                    nextX = expandLeft ?
                            childX - mButtonSpacing :
                            childX + child.getMeasuredWidth() + mButtonSpacing;
                }

                break;
        }
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        measureChildren(widthMeasureSpec, heightMeasureSpec);

        int width = 0;
        int height = 0;

        mMaxButtonWidth = 0;
        mMaxButtonHeight = 0;
        int maxLabelWidth = 0;

        for (int i = 0; i < mButtonsCount; i++) {
            final View child = getChildAt(i);

            if (child.getVisibility() == GONE) {
                continue;
            }

            switch (mExpandDirection) {
                case EXPAND_UP:
                case EXPAND_DOWN:
                    mMaxButtonWidth = Math.max(mMaxButtonWidth, child.getMeasuredWidth());
                    height += child.getMeasuredHeight();
                    break;
                case EXPAND_LEFT:
                case EXPAND_RIGHT:
                    width += child.getMeasuredWidth();
                    mMaxButtonHeight = Math.max(mMaxButtonHeight, child.getMeasuredHeight());
                    break;
            }

            if (!expandsHorizontally()) {
                final TextView label = (TextView) child.getTag(R.id.fab_label);
                if (label != null) {
                    maxLabelWidth = Math.max(maxLabelWidth, label.getMeasuredWidth());
                }
            }
        }

        if (expandsHorizontally()) {
            height = mMaxButtonHeight;
        } else {
            width = mMaxButtonWidth + (maxLabelWidth > 0 ? maxLabelWidth + mLabelsMargin : 0);
        }

        switch (mExpandDirection) {
            case EXPAND_UP:
            case EXPAND_DOWN:
                height += mButtonSpacing * (mButtonsCount - 1);
                height = adjustForOvershoot(height);
                break;
            case EXPAND_LEFT:
            case EXPAND_RIGHT:
                width += mButtonSpacing * (mButtonsCount - 1);
                width = adjustForOvershoot(width);
                break;
        }

        setMeasuredDimension(width, height);
    }

    @Override
    public void onRestoreInstanceState(final Parcelable state) {
        if (state instanceof SavedState) {
            final SavedState savedState = (SavedState) state;
            mExpanded = savedState.mExpanded;
            mTouchDelegateGroup.setEnabled(mExpanded);

            if (mRotatingDrawable != null) {
                mRotatingDrawable.setRotation(mExpanded ? EXPANDED_PLUS_ROTATION : COLLAPSED_PLUS_ROTATION);
            }

            super.onRestoreInstanceState(savedState.getSuperState());
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        final SavedState savedState = new SavedState(superState);
        savedState.mExpanded = mExpanded;

        return savedState;
    }

    public void removeButton(final LabeledFloatingActionButton button) {
        removeView(button.getLabelView());
        removeView(button);
        button.setTag(R.id.fab_label, null);
        mButtonsCount--;
    }

    public void setBehaviorYTranslation(final float behaviorYTranslation) {
        this.behaviorYTranslation = behaviorYTranslation;
        setTranslationY(behaviorYTranslation + scrollYTranslation);
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);

        mAddButton.setEnabled(enabled);
    }

    public void setOnFloatingActionsMenuUpdateListener(final OnFloatingActionsMenuUpdateListener listener) {
        mListener = listener;
    }

    public void setScrollYTranslation(final float scrollYTranslation) {
        this.scrollYTranslation = scrollYTranslation;
        setTranslationY(behaviorYTranslation + scrollYTranslation);
    }

    public void toggle() {
        if (mExpanded) {
            collapse();
        } else {
            expand();
        }
    }

    public interface OnFloatingActionsMenuUpdateListener {
        void onMenuCollapsed();

        void onMenuExpanded();
    }

    private static class RotatingDrawable extends LayerDrawable {
        private float mRotation;

        RotatingDrawable(final Drawable drawable) {
            super(new Drawable[]{drawable});
        }

        @Override
        public void draw(final Canvas canvas) {
            canvas.save();
            canvas.rotate(mRotation, getBounds().centerX(), getBounds().centerY());
            super.draw(canvas);
            canvas.restore();
        }

        @SuppressWarnings("UnusedDeclaration")
        public float getRotation() {
            return mRotation;
        }

        @Keep
        @SuppressWarnings("UnusedDeclaration")
        public void setRotation(final float rotation) {
            mRotation = rotation;
            invalidateSelf();
        }
    }

    public static class SavedState extends BaseSavedState {
        public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {

            @Override
            public SavedState createFromParcel(final Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(final int size) {
                return new SavedState[size];
            }
        };
        private boolean mExpanded;

        public SavedState(final Parcelable parcel) {
            super(parcel);
        }

        private SavedState(final Parcel in) {
            super(in);
            mExpanded = in.readInt() == 1;
        }

        @Override
        public void writeToParcel(final Parcel out, final int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mExpanded ? 1 : 0);
        }
    }

    private class LayoutParams extends ViewGroup.LayoutParams {

        private final ObjectAnimator mCollapseAlpha = new ObjectAnimator();
        private final ObjectAnimator mCollapseDir = new ObjectAnimator();
        private final ObjectAnimator mExpandAlpha = new ObjectAnimator();
        private final ObjectAnimator mExpandDir = new ObjectAnimator();
        private boolean animationsSetToPlay;

        LayoutParams(final ViewGroup.LayoutParams source) {
            super(source);

            mExpandDir.setInterpolator(EXPAND_INTERPOLATOR);
            mExpandAlpha.setInterpolator(ALPHA_EXPAND_INTERPOLATOR);
            mCollapseDir.setInterpolator(COLLAPSE_INTERPOLATOR);
            mCollapseAlpha.setInterpolator(COLLAPSE_INTERPOLATOR);

            mCollapseAlpha.setProperty(View.ALPHA);
            mCollapseAlpha.setFloatValues(1f, 0f);

            mExpandAlpha.setProperty(View.ALPHA);
            mExpandAlpha.setFloatValues(0f, 1f);

            switch (mExpandDirection) {
                case EXPAND_UP:
                case EXPAND_DOWN:
                    mCollapseDir.setProperty(View.TRANSLATION_Y);
                    mExpandDir.setProperty(View.TRANSLATION_Y);
                    break;
                case EXPAND_LEFT:
                case EXPAND_RIGHT:
                    mCollapseDir.setProperty(View.TRANSLATION_X);
                    mExpandDir.setProperty(View.TRANSLATION_X);
                    break;
            }
        }

        private void addLayerTypeListener(final Animator animator, final View view) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(final Animator animation) {
                    view.setLayerType(LAYER_TYPE_NONE, null);
                }

                @Override
                public void onAnimationStart(final Animator animation) {
                    view.setLayerType(LAYER_TYPE_HARDWARE, null);
                }
            });
        }

        public void setAnimationsTarget(final View view) {
            mCollapseAlpha.setTarget(view);
            mCollapseDir.setTarget(view);
            mExpandAlpha.setTarget(view);
            mExpandDir.setTarget(view);

            // Now that the animations have targets, set them to be played
            if (!animationsSetToPlay) {
                addLayerTypeListener(mExpandDir, view);
                addLayerTypeListener(mCollapseDir, view);

                mCollapseAnimation.play(mCollapseAlpha);
                mCollapseAnimation.play(mCollapseDir);
                mExpandAnimation.play(mExpandAlpha);
                mExpandAnimation.play(mExpandDir);
                animationsSetToPlay = true;
            }
        }
    }
}
