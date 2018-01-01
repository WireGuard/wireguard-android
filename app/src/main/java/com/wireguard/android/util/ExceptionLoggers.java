package com.wireguard.android.util;

import android.util.Log;

import java9.util.concurrent.CompletionException;
import java9.util.function.BiConsumer;

/**
 * Helpers for logging exceptions from asynchronous tasks. These can be passed to
 * {@code CompletionStage.handle()} at the end of an asynchronous future chain.
 */

public enum ExceptionLoggers implements BiConsumer<Object, Throwable> {
    D(Log.DEBUG),
    E(Log.ERROR);

    private static final String TAG = ExceptionLoggers.class.getSimpleName();

    private final int priority;

    ExceptionLoggers(final int priority) {
        this.priority = priority;
    }

    public static Throwable unwrap(final Throwable throwable) {
        if (throwable instanceof CompletionException)
            return throwable.getCause();
        return throwable;
    }

    @Override
    public void accept(final Object result, final Throwable throwable) {
        if (throwable != null)
            Log.println(Log.ERROR, TAG, Log.getStackTraceString(throwable));
        else if (priority <= Log.DEBUG)
            Log.println(priority, TAG, "Future completed successfully");
    }
}
