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

    public ApplicationData(@NonNull Drawable icon, @NonNull String name, @NonNull String packageName, boolean excludedFromTunnel) {
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

    public void setExcludedFromTunnel(boolean excludedFromTunnel) {
        this.excludedFromTunnel = excludedFromTunnel;
        notifyPropertyChanged(BR.excludedFromTunnel);
    }

    @Override
    public String getKey() {
        return name;
    }
}
