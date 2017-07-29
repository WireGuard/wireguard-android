package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.Observable;

import com.android.databinding.library.baseAdapters.BR;

/**
 * Represents the configuration for a WireGuard peer (a [Peer] block).
 */

public class Peer extends BaseObservable implements Observable {
    private String allowedIPs;
    private String endpoint;
    private String persistentKeepalive;
    private String publicKey;

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

    public void parseFrom(String line) {
        final Attribute key = Attribute.match(line);
        if (key == Attribute.ALLOWED_IPS)
            allowedIPs = key.parseFrom(line);
        else if (key == Attribute.ENDPOINT)
            endpoint = key.parseFrom(line);
        else if (key == Attribute.PERSISTENT_KEEPALIVE)
            persistentKeepalive = key.parseFrom(line);
        else if (key == Attribute.PUBLIC_KEY)
            publicKey = key.parseFrom(line);
    }

    public void setAllowedIPs(String allowedIPs) {
        this.allowedIPs = allowedIPs;
        notifyPropertyChanged(BR.allowedIPs);
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
        notifyPropertyChanged(BR.endpoint);
    }

    public void setPersistentKeepalive(String persistentKeepalive) {
        this.persistentKeepalive = persistentKeepalive;
        notifyPropertyChanged(BR.persistentKeepalive);
    }

    public void setPublicKey(String publicKey) {
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
