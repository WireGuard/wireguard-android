/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android.activity;

import android.content.Intent;
import android.databinding.CallbackRegistry;
import android.databinding.CallbackRegistry.NotifierCallback;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.wireguard.android.Application;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.Topic;

import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Base class for activities that need to remember the currently-selected tunnel.
 */

public abstract class BaseActivity extends AppCompatActivity implements Topic.Subscriber {
    private static final String TAG = "WireGuard/" + BaseActivity.class.getSimpleName();

    private static final String KEY_SELECTED_TUNNEL = "selected_tunnel";

    private final SelectionChangeRegistry selectionChangeRegistry = new SelectionChangeRegistry();
    private Tunnel selectedTunnel;

    public void addOnSelectedTunnelChangedListener(
            final OnSelectedTunnelChangedListener listener) {
        selectionChangeRegistry.add(listener);
    }

    public Tunnel getSelectedTunnel() {
        return selectedTunnel;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        subscribeTopics();

        // Restore the saved tunnel if there is one; otherwise grab it from the arguments.
        String savedTunnelName = null;
        if (savedInstanceState != null)
            savedTunnelName = savedInstanceState.getString(KEY_SELECTED_TUNNEL);
        else if (getIntent() != null)
            savedTunnelName = getIntent().getStringExtra(KEY_SELECTED_TUNNEL);
        if (savedTunnelName != null) {
            final TunnelManager tunnelManager = Application.getComponent().getTunnelManager();
            selectedTunnel = tunnelManager.getTunnels().get(savedTunnelName);
        }

        // The selected tunnel must be set before the superclass method recreates fragments.
        super.onCreate(savedInstanceState);

        if (Application.getComponent().getBackendType() == GoBackend.class) {
            final Intent intent = GoBackend.VpnService.prepare(this);
            if (intent != null) {
                startActivityForResult(intent, 0);
            }
        }
    }

    @Override
    protected void onDestroy() {
        unsubscribeTopics();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        if (selectedTunnel != null)
            outState.putString(KEY_SELECTED_TUNNEL, selectedTunnel.getName());
        super.onSaveInstanceState(outState);
    }

    protected abstract void onSelectedTunnelChanged(Tunnel oldTunnel, Tunnel newTunnel);

    public void removeOnSelectedTunnelChangedListener(
            final OnSelectedTunnelChangedListener listener) {
        selectionChangeRegistry.remove(listener);
    }

    public void setSelectedTunnel(final Tunnel tunnel) {
        final Tunnel oldTunnel = selectedTunnel;
        if (Objects.equals(oldTunnel, tunnel))
            return;
        selectedTunnel = tunnel;
        onSelectedTunnelChanged(oldTunnel, tunnel);
        selectionChangeRegistry.notifyCallbacks(oldTunnel, 0, tunnel);
    }

    public interface OnSelectedTunnelChangedListener {
        void onSelectedTunnelChanged(Tunnel oldTunnel, Tunnel newTunnel);
    }

    private static final class SelectionChangeNotifier
            extends NotifierCallback<OnSelectedTunnelChangedListener, Tunnel, Tunnel> {
        @Override
        public void onNotifyCallback(final OnSelectedTunnelChangedListener listener,
                                     final Tunnel oldTunnel, final int ignored,
                                     final Tunnel newTunnel) {
            listener.onSelectedTunnelChanged(oldTunnel, newTunnel);
        }
    }

    private static final class SelectionChangeRegistry
            extends CallbackRegistry<OnSelectedTunnelChangedListener, Tunnel, Tunnel> {
        private SelectionChangeRegistry() {
            super(new SelectionChangeNotifier());
        }
    }

    @Override
    public void onTopicPublished(Topic topic) {
        if (topic == Application.getComponent().getThemeChangeTopic()) {
            try {
                Field f = getResources().getClass().getDeclaredField("mResourcesImpl");
                f.setAccessible(true);
                Object o = f.get(getResources());
                f = o.getClass().getDeclaredField("mDrawableCache");
                f.setAccessible(true);
                o = f.get(o);
                o.getClass().getMethod("onConfigurationChange", int.class).invoke(o, -1);
            } catch (Exception e) {
                Log.e(TAG, "Failed to flush icon cache", e);
            }
            recreate();
        }
    }

    @Override
    public Topic[] getSubscription() {
        return new Topic[] { Application.getComponent().getThemeChangeTopic() };
    }
}
