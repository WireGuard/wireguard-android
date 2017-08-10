package com.wireguard.android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.wireguard.android.databinding.ProfileDetailFragmentBinding;
import com.wireguard.config.Profile;

/**
 * Fragment for viewing information about a WireGuard profile.
 */

public class ProfileDetailFragment extends ProfileFragment {
    private ProfileDetailFragmentBinding binding;

    @Override
    protected void onCachedProfileChanged(Profile cachedProfile) {
        if (binding != null)
            binding.setProfile(cachedProfile);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.profile_detail, menu);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        binding = ProfileDetailFragmentBinding.inflate(inflater, parent, false);
        binding.setProfile(getCachedProfile());
        return binding.getRoot();
    }
}
