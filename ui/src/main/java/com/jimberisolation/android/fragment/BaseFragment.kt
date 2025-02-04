/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.jimberisolation.android.fragment

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jimberisolation.android.Application
import com.jimberisolation.android.R
import com.jimberisolation.android.activity.BaseActivity
import com.jimberisolation.android.activity.BaseActivity.OnSelectedTunnelChangedListener
import com.jimberisolation.android.backend.GoBackend
import com.jimberisolation.android.backend.Tunnel
import com.jimberisolation.android.databinding.TunnelDetailFragmentBinding
import com.jimberisolation.android.databinding.TunnelListItemBinding
import com.jimberisolation.android.model.ObservableTunnel
import com.jimberisolation.android.networkcontroller.getDaemonConnectionData
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.ErrorMessages
import com.jimberisolation.android.util.parseEdPublicKeyToCurveX25519
import com.jimberisolation.config.Config
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Base class for fragments that need to know the currently-selected tunnel. Only does anything when
 * attached to a `BaseActivity`.
 */
abstract class BaseFragment : Fragment(), OnSelectedTunnelChangedListener {
    private var pendingTunnel: ObservableTunnel? = null
    private var pendingTunnelUp: Boolean? = null
    private val permissionActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val tunnel = pendingTunnel
        val checked = pendingTunnelUp
        if (tunnel != null && checked != null)
            setTunnelStateWithPermissionsResult(tunnel, checked)
        pendingTunnel = null
        pendingTunnelUp = null
    }

    protected var selectedTunnel: ObservableTunnel?
        get() = (activity as? BaseActivity)?.selectedTunnel
        protected set(tunnel) {
            (activity as? BaseActivity)?.selectedTunnel = tunnel
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (activity as? BaseActivity)?.addOnSelectedTunnelChangedListener(this)
    }

    override fun onDetach() {
        (activity as? BaseActivity)?.removeOnSelectedTunnelChangedListener(this)
        super.onDetach()
    }

    fun setTunnelState(view: View, isChecked: Boolean) {
        val tunnel = when (val binding = DataBindingUtil.findBinding<ViewDataBinding>(view)) {
            is TunnelDetailFragmentBinding -> binding.tunnel
            is TunnelListItemBinding -> binding.item
            else -> return
        } ?: return

        val activity = activity ?: return

        // Use lifecycleScope to launch a coroutine and handle the async call
        activity.lifecycleScope.launch {
            try {
                if(isChecked) {
                    // Await the result of the asynchronous configuration
                    val currentConfig = tunnel.getConfigAsync()
                    val currentConfigString = currentConfig.toWgQuickString();

                    val daemonId = tunnel.getDaemonId()
                    val kp = SharedStorage.getInstance().getDaemonKeyPairByDaemonId(daemonId)

                    val networkController = getDaemonConnectionData(daemonId, kp!!.companyName, kp.baseEncodedSkEd25519)
                    if(!networkController.isSuccess) {
                        tunnel.setStateAsync(Tunnel.State.DOWN)
                        return@launch;
                    }

                    val result = networkController.getOrNull();

                    val oldPublicIp = currentConfig.peers.first().endpoint.get().host;
                    val newPublicIp = result?.endpointAddress;

                    val oldPublicKey = currentConfig.peers.first().publicKey.toBase64();
                    val newPublicKey = parseEdPublicKeyToCurveX25519(result!!.routerPublicKey);

                    var updatedConfigString = currentConfigString.replace(Regex("(?<=Endpoint = )$oldPublicIp"), newPublicIp.toString())
                    updatedConfigString = updatedConfigString.replace(Regex("(?<=PublicKey = )$oldPublicKey"), newPublicKey)

                    val updatedConfig = Config.parse(ByteArrayInputStream(updatedConfigString.toByteArray(StandardCharsets.UTF_8)))
                    tunnel.setConfigAsync(updatedConfig);
                }

                // Proceed with permission handling if GoBackend is being used
                if (Application.getBackend() is GoBackend) {
                    try {
                        val intent = GoBackend.VpnService.prepare(activity)
                        if (intent != null) {
                            pendingTunnel = tunnel
                            pendingTunnelUp = isChecked
                            permissionActivityResultLauncher.launch(intent)
                            return@launch
                        }
                    } catch (e: Throwable) {
                        val message = activity.getString(R.string.error_prepare, ErrorMessages[e])
                        Log.e(TAG, message, e)
                    }
                }

                // Set the tunnel state with permissions
                setTunnelStateWithPermissionsResult(tunnel, isChecked)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to get or update tunnel config", e)
            }
        }
    }


    private fun setTunnelStateWithPermissionsResult(tunnel: ObservableTunnel, checked: Boolean) {
        val activity = activity ?: return
        activity.lifecycleScope.launch {
            try {
                tunnel.setStateAsync(Tunnel.State.of(checked))
            } catch (e: Throwable) {
                val error = ErrorMessages[e]
                val messageResId = if (checked) R.string.error_up else R.string.error_down
                val message = activity.getString(messageResId, error)
                val view = view
                if (view != null)
                else
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                Log.e(TAG, message, e)
            }
        }
    }

    companion object {
        private const val TAG = "WireGuard/BaseFragment"
    }
}
