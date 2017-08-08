package com.wireguard.android;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.wireguard.android.databinding.ProfileListFragmentBinding;
import com.wireguard.config.Profile;

/**
 * Fragment containing the list of available WireGuard profiles.
 */

public class ProfileListFragment extends ServiceClientFragment<ProfileServiceInterface> {
    private ProfileListFragmentBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        binding = ProfileListFragmentBinding.inflate(inflater, parent, false);
        final ListView listView = (ListView) binding.getRoot();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Profile profile = (Profile) parent.getItemAtPosition(position);
                ((ProfileActivity) getActivity()).onProfileSelected(profile.getName());
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                                           long id) {
                final Profile profile = (Profile) parent.getItemAtPosition(position);
                final ProfileServiceInterface service = getService();
                if (profile == null || service == null)
                    return false;
                if (profile.getIsConnected())
                    service.disconnectProfile(profile.getName());
                else
                    service.connectProfile(profile.getName());
                return true;
            }
        });
        return binding.getRoot();
    }

    @Override
    public void onServiceConnected(ProfileServiceInterface service) {
        super.onServiceConnected(service);
        binding.setProfiles(service.getProfiles());
    }
}
