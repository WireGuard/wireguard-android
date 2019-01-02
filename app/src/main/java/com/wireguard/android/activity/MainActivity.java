/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.wireguard.android.R;
import com.wireguard.android.fragment.TunnelDetailFragment;
import com.wireguard.android.fragment.TunnelEditorFragment;
import com.wireguard.android.fragment.TunnelListFragment;
import com.wireguard.android.model.Tunnel;

/**
 * CRUD interface for WireGuard tunnels. This activity serves as the main entry point to the
 * WireGuard application, and contains several fragments for listing, viewing details of, and
 * editing the configuration and interface state of WireGuard tunnels.
 */

public class MainActivity extends BaseActivity
        implements FragmentManager.OnBackStackChangedListener {
    @Nullable private ActionBar actionBar;
    private boolean isTwoPaneLayout;
    @Nullable private TunnelListFragment listFragment;

    @Override
    public void onBackPressed() {
        final int backStackEntries = getSupportFragmentManager().getBackStackEntryCount();
        // If the action menu is visible and expanded, collapse it instead of navigating back.
        if (isTwoPaneLayout || backStackEntries == 0) {
            if (listFragment != null && listFragment.collapseActionMenu())
                return;
        }
        // If the two-pane layout does not have an editor open, going back should exit the app.
        if (isTwoPaneLayout && backStackEntries <= 1) {
            finish();
            return;
        }
        // Deselect the current tunnel on navigating back from the detail pane to the one-pane list.
        if (!isTwoPaneLayout && backStackEntries == 1) {
            setSelectedTunnel(null);
            return;
        }
        super.onBackPressed();
    }

    @Override public void onBackStackChanged() {
        if (actionBar == null)
            return;
        // Do not show the home menu when the two-pane layout is at the detail view (see above).
        final int backStackEntries = getSupportFragmentManager().getBackStackEntryCount();
        final int minBackStackEntries = isTwoPaneLayout ? 2 : 1;
        actionBar.setDisplayHomeAsUpEnabled(backStackEntries >= minBackStackEntries);
    }

    // We use onTouchListener here to avoid the UI click sound, hence
    // calling View#performClick defeats the purpose of it.
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        actionBar = getSupportActionBar();
        isTwoPaneLayout = findViewById(R.id.master_detail_wrapper) instanceof LinearLayout;
        listFragment = (TunnelListFragment) getSupportFragmentManager().findFragmentByTag("LIST");
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        onBackStackChanged();
        final View actionBarView = findViewById(R.id.action_bar);
        if (actionBarView != null)
            actionBarView.setOnTouchListener((v, e) -> listFragment != null && listFragment.collapseActionMenu());
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // The back arrow in the action bar should act the same as the back button.
                onBackPressed();
                return true;
            case R.id.menu_action_edit:
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.detail_container, new TunnelEditorFragment())
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        .addToBackStack(null)
                        .commit();
                return true;
            case R.id.menu_action_save:
                // This menu item is handled by the editor fragment.
                return false;
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onSelectedTunnelChanged(@Nullable final Tunnel oldTunnel,
                                           @Nullable final Tunnel newTunnel) {
        final FragmentManager fragmentManager = getSupportFragmentManager();
        final int backStackEntries = fragmentManager.getBackStackEntryCount();
        if (newTunnel == null) {
            // Clear everything off the back stack (all editors and detail fragments).
            fragmentManager.popBackStackImmediate(0, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            return;
        }
        if (backStackEntries == 2) {
            // Pop the editor off the back stack to reveal the detail fragment. Use the immediate
            // method to avoid the editor picking up the new tunnel while it is still visible.
            fragmentManager.popBackStackImmediate();
        } else if (backStackEntries == 0) {
            // Create and show a new detail fragment.
            fragmentManager.beginTransaction()
                    .add(R.id.detail_container, new TunnelDetailFragment())
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .addToBackStack(null)
                    .commit();
        }
    }
}
