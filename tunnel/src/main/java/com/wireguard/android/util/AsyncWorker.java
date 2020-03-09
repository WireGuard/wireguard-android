/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util;

import android.os.Handler;

import java.util.concurrent.Executor;

import java9.util.concurrent.CompletableFuture;
import java9.util.concurrent.CompletionStage;

/**
 * Helper class for running asynchronous tasks and ensuring they are completed on the main thread.
 */

public class AsyncWorker {
    private final Executor executor;
    private final Handler handler;

    public AsyncWorker(final Executor executor, final Handler handler) {
        this.executor = executor;
        this.handler = handler;
    }

    public CompletionStage<Void> runAsync(final AsyncRunnable<?> runnable) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                runnable.run();
                handler.post(() -> future.complete(null));
            } catch (final Throwable t) {
                handler.post(() -> future.completeExceptionally(t));
            }
        });
        return future;
    }

    public <T> CompletionStage<T> supplyAsync(final AsyncSupplier<T, ?> supplier) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                final T result = supplier.get();
                handler.post(() -> future.complete(result));
            } catch (final Throwable t) {
                handler.post(() -> future.completeExceptionally(t));
            }
        });
        return future;
    }

    @FunctionalInterface
    public interface AsyncRunnable<E extends Throwable> {
        void run() throws E;
    }

    @FunctionalInterface
    public interface AsyncSupplier<T, E extends Throwable> {
        T get() throws E;
    }
}
