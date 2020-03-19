/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.preference.Preference
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.activity.SettingsActivity
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.WgQuickBackend
import java9.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

class KernelModuleDisablerPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var state = if (Application.getBackend() is WgQuickBackend) State.ENABLED else State.DISABLED

    override fun getSummary() = context.getString(state.summaryResourceId)

    override fun getTitle() = context.getString(state.titleResourceId)

    @SuppressLint("ApplySharedPref")
    override fun onClick() {
        if (state == State.DISABLED) {
            setState(State.ENABLING)
            Application.getSharedPreferences().edit().putBoolean("disable_kernel_module", false).commit()
        } else if (state == State.ENABLED) {
            setState(State.DISABLING)
            Application.getSharedPreferences().edit().putBoolean("disable_kernel_module", true).commit()
        }
        Application.getAsyncWorker().runAsync {
            Application.getTunnelManager().tunnels.thenApply { observableTunnels ->
                val downings = observableTunnels.values().map { it.setState(Tunnel.State.DOWN).toCompletableFuture() }.toTypedArray()
                CompletableFuture.allOf(*downings).thenRun {
                    val restartIntent = Intent(context, SettingsActivity::class.java)
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    Application.get().startActivity(restartIntent)
                    exitProcess(0)
                }
            }.join()
        }
    }

    private fun setState(state: State) {
        if (this.state == state) return
        this.state = state
        if (isEnabled != state.shouldEnableView) isEnabled = state.shouldEnableView
        notifyChanged()
    }

    private enum class State(val titleResourceId: Int, val summaryResourceId: Int, val shouldEnableView: Boolean) {
        ENABLED(R.string.module_disabler_enabled_title, R.string.module_disabler_enabled_summary, true),
        DISABLED(R.string.module_disabler_disabled_title, R.string.module_disabler_disabled_summary, true),
        ENABLING(R.string.module_disabler_disabled_title, R.string.success_application_will_restart, false),
        DISABLING(R.string.module_disabler_enabled_title, R.string.success_application_will_restart, false);
    }
}
