package com.wireguard.android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import com.wireguard.android.databinding.ProfileDetailFragmentBinding;

/**
 * Fragment for viewing and editing a WireGuard profile.
 */

public class ProfileDetailFragment extends ServiceClientFragment<ProfileServiceInterface> {
    private ProfileDetailFragmentBinding binding;
    private String name;

    public ProfileDetailFragment() {
        super();
        setArguments(new Bundle());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        name = getArguments().getString(ProfileActivity.KEY_PROFILE_NAME);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.profile_detail, menu);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        binding = ProfileDetailFragmentBinding.inflate(inflater, parent, false);
        return binding.getRoot();
    }

    @Override
    public void onServiceConnected(ProfileServiceInterface service) {
        super.onServiceConnected(service);
        binding.setProfile(service.getProfiles().get(name));
    }

    public void setProfile(String name) {
        this.name = name;
        getArguments().putString(ProfileActivity.KEY_PROFILE_NAME, name);
        final ProfileServiceInterface service = getService();
        if (binding != null && service != null)
            binding.setProfile(service.getProfiles().get(name));
    }
}
