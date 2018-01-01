package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Represents a wg-quick configuration file, its name, and its connection state.
 */

public class Config extends BaseObservable implements Parcelable {
    public static final Creator<Config> CREATOR = new Creator<Config>() {
        @Override
        public Config createFromParcel(final Parcel in) {
            return new Config(in);
        }

        @Override
        public Config[] newArray(final int size) {
            return new Config[size];
        }
    };

    private final Interface interfaceSection;
    private final ObservableList<Peer> peers = new ObservableArrayList<>();

    public Config() {
        interfaceSection = new Interface();
    }

    private Config(final Parcel in) {
        interfaceSection = in.readParcelable(Interface.class.getClassLoader());
        in.readTypedList(peers, Peer.CREATOR);
    }

    public static Config from(final InputStream stream)
            throws IOException {
        final Config config = new Config();
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
                    currentPeer = new Peer();
                    config.peers.add(currentPeer);
                    inInterfaceSection = false;
                } else if (inInterfaceSection) {
                    config.interfaceSection.parse(line);
                } else if (currentPeer != null) {
                    currentPeer.parse(line);
                } else {
                    throw new IllegalArgumentException("Invalid configuration line: " + line);
                }
            }
            if (!inInterfaceSection && currentPeer == null) {
                throw new IllegalArgumentException("Could not find any config information");
            }
        }
        return config;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public Interface getInterface() {
        return interfaceSection;
    }

    public ObservableList<Peer> getPeers() {
        return peers;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder().append(interfaceSection);
        for (final Peer peer : peers)
            sb.append('\n').append(peer);
        return sb.toString();
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeParcelable(interfaceSection, flags);
        dest.writeTypedList(peers);
    }
}
