package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.os.Parcel;
import android.os.Parcelable;

import com.wireguard.android.BR;
import com.wireguard.crypto.KeyEncoding;
import com.wireguard.crypto.Keypair;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;

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

    private List<IPCidr> addressList;
    private List<InetAddress> dnsList;
    private Keypair keypair;
    private int listenPort;
    private int mtu;
    private String privateKey;

    public Interface() {
        addressList = new LinkedList<>();
        dnsList = new LinkedList<>();
    }

    private Interface(final Parcel in) {
        addressList = in.createTypedArrayList(IPCidr.CREATOR);
        int dnsItems = in.readInt();
        dnsList = new LinkedList<>();
        for (int i = 0; i < dnsItems; ++i) {
            try {
                dnsList.add(InetAddress.getByAddress(in.createByteArray()));
            } catch (Exception e) {
            }
        }
        listenPort = in.readInt();
        mtu = in.readInt();
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
        if (addressList.isEmpty())
            return null;
        return Attribute.listToString(addressList);
    }

    @Bindable
    public IPCidr[] getAddresses() {
        return addressList.toArray(new IPCidr[addressList.size()]);
    }

    public List<String> getDnsStrings() {
        List<String> strings = new LinkedList<>();
        for (final InetAddress addr : dnsList)
            strings.add(addr.getHostAddress());
        return strings;
    }

    @Bindable
    public String getDnsString() {
        if (dnsList.isEmpty())
            return null;
        return Attribute.listToString(getDnsStrings());
    }

    @Bindable
    public InetAddress[] getDnses() {
        return dnsList.toArray(new InetAddress[dnsList.size()]);
    }

    @Bindable
    public int getListenPort() {
        return listenPort;
    }

    @Bindable
    public String getListenPortString() {
        if (listenPort == 0)
            return null;
        return new Integer(listenPort).toString();
    }

    @Bindable
    public int getMtu() {
        return mtu;
    }

    @Bindable
    public String getMtuString() {
        if (mtu == 0)
            return null;
        return new Integer(mtu).toString();
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
            setListenPortString(key.parse(line));
        else if (key == Attribute.MTU)
            setMtuString(key.parse(line));
        else if (key == Attribute.PRIVATE_KEY)
            setPrivateKey(key.parse(line));
        else
            throw new IllegalArgumentException(line);
    }

    public void addAddresses(String[] addresses) {
        if (addresses != null && addresses.length > 0) {
            for (final String addr : addresses) {
                if (addr.isEmpty())
                    throw new IllegalArgumentException("Address is empty");
                this.addressList.add(new IPCidr(addr));
            }
        }
        notifyPropertyChanged(BR.addresses);
        notifyPropertyChanged(BR.addressString);

    }

    public void setAddressString(final String addressString) {
        this.addressList.clear();
        addAddresses(Attribute.stringToList(addressString));
    }

    public void addDnses(String[] dnses) {
        if (dnses != null && dnses.length > 0) {
            for (final String dns : dnses) {
                if (dns.isEmpty())
                    throw new IllegalArgumentException("DNS is empty");
                try {
                    this.dnsList.add(InetAddress.getByName(dns));
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }
        notifyPropertyChanged(BR.dnses);
        notifyPropertyChanged(BR.dnsString);

    }

    public void setDnsString(final String dnsString) {
        this.dnsList.clear();
        addDnses(Attribute.stringToList(dnsString));
    }

    public void setListenPort(int listenPort) {
        this.listenPort = listenPort;
        notifyPropertyChanged(BR.listenPort);
        notifyPropertyChanged(BR.listenPortString);
    }

    public void setListenPortString(final String port) {
        if (port != null && !port.isEmpty())
            setListenPort(Integer.parseInt(port, 10));
        else
            setListenPort(0);
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
        notifyPropertyChanged(BR.mtu);
        notifyPropertyChanged(BR.mtuString);
    }

    public void setMtuString(final String mtu) {
        if (mtu != null && !mtu.isEmpty())
            setMtu(Integer.parseInt(mtu, 10));
        else
            setMtu(0);
    }

    public void setPrivateKey(String privateKey) {
        if (privateKey != null && privateKey.isEmpty())
            privateKey = null;
        this.privateKey = privateKey;
        if (privateKey != null && privateKey.length() == KeyEncoding.KEY_LENGTH_BASE64) {
            try {
                keypair = new Keypair(privateKey);
            } catch (final IllegalArgumentException e) {
                keypair = null;
                throw e;
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
        if (!addressList.isEmpty())
            sb.append(Attribute.ADDRESS.composeWith(addressList));
        if (!dnsList.isEmpty())
            sb.append(Attribute.DNS.composeWith(getDnsStrings()));
        if (listenPort != 0)
            sb.append(Attribute.LISTEN_PORT.composeWith(listenPort));
        if (mtu != 0)
            sb.append(Attribute.MTU.composeWith(mtu));
        if (privateKey != null)
            sb.append(Attribute.PRIVATE_KEY.composeWith(privateKey));
        return sb.toString();
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeTypedList(addressList);
        dest.writeInt(dnsList.size());
        for (final InetAddress addr : dnsList)
            dest.writeByteArray(addr.getAddress());
        dest.writeInt(listenPort);
        dest.writeInt(mtu);
        dest.writeString(privateKey);
    }
}
