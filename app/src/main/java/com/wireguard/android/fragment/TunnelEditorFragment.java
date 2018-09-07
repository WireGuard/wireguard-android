/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.fragment;

import android.app.Activity;
import android.content.Context;
import android.databinding.Observable;
import android.databinding.ObservableList;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.wireguard.android.Application;
import com.wireguard.android.BR;
import com.wireguard.android.R;
import com.wireguard.android.databinding.TunnelEditorFragmentBinding;
import com.wireguard.android.fragment.AppListDialogFragment.AppExclusionListener;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.config.Attribute;
import com.wireguard.config.Config;
import com.wireguard.config.Peer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Fragment for editing a WireGuard configuration.
 */

public class TunnelEditorFragment extends BaseFragment implements AppExclusionListener {
    private static final String KEY_LOCAL_CONFIG = "local_config";
    private static final String KEY_ORIGINAL_NAME = "original_name";
    private static final String TAG = "WireGuard/" + TunnelEditorFragment.class.getSimpleName();

    @Nullable private TunnelEditorFragmentBinding binding;
    @Nullable private Tunnel tunnel;

    private void onConfigLoaded(final String name, final Config config) {
        if (binding != null) {
            binding.setConfig(new Config.Observable(config, name));
        }
    }

    private void onConfigSaved(final Tunnel savedTunnel,
                               @Nullable final Throwable throwable) {
        final String message;
        if (throwable == null) {
            message = getString(R.string.config_save_success, savedTunnel.getName());
            Log.d(TAG, message);
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            onFinished();
        } else {
            final String error = ExceptionLoggers.unwrapMessage(throwable);
            message = getString(R.string.config_save_error, savedTunnel.getName(), error);
            Log.e(TAG, message, throwable);
            if (binding != null) {
                Snackbar.make(binding.mainContainer, message, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.config_editor, menu);
    }

    private final ObservableList.OnListChangedCallback<? extends ObservableList<Peer.Observable>> breakObjectListOrientedLayeringHandler = new ObservableList.OnListChangedCallback<ObservableList<Peer.Observable>>() {
        @Override
        public void onChanged(final ObservableList<Peer.Observable> sender) { }
        @Override
        public void onItemRangeChanged(final ObservableList<Peer.Observable> sender, final int positionStart, final int itemCount) { }
        @Override
        public void onItemRangeMoved(final ObservableList<Peer.Observable> sender, final int fromPosition, final int toPosition, final int itemCount) { }

        @Override
        public void onItemRangeInserted(final ObservableList<Peer.Observable> sender, final int positionStart, final int itemCount) {
            if (binding != null)
                breakObjectOrientedLayeringHandler.onPropertyChanged(binding.getConfig(), BR.peers);
        }
        @Override
        public void onItemRangeRemoved(final ObservableList<Peer.Observable> sender, final int positionStart, final int itemCount) {
            if (binding != null)
                breakObjectOrientedLayeringHandler.onPropertyChanged(binding.getConfig(), BR.peers);
        }
    };

    private final Collection<Object> breakObjectOrientedLayeringHandlerReceivers = new ArrayList<>();
    private final Observable.OnPropertyChangedCallback breakObjectOrientedLayeringHandler = new Observable.OnPropertyChangedCallback() {
        @Override
        public void onPropertyChanged(final Observable sender, final int propertyId) {
            if (binding == null)
                return;
            final Config.Observable config = binding.getConfig();
            if (config == null)
                return;
            if (propertyId == BR.config) {
                config.addOnPropertyChangedCallback(breakObjectOrientedLayeringHandler);
                breakObjectOrientedLayeringHandlerReceivers.add(config);
                config.getInterfaceSection().addOnPropertyChangedCallback(breakObjectOrientedLayeringHandler);
                breakObjectOrientedLayeringHandlerReceivers.add(config.getInterfaceSection());
                config.getPeers().addOnListChangedCallback(breakObjectListOrientedLayeringHandler);
                breakObjectOrientedLayeringHandlerReceivers.add(config.getPeers());
            } else if (propertyId == BR.dnses || propertyId == BR.peers)
                ;
            else
                return;
            final int numSiblings = config.getPeers().size() - 1;
            for (final Peer.Observable peer : config.getPeers()) {
                peer.setInterfaceDNSRoutes(config.getInterfaceSection().getDnses());
                peer.setNumSiblings(numSiblings);
            }
        }
    };

    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = TunnelEditorFragmentBinding.inflate(inflater, container, false);
        binding.addOnPropertyChangedCallback(breakObjectOrientedLayeringHandler);
        breakObjectOrientedLayeringHandlerReceivers.add(binding);
        binding.executePendingBindings();
        return binding.getRoot();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onDestroyView() {
        binding = null;
        for (final Object o : breakObjectOrientedLayeringHandlerReceivers) {
            if (o instanceof Observable)
                ((Observable)o).removeOnPropertyChangedCallback(breakObjectOrientedLayeringHandler);
            else if (o instanceof ObservableList)
                ((ObservableList)o).removeOnListChangedCallback(breakObjectListOrientedLayeringHandler);
        }
        super.onDestroyView();
    }

    private void onFinished() {
        // Hide the keyboard; it rarely goes away on its own.
        final Activity activity = getActivity();
        if (activity == null) return;
        final View focusedView = activity.getCurrentFocus();
        if (focusedView != null) {
            final Object service = activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            final InputMethodManager inputManager = (InputMethodManager) service;
            if (inputManager != null)
                inputManager.hideSoftInputFromWindow(focusedView.getWindowToken(),
                        InputMethodManager.HIDE_NOT_ALWAYS);
        }
        // Tell the activity to finish itself or go back to the detail view.
        getActivity().runOnUiThread(() -> {
            // TODO(smaeul): Remove this hack when fixing the Config ViewModel
            // The selected tunnel has to actually change, but we have to remember this one.
            final Tunnel savedTunnel = tunnel;
            if (savedTunnel == getSelectedTunnel())
                setSelectedTunnel(null);
            setSelectedTunnel(savedTunnel);
        });
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_save:
                final Config newConfig = new Config();
                try {
                    binding.getConfig().commitData(newConfig);
                } catch (final Exception e) {
                    final String error = ExceptionLoggers.unwrapMessage(e);
                    final String tunnelName = tunnel == null ? binding.getConfig().getName() : tunnel.getName();
                    final String message = getString(R.string.config_save_error, tunnelName, error);
                    Log.e(TAG, message, e);
                    Snackbar.make(binding.mainContainer, error, Snackbar.LENGTH_LONG).show();
                    return false;
                }
                if (tunnel == null) {
                    Log.d(TAG, "Attempting to create new tunnel " + binding.getConfig().getName());
                    final TunnelManager manager = Application.getTunnelManager();
                    manager.create(binding.getConfig().getName(), newConfig)
                            .whenComplete(this::onTunnelCreated);
                } else if (!tunnel.getName().equals(binding.getConfig().getName())) {
                    Log.d(TAG, "Attempting to rename tunnel to " + binding.getConfig().getName());
                    tunnel.setName(binding.getConfig().getName())
                            .whenComplete((a, b) -> onTunnelRenamed(tunnel, newConfig, b));
                } else {
                    Log.d(TAG, "Attempting to save config of " + tunnel.getName());
                    tunnel.setConfig(newConfig)
                            .whenComplete((a, b) -> onConfigSaved(tunnel, b));
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        outState.putParcelable(KEY_LOCAL_CONFIG, binding.getConfig());
        outState.putString(KEY_ORIGINAL_NAME, tunnel == null ? null : tunnel.getName());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onSelectedTunnelChanged(@Nullable final Tunnel oldTunnel, @Nullable final Tunnel newTunnel) {
        tunnel = newTunnel;
        if (binding == null)
            return;
        binding.setConfig(new Config.Observable(null, null));
        if (tunnel != null)
            tunnel.getConfigAsync().thenAccept(a -> onConfigLoaded(tunnel.getName(), a));
    }

    private void onTunnelCreated(final Tunnel newTunnel, @Nullable final Throwable throwable) {
        final String message;
        if (throwable == null) {
            tunnel = newTunnel;
            message = getString(R.string.tunnel_create_success, tunnel.getName());
            Log.d(TAG, message);
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            onFinished();
        } else {
            final String error = ExceptionLoggers.unwrapMessage(throwable);
            message = getString(R.string.tunnel_create_error, error);
            Log.e(TAG, message, throwable);
            if (binding != null) {
                Snackbar.make(binding.mainContainer, message, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private void onTunnelRenamed(final Tunnel renamedTunnel, final Config newConfig,
                                 @Nullable final Throwable throwable) {
        final String message;
        if (throwable == null) {
            message = getString(R.string.tunnel_rename_success, renamedTunnel.getName());
            Log.d(TAG, message);
            // Now save the rest of configuration changes.
            Log.d(TAG, "Attempting to save config of renamed tunnel " + tunnel.getName());
            renamedTunnel.setConfig(newConfig).whenComplete((a, b) -> onConfigSaved(renamedTunnel, b));
        } else {
            final String error = ExceptionLoggers.unwrapMessage(throwable);
            message = getString(R.string.tunnel_rename_error, error);
            Log.e(TAG, message, throwable);
            if (binding != null) {
                Snackbar.make(binding.mainContainer, message, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onViewStateRestored(@Nullable final Bundle savedInstanceState) {
        if (binding == null) {
            return;
        }

        binding.setFragment(this);

        if (savedInstanceState == null) {
            onSelectedTunnelChanged(null, getSelectedTunnel());
        } else {
            tunnel = getSelectedTunnel();
            final Config.Observable config = savedInstanceState.getParcelable(KEY_LOCAL_CONFIG);
            final String originalName = savedInstanceState.getString(KEY_ORIGINAL_NAME);
            if (tunnel != null && !tunnel.getName().equals(originalName))
                onSelectedTunnelChanged(null, tunnel);
            else
                binding.setConfig(config);
        }

        super.onViewStateRestored(savedInstanceState);
    }

    public void onRequestSetExcludedApplications(@SuppressWarnings("unused") final View view) {
        final FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager != null && binding != null) {
            final String[] excludedApps = Attribute.stringToList(binding.getConfig().getInterfaceSection().getExcludedApplications());
            final AppListDialogFragment fragment = AppListDialogFragment.newInstance(excludedApps, this);
            fragment.show(fragmentManager, null);
        }
    }

    @Override
    public void onExcludedAppsSelected(final List<String> excludedApps) {
        Objects.requireNonNull(binding, "Tried to set excluded apps while no view was loaded");
        binding.getConfig().getInterfaceSection().setExcludedApplications(Attribute.iterableToString(excludedApps));
    }

}
