/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android;

import android.annotation.TargetApi;
import android.content.Intent;
import android.databinding.Observable;
import android.databinding.Observable.OnPropertyChangedCallback;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

import com.wireguard.android.activity.MainActivity;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.ExceptionLoggers;

import java.util.Objects;

/**
 * Service that maintains the application's custom Quick Settings tile. This service is bound by the
 * system framework as necessary to update the appearance of the tile in the system UI, and to
 * forward click events to the application.
 */

@TargetApi(Build.VERSION_CODES.N)
public class QuickTileService extends TileService {
    private static final String TAG = "WireGuard/" + QuickTileService.class.getSimpleName();

    private final OnStateChangedCallback onStateChangedCallback = new OnStateChangedCallback();
    private final OnTunnelChangedCallback onTunnelChangedCallback = new OnTunnelChangedCallback();
    private Tunnel tunnel;
    private TunnelManager tunnelManager;

    @Override
    public void onClick() {
        if (tunnel != null) {
            tunnel.setState(State.TOGGLE).whenComplete(this::onToggleFinished);
        } else {
            final Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        tunnelManager = Application.getComponent().getTunnelManager();
    }

    @Override
    public void onStartListening() {
        tunnelManager.addOnPropertyChangedCallback(onTunnelChangedCallback);
        if (tunnel != null)
            tunnel.addOnPropertyChangedCallback(onStateChangedCallback);
        updateTile();
    }

    @Override
    public void onStopListening() {
        if (tunnel != null)
            tunnel.removeOnPropertyChangedCallback(onStateChangedCallback);
        tunnelManager.removeOnPropertyChangedCallback(onTunnelChangedCallback);
    }

    private void onToggleFinished(@SuppressWarnings("unused") final State state,
                                  final Throwable throwable) {
        if (throwable == null)
            return;
        final String error = ExceptionLoggers.unwrap(throwable).getMessage();
        final String message = getString(R.string.toggle_error, error);
        Log.e(TAG, message, throwable);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void updateTile() {
        // Update the tunnel.
        final Tunnel newTunnel = tunnelManager.getLastUsedTunnel();
        if (newTunnel != tunnel) {
            if (tunnel != null)
                tunnel.removeOnPropertyChangedCallback(onStateChangedCallback);
            tunnel = newTunnel;
            if (tunnel != null)
                tunnel.addOnPropertyChangedCallback(onStateChangedCallback);
        }
        // Update the tile contents.
        final String label;
        final int state;
        final Tile tile = getQsTile();
        if (tunnel != null) {
            label = tunnel.getName();
            state = tunnel.getState() == Tunnel.State.UP ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        } else {
            label = getString(R.string.app_name);
            state = Tile.STATE_INACTIVE;
        }
        if (tile == null)
            return;
        tile.setLabel(label);
        if (tile.getState() != state) {
            // The icon must be changed every time the state changes, or the shade will not change.
            final Integer iconResource = state == Tile.STATE_ACTIVE ? R.drawable.ic_tile
                    : R.drawable.ic_tile_disabled;
            tile.setIcon(Icon.createWithResource(this, iconResource));
            tile.setState(state);
        }
        tile.updateTile();
    }

    private final class OnStateChangedCallback extends OnPropertyChangedCallback {
        @Override
        public void onPropertyChanged(final Observable sender, final int propertyId) {
            if (!Objects.equals(sender, tunnel)) {
                sender.removeOnPropertyChangedCallback(this);
                return;
            }
            if (propertyId != 0 && propertyId != BR.state)
                return;
            updateTile();
        }
    }

    private final class OnTunnelChangedCallback extends OnPropertyChangedCallback {
        @Override
        public void onPropertyChanged(final Observable sender, final int propertyId) {
            if (propertyId != 0 && propertyId != BR.lastUsedTunnel)
                return;
            updateTile();
        }
    }
}
