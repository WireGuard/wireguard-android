/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.util.Log;

import com.wireguard.android.Application;

import java.lang.reflect.Field;

public abstract class ThemeChangeAwareActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "WireGuard/" + ThemeChangeAwareActivity.class.getSimpleName();
    private static boolean lastDarkMode;
    @Nullable private static Resources lastResources;

    private static synchronized void invalidateDrawableCache(final Resources resources, final boolean darkMode) {
        if (resources == lastResources && darkMode == lastDarkMode)
            return;

        try {
            Field f;
            Object o = resources;
            try {
                f = o.getClass().getDeclaredField("mResourcesImpl");
                f.setAccessible(true);
                o = f.get(o);
            } catch (final Exception ignored) {
            }
            f = o.getClass().getDeclaredField("mDrawableCache");
            f.setAccessible(true);
            o = f.get(o);
            try {
                o.getClass().getMethod("onConfigurationChange", int.class).invoke(o, -1);
            } catch (final Exception ignored) {
                o.getClass().getMethod("clear").invoke(o);
            }
        } catch (final Exception e) {
            Log.e(TAG, "Failed to flush drawable cache", e);
        }

        lastResources = resources;
        lastDarkMode = darkMode;
    }


    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Application.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        Application.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key) {
        if ("dark_theme".equals(key)) {
            final boolean darkMode = sharedPreferences.getBoolean(key, false);
            AppCompatDelegate.setDefaultNightMode(
                    sharedPreferences.getBoolean(key, false) ?
                            AppCompatDelegate.MODE_NIGHT_YES :
                            AppCompatDelegate.MODE_NIGHT_NO);
            invalidateDrawableCache(getResources(), darkMode);
            recreate();
        }
    }
}
