/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android;

import android.annotation.TargetApi;
import android.content.Intent;
import android.databinding.Observable;
import android.databinding.Observable.OnPropertyChangedCallback;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.wireguard.android.activity.MainActivity;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.android.widget.SlashDrawable;

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
    @Nullable private Tunnel tunnel;
    @Nullable private Icon iconOn;
    @Nullable private Icon iconOff;

    /* This works around an annoying unsolved frameworks bug some people are hitting. */
    @Override
    @Nullable
    public IBinder onBind(final Intent intent) {
        IBinder ret = null;
        try {
            ret = super.onBind(intent);
        } catch (final Exception e) {
            Log.d(TAG, "Failed to bind to TileService", e);
        }
        return ret;
    }

    @Override
    public void onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            iconOff = iconOn = Icon.createWithResource(this, R.drawable.ic_tile);
            return;
        }
        final SlashDrawable icon = new SlashDrawable(getResources().getDrawable(R.drawable.ic_tile, Application.get().getTheme()));
        icon.setAnimationEnabled(false); /* Unfortunately we can't have animations, since Icons are marshaled. */
        icon.setSlashed(false);
        Bitmap b = Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        icon.setBounds(0, 0, c.getWidth(), c.getHeight());
        icon.draw(c);
        iconOn = Icon.createWithBitmap(b);
        icon.setSlashed(true);
        b = Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        c = new Canvas(b);
        icon.setBounds(0, 0, c.getWidth(), c.getHeight());
        icon.draw(c);
        iconOff = Icon.createWithBitmap(b);
    }

    @Override
    public void onClick() {
        if (tunnel != null) {
            final Tile tile = getQsTile();
            if (tile != null) {
                tile.setIcon(tile.getIcon() == iconOn ? iconOff : iconOn);
                tile.updateTile();
            }
            tunnel.setState(State.TOGGLE).whenComplete(this::onToggleFinished);
        } else {
            final Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivityAndCollapse(intent);
        }
    }

    @Override
    public void onStartListening() {
        Application.getTunnelManager().addOnPropertyChangedCallback(onTunnelChangedCallback);
        if (tunnel != null)
            tunnel.addOnPropertyChangedCallback(onStateChangedCallback);
        updateTile();
    }

    @Override
    public void onStopListening() {
        if (tunnel != null)
            tunnel.removeOnPropertyChangedCallback(onStateChangedCallback);
        Application.getTunnelManager().removeOnPropertyChangedCallback(onTunnelChangedCallback);
    }

    private void onToggleFinished(@SuppressWarnings("unused") final State state,
                                  @Nullable final Throwable throwable) {
        if (throwable == null)
            return;
        final String error = ExceptionLoggers.unwrapMessage(throwable);
        final String message = getString(R.string.toggle_error, error);
        Log.e(TAG, message, throwable);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void updateTile() {
        // Update the tunnel.
        final Tunnel newTunnel = Application.getTunnelManager().getLastUsedTunnel();
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
            tile.setIcon(state == Tile.STATE_ACTIVE ? iconOn : iconOff);
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
