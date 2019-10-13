/*
 * Copyright © 2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference;

import android.content.Context;
import android.content.Intent;
import android.system.OsConstants;
import android.util.AttributeSet;
import android.widget.Toast;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.util.ModuleLoader;
import com.wireguard.android.util.ToolsInstaller;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

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

    private void onDownloadResult(final Integer result, @Nullable final Throwable throwable) {
        if (throwable != null) {
            setState(State.FAILURE);
            Toast.makeText(getContext(), throwable.getMessage(), Toast.LENGTH_LONG).show();
        } else if (result == OsConstants.ENOENT)
            setState(State.NOTFOUND);
        else if (result == OsConstants.EXIT_SUCCESS) {
            setState(State.SUCCESS);
            Application.getAsyncWorker().runAsync(() -> {
                Thread.sleep(1000 * 5);
                Intent i = getContext().getPackageManager().getLaunchIntentForPackage(getContext().getPackageName());
                if (i == null)
                    return;
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Application.get().startActivity(i);
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
        SUCCESS(R.string.module_installer_success, false),
        NOTFOUND(R.string.module_installer_not_found, false);

        private final int messageResourceId;
        private final boolean shouldEnableView;

        State(final int messageResourceId, final boolean shouldEnableView) {
            this.messageResourceId = messageResourceId;
            this.shouldEnableView = shouldEnableView;
        }
    }
}
