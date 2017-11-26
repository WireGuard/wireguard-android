package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.Observable;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import com.wireguard.android.BR;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Represents a wg-quick configuration file, its name, and its connection state.
 */

public class Config extends BaseObservable
        implements Comparable<Config>, Copyable<Config>, Observable, Parcelable {
    public static final Parcelable.Creator<Config> CREATOR = new Parcelable.Creator<Config>() {
        @Override
        public Config createFromParcel(final Parcel in) {
            return new Config(in);
        }

        @Override
        public Config[] newArray(final int size) {
            return new Config[size];
        }
    };
    public static final int NAME_MAX_LENGTH = 16;
    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9_=+.-]{1,16}$");

    public static boolean isNameValid(final String name) {
        return name.length() <= NAME_MAX_LENGTH && PATTERN.matcher(name).matches();
    }

    private final Interface iface;
    private boolean isEnabled;
    private boolean isPrimary;
    private String name;
    private final ObservableList<Peer> peers = new ObservableArrayList<>();

    public Config() {
        iface = new Interface();
    }

    protected Config(final Parcel in) {
        iface = in.readParcelable(Interface.class.getClassLoader());
        name = in.readString();
        // The flattened peers must be recreated to associate them with this config.
        final List<Peer> flattenedPeers = new LinkedList<>();
        in.readTypedList(flattenedPeers, Peer.CREATOR);
        for (final Peer peer : flattenedPeers)
            addPeer(peer);
    }

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
            name = source.name;
            peers.clear();
            for (final Peer peer : source.peers)
                addPeer(peer);
        } else {
            iface.copyFrom(null);
            name = null;
            peers.clear();
        }
        notifyChange();
    }

    @Override
    public int describeContents() {
        return 0;
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
            boolean inInterfaceSection = false;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                if ("[Interface]".equals(line)) {
                    currentPeer = null;
                    inInterfaceSection = true;
                } else if ("[Peer]".equals(line)) {
                    currentPeer = addPeer();
                    inInterfaceSection = false;
                } else if (inInterfaceSection) {
                    iface.parse(line);
                } else if (currentPeer != null) {
                    currentPeer.parse(line);
                } else {
                    throw new IllegalArgumentException("Invalid configuration line: " + line);
                }
            }
            if (!inInterfaceSection && currentPeer == null) {
                throw new IllegalArgumentException("Did not find any config information");
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

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeParcelable(iface, flags);
        dest.writeString(name);
        dest.writeTypedList(peers);
    }
}
