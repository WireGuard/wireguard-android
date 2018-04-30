package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.databinding.library.baseAdapters.BR;

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

public class Config {
    private final Interface interfaceSection = new Interface();
    private List<Peer> peers = new ArrayList<>();

    public static Config from(final InputStream stream) throws IOException {
        return from(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    public static Config from(final BufferedReader reader) throws IOException {
        final Config config = new Config();
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
        return config;
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

    public static class Observable extends BaseObservable implements Parcelable {
        public static final Creator<Observable> CREATOR = new Creator<Observable>() {
            @Override
            public Observable createFromParcel(final Parcel in) {
                return new Observable(in);
            }

            @Override
            public Observable[] newArray(final int size) {
                return new Observable[size];
            }
        };
        private String name;
        private Interface.Observable observableInterface;
        private ObservableList<Peer.Observable> observablePeers;

        public Observable(Config parent, String name) {
            this.name = name;
            loadData(parent);
        }

        private Observable(final Parcel in) {
            name = in.readString();
            observableInterface = in.readParcelable(Interface.Observable.class.getClassLoader());
            observablePeers = new ObservableArrayList<>();
            in.readTypedList(observablePeers, Peer.Observable.CREATOR);
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

        @Override
        public int describeContents() {
            return 0;
        }

        @Bindable
        public Interface.Observable getInterfaceSection() {
            return observableInterface;
        }

        @Bindable
        public String getName() {
            return name == null ? "" : name;
        }

        @Bindable
        public ObservableList<Peer.Observable> getPeers() {
            return observablePeers;
        }

        public void loadData(Config parent) {
            this.observableInterface = new Interface.Observable(parent == null ? null : parent.interfaceSection);
            this.observablePeers = new ObservableArrayList<>();
            if (parent != null) {
                for (Peer peer : parent.getPeers())
                    this.observablePeers.add(new Peer.Observable(peer));
            }
        }

        public void setName(String name) {
            this.name = name;
            notifyPropertyChanged(BR.name);
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeString(name);
            dest.writeParcelable(observableInterface, flags);
            dest.writeTypedList(observablePeers);
        }
    }
}
