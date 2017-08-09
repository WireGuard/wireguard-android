package com.wireguard.android;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.res.Configuration;
import android.os.Bundle;

/**
 * Activity that allows creating/viewing/editing/deleting WireGuard profiles.
 */

public class ProfileListActivity extends ProfileActivity {
    private boolean isSplitLayout;

    @Override
    public void onBackPressed() {
        final FragmentManager fm = getFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set up the base layout and fill it with fragments.
        setContentView(R.layout.profile_list_activity);
        final int orientation = getResources().getConfiguration().orientation;
        isSplitLayout = orientation == Configuration.ORIENTATION_LANDSCAPE;
        updateLayout(getCurrentProfile());
    }

    public void onProfileSelected(String profile) {
        updateLayout(profile);
        setCurrentProfile(profile);
    }

    private void updateLayout(String profile) {
        final FragmentManager fm = getFragmentManager();
        final Fragment detailFragment = fm.findFragmentByTag(TAG_DETAIL);
        final Fragment listFragment = fm.findFragmentByTag(TAG_LIST);
        final FragmentTransaction transaction = fm.beginTransaction();
        if (profile != null) {
            if (isSplitLayout) {
                if (listFragment.isHidden())
                    transaction.show(listFragment);
            } else {
                transaction.hide(listFragment);
            }
            if (detailFragment.isHidden())
                transaction.show(detailFragment);
        } else {
            if (isSplitLayout) {
                if (detailFragment.isHidden())
                    transaction.show(detailFragment);
            } else {
                transaction.hide(detailFragment);
            }
            if (listFragment.isHidden())
                transaction.show(listFragment);
        }
        transaction.commit();
        ((ProfileDetailFragment) detailFragment).setProfile(profile);
    }
}
