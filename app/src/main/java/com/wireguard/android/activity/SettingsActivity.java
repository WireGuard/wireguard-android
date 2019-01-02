/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity;

import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import android.util.SparseArray;
import android.view.MenuItem;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.backend.WgQuickBackend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Interface for changing application-global persistent settings.
 */

public class SettingsActivity extends ThemeChangeAwareActivity {
    private final SparseArray<PermissionRequestCallback> permissionRequestCallbacks = new SparseArray<>();
    private int permissionRequestCounter;

    public void ensurePermissions(final String[] permissions, final PermissionRequestCallback cb) {
        final List<String> needPermissions = new ArrayList<>(permissions.length);
        for (final String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED)
                needPermissions.add(permission);
        }
        if (needPermissions.isEmpty()) {
            final int[] granted = new int[permissions.length];
            Arrays.fill(granted, PackageManager.PERMISSION_GRANTED);
            cb.done(permissions, granted);
            return;
        }
        final int idx = permissionRequestCounter++;
        permissionRequestCallbacks.put(idx, cb);
        ActivityCompat.requestPermissions(this,
                needPermissions.toArray(new String[needPermissions.size()]), idx);
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportFragmentManager().findFragmentById(android.R.id.content) == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           final String[] permissions,
                                           final int[] grantResults) {
        final PermissionRequestCallback f = permissionRequestCallbacks.get(requestCode);
        if (f != null) {
            permissionRequestCallbacks.remove(requestCode);
            f.done(permissions, grantResults);
        }
    }

    public interface PermissionRequestCallback {
        void done(String[] permissions, int[] grantResults);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, final String key) {
            addPreferencesFromResource(R.xml.preferences);
            final Preference wgQuickOnlyPrefs[] = {
                    getPreferenceManager().findPreference("tools_installer"),
                    getPreferenceManager().findPreference("restore_on_boot")
            };
            for (final Preference pref : wgQuickOnlyPrefs)
                pref.setVisible(false);
            final PreferenceScreen screen = getPreferenceScreen();
            Application.getBackendAsync().thenAccept(backend -> {
                for (final Preference pref : wgQuickOnlyPrefs) {
                    if (backend instanceof WgQuickBackend)
                        pref.setVisible(true);
                    else
                        screen.removePreference(pref);
                }
            });
        }
    }
}
