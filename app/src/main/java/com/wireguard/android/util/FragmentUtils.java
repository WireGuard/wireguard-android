/*
 * Copyright © 2018 Harsh Shandilya <msfjarvis@gmail.com>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.util;

import android.content.Context;
import android.support.v7.preference.Preference;
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
