package com.wireguard.android;

import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

/**
 * Activity that allows editing a single WireGuard profile.
 */

public class ProfileEditActivity extends ProfileActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.profile_edit_activity);
        Fragment editFragment = getFragmentManager().findFragmentByTag(TAG_EDIT);
        ((ProfileEditFragment) editFragment).setProfile(getCurrentProfile());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_edit:
                throw new IllegalStateException();
            case R.id.menu_action_save:
                finish();
                return false;
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return false;
        }
    }
}
