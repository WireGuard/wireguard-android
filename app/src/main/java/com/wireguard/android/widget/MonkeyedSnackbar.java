package com.wireguard.android.widget;

import android.support.annotation.NonNull;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import com.wireguard.android.BootShutdownReceiver;

import java.lang.reflect.Field;

public class MonkeyedSnackbar {
    private static final String TAG = "WireGuard/" + BootShutdownReceiver.class.getSimpleName();

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
