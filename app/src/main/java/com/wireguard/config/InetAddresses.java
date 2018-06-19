/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.config;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;

public final class InetAddresses {
    private static final Method PARSER_METHOD;

    static {
        try {
            // This method is only present on Android.
            PARSER_METHOD = InetAddress.class.getMethod("parseNumericAddress", String.class);
        } catch (final NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private InetAddresses() {
        // Prevent instantiation.
    }

    @NonNull
    public static InetAddress parse(@Nullable final String address) {
        if (address == null || address.isEmpty())
            throw new IllegalArgumentException("Empty address");
        try {
            return (InetAddress) PARSER_METHOD.invoke(null, address);
        } catch (final IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
