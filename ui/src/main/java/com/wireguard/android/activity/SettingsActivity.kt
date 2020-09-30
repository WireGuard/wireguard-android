/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.android.preference.PreferencesPreferenceDataStore
import com.wireguard.android.util.AdminKnobs
import com.wireguard.android.util.ModuleLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Interface for changing application-global persistent settings.
 */
class SettingsActivity : ThemeChangeAwareActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (supportFragmentManager.findFragmentById(android.R.id.content) == null) {
            supportFragmentManager.commit {
                add(android.R.id.content, SettingsFragment())
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, key: String?) {
            preferenceManager.preferenceDataStore = PreferencesPreferenceDataStore(lifecycleScope, Application.getPreferencesDataStore())
            addPreferencesFromResource(R.xml.preferences)
            preferenceScreen.initialExpandedChildrenCount = 4
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val darkTheme = preferenceManager.findPreference<Preference>("dark_theme")
                darkTheme?.parent?.removePreference(darkTheme)
                --preferenceScreen.initialExpandedChildrenCount
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                val remoteApps = preferenceManager.findPreference<Preference>("allow_remote_control_intents")
                remoteApps?.parent?.removePreference(remoteApps)
            }
            if (AdminKnobs.disableConfigExport) {
                val zipExporter = preferenceManager.findPreference<Preference>("zip_exporter")
                zipExporter?.parent?.removePreference(zipExporter)
            }
            val wgQuickOnlyPrefs = arrayOf(
                    preferenceManager.findPreference("tools_installer"),
                    preferenceManager.findPreference("restore_on_boot"),
                    preferenceManager.findPreference<Preference>("multiple_tunnels")
            ).filterNotNull()
            wgQuickOnlyPrefs.forEach { it.isVisible = false }
            lifecycleScope.launch {
                if (Application.getBackend() is WgQuickBackend) {
                    ++preferenceScreen.initialExpandedChildrenCount
                    wgQuickOnlyPrefs.forEach { it.isVisible = true }
                } else {
                    wgQuickOnlyPrefs.forEach { it.parent?.removePreference(it) }
                }
            }
            preferenceManager.findPreference<Preference>("log_viewer")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), LogViewerActivity::class.java))
                true
            }
            val moduleInstaller = preferenceManager.findPreference<Preference>("module_downloader")
            val kernelModuleDisabler = preferenceManager.findPreference<Preference>("kernel_module_disabler")
            moduleInstaller?.isVisible = false
            if (ModuleLoader.isModuleLoaded()) {
                moduleInstaller?.parent?.removePreference(moduleInstaller)
                lifecycleScope.launch {
                    if (Application.getBackend() !is WgQuickBackend) {
                        try {
                            withContext(Dispatchers.IO) { Application.getRootShell().start() }
                        } catch (_: Throwable) {
                            kernelModuleDisabler?.parent?.removePreference(kernelModuleDisabler)
                        }
                    }
                }
            } else {
                kernelModuleDisabler?.parent?.removePreference(kernelModuleDisabler)
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) { Application.getRootShell().start() }
                        moduleInstaller?.isVisible = true
                    } catch (_: Throwable) {
                        moduleInstaller?.parent?.removePreference(moduleInstaller)
                    }
                }
            }
        }
    }
}
