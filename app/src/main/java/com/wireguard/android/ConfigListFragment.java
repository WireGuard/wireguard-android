package com.wireguard.android;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;

import com.wireguard.android.backends.VpnService;
import com.wireguard.android.bindings.ObservableMapAdapter;
import com.wireguard.android.databinding.ConfigListFragmentBinding;
import com.wireguard.config.Config;

import java.util.LinkedList;
import java.util.List;

/**
 * Fragment containing the list of known WireGuard configurations.
 */

public class ConfigListFragment extends BaseConfigFragment {
    private static final int REQUEST_IMPORT = 1;

    private ConfigListFragmentBinding binding;

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_IMPORT) {
            if (resultCode == Activity.RESULT_OK)
                VpnService.getInstance().importFrom(data.getData());
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup parent,
                             final Bundle savedInstanceState) {
        binding = ConfigListFragmentBinding.inflate(inflater, parent, false);
        binding.setConfigs(VpnService.getInstance().getConfigs());
        binding.addFromFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_IMPORT);
                binding.addMenu.collapse();
            }
        });
        binding.addFromScratch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                startActivity(new Intent(getActivity(), AddActivity.class));
                binding.addMenu.collapse();
            }
        });
        binding.configList.setMultiChoiceModeListener(new ConfigListModeListener());
        binding.configList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view,
                                    final int position, final long id) {
                final Config config = (Config) parent.getItemAtPosition(position);
                setCurrentConfig(config);
            }
        });
        binding.configList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> parent, final View view,
                                           final int position, final long id) {
                setConfigChecked(null);
                binding.configList.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE_MODAL);
                binding.configList.setItemChecked(position, true);
                return true;
            }
        });
        binding.configList.setOnTouchListener(new View.OnTouchListener() {
            @Override
            @SuppressLint("ClickableViewAccessibility")
            public boolean onTouch(final View view, final MotionEvent event) {
                binding.addMenu.collapse();
                return false;
            }
        });
        binding.executePendingBindings();
        setConfigChecked(getCurrentConfig());
        return binding.getRoot();
    }

    @Override
    protected void onCurrentConfigChanged(final Config config) {
        final BaseConfigActivity activity = ((BaseConfigActivity) getActivity());
        if (activity != null)
            activity.setCurrentConfig(config);
        if (binding != null)
            setConfigChecked(config);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setConfigChecked(final Config config) {
        if (config != null) {
            @SuppressWarnings("unchecked") final ObservableMapAdapter<String, Config> adapter =
                    (ObservableMapAdapter<String, Config>) binding.configList.getAdapter();
            final int position = adapter.getPosition(config.getName());
            if (position >= 0)
                binding.configList.setItemChecked(position, true);
        } else {
            final int position = binding.configList.getCheckedItemPosition();
            if (position >= 0)
                binding.configList.setItemChecked(position, false);
        }
    }

    public boolean tryCollapseMenu() {
        if (binding != null && binding.addMenu.isExpanded()) {
            binding.addMenu.collapse();
            return true;
        }
        return false;
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
                configsToRemove.add((Config) binding.configList.getItemAtPosition(position));
            else
                configsToRemove.remove(binding.configList.getItemAtPosition(position));
            final int count = configsToRemove.size();
            final Resources resources = binding.getRoot().getContext().getResources();
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
            binding.configList.post(new Runnable() {
                @Override
                public void run() {
                    binding.configList.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
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
