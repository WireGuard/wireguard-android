/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util;

import android.support.annotation.Nullable;
import android.util.Log;

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
        return throwable;
    }

    public static String unwrapMessage(Throwable throwable) {
        throwable = unwrap(throwable);
        final String message = throwable.getMessage();
        if (message != null)
            return message;
        return throwable.getClass().getSimpleName();
    }

    @Override
    public void accept(final Object result, @Nullable final Throwable throwable) {
        if (throwable != null)
            Log.println(Log.ERROR, TAG, Log.getStackTraceString(throwable));
        else if (priority <= Log.DEBUG)
            Log.println(priority, TAG, "Future completed successfully");
    }
}
