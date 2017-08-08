package com.wireguard.android;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

/**
 * Activity that allows creating/viewing/editing/deleting WireGuard profiles.
 */

public class ProfileActivity extends ServiceClientActivity<ProfileServiceInterface> {
    public static final String KEY_PROFILE_NAME = "profile_name";

    // FIXME: These must match the constants in profile_list_activity.xml
    private static final String TAG_DETAIL = "detail";
    private static final String TAG_LIST = "list";

    private String currentProfile;
    private boolean isSplitLayout;

    public ProfileActivity() {
        super(ProfileService.class);
    }

    @Override
    public void onBackPressed() {
        if (!isSplitLayout && currentProfile != null) {
            onProfileSelected(null);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Restore the saved profile if there is one; otherwise grab it from the intent.
        if (savedInstanceState != null)
            currentProfile = savedInstanceState.getString(KEY_PROFILE_NAME);
        else
            currentProfile = getIntent().getStringExtra(KEY_PROFILE_NAME);
        // Set up the base layout and fill it with fragments.
        setContentView(R.layout.profile_activity);
        final int orientation = getResources().getConfiguration().orientation;
        isSplitLayout = orientation == Configuration.ORIENTATION_LANDSCAPE;
        updateLayout(currentProfile);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void onMenuEdit(MenuItem item) {

    }

    public void onMenuSave(MenuItem item) {

    }

    public void onMenuSettings(MenuItem item) {

    }

    public void onProfileSelected(String profile) {
        updateLayout(profile);
        currentProfile = profile;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PROFILE_NAME, currentProfile);
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
