/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android.preference;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.preference.Preference;
import android.system.OsConstants;
import android.util.AttributeSet;

import com.wireguard.android.Application;
import com.wireguard.android.R;

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

    private void onCheckResult(final Integer result, final Throwable throwable) {
        setState(throwable == null && result == OsConstants.EALREADY ?
                State.ALREADY : initialState());
    }

    @Override
    protected void onClick() {
        setState(workingState());
        Application.getAsyncWorker().supplyAsync(Application.getToolsInstaller()::install).whenComplete(this::onInstallResult);
    }

    private void onInstallResult(final Integer result, final Throwable throwable) {
        final State nextState;
        if (throwable != null)
            nextState = State.FAILURE;
        else if (result == OsConstants.EXIT_SUCCESS)
            nextState = successState();
        else if (result == OsConstants.EALREADY)
            nextState = State.ALREADY;
        else
            nextState = State.FAILURE;
        setState(nextState);
    }

    private void setState(@NonNull final State state) {
        if (this.state == state)
            return;
        this.state = state;
        if (isEnabled() != state.shouldEnableView)
            setEnabled(state.shouldEnableView);
        notifyChanged();
    }

    private State initialState() {
        return Application.getToolsInstaller().willInstallAsMagiskModule(false) ? State.INITIAL_MAGISK : State.INITIAL_SYSTEM;
    }
    private State workingState() {
        return Application.getToolsInstaller().willInstallAsMagiskModule(false) ? State.WORKING_MAGISK : State.WORKING_SYSTEM;
    }
    private State successState() {
        return Application.getToolsInstaller().willInstallAsMagiskModule(false) ? State.SUCCESS_MAGISK : State.SUCCESS_SYSTEM;
    }

    private enum State {
        INITIAL(R.string.tools_installer_initial, true),
        ALREADY(R.string.tools_installer_already, false),
        FAILURE(R.string.tools_installer_failure, true),
        INITIAL_SYSTEM(R.string.tools_installer_initial_system, true),
        SUCCESS_SYSTEM(R.string.tools_installer_success_system, false),
        WORKING_SYSTEM(R.string.tools_installer_working_system, false),
        INITIAL_MAGISK(R.string.tools_installer_initial_magisk, true),
        SUCCESS_MAGISK(R.string.tools_installer_success_magisk, false),
        WORKING_MAGISK(R.string.tools_installer_working_magisk, false);

        private final int messageResourceId;
        private final boolean shouldEnableView;

        State(final int messageResourceId, final boolean shouldEnableView) {
            this.messageResourceId = messageResourceId;
            this.shouldEnableView = shouldEnableView;
        }
    }
}
