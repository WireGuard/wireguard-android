package com.wireguard.android;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;

import com.wireguard.config.Config;

/**
 * Standalone activity for creating configurations.
 */

public class ConfigAddActivity extends BaseConfigActivity {
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.config_add_activity);
    }

    @Override
    protected void onCurrentConfigChanged(final Config config) {
        // This is the result of ConfigEditFragment signalling that a configuration was created.
        if (config != null)
            finish();
    }

    @Override
    protected void onServiceAvailable() {
        super.onServiceAvailable();
        final FragmentManager fm = getFragmentManager();
        if (fm.findFragmentById(R.id.master_fragment) == null) {
            final FragmentTransaction transaction = fm.beginTransaction();
            transaction.add(R.id.master_fragment, new ConfigEditFragment());
            transaction.commit();
        }
    }
}
