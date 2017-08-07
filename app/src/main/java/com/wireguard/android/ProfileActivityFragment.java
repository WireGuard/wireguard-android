package com.wireguard.android;

import android.app.Fragment;
import android.content.Context;

/**
 * Base class for fragments that are part of a ProfileActivity.
 */

public class ProfileActivityFragment extends Fragment implements ServiceConnectionListener {
    private ProfileActivity activity;
    protected ProfileServiceInterface service;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (ProfileActivity) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        activity = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.addServiceConnectionListener(this);
        // If the service is already connected, there will be no callback, so run the handler now.
        final ProfileServiceInterface service = activity.getService();
        if (service != null)
            onServiceConnected(service);
    }

    @Override
    public void onStop() {
        super.onStop();
        activity.removeServiceConnectionListener(this);
    }

    @Override
    public void onServiceConnected(ProfileServiceInterface service) {
        this.service = service;
    }

    @Override
    public void onServiceDisconnected() {
        service = null;
    }
}
