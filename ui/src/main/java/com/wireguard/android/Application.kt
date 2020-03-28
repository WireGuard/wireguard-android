/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.AsyncTask
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.android.configStore.FileConfigStore
import com.wireguard.android.model.TunnelManager
import com.wireguard.android.util.AsyncWorker
import com.wireguard.android.util.ExceptionLoggers
import com.wireguard.android.util.ModuleLoader
import com.wireguard.android.util.RootShell
import com.wireguard.android.util.ToolsInstaller
import java9.util.concurrent.CompletableFuture
import java.lang.ref.WeakReference
import java.util.Locale

class Application : android.app.Application(), OnSharedPreferenceChangeListener {
    private val futureBackend = CompletableFuture<Backend>()
    private lateinit var asyncWorker: AsyncWorker
    private var backend: Backend? = null
    private lateinit var moduleLoader: ModuleLoader
    private lateinit var rootShell: RootShell
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var toolsInstaller: ToolsInstaller
    private lateinit var tunnelManager: TunnelManager

    override fun attachBaseContext(context: Context) {
        super.attachBaseContext(context)
        if (BuildConfig.MIN_SDK_VERSION > Build.VERSION.SDK_INT) {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            System.exit(0)
        }
        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(VmPolicy.Builder().detectAll().penaltyLog().build())
        }
    }

    override fun onCreate() {
        Log.i(TAG, USER_AGENT)
        super.onCreate()
        asyncWorker = AsyncWorker(AsyncTask.SERIAL_EXECUTOR, Handler(Looper.getMainLooper()))
        rootShell = RootShell(applicationContext)
        toolsInstaller = ToolsInstaller(applicationContext, rootShell)
        moduleLoader = ModuleLoader(applicationContext, rootShell, USER_AGENT)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            AppCompatDelegate.setDefaultNightMode(
                    if (sharedPreferences.getBoolean("dark_theme", false)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        tunnelManager = TunnelManager(FileConfigStore(applicationContext))
        tunnelManager.onCreate()
        asyncWorker.supplyAsync(Companion::getBackend).thenAccept { futureBackend.complete(it) }
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if ("multiple_tunnels" == key && backend != null && backend is WgQuickBackend)
            (backend as WgQuickBackend).setMultipleTunnels(sharedPreferences.getBoolean(key, false))
    }

    override fun onTerminate() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onTerminate()
    }

    companion object {
        val USER_AGENT = String.format(Locale.ENGLISH, "WireGuard/%s (Android %d; %s; %s; %s %s; %s)", BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "unknown ABI", Build.BOARD, Build.MANUFACTURER, Build.MODEL, Build.FINGERPRINT)
        private val TAG = "WireGuard/${Application::class.simpleName!!}"
        private lateinit var weakSelf: WeakReference<Application>

        @JvmStatic
        fun get(): Application {
            return weakSelf.get()!!
        }

        @JvmStatic
        fun getAsyncWorker() = get().asyncWorker

        @JvmStatic
        fun getBackend(): Backend {
            val app = get()
            synchronized(app.futureBackend) {
                if (app.backend == null) {
                    var backend: Backend? = null
                    var didStartRootShell = false
                    if (!ModuleLoader.isModuleLoaded() && app.moduleLoader.moduleMightExist()) {
                        try {
                            app.rootShell.start()
                            didStartRootShell = true
                            app.moduleLoader.loadModule()
                        } catch (ignored: Exception) {
                        }
                    }
                    if (!app.sharedPreferences.getBoolean("disable_kernel_module", false) && ModuleLoader.isModuleLoaded()) {
                        try {
                            if (!didStartRootShell)
                                app.rootShell.start()
                            val wgQuickBackend = WgQuickBackend(app.applicationContext, app.rootShell, app.toolsInstaller)
                            wgQuickBackend.setMultipleTunnels(app.sharedPreferences.getBoolean("multiple_tunnels", false))
                            backend = wgQuickBackend
                        } catch (ignored: Exception) {
                        }
                    }
                    if (backend == null) {
                        backend = GoBackend(app.applicationContext)
                        GoBackend.setAlwaysOnCallback { get().tunnelManager.restoreState(true).whenComplete(ExceptionLoggers.D) }
                    }
                    app.backend = backend
                }
                return app.backend!!
            }
        }

        @JvmStatic
        fun getBackendAsync() = get().futureBackend

        @JvmStatic
        fun getModuleLoader() = get().moduleLoader

        @JvmStatic
        fun getRootShell() = get().rootShell

        @JvmStatic
        fun getSharedPreferences() = get().sharedPreferences

        @JvmStatic
        fun getToolsInstaller() = get().toolsInstaller

        @JvmStatic
        fun getTunnelManager() = get().tunnelManager
    }

    init {
        weakSelf = WeakReference(this)
    }
}
