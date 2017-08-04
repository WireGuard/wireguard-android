package com.wireguard.android;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.wireguard.android.databinding.ProfileListFragmentBinding;
import com.wireguard.config.Profile;

/**
 * Fragment containing the list of available WireGuard profiles. Must be part of a ProfileActivity.
 */

public class ProfileListFragment extends Fragment implements ServiceConnectionListener {
    private ProfileActivity activity;
    private ProfileListFragmentBinding binding;
    private ProfileServiceInterface service;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        activity = (ProfileActivity) context;
        activity.addServiceConnectionListener(this);
        service = activity.getService();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        binding = ProfileListFragmentBinding.inflate(inflater, parent, false);
        final ListView listView = (ListView) binding.getRoot();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Profile profile = (Profile) parent.getItemAtPosition(position);
                ((ProfileActivity) getActivity()).onProfileSelected(profile);
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                                           long id) {
                final Profile profile = (Profile) parent.getItemAtPosition(position);
                if (profile == null || service == null)
                    return false;
                if (profile.getIsConnected())
                    service.disconnectProfile(profile);
                else
                    service.connectProfile(profile);
                return true;
            }
        });
        return binding.getRoot();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        activity.removeServiceConnectionListener(this);
    }

    @Override
    public void onServiceConnected(ProfileServiceInterface service) {
        this.service = service;
        binding.setProfiles(service.getProfiles());
    }

    @Override
    public void onServiceDisconnected() {
        service = null;
    }
}
