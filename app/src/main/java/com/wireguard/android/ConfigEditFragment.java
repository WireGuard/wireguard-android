package com.wireguard.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.wireguard.android.databinding.ConfigEditFragmentBinding;
import com.wireguard.config.Config;

/**
 * Fragment for editing a WireGuard configuration.
 */

public class ConfigEditFragment extends BaseConfigFragment {
    private static final String KEY_MODIFIED_CONFIG = "modifiedConfig";
    private static final String KEY_ORIGINAL_NAME = "originalName";

    public static void copyPublicKey(final Context context, final String publicKey) {
        if (publicKey == null || publicKey.isEmpty())
            return;
        final ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        final String description =
                context.getResources().getString(R.string.public_key_description);
        clipboard.setPrimaryClip(ClipData.newPlainText(description, publicKey));
        final String message = context.getResources().getString(R.string.public_key_copied_message);
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private Config localConfig;
    private String originalName;

    @Override
    protected void onCurrentConfigChanged(final Config config) {
        // Only discard modifications when the config *they are based on* changes.
        if (config == null || config.getName().equals(originalName) || localConfig == null)
            return;
        localConfig.copyFrom(config);
        originalName = config.getName();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Restore more saved information.
        if (savedInstanceState != null) {
            localConfig = savedInstanceState.getParcelable(KEY_MODIFIED_CONFIG);
            originalName = savedInstanceState.getString(KEY_ORIGINAL_NAME);
        } else if (getArguments() != null) {
            final Bundle arguments = getArguments();
            localConfig = arguments.getParcelable(KEY_MODIFIED_CONFIG);
            originalName = arguments.getString(KEY_ORIGINAL_NAME);
        }
        if (localConfig == null) {
            localConfig = new Config();
            originalName = null;
        }
        onCurrentConfigChanged(getCurrentConfig());
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.config_edit, menu);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup parent,
                             final Bundle savedInstanceState) {
        final ConfigEditFragmentBinding binding =
                ConfigEditFragmentBinding.inflate(inflater, parent, false);
        binding.setConfig(localConfig);
        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Reset changes to the config when the user cancels editing. See also the comment below.
        if (isRemoving())
            localConfig.copyFrom(getCurrentConfig());
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_save:
                saveConfig();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        // When ConfigActivity unwinds the back stack, isRemoving() is true, so localConfig will be
        // reset. Since outState is not serialized yet, it resets the saved config too. Avoid this
        // by copying the local config. originalName is fine because it is replaced, not modified.
        outState.putParcelable(KEY_MODIFIED_CONFIG, localConfig.copy());
        outState.putString(KEY_ORIGINAL_NAME, originalName);
    }

    private void saveConfig() {
        final VpnService service = VpnService.getInstance();
        try {
            if (getCurrentConfig() != null)
                service.update(getCurrentConfig().getName(), localConfig);
            else
                service.add(localConfig);
        } catch (final IllegalArgumentException | IllegalStateException e) {
            Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        // Hide the keyboard; it rarely goes away on its own.
        final Activity activity = getActivity();
        final View focusedView = activity.getCurrentFocus();
        if (focusedView != null) {
            final InputMethodManager inputManager =
                    (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(focusedView.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
        // Tell the activity to finish itself or go back to the detail view.
        ((BaseConfigActivity) activity).setIsEditing(false);
    }
}
