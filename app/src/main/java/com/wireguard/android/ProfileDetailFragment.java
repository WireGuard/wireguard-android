package com.wireguard.android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.wireguard.android.databinding.ProfileDetailFragmentBinding;

/**
 * Fragment for viewing information about a WireGuard profile.
 */

public class ProfileDetailFragment extends ProfileFragment {
    private ProfileDetailFragmentBinding binding;

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

    @Override
    public void onServiceConnected(ProfileServiceInterface service) {
        super.onServiceConnected(service);
        binding.setProfile(service.getProfiles().get(getProfile()));
    }

    @Override
    public void setProfile(String profile) {
        super.setProfile(profile);
        if (binding != null)
            binding.setProfile(getCachedProfile());
    }
}
