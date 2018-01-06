package com.wireguard.android.preference;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.wireguard.android.Application;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.TunnelManager;

import java9.util.stream.StreamSupport;

/**
 * ListPreference that is automatically filled with the list of tunnels.
 */

public class TunnelListPreference extends ListPreference {
    public TunnelListPreference(final Context context, final AttributeSet attrs,
                                final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final TunnelManager tunnelManager = Application.getComponent().getTunnelManager();
        final CharSequence[] entries = StreamSupport.stream(tunnelManager.getTunnels())
                .map(Tunnel::getName)
                .toArray(String[]::new);
        setEntries(entries);
        setEntryValues(entries);
    }

    public TunnelListPreference(final Context context, final AttributeSet attrs,
                                final int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public TunnelListPreference(final Context context, final AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public TunnelListPreference(final Context context) {
        this(context, null);
    }

    public void show() {
        showDialog(null);
    }
}
