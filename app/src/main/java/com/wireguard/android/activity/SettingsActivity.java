package com.wireguard.android.activity;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.view.MenuItem;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.backend.WgQuickBackend;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Interface for changing application-global persistent settings.
 */

public class SettingsActivity extends AppCompatActivity {
    private HashMap<Integer, PermissionRequestCallback> permissionRequestCallbacks = new HashMap<>();
    private int permissionRequestCounter = 0;

    public synchronized void ensurePermissions(String[] permissions, PermissionRequestCallback cb) {
        List<String> needPermissions = new ArrayList<>(permissions.length);
        for (final String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                needPermissions.add(permission);
        }
        if (needPermissions.isEmpty()) {
            int[] granted = new int[permissions.length];
            Arrays.fill(granted, PackageManager.PERMISSION_GRANTED);
            cb.done(permissions, granted);
            return;
        }
        int idx = permissionRequestCounter++;
        permissionRequestCallbacks.put(idx, cb);
        ActivityCompat.requestPermissions(this, needPermissions.toArray(new String[needPermissions.size()]), idx);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportFragmentManager().findFragmentById(android.R.id.content) == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        final PermissionRequestCallback f = permissionRequestCallbacks.get(requestCode);
        if (f != null) {
            permissionRequestCallbacks.remove(requestCode);
            f.done(permissions, grantResults);
        }
    }

    @FunctionalInterface
    public interface PermissionRequestCallback {
        void done(String[] permissions, int[] grantResults);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, final String key) {
            addPreferencesFromResource(R.xml.preferences);
            if (Application.getComponent().getBackendType() != WgQuickBackend.class) {
                final Preference toolsInstaller =
                        getPreferenceManager().findPreference("tools_installer");
                getPreferenceScreen().removePreference(toolsInstaller);
            }
        }
    }
}
