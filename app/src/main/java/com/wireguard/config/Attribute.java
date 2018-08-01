/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import android.annotation.SuppressLint;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The set of valid attributes for an interface or peer in a WireGuard configuration file.
 */

public enum Attribute {
    ADDRESS("Address"),
    ALLOWED_IPS("AllowedIPs"),
    DNS("DNS"),
    EXCLUDED_APPLICATIONS("ExcludedApplications"),
    ENDPOINT("Endpoint"),
    LISTEN_PORT("ListenPort"),
    MTU("MTU"),
    PERSISTENT_KEEPALIVE("PersistentKeepalive"),
    PRESHARED_KEY("PresharedKey"),
    PRIVATE_KEY("PrivateKey"),
    PUBLIC_KEY("PublicKey");

    private static final String[] EMPTY_LIST = new String[0];
    private static final Map<String, Attribute> KEY_MAP;
    private static final Pattern LIST_SEPARATOR_PATTERN = Pattern.compile("\\s*,\\s*");
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("\\s|=");

    static {
        KEY_MAP = new HashMap<>(Attribute.values().length);
        for (final Attribute key : Attribute.values()) {
            KEY_MAP.put(key.token.toLowerCase(), key);
        }
    }

    private final Pattern pattern;
    private final String token;

    Attribute(final String token) {
        pattern = Pattern.compile(token + "\\s*=\\s*(\\S.*)");
        this.token = token;
    }

    public static <T> String iterableToString(final Iterable<T> iterable) {
        return TextUtils.join(", ", iterable);
    }

    @Nullable
    public static Attribute match(final CharSequence line) {
        return KEY_MAP.get(SEPARATOR_PATTERN.split(line)[0].toLowerCase());
    }

    public static String[] stringToList(@Nullable final String string) {
        if (TextUtils.isEmpty(string))
            return EMPTY_LIST;
        return LIST_SEPARATOR_PATTERN.split(string.trim());
    }

    @SuppressLint("DefaultLocale")
    public String composeWith(@Nullable final Object value) {
        return String.format("%s = %s%n", token, value);
    }

    @SuppressLint("DefaultLocale")
    public String composeWith(final int value) {
        return String.format("%s = %d%n", token, value);
    }

    public <T> String composeWith(final Iterable<T> value) {
        return String.format("%s = %s%n", token, iterableToString(value));
    }

    @Nullable
    public String parse(final CharSequence line) {
        final Matcher matcher = pattern.matcher(line);
        return matcher.matches() ? matcher.group(1) : null;
    }

    @Nullable
    public String[] parseList(final CharSequence line) {
        final Matcher matcher = pattern.matcher(line);
        return matcher.matches() ? stringToList(matcher.group(1)) : null;
    }
}
