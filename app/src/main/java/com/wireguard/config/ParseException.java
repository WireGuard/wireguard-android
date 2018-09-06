/*
 * Copyright © 2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.config;

/**
 * An exception representing a failure to parse an element of a WireGuard configuration. The context
 * for this failure can be retrieved with {@link #getContext}, and the text that failed to parse can
 * be retrieved with {@link #getText}.
 */
public class ParseException extends Exception {
    private final String context;
    private final CharSequence text;

    public ParseException(final String context, final CharSequence text, final String message) {
        super(message);
        this.context = context;
        this.text = text;
    }

    public ParseException(final String context, final CharSequence text, final Throwable cause) {
        super(cause.getMessage(), cause);
        this.context = context;
        this.text = text;
    }

    public ParseException(final String context, final CharSequence text) {
        this.context = context;
        this.text = text;
    }

    public String getContext() {
        return context;
    }

    public CharSequence getText() {
        return text;
    }
}
