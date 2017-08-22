package com.wireguard.android;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;

import com.wireguard.android.databinding.ConfigListFragmentBinding;
import com.wireguard.config.Config;

import java.util.LinkedList;
import java.util.List;

/**
 * Fragment containing the list of known WireGuard configurations.
 */

public class ConfigListFragment extends BaseConfigFragment {
    private ListView listView;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.config_list, menu);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup parent,
                             final Bundle savedInstanceState) {
        final ConfigListFragmentBinding binding =
                ConfigListFragmentBinding.inflate(inflater, parent, false);
        binding.setConfigs(VpnService.getInstance().getConfigs());
        final View root = binding.getRoot();
        listView = root.findViewById(R.id.config_list);
        listView.setMultiChoiceModeListener(new ConfigListModeListener());
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
                setConfigChecked(null);
                listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
                listView.setItemChecked(position, true);
                return true;
            }
        });
        binding.executePendingBindings();
        setConfigChecked(getCurrentConfig());
        return root;
    }

    @Override
    protected void onCurrentConfigChanged(final Config config) {
        Log.d(getClass().getSimpleName(), "onCurrentConfigChanged config=" +
                (config != null ? config.getName() : null));
        final BaseConfigActivity activity = ((BaseConfigActivity) getActivity());
        if (activity != null)
            activity.setCurrentConfig(config);
        if (listView != null)
            setConfigChecked(config);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        listView = null;
    }

    private void setConfigChecked(final Config config) {
        if (config != null) {
            @SuppressWarnings("unchecked")
            final ObservableMapAdapter<String, Config> adapter =
                    (ObservableMapAdapter<String, Config>) listView.getAdapter();
            final int position = adapter.getItemPosition(config.getName());
            if (position >= 0)
                listView.setItemChecked(position, true);
        } else {
            final int position = listView.getCheckedItemPosition();
            if (position >= 0)
                listView.setItemChecked(position, false);
        }
    }

    private class ConfigListModeListener implements AbsListView.MultiChoiceModeListener {
        private final List<Config> configsToRemove = new LinkedList<>();

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_action_delete:
                    // Ensure an unmanaged config is never the current config.
                    if (configsToRemove.contains(getCurrentConfig()))
                        setCurrentConfig(null);
                    for (final Config config : configsToRemove)
                        VpnService.getInstance().remove(config.getName());
                    configsToRemove.clear();
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onItemCheckedStateChanged(final ActionMode mode, final int position,
                                              final long id, final boolean checked) {
            if (checked)
                configsToRemove.add((Config) listView.getItemAtPosition(position));
            else
                configsToRemove.remove(listView.getItemAtPosition(position));
            final int count = configsToRemove.size();
            final Resources resources = listView.getContext().getResources();
            mode.setTitle(resources.getQuantityString(R.plurals.list_delete_title, count, count));
        }

        @Override
        public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
            mode.getMenuInflater().inflate(R.menu.config_list_delete, menu);
            return true;
        }

        @Override
        public void onDestroyActionMode(final ActionMode mode) {
            configsToRemove.clear();
            listView.post(new Runnable() {
                @Override
                public void run() {
                    listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
                    // Restore the previous selection (before entering the action mode).
                    setConfigChecked(getCurrentConfig());
                }
            });
        }

        @Override
        public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
            configsToRemove.clear();
            return false;
        }
    }
}
