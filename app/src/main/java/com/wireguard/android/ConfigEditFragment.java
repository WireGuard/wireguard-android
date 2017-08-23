package com.wireguard.android;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.wireguard.android.databinding.ConfigEditFragmentBinding;
import com.wireguard.config.Config;

/**
 * Fragment for editing a WireGuard configuration.
 */

public class ConfigEditFragment extends BaseConfigFragment {
    private final Config localConfig = new Config();

    @Override
    protected void onCurrentConfigChanged(final Config config) {
        localConfig.copyFrom(config);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.config_edit, menu);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup parent,
                             final Bundle savedInstanceState) {
        final ConfigEditFragmentBinding binding =
                ConfigEditFragmentBinding.inflate(inflater, parent, false);
        binding.setConfig(localConfig);
        return binding.getRoot();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_save:
                saveConfig();
                return true;
            default:
                return false;
        }
    }

    private void saveConfig() {
        final String errorMessage = localConfig.validate();
        if (errorMessage != null) {
            Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (getCurrentConfig() != null)
                VpnService.getInstance().update(getCurrentConfig().getName(), localConfig);
            else
                VpnService.getInstance().add(localConfig);
        } catch (final IllegalStateException e) {
            Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        // Hide the keyboard; it rarely goes away on its own.
        final BaseConfigActivity activity = (BaseConfigActivity) getActivity();
        final View focusedView = activity.getCurrentFocus();
        if (focusedView != null) {
            final InputMethodManager inputManager =
                    (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(focusedView.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
        // Tell the activity to finish itself or go back to the detail view.
        activity.setCurrentConfig(localConfig);
    }
}
