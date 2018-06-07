/*
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android.preference;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.preference.Preference;
import android.util.AttributeSet;

import com.wireguard.android.Application;
import com.wireguard.android.BuildConfig;
import com.wireguard.android.R;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.WgQuickBackend;
import com.wireguard.android.util.RootShell;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import java9.util.concurrent.CompletionStage;

public class VersionPreference extends Preference {
    private String versionSummary;

    public VersionPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        if (Application.getComponent().getBackendType() == GoBackend.class) {
            versionSummary = getContext().getString(R.string.version_userspace_summary, GoBackend.getVersion());
        } else if (Application.getComponent().getBackendType() == WgQuickBackend.class) {
            versionSummary = getContext().getString(R.string.version_kernel_summary_checking);
            Application.getComponent().getAsyncWorker().supplyAsync(() -> {
                final List<String> output = new ArrayList<>();
                if (Application.getComponent().getRootShell()
                        .run(output, "cat /sys/module/wireguard/version") != 0 || output.isEmpty())
                    throw new RuntimeException("Unable to determine kernel module version");
                return output.get(0);
            }).whenComplete((version, exception) -> {
                versionSummary = exception == null
                        ? getContext().getString(R.string.version_kernel_summary, version)
                        : getContext().getString(R.string.version_kernel_summary_unknown);
                notifyChanged();
            });
        }
    }

    @Override
    public CharSequence getSummary() {
        return versionSummary;
    }

    @Override
    public CharSequence getTitle() {
        return getContext().getString(R.string.version_title, BuildConfig.VERSION_NAME);
    }

    @Override
    protected void onClick() {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://www.wireguard.com/"));
        getContext().startActivity(intent);
    }

}
