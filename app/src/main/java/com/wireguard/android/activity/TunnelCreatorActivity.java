/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android.activity;

import android.os.Bundle;

import com.wireguard.android.fragment.TunnelEditorFragment;
import com.wireguard.android.model.Tunnel;

/**
 * Standalone activity for creating tunnels.
 */

public class TunnelCreatorActivity extends BaseActivity {
    @Override
    @SuppressWarnings("UnnecessaryFullyQualifiedName")
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportFragmentManager().findFragmentById(android.R.id.content) == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(android.R.id.content, new TunnelEditorFragment())
                    .commit();
        }
    }

    @Override
    protected void onSelectedTunnelChanged(final Tunnel oldTunnel, final Tunnel newTunnel) {
        finish();
    }
}
