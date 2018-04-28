package com.wireguard.config;

import com.android.databinding.library.baseAdapters.BR;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a wg-quick configuration file, its name, and its connection state.
 */

public class Config implements Parcelable {
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

    public static class Observable extends BaseObservable {
        private String name;
        private Interface.Observable observableInterface;
        private ObservableList<Peer.Observable> observablePeers;


        public Observable(Config parent, String name) {
            this.name = name;
            loadData(parent);
        }

        public void loadData(Config parent) {
            this.observableInterface = new Interface.Observable(parent.interfaceSection);
            this.observablePeers = new ObservableArrayList<>();
            for (Peer peer : parent.getPeers())
                this.observablePeers.add(new Peer.Observable(peer));
        }

        public void commitData(Config parent) {
            this.observableInterface.commitData(parent.interfaceSection);
            List<Peer> newPeers = new ArrayList<>(this.observablePeers.size());
            for (Peer.Observable observablePeer : this.observablePeers) {
                Peer peer = new Peer();
                observablePeer.commitData(peer);
                newPeers.add(peer);
            }
            parent.peers = newPeers;
            notifyChange();
        }

        @Bindable
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
            notifyPropertyChanged(BR.name);
        }

        @Bindable
        public Interface.Observable getInterfaceSection() {
            return observableInterface;
        }

        @Bindable
        public ObservableList<Peer.Observable> getPeers() {
            return observablePeers;
        }
    }

    private final Interface interfaceSection;
    private List<Peer> peers = new ArrayList<>();

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

    public List<Peer> getPeers() {
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
