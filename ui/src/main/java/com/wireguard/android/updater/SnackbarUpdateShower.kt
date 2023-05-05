/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.updater

import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.wireguard.android.BuildConfig
import com.wireguard.android.R
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.util.QuantityFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

object SnackbarUpdateShower {
    private class SwapableSnackbar(activity: FragmentActivity, view: View, anchor: View?) {
        val actionSnackbar = makeSnackbar(activity, view, anchor)
        val statusSnackbar = makeSnackbar(activity, view, anchor)
        var showingAction: Boolean = false
        var showingStatus: Boolean = false

        private fun makeSnackbar(activity: FragmentActivity, view: View, anchor: View?): Snackbar {
            val snackbar = Snackbar.make(activity, view, "", Snackbar.LENGTH_INDEFINITE)
            if (anchor != null)
                snackbar.anchorView = anchor
            snackbar.setTextMaxLines(6)
            snackbar.behavior = object : BaseTransientBottomBar.Behavior() {
                override fun canSwipeDismissView(child: View): Boolean {
                    return false
                }
            }
            snackbar.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(snackbar: Snackbar?, @DismissEvent event: Int) {
                    super.onDismissed(snackbar, event)
                    if (event == DISMISS_EVENT_MANUAL || event == DISMISS_EVENT_ACTION ||
                        (snackbar == actionSnackbar && !showingAction) || (snackbar == statusSnackbar && !showingStatus)
                    )
                        return
                    activity.lifecycleScope.launch {
                        delay(5.seconds)
                        snackbar?.show()
                    }
                }
            })
            return snackbar
        }

        fun showAction(text: String, action: String, listener: View.OnClickListener) {
            if (showingStatus) {
                showingStatus = false
                statusSnackbar.dismiss()
            }
            actionSnackbar.setText(text)
            actionSnackbar.setAction(action, listener)
            if (!showingAction) {
                actionSnackbar.show()
                showingAction = true
            }
        }

        fun showText(text: String) {
            if (showingAction) {
                showingAction = false
                actionSnackbar.dismiss()
            }
            statusSnackbar.setText(text)
            if (!showingStatus) {
                statusSnackbar.show()
                showingStatus = true
            }
        }

        fun dismiss() {
            actionSnackbar.dismiss()
            statusSnackbar.dismiss()
            showingAction = false
            showingStatus = false
        }
    }

    fun attachToActivity(activity: FragmentActivity, view: View, anchor: View?) {
        if (BuildConfig.IS_GOOGLE_PLAY)
            return

        val snackbar = SwapableSnackbar(activity, view, anchor)
        val context = activity.applicationContext

        var lastUserIntervention: Updater.Progress.NeedsUserIntervention? = null
        val intentLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            lastUserIntervention?.markAsDone()
        }

        Updater.state.onEach { progress ->
            when (progress) {
                is Updater.Progress.Complete ->
                    snackbar.dismiss()

                is Updater.Progress.Available ->
                    snackbar.showAction(context.getString(R.string.updater_avalable), context.getString(R.string.updater_action)) {
                        progress.update()
                    }

                is Updater.Progress.NeedsUserIntervention -> {
                    lastUserIntervention = progress
                    intentLauncher.launch(progress.intent)
                }

                is Updater.Progress.Installing ->
                    snackbar.showText(context.getString(R.string.updater_installing))

                is Updater.Progress.Rechecking ->
                    snackbar.showText(context.getString(R.string.updater_rechecking))

                is Updater.Progress.Downloading -> {
                    if (progress.bytesTotal != 0UL) {
                        snackbar.showText(
                            context.getString(
                                R.string.updater_download_progress,
                                QuantityFormatter.formatBytes(progress.bytesDownloaded.toLong()),
                                QuantityFormatter.formatBytes(progress.bytesTotal.toLong()),
                                progress.bytesDownloaded.toFloat() * 100.0 / progress.bytesTotal.toFloat()
                            )
                        )
                    } else {
                        snackbar.showText(
                            context.getString(
                                R.string.updater_download_progress_nototal,
                                QuantityFormatter.formatBytes(progress.bytesDownloaded.toLong())
                            )
                        )
                    }
                }

                is Updater.Progress.Failure -> {
                    snackbar.showText( context.getString(R.string.updater_failure, ErrorMessages[progress.error]))
                    delay(5.seconds)
                    progress.retry()
                }
            }
        }.launchIn(activity.lifecycleScope)
    }
}