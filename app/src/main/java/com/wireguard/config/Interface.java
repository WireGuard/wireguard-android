package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.Observable;

import com.wireguard.android.BR;

/**
 * Represents the configuration for a WireGuard interface (an [Interface] block).
 */

public class Interface extends BaseObservable implements Observable {
    private String address;
    private String dns;
    private String listenPort;
    private String mtu;
    private String privateKey;

    @Bindable
    public String getAddress() {
        return address;
    }

    @Bindable
    public String getDns() {
        return dns;
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

    public void parseFrom(String line) {
        final Attribute key = Attribute.match(line);
        if (key == Attribute.ADDRESS)
            address = key.parseFrom(line);
        else if (key == Attribute.DNS)
            dns = key.parseFrom(line);
        else if (key == Attribute.LISTEN_PORT)
            listenPort = key.parseFrom(line);
        else if (key == Attribute.MTU)
            mtu = key.parseFrom(line);
        else if (key == Attribute.PRIVATE_KEY)
            privateKey = key.parseFrom(line);
    }

    public void setAddress(String address) {
        this.address = address;
        notifyPropertyChanged(BR.address);
    }

    public void setDns(String dns) {
        this.dns = dns;
        notifyPropertyChanged(BR.dns);
    }

    public void setListenPort(String listenPort) {
        this.listenPort = listenPort;
        notifyPropertyChanged(BR.listenPort);
    }

    public void setMtu(String mtu) {
        this.mtu = mtu;
        notifyPropertyChanged(BR.mtu);
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
        notifyPropertyChanged(BR.privateKey);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder().append("[Interface]\n");
        if (address != null)
            sb.append(Attribute.ADDRESS.composeWith(address));
        if (dns != null)
            sb.append(Attribute.DNS.composeWith(dns));
        if (listenPort != null)
            sb.append(Attribute.LISTEN_PORT.composeWith(listenPort));
        if (mtu != null)
            sb.append(Attribute.MTU.composeWith(mtu));
        if (privateKey != null)
            sb.append(Attribute.PRIVATE_KEY.composeWith(privateKey));
        return sb.toString();
    }
}
