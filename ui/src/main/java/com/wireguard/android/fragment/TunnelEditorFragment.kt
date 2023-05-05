/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.fragment

import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.BundleCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.databinding.TunnelEditorFragmentBinding
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.util.AdminKnobs
import com.wireguard.android.util.BiometricAuthenticator
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.viewmodel.ConfigProxy
import com.wireguard.config.Config
import kotlinx.coroutines.launch

/**
 * Fragment for editing a WireGuard configuration.
 */
class TunnelEditorFragment : BaseFragment(), MenuProvider {
    private var haveShownKeys = false
    private var binding: TunnelEditorFragmentBinding? = null
    private var tunnel: ObservableTunnel? = null

    private fun onConfigLoaded(config: Config) {
        binding?.config = ConfigProxy(config)
    }

    private fun onConfigSaved(savedTunnel: Tunnel, throwable: Throwable?) {
        val ctx = activity ?: Application.get()
        if (throwable == null) {
            val message = ctx.getString(R.string.config_save_success, savedTunnel.name)
            Log.d(TAG, message)
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            onFinished()
        } else {
            val error = ErrorMessages[throwable]
            val message = ctx.getString(R.string.config_save_error, savedTunnel.name, error)
            Log.e(TAG, message, throwable)
            val binding = binding
            if (binding != null)
                Snackbar.make(binding.mainContainer, message, Snackbar.LENGTH_LONG).show()
            else
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.config_editor, menu)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = TunnelEditorFragmentBinding.inflate(inflater, container, false)
        binding?.apply {
            executePendingBindings()
            privateKeyTextLayout.setEndIconOnClickListener { config?.`interface`?.generateKeyPair() }
        }
        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onDestroyView() {
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = null
        super.onDestroyView()
    }

    private fun onFinished() {
        // Hide the keyboard; it rarely goes away on its own.
        val activity = activity ?: return
        val focusedView = activity.currentFocus
        if (focusedView != null) {
            val inputManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputManager?.hideSoftInputFromWindow(
                focusedView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }
        parentFragmentManager.popBackStackImmediate()

        // If we just made a new one, save it to select the details page.
        if (selectedTunnel != tunnel)
            selectedTunnel = tunnel
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.menu_action_save) {
            binding ?: return false
            val newConfig = try {
                binding!!.config!!.resolve()
            } catch (e: Throwable) {
                val error = ErrorMessages[e]
                val tunnelName = if (tunnel == null) binding!!.name else tunnel!!.name
                val message = getString(R.string.config_save_error, tunnelName, error)
                Log.e(TAG, message, e)
                Snackbar.make(binding!!.mainContainer, error, Snackbar.LENGTH_LONG).show()
                return false
            }
            val activity = requireActivity()
            activity.lifecycleScope.launch {
                when {
                    tunnel == null -> {
                        Log.d(TAG, "Attempting to create new tunnel " + binding!!.name)
                        val manager = Application.getTunnelManager()
                        try {
                            onTunnelCreated(manager.create(binding!!.name!!, newConfig), null)
                        } catch (e: Throwable) {
                            onTunnelCreated(null, e)
                        }
                    }

                    tunnel!!.name != binding!!.name -> {
                        Log.d(TAG, "Attempting to rename tunnel to " + binding!!.name)
                        try {
                            tunnel!!.setNameAsync(binding!!.name!!)
                            onTunnelRenamed(tunnel!!, newConfig, null)
                        } catch (e: Throwable) {
                            onTunnelRenamed(tunnel!!, newConfig, e)
                        }
                    }

                    else -> {
                        Log.d(TAG, "Attempting to save config of " + tunnel!!.name)
                        try {
                            tunnel!!.setConfigAsync(newConfig)
                            onConfigSaved(tunnel!!, null)
                        } catch (e: Throwable) {
                            onConfigSaved(tunnel!!, e)
                        }
                    }
                }
            }
            return true
        }
        return false
    }

    @Suppress("UNUSED_PARAMETER")
    fun onRequestSetExcludedIncludedApplications(view: View?) {
        if (binding != null) {
            var isExcluded = true
            var selectedApps = ArrayList(binding!!.config!!.`interface`.excludedApplications)
            if (selectedApps.isEmpty()) {
                selectedApps = ArrayList(binding!!.config!!.`interface`.includedApplications)
                if (selectedApps.isNotEmpty())
                    isExcluded = false
            }
            val fragment = AppListDialogFragment.newInstance(selectedApps, isExcluded)
            childFragmentManager.setFragmentResultListener(AppListDialogFragment.REQUEST_SELECTION, viewLifecycleOwner) { _, bundle ->
                requireNotNull(binding) { "Tried to set excluded/included apps while no view was loaded" }
                val newSelections = requireNotNull(bundle.getStringArray(AppListDialogFragment.KEY_SELECTED_APPS))
                val excluded = requireNotNull(bundle.getBoolean(AppListDialogFragment.KEY_IS_EXCLUDED))
                if (excluded) {
                    binding!!.config!!.`interface`.includedApplications.clear()
                    binding!!.config!!.`interface`.excludedApplications.apply {
                        clear()
                        addAll(newSelections)
                    }
                } else {
                    binding!!.config!!.`interface`.excludedApplications.clear()
                    binding!!.config!!.`interface`.includedApplications.apply {
                        clear()
                        addAll(newSelections)
                    }
                }
            }
            fragment.show(childFragmentManager, null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (binding != null) outState.putParcelable(KEY_LOCAL_CONFIG, binding!!.config)
        outState.putString(KEY_ORIGINAL_NAME, if (tunnel == null) null else tunnel!!.name)
        super.onSaveInstanceState(outState)
    }

    override fun onSelectedTunnelChanged(
        oldTunnel: ObservableTunnel?,
        newTunnel: ObservableTunnel?
    ) {
        tunnel = newTunnel
        if (binding == null) return
        binding!!.config = ConfigProxy()
        if (tunnel != null) {
            binding!!.name = tunnel!!.name
            lifecycleScope.launch {
                try {
                    onConfigLoaded(tunnel!!.getConfigAsync())
                } catch (_: Throwable) {
                }
            }
        } else {
            binding!!.name = ""
        }
    }

    private fun onTunnelCreated(newTunnel: ObservableTunnel?, throwable: Throwable?) {
        val ctx = activity ?: Application.get()
        if (throwable == null) {
            tunnel = newTunnel
            val message = ctx.getString(R.string.tunnel_create_success, tunnel!!.name)
            Log.d(TAG, message)
            Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
            onFinished()
        } else {
            val error = ErrorMessages[throwable]
            val message = ctx.getString(R.string.tunnel_create_error, error)
            Log.e(TAG, message, throwable)
            val binding = binding
            if (binding != null)
                Snackbar.make(binding.mainContainer, message, Snackbar.LENGTH_LONG).show()
            else
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun onTunnelRenamed(
        renamedTunnel: ObservableTunnel, newConfig: Config,
        throwable: Throwable?
    ) {
        val ctx = activity ?: Application.get()
        if (throwable == null) {
            val message = ctx.getString(R.string.tunnel_rename_success, renamedTunnel.name)
            Log.d(TAG, message)
            // Now save the rest of configuration changes.
            Log.d(TAG, "Attempting to save config of renamed tunnel " + tunnel!!.name)
            try {
                renamedTunnel.setConfigAsync(newConfig)
                onConfigSaved(renamedTunnel, null)
            } catch (e: Throwable) {
                onConfigSaved(renamedTunnel, e)
            }
        } else {
            val error = ErrorMessages[throwable]
            val message = ctx.getString(R.string.tunnel_rename_error, error)
            Log.e(TAG, message, throwable)
            val binding = binding
            if (binding != null)
                Snackbar.make(binding.mainContainer, message, Snackbar.LENGTH_LONG).show()
            else
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        binding ?: return
        binding!!.fragment = this
        if (savedInstanceState == null) {
            onSelectedTunnelChanged(null, selectedTunnel)
        } else {
            tunnel = selectedTunnel
            val config = BundleCompat.getParcelable(savedInstanceState, KEY_LOCAL_CONFIG, ConfigProxy::class.java)!!
            val originalName = savedInstanceState.getString(KEY_ORIGINAL_NAME)
            if (tunnel != null && tunnel!!.name != originalName) onSelectedTunnelChanged(null, tunnel) else binding!!.config = config
        }
        super.onViewStateRestored(savedInstanceState)
    }

    private var showingAuthenticator = false

    fun onKeyClick(view: View) = onKeyFocusChange(view, true)

    fun onKeyFocusChange(view: View, isFocused: Boolean) {
        if (!isFocused || showingAuthenticator) return
        val edit = view as? EditText ?: return
        if (edit.inputType == InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) return
        if (!haveShownKeys && edit.text.isNotEmpty()) {
            if (AdminKnobs.disableConfigExport) return
            showingAuthenticator = true
            BiometricAuthenticator.authenticate(R.string.biometric_prompt_private_key_title, this) {
                showingAuthenticator = false
                when (it) {
                    is BiometricAuthenticator.Result.Success, is BiometricAuthenticator.Result.HardwareUnavailableOrDisabled -> {
                        haveShownKeys = true
                        showPrivateKey(edit)
                    }

                    is BiometricAuthenticator.Result.Failure -> {
                        Snackbar.make(
                            binding!!.mainContainer,
                            it.message,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }

                    is BiometricAuthenticator.Result.Cancelled -> {}
                }
            }
        } else {
            showPrivateKey(edit)
        }
    }

    private fun showPrivateKey(edit: EditText) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        edit.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }

    companion object {
        private const val KEY_LOCAL_CONFIG = "local_config"
        private const val KEY_ORIGINAL_NAME = "original_name"
        private const val TAG = "WireGuard/TunnelEditorFragment"
    }
}
