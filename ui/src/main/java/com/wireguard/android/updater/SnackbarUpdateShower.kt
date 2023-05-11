/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.updater

import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.wireguard.android.R
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.util.QuantityFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class SnackbarUpdateShower(private val fragment: Fragment) {
    private var lastUserIntervention: Updater.Progress.NeedsUserIntervention? = null
    private val intentLauncher = fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        lastUserIntervention?.markAsDone()
    }

    private class SwapableSnackbar(fragment: Fragment, view: View, anchor: View?) {
        private val actionSnackbar = makeSnackbar(fragment, view, anchor)
        private val statusSnackbar = makeSnackbar(fragment, view, anchor)
        private var showingAction: Boolean = false
        private var showingStatus: Boolean = false

        private fun makeSnackbar(fragment: Fragment, view: View, anchor: View?): Snackbar {
            val snackbar = Snackbar.make(fragment.requireContext(), view, "", Snackbar.LENGTH_INDEFINITE)
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
                    fragment.lifecycleScope.launch {
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

    fun attach(view: View, anchor: View?) {
        val snackbar = SwapableSnackbar(fragment, view, anchor)
        val context = fragment.requireContext()

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
                    snackbar.showText(context.getString(R.string.updater_failure, ErrorMessages[progress.error]))
                    delay(5.seconds)
                    progress.retry()
                }

                is Updater.Progress.Corrupt -> {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.updater_corrupt_title)
                        .setMessage(R.string.updater_corrupt_message)
                        .setPositiveButton(R.string.updater_corrupt_navigate) { _, _ ->
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = Uri.parse(progress.downloadUrl)
                            try {
                                context.startActivity(intent)
                            } catch (e: Throwable) {
                                Toast.makeText(context, ErrorMessages[e], Toast.LENGTH_SHORT).show()
                            }
                        }.setCancelable(false).setOnDismissListener {
                            val intent = Intent(Intent.ACTION_MAIN)
                            intent.addCategory(Intent.CATEGORY_HOME)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            System.exit(0)
                        }.show()
                }
            }
        }.launchIn(fragment.lifecycleScope)
    }
}