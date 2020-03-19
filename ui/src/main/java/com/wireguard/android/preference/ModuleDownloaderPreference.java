/*
 * Copyright © 2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.system.OsConstants;
import android.util.AttributeSet;
import android.widget.Toast;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.activity.SettingsActivity;
import com.wireguard.android.util.ErrorMessages;
import com.wireguard.util.NonNullForAll;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

@NonNullForAll
public class ModuleDownloaderPreference extends Preference {
    private State state = State.INITIAL;

    public ModuleDownloaderPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getSummary() {
        return getContext().getString(state.messageResourceId);
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(R.string.module_installer_title);
    }

    @Override
    protected void onClick() {
        setState(State.WORKING);
        Application.getAsyncWorker().supplyAsync(Application.getModuleLoader()::download).whenComplete(this::onDownloadResult);
    }

    @SuppressLint("ApplySharedPref")
    private void onDownloadResult(final Integer result, @Nullable final Throwable throwable) {
        if (throwable != null) {
            setState(State.FAILURE);
            Toast.makeText(getContext(), ErrorMessages.get(throwable), Toast.LENGTH_LONG).show();
        } else if (result == OsConstants.ENOENT)
            setState(State.NOTFOUND);
        else if (result == OsConstants.EXIT_SUCCESS) {
            setState(State.SUCCESS);
            Application.getSharedPreferences().edit().remove("disable_kernel_module").commit();
            Application.getAsyncWorker().runAsync(() -> {
                final Intent restartIntent = new Intent(getContext(), SettingsActivity.class);
                restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Application.get().startActivity(restartIntent);
                System.exit(0);
            });
        } else
            setState(State.FAILURE);
    }

    private void setState(final State state) {
        if (this.state == state)
            return;
        this.state = state;
        if (isEnabled() != state.shouldEnableView)
            setEnabled(state.shouldEnableView);
        notifyChanged();
    }

    private enum State {
        INITIAL(R.string.module_installer_initial, true),
        FAILURE(R.string.module_installer_error, true),
        WORKING(R.string.module_installer_working, false),
        SUCCESS(R.string.success_application_will_restart, false),
        NOTFOUND(R.string.module_installer_not_found, false);

        private final int messageResourceId;
        private final boolean shouldEnableView;

        State(final int messageResourceId, final boolean shouldEnableView) {
            this.messageResourceId = messageResourceId;
            this.shouldEnableView = shouldEnableView;
        }
    }
}
