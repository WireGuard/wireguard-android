/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.databinding.AppListDialogFragmentBinding
import com.wireguard.android.model.ApplicationData
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.util.ObservableKeyedArrayList
import com.wireguard.android.util.ObservableKeyedList

class AppListDialogFragment : DialogFragment() {
    private val appData: ObservableKeyedList<String, ApplicationData> = ObservableKeyedArrayList()
    private var currentlyExcludedApps = emptyList<String>()

    private fun loadData() {
        val activity = activity ?: return
        val pm = activity.packageManager
        Application.getAsyncWorker().supplyAsync<List<ApplicationData>> {
            val launcherIntent = Intent(Intent.ACTION_MAIN, null)
            launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
            val applicationData: MutableList<ApplicationData> = ArrayList()
            resolveInfos.forEach {
                val packageName = it.activityInfo.packageName
                applicationData.add(ApplicationData(it.loadIcon(pm), it.loadLabel(pm).toString(), packageName, currentlyExcludedApps.contains(packageName)))
            }
            applicationData.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            applicationData
        }.whenComplete { data, throwable ->
            if (data != null) {
                appData.clear()
                appData.addAll(data)
            } else {
                val error = ErrorMessages.get(throwable)
                val message = activity.getString(R.string.error_fetching_apps, error)
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                dismissAllowingStateLoss()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val excludedApps = requireArguments().getStringArrayList(KEY_EXCLUDED_APPS)
        currentlyExcludedApps = (excludedApps ?: emptyList())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder = AlertDialog.Builder(requireActivity())
        alertDialogBuilder.setTitle(R.string.excluded_applications)
        val binding = AppListDialogFragmentBinding.inflate(requireActivity().layoutInflater, null, false)
        binding.executePendingBindings()
        alertDialogBuilder.setView(binding.root)
        alertDialogBuilder.setPositiveButton(R.string.set_exclusions) { _, _ -> setExclusionsAndDismiss() }
        alertDialogBuilder.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
        alertDialogBuilder.setNeutralButton(R.string.toggle_all) { _, _ -> }
        binding.fragment = this
        binding.appData = appData
        loadData()
        val dialog = alertDialogBuilder.create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                val selectedItems = appData
                        .filter { it.isExcludedFromTunnel }

                val excludeAll = selectedItems.isEmpty()
                appData.forEach {
                    it.isExcludedFromTunnel = excludeAll
                }
            }
        }
        return dialog
    }

    private fun setExclusionsAndDismiss() {
        val excludedApps: MutableList<String> = ArrayList()
        for (data in appData) {
            if (data.isExcludedFromTunnel) {
                excludedApps.add(data.packageName)
            }
        }
        (targetFragment as AppExclusionListener?)!!.onExcludedAppsSelected(excludedApps)
        dismiss()
    }

    interface AppExclusionListener {
        fun onExcludedAppsSelected(excludedApps: List<String>)
    }

    companion object {
        private const val KEY_EXCLUDED_APPS = "excludedApps"
        fun <T> newInstance(excludedApps: ArrayList<String?>?, target: T): AppListDialogFragment where T : Fragment?, T : AppExclusionListener? {
            val extras = Bundle()
            extras.putStringArrayList(KEY_EXCLUDED_APPS, excludedApps)
            val fragment = AppListDialogFragment()
            fragment.setTargetFragment(target, 0)
            fragment.arguments = extras
            return fragment
        }
    }
}
