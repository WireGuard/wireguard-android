/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.jimberisolation.android.activity

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.jimberisolation.android.Application
import com.jimberisolation.android.Application.Companion.getTunnelManager
import com.jimberisolation.android.QuickTileService
import com.jimberisolation.android.R
import com.jimberisolation.android.backend.WgQuickBackend
import com.jimberisolation.android.preference.PreferencesPreferenceDataStore
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.AdminKnobs
import com.jimberisolation.android.util.lifecycleScope
import com.jimberisolation.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Interface for changing application-global persistent settings.
 */
class SettingsActivity : AppCompatActivity() {
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

            val clearCachePreference: Preference? = findPreference("clear_cache")
            val changePublicIpPreference: Preference? = findPreference("change_public_ip")

            // Set an onClick listener
            clearCachePreference?.setOnPreferenceClickListener {
                lifecycleScope.launch {
                    SharedStorage.getInstance().clearAll()
                    val tunnels = getTunnelManager().getTunnels()
                    tunnels.forEach { tunnel ->
                        tunnel.deleteAsync()
                    }
                }

                Toast.makeText(activity ?: Application.get(), "Cache cleared", Toast.LENGTH_SHORT).show()
                true
            }

            // Set an onClick listener
            changePublicIpPreference?.setOnPreferenceClickListener {
                val manager = getTunnelManager();

                lifecycleScope.launch {
                    val tunnel = manager.getTunnels().first();

                    val currentConfig = tunnel.getConfigAsync()
                    val currentConfigString = currentConfig.toWgQuickString();

                    val oldPublicIp = currentConfig.peers.first().endpoint.get().host;
                    val newPublicIp = "1.1.1.1";

                    val updatedConfigString = currentConfigString.replace(Regex("(?<=Endpoint = )$oldPublicIp"), newPublicIp)

                    val updatedConfig = Config.parse(ByteArrayInputStream(updatedConfigString.toByteArray(StandardCharsets.UTF_8)))
                    tunnel.setConfigAsync(updatedConfig);
                }

                true
            }


            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || QuickTileService.isAdded) {
                val quickTile = preferenceManager.findPreference<Preference>("quick_tile")
                quickTile?.parent?.removePreference(quickTile)
                --preferenceScreen.initialExpandedChildrenCount
            }
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
            val kernelModuleEnabler = preferenceManager.findPreference<Preference>("kernel_module_enabler")
            if (WgQuickBackend.hasKernelSupport()) {
                lifecycleScope.launch {
                    if (Application.getBackend() !is WgQuickBackend) {
                        try {
                            withContext(Dispatchers.IO) { Application.getRootShell().start() }
                        } catch (_: Throwable) {
                            kernelModuleEnabler?.parent?.removePreference(kernelModuleEnabler)
                        }
                    }
                }
            } else {
                kernelModuleEnabler?.parent?.removePreference(kernelModuleEnabler)
            }
        }
    }
}
