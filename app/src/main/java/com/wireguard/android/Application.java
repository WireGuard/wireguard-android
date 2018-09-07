/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatDelegate;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.WgQuickBackend;
import com.wireguard.android.configStore.FileConfigStore;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.AsyncWorker;
import com.wireguard.android.util.RootShell;
import com.wireguard.android.util.ToolsInstaller;

import org.acra.ACRA;
import org.acra.annotation.AcraCore;
import org.acra.annotation.AcraHttpSender;
import org.acra.data.StringFormat;
import org.acra.sender.HttpSender;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.ref.WeakReference;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import java9.util.concurrent.CompletableFuture;


@AcraCore(reportFormat = StringFormat.JSON,
        buildConfigClass = BuildConfig.class,
        logcatArguments = {"-b", "all", "-d", "-v", "threadtime", "*:V"},
        excludeMatchingSharedPreferencesKeys = {"last_used_tunnel", "enabled_configs"})
@AcraHttpSender(uri = "https://crashreport.zx2c4.com/android/report",
        basicAuthLogin = "6RCovLxEVCTXGiW5",
        basicAuthPassword = "O7I3sVa5ULVdiC51",
        httpMethod = HttpSender.Method.POST,
        compress = true)
public class Application extends android.app.Application {
    @SuppressWarnings("NullableProblems") private static WeakReference<Application> weakSelf;
    @SuppressWarnings("NullableProblems") private AsyncWorker asyncWorker;
    @SuppressWarnings("NullableProblems") private RootShell rootShell;
    @SuppressWarnings("NullableProblems") private SharedPreferences sharedPreferences;
    @SuppressWarnings("NullableProblems") private ToolsInstaller toolsInstaller;
    @SuppressWarnings("NullableProblems") private TunnelManager tunnelManager;
    @Nullable private Backend backend;
    private final CompletableFuture<Backend> futureBackend = new CompletableFuture<>();

    public Application() {
        weakSelf = new WeakReference<>(this);
    }

    /* The ACRA password can be trivially reverse engineered and is open source anyway,
     * so there's no point in trying to protect it. However, we do want to at least
     * prevent innocent self-builders from uploading stuff to our crash reporter. So, we
     * check the DN of the certs that signed the apk, without even bothering to try
     * validating that they're authentic. It's a good enough heuristic.
     */
    @SuppressWarnings("deprecation")
    @Nullable
    private static String getInstallSource(final Context context) {
        if (BuildConfig.DEBUG)
            return null;
        try {
            final CertificateFactory cf = CertificateFactory.getInstance("X509");
            for (final Signature sig : context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES).signatures) {
                try {
                    for (final String category : ((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(sig.toByteArray()))).getSubjectDN().getName().split(", *")) {
                        final String[] parts = category.split("=", 2);
                        if (!"O".equals(parts[0]))
                            continue;
                        switch (parts[1]) {
                            case "Google Inc.":
                                return "Play Store";
                            case "fdroid.org":
                                return "F-Droid";
                        }
                    }
                } catch (final Exception ignored) { }
            }
        } catch (final Exception ignored) { }
        return null;
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

        final String installSource = getInstallSource(context);
        if (installSource != null) {
            ACRA.init(this);
            ACRA.getErrorReporter().putCustomData("installSource", installSource);
        }
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
    public void onCreate() {
        super.onCreate();

        asyncWorker = new AsyncWorker(AsyncTask.SERIAL_EXECUTOR, new Handler(Looper.getMainLooper()));
        rootShell = new RootShell(getApplicationContext());
        toolsInstaller = new ToolsInstaller(getApplicationContext());

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        AppCompatDelegate.setDefaultNightMode(
                sharedPreferences.getBoolean("dark_theme", false) ?
                        AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        tunnelManager = new TunnelManager(new FileConfigStore(getApplicationContext()));
        tunnelManager.onCreate();

        asyncWorker.supplyAsync(Application::getBackend).thenAccept(backend -> {
            futureBackend.complete(backend);
            if (ACRA.isInitialised()) {
                ACRA.getErrorReporter().putCustomData("backend", backend.getClass().getSimpleName());
                asyncWorker.supplyAsync(backend::getVersion).thenAccept(version ->
                        ACRA.getErrorReporter().putCustomData("backendVersion", version));
            }
        });
    }
}
