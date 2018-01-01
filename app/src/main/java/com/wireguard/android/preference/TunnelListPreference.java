package com.wireguard.android.preference;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.wireguard.android.Application;

import java.util.Set;

/**
 * ListPreference that is automatically filled with the list of configurations.
 */

public class TunnelListPreference extends ListPreference {
    public TunnelListPreference(final Context context, final AttributeSet attrs,
                                final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final Set<String> entrySet = Application.getComponent().getTunnelManager().getTunnels().keySet();
        final CharSequence[] entries = entrySet.toArray(new CharSequence[entrySet.size()]);
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
