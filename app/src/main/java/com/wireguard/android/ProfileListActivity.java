package com.wireguard.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.IBinder;

import com.wireguard.android.databinding.ProfileListActivityBinding;

public class ProfileListActivity extends Activity {
    private final ServiceConnection connection = new ProfileServiceConnection();
    private ProfileListActivityBinding binding;
    private ProfileServiceInterface service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.profile_list_activity);
        // Ensure the long-running service is started. This only needs to happen once.
        Intent intent = new Intent(this, ProfileService.class);
        startService(intent);
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
            service = null;
        }
    }

    private class ProfileServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName component, IBinder binder) {
            service = (ProfileServiceInterface) binder;
            binding.setProfiles(service.getProfiles());
        }

        @Override
        public void onServiceDisconnected(ComponentName component) {
            // This function is only called when the service crashes or goes away unexpectedly.
            service = null;
        }
    }
}
