package com.wireguard.android;

/**
 * Interface for activities that provide a connection to a service.
 */

interface ServiceConnectionProvider<T> {
    void addServiceConnectionListener(ServiceConnectionListener<T> listener);

    T getService();

    void removeServiceConnectionListener(ServiceConnectionListener<T> listener);
}
