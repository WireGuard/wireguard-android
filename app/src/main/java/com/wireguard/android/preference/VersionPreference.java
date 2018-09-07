/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;
import android.util.AttributeSet;

import com.wireguard.android.Application;
import com.wireguard.android.BuildConfig;
import com.wireguard.android.R;

public class VersionPreference extends Preference {
    @Nullable private String versionSummary;

    public VersionPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        Application.getBackendAsync().thenAccept(backend -> {
            versionSummary = getContext().getString(R.string.version_summary_checking, backend.getTypeName().toLowerCase());
            Application.getAsyncWorker().supplyAsync(backend::getVersion).whenComplete((version, exception) -> {
                versionSummary = exception == null
                        ? getContext().getString(R.string.version_summary, backend.getTypeName(), version)
                        : getContext().getString(R.string.version_summary_unknown, backend.getTypeName().toLowerCase());
                notifyChanged();
            });
        });
    }

    @Override @Nullable
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
        try {
            getContext().startActivity(intent);
        } catch (final ActivityNotFoundException ignored) { }
    }

}
