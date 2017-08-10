package com.wireguard.android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.wireguard.android.databinding.ProfileEditFragmentBinding;
import com.wireguard.config.Profile;

/**
 * Fragment for editing a WireGuard profile.
 */

public class ProfileEditFragment extends ProfileFragment {
    private ProfileEditFragmentBinding binding;
    private Profile copy;

    @Override
    protected void onCachedProfileChanged(Profile cachedProfile) {
        copy = cachedProfile != null ? cachedProfile.copy() : null;
        if (binding != null)
            binding.setProfile(copy);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.profile_edit, menu);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        binding = ProfileEditFragmentBinding.inflate(inflater, parent, false);
        binding.setProfile(copy);
        return binding.getRoot();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_save:
                final ProfileServiceInterface service = getService();
                if (service != null)
                    service.saveProfile(getProfile(), copy);
                return true;
            default:
                return false;
        }
    }
}
