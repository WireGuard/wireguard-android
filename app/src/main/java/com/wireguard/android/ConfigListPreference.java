package com.wireguard.android;

import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.wireguard.android.backends.VpnService;

import java.util.Set;

/**
 * ListPreference that is automatically filled with the list of configurations.
 */

public class ConfigListPreference extends ListPreference {
    public ConfigListPreference(final Context context, final AttributeSet attrs,
                                final int defStyleAttr, final int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final Set<String> entrySet = VpnService.getInstance().getConfigs().keySet();
        final CharSequence[] entries = entrySet.toArray(new CharSequence[entrySet.size()]);
        setEntries(entries);
        setEntryValues(entries);
    }

    public ConfigListPreference(final Context context, final AttributeSet attrs,
                                final int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ConfigListPreference(final Context context, final AttributeSet attrs) {
        this(context, attrs, android.R.attr.dialogPreferenceStyle);
    }

    public ConfigListPreference(final Context context) {
        this(context, null);
    }
}
