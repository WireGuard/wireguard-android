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

    protected void onCachedProfileChanged(Profile cachedProfile) {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Restore the saved profile if there is one; otherwise grab it from the arguments.
        if (savedInstanceState != null)
            setProfile(savedInstanceState.getString(ProfileActivity.KEY_PROFILE_NAME));
        else if (getArguments() != null)
            setProfile(getArguments().getString(ProfileActivity.KEY_PROFILE_NAME));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ProfileActivity.KEY_PROFILE_NAME, profile);
    }

    @Override
    public void onServiceConnected(ProfileServiceInterface service) {
        super.onServiceConnected(service);
        updateCachedProfile(service);
    }

    public void setProfile(String profile) {
        this.profile = profile;
        updateCachedProfile(getService());
    }

    private void updateCachedProfile(ProfileServiceInterface service) {
        final Profile newCachedProfile = service != null
                ? service.getProfiles().get(profile) : null;
        if (newCachedProfile != cachedProfile) {
            cachedProfile = newCachedProfile;
            onCachedProfileChanged(newCachedProfile);
        }
    }
}
