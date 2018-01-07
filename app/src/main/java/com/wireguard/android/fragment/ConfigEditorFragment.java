package com.wireguard.android.fragment;

import android.app.Activity;
import android.content.Context;
import android.databinding.ObservableField;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
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
import com.wireguard.android.databinding.ConfigEditorFragmentBinding;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.config.Config;

/**
 * Fragment for editing a WireGuard configuration.
 */

public class ConfigEditorFragment extends BaseFragment {
    private static final String KEY_LOCAL_CONFIG = "local_config";
    private static final String KEY_LOCAL_NAME = "local_name";
    private static final String TAG = ConfigEditorFragment.class.getSimpleName();

    private final ObservableField<String> localName = new ObservableField<>("");
    private ConfigEditorFragmentBinding binding;
    private boolean isViewStateRestored;
    private Config localConfig = new Config();
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

    private void onConfigCreated(final Tunnel tunnel, final Throwable throwable) {
        if (throwable != null) {
            Log.e(TAG, "Cannot create tunnel", throwable);
            final String message = "Cannot create tunnel: "
                    + ExceptionLoggers.unwrap(throwable).getMessage();
            if (binding != null) {
                final CoordinatorLayout container = binding.mainContainer;
                Snackbar.make(container, message, Snackbar.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "Successfully created tunnel " + tunnel.getName());
            onFinished(tunnel);
        }
    }

    private void onConfigLoaded(final Config config) {
        localConfig = copyParcelable(config);
        if (binding != null && isViewStateRestored)
            binding.setConfig(localConfig);
    }

    private void onConfigSaved(final Config config, final Throwable throwable) {
        if (throwable != null) {
            Log.e(TAG, "Cannot save configuration", throwable);
            final String message = "Cannot save configuration: "
                    + ExceptionLoggers.unwrap(throwable).getMessage();
            if (binding != null) {
                final CoordinatorLayout container = binding.mainContainer;
                Snackbar.make(container, message, Snackbar.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "Successfully saved configuration for " + getSelectedTunnel().getName());
            onFinished(getSelectedTunnel());
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            localConfig = savedInstanceState.getParcelable(KEY_LOCAL_CONFIG);
            localName.set(savedInstanceState.getString(KEY_LOCAL_NAME));
            originalName = savedInstanceState.getString(TunnelManager.KEY_SELECTED_TUNNEL);
        }
        // Erase the remains of creating or editing a different tunnel.
        if (getSelectedTunnel() != null && !getSelectedTunnel().getName().equals(originalName)) {
            // The config must be loaded asynchronously since it's not an observable property.
            localConfig = null;
            getSelectedTunnel().getConfigAsync().thenAccept(this::onConfigLoaded);
            originalName = getSelectedTunnel().getName();
            localName.set(originalName);
        } else if (getSelectedTunnel() == null && originalName != null) {
            localConfig = new Config();
            originalName = null;
            localName.set("");
        }
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.config_editor, menu);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = ConfigEditorFragmentBinding.inflate(inflater, container, false);
        binding.executePendingBindings();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private void onFinished(final Tunnel tunnel) {
        // Hide the keyboard; it rarely goes away on its own.
        final Activity activity = getActivity();
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
            setSelectedTunnel(null);
            setSelectedTunnel(tunnel);
        });
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_save:
                if (getSelectedTunnel() != null) {
                    Log.d(TAG, "Attempting to save config to " + getSelectedTunnel().getName());
                    getSelectedTunnel().setConfig(localConfig)
                            .whenComplete(this::onConfigSaved);
                } else {
                    Log.d(TAG, "Attempting to create new tunnel " + localName.get());
                    final TunnelManager manager = Application.getComponent().getTunnelManager();
                    manager.create(localName.get(), localConfig)
                            .whenComplete(this::onConfigCreated);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        outState.putParcelable(KEY_LOCAL_CONFIG, localConfig);
        outState.putString(KEY_LOCAL_NAME, localName.get());
        outState.putString(TunnelManager.KEY_SELECTED_TUNNEL, originalName);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onSelectedTunnelChanged(final Tunnel oldTunnel, final Tunnel newTunnel) {
        // Erase the remains of creating or editing a different tunnel.
        if (newTunnel != null) {
            // The config must be loaded asynchronously since it's not an observable property.
            localConfig = null;
            newTunnel.getConfigAsync().thenAccept(this::onConfigLoaded);
            originalName = newTunnel.getName();
            localName.set(originalName);
        } else {
            localConfig = new Config();
            if (binding != null && isViewStateRestored)
                binding.setConfig(localConfig);
            originalName = null;
            localName.set("");
        }
    }

    @Override
    public void onViewStateRestored(final Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        binding.setConfig(localConfig);
        binding.setName(localName);
        // FIXME: Remove this when renaming works.
        binding.interfaceNameText.setEnabled(originalName == null);
        isViewStateRestored = true;
    }
}
