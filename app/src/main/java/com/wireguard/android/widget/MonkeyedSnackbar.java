/*
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.widget;

import android.support.annotation.NonNull;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import java.lang.reflect.Field;

public class MonkeyedSnackbar {
    private static final String TAG = "WireGuard/" + MonkeyedSnackbar.class.getSimpleName();

    public static Snackbar make(@NonNull final View view, @NonNull final CharSequence text,
                                @BaseTransientBottomBar.Duration final int duration) {
        final Snackbar snackbar = Snackbar.make(view, text, duration);

        try {
            final Field accessibilityManager = Snackbar.class.getSuperclass().getDeclaredField("mAccessibilityManager");
            accessibilityManager.setAccessible(true);
            final Field isEnabled = AccessibilityManager.class.getDeclaredField("mIsEnabled");
            isEnabled.setAccessible(true);
            isEnabled.setBoolean(accessibilityManager.get(snackbar), false);
        } catch (final Exception e) {
            Log.e(TAG, "Unable to force-enable snackbar animations", e);
        }

        return snackbar;
    }
}
