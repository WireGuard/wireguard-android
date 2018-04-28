package com.wireguard.config;

import android.os.Parcel;
import android.os.Parcelable;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

public class IPCidr implements Parcelable {
    InetAddress address;
    int cidr;


    public static final Parcelable.Creator<IPCidr> CREATOR = new Parcelable.Creator<IPCidr>() {
        @Override
        public IPCidr createFromParcel(final Parcel in) {
            return new IPCidr(in);
        }

        @Override
        public IPCidr[] newArray(final int size) {
            return new IPCidr[size];
        }
    };

    public IPCidr(String in) {
        parse(in);
    }

    private void parse(String in) {
        cidr = -1;
        int slash = in.lastIndexOf('/');
        if (slash != -1 && slash < in.length() - 1) {
            try {
                cidr = Integer.parseInt(in.substring(slash + 1), 10);
                in = in.substring(0, slash);
            } catch (Exception e) {
            }
        }
        address = Attribute.parseIPString(in);
        if ((address instanceof Inet6Address) && (cidr > 128 || cidr < 0))
            cidr = 128;
        else if ((address instanceof Inet4Address) && (cidr > 32 || cidr < 0))
            cidr = 32;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getCidr() {
        return cidr;
    }

    @Override
    public String toString() {
        return String.format("%s/%d", address.getHostAddress(), cidr);
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeString(this.toString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private IPCidr(final Parcel in) {
        try {
            parse(in.readString());
        } catch (Exception e) {
        }
    }

}
