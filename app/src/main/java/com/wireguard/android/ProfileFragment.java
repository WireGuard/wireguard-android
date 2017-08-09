package com.wireguard.android;

import android.os.Bundle;

import com.wireguard.config.Profile;

/**
 * Base class for fragments that need to remember which profile they belong to.
 */

abstract class ProfileFragment extends ServiceClientFragment<ProfileServiceInterface> {
    private Profile cachedProfile;
    private String profile;

    protected Profile getCachedProfile() {
        return cachedProfile;
    }

    public String getProfile() {
        return profile;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Restore the saved profile if there is one; otherwise grab it from the arguments.
        if (savedInstanceState != null)
            profile = savedInstanceState.getString(ProfileActivity.KEY_PROFILE_NAME);
        else if (getArguments() != null)
            profile = getArguments().getString(ProfileActivity.KEY_PROFILE_NAME);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ProfileActivity.KEY_PROFILE_NAME, profile);
    }

    @Override
    public void onServiceConnected(ProfileServiceInterface service) {
        super.onServiceConnected(service);
        cachedProfile = service.getProfiles().get(profile);
    }

    public void setProfile(String profile) {
        this.profile = profile;
        final ProfileServiceInterface service = getService();
        if (service != null)
            cachedProfile = service.getProfiles().get(profile);
        else
            cachedProfile = null;
    }
}
