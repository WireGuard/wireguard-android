/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.util.Log;

import com.wireguard.android.Application;

import java.lang.reflect.Field;

public abstract class ThemeChangeAwareActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "WireGuard/" + ThemeChangeAwareActivity.class.getSimpleName();

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            Application.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
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
        }
    }
}
