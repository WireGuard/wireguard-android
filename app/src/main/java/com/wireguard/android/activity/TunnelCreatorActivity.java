package com.wireguard.android.activity;

import android.os.Bundle;

import com.wireguard.android.fragment.ConfigEditorFragment;
import com.wireguard.android.model.Tunnel;

/**
 * Created by samuel on 12/29/17.
 */

public class TunnelCreatorActivity extends BaseActivity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getFragmentManager().findFragmentById(android.R.id.content) == null) {
            getFragmentManager().beginTransaction()
                    .add(android.R.id.content, new ConfigEditorFragment())
                    .commit();
        }
    }

    @Override
    protected void onSelectedTunnelChanged(final Tunnel oldTunnel, final Tunnel newTunnel) {
        finish();
    }
}
