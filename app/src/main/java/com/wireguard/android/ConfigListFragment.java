package com.wireguard.android;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.wireguard.android.databinding.ConfigListFragmentBinding;
import com.wireguard.config.Config;

/**
 * Fragment containing the list of known WireGuard configurations.
 */

public class ConfigListFragment extends BaseConfigFragment {

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup parent,
                             final Bundle savedInstanceState) {
        final ConfigListFragmentBinding binding =
                ConfigListFragmentBinding.inflate(inflater, parent, false);
        binding.setConfigs(VpnService.getInstance().getConfigs());
        final ListView listView = binding.getRoot().findViewById(R.id.config_list);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view,
                                    final int position, final long id) {
                final Config config = (Config) parent.getItemAtPosition(position);
                setCurrentConfig(config);
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> parent, final View view,
                                           final int position, final long id) {
                final Config config = (Config) parent.getItemAtPosition(position);
                final VpnService service = VpnService.getInstance();
                if (config == null || service == null)
                    return false;
                if (config.isEnabled())
                    service.disable(config.getName());
                else
                    service.enable(config.getName());
                return true;
            }
        });
        return binding.getRoot();
    }

    @Override
    protected void onCurrentConfigChanged(final Config config) {
        Log.d(getClass().getSimpleName(), "onCurrentConfigChanged config=" +
                (config != null ? config.getName() : null));
        final BaseConfigActivity activity = ((BaseConfigActivity) getActivity());
        if (activity != null && activity.getCurrentConfig() != config)
            activity.setCurrentConfig(config);
    }
}
