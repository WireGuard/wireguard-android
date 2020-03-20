/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.activity

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.SparseArray
import android.view.MenuItem
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.android.util.ModuleLoader
import java.util.ArrayList
import java.util.Arrays

/**
 * Interface for changing application-global persistent settings.
 */
class SettingsActivity : ThemeChangeAwareActivity() {
    private val permissionRequestCallbacks = SparseArray<PermissionRequestCallback>()
    private var permissionRequestCounter = 0

    fun ensurePermissions(permissions: Array<String>, cb: PermissionRequestCallback) {
        val needPermissions: MutableList<String> = ArrayList(permissions.size)
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED) {
                needPermissions.add(it)
            }
        }
        if (needPermissions.isEmpty()) {
            val granted = IntArray(permissions.size)
            Arrays.fill(granted, PackageManager.PERMISSION_GRANTED)
            cb.done(permissions, granted)
            return
        }
        val idx = permissionRequestCounter++
        permissionRequestCallbacks.put(idx, cb)
        ActivityCompat.requestPermissions(this,
                needPermissions.toTypedArray(), idx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (supportFragmentManager.findFragmentById(android.R.id.content) == null) {
            supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, SettingsFragment())
                    .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        val f = permissionRequestCallbacks[requestCode]
        if (f != null) {
            permissionRequestCallbacks.remove(requestCode)
            f.done(permissions, grantResults)
        }
    }

    interface PermissionRequestCallback {
        fun done(permissions: Array<String>, grantResults: IntArray)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, key: String?) {
            addPreferencesFromResource(R.xml.preferences)
            val screen = preferenceScreen
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) screen.removePreference(preferenceManager.findPreference("dark_theme"))
            val wgQuickOnlyPrefs = arrayOf(
                    preferenceManager.findPreference("tools_installer"),
                    preferenceManager.findPreference("restore_on_boot"),
                    preferenceManager.findPreference<Preference>("multiple_tunnels")
            ).filterNotNull()
            wgQuickOnlyPrefs.forEach { it.isVisible = false }
            Application.getBackendAsync().thenAccept { backend ->
                if (backend is WgQuickBackend) {
                    wgQuickOnlyPrefs.forEach { it.isVisible = true }
                } else {
                    wgQuickOnlyPrefs.forEach { screen.removePreference(it) }
                }
            }
            val moduleInstaller = preferenceManager.findPreference<Preference>("module_downloader")
            val kernelModuleDisabler = preferenceManager.findPreference<Preference>("kernel_module_disabler")
            moduleInstaller?.isVisible = false
            if (ModuleLoader.isModuleLoaded()) {
                screen.removePreference(moduleInstaller)
            } else {
                screen.removePreference(kernelModuleDisabler)
                Application.getAsyncWorker().runAsync(Application.getRootShell()::start).whenComplete { _, e ->
                    if (e == null)
                        moduleInstaller?.isVisible = true
                    else
                        screen.removePreference(moduleInstaller)
                }
            }
        }
    }
}
