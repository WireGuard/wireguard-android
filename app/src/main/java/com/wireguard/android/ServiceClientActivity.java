package com.wireguard.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for activities that maintain a connection to a background service.
 */

abstract class ServiceClientActivity<T> extends Activity implements ServiceConnectionProvider<T> {
    private final ServiceConnectionCallbacks callbacks = new ServiceConnectionCallbacks();
    private final List<ServiceConnectionListener<T>> listeners = new ArrayList<>();
    private T service;
    private final Class<?> serviceClass;

    protected ServiceClientActivity(Class<?> serviceClass) {
        this.serviceClass = serviceClass;
    }

    @Override
    public void addServiceConnectionListener(ServiceConnectionListener<T> listener) {
        listeners.add(listener);
    }

    public T getService() {
        return service;
    }

    @Override
    public void onStart() {
        super.onStart();
        bindService(new Intent(this, serviceClass), callbacks, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (service != null) {
            service = null;
            unbindService(callbacks);
            for (ServiceConnectionListener listener : listeners)
                listener.onServiceDisconnected();
        }
    }

    @Override
    public void removeServiceConnectionListener(ServiceConnectionListener<T> listener) {
        listeners.remove(listener);
    }

    private class ServiceConnectionCallbacks implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName component, IBinder binder) {
            @SuppressWarnings("unchecked")
            final T localBinder = (T) binder;
            service = localBinder;
            for (ServiceConnectionListener<T> listener : listeners)
                listener.onServiceConnected(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName component) {
            service = null;
            for (ServiceConnectionListener<T> listener : listeners)
                listener.onServiceDisconnected();
        }
    }
}
