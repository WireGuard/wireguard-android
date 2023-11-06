/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

import com.wireguard.util.NonNullForAll;

import androidx.annotation.Nullable;

/**
 *
 */
@NonNullForAll
public class ParseException extends Exception {
    private final Class<?> parsingClass;
    private final CharSequence text;

    public ParseException(final Class<?> parsingClass, final CharSequence text,
                          @Nullable final String message, @Nullable final Throwable cause) {
        super(message, cause);
        this.parsingClass = parsingClass;
        this.text = text;
    }

    public ParseException(final Class<?> parsingClass, final CharSequence text,
                          @Nullable final String message) {
        this(parsingClass, text, message, null);
    }

    public ParseException(final Class<?> parsingClass, final CharSequence text,
                          @Nullable final Throwable cause) {
        this(parsingClass, text, null, cause);
    }

    public ParseException(final Class<?> parsingClass, final CharSequence text) {
        this(parsingClass, text, null, null);
    }

    public Class<?> getParsingClass() {
        return parsingClass;
    }

    public CharSequence getText() {
        return text;
    }
}
