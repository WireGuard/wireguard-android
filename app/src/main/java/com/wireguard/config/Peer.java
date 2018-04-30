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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Represents the configuration for a WireGuard peer (a [Peer] block).
 */

public class Peer {
    public static class Observable extends BaseObservable implements Parcelable {
        private String allowedIPs;
        private String endpoint;
        private String persistentKeepalive;
        private String preSharedKey;
        private String publicKey;

        public Observable(Peer parent) {
            loadData(parent);
        }
        public static Observable newInstance() {
            return new Observable(new Peer());
        }

        public void loadData(Peer parent) {
            this.allowedIPs = parent.getAllowedIPsString();
            this.endpoint = parent.getEndpointString();
            this.persistentKeepalive = parent.getPersistentKeepaliveString();
            this.preSharedKey = parent.getPreSharedKey();
            this.publicKey = parent.getPublicKey();
        }

        public void commitData(Peer parent) {
            parent.setAllowedIPsString(this.allowedIPs);
            parent.setEndpointString(this.endpoint);
            parent.setPersistentKeepaliveString(this.persistentKeepalive);
            parent.setPreSharedKey(this.preSharedKey);
            parent.setPublicKey(this.publicKey);
            if (parent.getPublicKey() == null)
                throw new IllegalArgumentException("Peer public key may not be empty");
            loadData(parent);
            notifyChange();
        }

        @Bindable
        public String getAllowedIPs() {
            return allowedIPs;
        }

        public void setAllowedIPs(String allowedIPs) {
            this.allowedIPs = allowedIPs;
            notifyPropertyChanged(BR.allowedIPs);
        }

        @Bindable
        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
            notifyPropertyChanged(BR.endpoint);
        }

        @Bindable
        public String getPersistentKeepalive() {
            return persistentKeepalive;
        }

        public void setPersistentKeepalive(String persistentKeepalive) {
            this.persistentKeepalive = persistentKeepalive;
            notifyPropertyChanged(BR.persistentKeepalive);
        }

        @Bindable
        public String getPreSharedKey() {
            return preSharedKey;
        }

        public void setPreSharedKey(String preSharedKey) {
            this.preSharedKey = preSharedKey;
            notifyPropertyChanged(BR.preSharedKey);
        }

        @Bindable
        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
            notifyPropertyChanged(BR.publicKey);
        }


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

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeString(allowedIPs);
            dest.writeString(endpoint);
            dest.writeString(persistentKeepalive);
            dest.writeString(preSharedKey);
            dest.writeString(publicKey);
        }

        private Observable(final Parcel in) {
            allowedIPs = in.readString();
            endpoint = in.readString();
            persistentKeepalive = in.readString();
            preSharedKey = in.readString();
            publicKey = in.readString();
        }
    }

    private List<IPCidr> allowedIPsList;
    private InetSocketAddress endpoint;
    private int persistentKeepalive;
    private String preSharedKey;
    private String publicKey;

    public Peer() {
        allowedIPsList = new LinkedList<>();
    }


    private String getAllowedIPsString() {
        if (allowedIPsList.isEmpty())
            return null;
        return Attribute.listToString(allowedIPsList);
    }

    public IPCidr[] getAllowedIPs() {
        return allowedIPsList.toArray(new IPCidr[allowedIPsList.size()]);
    }

    public InetSocketAddress getEndpoint() {
        return endpoint;
    }

    private String getEndpointString() {
        if (endpoint == null)
            return null;
        return String.format(Locale.getDefault(), "%s:%d", endpoint.getHostString(), endpoint.getPort());
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

    private void addAllowedIPs(String[] allowedIPs) {
        if (allowedIPs != null && allowedIPs.length > 0) {
            for (final String allowedIP : allowedIPs) {
                this.allowedIPsList.add(new IPCidr(allowedIP));
            }
        }
    }

    private void setAllowedIPsString(final String allowedIPsString) {
        this.allowedIPsList.clear();
        addAllowedIPs(Attribute.stringToList(allowedIPsString));
    }

    private void setEndpoint(InetSocketAddress endpoint) {
        this.endpoint = endpoint;
    }

    private void setEndpointString(final String endpoint) {
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

    private void setPersistentKeepalive(int persistentKeepalive) {
        this.persistentKeepalive = persistentKeepalive;
    }

    private void setPersistentKeepaliveString(String persistentKeepalive) {
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
}
