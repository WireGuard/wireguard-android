/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import android.annotation.SuppressLint;
import android.content.Context;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.android.databinding.library.baseAdapters.BR;
import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.crypto.KeyEncoding;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import java9.lang.Iterables;

/**
 * Represents the configuration for a WireGuard peer (a [Peer] block).
 */

public class Peer {
    private final List<InetNetwork> allowedIPsList;
    @Nullable private InetSocketAddress endpoint;
    private int persistentKeepalive;
    @Nullable private String preSharedKey;
    @Nullable private String publicKey;
    private final Context context = Application.get();

    public Peer() {
        allowedIPsList = new ArrayList<>();
    }

    private void addAllowedIPs(@Nullable final String[] allowedIPs) {
        if (allowedIPs != null && allowedIPs.length > 0) {
            for (final String allowedIP : allowedIPs) {
                allowedIPsList.add(new InetNetwork(allowedIP));
            }
        }
    }

    public InetNetwork[] getAllowedIPs() {
        return allowedIPsList.toArray(new InetNetwork[allowedIPsList.size()]);
    }

    @Nullable
    private String getAllowedIPsString() {
        if (allowedIPsList.isEmpty())
            return null;
        return Attribute.iterableToString(allowedIPsList);
    }

    @Nullable
    public InetSocketAddress getEndpoint() {
        return endpoint;
    }

    @SuppressLint("DefaultLocale")
    @Nullable
    private String getEndpointString() {
        if (endpoint == null)
            return null;
        if (endpoint.getHostString().contains(":") && !endpoint.getHostString().contains("["))
            return String.format("[%s]:%d", endpoint.getHostString(), endpoint.getPort());
        else
            return String.format("%s:%d", endpoint.getHostString(), endpoint.getPort());
    }

    public int getPersistentKeepalive() {
        return persistentKeepalive;
    }

    @Nullable
    private String getPersistentKeepaliveString() {
        if (persistentKeepalive == 0)
            return null;
        return Integer.valueOf(persistentKeepalive).toString();
    }

    @Nullable
    public String getPreSharedKey() {
        return preSharedKey;
    }

    @Nullable
    public String getPublicKey() {
        return publicKey;
    }

    @SuppressLint("DefaultLocale")
    public String getResolvedEndpointString() throws UnknownHostException {
        if (endpoint == null)
            throw new UnknownHostException("{empty}");
        if (endpoint.isUnresolved())
            endpoint = new InetSocketAddress(endpoint.getHostString(), endpoint.getPort());
        if (endpoint.isUnresolved())
            throw new UnknownHostException(endpoint.getHostString());
        if (endpoint.getAddress() instanceof Inet6Address)
            return String.format("[%s]:%d",
                    endpoint.getAddress().getHostAddress(),
                    endpoint.getPort());
        return String.format("%s:%d",
                endpoint.getAddress().getHostAddress(),
                endpoint.getPort());
    }

    public void parse(final String line) {
        final Attribute key = Attribute.match(line);
        if (key == null)
            throw new IllegalArgumentException(context.getString(R.string.tunnel_error_interface_parse_failed, line));
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

    private void setAllowedIPsString(@Nullable final String allowedIPsString) {
        allowedIPsList.clear();
        addAllowedIPs(Attribute.stringToList(allowedIPsString));
    }

    private void setEndpoint(@Nullable final InetSocketAddress endpoint) {
        this.endpoint = endpoint;
    }

    private void setEndpointString(@Nullable final String endpoint) {
        if (endpoint != null && !endpoint.isEmpty()) {
            final InetSocketAddress constructedEndpoint;
            if (endpoint.indexOf('/') != -1 || endpoint.indexOf('?') != -1 || endpoint.indexOf('#') != -1)
                throw new IllegalArgumentException(context.getString(R.string.tunnel_error_forbidden_endpoint_chars));
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

    private void setPersistentKeepaliveString(@Nullable final String persistentKeepalive) {
        if (persistentKeepalive != null && !persistentKeepalive.isEmpty())
            setPersistentKeepalive(Integer.parseInt(persistentKeepalive, 10));
        else
            setPersistentKeepalive(0);
    }

    private void setPreSharedKey(@Nullable String preSharedKey) {
        if (preSharedKey != null && preSharedKey.isEmpty())
            preSharedKey = null;
        if (preSharedKey != null)
            KeyEncoding.keyFromBase64(preSharedKey);
        this.preSharedKey = preSharedKey;
    }

    private void setPublicKey(@Nullable String publicKey) {
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
        @Nullable private String allowedIPs;
        @Nullable private String endpoint;
        @Nullable private String persistentKeepalive;
        @Nullable private String preSharedKey;
        @Nullable private String publicKey;
        private final List<String> interfaceDNSRoutes = new ArrayList<>();
        private int numSiblings;

        public Observable(final Peer parent) {
            loadData(parent);
        }

        private Observable(final Parcel in) {
            allowedIPs = in.readString();
            endpoint = in.readString();
            persistentKeepalive = in.readString();
            preSharedKey = in.readString();
            publicKey = in.readString();
            numSiblings = in.readInt();
            in.readStringList(interfaceDNSRoutes);
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
                throw new IllegalArgumentException(Application.get().getString(R.string.tunnel_error_empty_peer_public_key));
            loadData(parent);
            notifyChange();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        private static final String DEFAULT_ROUTE_V4 = "0.0.0.0/0";
        private static final List<String> DEFAULT_ROUTE_MOD_RFC1918_V4 = Arrays.asList("0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4", "32.0.0.0/3", "64.0.0.0/2", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/6", "172.0.0.0/12", "172.32.0.0/11", "172.64.0.0/10", "172.128.0.0/9", "173.0.0.0/8", "174.0.0.0/7", "176.0.0.0/4", "192.0.0.0/9", "192.128.0.0/11", "192.160.0.0/13", "192.169.0.0/16", "192.170.0.0/15", "192.172.0.0/14", "192.176.0.0/12", "192.192.0.0/10", "193.0.0.0/8", "194.0.0.0/7", "196.0.0.0/6", "200.0.0.0/5", "208.0.0.0/4");

        public void toggleExcludePrivateIPs() {
            final Collection<String> ips = new HashSet<>(Arrays.asList(Attribute.stringToList(allowedIPs)));
            final boolean hasDefaultRoute = ips.contains(DEFAULT_ROUTE_V4);
            final boolean hasDefaultRouteModRFC1918 = ips.containsAll(DEFAULT_ROUTE_MOD_RFC1918_V4);
            if ((!hasDefaultRoute && !hasDefaultRouteModRFC1918) || numSiblings > 0)
                return;
            Iterables.removeIf(ips, ip -> !ip.contains(":"));
            if (hasDefaultRoute) {
                ips.addAll(DEFAULT_ROUTE_MOD_RFC1918_V4);
                ips.addAll(interfaceDNSRoutes);
            } else if (hasDefaultRouteModRFC1918)
                ips.add(DEFAULT_ROUTE_V4);
            setAllowedIPs(Attribute.iterableToString(ips));
        }

        @Bindable
        public boolean getCanToggleExcludePrivateIPs() {
            final Collection<String> ips = Arrays.asList(Attribute.stringToList(allowedIPs));
            return numSiblings == 0 && (ips.contains(DEFAULT_ROUTE_V4) || ips.containsAll(DEFAULT_ROUTE_MOD_RFC1918_V4));
        }

        @Bindable
        public boolean getIsExcludePrivateIPsOn() {
            return numSiblings == 0 && Arrays.asList(Attribute.stringToList(allowedIPs)).containsAll(DEFAULT_ROUTE_MOD_RFC1918_V4);
        }

        @Bindable @Nullable
        public String getAllowedIPs() {
            return allowedIPs;
        }

        @Bindable @Nullable
        public String getEndpoint() {
            return endpoint;
        }

        @Bindable @Nullable
        public String getPersistentKeepalive() {
            return persistentKeepalive;
        }

        @Bindable @Nullable
        public String getPreSharedKey() {
            return preSharedKey;
        }

        @Bindable @Nullable
        public String getPublicKey() {
            return publicKey;
        }

        private void loadData(final Peer parent) {
            allowedIPs = parent.getAllowedIPsString();
            endpoint = parent.getEndpointString();
            persistentKeepalive = parent.getPersistentKeepaliveString();
            preSharedKey = parent.getPreSharedKey();
            publicKey = parent.getPublicKey();
        }

        public void setAllowedIPs(final String allowedIPs) {
            this.allowedIPs = allowedIPs;
            notifyPropertyChanged(BR.allowedIPs);
            notifyPropertyChanged(BR.canToggleExcludePrivateIPs);
            notifyPropertyChanged(BR.isExcludePrivateIPsOn);
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

        public void setInterfaceDNSRoutes(@Nullable final String dnsServers) {
            final Collection<String> ips = new HashSet<>(Arrays.asList(Attribute.stringToList(allowedIPs)));
            final boolean modifyAllowedIPs = ips.containsAll(DEFAULT_ROUTE_MOD_RFC1918_V4);

            ips.removeAll(interfaceDNSRoutes);
            interfaceDNSRoutes.clear();
            for (final String dnsServer : Attribute.stringToList(dnsServers)) {
                if (!dnsServer.contains(":"))
                    interfaceDNSRoutes.add(dnsServer + "/32");
            }
            ips.addAll(interfaceDNSRoutes);
            if (modifyAllowedIPs)
                setAllowedIPs(Attribute.iterableToString(ips));
        }

        public void setNumSiblings(final int num) {
            numSiblings = num;
            notifyPropertyChanged(BR.canToggleExcludePrivateIPs);
            notifyPropertyChanged(BR.isExcludePrivateIPsOn);
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeString(allowedIPs);
            dest.writeString(endpoint);
            dest.writeString(persistentKeepalive);
            dest.writeString(preSharedKey);
            dest.writeString(publicKey);
            dest.writeInt(numSiblings);
            dest.writeStringList(interfaceDNSRoutes);
        }
    }
}
