/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import com.wireguard.android.R
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.databinding.TunnelDetailFragmentBinding
import com.wireguard.android.databinding.TunnelDetailPeerBinding
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.android.widget.EdgeToEdge.setUpRoot
import com.wireguard.android.widget.EdgeToEdge.setUpScrollingContent
import java.util.Timer
import java.util.TimerTask

/**
 * Fragment that shows details about a specific tunnel.
 */
class TunnelDetailFragment : BaseFragment() {
    private var binding: TunnelDetailFragmentBinding? = null
    private var lastState = Tunnel.State.TOGGLE
    private var timer: Timer? = null

    private fun formatBytes(bytes: Long): String {
        val context = requireContext()
        return when {
            bytes < 1024 -> context.getString(R.string.transfer_bytes, bytes)
            bytes < 1024 * 1024 -> context.getString(R.string.transfer_kibibytes, bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> context.getString(R.string.transfer_mibibytes, bytes / (1024.0 * 1024.0))
            bytes < 1024 * 1024 * 1024 * 1024L -> context.getString(R.string.transfer_gibibytes, bytes / (1024.0 * 1024.0 * 1024.0))
            else -> context.getString(R.string.transfer_tibibytes, bytes / (1024.0 * 1024.0 * 1024.0) / 1024.0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.tunnel_detail, menu)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        binding = TunnelDetailFragmentBinding.inflate(inflater, container, false)
        binding?.apply {
            executePendingBindings()
            setUpRoot(root as ViewGroup)
            setUpScrollingContent(root as ViewGroup, null)
        }
        return binding!!.root
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        timer = Timer()
        timer!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                updateStats()
            }
        }, 0, 1000)
    }

    override fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?, newTunnel: ObservableTunnel?) {
        binding ?: return
        binding!!.tunnel = newTunnel
        if (newTunnel == null) binding!!.config = null else newTunnel.configAsync.thenAccept { config -> binding!!.config = config }
        lastState = Tunnel.State.TOGGLE
        updateStats()
    }

    override fun onStop() {
        super.onStop()
        if (timer != null) {
            timer!!.cancel()
            timer = null
        }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        binding ?: return
        binding!!.fragment = this
        onSelectedTunnelChanged(null, selectedTunnel)
        super.onViewStateRestored(savedInstanceState)
    }

    private fun updateStats() {
        if (binding == null || !isResumed) return
        val tunnel = binding!!.tunnel ?: return
        val state = tunnel.state
        if (state != Tunnel.State.UP && lastState == state) return
        lastState = state
        tunnel.statisticsAsync.whenComplete { statistics, throwable ->
            if (throwable != null) {
                for (i in 0 until binding!!.peersLayout.childCount) {
                    val peer: TunnelDetailPeerBinding = DataBindingUtil.getBinding(binding!!.peersLayout.getChildAt(i))
                            ?: continue
                    peer.transferLabel.visibility = View.GONE
                    peer.transferText.visibility = View.GONE
                }
                return@whenComplete
            }
            for (i in 0 until binding!!.peersLayout.childCount) {
                val peer: TunnelDetailPeerBinding = DataBindingUtil.getBinding(binding!!.peersLayout.getChildAt(i))
                        ?: continue
                val publicKey = peer.item!!.publicKey
                val rx = statistics.peerRx(publicKey)
                val tx = statistics.peerTx(publicKey)
                if (rx == 0L && tx == 0L) {
                    peer.transferLabel.visibility = View.GONE
                    peer.transferText.visibility = View.GONE
                    continue
                }
                peer.transferText.text = requireContext().getString(R.string.transfer_rx_tx, formatBytes(rx), formatBytes(tx))
                peer.transferLabel.visibility = View.VISIBLE
                peer.transferText.visibility = View.VISIBLE
            }
        }
    }
}
