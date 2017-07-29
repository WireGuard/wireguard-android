package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.Observable;

import com.wireguard.android.BR;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Represents a wg-quick profile.
 */

public class Profile extends BaseObservable implements Observable {
    private String config;
    private final String name;

    public Profile(String name) {
        this.name = name;
    }

    public void fromStream(InputStream stream)
            throws IOException {
        final StringBuilder sb = new StringBuilder(stream.available());
        String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            while ((line = reader.readLine()) != null)
                sb.append(line).append('\n');
        }
        setConfig(sb.toString());
    }

    @Bindable
    public String getConfig() {
        return config;
    }

    @Bindable
    public String getName() {
        return name;
    }

    public void setConfig(String config) {
        this.config = config;
        notifyPropertyChanged(BR.config);
    }

    @Override
    public String toString() {
        return getConfig();
    }
}
