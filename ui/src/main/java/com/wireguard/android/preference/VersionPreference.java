/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import android.util.AttributeSet;

import com.wireguard.android.Application;
import com.wireguard.android.BuildConfig;
import com.wireguard.android.R;

import java.util.Locale;

public class VersionPreference extends Preference {
    @Nullable private String versionSummary;

    public VersionPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        Application.getBackendAsync().thenAccept(backend -> {
            versionSummary = getContext().getString(R.string.version_summary_checking, backend.getTypePrettyName().toLowerCase(Locale.ENGLISH));
            Application.getAsyncWorker().supplyAsync(backend::getVersion).whenComplete((version, exception) -> {
                versionSummary = exception == null
                        ? getContext().getString(R.string.version_summary, backend.getTypePrettyName(), version)
                        : getContext().getString(R.string.version_summary_unknown, backend.getTypePrettyName().toLowerCase(Locale.ENGLISH));
                notifyChanged();
            });
        });
    }

    @Nullable
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
        try {
            getContext().startActivity(intent);
        } catch (final ActivityNotFoundException ignored) {
        }
    }

}
