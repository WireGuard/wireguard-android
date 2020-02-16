/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import android.util.AttributeSet;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.util.ToolsInstaller;

/**
 * Preference implementing a button that asynchronously runs {@code ToolsInstaller} and displays the
 * result as the preference summary.
 */

public class ToolsInstallerPreference extends Preference {
    private State state = State.INITIAL;

    public ToolsInstallerPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getSummary() {
        return getContext().getString(state.messageResourceId);
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(R.string.tools_installer_title);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        Application.getAsyncWorker().supplyAsync(Application.getToolsInstaller()::areInstalled).whenComplete(this::onCheckResult);
    }

    private void onCheckResult(final int state, @Nullable final Throwable throwable) {
        if (throwable != null || state == ToolsInstaller.ERROR)
            setState(State.INITIAL);
        else if ((state & ToolsInstaller.YES) == ToolsInstaller.YES)
            setState(State.ALREADY);
        else if ((state & (ToolsInstaller.MAGISK | ToolsInstaller.NO)) == (ToolsInstaller.MAGISK | ToolsInstaller.NO))
            setState(State.INITIAL_MAGISK);
        else if ((state & (ToolsInstaller.SYSTEM | ToolsInstaller.NO)) == (ToolsInstaller.SYSTEM | ToolsInstaller.NO))
            setState(State.INITIAL_SYSTEM);
        else
            setState(State.INITIAL);
    }

    @Override
    protected void onClick() {
        setState(State.WORKING);
        Application.getAsyncWorker().supplyAsync(Application.getToolsInstaller()::install).whenComplete(this::onInstallResult);
    }

    private void onInstallResult(final Integer result, @Nullable final Throwable throwable) {
        if (throwable != null)
            setState(State.FAILURE);
        else if ((result & (ToolsInstaller.YES | ToolsInstaller.MAGISK)) == (ToolsInstaller.YES | ToolsInstaller.MAGISK))
            setState(State.SUCCESS_MAGISK);
        else if ((result & (ToolsInstaller.YES | ToolsInstaller.SYSTEM)) == (ToolsInstaller.YES | ToolsInstaller.SYSTEM))
            setState(State.SUCCESS_SYSTEM);
        else
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
        INITIAL(R.string.tools_installer_initial, true),
        ALREADY(R.string.tools_installer_already, false),
        FAILURE(R.string.tools_installer_failure, true),
        WORKING(R.string.tools_installer_working, false),
        INITIAL_SYSTEM(R.string.tools_installer_initial_system, true),
        SUCCESS_SYSTEM(R.string.tools_installer_success_system, false),
        INITIAL_MAGISK(R.string.tools_installer_initial_magisk, true),
        SUCCESS_MAGISK(R.string.tools_installer_success_magisk, false);

        private final int messageResourceId;
        private final boolean shouldEnableView;

        State(final int messageResourceId, final boolean shouldEnableView) {
            this.messageResourceId = messageResourceId;
            this.shouldEnableView = shouldEnableView;
        }
    }
}
