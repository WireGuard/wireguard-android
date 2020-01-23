/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.fragment;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.wireguard.android.R;
import com.wireguard.android.databinding.TunnelDetailFragmentBinding;
import com.wireguard.android.databinding.TunnelDetailPeerBinding;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.crypto.Key;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Fragment that shows details about a specific tunnel.
 */

public class TunnelDetailFragment extends BaseFragment {
    @Nullable private TunnelDetailFragmentBinding binding;
    @Nullable private Timer timer;
    @Nullable private State lastState = State.TOGGLE;

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
    public void onStop() {
        super.onStop();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateStats();
            }
        }, 0, 1000);
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
        lastState = State.TOGGLE;
        updateStats();
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

    private String formatBytes(final long bytes) {
        if (bytes < 1024)
            return getContext().getString(R.string.transfer_bytes, bytes);
        else if (bytes < 1024*1024)
            return getContext().getString(R.string.transfer_kibibytes, bytes/1024.0);
        else if (bytes < 1024*1024*1024)
            return getContext().getString(R.string.transfer_mibibytes, bytes/(1024.0*1024.0));
        else if (bytes < 1024*1024*1024*1024)
            return getContext().getString(R.string.transfer_gibibytes, bytes/(1024.0*1024.0*1024.0));
        return getContext().getString(R.string.transfer_tibibytes, bytes/(1024.0*1024.0*1024.0)/1024.0);
    }

    private void updateStats() {
        if (binding == null || !isResumed())
            return;
        final Tunnel tunnel = binding.getTunnel();
        if (tunnel == null)
            return;
        final State state = tunnel.getState();
        if (state != State.UP && lastState == state)
            return;
        lastState = state;
        tunnel.getStatisticsAsync().whenComplete((statistics, throwable) -> {
            if (throwable != null) {
                for (int i = 0; i < binding.peersLayout.getChildCount(); ++i) {
                    final TunnelDetailPeerBinding peer = DataBindingUtil.getBinding(binding.peersLayout.getChildAt(i));
                    if (peer == null)
                        continue;
                    peer.transferLabel.setVisibility(View.GONE);
                    peer.transferText.setVisibility(View.GONE);
                }
                return;
            }
            for (int i = 0; i < binding.peersLayout.getChildCount(); ++i) {
                final TunnelDetailPeerBinding peer = DataBindingUtil.getBinding(binding.peersLayout.getChildAt(i));
                if (peer == null)
                    continue;
                final Key publicKey = peer.getItem().getPublicKey();
                final long rx = statistics.peerRx(publicKey);
                final long tx = statistics.peerTx(publicKey);
                if (rx == 0 && tx == 0) {
                    peer.transferLabel.setVisibility(View.GONE);
                    peer.transferText.setVisibility(View.GONE);
                    continue;
                }
                peer.transferText.setText(getContext().getString(R.string.transfer_rx_tx, formatBytes(rx), formatBytes(tx)));
                peer.transferLabel.setVisibility(View.VISIBLE);
                peer.transferText.setVisibility(View.VISIBLE);
            }
        });
    }
}
