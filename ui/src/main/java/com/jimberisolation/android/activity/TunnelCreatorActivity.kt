/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.jimberisolation.android.activity

import android.os.Bundle
import androidx.fragment.app.commit
import com.jimberisolation.android.fragment.TunnelEditorFragment
import com.jimberisolation.android.model.ObservableTunnel

/**
 * Standalone activity for creating tunnels.
 */
class TunnelCreatorActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (supportFragmentManager.findFragmentById(android.R.id.content) == null) {
            supportFragmentManager.commit {
                add(android.R.id.content, TunnelEditorFragment())
            }
        }
    }

    override fun onSelectedTunnelChanged(oldTunnel: ObservableTunnel?, newTunnel: ObservableTunnel?): Boolean {
        finish()
        return true
    }
}
