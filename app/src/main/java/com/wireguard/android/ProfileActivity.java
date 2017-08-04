package com.wireguard.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;

import com.wireguard.config.Profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity that allows creating/viewing/editing/deleting WireGuard profiles.
 */

public class ProfileActivity extends Activity {
    private final ServiceConnection connection = new ProfileServiceConnection();
    private final List<ServiceConnectionListener> listeners = new ArrayList<>();
    private ProfileServiceInterface service;

    public void addServiceConnectionListener(ServiceConnectionListener listener) {
        listeners.add(listener);
    }

    public ProfileServiceInterface getService() {
        return service;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_activity);
        // Ensure the long-running service is started. This only needs to happen once.
        Intent intent = new Intent(this, ProfileService.class);
        startService(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void onMenuSettings(MenuItem item) {

    }

    public void onProfileSelected(Profile profile) {

    }

    @Override
    public void onStart() {
        super.onStart();
        Intent intent = new Intent(this, ProfileService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (service != null) {
            unbindService(connection);
            for (ServiceConnectionListener listener : listeners)
                listener.onServiceDisconnected();
            service = null;
        }
    }

    public void removeServiceConnectionListener(ServiceConnectionListener listener) {
        listeners.remove(listener);
    }

    private class ProfileServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName component, IBinder binder) {
            service = (ProfileServiceInterface) binder;
            for (ServiceConnectionListener listener : listeners)
                listener.onServiceConnected(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName component) {
            // This function is only called when the service crashes or goes away unexpectedly.
            for (ServiceConnectionListener listener : listeners)
                listener.onServiceDisconnected();
            service = null;
        }
    }
}
