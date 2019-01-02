/*
 * Copyright © 2018-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import android.text.TextUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java9.util.Optional;

public final class Attribute {
    private static final Pattern LINE_PATTERN = Pattern.compile("(\\w+)\\s*=\\s*([^\\s#][^#]*)");
    private static final Pattern LIST_SEPARATOR = Pattern.compile("\\s*,\\s*");

    private final String key;
    private final String value;

    private Attribute(final String key, final String value) {
        this.key = key;
        this.value = value;
    }

    public static String join(final Iterable<?> values) {
        return TextUtils.join(", ", values);
    }

    public static Optional<Attribute> parse(final CharSequence line) {
        final Matcher matcher = LINE_PATTERN.matcher(line);
        if (!matcher.matches())
            return Optional.empty();
        return Optional.of(new Attribute(matcher.group(1), matcher.group(2)));
    }

    public static String[] split(final CharSequence value) {
        return LIST_SEPARATOR.split(value);
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
