/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.jimberisolation.android.activity

import android.os.Bundle
import android.content.Intent
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.ActionBar
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.jimberisolation.android.Application
import com.jimberisolation.android.Application.Companion.getTunnelManager
import com.jimberisolation.android.R
import com.jimberisolation.android.fragment.TunnelDetailFragment
import com.jimberisolation.android.fragment.TunnelEditorFragment
import com.jimberisolation.android.fragment.TunnelListFragment
import com.jimberisolation.android.model.ObservableTunnel
import com.jimberisolation.android.model.TunnelManager
import com.jimberisolation.android.storage.SharedStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CRUD interface for WireGuard tunnels. This activity serves as the main entry point to the
 * WireGuard application, and contains several fragments for listing, viewing details of, and
 * editing the configuration and interface state of WireGuard tunnels.
 */
class MainActivity : BaseActivity(), FragmentManager.OnBackStackChangedListener {
    private var actionBar: ActionBar? = null
    private var isTwoPaneLayout = false
    private var backPressedCallback: OnBackPressedCallback? = null

    private lateinit var spinner: ProgressBar
    private lateinit var detailContainer: FragmentContainerView

    private fun handleBackPressed() {
        val backStackEntries = supportFragmentManager.backStackEntryCount
        // If the two-pane layout does not have an editor open, going back should exit the app.
        if (isTwoPaneLayout && backStackEntries <= 1) {
            finish()
            return
        }

        if (backStackEntries >= 1)
            supportFragmentManager.popBackStack()

        // Deselect the current tunnel on navigating back from the detail pane to the one-pane list.
        if (backStackEntries == 1)
            selectedTunnel = null
    }

    override fun onBackStackChanged() {
        val backStackEntries = supportFragmentManager.backStackEntryCount
        backPressedCallback?.isEnabled = backStackEntries >= 1
        if (actionBar == null) return
        // Do not show the home menu when the two-pane layout is at the detail view (see above).
        val minBackStackEntries = if (isTwoPaneLayout) 2 else 1
        actionBar!!.setDisplayHomeAsUpEnabled(backStackEntries >= minBackStackEntries)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        var isInitialized = false;
        LibsodiumInitializer.initializeWithCallback {
            isInitialized = true;
        }

        while(!isInitialized) { }

        spinner = findViewById(R.id.loading_spinner)
        detailContainer = findViewById(R.id.detail_container)

        actionBar = supportActionBar

        toggleLoading(true);

        SharedStorage.initialize(this)

        supportActionBar?.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM;
        supportActionBar?.setCustomView(R.layout.jimber_action_bar);

        isTwoPaneLayout = findViewById<View?>(R.id.master_detail_wrapper) != null
        supportFragmentManager.addOnBackStackChangedListener(this)
        backPressedCallback = onBackPressedDispatcher.addCallback(this) { handleBackPressed() }
        onBackStackChanged()

        lifecycleScope.launch {
            startScreen()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // The back arrow in the action bar should act the same as the back button.
                onBackPressedDispatcher.onBackPressed()
                true
            }

            R.id.menu_action_edit -> {
                supportFragmentManager.commit {
                    replace(R.id.detail_container, TunnelEditorFragment())
                    setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    addToBackStack(null)
                }
                true
            }
            // This menu item is handled by the editor fragment.
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
            // Clear everything off the back stack (all editors and detail fragments).
            fragmentManager.popBackStackImmediate(0, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            return true
        }
        if (backStackEntries == 2) {
            // Pop the editor off the back stack to reveal the detail fragment. Use the immediate
            // method to avoid the editor picking up the new tunnel while it is still visible.
            fragmentManager.popBackStackImmediate()
        } else if (backStackEntries == 0) {
            // Create and show a new detail fragment.
            fragmentManager.commit {
                add(R.id.detail_container, TunnelDetailFragment())
                setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                addToBackStack(null)
            }
        }
        return true
    }

    private suspend fun startScreen() {
        toggleLoading(false)

        val userId = SharedStorage.getInstance().getCurrentUser()?.id
        if (userId == null) {
            withContext(Dispatchers.Main) {
                startActivity(Intent(this@MainActivity, SignInActivity::class.java))
                finish()
            }
            return
        }

        val tunnelManager = getTunnelManager();
        val tunnels = tunnelManager.getTunnelsOfUser();

        if (tunnels.isEmpty()) {
            withContext(Dispatchers.Main) {
                startActivity(Intent(this@MainActivity, SignInActivity::class.java))
                finish()
            }
            return
        }

        withContext(Dispatchers.Main) {
            supportFragmentManager.commit {
                replace(R.id.detail_container, TunnelListFragment())
            }
        }
    }

    private fun toggleLoading(isLoading: Boolean) {
        if (isLoading) {
            spinner.visibility = View.VISIBLE
            detailContainer.visibility = View.GONE
        } else {
            spinner.visibility = View.GONE
            detailContainer.visibility = View.VISIBLE
        }
    }
}
