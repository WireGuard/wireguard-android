package com.wireguard.android;

import android.app.Fragment;
import android.content.Context;

/**
 * Base class for fragments in activities that maintain a connection to a background service.
 */

abstract class ServiceClientFragment<T> extends Fragment implements ServiceConnectionListener<T> {
    private ServiceConnectionProvider<T> provider;
    private T service;

    protected T getService() {
        return service;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        @SuppressWarnings("unchecked")
        final ServiceConnectionProvider<T> localContext = (ServiceConnectionProvider<T>) context;
        provider = localContext;
        service = provider.getService();
        if (service != null)
            onServiceConnected(service);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        provider = null;
    }

    @Override
    public void onStart() {
        super.onStart();
        provider.addServiceConnectionListener(this);
        // Run the handler if the connection state changed while we were not paying attention.
        final T localService = provider.getService();
        if (localService != service) {
            if (localService != null)
                onServiceConnected(localService);
            else
                onServiceDisconnected();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        provider.removeServiceConnectionListener(this);
    }

    @Override
    public void onServiceConnected(T service) {
        this.service = service;
    }

    @Override
    public void onServiceDisconnected() {
        service = null;
    }
}
