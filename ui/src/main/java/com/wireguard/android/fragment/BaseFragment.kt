/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.fragment

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.wireguard.android.Application
import com.wireguard.android.R
import com.wireguard.android.activity.BaseActivity
import com.wireguard.android.activity.BaseActivity.OnSelectedTunnelChangedListener
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.databinding.TunnelDetailFragmentBinding
import com.wireguard.android.databinding.TunnelListItemBinding
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.util.ErrorMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Base class for fragments that need to know the currently-selected tunnel. Only does anything when
 * attached to a `BaseActivity`.
 */
abstract class BaseFragment : Fragment(), OnSelectedTunnelChangedListener {
    private var baseActivity: BaseActivity? = null
    private var pendingTunnel: ObservableTunnel? = null
    private var pendingTunnelUp: Boolean? = null
    protected var selectedTunnel: ObservableTunnel?
        get() = baseActivity?.selectedTunnel
        protected set(tunnel) {
            baseActivity?.selectedTunnel = tunnel
        }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_VPN_PERMISSION) {
            if (pendingTunnel != null && pendingTunnelUp != null) setTunnelStateWithPermissionsResult(pendingTunnel!!, pendingTunnelUp!!)
            pendingTunnel = null
            pendingTunnelUp = null
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is BaseActivity) {
            baseActivity = context
            baseActivity?.addOnSelectedTunnelChangedListener(this)
        } else {
            baseActivity = null
        }
    }

    override fun onDetach() {
        baseActivity?.removeOnSelectedTunnelChangedListener(this)
        baseActivity = null
        super.onDetach()
    }

    fun setTunnelState(view: View, checked: Boolean) {
        val tunnel = when (val binding = DataBindingUtil.findBinding<ViewDataBinding>(view)) {
            is TunnelDetailFragmentBinding -> binding.tunnel
            is TunnelListItemBinding -> binding.item
            else -> return
        } ?: return
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            if (Application.getBackend() is GoBackend) {
                val intent = GoBackend.VpnService.prepare(view.context)
                if (intent != null) {
                    pendingTunnel = tunnel
                    pendingTunnelUp = checked
                    startActivityForResult(intent, REQUEST_CODE_VPN_PERMISSION)
                    return@launch
                }
            }
            setTunnelStateWithPermissionsResult(tunnel, checked)
        }
    }

    private fun setTunnelStateWithPermissionsResult(tunnel: ObservableTunnel, checked: Boolean) {
        lifecycleScope.launch(Dispatchers.Main.immediate) {
            try {
                tunnel.setStateAsync(Tunnel.State.of(checked))
            } catch (e: Throwable) {
                val error = ErrorMessages[e]
                val messageResId = if (checked) R.string.error_up else R.string.error_down
                val message = requireContext().getString(messageResId, error)
                val view = view
                if (view != null)
                    Snackbar.make(view, message, Snackbar.LENGTH_LONG)
                            .setAnchorView(view.findViewById<View>(R.id.create_fab))
                            .show()
                else
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                Log.e(TAG, message, e)
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_VPN_PERMISSION = 23491
        private const val TAG = "WireGuard/BaseFragment"
    }
}
