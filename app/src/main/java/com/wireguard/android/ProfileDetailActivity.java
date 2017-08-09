package com.wireguard.android;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

/**
 * Activity that allows viewing information about a single WireGuard profile.
 */

public class ProfileDetailActivity extends ProfileActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_detail_activity);
        setTitle(getCurrentProfile());
        Fragment detailFragment = getFragmentManager().findFragmentByTag(TAG_DETAIL);
        ((ProfileDetailFragment) detailFragment).setProfile(getCurrentProfile());
    }

    @Override
    public void onMenuEdit(MenuItem item) {
        final Intent intent = new Intent(this, ProfileEditActivity.class);
        intent.putExtra(KEY_PROFILE_NAME, getCurrentProfile());
        startActivity(intent);
    }

    @Override
    public void onMenuSave(MenuItem item) {
        throw new IllegalStateException();
    }
}
