package com.wireguard.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;

import com.wireguard.config.Config;

/**
 * Base class for activities that need to remember the current configuration and wait for a service.
 */

abstract class BaseConfigActivity extends Activity {
    protected static final String KEY_CURRENT_CONFIG = "currentConfig";
    protected static final String TAG_DETAIL = "detail";
    protected static final String TAG_EDIT = "edit";
    protected static final String TAG_LIST = "list";
    protected static final String TAG_PLACEHOLDER = "placeholder";

    private final ServiceConnection callbacks = new ServiceConnectionCallbacks();
    private Config currentConfig;
    private String initialConfig;

    protected Config getCurrentConfig() {
        return currentConfig;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Trigger starting the service as early as possible
        bindService(new Intent(this, VpnService.class), callbacks, Context.BIND_AUTO_CREATE);
        // Restore the saved configuration if there is one; otherwise grab it from the intent.
        if (savedInstanceState != null)
            initialConfig = savedInstanceState.getString(KEY_CURRENT_CONFIG);
        else
            initialConfig = getIntent().getStringExtra(KEY_CURRENT_CONFIG);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    protected abstract void onCurrentConfigChanged(Config config);

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentConfig != null)
            outState.putString(KEY_CURRENT_CONFIG, currentConfig.getName());
    }

    protected abstract void onServiceAvailable();

    public void setCurrentConfig(final Config config) {
        currentConfig = config;
        onCurrentConfigChanged(currentConfig);
    }

    private class ServiceConnectionCallbacks implements ServiceConnection {
        @Override
        public void onServiceConnected(final ComponentName component, final IBinder binder) {
            // We don't actually need a binding, only notification that the service is started.
            unbindService(callbacks);
            // Tell the subclass that it is now safe to use the service.
            onServiceAvailable();
            // Make sure the subclass activity is initialized before setting its config.
            if (initialConfig != null && currentConfig == null)
                setCurrentConfig(VpnService.getInstance().get(initialConfig));
        }

        @Override
        public void onServiceDisconnected(final ComponentName component) {
            // This can never happen; the service runs in the same thread as the activity.
            throw new IllegalStateException();
        }
    }
}
