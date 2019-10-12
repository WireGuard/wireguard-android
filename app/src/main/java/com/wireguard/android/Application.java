/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.WgQuickBackend;
import com.wireguard.android.configStore.FileConfigStore;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.AsyncWorker;
import com.wireguard.android.util.RootShell;
import com.wireguard.android.util.ToolsInstaller;

import java.io.File;
import java.lang.ref.WeakReference;

import java9.util.concurrent.CompletableFuture;

public class Application extends android.app.Application {
    @SuppressWarnings("NullableProblems") private static WeakReference<Application> weakSelf;
    private final CompletableFuture<Backend> futureBackend = new CompletableFuture<>();
    @SuppressWarnings("NullableProblems") private AsyncWorker asyncWorker;
    @Nullable private Backend backend;
    @SuppressWarnings("NullableProblems") private RootShell rootShell;
    @SuppressWarnings("NullableProblems") private SharedPreferences sharedPreferences;
    @SuppressWarnings("NullableProblems") private ToolsInstaller toolsInstaller;
    @SuppressWarnings("NullableProblems") private TunnelManager tunnelManager;

    public Application() {
        weakSelf = new WeakReference<>(this);
    }

    public static Application get() {
        return weakSelf.get();
    }

    public static AsyncWorker getAsyncWorker() {
        return get().asyncWorker;
    }

    public static Backend getBackend() {
        final Application app = get();
        synchronized (app.futureBackend) {
            if (app.backend == null) {
                Backend backend = null;
                if (new File("/sys/module/wireguard").exists()) {
                    try {
                        app.rootShell.start();
                        backend = new WgQuickBackend(app.getApplicationContext());
                    } catch (final Exception ignored) {
                    }
                }
                if (backend == null)
                    backend = new GoBackend(app.getApplicationContext());
                app.backend = backend;
            }
            return app.backend;
        }
    }

    public static CompletableFuture<Backend> getBackendAsync() {
        return get().futureBackend;
    }

    public static RootShell getRootShell() {
        return get().rootShell;
    }

    public static SharedPreferences getSharedPreferences() {
        return get().sharedPreferences;
    }

    public static ToolsInstaller getToolsInstaller() {
        return get().toolsInstaller;
    }

    public static TunnelManager getTunnelManager() {
        return get().tunnelManager;
    }

    @Override
    protected void attachBaseContext(final Context context) {
        super.attachBaseContext(context);

        if (BuildConfig.MIN_SDK_VERSION > Build.VERSION.SDK_INT) {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            System.exit(0);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        asyncWorker = new AsyncWorker(AsyncTask.SERIAL_EXECUTOR, new Handler(Looper.getMainLooper()));
        rootShell = new RootShell(getApplicationContext());
        toolsInstaller = new ToolsInstaller(getApplicationContext());

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            AppCompatDelegate.setDefaultNightMode(
                    sharedPreferences.getBoolean("dark_theme", false) ?
                            AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }

        tunnelManager = new TunnelManager(new FileConfigStore(getApplicationContext()));
        tunnelManager.onCreate();

        asyncWorker.supplyAsync(Application::getBackend).thenAccept(futureBackend::complete);
    }
}
