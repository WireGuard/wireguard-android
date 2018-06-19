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

import com.android.databinding.library.baseAdapters.BR;
import com.wireguard.crypto.KeyEncoding;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Represents the configuration for a WireGuard peer (a [Peer] block).
 */

public class Peer {
    private final List<InetNetwork> allowedIPsList;
    private InetSocketAddress endpoint;
    private int persistentKeepalive;
    private String preSharedKey;
    private String publicKey;

    public Peer() {
        allowedIPsList = new ArrayList<>();
    }

    private void addAllowedIPs(final String[] allowedIPs) {
        if (allowedIPs != null && allowedIPs.length > 0) {
            for (final String allowedIP : allowedIPs) {
                allowedIPsList.add(new InetNetwork(allowedIP));
            }
        }
    }

    public InetNetwork[] getAllowedIPs() {
        return allowedIPsList.toArray(new InetNetwork[allowedIPsList.size()]);
    }

    private String getAllowedIPsString() {
        if (allowedIPsList.isEmpty())
            return null;
        return Attribute.iterableToString(allowedIPsList);
    }

    public InetSocketAddress getEndpoint() {
        return endpoint;
    }

    private String getEndpointString() {
        if (endpoint == null)
            return null;
        return String.format(Locale.getDefault(), "%s:%d", endpoint.getHostString(), endpoint.getPort());
    }

    public int getPersistentKeepalive() {
        return persistentKeepalive;
    }

    private String getPersistentKeepaliveString() {
        if (persistentKeepalive == 0)
            return null;
        return Integer.valueOf(persistentKeepalive).toString();
    }

    public String getPreSharedKey() {
        return preSharedKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getResolvedEndpointString() throws UnknownHostException {
        if (endpoint == null)
            throw new UnknownHostException("{empty}");
        if (endpoint.isUnresolved())
            endpoint = new InetSocketAddress(endpoint.getHostString(), endpoint.getPort());
        if (endpoint.isUnresolved())
            throw new UnknownHostException(endpoint.getHostString());
        if (endpoint.getAddress() instanceof Inet6Address)
            return String.format(Locale.getDefault(),
                    "[%s]:%d",
                    endpoint.getAddress().getHostAddress(),
                    endpoint.getPort());
        return String.format(Locale.getDefault(),
                "%s:%d",
                endpoint.getAddress().getHostAddress(),
                endpoint.getPort());
    }

    public void parse(final String line) {
        final Attribute key = Attribute.match(line);
        switch (key) {
            case ALLOWED_IPS:
                addAllowedIPs(key.parseList(line));
                break;
            case ENDPOINT:
                setEndpointString(key.parse(line));
                break;
            case PERSISTENT_KEEPALIVE:
                setPersistentKeepaliveString(key.parse(line));
                break;
            case PRESHARED_KEY:
                setPreSharedKey(key.parse(line));
                break;
            case PUBLIC_KEY:
                setPublicKey(key.parse(line));
                break;
            default:
                throw new IllegalArgumentException(line);
        }
    }

    private void setAllowedIPsString(final String allowedIPsString) {
        allowedIPsList.clear();
        addAllowedIPs(Attribute.stringToList(allowedIPsString));
    }

    private void setEndpoint(final InetSocketAddress endpoint) {
        this.endpoint = endpoint;
    }

    private void setEndpointString(final String endpoint) {
        if (endpoint != null && !endpoint.isEmpty()) {
            final InetSocketAddress constructedEndpoint;
            if (endpoint.indexOf('/') != -1 || endpoint.indexOf('?') != -1 || endpoint.indexOf('#') != -1)
                throw new IllegalArgumentException("Forbidden characters in endpoint");
            final URI uri;
            try {
                uri = new URI("wg://" + endpoint);
            } catch (final URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
            constructedEndpoint = InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
            setEndpoint(constructedEndpoint);
        } else
            setEndpoint(null);
    }

    private void setPersistentKeepalive(final int persistentKeepalive) {
        this.persistentKeepalive = persistentKeepalive;
    }

    private void setPersistentKeepaliveString(final String persistentKeepalive) {
        if (persistentKeepalive != null && !persistentKeepalive.isEmpty())
            setPersistentKeepalive(Integer.parseInt(persistentKeepalive, 10));
        else
            setPersistentKeepalive(0);
    }

    private void setPreSharedKey(String preSharedKey) {
        if (preSharedKey != null && preSharedKey.isEmpty())
            preSharedKey = null;
        if (preSharedKey != null)
            KeyEncoding.keyFromBase64(preSharedKey);
        this.preSharedKey = preSharedKey;
    }

    private void setPublicKey(String publicKey) {
        if (publicKey != null && publicKey.isEmpty())
            publicKey = null;
        if (publicKey != null)
            KeyEncoding.keyFromBase64(publicKey);
        this.publicKey = publicKey;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder().append("[Peer]\n");
        if (!allowedIPsList.isEmpty())
            sb.append(Attribute.ALLOWED_IPS.composeWith(allowedIPsList));
        if (endpoint != null)
            sb.append(Attribute.ENDPOINT.composeWith(getEndpointString()));
        if (persistentKeepalive != 0)
            sb.append(Attribute.PERSISTENT_KEEPALIVE.composeWith(persistentKeepalive));
        if (preSharedKey != null)
            sb.append(Attribute.PRESHARED_KEY.composeWith(preSharedKey));
        if (publicKey != null)
            sb.append(Attribute.PUBLIC_KEY.composeWith(publicKey));
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
        private String allowedIPs;
        private String endpoint;
        private String persistentKeepalive;
        private String preSharedKey;
        private String publicKey;

        public Observable(final Peer parent) {
            loadData(parent);
        }

        private Observable(final Parcel in) {
            allowedIPs = in.readString();
            endpoint = in.readString();
            persistentKeepalive = in.readString();
            preSharedKey = in.readString();
            publicKey = in.readString();
        }

        public static Observable newInstance() {
            return new Observable(new Peer());
        }

        public void commitData(final Peer parent) {
            parent.setAllowedIPsString(allowedIPs);
            parent.setEndpointString(endpoint);
            parent.setPersistentKeepaliveString(persistentKeepalive);
            parent.setPreSharedKey(preSharedKey);
            parent.setPublicKey(publicKey);
            if (parent.getPublicKey() == null)
                throw new IllegalArgumentException("Peer public key may not be empty");
            loadData(parent);
            notifyChange();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Bindable
        public String getAllowedIPs() {
            return allowedIPs;
        }

        @Bindable
        public String getEndpoint() {
            return endpoint;
        }

        @Bindable
        public String getPersistentKeepalive() {
            return persistentKeepalive;
        }

        @Bindable
        public String getPreSharedKey() {
            return preSharedKey;
        }

        @Bindable
        public String getPublicKey() {
            return publicKey;
        }

        protected void loadData(final Peer parent) {
            allowedIPs = parent.getAllowedIPsString();
            endpoint = parent.getEndpointString();
            persistentKeepalive = parent.getPersistentKeepaliveString();
            preSharedKey = parent.getPreSharedKey();
            publicKey = parent.getPublicKey();
        }

        public void setAllowedIPs(final String allowedIPs) {
            this.allowedIPs = allowedIPs;
            notifyPropertyChanged(BR.allowedIPs);
        }

        public void setEndpoint(final String endpoint) {
            this.endpoint = endpoint;
            notifyPropertyChanged(BR.endpoint);
        }

        public void setPersistentKeepalive(final String persistentKeepalive) {
            this.persistentKeepalive = persistentKeepalive;
            notifyPropertyChanged(BR.persistentKeepalive);
        }

        public void setPreSharedKey(final String preSharedKey) {
            this.preSharedKey = preSharedKey;
            notifyPropertyChanged(BR.preSharedKey);
        }

        public void setPublicKey(final String publicKey) {
            this.publicKey = publicKey;
            notifyPropertyChanged(BR.publicKey);
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeString(allowedIPs);
            dest.writeString(endpoint);
            dest.writeString(persistentKeepalive);
            dest.writeString(preSharedKey);
            dest.writeString(publicKey);
        }
    }
}
