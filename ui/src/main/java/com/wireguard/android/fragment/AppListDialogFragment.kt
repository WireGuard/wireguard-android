/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.fragment

import android.app.Dialog
import android.content.Intent
import android.net.ConnectivityManager.ACTION_CAPTIVE_PORTAL_SIGN_IN
import android.os.Bundle
import android.os.Build
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.databinding.Observable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.wireguard.android.BR
import com.wireguard.android.R
import com.wireguard.android.databinding.AppListDialogFragmentBinding
import com.wireguard.android.databinding.ObservableKeyedArrayList
import com.wireguard.android.model.ApplicationData
import com.wireguard.android.util.ErrorMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppListDialogFragment : DialogFragment() {
    private val appData = ObservableKeyedArrayList<String, ApplicationData>()
    private var currentlySelectedApps = emptyList<String>()
    private var initiallyExcluded = false
    private var button: Button? = null
    private var tabs: TabLayout? = null

    private fun loadData() {
        val activity = activity ?: return
        val pm = activity.packageManager
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val applicationData: MutableList<ApplicationData> = ArrayList()
                withContext(Dispatchers.IO) {
                    val launcherIntent = Intent(Intent.ACTION_MAIN, null)
                    launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                    val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val captiveIntent = Intent(ACTION_CAPTIVE_PORTAL_SIGN_IN)
                        resolveInfos.addAll(pm.queryIntentActivities(captiveIntent, 0))
                    }
                    resolveInfos.forEach {
                        val packageName = it.activityInfo.packageName
                        val appData = ApplicationData(it.loadIcon(pm), it.loadLabel(pm).toString(), packageName, currentlySelectedApps.contains(packageName))
                        applicationData.add(appData)
                        appData.addOnPropertyChangedCallback(object : Observable.OnPropertyChangedCallback() {
                            override fun onPropertyChanged(sender: Observable?, propertyId: Int) {
                                if (propertyId == BR.selected)
                                    setButtonText()
                            }
                        })
                    }
                }
                applicationData.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                withContext(Dispatchers.Main.immediate) {
                    appData.clear()
                    appData.addAll(applicationData)
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main.immediate) {
                    val error = ErrorMessages[e]
                    val message = activity.getString(R.string.error_fetching_apps, error)
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentlySelectedApps = (arguments?.getStringArrayList(KEY_SELECTED_APPS) ?: emptyList())
        initiallyExcluded = arguments?.getBoolean(KEY_IS_EXCLUDED) ?: true
    }

    private fun setButtonText() {
        val numSelected = appData.count { it.isSelected }
        button?.text = if (numSelected == 0)
            getString(R.string.use_all_applications)
        else when (tabs?.selectedTabPosition) {
            0 -> resources.getQuantityString(R.plurals.exclude_n_applications, numSelected, numSelected)
            1 -> resources.getQuantityString(R.plurals.include_n_applications, numSelected, numSelected)
            else -> null
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alertDialogBuilder = AlertDialog.Builder(requireActivity())
        val binding = AppListDialogFragmentBinding.inflate(requireActivity().layoutInflater, null, false)
        binding.executePendingBindings()
        alertDialogBuilder.setView(binding.root)
        tabs = binding.tabs
        tabs?.apply {
            selectTab(binding.tabs.getTabAt(if (initiallyExcluded) 0 else 1))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabReselected(tab: TabLayout.Tab?) = Unit
                override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
                override fun onTabSelected(tab: TabLayout.Tab?) = setButtonText()
            })
        }
        alertDialogBuilder.setPositiveButton(" ") { _, _ -> setSelectionAndDismiss() }
        alertDialogBuilder.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
        alertDialogBuilder.setNeutralButton(R.string.toggle_all) { _, _ -> }
        binding.fragment = this
        binding.appData = appData
        loadData()
        val dialog = alertDialogBuilder.create()
        dialog.setOnShowListener {
            button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            setButtonText()
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { _ ->
                val selectAll = appData.none { it.isSelected }
                appData.forEach {
                    it.isSelected = selectAll
                }
            }
        }
        return dialog
    }

    private fun setSelectionAndDismiss() {
        val selectedApps: MutableList<String> = ArrayList()
        for (data in appData) {
            if (data.isSelected) {
                selectedApps.add(data.packageName)
            }
        }
        setFragmentResult(REQUEST_SELECTION, bundleOf(
                KEY_SELECTED_APPS to selectedApps.toTypedArray(),
                KEY_IS_EXCLUDED to (tabs?.selectedTabPosition == 0)
        ))
        dismiss()
    }

    companion object {
        const val KEY_SELECTED_APPS = "selected_apps"
        const val KEY_IS_EXCLUDED = "is_excluded"
        const val REQUEST_SELECTION = "request_selection"
        fun newInstance(selectedApps: ArrayList<String?>?, isExcluded: Boolean): AppListDialogFragment {
            val extras = Bundle()
            extras.putStringArrayList(KEY_SELECTED_APPS, selectedApps)
            extras.putBoolean(KEY_IS_EXCLUDED, isExcluded)
            val fragment = AppListDialogFragment()
            fragment.arguments = extras
            return fragment
        }
    }
}
