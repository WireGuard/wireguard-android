package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.os.Parcel;
import android.os.Parcelable;

import com.wireguard.android.BR;
import com.wireguard.crypto.KeyEncoding;
import com.wireguard.crypto.Keypair;

/**
 * Represents the configuration for a WireGuard interface (an [Interface] block).
 */

public class Interface extends BaseObservable implements Parcelable {
    public static final Creator<Interface> CREATOR = new Creator<Interface>() {
        @Override
        public Interface createFromParcel(final Parcel in) {
            return new Interface(in);
        }

        @Override
        public Interface[] newArray(final int size) {
            return new Interface[size];
        }
    };

    private String[] addressList;
    private String[] dnsList;
    private Keypair keypair;
    private String listenPort;
    private String mtu;
    private String privateKey;

    public Interface() {
        addressList = new String[0];
        dnsList = new String[0];
    }

    private Interface(final Parcel in) {
        addressList = in.createStringArray();
        dnsList = in.createStringArray();
        listenPort = in.readString();
        mtu = in.readString();
        setPrivateKey(in.readString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void generateKeypair() {
        keypair = new Keypair();
        privateKey = keypair.getPrivateKey();
        notifyPropertyChanged(BR.privateKey);
        notifyPropertyChanged(BR.publicKey);
    }

    @Bindable
    public String getAddressString() {
        return Attribute.listToString(addressList);
    }

    @Bindable
    public String[] getAddresses() {
        return addressList;
    }

    @Bindable
    public String getDnsString() {
        return Attribute.listToString(dnsList);
    }

    @Bindable
    public String[] getDnses() {
        return dnsList;
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
        return keypair != null ? keypair.getPublicKey() : null;
    }

    public void parse(final String line) {
        final Attribute key = Attribute.match(line);
        if (key == Attribute.ADDRESS)
            addAddresses(key.parseList(line));
        else if (key == Attribute.DNS)
            addDnses(key.parseList(line));
        else if (key == Attribute.LISTEN_PORT)
            setListenPort(key.parse(line));
        else if (key == Attribute.MTU)
            setMtu(key.parse(line));
        else if (key == Attribute.PRIVATE_KEY)
            setPrivateKey(key.parse(line));
        else
            throw new IllegalArgumentException(line);
    }

    public void addAddresses(String[] addresses) {
        if (addresses == null || addresses.length == 0)
            return;
        String[] both = new String[addresses.length + this.addressList.length];
        System.arraycopy(this.addressList, 0, both, 0, this.addressList.length);
        System.arraycopy(addresses, 0, both, this.addressList.length, addresses.length);
        setAddresses(both);
    }

    public void setAddresses(String[] addresses) {
        if (addresses == null)
            addresses = new String[0];
        this.addressList = addresses;
        notifyPropertyChanged(BR.addresses);
    }

    public void setAddressString(String addressString) {
        setAddresses(Attribute.stringToList(addressString));
    }

    public void addDnses(String[] dnses) {
        if (dnses == null || dnses.length == 0)
            return;
        String[] both = new String[dnses.length + this.dnsList.length];
        System.arraycopy(this.dnsList, 0, both, 0, this.dnsList.length);
        System.arraycopy(dnses, 0, both, this.dnsList.length, dnses.length);
        setDnses(both);
    }

    public void setDnses(String[] dnses) {
        if (dnses == null)
            dnses = new String[0];
        this.dnsList = dnses;
        notifyPropertyChanged(BR.dnses);
    }

    public void setDnsString(String dnsString) {
        setDnses(Attribute.stringToList(dnsString));
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

    public void setPrivateKey(String privateKey) {
        if (privateKey != null && privateKey.isEmpty())
            privateKey = null;
        this.privateKey = privateKey;
        if (privateKey != null && privateKey.length() == KeyEncoding.KEY_LENGTH_BASE64) {
            try {
                keypair = new Keypair(privateKey);
            } catch (final IllegalArgumentException ignored) {
                keypair = null;
            }
        } else {
            keypair = null;
        }
        notifyPropertyChanged(BR.privateKey);
        notifyPropertyChanged(BR.publicKey);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder().append("[Interface]\n");
        if (addressList != null && addressList.length > 0)
            sb.append(Attribute.ADDRESS.composeWith(addressList));
        if (dnsList != null && dnsList.length > 0)
            sb.append(Attribute.DNS.composeWith(dnsList));
        if (listenPort != null)
            sb.append(Attribute.LISTEN_PORT.composeWith(listenPort));
        if (mtu != null)
            sb.append(Attribute.MTU.composeWith(mtu));
        if (privateKey != null)
            sb.append(Attribute.PRIVATE_KEY.composeWith(privateKey));
        return sb.toString();
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeStringArray(addressList);
        dest.writeStringArray(dnsList);
        dest.writeString(listenPort);
        dest.writeString(mtu);
        dest.writeString(privateKey);
    }
}
