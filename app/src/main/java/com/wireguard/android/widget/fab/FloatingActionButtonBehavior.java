/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget.fab;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.snackbar.Snackbar;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import android.util.AttributeSet;
import android.view.View;

public class FloatingActionButtonBehavior extends CoordinatorLayout.Behavior<FloatingActionsMenu> {

    private static final long ANIMATION_DURATION = 250;
    private static final TimeInterpolator FAST_OUT_SLOW_IN_INTERPOLATOR = new FastOutSlowInInterpolator();

    public FloatingActionButtonBehavior(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    private static void animateChange(final FloatingActionsMenu child, final float destination, final float fullSpan) {
        final float origin = child.getBehaviorYTranslation();
        if (Math.abs(destination - origin) < fullSpan / 2) {
            child.setBehaviorYTranslation(destination);
            return;
        }
        final ValueAnimator animator = new ValueAnimator();
        animator.setFloatValues(origin, destination);
        animator.setInterpolator(FAST_OUT_SLOW_IN_INTERPOLATOR);
        animator.setDuration((long) (ANIMATION_DURATION * (Math.abs(destination - origin) / fullSpan)));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator a) {
                child.setBehaviorYTranslation(destination);
            }
        });
        animator.addUpdateListener(a -> child.setBehaviorYTranslation((float) a.getAnimatedValue()));
        animator.start();
    }

    @Override
    public boolean layoutDependsOn(final CoordinatorLayout parent, final FloatingActionsMenu child,
                                   final View dependency) {
        return dependency instanceof Snackbar.SnackbarLayout;
    }

    @Override
    public boolean onDependentViewChanged(final CoordinatorLayout parent, final FloatingActionsMenu child,
                                          final View dependency) {
        animateChange(child, Math.min(0, dependency.getTranslationY() - dependency.getMeasuredHeight()), dependency.getMeasuredHeight());
        return true;
    }

    @Override
    public void onDependentViewRemoved(final CoordinatorLayout parent, final FloatingActionsMenu child,
                                       final View dependency) {
        animateChange(child, 0, dependency.getMeasuredHeight());
    }
}
