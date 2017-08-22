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
    private static final String TAG_DETAIL = "detail";
    private static final String TAG_EDIT = "edit";
    private static final String TAG_LIST = "list";

    private final FragmentManager fm = getFragmentManager();
    private final FragmentCache fragments = new FragmentCache(fm);
    private boolean isEditing;
    private boolean isLayoutFinished;
    private boolean isServiceAvailable;
    private boolean isSplitLayout;
    private boolean isStateSaved;
    private int mainContainer;
    private String visibleFragmentTag;

    /**
     * Updates the fragment visible in the UI.
     * Sets visibleFragmentTag.
     *
     * @param config The config that should be visible.
     * @param tag    The tag of the fragment that should be visible.
     */
    private void moveToFragment(final Config config, final String tag) {
        // Sanity check.
        if (tag == null && config != null)
            throw new IllegalArgumentException("Cannot set a config on a null fragment");
        if ((tag == null && !isSplitLayout) || (TAG_LIST.equals(tag) && isSplitLayout))
            throw new IllegalArgumentException("Requested tag " + tag + " does not match layout");
        // First tear down fragments as necessary.
        if (tag == null || TAG_LIST.equals(tag) || (TAG_DETAIL.equals(tag)
                && TAG_EDIT.equals(visibleFragmentTag))) {
            while (visibleFragmentTag != null && !visibleFragmentTag.equals(tag) &&
                    fm.getBackStackEntryCount() > 0) {
                final Fragment removedFragment = fm.findFragmentById(mainContainer);
                // The fragment *must* be removed first, or it will stay attached to the layout!
                fm.beginTransaction().remove(removedFragment).commit();
                fm.popBackStackImmediate();
                // Recompute the visible fragment.
                if (TAG_EDIT.equals(visibleFragmentTag))
                    visibleFragmentTag = TAG_DETAIL;
                else if (!isSplitLayout && TAG_DETAIL.equals(visibleFragmentTag))
                    visibleFragmentTag = TAG_LIST;
                else
                    throw new IllegalStateException();
            }
        }
        // Now build up intermediate entries in the back stack as necessary.
        if (TAG_EDIT.equals(tag) && !TAG_DETAIL.equals(visibleFragmentTag))
            moveToFragment(config, TAG_DETAIL);
        // Finally, set the main container's content to the new top-level fragment.
        if (tag == null) {
            if (visibleFragmentTag != null) {
                final BaseConfigFragment fragment = fragments.get(visibleFragmentTag);
                fm.beginTransaction().remove(fragment).commit();
                fm.executePendingTransactions();
                visibleFragmentTag = null;
            }
        } else if (!TAG_LIST.equals(tag)) {
            final BaseConfigFragment fragment = fragments.get(tag);
            if (!tag.equals(visibleFragmentTag)) {
                final FragmentTransaction transaction = fm.beginTransaction();
                if (TAG_EDIT.equals(tag) || (!isSplitLayout && TAG_DETAIL.equals(tag))) {
                    transaction.addToBackStack(null);
                    transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                }
                transaction.replace(mainContainer, fragment, tag).commit();
                visibleFragmentTag = tag;
            }
            if (fragment.getCurrentConfig() != config)
                fragment.setCurrentConfig(config);
        }
    }

    /**
     * Transition the state machine to the desired state, if possible.
     * Sets currentConfig and isEditing.
     *
     * @param config          The desired config to show in the UI.
     * @param shouldBeEditing Whether or not the config should be in the editing state.
     */
    private void moveToState(final Config config, final boolean shouldBeEditing) {
        // Update the saved state.
        setCurrentConfig(config);
        isEditing = shouldBeEditing;
        // Avoid performing fragment transactions when the app is not fully initialized.
        if (!isLayoutFinished || !isServiceAvailable || isStateSaved)
            return;
        // Ensure the list is present in the master pane. It will be restored on activity restarts!
        final BaseConfigFragment listFragment = fragments.get(TAG_LIST);
        if (fm.findFragmentById(R.id.master_fragment) == null) {
            fm.beginTransaction().add(R.id.master_fragment, listFragment, TAG_LIST).commit();
            fm.executePendingTransactions();
        }
        // In the single-pane layout, the main container starts holding the list fragment.
        if (!isSplitLayout && visibleFragmentTag == null)
            visibleFragmentTag = TAG_LIST;
        // Forward any config changes to the list (they may have come from the intent or editing).
        listFragment.setCurrentConfig(config);
        // Ensure the correct main fragment is visible, adjusting the back stack as necessary.
        moveToFragment(config, shouldBeEditing ? TAG_EDIT :
                (config != null ? TAG_DETAIL : (isSplitLayout ? null : TAG_LIST)));
        // Show the current config as the title if the list of configurations is not visible.
        setTitle(!isSplitLayout && config != null ? config.getName() : getString(R.string.app_name));
        // Show or hide the action bar back button if the back stack is not empty.
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(config != null &&
                    (!isSplitLayout || shouldBeEditing));
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // The visible fragment is now the one that was on top of the back stack, if there was one.
        if (isEditing)
            visibleFragmentTag = TAG_DETAIL;
        else if (!isSplitLayout && TAG_DETAIL.equals(visibleFragmentTag))
            visibleFragmentTag = TAG_LIST;
        // If the user went back from the detail screen to the list, clear the current config.
        moveToState(isEditing ? getCurrentConfig() : null, false);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.config_activity);
        isSplitLayout = findViewById(R.id.detail_fragment) != null;
        mainContainer = isSplitLayout ? R.id.detail_fragment : R.id.master_fragment;
        isLayoutFinished = true;
        moveToState(getCurrentConfig(), isEditing);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    protected void onCurrentConfigChanged(final Config config) {
        Log.d(getClass().getSimpleName(), "onCurrentConfigChanged: config=" +
                (config != null ? config.getName() : null));
        // Abandon editing a config when the current config changes.
        moveToState(config, false);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // The back arrow in the action bar should act the same as the back button.
                onBackPressed();
                return true;
            case R.id.menu_action_add:
                startActivity(new Intent(this, AddActivity.class));
                return true;
            case R.id.menu_action_edit:
                // Try to make the editing fragment visible.
                moveToState(getCurrentConfig(), true);
                return true;
            case R.id.menu_action_save:
                // This menu item is handled by the editing fragment.
                return false;
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPostResume() {
        super.onPostResume();
        // Allow changes to fragments.
        isStateSaved = false;
        moveToState(getCurrentConfig(), isEditing);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        // We cannot save fragments that might switch between containers if the layout changes.
        if (isLayoutFinished && isServiceAvailable && !isStateSaved)
            moveToFragment(null, isSplitLayout ? null : TAG_LIST);
        // Prevent further changes to fragments.
        isStateSaved = true;
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onServiceAvailable() {
        super.onServiceAvailable();
        // Allow creating fragments.
        isServiceAvailable = true;
        moveToState(getCurrentConfig(), isEditing);
    }

    private static class FragmentCache {
        private ConfigDetailFragment detailFragment;
        private ConfigEditFragment editFragment;
        private final FragmentManager fm;
        private ConfigListFragment listFragment;

        private FragmentCache(final FragmentManager fm) {
            this.fm = fm;
        }

        private BaseConfigFragment get(final String tag) {
            switch (tag) {
                case TAG_DETAIL:
                    if (detailFragment == null)
                        detailFragment = (ConfigDetailFragment) fm.findFragmentByTag(tag);
                    if (detailFragment == null)
                        detailFragment = new ConfigDetailFragment();
                    return detailFragment;
                case TAG_EDIT:
                    if (editFragment == null)
                        editFragment = (ConfigEditFragment) fm.findFragmentByTag(tag);
                    if (editFragment == null)
                        editFragment = new ConfigEditFragment();
                    return editFragment;
                case TAG_LIST:
                    if (listFragment == null)
                        listFragment = (ConfigListFragment) fm.findFragmentByTag(tag);
                    if (listFragment == null)
                        listFragment = new ConfigListFragment();
                    return listFragment;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }
}
