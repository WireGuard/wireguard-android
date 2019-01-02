/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.fragment;

import android.os.Bundle;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.wireguard.android.R;
import com.wireguard.android.databinding.TunnelDetailFragmentBinding;
import com.wireguard.android.model.Tunnel;

/**
 * Fragment that shows details about a specific tunnel.
 */

public class TunnelDetailFragment extends BaseFragment {
    @Nullable private TunnelDetailFragmentBinding binding;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.tunnel_detail, menu);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = TunnelDetailFragmentBinding.inflate(inflater, container, false);
        binding.executePendingBindings();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onSelectedTunnelChanged(@Nullable final Tunnel oldTunnel, @Nullable final Tunnel newTunnel) {
        if (binding == null)
            return;
        binding.setTunnel(newTunnel);
        if (newTunnel == null)
            binding.setConfig(null);
        else
            newTunnel.getConfigAsync().thenAccept(binding::setConfig);
    }

    @Override
    public void onViewStateRestored(@Nullable final Bundle savedInstanceState) {
        if (binding == null) {
            return;
        }

        binding.setFragment(this);
        onSelectedTunnelChanged(null, getSelectedTunnel());
        super.onViewStateRestored(savedInstanceState);
    }

}
