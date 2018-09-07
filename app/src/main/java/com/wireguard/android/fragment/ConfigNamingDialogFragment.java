/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.databinding.ConfigNamingDialogFragmentBinding;
import com.wireguard.config.Config;

import java.io.IOException;
import java.util.Objects;

public class ConfigNamingDialogFragment extends DialogFragment {

    private static final String KEY_CONFIG_TEXT = "config_text";

    @Nullable private Config config;
    @Nullable private ConfigNamingDialogFragmentBinding binding;
    @Nullable private InputMethodManager imm;

    public static ConfigNamingDialogFragment newInstance(final String configText) {
        final Bundle extras = new Bundle();
        extras.putString(KEY_CONFIG_TEXT, configText);
        final ConfigNamingDialogFragment fragment = new ConfigNamingDialogFragment();
        fragment.setArguments(extras);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            config = Config.from(getArguments().getString(KEY_CONFIG_TEXT));
        } catch (final IOException exception) {
            throw new RuntimeException("Invalid config passed to " + getClass().getSimpleName(), exception);
        }
    }

    @Override public void onResume() {
        super.onResume();

        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> createTunnelAndDismiss());

            setKeyboardVisible(true);
        }
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Activity activity = getActivity();

        imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);

        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
        alertDialogBuilder.setTitle(R.string.import_from_qrcode);

        binding = ConfigNamingDialogFragmentBinding.inflate(getActivity().getLayoutInflater(), null, false);
        binding.executePendingBindings();
        alertDialogBuilder.setView(binding.getRoot());

        alertDialogBuilder.setPositiveButton(R.string.create_tunnel, null);
        alertDialogBuilder.setNegativeButton(R.string.cancel, (dialog, which) -> dismiss());

        return alertDialogBuilder.create();
    }

    @Override
    public void dismiss() {
        setKeyboardVisible(false);
        super.dismiss();
    }

    private void createTunnelAndDismiss() {
        if (binding != null) {
            final String name = binding.tunnelNameText.getText().toString();

            Application.getTunnelManager().create(name, config).whenComplete((tunnel, throwable) -> {
                if (tunnel != null) {
                    dismiss();
                } else {
                    binding.tunnelNameTextLayout.setError(throwable.getMessage());
                }
            });
        }
    }

    private void setKeyboardVisible(final boolean visible) {
        Objects.requireNonNull(imm);

        if (visible) {
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        } else if (binding != null) {
            imm.hideSoftInputFromWindow(binding.tunnelNameText.getWindowToken(), 0);
        }
    }

}
