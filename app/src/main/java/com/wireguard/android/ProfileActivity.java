package com.wireguard.android;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

/**
 * Base class for activities that use ProfileListFragment and ProfileDetailFragment.
 */

abstract class ProfileActivity extends ServiceClientActivity<ProfileServiceInterface> {
    public static final String KEY_IS_EDITING = "is_editing";
    public static final String KEY_PROFILE_NAME = "profile_name";
    protected static final String TAG_DETAIL = "detail";
    protected static final String TAG_LIST = "list";
    protected static final String TAG_PLACEHOLDER = "placeholder";

    private String currentProfile;
    private boolean isEditing;

    public ProfileActivity() {
        super(ProfileService.class);
    }

    protected String getCurrentProfile() {
        return currentProfile;
    }

    protected boolean isEditing() {
        return isEditing;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Restore the saved profile if there is one; otherwise grab it from the intent.
        if (savedInstanceState != null) {
            currentProfile = savedInstanceState.getString(KEY_PROFILE_NAME);
            isEditing = savedInstanceState.getBoolean(KEY_IS_EDITING, false);
        } else {
            final Intent intent = getIntent();
            currentProfile = intent.getStringExtra(KEY_PROFILE_NAME);
            isEditing = intent.getBooleanExtra(KEY_IS_EDITING, false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public abstract void onMenuEdit(MenuItem item);

    public abstract void onMenuSave(MenuItem item);

    public void onMenuSettings(MenuItem item) {

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IS_EDITING, isEditing);
        outState.putString(KEY_PROFILE_NAME, currentProfile);
    }

    protected void setCurrentProfile(String profile) {
        currentProfile = profile;
    }

    protected void setIsEditing(boolean isEditing) {
        this.isEditing = isEditing;
    }
}
