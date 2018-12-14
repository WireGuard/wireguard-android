/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util;

import android.content.res.Resources;
import android.support.annotation.Nullable;
import android.util.Log;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.config.BadConfigException;
import com.wireguard.config.ParseException;
import com.wireguard.crypto.KeyFormatException;

import java9.util.concurrent.CompletionException;
import java9.util.function.BiConsumer;

/**
 * Helpers for logging exceptions from asynchronous tasks. These can be passed to
 * {@code CompletionStage.whenComplete()} at the end of an asynchronous future chain.
 */

public enum ExceptionLoggers implements BiConsumer<Object, Throwable> {
    D(Log.DEBUG),
    E(Log.ERROR);

    private static final String TAG = "WireGuard/" + ExceptionLoggers.class.getSimpleName();

    private final int priority;

    ExceptionLoggers(final int priority) {
        this.priority = priority;
    }

    public static Throwable unwrap(final Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null)
            return throwable.getCause();
        if (throwable instanceof ParseException && throwable.getCause() != null)
            return throwable.getCause();
        return throwable;
    }

    public static String unwrapMessage(final Throwable throwable) {
        final Throwable innerThrowable = unwrap(throwable);
        final Resources resources = Application.get().getResources();
        String message;
        if (innerThrowable instanceof BadConfigException) {
            final BadConfigException configException = (BadConfigException) innerThrowable;
            message = resources.getString(R.string.parse_error, configException.getText(), configException.getLocation());
            final Throwable cause = unwrap(configException);
            if (cause.getMessage() != null)
                message += ": " + cause.getMessage();
        } else if (innerThrowable instanceof KeyFormatException) {
            final KeyFormatException keyFormatException = (KeyFormatException) innerThrowable;
            switch (keyFormatException.getFormat()) {
                case BASE64:
                    message = resources.getString(R.string.key_length_base64_exception_message);
                    break;
                case BINARY:
                    message = resources.getString(R.string.key_length_exception_message);
                    break;
                case HEX:
                    message = resources.getString(R.string.key_length_hex_exception_message);
                    break;
                default:
                    // Will never happen, as getFormat is not nullable.
                    message = null;
            }
        } else {
            message = innerThrowable.getMessage();
        }
        return message != null ? message : innerThrowable.getClass().getSimpleName();
    }

    @Override
    public void accept(final Object result, @Nullable final Throwable throwable) {
        if (throwable != null)
            Log.println(Log.ERROR, TAG, Log.getStackTraceString(throwable));
        else if (priority <= Log.DEBUG)
            Log.println(priority, TAG, "Future completed successfully");
    }
}
