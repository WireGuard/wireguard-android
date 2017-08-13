package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.Observable;

import com.wireguard.android.BR;
import com.wireguard.crypto.Keypair;
import com.wireguard.crypto.KeyEncoding;

/**
 * Represents the configuration for a WireGuard interface (an [Interface] block).
 */

public class Interface extends BaseObservable implements Copyable<Interface>, Observable {
    private String address;
    private String dns;
    private String listenPort;
    private Keypair keypair;
    private String mtu;

    @Override
    public Interface copy() {
        final Interface copy = new Interface();
        copy.copyFrom(this);
        return copy;
    }

    @Override
    public void copyFrom(final Interface source) {
        address = source.address;
        dns = source.dns;
        listenPort = source.listenPort;
        keypair = source.keypair;
        mtu = source.mtu;
    }

    public void generateKeypair() {
        keypair = new Keypair();
        notifyPropertyChanged(BR.privateKey);
        notifyPropertyChanged(BR.publicKey);
    }

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
        return keypair != null ? keypair.getPrivateKey() : null;
    }

    @Bindable
    public String getPublicKey() {
        return keypair != null ? keypair.getPublicKey() : null;
    }

    public void parseFrom(final String line) {
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
            keypair = new Keypair(key.parseFrom(line));
        else
            throw new IllegalArgumentException(line);
    }

    public void setAddress(String address) {
        if (address != null && address.isEmpty())
            address = null;
        this.address = address;
        notifyPropertyChanged(BR.address);
    }

    public void setDns(String dns) {
        if (dns != null && dns.isEmpty())
            dns = null;
        this.dns = dns;
        notifyPropertyChanged(BR.dns);
    }

    public void setListenPort(String listenPort) {
        if (listenPort != null && listenPort.isEmpty())
            listenPort = null;
        this.listenPort = listenPort;
        notifyPropertyChanged(BR.listenPort);
    }

    public void setMtu(String mtu) {
        if (mtu != null && mtu.isEmpty())
            mtu = null;
        this.mtu = mtu;
        notifyPropertyChanged(BR.mtu);
    }

    public void setPrivateKey(final String privateKey) {
        if (privateKey != null && !privateKey.isEmpty()) {
            // Avoid exceptions from Keypair while the user is typing.
            if (privateKey.length() != KeyEncoding.KEY_LENGTH_BASE64)
                return;
            keypair = new Keypair(privateKey);
        } else {
            keypair = null;
        }
        notifyPropertyChanged(BR.privateKey);
        notifyPropertyChanged(BR.publicKey);
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
        if (keypair != null)
            sb.append(Attribute.PRIVATE_KEY.composeWith(keypair.getPrivateKey()));
        return sb.toString();
    }
}
