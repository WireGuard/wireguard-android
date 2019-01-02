/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.util;

import android.content.Context;
import androidx.preference.Preference;
import android.view.ContextThemeWrapper;

import com.wireguard.android.activity.SettingsActivity;

public final class FragmentUtils {
    private FragmentUtils() {
        // Prevent instantiation
    }

    public static SettingsActivity getPrefActivity(final Preference preference) {
        final Context context = preference.getContext();
        if (context instanceof ContextThemeWrapper) {
            if (((ContextThemeWrapper) context).getBaseContext() instanceof SettingsActivity) {
                return ((SettingsActivity) ((ContextThemeWrapper) context).getBaseContext());
            }
        }
        return null;
    }
}
