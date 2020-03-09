/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.backend.WgQuickBackend;
import com.wireguard.util.NonNullForAll;

import androidx.preference.Preference;

@NonNullForAll
public class KernelModuleDisablerPreference extends Preference {
    private State state;

    public KernelModuleDisablerPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        state = Application.getBackend() instanceof WgQuickBackend ? State.ENABLED : State.DISABLED;
    }

    @Override
    public CharSequence getSummary() {
        return getContext().getString(state.summaryResourceId);
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(state.titleResourceId);
    }

    @Override
    protected void onClick() {
        if (state == State.DISABLED) {
            setState(State.ENABLING);
            Application.getSharedPreferences().edit().putBoolean("disable_kernel_module", false).apply();
        } else if (state == State.ENABLED) {
            setState(State.DISABLING);
            Application.getSharedPreferences().edit().putBoolean("disable_kernel_module", true).apply();
        }
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
        ENABLED(R.string.module_disabler_enabled_title, R.string.module_disabler_enabled_summary, true),
        DISABLED(R.string.module_disabler_disabled_title, R.string.module_disabler_disabled_summary, true),
        ENABLING(R.string.module_disabler_disabled_title, R.string.module_disabler_working, false),
        DISABLING(R.string.module_disabler_enabled_title, R.string.module_disabler_working, false);

        private final boolean shouldEnableView;
        private final int summaryResourceId;
        private final int titleResourceId;

        State(final int titleResourceId, final int summaryResourceId, final boolean shouldEnableView) {
            this.summaryResourceId = summaryResourceId;
            this.titleResourceId = titleResourceId;
            this.shouldEnableView = shouldEnableView;
        }
    }
}
