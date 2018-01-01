package com.wireguard.android;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.databinding.Observable;
import android.databinding.Observable.OnPropertyChangedCallback;
import android.databinding.ObservableMap.OnMapChangedCallback;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import android.widget.Toast;

import com.wireguard.android.Application.ApplicationComponent;
import com.wireguard.android.activity.MainActivity;
import com.wireguard.android.activity.SettingsActivity;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.TunnelCollection;
import com.wireguard.android.model.TunnelManager;

import java.util.Objects;

/**
 * Service that maintains the application's custom Quick Settings tile. This service is bound by the
 * system framework as necessary to update the appearance of the tile in the system UI, and to
 * forward click events to the application.
 */

@TargetApi(Build.VERSION_CODES.N)
public class QuickTileService extends TileService implements OnSharedPreferenceChangeListener {
    private static final String TAG = QuickTileService.class.getSimpleName();

    private final OnTunnelStateChangedCallback tunnelCallback = new OnTunnelStateChangedCallback();
    private final OnTunnelMapChangedCallback tunnelMapCallback = new OnTunnelMapChangedCallback();
    private SharedPreferences preferences;
    private Tunnel tunnel;
    private TunnelManager tunnelManager;

    @Override
    public void onClick() {
        if (tunnel != null) {
            tunnel.setState(State.TOGGLE).handle(this::onToggleFinished);
        } else {
            if (tunnelManager.getTunnels().isEmpty()) {
                // Prompt the user to create or import a tunnel configuration.
                startActivityAndCollapse(new Intent(this, MainActivity.class));
            } else {
                // Prompt the user to select a tunnel for use with the quick settings tile.
                final Intent intent = new Intent(this, SettingsActivity.class);
                intent.putExtra(SettingsActivity.KEY_SHOW_QUICK_TILE_SETTINGS, true);
                startActivityAndCollapse(intent);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        final ApplicationComponent component = Application.getComponent();
        preferences = component.getPreferences();
        tunnelManager = component.getTunnelManager();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences preferences, final String key) {
        if (!TunnelManager.KEY_PRIMARY_TUNNEL.equals(key))
            return;
        updateTile();
    }

    @Override
    public void onStartListening() {
        preferences.registerOnSharedPreferenceChangeListener(this);
        tunnelManager.getTunnels().addOnMapChangedCallback(tunnelMapCallback);
        if (tunnel != null)
            tunnel.addOnPropertyChangedCallback(tunnelCallback);
        updateTile();
    }

    @Override
    public void onStopListening() {
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        tunnelManager.getTunnels().removeOnMapChangedCallback(tunnelMapCallback);
        if (tunnel != null)
            tunnel.removeOnPropertyChangedCallback(tunnelCallback);
    }

    @SuppressWarnings("unused")
    private Void onToggleFinished(final State state, final Throwable throwable) {
        if (throwable == null)
            return null;
        Log.e(TAG, "Cannot toggle tunnel", throwable);
        final String message = "Cannot toggle tunnel: " + throwable.getCause().getMessage();
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        return null;
    }

    private void updateTile() {
        // Update the tunnel.
        final String currentName = tunnel != null ? tunnel.getName() : null;
        final String newName = preferences.getString(TunnelManager.KEY_PRIMARY_TUNNEL, null);
        if (!Objects.equals(currentName, newName)) {
            final TunnelCollection tunnels = tunnelManager.getTunnels();
            final Tunnel newTunnel = newName != null ? tunnels.get(newName) : null;
            if (tunnel != null)
                tunnel.removeOnPropertyChangedCallback(tunnelCallback);
            tunnel = newTunnel;
            if (tunnel != null)
                tunnel.addOnPropertyChangedCallback(tunnelCallback);
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
        tile.setLabel(label);
        if (tile.getState() != state) {
            // The icon must be changed every time the state changes, or the shade will not change.
            final Integer iconResource = (state == Tile.STATE_ACTIVE)
                    ? R.drawable.ic_tile : R.drawable.ic_tile_disabled;
            tile.setIcon(Icon.createWithResource(this, iconResource));
            tile.setState(state);
        }
        tile.updateTile();
    }

    private final class OnTunnelMapChangedCallback
            extends OnMapChangedCallback<TunnelCollection, String, Tunnel> {
        @Override
        public void onMapChanged(final TunnelCollection sender, final String key) {
            if (!key.equals(preferences.getString(TunnelManager.KEY_PRIMARY_TUNNEL, null)))
                return;
            updateTile();
        }
    }

    private final class OnTunnelStateChangedCallback extends OnPropertyChangedCallback {
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
}
