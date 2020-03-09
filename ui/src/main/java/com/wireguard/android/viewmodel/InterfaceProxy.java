/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.viewmodel;

import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import android.os.Parcel;
import android.os.Parcelable;

import com.wireguard.android.BR;
import com.wireguard.config.Attribute;
import com.wireguard.config.BadConfigException;
import com.wireguard.config.Interface;
import com.wireguard.crypto.Key;
import com.wireguard.crypto.KeyFormatException;
import com.wireguard.crypto.KeyPair;

import java.net.InetAddress;
import java.util.List;

import java9.util.stream.Collectors;
import java9.util.stream.StreamSupport;

public class InterfaceProxy extends BaseObservable implements Parcelable {
    public static final Parcelable.Creator<InterfaceProxy> CREATOR = new InterfaceProxyCreator();

    private final ObservableList<String> excludedApplications = new ObservableArrayList<>();
    private String addresses;
    private String dnsServers;
    private String listenPort;
    private String mtu;
    private String privateKey;
    private String publicKey;

    private InterfaceProxy(final Parcel in) {
        addresses = in.readString();
        dnsServers = in.readString();
        in.readStringList(excludedApplications);
        listenPort = in.readString();
        mtu = in.readString();
        privateKey = in.readString();
        publicKey = in.readString();
    }

    public InterfaceProxy(final Interface other) {
        addresses = Attribute.join(other.getAddresses());
        final List<String> dnsServerStrings = StreamSupport.stream(other.getDnsServers())
                .map(InetAddress::getHostAddress)
                .collect(Collectors.toUnmodifiableList());
        dnsServers = Attribute.join(dnsServerStrings);
        excludedApplications.addAll(other.getExcludedApplications());
        listenPort = other.getListenPort().map(String::valueOf).orElse("");
        mtu = other.getMtu().map(String::valueOf).orElse("");
        final KeyPair keyPair = other.getKeyPair();
        privateKey = keyPair.getPrivateKey().toBase64();
        publicKey = keyPair.getPublicKey().toBase64();
    }

    public InterfaceProxy() {
        addresses = "";
        dnsServers = "";
        listenPort = "";
        mtu = "";
        privateKey = "";
        publicKey = "";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void generateKeyPair() {
        final KeyPair keyPair = new KeyPair();
        privateKey = keyPair.getPrivateKey().toBase64();
        publicKey = keyPair.getPublicKey().toBase64();
        notifyPropertyChanged(BR.privateKey);
        notifyPropertyChanged(BR.publicKey);
    }

    @Bindable
    public String getAddresses() {
        return addresses;
    }

    @Bindable
    public String getDnsServers() {
        return dnsServers;
    }

    public ObservableList<String> getExcludedApplications() {
        return excludedApplications;
    }

    @Bindable
    public String getListenPort() {
        return listenPort;
    }

    @Bindable
    public String getMtu() {
        return mtu;
    }

    @Bindable
    public String getPrivateKey() {
        return privateKey;
    }

    @Bindable
    public String getPublicKey() {
        return publicKey;
    }

    public Interface resolve() throws BadConfigException {
        final Interface.Builder builder = new Interface.Builder();
        if (!addresses.isEmpty())
            builder.parseAddresses(addresses);
        if (!dnsServers.isEmpty())
            builder.parseDnsServers(dnsServers);
        if (!excludedApplications.isEmpty())
            builder.excludeApplications(excludedApplications);
        if (!listenPort.isEmpty())
            builder.parseListenPort(listenPort);
        if (!mtu.isEmpty())
            builder.parseMtu(mtu);
        if (!privateKey.isEmpty())
            builder.parsePrivateKey(privateKey);
        return builder.build();
    }

    public void setAddresses(final String addresses) {
        this.addresses = addresses;
        notifyPropertyChanged(BR.addresses);
    }

    public void setDnsServers(final String dnsServers) {
        this.dnsServers = dnsServers;
        notifyPropertyChanged(BR.dnsServers);
    }

    public void setListenPort(final String listenPort) {
        this.listenPort = listenPort;
        notifyPropertyChanged(BR.listenPort);
    }

    public void setMtu(final String mtu) {
        this.mtu = mtu;
        notifyPropertyChanged(BR.mtu);
    }

    public void setPrivateKey(final String privateKey) {
        this.privateKey = privateKey;
        try {
            publicKey = new KeyPair(Key.fromBase64(privateKey)).getPublicKey().toBase64();
        } catch (final KeyFormatException ignored) {
            publicKey = "";
        }
        notifyPropertyChanged(BR.privateKey);
        notifyPropertyChanged(BR.publicKey);
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeString(addresses);
        dest.writeString(dnsServers);
        dest.writeStringList(excludedApplications);
        dest.writeString(listenPort);
        dest.writeString(mtu);
        dest.writeString(privateKey);
        dest.writeString(publicKey);
    }

    private static class InterfaceProxyCreator implements Parcelable.Creator<InterfaceProxy> {
        @Override
        public InterfaceProxy createFromParcel(final Parcel in) {
            return new InterfaceProxy(in);
        }

        @Override
        public InterfaceProxy[] newArray(final int size) {
            return new InterfaceProxy[size];
        }
    }
}
