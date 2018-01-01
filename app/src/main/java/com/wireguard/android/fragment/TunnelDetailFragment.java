package com.wireguard.android.fragment;

import android.os.Bundle;
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
    private TunnelDetailFragmentBinding binding;
    private boolean isViewStateRestored;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.tunnel_detail, menu);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
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
    public void onSelectedTunnelChanged(final Tunnel oldTunnel, final Tunnel newTunnel) {
        if (binding != null && isViewStateRestored)
            binding.setTunnel(newTunnel);
    }

    @Override
    public void onViewStateRestored(final Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        binding.setTunnel(getSelectedTunnel());
        isViewStateRestored = true;
    }
}
