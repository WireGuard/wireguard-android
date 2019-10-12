/*
 * Copyright © 2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;

import com.wireguard.android.Application;
import com.wireguard.android.BuildConfig;
import com.wireguard.android.R;

import java.util.Locale;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

public class DonatePreference extends Preference {
    public DonatePreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public CharSequence getSummary() { return getContext().getString(R.string.donate_summary); }

    @Override
    public CharSequence getTitle() { return getContext().getString(R.string.donate_title); }

    @Override
    protected void onClick() {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("https://www.wireguard.com/donations/"));
        try {
            getContext().startActivity(intent);
        } catch (final ActivityNotFoundException ignored) {
        }
    }

}
