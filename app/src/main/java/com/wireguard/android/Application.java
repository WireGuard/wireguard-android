/*
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatDelegate;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.WgQuickBackend;
import com.wireguard.android.configStore.ConfigStore;
import com.wireguard.android.configStore.FileConfigStore;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.AsyncWorker;
import com.wireguard.android.util.RootShell;
import com.wireguard.android.util.ToolsInstaller;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;

public class Application extends android.app.Application {
    private static WeakReference<Application> weakSelf;
    private AsyncWorker asyncWorker;
    private Backend backend;
    private RootShell rootShell;
    private SharedPreferences sharedPreferences;
    private ToolsInstaller toolsInstaller;
    private TunnelManager tunnelManager;
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
        return get().backend;
    }

    public static Class getBackendType() {
        return get().backend.getClass();
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
    public void onCreate() {
        super.onCreate();

        final Executor executor = AsyncTask.SERIAL_EXECUTOR;
        final Handler handler = new Handler(Looper.getMainLooper());
        final ConfigStore configStore = new FileConfigStore(getApplicationContext());

        asyncWorker = new AsyncWorker(executor, handler);
        rootShell = new RootShell(getApplicationContext());
        toolsInstaller = new ToolsInstaller(getApplicationContext());

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        AppCompatDelegate.setDefaultNightMode(
                sharedPreferences.getBoolean("dark_theme", false) ?
                        AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        if (new File("/sys/module/wireguard").exists()) {
            try {
                rootShell.start();
                backend = new WgQuickBackend(getApplicationContext());
            } catch (final Exception ignored) { }
        }
        if (backend == null)
            backend = new GoBackend(getApplicationContext());

        tunnelManager = new TunnelManager(backend, configStore);
        tunnelManager.onCreate();
    }
}
