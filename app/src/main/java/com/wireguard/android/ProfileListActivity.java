package com.wireguard.android;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

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
        final FragmentTransaction transaction = getFragmentManager().beginTransaction();
        final Fragment listFragment = getFragmentManager().findFragmentByTag(TAG_LIST);
        if (listFragment instanceof ProfileListFragment) {
            ((ProfileListFragment) listFragment).setIsSplitLayout(isSplitLayout);
        } else {
            final ProfileListFragment newListFragment = new ProfileListFragment();
            newListFragment.setIsSplitLayout(isSplitLayout);
            transaction.add(R.id.list_container, newListFragment, TAG_LIST);
        }
        if (!isSplitLayout) {
            // Avoid ProfileDetailFragment adding its menu when it is not in the view hierarchy.
            final Fragment detailFragment = getFragmentManager().findFragmentByTag(TAG_DETAIL);
            if (detailFragment != null)
                transaction.remove(detailFragment);
        }
        transaction.commit();
        if (isEditing())
            startEditing();
        else
            onProfileSelected(getCurrentProfile());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_edit:
                startEditing();
                return true;
            case R.id.menu_action_save:
                getFragmentManager().popBackStack();
                return false;
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return false;
        }
    }

    public void onProfileSelected(String profile) {
        if (isSplitLayout) {
            if (isEditing())
                getFragmentManager().popBackStack();
            setIsEditing(false);
            updateLayout(profile);
            setCurrentProfile(profile);
        } else if (profile != null) {
            final Intent intent = new Intent(this, ProfileDetailActivity.class);
            intent.putExtra(KEY_PROFILE_NAME, profile);
            startActivity(intent);
            setCurrentProfile(null);
        }
    }

    private void startEditing() {
        if (isSplitLayout) {
            setIsEditing(true);
            updateLayout(getCurrentProfile());
        } else if (getCurrentProfile() != null) {
            final Intent intent = new Intent(this, ProfileEditActivity.class);
            intent.putExtra(KEY_PROFILE_NAME, getCurrentProfile());
            startActivity(intent);
            setCurrentProfile(null);
            setIsEditing(false);
        }
    }

    public void updateLayout(String profile) {
        final Fragment fragment = getFragmentManager().findFragmentById(R.id.fragment_container);
        if (isEditing()) {
            if (fragment instanceof ProfileEditFragment) {
                final ProfileEditFragment editFragment = (ProfileEditFragment) fragment;
                if (!profile.equals(editFragment.getProfile()))
                    editFragment.setProfile(profile);
            } else {
                final ProfileEditFragment editFragment = new ProfileEditFragment();
                editFragment.setProfile(profile);
                final FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.addToBackStack(null);
                transaction.replace(R.id.fragment_container, editFragment, TAG_EDIT);
                transaction.commit();
            }
        } else if (profile != null) {
            if (fragment instanceof ProfileDetailFragment) {
                final ProfileDetailFragment detailFragment = (ProfileDetailFragment) fragment;
                if (!profile.equals(detailFragment.getProfile()))
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
