/*
 * Copyright © 2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.preference

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.system.OsConstants
import android.util.AttributeSet
import android.widget.Toast
import androidx.preference.Preference
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.activity.SettingsActivity
import com.wireguard.android.util.ErrorMessages
import kotlin.system.exitProcess

class ModuleDownloaderPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var state = State.INITIAL

    override fun getSummary() = context.getString(state.messageResourceId)

    override fun getTitle() = context.getString(R.string.module_installer_title)

    override fun onClick() {
        setState(State.WORKING)
        Application.getAsyncWorker().supplyAsync(Application.getModuleLoader()::download).whenComplete(this::onDownloadResult)
    }

    @SuppressLint("ApplySharedPref")
    private fun onDownloadResult(result: Int, throwable: Throwable?) {
        when {
            throwable != null -> {
                setState(State.FAILURE)
                Toast.makeText(context, ErrorMessages.get(throwable), Toast.LENGTH_LONG).show()
            }
            result == OsConstants.ENOENT -> setState(State.NOTFOUND)
            result == OsConstants.EXIT_SUCCESS -> {
                setState(State.SUCCESS)
                Application.getSharedPreferences().edit().remove("disable_kernel_module").commit()
                Application.getAsyncWorker().runAsync {
                    val restartIntent = Intent(context, SettingsActivity::class.java)
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    Application.get().startActivity(restartIntent)
                    exitProcess(0)
                }
            }
            else -> setState(State.FAILURE)
        }
    }

    private fun setState(state: State) {
        if (this.state == state) return
        this.state = state
        if (isEnabled != state.shouldEnableView) isEnabled = state.shouldEnableView
        notifyChanged()
    }

    private enum class State(val messageResourceId: Int, val shouldEnableView: Boolean) {
        INITIAL(R.string.module_installer_initial, true),
        FAILURE(R.string.module_installer_error, true),
        WORKING(R.string.module_installer_working, false),
        SUCCESS(R.string.success_application_will_restart, false),
        NOTFOUND(R.string.module_installer_not_found, false);
    }
}
