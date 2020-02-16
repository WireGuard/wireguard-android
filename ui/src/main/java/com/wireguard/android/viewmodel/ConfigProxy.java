/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.viewmodel;

import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import android.os.Parcel;
import android.os.Parcelable;

import com.wireguard.config.BadConfigException;
import com.wireguard.config.Config;
import com.wireguard.config.Peer;

import java.util.ArrayList;
import java.util.Collection;

public class ConfigProxy implements Parcelable {
    public static final Parcelable.Creator<ConfigProxy> CREATOR = new ConfigProxyCreator();

    private final InterfaceProxy interfaze;
    private final ObservableList<PeerProxy> peers = new ObservableArrayList<>();

    private ConfigProxy(final Parcel in) {
        interfaze = in.readParcelable(InterfaceProxy.class.getClassLoader());
        in.readTypedList(peers, PeerProxy.CREATOR);
        for (final PeerProxy proxy : peers)
            proxy.bind(this);
    }

    public ConfigProxy(final Config other) {
        interfaze = new InterfaceProxy(other.getInterface());
        for (final Peer peer : other.getPeers()) {
            final PeerProxy proxy = new PeerProxy(peer);
            peers.add(proxy);
            proxy.bind(this);
        }
    }

    public ConfigProxy() {
        interfaze = new InterfaceProxy();
    }

    public PeerProxy addPeer() {
        final PeerProxy proxy = new PeerProxy();
        peers.add(proxy);
        proxy.bind(this);
        return proxy;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public InterfaceProxy getInterface() {
        return interfaze;
    }

    public ObservableList<PeerProxy> getPeers() {
        return peers;
    }

    public Config resolve() throws BadConfigException {
        final Collection<Peer> resolvedPeers = new ArrayList<>();
        for (final PeerProxy proxy : peers)
            resolvedPeers.add(proxy.resolve());
        return new Config.Builder()
                .setInterface(interfaze.resolve())
                .addPeers(resolvedPeers)
                .build();
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeParcelable(interfaze, flags);
        dest.writeTypedList(peers);
    }

    private static class ConfigProxyCreator implements Parcelable.Creator<ConfigProxy> {
        @Override
        public ConfigProxy createFromParcel(final Parcel in) {
            return new ConfigProxy(in);
        }

        @Override
        public ConfigProxy[] newArray(final int size) {
            return new ConfigProxy[size];
        }
    }
}
