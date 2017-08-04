package com.wireguard.android;

/**
 * Interface for fragments that need notification about connection changes to the ProfileService.
 */

interface ServiceConnectionListener {
    void onServiceConnected(ProfileServiceInterface service);

    void onServiceDisconnected();
}
