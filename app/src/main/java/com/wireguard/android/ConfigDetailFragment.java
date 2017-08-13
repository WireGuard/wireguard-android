package com.wireguard.android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.wireguard.android.databinding.ConfigDetailFragmentBinding;
import com.wireguard.config.Config;

/**
 * Fragment for viewing information about a WireGuard configuration.
 */

public class ConfigDetailFragment extends BaseConfigFragment {
    private ConfigDetailFragmentBinding binding;

    @Override
    protected void onCurrentConfigChanged(final Config config) {
        if (binding != null)
            binding.setConfig(config);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.config_detail, menu);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup parent,
                             final Bundle savedInstanceState) {
        binding = ConfigDetailFragmentBinding.inflate(inflater, parent, false);
        binding.setConfig(getCurrentConfig());
        return binding.getRoot();
    }
}
