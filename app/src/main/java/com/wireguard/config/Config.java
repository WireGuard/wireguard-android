/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import android.content.Context;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.android.databinding.library.baseAdapters.BR;
import com.wireguard.android.Application;
import com.wireguard.android.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a wg-quick configuration file, its name, and its connection state.
 */

public class Config {
    private final Interface interfaceSection = new Interface();
    private List<Peer> peers = new ArrayList<>();

    public static Config from(final String string) throws IOException {
        return from(new BufferedReader(new StringReader(string)));
    }

    public static Config from(final InputStream stream) throws IOException {
        return from(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
    }

    public static Config from(final BufferedReader reader) throws IOException {
        final Config config = new Config();
        final Context context = Application.get();
        Peer currentPeer = null;
        String line;
        boolean inInterfaceSection = false;
        while ((line = reader.readLine()) != null) {
            final int commentIndex = line.indexOf('#');
            if (commentIndex != -1)
                line = line.substring(0, commentIndex);
            line = line.trim();
            if (line.isEmpty())
                continue;
            if ("[Interface]".toLowerCase().equals(line.toLowerCase())) {
                currentPeer = null;
                inInterfaceSection = true;
            } else if ("[Peer]".toLowerCase().equals(line.toLowerCase())) {
                currentPeer = new Peer();
                config.peers.add(currentPeer);
                inInterfaceSection = false;
            } else if (inInterfaceSection) {
                config.interfaceSection.parse(line);
            } else if (currentPeer != null) {
                currentPeer.parse(line);
            } else {
                throw new IllegalArgumentException(context.getString(R.string.tunnel_error_invalid_config_line, line));
            }
        }
        if (!inInterfaceSection && currentPeer == null) {
            throw new IllegalArgumentException(context.getString(R.string.tunnel_error_no_config_information));
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
        @Nullable private String name;
        private final Interface.Observable observableInterface;
        private final ObservableList<Peer.Observable> observablePeers;

        public Observable(@Nullable final Config parent, @Nullable final String name) {
            this.name = name;

            observableInterface = new Interface.Observable(parent == null ? null : parent.interfaceSection);
            observablePeers = new ObservableArrayList<>();
            if (parent != null) {
                for (final Peer peer : parent.getPeers())
                    observablePeers.add(new Peer.Observable(peer));
            }
        }

        private Observable(final Parcel in) {
            name = in.readString();
            observableInterface = in.readParcelable(Interface.Observable.class.getClassLoader());
            observablePeers = new ObservableArrayList<>();
            in.readTypedList(observablePeers, Peer.Observable.CREATOR);
        }

        public void commitData(final Config parent) {
            observableInterface.commitData(parent.interfaceSection);
            final List<Peer> newPeers = new ArrayList<>(observablePeers.size());
            for (final Peer.Observable observablePeer : observablePeers) {
                final Peer peer = new Peer();
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

        public void setName(final String name) {
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
