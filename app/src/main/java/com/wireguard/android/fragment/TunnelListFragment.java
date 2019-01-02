/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.fragment;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.activity.TunnelCreatorActivity;
import com.wireguard.android.databinding.ObservableKeyedRecyclerViewAdapter;
import com.wireguard.android.databinding.TunnelListFragmentBinding;
import com.wireguard.android.databinding.TunnelListItemBinding;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.util.ErrorMessages;
import com.wireguard.android.widget.MultiselectableRelativeLayout;
import com.wireguard.android.widget.fab.FloatingActionsMenuRecyclerViewScrollListener;
import com.wireguard.config.BadConfigException;
import com.wireguard.config.Config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java9.util.concurrent.CompletableFuture;
import java9.util.stream.StreamSupport;

/**
 * Fragment containing a list of known WireGuard tunnels. It allows creating and deleting tunnels.
 */

public class TunnelListFragment extends BaseFragment {
    private static final int REQUEST_IMPORT = 1;
    private static final String TAG = "WireGuard/" + TunnelListFragment.class.getSimpleName();

    private final ActionModeListener actionModeListener = new ActionModeListener();
    @Nullable private ActionMode actionMode;
    @Nullable private TunnelListFragmentBinding binding;

    public boolean collapseActionMenu() {
        if (binding != null && binding.createMenu.isExpanded()) {
            binding.createMenu.collapse();
            return true;
        }
        return false;
    }

    private void importTunnel(@NonNull final String configText) {
        try {
            // Ensure the config text is parseable before proceeding…
            Config.parse(new ByteArrayInputStream(configText.getBytes(StandardCharsets.UTF_8)));

            // Config text is valid, now create the tunnel…
            final FragmentManager fragmentManager = getFragmentManager();
            if (fragmentManager != null)
                ConfigNamingDialogFragment.newInstance(configText).show(fragmentManager, null);
        } catch (final BadConfigException | IOException e) {
            onTunnelImportFinished(Collections.emptyList(), Collections.singletonList(e));
        }
    }

    private void importTunnel(@Nullable final Uri uri) {
        final Activity activity = getActivity();
        if (activity == null || uri == null)
            return;
        final ContentResolver contentResolver = activity.getContentResolver();

        final Collection<CompletableFuture<Tunnel>> futureTunnels = new ArrayList<>();
        final List<Throwable> throwables = new ArrayList<>();
        Application.getAsyncWorker().supplyAsync(() -> {
            final String[] columns = {OpenableColumns.DISPLAY_NAME};
            String name = null;
            try (Cursor cursor = contentResolver.query(uri, columns,
                    null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0))
                    name = cursor.getString(0);
            }
            if (name == null)
                name = Uri.decode(uri.getLastPathSegment());
            int idx = name.lastIndexOf('/');
            if (idx >= 0) {
                if (idx >= name.length() - 1)
                    throw new IllegalArgumentException(getResources().getString(R.string.illegal_filename_error, name));
                name = name.substring(idx + 1);
            }
            boolean isZip = name.toLowerCase(Locale.ENGLISH).endsWith(".zip");
            if (name.toLowerCase(Locale.ENGLISH).endsWith(".conf"))
                name = name.substring(0, name.length() - ".conf".length());
            else if (!isZip)
                throw new IllegalArgumentException(getResources().getString(R.string.bad_extension_error));

            if (isZip) {
                try (ZipInputStream zip = new ZipInputStream(contentResolver.openInputStream(uri));
                     BufferedReader reader = new BufferedReader(new InputStreamReader(zip))) {
                    ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (entry.isDirectory())
                            continue;
                        name = entry.getName();
                        idx = name.lastIndexOf('/');
                        if (idx >= 0) {
                            if (idx >= name.length() - 1)
                                continue;
                            name = name.substring(name.lastIndexOf('/') + 1);
                        }
                        if (name.toLowerCase(Locale.ENGLISH).endsWith(".conf"))
                            name = name.substring(0, name.length() - ".conf".length());
                        else
                            continue;
                        Config config = null;
                        try {
                            config = Config.parse(reader);
                        } catch (Exception e) {
                            throwables.add(e);
                        }
                        if (config != null)
                            futureTunnels.add(Application.getTunnelManager().create(name, config).toCompletableFuture());
                    }
                }
            } else {
                futureTunnels.add(Application.getTunnelManager().create(name,
                        Config.parse(contentResolver.openInputStream(uri))).toCompletableFuture());
            }

            if (futureTunnels.isEmpty()) {
                if (throwables.size() == 1)
                    throw throwables.get(0);
                else if (throwables.isEmpty())
                    throw new IllegalArgumentException(getResources().getString(R.string.no_configs_error));
            }

            return CompletableFuture.allOf(futureTunnels.toArray(new CompletableFuture[futureTunnels.size()]));
        }).whenComplete((future, exception) -> {
            if (exception != null) {
                onTunnelImportFinished(Collections.emptyList(), Collections.singletonList(exception));
            } else {
                future.whenComplete((ignored1, ignored2) -> {
                    final List<Tunnel> tunnels = new ArrayList<>(futureTunnels.size());
                    for (final CompletableFuture<Tunnel> futureTunnel : futureTunnels) {
                        Tunnel tunnel = null;
                        try {
                            tunnel = futureTunnel.getNow(null);
                        } catch (final Exception e) {
                            throwables.add(e);
                        }
                        if (tunnel != null)
                            tunnels.add(tunnel);
                    }
                    onTunnelImportFinished(tunnels, throwables);
                });
            }
        });
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            final Collection<Integer> checkedItems = savedInstanceState.getIntegerArrayList("CHECKED_ITEMS");
            if (checkedItems != null) {
                for (final Integer i : checkedItems)
                    actionModeListener.setItemChecked(i, true);
            }
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, @Nullable final Intent data) {
        switch (requestCode) {
            case REQUEST_IMPORT:
                if (resultCode == Activity.RESULT_OK && data != null)
                    importTunnel(data.getData());
                return;
            case IntentIntegrator.REQUEST_CODE:
                final IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
                if (result != null && result.getContents() != null) {
                    importTunnel(result.getContents());
                }
                return;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = TunnelListFragmentBinding.inflate(inflater, container, false);

        binding.tunnelList.setOnTouchListener((view, motionEvent) -> {
            if (binding != null) {
                binding.createMenu.collapse();
            }
            return false;
        });
        binding.tunnelList.setOnScrollListener(new FloatingActionsMenuRecyclerViewScrollListener(binding.createMenu));
        binding.executePendingBindings();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onPause() {
        if (binding != null) {
            binding.createMenu.collapse();
        }
        super.onPause();
    }

    public void onRequestCreateConfig(@SuppressWarnings("unused") final View view) {
        startActivity(new Intent(getActivity(), TunnelCreatorActivity.class));
        if (binding != null)
            binding.createMenu.collapse();
    }

    public void onRequestImportConfig(@SuppressWarnings("unused") final View view) {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT);
        if (binding != null)
            binding.createMenu.collapse();
    }

    public void onRequestScanQRCode(@SuppressWarnings("unused") final View view) {
        final IntentIntegrator intentIntegrator = IntentIntegrator.forSupportFragment(this);
        intentIntegrator.setOrientationLocked(false);
        intentIntegrator.setBeepEnabled(false);
        intentIntegrator.setPrompt(getString(R.string.qr_code_hint));
        intentIntegrator.initiateScan(Collections.singletonList(IntentIntegrator.QR_CODE));

        if (binding != null)
            binding.createMenu.collapse();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putIntegerArrayList("CHECKED_ITEMS", actionModeListener.getCheckedItems());
    }

    @Override
    public void onSelectedTunnelChanged(@Nullable final Tunnel oldTunnel, @Nullable final Tunnel newTunnel) {
        if (binding == null)
            return;
        Application.getTunnelManager().getTunnels().thenAccept(tunnels -> {
            if (newTunnel != null)
                viewForTunnel(newTunnel, tunnels).setSingleSelected(true);
            if (oldTunnel != null)
                viewForTunnel(oldTunnel, tunnels).setSingleSelected(false);
        });
    }

    private void onTunnelDeletionFinished(final Integer count, @Nullable final Throwable throwable) {
        final String message;
        if (throwable == null) {
            message = getResources().getQuantityString(R.plurals.delete_success, count, count);
        } else {
            final String error = ErrorMessages.get(throwable);
            message = getResources().getQuantityString(R.plurals.delete_error, count, count, error);
            Log.e(TAG, message, throwable);
        }
        if (binding != null) {
            Snackbar.make(binding.mainContainer, message, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onTunnelImportFinished(final List<Tunnel> tunnels, final Collection<Throwable> throwables) {
        String message = null;

        for (final Throwable throwable : throwables) {
            final String error = ErrorMessages.get(throwable);
            message = getString(R.string.import_error, error);
            Log.e(TAG, message, throwable);
        }

        if (tunnels.size() == 1 && throwables.isEmpty())
            message = getString(R.string.import_success, tunnels.get(0).getName());
        else if (tunnels.isEmpty() && throwables.size() == 1)
            /* Use the exception message from above. */ ;
        else if (throwables.isEmpty())
            message = getResources().getQuantityString(R.plurals.import_total_success,
                    tunnels.size(), tunnels.size());
        else if (!throwables.isEmpty())
            message = getResources().getQuantityString(R.plurals.import_partial_success,
                    tunnels.size() + throwables.size(),
                    tunnels.size(), tunnels.size() + throwables.size());

        if (binding != null)
            Snackbar.make(binding.mainContainer, message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onViewStateRestored(@Nullable final Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);

        if (binding == null) {
            return;
        }

        binding.setFragment(this);
        Application.getTunnelManager().getTunnels().thenAccept(binding::setTunnels);
        binding.setRowConfigurationHandler((ObservableKeyedRecyclerViewAdapter.RowConfigurationHandler<TunnelListItemBinding, Tunnel>) (binding, tunnel, position) -> {
            binding.setFragment(this);
            binding.getRoot().setOnClickListener(clicked -> {
                if (actionMode == null) {
                    setSelectedTunnel(tunnel);
                } else {
                    actionModeListener.toggleItemChecked(position);
                }
            });
            binding.getRoot().setOnLongClickListener(clicked -> {
                actionModeListener.toggleItemChecked(position);
                return true;
            });

            if (actionMode != null)
                ((MultiselectableRelativeLayout) binding.getRoot()).setMultiSelected(actionModeListener.checkedItems.contains(position));
            else
                ((MultiselectableRelativeLayout) binding.getRoot()).setSingleSelected(getSelectedTunnel() == tunnel);
        });
    }

    private MultiselectableRelativeLayout viewForTunnel(final Tunnel tunnel, final List tunnels) {
        return (MultiselectableRelativeLayout) binding.tunnelList.findViewHolderForAdapterPosition(tunnels.indexOf(tunnel)).itemView;
    }

    private final class ActionModeListener implements ActionMode.Callback {
        private final Collection<Integer> checkedItems = new HashSet<>();

        @Nullable private Resources resources;

        public ArrayList<Integer> getCheckedItems() {
            return new ArrayList<>(checkedItems);
        }

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_action_delete:
                    final Iterable<Integer> copyCheckedItems = new HashSet<>(checkedItems);
                    Application.getTunnelManager().getTunnels().thenAccept(tunnels -> {
                        final Collection<Tunnel> tunnelsToDelete = new ArrayList<>();
                        for (final Integer position : copyCheckedItems)
                            tunnelsToDelete.add(tunnels.get(position));

                        final CompletableFuture[] futures = StreamSupport.stream(tunnelsToDelete)
                                .map(Tunnel::delete)
                                .toArray(CompletableFuture[]::new);
                        CompletableFuture.allOf(futures)
                                .thenApply(x -> futures.length)
                                .whenComplete(TunnelListFragment.this::onTunnelDeletionFinished);

                    });
                    checkedItems.clear();
                    mode.finish();
                    return true;
                case R.id.menu_action_select_all:
                    Application.getTunnelManager().getTunnels().thenAccept(tunnels -> {
                        for (int i = 0; i < tunnels.size(); ++i) {
                            setItemChecked(i, true);
                        }
                    });
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
            actionMode = mode;
            if (getActivity() != null) {
                resources = getActivity().getResources();
            }
            mode.getMenuInflater().inflate(R.menu.tunnel_list_action_mode, menu);
            binding.tunnelList.getAdapter().notifyDataSetChanged();
            return true;
        }

        @Override
        public void onDestroyActionMode(final ActionMode mode) {
            actionMode = null;
            resources = null;
            checkedItems.clear();
            binding.tunnelList.getAdapter().notifyDataSetChanged();
        }

        @Override
        public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
            updateTitle(mode);
            return false;
        }

        void setItemChecked(final int position, final boolean checked) {
            if (checked) {
                checkedItems.add(position);
            } else {
                checkedItems.remove(position);
            }

            final RecyclerView.Adapter adapter = binding == null ? null : binding.tunnelList.getAdapter();

            if (actionMode == null && !checkedItems.isEmpty() && getActivity() != null) {
                ((AppCompatActivity) getActivity()).startSupportActionMode(this);
            } else if (actionMode != null && checkedItems.isEmpty()) {
                actionMode.finish();
            }

            if (adapter != null)
                adapter.notifyItemChanged(position);

            updateTitle(actionMode);
        }

        void toggleItemChecked(final int position) {
            setItemChecked(position, !checkedItems.contains(position));
        }

        private void updateTitle(@Nullable final ActionMode mode) {
            if (mode == null) {
                return;
            }

            final int count = checkedItems.size();
            if (count == 0) {
                mode.setTitle("");
            } else {
                mode.setTitle(resources.getQuantityString(R.plurals.delete_title, count, count));
            }
        }
    }

}
