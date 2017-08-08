package com.wireguard.android;

/**
 * Interface for fragments that need notification about service connection changes.
 */

interface ServiceConnectionListener<T> {
    void onServiceConnected(T service);

    void onServiceDisconnected();
}
