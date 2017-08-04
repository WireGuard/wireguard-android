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
        activity.addServiceConnectionListener(this);
        service = activity.getService();
    }

    @Override
    public void onDetach() {
        super.onDetach();
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
