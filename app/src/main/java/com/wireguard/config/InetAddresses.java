/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import android.os.Build;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Utility methods for creating instances of {@link InetAddress}.
 */
public final class InetAddresses {
    private static Method PARSER_METHOD;


    private static Method getParserMethod() {
        if (PARSER_METHOD != null)
                return PARSER_METHOD;
        try {
            // This method is only present on Android.
            // noinspection JavaReflectionMemberAccess
            PARSER_METHOD = InetAddress.class.getMethod("parseNumericAddress", String.class);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        return PARSER_METHOD;
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                return (InetAddress) getParserMethod().invoke(null, address);
            else
                return android.net.InetAddresses.parseNumericAddress(address);
        } catch (final IllegalAccessException | InvocationTargetException e) {
            final Throwable cause = e.getCause();
            // Re-throw parsing exceptions with the original type, as callers might try to catch
            // them. On the other hand, callers cannot be expected to handle reflection failures.
            if (cause instanceof IllegalArgumentException)
                throw new ParseException(InetAddress.class, address, cause);
            throw new RuntimeException(e);
        } catch (final IllegalArgumentException e) {
                throw new ParseException(InetAddress.class, address, e);
        }
    }
}
