package com.wireguard.android;

import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.LoginFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import com.wireguard.android.databinding.ConfigEditFragmentBinding;
import com.wireguard.config.Config;
import com.wireguard.crypto.KeyEncoding;

/**
 * Fragment for editing a WireGuard configuration.
 */

public class ConfigEditFragment extends BaseConfigFragment {
    private final Config localConfig = new Config();

    @Override
    protected void onCurrentConfigChanged(final Config config) {
        localConfig.copyFrom(config);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        final EditText configNameText = binding.getRoot().findViewById(R.id.config_name_text);
        configNameText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(16),
                new LoginFilter.UsernameFilterGeneric() {
                    @Override
                    public boolean isAllowed(final char c) {
                        return Character.isLetterOrDigit(c) || "_=+.-".indexOf(c) != -1;
                    }
                }
        });
        final EditText privateKeyText = binding.getRoot().findViewById(R.id.private_key_text);
        privateKeyText.setFilters(new InputFilter[]{
                new InputFilter() {
                    @Override
                    public CharSequence filter(final CharSequence source,
                                               final int sStart, final int sEnd,
                                               final Spanned dest,
                                               final int dStart, final int dEnd) {
                        SpannableStringBuilder replacement = null;
                        int rIndex = 0;
                        final int dLength = dest.length();
                        for (int sIndex = sStart; sIndex < sEnd; ++sIndex) {
                            final char c = source.charAt(sIndex);
                            final int dIndex = dStart + (sIndex - sStart);
                            // Restrict characters to the base64 character set.
                            // Ensure adding this character does not push the length over the limit.
                            if (((dIndex + 1 < KeyEncoding.KEY_LENGTH_BASE64 && isAllowed(c)) ||
                                    (dIndex + 1 == KeyEncoding.KEY_LENGTH_BASE64 && c == '=')) &&
                                    dLength + (sIndex - sStart) < KeyEncoding.KEY_LENGTH_BASE64) {
                                ++rIndex;
                            } else {
                                if (replacement == null)
                                    replacement = new SpannableStringBuilder(source, sStart, sEnd);
                                replacement.delete(rIndex, rIndex + 1);
                            }
                        }
                        return replacement;
                    }

                    private boolean isAllowed(final char c) {
                        return Character.isLetterOrDigit(c) || c == '+' || c == '/';
                    }
                }
        });
        binding.setConfig(localConfig);
        return binding.getRoot();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_action_save:
                saveConfig();
                return true;
            default:
                return false;
        }
    }

    private void saveConfig() {
        // FIXME: validate input
        VpnService.getInstance().update(getCurrentConfig().getName(), localConfig);
        // Hide the keyboard; it rarely goes away on its own.
        final BaseConfigActivity activity = (BaseConfigActivity) getActivity();
        final View focusedView = activity.getCurrentFocus();
        if (focusedView != null) {
            final InputMethodManager inputManager =
                    (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(focusedView.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
        // Tell the activity to go back to the detail view.
        activity.setCurrentConfig(localConfig);
    }
}
