/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
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
import com.wireguard.android.R;
import com.wireguard.android.databinding.TunnelEditorFragmentBinding;
import com.wireguard.android.fragment.AppListDialogFragment.AppExclusionListener;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.config.Attribute;
import com.wireguard.config.Config;

import java.util.List;

/**
 * Fragment for editing a WireGuard configuration.
 */

public class TunnelEditorFragment extends BaseFragment implements AppExclusionListener {
    private static final String KEY_LOCAL_CONFIG = "local_config";
    private static final String KEY_ORIGINAL_NAME = "original_name";
    private static final String TAG = "WireGuard/" + TunnelEditorFragment.class.getSimpleName();

    private TunnelEditorFragmentBinding binding;
    private Tunnel tunnel;

    private void onConfigLoaded(final String name, final Config config) {
        binding.setConfig(new Config.Observable(config, name));
    }

    private void onConfigSaved(final Tunnel savedTunnel,
                               final Throwable throwable) {
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
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.config_editor, menu);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = TunnelEditorFragmentBinding.inflate(inflater, container, false);
        binding.executePendingBindings();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
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
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putParcelable(KEY_LOCAL_CONFIG, binding.getConfig());
        outState.putString(KEY_ORIGINAL_NAME, tunnel == null ? null : tunnel.getName());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onSelectedTunnelChanged(final Tunnel oldTunnel, final Tunnel newTunnel) {
        tunnel = newTunnel;
        if (binding == null)
            return;
        binding.setConfig(new Config.Observable(null, null));
        if (tunnel != null)
            tunnel.getConfigAsync().thenAccept(a -> onConfigLoaded(tunnel.getName(), a));
    }

    private void onTunnelCreated(final Tunnel newTunnel, final Throwable throwable) {
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
                                 final Throwable throwable) {
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
    public void onViewStateRestored(final Bundle savedInstanceState) {
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
        if (fragmentManager != null) {
            final String[] excludedApps = Attribute.stringToList(binding.getConfig().getInterfaceSection().getExcludedApplications());
            final AppListDialogFragment fragment = AppListDialogFragment.newInstance(excludedApps, this);
            fragment.show(getFragmentManager(), null);
        }
    }

    @Override
    public void onExcludedAppsSelected(final List<String> excludedApps) {
        binding.getConfig().getInterfaceSection().setExcludedApplications(Attribute.iterableToString(excludedApps));
    }

}
