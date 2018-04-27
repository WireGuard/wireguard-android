package com.wireguard.android.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import com.commonsware.cwac.crossport.design.widget.CoordinatorLayout;
import com.commonsware.cwac.crossport.design.widget.Snackbar;
import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.databinding.TunnelEditorFragmentBinding;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.config.Config;

/**
 * Fragment for editing a WireGuard configuration.
 */

public class TunnelEditorFragment extends BaseFragment {
    private static final String KEY_LOCAL_CONFIG = "local_config";
    private static final String KEY_ORIGINAL_NAME = "original_name";
    private static final String TAG = "WireGuard/" + TunnelEditorFragment.class.getSimpleName();

    private TunnelEditorFragmentBinding binding;
    private boolean isViewStateRestored;
    private Config localConfig = new Config();
    private Tunnel localTunnel;
    private String originalName;

    private static <T extends Parcelable> T copyParcelable(final T original) {
        if (original == null)
            return null;
        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(original, 0);
        parcel.setDataPosition(0);
        final T copy = parcel.readParcelable(original.getClass().getClassLoader());
        parcel.recycle();
        return copy;
    }

    private void onConfigLoaded(final Config config) {
        localConfig = copyParcelable(config);
        if (binding != null && isViewStateRestored)
            binding.setConfig(new Config.Observable(localConfig, originalName));
    }

    private void onConfigSaved(@SuppressWarnings("unused") final Config config,
                               final Throwable throwable) {
        final String message;
        if (throwable == null) {
            message = getString(R.string.config_save_success, localTunnel.getName());
            Log.d(TAG, message);
            onFinished();
        } else {
            final String error = ExceptionLoggers.unwrap(throwable).getMessage();
            message = getString(R.string.config_save_error, localTunnel.getName(), error);
            Log.e(TAG, message, throwable);
            if (binding != null) {
                final CoordinatorLayout container = binding.mainContainer;
                Snackbar.make(container, message, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            localConfig = savedInstanceState.getParcelable(KEY_LOCAL_CONFIG);
            originalName = savedInstanceState.getString(KEY_ORIGINAL_NAME);
        }
        // Erase the remains of creating or editing a different tunnel.
        if (getSelectedTunnel() != null && !getSelectedTunnel().getName().equals(originalName)) {
            // The config must be loaded asynchronously since it's not an observable property.
            localConfig = null;
            originalName = getSelectedTunnel().getName();
            getSelectedTunnel().getConfigAsync().thenAccept(this::onConfigLoaded);
        } else if (getSelectedTunnel() == null && originalName != null) {
            localConfig = new Config();
            originalName = null;
        }
        localTunnel = getSelectedTunnel();
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
            final Tunnel savedTunnel = localTunnel;
            if (savedTunnel == getSelectedTunnel())
                setSelectedTunnel(null);
            setSelectedTunnel(savedTunnel);
        });
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_save:
                final Tunnel selectedTunnel = getSelectedTunnel();
                if (localConfig != null) {
                    try {
                        binding.getConfig().commitData(localConfig);
                    } catch (Exception e) {
                        final String error = ExceptionLoggers.unwrap(e).getMessage();
                        final String message = getString(R.string.config_save_error, localTunnel.getName(), error);
                        Log.e(TAG, message, e);
                        final CoordinatorLayout container = binding.mainContainer;
                        Snackbar.make(container, error, Snackbar.LENGTH_LONG).show();
                        return false;
                    }
                }
                if (selectedTunnel == null) {
                    Log.d(TAG, "Attempting to create new tunnel " + binding.getConfig().getName());
                    final TunnelManager manager = Application.getComponent().getTunnelManager();
                    manager.create(binding.getConfig().getName(), localConfig)
                            .whenComplete(this::onTunnelCreated);
                } else if (!selectedTunnel.getName().equals(binding.getConfig().getName())) {
                    Log.d(TAG, "Attempting to rename tunnel to " + binding.getConfig().getName());
                    selectedTunnel.setName(binding.getConfig().getName())
                            .whenComplete(this::onTunnelRenamed);
                } else if (localConfig != null) {
                    Log.d(TAG, "Attempting to save config of " + selectedTunnel.getName());
                    selectedTunnel.setConfig(localConfig)
                            .whenComplete(this::onConfigSaved);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        outState.putParcelable(KEY_LOCAL_CONFIG, localConfig);
        outState.putString(KEY_ORIGINAL_NAME, originalName);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onSelectedTunnelChanged(final Tunnel oldTunnel, final Tunnel newTunnel) {
        // Erase the remains of creating or editing a different tunnel.
        if (newTunnel != null) {
            // The config must be loaded asynchronously since it's not an observable property.
            localConfig = null;
            originalName = newTunnel.getName();
            newTunnel.getConfigAsync().thenAccept(this::onConfigLoaded);
        } else {
            localConfig = new Config();
            if (binding != null && isViewStateRestored)
                binding.setConfig(new Config.Observable(localConfig, ""));
            originalName = null;
        }
        localTunnel = newTunnel;
    }

    private void onTunnelCreated(final Tunnel tunnel, final Throwable throwable) {
        final String message;
        if (throwable == null) {
            message = getString(R.string.tunnel_create_success, tunnel.getName());
            Log.d(TAG, message);
            localTunnel = tunnel;
            onFinished();
        } else {
            final String error = ExceptionLoggers.unwrap(throwable).getMessage();
            message = getString(R.string.tunnel_create_error, error);
            Log.e(TAG, message, throwable);
            if (binding != null) {
                final CoordinatorLayout container = binding.mainContainer;
                Snackbar.make(container, message, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    private void onTunnelRenamed(final String name, final Throwable throwable) {
        final String message;
        if (throwable == null) {
            message = getString(R.string.tunnel_rename_success, localTunnel.getName(), name);
            Log.d(TAG, message);
            // Now save the rest of configuration changes.
            Log.d(TAG, "Attempting to save config of renamed tunnel " + localTunnel.getName());
            localTunnel.setConfig(localConfig).whenComplete(this::onConfigSaved);
        } else {
            final String error = ExceptionLoggers.unwrap(throwable).getMessage();
            message = getString(R.string.tunnel_rename_error, error);
            Log.e(TAG, message, throwable);
            if (binding != null) {
                final CoordinatorLayout container = binding.mainContainer;
                Snackbar.make(container, message, Snackbar.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onViewStateRestored(final Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (localConfig == null)
            localConfig = new Config();
        binding.setConfig(new Config.Observable(localConfig, originalName));
        isViewStateRestored = true;
    }
}
