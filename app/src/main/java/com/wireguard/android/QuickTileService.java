package com.wireguard.android;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.wireguard.config.Config;

@TargetApi(Build.VERSION_CODES.N)
public class QuickTileService extends TileService {
    private Config config;
    private SharedPreferences preferences;
    private VpnService service;

    @Override
    public void onClick() {
        if (service != null && config != null) {
            if (config.isEnabled())
                service.disable(config.getName());
            else
                service.enable(config.getName());
        }
    }

    @Override
    public void onCreate() {
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        service = VpnService.getInstance();
        if (service == null)
            bindService(new Intent(this, VpnService.class), new ServiceConnectionCallbacks(),
                    Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStartListening() {
        // Since this is an active tile, this only gets called when we want to update the tile.
        final Tile tile = getQsTile();
        final String configName = preferences.getString(VpnService.KEY_PRIMARY_CONFIG, null);
        config = configName != null && service != null ? service.get(configName) : null;
        if (config != null) {
            tile.setLabel(config.getName());
            final int state = config.isEnabled() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
            if (tile.getState() != state) {
                // The icon must be changed every time the state changes, or the color won't change.
                final Integer iconResource = (state == Tile.STATE_ACTIVE) ?
                        R.drawable.ic_tile : R.drawable.ic_tile_disabled;
                tile.setIcon(Icon.createWithResource(this, iconResource));
                tile.setState(state);
            }
        } else {
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_tile_disabled));
            tile.setLabel(getString(R.string.loading));
            tile.setState(Tile.STATE_UNAVAILABLE);
        }
        tile.updateTile();
    }

    private class ServiceConnectionCallbacks implements ServiceConnection {
        @Override
        public void onServiceConnected(final ComponentName component, final IBinder binder) {
            // We don't actually need a binding, only notification that the service is started.
            unbindService(this);
            service = VpnService.getInstance();
        }

        @Override
        public void onServiceDisconnected(final ComponentName component) {
            // This can never happen; the service runs in the same thread as this service.
            throw new IllegalStateException();
        }
    }
}
