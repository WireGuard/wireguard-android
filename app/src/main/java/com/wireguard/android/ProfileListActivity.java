package com.wireguard.android;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

/**
 * Activity that allows creating/viewing/editing/deleting WireGuard profiles.
 */

public class ProfileListActivity extends ProfileActivity {
    private boolean isSplitLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_list_activity);
        isSplitLayout = findViewById(R.id.fragment_container) != null;
        if (!isSplitLayout) {
            // Avoid ProfileDetailFragment adding its menu when it is not in the view hierarchy.
            final Fragment fragment = getFragmentManager().findFragmentByTag(TAG_DETAIL);
            if (fragment != null) {
                final FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.remove(fragment);
                transaction.commit();
            }
        }
        onProfileSelected(getCurrentProfile());
    }

    public void onProfileSelected(String profile) {
        if (isSplitLayout) {
            updateLayout(profile);
            setCurrentProfile(profile);
        } else if (profile != null) {
            final Intent intent = new Intent(this, ProfileDetailActivity.class);
            intent.putExtra(KEY_PROFILE_NAME, profile);
            startActivity(intent);
            setCurrentProfile(null);
        }
    }

    public void updateLayout(String profile) {
        final Fragment fragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        if (profile != null) {
            if (fragment instanceof ProfileDetailFragment) {
                final ProfileDetailFragment detailFragment = (ProfileDetailFragment) fragment;
                detailFragment.setProfile(profile);
            } else {
                final ProfileDetailFragment detailFragment = new ProfileDetailFragment();
                detailFragment.setProfile(profile);
                final FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(R.id.fragment_container, detailFragment, TAG_DETAIL);
                transaction.commit();
            }
        } else {
            if (!(fragment instanceof PlaceholderFragment)) {
                final PlaceholderFragment placeholderFragment = new PlaceholderFragment();
                final FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(R.id.fragment_container, placeholderFragment, TAG_PLACEHOLDER);
                transaction.commit();
            }
        }
    }
}
