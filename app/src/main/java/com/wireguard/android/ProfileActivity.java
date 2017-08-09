package com.wireguard.android;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

/**
 * Base class for activities that use ProfileListFragment and ProfileDetailFragment.
 */

abstract class ProfileActivity extends ServiceClientActivity<ProfileServiceInterface> {
    public static final String KEY_PROFILE_NAME = "profile_name";
    protected static final String TAG_DETAIL = "detail";
    protected static final String TAG_LIST = "list";
    protected static final String TAG_PLACEHOLDER = "placeholder";

    private String currentProfile;

    public ProfileActivity() {
        super(ProfileService.class);
    }

    protected String getCurrentProfile() {
        return currentProfile;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Restore the saved profile if there is one; otherwise grab it from the intent.
        if (savedInstanceState != null)
            currentProfile = savedInstanceState.getString(KEY_PROFILE_NAME);
        else
            currentProfile = getIntent().getStringExtra(KEY_PROFILE_NAME);
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

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_PROFILE_NAME, currentProfile);
    }

    protected void setCurrentProfile(String profile) {
        currentProfile = profile;
    }
}
