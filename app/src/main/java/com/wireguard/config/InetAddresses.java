/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Utility methods for creating instances of {@link InetAddress}.
 */
public final class InetAddresses {
    private static final Method PARSER_METHOD;

    static {
        try {
            // This method is only present on Android.
            // noinspection JavaReflectionMemberAccess
            PARSER_METHOD = InetAddress.class.getMethod("parseNumericAddress", String.class);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private InetAddresses() {
        // Prevent instantiation.
    }

    /**
     * Parses a numeric IPv4 or IPv6 address without performing any DNS lookups.
     *
     * @param address a string representing the IP address
     * @return an instance of {@link Inet4Address} or {@link Inet6Address}, as appropriate
     */
    public static InetAddress parse(final String address) throws ParseException {
        if (address.isEmpty())
            throw new ParseException(InetAddress.class, address, "Empty address");
        try {
            return (InetAddress) PARSER_METHOD.invoke(null, address);
        } catch (final IllegalAccessException | InvocationTargetException e) {
            final Throwable cause = e.getCause();
            // Re-throw parsing exceptions with the original type, as callers might try to catch
            // them. On the other hand, callers cannot be expected to handle reflection failures.
            if (cause instanceof IllegalArgumentException)
                throw new ParseException(InetAddress.class, address, cause);
            throw new RuntimeException(e);
        }
    }
}
