package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.Observable;

import com.android.databinding.library.baseAdapters.BR;

/**
 * Represents the configuration for a WireGuard peer (a [Peer] block).
 */

public class Peer extends BaseObservable implements Copyable<Peer>, Observable {
    private String allowedIPs;
    private Config config;
    private String endpoint;
    private String persistentKeepalive;
    private String publicKey;

    public Peer(final Config config) {
        this.config = config;
    }

    @Override
    public Peer copy() {
        return copy(config);
    }

    public Peer copy(final Config config) {
        final Peer copy = new Peer(config);
        copy.copyFrom(this);
        return copy;
    }

    @Override
    public void copyFrom(final Peer source) {
        allowedIPs = source.allowedIPs;
        endpoint = source.endpoint;
        persistentKeepalive = source.persistentKeepalive;
        publicKey = source.publicKey;
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
    public String getPublicKey() {
        return publicKey;
    }

    public void parseFrom(final String line) {
        final Attribute key = Attribute.match(line);
        if (key == Attribute.ALLOWED_IPS)
            allowedIPs = key.parseFrom(line);
        else if (key == Attribute.ENDPOINT)
            endpoint = key.parseFrom(line);
        else if (key == Attribute.PERSISTENT_KEEPALIVE)
            persistentKeepalive = key.parseFrom(line);
        else if (key == Attribute.PUBLIC_KEY)
            publicKey = key.parseFrom(line);
        else
            throw new IllegalArgumentException(line);
    }

    public void removeSelf() {
        config.getPeers().remove(this);
        config = null;
    }

    public void setAllowedIPs(String allowedIPs) {
        if (allowedIPs != null && allowedIPs.isEmpty())
            allowedIPs = null;
        this.allowedIPs = allowedIPs;
        notifyPropertyChanged(BR.allowedIPs);
    }

    public void setEndpoint(String endpoint) {
        if (endpoint != null && endpoint.isEmpty())
            endpoint = null;
        this.endpoint = endpoint;
        notifyPropertyChanged(BR.endpoint);
    }

    public void setPersistentKeepalive(String persistentKeepalive) {
        if (persistentKeepalive != null && persistentKeepalive.isEmpty())
            persistentKeepalive = null;
        this.persistentKeepalive = persistentKeepalive;
        notifyPropertyChanged(BR.persistentKeepalive);
    }

    public void setPublicKey(String publicKey) {
        if (publicKey != null && publicKey.isEmpty())
            publicKey = null;
        this.publicKey = publicKey;
        notifyPropertyChanged(BR.publicKey);
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder().append("[Peer]\n");
        if (allowedIPs != null)
            sb.append(Attribute.ALLOWED_IPS.composeWith(allowedIPs));
        if (endpoint != null)
            sb.append(Attribute.ENDPOINT.composeWith(endpoint));
        if (persistentKeepalive != null)
            sb.append(Attribute.PERSISTENT_KEEPALIVE.composeWith(persistentKeepalive));
        if (publicKey != null)
            sb.append(Attribute.PUBLIC_KEY.composeWith(publicKey));
        return sb.toString();
    }
}
