/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity;

import androidx.databinding.CallbackRegistry;
import androidx.databinding.CallbackRegistry.NotifierCallback;
import android.os.Bundle;
import androidx.annotation.Nullable;

import com.wireguard.android.Application;
import com.wireguard.android.model.Tunnel;

import java.util.Objects;

/**
 * Base class for activities that need to remember the currently-selected tunnel.
 */

public abstract class BaseActivity extends ThemeChangeAwareActivity {
    private static final String KEY_SELECTED_TUNNEL = "selected_tunnel";

    private final SelectionChangeRegistry selectionChangeRegistry = new SelectionChangeRegistry();
    @Nullable private Tunnel selectedTunnel;

    public void addOnSelectedTunnelChangedListener(final OnSelectedTunnelChangedListener listener) {
        selectionChangeRegistry.add(listener);
    }

    @Nullable
    public Tunnel getSelectedTunnel() {
        return selectedTunnel;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        // Restore the saved tunnel if there is one; otherwise grab it from the arguments.
        final String savedTunnelName;
        if (savedInstanceState != null)
            savedTunnelName = savedInstanceState.getString(KEY_SELECTED_TUNNEL);
        else if (getIntent() != null)
            savedTunnelName = getIntent().getStringExtra(KEY_SELECTED_TUNNEL);
        else
            savedTunnelName = null;

        if (savedTunnelName != null)
            Application.getTunnelManager().getTunnels()
                    .thenAccept(tunnels -> setSelectedTunnel(tunnels.get(savedTunnelName)));

        // The selected tunnel must be set before the superclass method recreates fragments.
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        if (selectedTunnel != null)
            outState.putString(KEY_SELECTED_TUNNEL, selectedTunnel.getName());
        super.onSaveInstanceState(outState);
    }

    protected abstract void onSelectedTunnelChanged(@Nullable Tunnel oldTunnel, @Nullable Tunnel newTunnel);

    public void removeOnSelectedTunnelChangedListener(
            final OnSelectedTunnelChangedListener listener) {
        selectionChangeRegistry.remove(listener);
    }

    public void setSelectedTunnel(@Nullable final Tunnel tunnel) {
        final Tunnel oldTunnel = selectedTunnel;
        if (Objects.equals(oldTunnel, tunnel))
            return;
        selectedTunnel = tunnel;
        onSelectedTunnelChanged(oldTunnel, tunnel);
        selectionChangeRegistry.notifyCallbacks(oldTunnel, 0, tunnel);
    }

    public interface OnSelectedTunnelChangedListener {
        void onSelectedTunnelChanged(@Nullable Tunnel oldTunnel, @Nullable Tunnel newTunnel);
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
}
