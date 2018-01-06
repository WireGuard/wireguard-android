package com.wireguard.android.activity;

import android.app.Activity;
import android.databinding.CallbackRegistry;
import android.databinding.CallbackRegistry.NotifierCallback;
import android.os.Bundle;

import com.wireguard.android.Application;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.TunnelManager;

import java.util.Objects;

/**
 * Base class for activities that need to remember the currently-selected tunnel.
 */

public abstract class BaseActivity extends Activity {
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
        // Restore the saved tunnel if there is one; otherwise grab it from the arguments.
        String savedTunnelName = null;
        if (savedInstanceState != null)
            savedTunnelName = savedInstanceState.getString(TunnelManager.KEY_SELECTED_TUNNEL);
        else if (getIntent() != null)
            savedTunnelName = getIntent().getStringExtra(TunnelManager.KEY_SELECTED_TUNNEL);
        if (savedTunnelName != null) {
            final TunnelManager manager = Application.getComponent().getTunnelManager();
            selectedTunnel = manager.getTunnels().get(savedTunnelName);
        }
        // The selected tunnel must be set before the superclass method recreates fragments.
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        if (selectedTunnel != null)
            outState.putString(TunnelManager.KEY_SELECTED_TUNNEL, selectedTunnel.getName());
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
}
