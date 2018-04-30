package com.wireguard.config;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Locale;

public class IPCidr {
    private final InetAddress address;
    private int cidr;

    public IPCidr(String in) {
        cidr = -1;
        final int slash = in.lastIndexOf('/');
        if (slash != -1 && slash < in.length() - 1) {
            try {
                cidr = Integer.parseInt(in.substring(slash + 1), 10);
                in = in.substring(0, slash);
            } catch (final Exception ignored) {
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
        return String.format(Locale.getDefault(), "%s/%d", address.getHostAddress(), cidr);
    }
}
