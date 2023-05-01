/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.android.material.color.DynamicColors
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.android.configStore.FileConfigStore
import com.wireguard.android.model.TunnelManager
import com.wireguard.android.updater.Updater
import com.wireguard.android.util.RootShell
import com.wireguard.android.util.ToolsInstaller
import com.wireguard.android.util.UserKnobs
import com.wireguard.android.util.applicationScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.lang.ref.WeakReference
import java.util.Locale

class Application : android.app.Application() {
    private val futureBackend = CompletableDeferred<Backend>()
    private val coroutineScope = CoroutineScope(Job() + Dispatchers.Main.immediate)
    private var backend: Backend? = null
    private lateinit var rootShell: RootShell
    private lateinit var preferencesDataStore: DataStore<Preferences>
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
    }

    private suspend fun determineBackend(): Backend {
        var backend: Backend? = null
        if (UserKnobs.enableKernelModule.first() && WgQuickBackend.hasKernelSupport()) {
            try {
                rootShell.start()
                val wgQuickBackend = WgQuickBackend(applicationContext, rootShell, toolsInstaller)
                wgQuickBackend.setMultipleTunnels(UserKnobs.multipleTunnels.first())
                backend = wgQuickBackend
                UserKnobs.multipleTunnels.onEach {
                    wgQuickBackend.setMultipleTunnels(it)
                }.launchIn(coroutineScope)
            } catch (ignored: Exception) {
            }
        }
        if (backend == null) {
            backend = GoBackend(applicationContext)
            GoBackend.setAlwaysOnCallback { get().applicationScope.launch { get().tunnelManager.restoreState(true) } }
        }
        return backend
    }

    override fun onCreate() {
        Log.i(TAG, USER_AGENT)
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        rootShell = RootShell(applicationContext)
        toolsInstaller = ToolsInstaller(applicationContext, rootShell)
        preferencesDataStore = PreferenceDataStoreFactory.create { applicationContext.preferencesDataStoreFile("settings") }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            runBlocking {
                AppCompatDelegate.setDefaultNightMode(if (UserKnobs.darkTheme.first()) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
            }
            UserKnobs.darkTheme.onEach {
                val newMode = if (it) {
                    AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    AppCompatDelegate.MODE_NIGHT_NO
                }
                if (AppCompatDelegate.getDefaultNightMode() != newMode) {
                    AppCompatDelegate.setDefaultNightMode(newMode)
                }
            }.launchIn(coroutineScope)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        tunnelManager = TunnelManager(FileConfigStore(applicationContext))
        tunnelManager.onCreate()
        coroutineScope.launch(Dispatchers.IO) {
            try {
                backend = determineBackend()
                futureBackend.complete(backend!!)
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
        Updater.monitorForUpdates()

        if (BuildConfig.DEBUG) {
            StrictMode.setVmPolicy(VmPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setThreadPolicy(ThreadPolicy.Builder().detectAll().penaltyLog().build())
        }
    }

    override fun onTerminate() {
        coroutineScope.cancel()
        super.onTerminate()
    }

    companion object {
        val USER_AGENT = String.format(Locale.ENGLISH, "WireGuard/%s (Android %d; %s; %s; %s %s; %s)", BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, if (Build.SUPPORTED_ABIS.isNotEmpty()) Build.SUPPORTED_ABIS[0] else "unknown ABI", Build.BOARD, Build.MANUFACTURER, Build.MODEL, Build.FINGERPRINT)
        private const val TAG = "WireGuard/Application"
        private lateinit var weakSelf: WeakReference<Application>

        fun get(): Application {
            return weakSelf.get()!!
        }

        suspend fun getBackend() = get().futureBackend.await()

        fun getRootShell() = get().rootShell

        fun getPreferencesDataStore() = get().preferencesDataStore

        fun getToolsInstaller() = get().toolsInstaller

        fun getTunnelManager() = get().tunnelManager

        fun getCoroutineScope() = get().coroutineScope
    }

    init {
        weakSelf = WeakReference(this)
    }
}
