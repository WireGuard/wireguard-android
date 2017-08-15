package com.wireguard.android;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.wireguard.config.Config;

/**
 * Activity that allows creating/viewing/editing/deleting WireGuard configurations.
 */

public class ConfigActivity extends BaseConfigActivity {
    private ConfigDetailFragment detailFragment;
    private ConfigEditFragment editFragment;
    private final FragmentManager fm = getFragmentManager();
    private boolean isEditing;
    private boolean isServiceAvailable;
    private boolean isSplitLayout;
    private boolean isStateSaved;
    private ConfigListFragment listFragment;
    private int mainContainer;
    private final PlaceholderFragment placeholderFragment = new PlaceholderFragment();
    private boolean updateWasSkipped;

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // Ensure the current config is cleared when going back to the single-fragment-layout list.
        if (isEditing)
            isEditing = false;
        else
            setCurrentConfig(null);
        if (!isSplitLayout && fm.getBackStackEntryCount() == 0 && getActionBar() != null)
            getActionBar().setDisplayHomeAsUpEnabled(false);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        setContentView(R.layout.config_activity);
        isSplitLayout = findViewById(R.id.detail_fragment) != null;
        mainContainer = isSplitLayout ? R.id.detail_fragment : R.id.master_fragment;
        Log.d(getClass().getSimpleName(), "onCreate isSplitLayout=" + isSplitLayout);
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onCurrentConfigChanged(final Config config) {
        Log.d(getClass().getSimpleName(), "onCurrentConfigChanged config=" + (config != null ?
                config.getName() : null) + " fragment=" + fm.findFragmentById(mainContainer) +
                (!isServiceAvailable || isStateSaved ? " SKIPPING" : ""));
        // Avoid performing fragment transactions when it would be illegal or the service is null.
        if (!isServiceAvailable || isStateSaved) {
            // Signal that updates need to be performed once the activity is resumed.
            updateWasSkipped = true;
            return;
        } else {
            // Now that an update is being performed, reset the flag.
            updateWasSkipped = false;
        }
        // If the config change came from the intent or ConfigEditFragment, forward it to the list.
        // listFragment is guaranteed not to be null at this point by onServiceAvailable().
        if (listFragment.getCurrentConfig() != config)
            listFragment.setCurrentConfig(config);
        if (isEditing) {
            fm.popBackStackImmediate();
            isEditing = false;
        }
        if (config != null) {
            final boolean shouldPush = !isSplitLayout &&
                    fm.findFragmentById(mainContainer) instanceof ConfigListFragment;
            switchToFragment(mainContainer, TAG_DETAIL, shouldPush);
        } else if (isSplitLayout) {
            switchToFragment(mainContainer, TAG_PLACEHOLDER, false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.menu_action_edit:
                switchToFragment(mainContainer, TAG_EDIT, true);
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
        if (updateWasSkipped)
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
            final Fragment oldFragment = fm.findFragmentById(mainContainer);
            if (oldFragment != null) {
                fm.beginTransaction().remove(oldFragment).commit();
                fm.executePendingTransactions();
            }
        }
        isStateSaved = true;
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onServiceAvailable() {
        isServiceAvailable = true;
        // Create the initial fragment set.
        final Fragment masterFragment = fm.findFragmentById(R.id.master_fragment);
        if (masterFragment instanceof ConfigListFragment)
            listFragment = (ConfigListFragment) masterFragment;
        else
            switchToFragment(R.id.master_fragment, TAG_LIST, false);
        // This must run even if no update was skipped, so the restored config gets applied.
        onCurrentConfigChanged(getCurrentConfig());
    }

    private void switchToFragment(final int container, final String tag, final boolean push) {
        final Fragment fragment;
        if (tag.equals(TAG_PLACEHOLDER)) {
            fragment = placeholderFragment;
        } else {
            final BaseConfigFragment configFragment;
            switch (tag) {
                case TAG_DETAIL:
                    if (detailFragment == null)
                        detailFragment = new ConfigDetailFragment();
                    configFragment = detailFragment;
                    break;
                case TAG_EDIT:
                    if (editFragment == null)
                        editFragment = new ConfigEditFragment();
                    configFragment = editFragment;
                    break;
                case TAG_LIST:
                    if (listFragment == null)
                        listFragment = new ConfigListFragment();
                    configFragment = listFragment;
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            if (configFragment.getCurrentConfig() != getCurrentConfig())
                configFragment.setCurrentConfig(getCurrentConfig());
            fragment = configFragment;
        }
        if (fm.findFragmentById(container) != fragment) {
            final FragmentTransaction transaction = fm.beginTransaction();
            if (push) {
                transaction.addToBackStack(null);
                transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                if (!isSplitLayout && getActionBar() != null)
                    getActionBar().setDisplayHomeAsUpEnabled(true);
            }
            transaction.replace(container, fragment, null).commit();
        }
    }
}
