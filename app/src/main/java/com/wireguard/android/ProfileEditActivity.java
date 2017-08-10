package com.wireguard.android;

import android.content.Intent;
import android.view.MenuItem;

/**
 * Activity that allows editing a single WireGuard profile.
 */

public class ProfileEditActivity extends ProfileActivity {
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
