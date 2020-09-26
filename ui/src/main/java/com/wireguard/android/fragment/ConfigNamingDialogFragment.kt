/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.core.content.getSystemService
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.databinding.ConfigNamingDialogFragmentBinding
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

class ConfigNamingDialogFragment : DialogFragment() {
    private var binding: ConfigNamingDialogFragmentBinding? = null
    private var config: Config? = null
    private var imm: InputMethodManager? = null

    private fun createTunnelAndDismiss() {
        val binding = binding ?: return
        val activity = activity ?: return
        val name = binding.tunnelNameText.text.toString()
        activity.lifecycleScope.launch {
            try {
                Application.getTunnelManager().create(name, config)
                dismiss()
            } catch (e: Throwable) {
                binding.tunnelNameTextLayout.error = e.message
            }
        }
    }

    override fun dismiss() {
        setKeyboardVisible(false)
        super.dismiss()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configText = requireArguments().getString(KEY_CONFIG_TEXT)
        val configBytes = configText!!.toByteArray(StandardCharsets.UTF_8)
        config = try {
            Config.parse(ByteArrayInputStream(configBytes))
        } catch (e: Throwable) {
            when (e) {
                is BadConfigException, is IOException -> throw IllegalArgumentException("Invalid config passed to ${javaClass.simpleName}", e)
                else -> throw e
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        imm = activity.getSystemService()
        val alertDialogBuilder = AlertDialog.Builder(activity)
        alertDialogBuilder.setTitle(R.string.import_from_qr_code)
        binding = ConfigNamingDialogFragmentBinding.inflate(activity.layoutInflater, null, false)
        binding?.apply {
            executePendingBindings()
            alertDialogBuilder.setView(root)
        }
        alertDialogBuilder.setPositiveButton(R.string.create_tunnel, null)
        alertDialogBuilder.setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
        return alertDialogBuilder.create().apply {
            setOnShowListener {
                findViewById<TextInputEditText>(R.id.tunnel_name_text)?.apply {
                    setOnFocusChangeListener { v, _ ->
                        v.post {
                            imm?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                    requestFocus()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val dialog = dialog as AlertDialog?
        if (dialog != null) {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener { createTunnelAndDismiss() }
            setKeyboardVisible(true)
        }
    }

    private fun setKeyboardVisible(visible: Boolean) {
        if (visible) {
            imm!!.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
        } else if (binding != null) {
            imm!!.hideSoftInputFromWindow(binding!!.tunnelNameText.windowToken, 0)
        }
    }

    companion object {
        private const val KEY_CONFIG_TEXT = "config_text"

        @JvmStatic
        fun newInstance(configText: String?): ConfigNamingDialogFragment {
            val extras = Bundle()
            extras.putString(KEY_CONFIG_TEXT, configText)
            val fragment = ConfigNamingDialogFragment()
            fragment.arguments = extras
            return fragment
        }
    }
}
