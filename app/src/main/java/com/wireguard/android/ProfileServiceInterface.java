package com.wireguard.android;

import android.databinding.ObservableList;

import com.wireguard.config.Profile;

/**
 * Interface for the background connection service.
 */

public interface ProfileServiceInterface {
    ObservableList<Profile> getProfiles();
}
