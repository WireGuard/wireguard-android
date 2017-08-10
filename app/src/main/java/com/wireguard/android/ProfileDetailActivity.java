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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_edit:
                final Intent intent = new Intent(this, ProfileEditActivity.class);
                intent.putExtra(KEY_PROFILE_NAME, getCurrentProfile());
                startActivity(intent);
                return true;
            case R.id.menu_action_save:
                throw new IllegalStateException();
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return false;
        }
    }
}
