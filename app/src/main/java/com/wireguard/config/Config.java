package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.Observable;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;
import android.support.annotation.NonNull;

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

public class Config extends BaseObservable
        implements Comparable<Config>, Copyable<Config>, Observable {
    public static final int NAME_MAX_LENGTH = 16;
    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9_=+.-]{1,16}$");

    private static boolean isNameValid(final String name) {
        return name.length() <= NAME_MAX_LENGTH && PATTERN.matcher(name).matches();
    }

    private final Interface iface = new Interface();
    private boolean isEnabled;
    private boolean isPrimary;
    private String name;
    private final ObservableList<Peer> peers = new ObservableArrayList<>();

    public Peer addPeer() {
        final Peer peer = new Peer(this);
        peers.add(peer);
        return peer;
    }

    private Peer addPeer(final Peer peer) {
        final Peer copy = peer.copy(this);
        peers.add(copy);
        return copy;
    }

    @Override
    public int compareTo(@NonNull final Config config) {
        return getName().compareTo(config.getName());
    }

    @Override
    public Config copy() {
        final Config copy = new Config();
        copy.copyFrom(this);
        return copy;
    }

    @Override
    public void copyFrom(final Config source) {
        if (source != null) {
            iface.copyFrom(source.iface);
            isEnabled = source.isEnabled;
            isPrimary = source.isPrimary;
            name = source.name;
            peers.clear();
            for (final Peer peer : source.peers)
                addPeer(peer);
        } else {
            iface.copyFrom(null);
            isEnabled = false;
            isPrimary = false;
            name = null;
            peers.clear();
        }
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

    @Bindable
    public boolean isPrimary() {
        return isPrimary;
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
                    currentPeer = addPeer();
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

    public void setIsPrimary(final boolean isPrimary) {
        this.isPrimary = isPrimary;
        notifyPropertyChanged(BR.primary);
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

    public String validate() {
        if (name == null || !isNameValid(name))
            return "This configuration does not have a valid name.";
        if (iface.getPublicKey() == null)
            return "This configuration does not have a valid keypair.";
        return null;
    }
}
