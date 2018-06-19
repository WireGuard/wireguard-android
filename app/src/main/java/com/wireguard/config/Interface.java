/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.os.Parcel;
import android.os.Parcelable;

import com.wireguard.android.BR;
import com.wireguard.crypto.Keypair;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the configuration for a WireGuard interface (an [Interface] block).
 */

public class Interface {
    private final List<InetNetwork> addressList;
    private final List<InetAddress> dnsList;
    private Keypair keypair;
    private int listenPort;
    private int mtu;

    public Interface() {
        addressList = new ArrayList<>();
        dnsList = new ArrayList<>();
    }

    private void addAddresses(final String[] addresses) {
        if (addresses != null && addresses.length > 0) {
            for (final String addr : addresses) {
                if (addr.isEmpty())
                    throw new IllegalArgumentException("Address is empty");
                addressList.add(new InetNetwork(addr));
            }
        }
    }

    private void addDnses(final String[] dnses) {
        if (dnses != null && dnses.length > 0) {
            for (final String dns : dnses) {
                dnsList.add(InetAddresses.parse(dns));
            }
        }
    }

    private String getAddressString() {
        if (addressList.isEmpty())
            return null;
        return Attribute.iterableToString(addressList);
    }

    public InetNetwork[] getAddresses() {
        return addressList.toArray(new InetNetwork[addressList.size()]);
    }

    private String getDnsString() {
        if (dnsList.isEmpty())
            return null;
        return Attribute.iterableToString(getDnsStrings());
    }

    private List<String> getDnsStrings() {
        final List<String> strings = new ArrayList<>();
        for (final InetAddress addr : dnsList)
            strings.add(addr.getHostAddress());
        return strings;
    }

    public InetAddress[] getDnses() {
        return dnsList.toArray(new InetAddress[dnsList.size()]);
    }

    public int getListenPort() {
        return listenPort;
    }

    private String getListenPortString() {
        if (listenPort == 0)
            return null;
        return Integer.valueOf(listenPort).toString();
    }

    public int getMtu() {
        return mtu;
    }

    private String getMtuString() {
        if (mtu == 0)
            return null;
        return Integer.toString(mtu);
    }

    public String getPrivateKey() {
        if (keypair == null)
            return null;
        return keypair.getPrivateKey();
    }

    public String getPublicKey() {
        if (keypair == null)
            return null;
        return keypair.getPublicKey();
    }

    public void parse(final String line) {
        final Attribute key = Attribute.match(line);
        switch (key) {
            case ADDRESS:
                addAddresses(key.parseList(line));
                break;
            case DNS:
                addDnses(key.parseList(line));
                break;
            case LISTEN_PORT:
                setListenPortString(key.parse(line));
                break;
            case MTU:
                setMtuString(key.parse(line));
                break;
            case PRIVATE_KEY:
                setPrivateKey(key.parse(line));
                break;
            default:
                throw new IllegalArgumentException(line);
        }
    }

    private void setAddressString(final String addressString) {
        addressList.clear();
        addAddresses(Attribute.stringToList(addressString));
    }

    private void setDnsString(final String dnsString) {
        dnsList.clear();
        addDnses(Attribute.stringToList(dnsString));
    }

    private void setListenPort(final int listenPort) {
        this.listenPort = listenPort;
    }

    private void setListenPortString(final String port) {
        if (port != null && !port.isEmpty())
            setListenPort(Integer.parseInt(port, 10));
        else
            setListenPort(0);
    }

    private void setMtu(final int mtu) {
        this.mtu = mtu;
    }

    private void setMtuString(final String mtu) {
        if (mtu != null && !mtu.isEmpty())
            setMtu(Integer.parseInt(mtu, 10));
        else
            setMtu(0);
    }

    private void setPrivateKey(String privateKey) {
        if (privateKey != null && privateKey.isEmpty())
            privateKey = null;
        keypair = privateKey == null ? null : new Keypair(privateKey);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder().append("[Interface]\n");
        if (!addressList.isEmpty())
            sb.append(Attribute.ADDRESS.composeWith(addressList));
        if (!dnsList.isEmpty())
            sb.append(Attribute.DNS.composeWith(getDnsStrings()));
        if (listenPort != 0)
            sb.append(Attribute.LISTEN_PORT.composeWith(listenPort));
        if (mtu != 0)
            sb.append(Attribute.MTU.composeWith(mtu));
        if (keypair != null)
            sb.append(Attribute.PRIVATE_KEY.composeWith(keypair.getPrivateKey()));
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
        private String addresses;
        private String dnses;
        private String listenPort;
        private String mtu;
        private String privateKey;
        private String publicKey;

        public Observable(final Interface parent) {
            if (parent != null)
                loadData(parent);
        }

        private Observable(final Parcel in) {
            addresses = in.readString();
            dnses = in.readString();
            publicKey = in.readString();
            privateKey = in.readString();
            listenPort = in.readString();
            mtu = in.readString();
        }

        public void commitData(final Interface parent) {
            parent.setAddressString(addresses);
            parent.setDnsString(dnses);
            parent.setPrivateKey(privateKey);
            parent.setListenPortString(listenPort);
            parent.setMtuString(mtu);
            loadData(parent);
            notifyChange();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public void generateKeypair() {
            final Keypair keypair = new Keypair();
            privateKey = keypair.getPrivateKey();
            publicKey = keypair.getPublicKey();
            notifyPropertyChanged(BR.privateKey);
            notifyPropertyChanged(BR.publicKey);
        }

        @Bindable
        public String getAddresses() {
            return addresses;
        }

        @Bindable
        public String getDnses() {
            return dnses;
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

        protected void loadData(final Interface parent) {
            addresses = parent.getAddressString();
            dnses = parent.getDnsString();
            publicKey = parent.getPublicKey();
            privateKey = parent.getPrivateKey();
            listenPort = parent.getListenPortString();
            mtu = parent.getMtuString();
        }

        public void setAddresses(final String addresses) {
            this.addresses = addresses;
            notifyPropertyChanged(BR.addresses);
        }

        public void setDnses(final String dnses) {
            this.dnses = dnses;
            notifyPropertyChanged(BR.dnses);
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
                publicKey = new Keypair(privateKey).getPublicKey();
            } catch (final IllegalArgumentException ignored) {
                publicKey = "";
            }

            notifyPropertyChanged(BR.privateKey);
            notifyPropertyChanged(BR.publicKey);
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeString(addresses);
            dest.writeString(dnses);
            dest.writeString(publicKey);
            dest.writeString(privateKey);
            dest.writeString(listenPort);
            dest.writeString(mtu);
        }
    }
}
