/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.inputmethod.InputMethodManager;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.databinding.ConfigNamingDialogFragmentBinding;
import com.wireguard.config.BadConfigException;
import com.wireguard.config.Config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ConfigNamingDialogFragment extends DialogFragment {
    private static final String KEY_CONFIG_TEXT = "config_text";

    @Nullable private ConfigNamingDialogFragmentBinding binding;
    @Nullable private Config config;
    @Nullable private InputMethodManager imm;

    public static ConfigNamingDialogFragment newInstance(final String configText) {
        final Bundle extras = new Bundle();
        extras.putString(KEY_CONFIG_TEXT, configText);
        final ConfigNamingDialogFragment fragment = new ConfigNamingDialogFragment();
        fragment.setArguments(extras);
        return fragment;
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

    @Override
    public void dismiss() {
        setKeyboardVisible(false);
        super.dismiss();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle arguments = getArguments();
        final String configText = arguments.getString(KEY_CONFIG_TEXT);
        final byte[] configBytes = configText.getBytes(StandardCharsets.UTF_8);
        try {
            config = Config.parse(new ByteArrayInputStream(configBytes));
        } catch (final BadConfigException | IOException e) {
            throw new IllegalArgumentException("Invalid config passed to " + getClass().getSimpleName(), e);
        }
    }

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Activity activity = Objects.requireNonNull(getActivity());

        imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);

        final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(activity);
        alertDialogBuilder.setTitle(R.string.import_from_qr_code);

        binding = ConfigNamingDialogFragmentBinding.inflate(activity.getLayoutInflater(), null, false);
        binding.executePendingBindings();
        alertDialogBuilder.setView(binding.getRoot());

        alertDialogBuilder.setPositiveButton(R.string.create_tunnel, null);
        alertDialogBuilder.setNegativeButton(R.string.cancel, (dialog, which) -> dismiss());

        return alertDialogBuilder.create();
    }

    @Override public void onResume() {
        super.onResume();

        final AlertDialog dialog = (AlertDialog) getDialog();
        if (dialog != null) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> createTunnelAndDismiss());

            setKeyboardVisible(true);
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
