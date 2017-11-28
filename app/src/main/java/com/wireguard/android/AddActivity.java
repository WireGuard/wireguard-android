package com.wireguard.android;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;

import com.wireguard.config.Config;

/**
 * Standalone activity for creating configurations.
 */

public class AddActivity extends BaseConfigActivity {
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_activity);
    }

    @Override
    protected void onCurrentConfigChanged(final Config oldConfig, final Config newConfig) {
        // Do nothing (this never happens).
    }

    @Override
    protected void onEditingStateChanged(final boolean isEditing) {
        // Go back to the main activity once the new configuration is created.
        if (!isEditing)
            finish();
    }

    @Override
    protected void onServiceAvailable() {
        super.onServiceAvailable();
        final FragmentManager fm = getFragmentManager();
        ConfigEditFragment fragment = (ConfigEditFragment) fm.findFragmentById(R.id.master_fragment);
        if (fragment == null) {
            fragment = new ConfigEditFragment();
            final FragmentTransaction transaction = fm.beginTransaction();
            transaction.add(R.id.master_fragment, fragment);
            transaction.commit();
        }
        // Prime the state for the fragment to tell us it is finished.
        setIsEditing(true);
    }
}
