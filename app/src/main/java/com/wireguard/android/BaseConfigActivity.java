package com.wireguard.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import com.wireguard.config.Config;

/**
 * Base class for activities that need to remember the current configuration and wait for a service.
 */

abstract class BaseConfigActivity extends Activity {
    protected static final String KEY_CURRENT_CONFIG = "currentConfig";
    protected static final String KEY_IS_EDITING = "isEditing";

    private Config currentConfig;
    private String initialConfig;
    private boolean isEditing;
    private boolean wasEditing;

    protected Config getCurrentConfig() {
        return currentConfig;
    }

    protected boolean isEditing() {
        return isEditing;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Restore the saved configuration if there is one; otherwise grab it from the intent.
        if (savedInstanceState != null) {
            initialConfig = savedInstanceState.getString(KEY_CURRENT_CONFIG);
            wasEditing = savedInstanceState.getBoolean(KEY_IS_EDITING, false);
        } else {
            final Intent intent = getIntent();
            initialConfig = intent.getStringExtra(KEY_CURRENT_CONFIG);
            wasEditing = intent.getBooleanExtra(KEY_IS_EDITING, false);
        }
        // Trigger starting the service as early as possible
        if (VpnService.getInstance() != null)
            onServiceAvailable();
        else
            bindService(new Intent(this, VpnService.class), new ServiceConnectionCallbacks(),
                    Context.BIND_AUTO_CREATE);
    }

    protected abstract void onCurrentConfigChanged(Config config);

    protected abstract void onEditingStateChanged(boolean isEditing);

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (currentConfig != null)
            outState.putString(KEY_CURRENT_CONFIG, currentConfig.getName());
        outState.putBoolean(KEY_IS_EDITING, isEditing);
    }

    protected void onServiceAvailable() {
        // Make sure the subclass activity is initialized before setting its config.
        if (initialConfig != null && currentConfig == null)
            setCurrentConfig(VpnService.getInstance().get(initialConfig));
        setIsEditing(wasEditing);
    }

    public void setCurrentConfig(final Config config) {
        if (currentConfig == config)
            return;
        currentConfig = config;
        onCurrentConfigChanged(config);
    }

    public void setIsEditing(final boolean isEditing) {
        if (this.isEditing == isEditing)
            return;
        this.isEditing = isEditing;
        onEditingStateChanged(isEditing);
    }

    private class ServiceConnectionCallbacks implements ServiceConnection {
        @Override
        public void onServiceConnected(final ComponentName component, final IBinder binder) {
            // We don't actually need a binding, only notification that the service is started.
            unbindService(this);
            onServiceAvailable();
        }

        @Override
        public void onServiceDisconnected(final ComponentName component) {
            // This can never happen; the service runs in the same thread as the activity.
            throw new IllegalStateException();
        }
    }
}
