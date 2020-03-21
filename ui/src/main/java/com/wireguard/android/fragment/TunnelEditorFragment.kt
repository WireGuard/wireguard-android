/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.fragment

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.databinding.TunnelEditorFragmentBinding
import com.wireguard.android.fragment.AppListDialogFragment.AppExclusionListener
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.widget.EdgeToEdge.setUpRoot
import com.wireguard.android.widget.EdgeToEdge.setUpScrollingContent
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.viewmodel.ConfigProxy
import com.wireguard.config.Config

/**
 * Fragment for editing a WireGuard configuration.
 */
class TunnelEditorFragment : BaseFragment(), AppExclusionListener {
    private var binding: TunnelEditorFragmentBinding? = null
    private var tunnel: ObservableTunnel? = null
    private fun onConfigLoaded(config: Config) {
        binding?.config = ConfigProxy(config)
    }

    private fun onConfigSaved(savedTunnel: Tunnel, throwable: Throwable?) {
        val message: String
        if (throwable == null) {
            message = getString(R.string.config_save_success, savedTunnel.name)
            Log.d(TAG, message)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            onFinished()
        } else {
            val error = ErrorMessages.get(throwable)
            message = getString(R.string.config_save_error, savedTunnel.name, error)
            Log.e(TAG, message, throwable)
            binding?.let {
                Snackbar.make(it.mainContainer, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.config_editor, menu)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = TunnelEditorFragmentBinding.inflate(inflater, container, false)
        binding?.apply {
            executePendingBindings()
            setUpRoot(root as ViewGroup)
            setUpScrollingContent(mainContainer, null)
        }
        return binding?.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onExcludedAppsSelected(excludedApps: List<String>) {
        requireNotNull(binding) { "Tried to set excluded apps while no view was loaded" }
        binding!!.config!!.`interface`.excludedApplications.apply {
            clear()
            addAll(excludedApps)
        }
    }

    private fun onFinished() {
        // Hide the keyboard; it rarely goes away on its own.
        val activity = activity ?: return
        val focusedView = activity.currentFocus
        if (focusedView != null) {
            val inputManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputManager?.hideSoftInputFromWindow(focusedView.windowToken,
                    InputMethodManager.HIDE_NOT_ALWAYS)
        }
        // Tell the activity to finish itself or go back to the detail view.
        requireActivity().runOnUiThread {
            // TODO(smaeul): Remove this hack when fixing the Config ViewModel
            // The selected tunnel has to actually change, but we have to remember this one.
            val savedTunnel = tunnel
            if (savedTunnel === selectedTunnel) selectedTunnel = null
            selectedTunnel = savedTunnel
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_action_save) {
            binding ?: return false
            val newConfig = try {
                binding!!.config!!.resolve()
            } catch (e: Exception) {
                val error = ErrorMessages.get(e)
                val tunnelName = if (tunnel == null) binding!!.name else tunnel!!.name
                val message = getString(R.string.config_save_error, tunnelName, error)
                Log.e(TAG, message, e)
                Snackbar.make(binding!!.mainContainer, error, Snackbar.LENGTH_LONG).show()
                return false
            }
            when {
                tunnel == null -> {
                    Log.d(TAG, "Attempting to create new tunnel " + binding!!.name)
                    val manager = Application.getTunnelManager()
                    manager.create(binding!!.name!!, newConfig)
                            .whenComplete(this::onTunnelCreated)
                }
                tunnel!!.name != binding!!.name -> {
                    Log.d(TAG, "Attempting to rename tunnel to " + binding!!.name)
                    tunnel!!.setName(binding!!.name!!)
                            .whenComplete { _, t -> onTunnelRenamed(tunnel!!, newConfig, t) }
                }
                else -> {
                    Log.d(TAG, "Attempting to save config of " + tunnel!!.name)
                    tunnel!!.setConfig(newConfig)
                            .whenComplete { _, t -> onConfigSaved(tunnel!!, t) }
                }
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onRequestSetExcludedApplications(view: View?) {
        if (binding != null) {
            val excludedApps = ArrayList(binding!!.config!!.`interface`.excludedApplications)
            val fragment = AppListDialogFragment.newInstance(excludedApps, this)
            fragment.show(parentFragmentManager, null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (binding != null) outState.putParcelable(KEY_LOCAL_CONFIG, binding!!.config)
        outState.putString(KEY_ORIGINAL_NAME, if (tunnel == null) null else tunnel!!.name)
        super.onSaveInstanceState(outState)
    }

    override fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?,
                                         newTunnel: ObservableTunnel?) {
        tunnel = newTunnel
        if (binding == null) return
        binding!!.config = ConfigProxy()
        if (tunnel != null) {
            binding!!.name = tunnel!!.name
            tunnel!!.configAsync.thenAccept(this::onConfigLoaded)
        } else {
            binding!!.name = ""
        }
    }

    private fun onTunnelCreated(newTunnel: ObservableTunnel, throwable: Throwable?) {
        val message: String
        if (throwable == null) {
            tunnel = newTunnel
            message = getString(R.string.tunnel_create_success, tunnel!!.name)
            Log.d(TAG, message)
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            onFinished()
        } else {
            val error = ErrorMessages.get(throwable)
            message = getString(R.string.tunnel_create_error, error)
            Log.e(TAG, message, throwable)
            binding?.let {
                Snackbar.make(it.mainContainer, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun onTunnelRenamed(renamedTunnel: ObservableTunnel, newConfig: Config,
                                throwable: Throwable?) {
        val message: String
        if (throwable == null) {
            message = getString(R.string.tunnel_rename_success, renamedTunnel.name)
            Log.d(TAG, message)
            // Now save the rest of configuration changes.
            Log.d(TAG, "Attempting to save config of renamed tunnel " + tunnel!!.name)
            renamedTunnel.setConfig(newConfig).whenComplete { _, t -> onConfigSaved(renamedTunnel, t) }
        } else {
            val error = ErrorMessages.get(throwable)
            message = getString(R.string.tunnel_rename_error, error)
            Log.e(TAG, message, throwable)
            binding?.let {
                Snackbar.make(it.mainContainer, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        binding ?: return
        binding!!.fragment = this
        if (savedInstanceState == null) {
            onSelectedTunnelChanged(null, selectedTunnel)
        } else {
            tunnel = selectedTunnel
            val config: ConfigProxy = savedInstanceState.getParcelable(KEY_LOCAL_CONFIG)!!
            val originalName = savedInstanceState.getString(KEY_ORIGINAL_NAME)
            if (tunnel != null && tunnel!!.name != originalName) onSelectedTunnelChanged(null, tunnel) else binding!!.config = config
        }
        super.onViewStateRestored(savedInstanceState)
    }

    companion object {
        private const val KEY_LOCAL_CONFIG = "local_config"
        private const val KEY_ORIGINAL_NAME = "original_name"
        private val TAG = "WireGuard/" + TunnelEditorFragment::class.java.simpleName
    }
}
