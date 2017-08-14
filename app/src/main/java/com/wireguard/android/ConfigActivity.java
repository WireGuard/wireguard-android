package com.wireguard.android;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import com.wireguard.config.Config;

/**
 * Activity that allows creating/viewing/editing/deleting WireGuard configurations.
 */

public class ConfigActivity extends BaseConfigActivity {
    private int containerId;
    private final FragmentManager fm = getFragmentManager();
    private boolean isEditing;
    private boolean isServiceAvailable;
    private boolean isSplitLayout;
    private boolean isStateSaved;

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Make sure the current config is cleared when going back to the list.
        if (isEditing)
            isEditing = false;
        else
            setCurrentConfig(null);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.config_activity);
        isSplitLayout = findViewById(R.id.detail_fragment) != null;
        if (isSplitLayout)
            containerId = R.id.detail_fragment;
        else
            containerId = R.id.master_fragment;
    }

    @Override
    protected void onCurrentConfigChanged(final Config config) {
        if (!isServiceAvailable || isStateSaved)
            return;
        final Fragment currentFragment = fm.findFragmentById(containerId);
        Log.d(getClass().getSimpleName(), "onCurrentConfigChanged config=" +
                (config != null ? config.getName() : null) + " fragment=" + currentFragment);
        if (currentFragment instanceof ConfigDetailFragment) {
            // Handle the case when the split layout is switching from one config to another.
            final ConfigDetailFragment detailFragment = (ConfigDetailFragment) currentFragment;
            if (detailFragment.getCurrentConfig() != config)
                detailFragment.setCurrentConfig(config);
        } else if (currentFragment instanceof ConfigEditFragment) {
            // Handle the case when ConfigEditFragment is finished updating a config.
            fm.popBackStack();
            isEditing = false;
            final ConfigDetailFragment detailFragment =
                    (ConfigDetailFragment) fm.findFragmentByTag(TAG_DETAIL);
            if (detailFragment.getCurrentConfig() != config)
                detailFragment.setCurrentConfig(config);
        } else if (config != null) {
            // Handle the single-fragment-layout case and the case when a placeholder is replaced.
            ConfigDetailFragment detailFragment =
                    (ConfigDetailFragment) fm.findFragmentByTag(TAG_DETAIL);
            if (detailFragment != null) {
                detailFragment.setCurrentConfig(config);
            } else {
                detailFragment = new ConfigDetailFragment();
                final Bundle arguments = new Bundle();
                arguments.putString(KEY_CURRENT_CONFIG, config.getName());
                detailFragment.setArguments(arguments);
            }
            final FragmentTransaction transaction = fm.beginTransaction();
            if (!isSplitLayout)
                transaction.addToBackStack(TAG_DETAIL);
            transaction.replace(containerId, detailFragment, TAG_DETAIL);
            transaction.commit();
        } else {
            if (isSplitLayout) {
                // Handle the split layout case when there is no config, so a placeholder is shown.
                PlaceholderFragment placeholderFragment =
                        (PlaceholderFragment) fm.findFragmentByTag(TAG_PLACEHOLDER);
                if (placeholderFragment == null)
                    placeholderFragment = new PlaceholderFragment();
                final FragmentTransaction transaction = fm.beginTransaction();
                transaction.replace(containerId, placeholderFragment, TAG_PLACEHOLDER);
                transaction.commit();
            }
        }
        // If the config change came from the intent or ConfigEditFragment, forward it to the list.
        ConfigListFragment listFragment = (ConfigListFragment) fm.findFragmentByTag(TAG_LIST);
        if (listFragment == null) {
            listFragment = new ConfigListFragment();
            final FragmentTransaction transaction = fm.beginTransaction();
            transaction.replace(R.id.master_fragment, listFragment, TAG_LIST);
            transaction.commit();
        }
        if (listFragment.getCurrentConfig() != config)
            listFragment.setCurrentConfig(config);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_edit:
                ConfigEditFragment editFragment =
                        (ConfigEditFragment) fm.findFragmentByTag(TAG_EDIT);
                if (editFragment != null) {
                    editFragment.setCurrentConfig(getCurrentConfig());
                } else {
                    editFragment = new ConfigEditFragment();
                    final Bundle arguments = new Bundle();
                    arguments.putString(KEY_CURRENT_CONFIG, getCurrentConfig().getName());
                    editFragment.setArguments(arguments);
                }
                final FragmentTransaction transaction = fm.beginTransaction();
                transaction.addToBackStack(TAG_EDIT);
                transaction.replace(containerId, editFragment, TAG_EDIT);
                transaction.commit();
                isEditing = true;
                return true;
            case R.id.menu_action_save:
                // This menu item is handled by the current fragment.
                return false;
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onPostResume() {
        super.onPostResume();
        isStateSaved = false;
        onCurrentConfigChanged(getCurrentConfig());
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        // We cannot save fragments that might switch between containers if the layout changes.
        if (fm.getBackStackEntryCount() > 0) {
            final int bottomEntryId = fm.getBackStackEntryAt(0).getId();
            fm.popBackStackImmediate(bottomEntryId, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        if (isSplitLayout) {
            final Fragment oldFragment = fm.findFragmentById(containerId);
            if (oldFragment != null)
                fm.beginTransaction().remove(oldFragment).commit();
        }
        isStateSaved = true;
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onServiceAvailable() {
        // Create the initial fragment set.
        isServiceAvailable = true;
        onCurrentConfigChanged(getCurrentConfig());
    }
}
