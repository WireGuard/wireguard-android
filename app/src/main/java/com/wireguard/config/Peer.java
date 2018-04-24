package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.databinding.library.baseAdapters.BR;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

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

    private List<IPCidr> allowedIPsList;
    private InetSocketAddress endpoint;
    private int persistentKeepalive;
    private String preSharedKey;
    private String publicKey;

    public Peer() {
        allowedIPsList = new LinkedList<>();
    }

    private Peer(final Parcel in) {
        allowedIPsList = in.createTypedArrayList(IPCidr.CREATOR);
        String host = in.readString();
        int port = in.readInt();
        if (host != null && !host.isEmpty() && port > 0)
            endpoint = InetSocketAddress.createUnresolved(host, port);
        persistentKeepalive = in.readInt();
        preSharedKey = in.readString();
        if (preSharedKey != null && preSharedKey.isEmpty())
            preSharedKey = null;
        publicKey = in.readString();
        if (publicKey != null && publicKey.isEmpty())
            publicKey = null;
    }

    public static Peer newInstance() {
        return new Peer();
    }

    @Override
    public int describeContents() {
        return 0;
    }


    @Bindable
    public String getAllowedIPsString() {
        if (allowedIPsList.isEmpty())
            return null;
        return Attribute.listToString(allowedIPsList);
    }

    @Bindable
    public IPCidr[] getAllowedIPs() {
        return allowedIPsList.toArray(new IPCidr[allowedIPsList.size()]);
    }

    @Bindable
    public InetSocketAddress getEndpoint() {
        return endpoint;
    }

    @Bindable
    public String getEndpointString() {
        if (endpoint == null)
            return null;
        return String.format("%s:%d", endpoint.getHostString(), endpoint.getPort());
    }

    public String getResolvedEndpointString() throws UnknownHostException {
        if (endpoint == null)
            throw new UnknownHostException("{empty}");
        if (endpoint.isUnresolved())
            endpoint = new InetSocketAddress(endpoint.getHostString(), endpoint.getPort());
        if (endpoint.isUnresolved())
            throw new UnknownHostException(endpoint.getHostString());
        if (endpoint.getAddress() instanceof Inet6Address)
            return String.format("[%s]:%d", endpoint.getAddress().getHostAddress(), endpoint.getPort());
        return String.format("%s:%d",  endpoint.getAddress().getHostAddress(), endpoint.getPort());
    }

    @Bindable
    public int getPersistentKeepalive() {
        return persistentKeepalive;
    }

    @Bindable
    public String getPersistentKeepaliveString() {
        if (persistentKeepalive == 0)
            return null;
        return new Integer(persistentKeepalive).toString();
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
            setEndpointString(key.parse(line));
        else if (key == Attribute.PERSISTENT_KEEPALIVE)
            setPersistentKeepaliveString(key.parse(line));
        else if (key == Attribute.PRESHARED_KEY)
            setPreSharedKey(key.parse(line));
        else if (key == Attribute.PUBLIC_KEY)
            setPublicKey(key.parse(line));
        else
            throw new IllegalArgumentException(line);
    }

    public void addAllowedIPs(String[] allowedIPs) {
        if (allowedIPs != null && allowedIPs.length > 0) {
            for (final String allowedIP : allowedIPs) {
                if (allowedIP.isEmpty())
                    throw new IllegalArgumentException("AllowedIP is empty");
                this.allowedIPsList.add(new IPCidr(allowedIP));
            }
        }
        notifyPropertyChanged(BR.allowedIPs);
        notifyPropertyChanged(BR.allowedIPsString);
    }

    public void setAllowedIPsString(final String allowedIPsString) {
        this.allowedIPsList.clear();
        addAllowedIPs(Attribute.stringToList(allowedIPsString));
    }

    public void setEndpoint(InetSocketAddress endpoint) {
        this.endpoint = endpoint;
        notifyPropertyChanged(BR.endpoint);
        notifyPropertyChanged(BR.endpointString);
    }

    public void setEndpointString(final String endpoint) {
        if (endpoint != null && !endpoint.isEmpty()) {
            InetSocketAddress constructedEndpoint;
            if (endpoint.indexOf('/') != -1 || endpoint.indexOf('?') != -1 || endpoint.indexOf('#') != -1)
                    throw new IllegalArgumentException("Forbidden characters in endpoint");
            URI uri;
            try {
                uri = new URI("wg://" + endpoint);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
            constructedEndpoint = InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort());
            setEndpoint(constructedEndpoint);
        } else
            setEndpoint(null);
    }

    public void setPersistentKeepalive(int persistentKeepalive) {
        this.persistentKeepalive = persistentKeepalive;
        notifyPropertyChanged(BR.persistentKeepalive);
        notifyPropertyChanged(BR.persistentKeepaliveString);
    }

    public void setPersistentKeepaliveString(String persistentKeepalive) {
        if (persistentKeepalive != null && !persistentKeepalive.isEmpty())
            setPersistentKeepalive(Integer.parseInt(persistentKeepalive, 10));
        else
            setPersistentKeepalive(0);
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

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeTypedList(allowedIPsList);
        dest.writeString(endpoint == null ? null : endpoint.getHostString());
        dest.writeInt(endpoint == null ? 0 : endpoint.getPort());
        dest.writeInt(persistentKeepalive);
        dest.writeString(preSharedKey);
        dest.writeString(publicKey);
    }
}
