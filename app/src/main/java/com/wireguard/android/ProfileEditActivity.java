package com.wireguard.android;

import android.view.MenuItem;

/**
 * Activity that allows editing a single WireGuard profile.
 */

public class ProfileEditActivity extends ProfileActivity {
    @Override
    public void onMenuEdit(MenuItem item) {
        throw new IllegalStateException();
    }

    @Override
    public void onMenuSave(MenuItem item) {

    }
}
