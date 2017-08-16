package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.Observable;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;

import com.wireguard.android.BR;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Represents a wg-quick configuration file, its name, and its connection state.
 */

public class Config extends BaseObservable implements Copyable<Config>, Observable {
    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9_=+.-]{1,16}$");

    private static boolean isNameValid(final String name) {
        return PATTERN.matcher(name).matches();
    }

    private final Interface iface = new Interface();
    private boolean isEnabled;
    private String name;
    private final ObservableList<Peer> peers = new ObservableArrayList<>();

    @Override
    public Config copy() {
        final Config copy = new Config();
        copy.copyFrom(this);
        return copy;
    }

    @Override
    public void copyFrom(final Config source) {
        iface.copyFrom(source.iface);
        isEnabled = source.isEnabled;
        name = source.name;
        peers.clear();
        for (final Peer peer : source.peers)
            peers.add(peer.copy());
    }

    public Interface getInterface() {
        return iface;
    }

    @Bindable
    public String getName() {
        return name;
    }

    public ObservableList<Peer> getPeers() {
        return peers;
    }

    @Bindable
    public boolean isEnabled() {
        return isEnabled;
    }

    public void parseFrom(final InputStream stream)
            throws IOException {
        peers.clear();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            Peer currentPeer = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty())
                    continue;
                if ("[Interface]".equals(line)) {
                    currentPeer = null;
                } else if ("[Peer]".equals(line)) {
                    currentPeer = new Peer();
                    peers.add(currentPeer);
                } else if (currentPeer == null) {
                    iface.parseFrom(line);
                } else {
                    currentPeer.parseFrom(line);
                }
            }
        }
    }

    public void setIsEnabled(final boolean isEnabled) {
        this.isEnabled = isEnabled;
        notifyPropertyChanged(BR.enabled);
    }

    public void setName(final String name) {
        if (name != null && !name.isEmpty() && !isNameValid(name))
            throw new IllegalArgumentException();
        this.name = name;
        notifyPropertyChanged(BR.name);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder().append(iface);
        for (final Peer peer : peers)
            sb.append('\n').append(peer);
        return sb.toString();
    }
}
