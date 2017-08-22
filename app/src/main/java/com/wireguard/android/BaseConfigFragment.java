package com.wireguard.android;

import android.app.Fragment;
import android.os.Bundle;

import com.wireguard.config.Config;

/**
 * Base class for fragments that need to remember the current configuration.
 */

abstract class BaseConfigFragment extends Fragment {
    private static final String KEY_CURRENT_CONFIG = "currentConfig";

    private Config currentConfig;

    protected Config getCurrentConfig() {
        return currentConfig;
    }

    protected abstract void onCurrentConfigChanged(Config config);

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Restore the saved configuration if there is one; otherwise grab it from the arguments.
        String initialConfig = null;
        if (savedInstanceState != null)
            initialConfig = savedInstanceState.getString(KEY_CURRENT_CONFIG);
        else if (getArguments() != null)
            initialConfig = getArguments().getString(KEY_CURRENT_CONFIG);
        if (initialConfig != null && currentConfig == null)
            setCurrentConfig(VpnService.getInstance().get(initialConfig));
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentConfig != null)
            outState.putString(KEY_CURRENT_CONFIG, currentConfig.getName());
    }

    public void setCurrentConfig(final Config config) {
        if (currentConfig == config)
            return;
        currentConfig = config;
        onCurrentConfigChanged(currentConfig);
    }
}
