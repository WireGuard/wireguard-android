package com.wireguard.config;

import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.os.Parcel;
import android.os.Parcelable;

import com.wireguard.android.BR;
import com.wireguard.crypto.Keypair;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents the configuration for a WireGuard interface (an [Interface] block).
 */

public class Interface {
    private List<IPCidr> addressList;
    private List<InetAddress> dnsList;
    private Keypair keypair;
    private int listenPort;
    private int mtu;
    public Interface() {
        addressList = new LinkedList<>();
        dnsList = new LinkedList<>();
    }

    private void addAddresses(String[] addresses) {
        if (addresses != null && addresses.length > 0) {
            for (final String addr : addresses) {
                if (addr.isEmpty())
                    throw new IllegalArgumentException("Address is empty");
                this.addressList.add(new IPCidr(addr));
            }
        }
    }

    private void addDnses(String[] dnses) {
        if (dnses != null && dnses.length > 0) {
            for (final String dns : dnses) {
                this.dnsList.add(Attribute.parseIPString(dns));
            }
        }
    }

    private String getAddressString() {
        if (addressList.isEmpty())
            return null;
        return Attribute.listToString(addressList);
    }

    public IPCidr[] getAddresses() {
        return addressList.toArray(new IPCidr[addressList.size()]);
    }

    private String getDnsString() {
        if (dnsList.isEmpty())
            return null;
        return Attribute.listToString(getDnsStrings());
    }

    private List<String> getDnsStrings() {
        List<String> strings = new LinkedList<>();
        for (final InetAddress addr : dnsList)
            strings.add(addr.getHostAddress());
        return strings;
    }

    public InetAddress[] getDnses() {
        return dnsList.toArray(new InetAddress[dnsList.size()]);
    }

    public int getListenPort() {
        return listenPort;
    }

    private String getListenPortString() {
        if (listenPort == 0)
            return null;
        return Integer.valueOf(listenPort).toString();
    }

    public int getMtu() {
        return mtu;
    }

    private String getMtuString() {
        if (mtu == 0)
            return null;
        return Integer.toString(mtu);
    }

    public String getPrivateKey() {
        if (keypair == null)
            return null;
        return keypair.getPrivateKey();
    }

    public String getPublicKey() {
        if (keypair == null)
            return null;
        return keypair.getPublicKey();
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

    private void setAddressString(final String addressString) {
        this.addressList.clear();
        addAddresses(Attribute.stringToList(addressString));
    }

    private void setDnsString(final String dnsString) {
        this.dnsList.clear();
        addDnses(Attribute.stringToList(dnsString));
    }

    private void setListenPort(int listenPort) {
        this.listenPort = listenPort;
    }

    private void setListenPortString(final String port) {
        if (port != null && !port.isEmpty())
            setListenPort(Integer.parseInt(port, 10));
        else
            setListenPort(0);
    }

    private void setMtu(int mtu) {
        this.mtu = mtu;
    }

    private void setMtuString(final String mtu) {
        if (mtu != null && !mtu.isEmpty())
            setMtu(Integer.parseInt(mtu, 10));
        else
            setMtu(0);
    }

    private void setPrivateKey(String privateKey) {
        if (privateKey != null && privateKey.isEmpty())
            privateKey = null;
        if (privateKey == null)
            keypair = null;
        else
            keypair = new Keypair(privateKey);
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
        if (keypair != null)
            sb.append(Attribute.PRIVATE_KEY.composeWith(keypair.getPrivateKey()));
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
        private String addresses;
        private String dnses;
        private String listenPort;
        private String mtu;
        private String privateKey;
        private String publicKey;

        public Observable(Interface parent) {
            if (parent != null)
                loadData(parent);
        }

        private Observable(final Parcel in) {
            addresses = in.readString();
            dnses = in.readString();
            publicKey = in.readString();
            privateKey = in.readString();
            listenPort = in.readString();
            mtu = in.readString();
        }

        public void commitData(Interface parent) {
            parent.setAddressString(this.addresses);
            parent.setDnsString(this.dnses);
            parent.setPrivateKey(this.privateKey);
            parent.setListenPortString(this.listenPort);
            parent.setMtuString(this.mtu);
            loadData(parent);
            notifyChange();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public void generateKeypair() {
            Keypair keypair = new Keypair();
            privateKey = keypair.getPrivateKey();
            publicKey = keypair.getPublicKey();
            notifyPropertyChanged(BR.privateKey);
            notifyPropertyChanged(BR.publicKey);
        }

        @Bindable
        public String getAddresses() {
            return addresses;
        }

        @Bindable
        public String getDnses() {
            return dnses;
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
            return publicKey;
        }

        public void loadData(Interface parent) {
            this.addresses = parent.getAddressString();
            this.dnses = parent.getDnsString();
            this.publicKey = parent.getPublicKey();
            this.privateKey = parent.getPrivateKey();
            this.listenPort = parent.getListenPortString();
            this.mtu = parent.getMtuString();
        }

        public void setAddresses(String addresses) {
            this.addresses = addresses;
            notifyPropertyChanged(BR.addresses);
        }

        public void setDnses(String dnses) {
            this.dnses = dnses;
            notifyPropertyChanged(BR.dnses);
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

            try {
                this.publicKey = new Keypair(privateKey).getPublicKey();
            } catch (IllegalArgumentException ignored) {
                this.publicKey = "";
            }

            notifyPropertyChanged(BR.privateKey);
            notifyPropertyChanged(BR.publicKey);
        }

        @Override
        public void writeToParcel(final Parcel dest, final int flags) {
            dest.writeString(addresses);
            dest.writeString(dnses);
            dest.writeString(publicKey);
            dest.writeString(privateKey);
            dest.writeString(listenPort);
            dest.writeString(mtu);
        }
    }
}
