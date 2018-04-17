package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.databinding.library.baseAdapters.BR;

/**
 * Represents the configuration for a WireGuard peer (a [Peer] block).
 */

public class Peer extends BaseObservable implements Parcelable {
    public static final Creator<Peer> CREATOR = new Creator<Peer>() {
        @Override
        public Peer createFromParcel(final Parcel in) {
            return new Peer(in);
        }

        @Override
        public Peer[] newArray(final int size) {
            return new Peer[size];
        }
    };

    private String[] allowedIPList;
    private String endpoint;
    private String persistentKeepalive;
    private String preSharedKey;
    private String publicKey;

    public Peer() {
        allowedIPList = new String[0];
    }

    private Peer(final Parcel in) {
        allowedIPList = in.createStringArray();
        endpoint = in.readString();
        persistentKeepalive = in.readString();
        preSharedKey = in.readString();
        publicKey = in.readString();
    }

    public static Peer newInstance() {
        return new Peer();
    }

    @Override
    public int describeContents() {
        return 0;
    }


    @Bindable
    public String getAllowedIPsString() { return Attribute.listToString(allowedIPList); }

    @Bindable
    public String[] getAllowedIPs() {
        return allowedIPList;
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

    public void parse(final String line) {
        final Attribute key = Attribute.match(line);
        if (key == Attribute.ALLOWED_IPS)
            addAllowedIPs(key.parseList(line));
        else if (key == Attribute.ENDPOINT)
            setEndpoint(key.parse(line));
        else if (key == Attribute.PERSISTENT_KEEPALIVE)
            setPersistentKeepalive(key.parse(line));
        else if (key == Attribute.PRESHARED_KEY)
            setPreSharedKey(key.parse(line));
        else if (key == Attribute.PUBLIC_KEY)
            setPublicKey(key.parse(line));
        else
            throw new IllegalArgumentException(line);
    }

    public void addAllowedIPs(String[] allowedIPs) {
        if (allowedIPs == null || allowedIPs.length == 0)
            return;
        String[] both = new String[allowedIPs.length + this.allowedIPList.length];
        System.arraycopy(this.allowedIPList, 0, both, 0, this.allowedIPList.length);
        System.arraycopy(allowedIPs, 0, both, this.allowedIPList.length, allowedIPs.length);
        setAllowedIPs(both);
    }

    public void setAllowedIPs(String[] allowedIPs) {
        if (allowedIPs == null)
            allowedIPs = new String[0];
        this.allowedIPList = allowedIPs;
        notifyPropertyChanged(BR.allowedIPs);
    }

    public void setAllowedIPsString(String allowedIPsString) {
        setAllowedIPs(Attribute.stringToList(allowedIPsString));
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

    public void setPreSharedKey(String preSharedKey) {
        if (preSharedKey != null && preSharedKey.isEmpty())
            preSharedKey = null;
        this.preSharedKey = preSharedKey;
        notifyPropertyChanged(BR.preSharedKey);
    }

    public void setPublicKey(String publicKey) {
        if (publicKey != null && publicKey.isEmpty())
            publicKey = null;
        this.publicKey = publicKey;
        notifyPropertyChanged(BR.publicKey);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder().append("[Peer]\n");
        if (allowedIPList != null)
            sb.append(Attribute.ALLOWED_IPS.composeWith(allowedIPList));
        if (endpoint != null)
            sb.append(Attribute.ENDPOINT.composeWith(endpoint));
        if (persistentKeepalive != null)
            sb.append(Attribute.PERSISTENT_KEEPALIVE.composeWith(persistentKeepalive));
        if (preSharedKey != null)
            sb.append(Attribute.PRESHARED_KEY.composeWith(preSharedKey));
        if (publicKey != null)
            sb.append(Attribute.PUBLIC_KEY.composeWith(publicKey));
        return sb.toString();
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeStringArray(allowedIPList);
        dest.writeString(endpoint);
        dest.writeString(persistentKeepalive);
        dest.writeString(preSharedKey);
        dest.writeString(publicKey);
    }
}
