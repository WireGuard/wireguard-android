/*
 * Copyright © 2018 Eric Kuck <eric@bluelinelabs.com>.
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier:
 */

package com.wireguard.android.model;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

import com.wireguard.android.BR;
import com.wireguard.util.Keyed;

public class ApplicationData extends BaseObservable implements Keyed<String> {

    @NonNull private final Drawable icon;
    @NonNull private final String name;
    @NonNull private final String packageName;
    private boolean excludedFromTunnel;

    public ApplicationData(@NonNull final Drawable icon, @NonNull final String name, @NonNull final String packageName, final boolean excludedFromTunnel) {
        this.icon = icon;
        this.name = name;
        this.packageName = packageName;
        this.excludedFromTunnel = excludedFromTunnel;
    }

    @NonNull
    public Drawable getIcon() {
        return icon;
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    @Bindable
    public boolean isExcludedFromTunnel() {
        return excludedFromTunnel;
    }

    public void setExcludedFromTunnel(final boolean excludedFromTunnel) {
        this.excludedFromTunnel = excludedFromTunnel;
        notifyPropertyChanged(BR.excludedFromTunnel);
    }

    @Override
    public String getKey() {
        return name;
    }
}
