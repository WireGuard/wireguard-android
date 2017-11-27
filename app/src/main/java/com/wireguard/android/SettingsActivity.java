package com.wireguard.android;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.preference.PreferenceFragment;

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        final Bundle args = new Bundle();
        args.putBoolean("showQuickTile", getIntent().getBooleanExtra("showQuickTile", false));
        final SettingsFragment fragment = new SettingsFragment();
        fragment.setArguments(args);
        transaction.replace(android.R.id.content, fragment).commit();
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            if (getArguments().getBoolean("showQuickTile"))
                ((ConfigListPreference) findPreference("primary_config")).show();
        }
    }
}
