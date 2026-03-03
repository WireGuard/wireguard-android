/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.wireguard.android.R
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.fragment.TunnelDetailFragment
import com.wireguard.android.fragment.TunnelEditorFragment
import com.wireguard.android.model.ObservableTunnel
import com.wireguard.config.Config
import kotlinx.coroutines.launch

class MainActivity : BaseActivity(), FragmentManager.OnBackStackChangedListener {
    private var actionBar: ActionBar? = null
    private var isTwoPaneLayout = false
    private var backPressedCallback: OnBackPressedCallback? = null

    /**
     * ✅ Import default config from assets if no tunnel exists
     */
    private fun importDefaultConfigIfNeeded() {
        val tunnelManager = application.tunnelManager

        if (tunnelManager.tunnels.isEmpty()) {
            lifecycleScope.launch {
                try {
                    val inputStream = assets.open("myvpn.conf")
                    val configText = inputStream.bufferedReader().use { it.readText() }
                    val config = Config.parse(configText.byteInputStream())

                    val tunnel = tunnelManager.create("MyVPN", config)

                    // 🔥 Auto connect
                    tunnel.setState(Tunnel.State.UP)

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun handleBackPressed() {
        val backStackEntries = supportFragmentManager.backStackEntryCount
        if (isTwoPaneLayout && backStackEntries <= 1) {
            finish()
            return
        }

        if (backStackEntries >= 1)
            supportFragmentManager.popBackStack()

        if (backStackEntries == 1)
            selectedTunnel = null
    }

    override fun onBackStackChanged() {
        val backStackEntries = supportFragmentManager.backStackEntryCount
        backPressedCallback?.isEnabled = backStackEntries >= 1
        if (actionBar == null) return
        val minBackStackEntries = if (isTwoPaneLayout) 2 else 1
        actionBar!!.setDisplayHomeAsUpEnabled(backStackEntries >= minBackStackEntries)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        actionBar = supportActionBar
        isTwoPaneLayout = findViewById<View?>(R.id.master_detail_wrapper) != null
        supportFragmentManager.addOnBackStackChangedListener(this)
        backPressedCallback = onBackPressedDispatcher.addCallback(this) { handleBackPressed() }
        onBackStackChanged()

        // ✅ Import & Auto Connect
        importDefaultConfigIfNeeded()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }

            R.id.menu_action_edit -> {
                supportFragmentManager.commit {
                    replace(
                        if (isTwoPaneLayout) R.id.detail_container
                        else R.id.list_detail_container,
                        TunnelEditorFragment()
                    )
                    setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    addToBackStack(null)
                }
                true
            }

            R.id.menu_action_save -> false

            R.id.menu_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSelectedTunnelChanged(
        oldTunnel: ObservableTunnel?,
        newTunnel: ObservableTunnel?
    ): Boolean {
        val fragmentManager = supportFragmentManager
        if (fragmentManager.isStateSaved) {
            return false
        }

        val backStackEntries = fragmentManager.backStackEntryCount
        if (newTunnel == null) {
            fragmentManager.popBackStackImmediate(
                0,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
            return true
        }

        if (backStackEntries == 2) {
            fragmentManager.popBackStackImmediate()
        } else if (backStackEntries == 0) {
            fragmentManager.commit {
                add(
                    if (isTwoPaneLayout) R.id.detail_container
                    else R.id.list_detail_container,
                    TunnelDetailFragment()
                )
                setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                addToBackStack(null)
            }
        }
        return true
    }
}