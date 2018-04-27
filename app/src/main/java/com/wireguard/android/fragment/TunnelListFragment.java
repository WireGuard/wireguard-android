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
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.TextView;

import com.wireguard.android.Application;
import com.wireguard.android.Application.ApplicationComponent;
import com.wireguard.android.R;
import com.wireguard.android.activity.TunnelCreatorActivity;
import com.wireguard.android.databinding.TunnelListFragmentBinding;
import com.wireguard.android.model.Tunnel;
import com.wireguard.android.model.TunnelManager;
import com.wireguard.android.util.AsyncWorker;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.config.Config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java9.util.concurrent.CompletableFuture;
import java9.util.stream.Collectors;
import java9.util.stream.IntStream;
import java9.util.stream.StreamSupport;

/**
 * Fragment containing a list of known WireGuard tunnels. It allows creating and deleting tunnels.
 */

public class TunnelListFragment extends BaseFragment {
    private static final int REQUEST_IMPORT = 1;
    private static final String TAG = "WireGuard/" + TunnelListFragment.class.getSimpleName();

    private final MultiChoiceModeListener actionModeListener = new ActionModeListener();
    private final ListViewCallbacks listViewCallbacks = new ListViewCallbacks();
    private ActionMode actionMode;
    private AsyncWorker asyncWorker;
    private TunnelListFragmentBinding binding;
    private TunnelManager tunnelManager;

    private void importTunnel(final Uri uri) {
        final Activity activity = getActivity();
        if (activity == null)
            return;
        final ContentResolver contentResolver = activity.getContentResolver();

        final List<CompletableFuture<Tunnel>> futureTunnels = new ArrayList<>();
        final List<Throwable> throwables = new ArrayList<>();
        asyncWorker.supplyAsync(() -> {
            final String[] columns = {OpenableColumns.DISPLAY_NAME};
            String name = null;
            try (Cursor cursor = contentResolver.query(uri, columns, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0))
                    name = cursor.getString(0);
            }
            if (name == null)
                name = Uri.decode(uri.getLastPathSegment());
            int idx = name.lastIndexOf('/');
            if (idx >= 0) {
                if (idx >= name.length() - 1)
                    throw new IllegalArgumentException("Illegal file name: " + name);
                name = name.substring(idx + 1);
            }
            boolean isZip = name.toLowerCase().endsWith(".zip");
            if (name.toLowerCase().endsWith(".conf"))
                name = name.substring(0, name.length() - ".conf".length());

            if (isZip) {
                ZipInputStream zip = new ZipInputStream(contentResolver.openInputStream(uri));
                BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
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
                    if (name.toLowerCase().endsWith(".conf"))
                        name = name.substring(0, name.length() - ".conf".length());
                    else
                        continue;
                    Config config = null;
                    try {
                        config = Config.from(reader);
                    } catch (Exception e) {
                        throwables.add(e);
                    }
                    if (config != null)
                        futureTunnels.add(tunnelManager.create(name, config).toCompletableFuture());
                }
            } else {
                futureTunnels.add(tunnelManager.create(name, Config.from(contentResolver.openInputStream(uri))).toCompletableFuture());
            }

            if (futureTunnels.isEmpty() && throwables.size() == 1)
                throw throwables.get(0);

            return CompletableFuture.allOf(futureTunnels.toArray(new CompletableFuture[futureTunnels.size()]));
        }).whenComplete((future, exception) -> {
            if (exception != null) {
                this.onTunnelImportFinished(Collections.emptyList(), Collections.singletonList(exception));
            } else {
                future.whenComplete((ignored1, ignored2) -> {
                    ArrayList<Tunnel> tunnels = new ArrayList<>(futureTunnels.size());
                    for (CompletableFuture<Tunnel> futureTunnel : futureTunnels) {
                        Tunnel tunnel = null;
                        try {
                            tunnel = futureTunnel.getNow(null);
                        } catch (Exception e) {
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
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_IMPORT:
                if (resultCode == Activity.RESULT_OK)
                    importTunnel(data.getData());
                return;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ApplicationComponent applicationComponent = Application.getComponent();
        asyncWorker = applicationComponent.getAsyncWorker();
        tunnelManager = applicationComponent.getTunnelManager();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        binding = TunnelListFragmentBinding.inflate(inflater, container, false);
        binding.tunnelList.setMultiChoiceModeListener(actionModeListener);
        binding.tunnelList.setOnItemClickListener(listViewCallbacks);
        binding.tunnelList.setOnItemLongClickListener(listViewCallbacks);
        binding.tunnelList.setOnTouchListener(listViewCallbacks);
        binding.executePendingBindings();
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    public boolean collapseActionMenu() {
        if (binding.createMenu.isExpanded()) {
            binding.createMenu.collapse();
            return true;
        }
        return false;
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

    @Override
    public void onSelectedTunnelChanged(final Tunnel oldTunnel, final Tunnel newTunnel) {
        // Do nothing.
    }

    private void onTunnelDeletionFinished(final Integer count, final Throwable throwable) {
        final String message;
        if (throwable == null) {
            message = getResources().getQuantityString(R.plurals.delete_success, count, count);
        } else {
            final String error = ExceptionLoggers.unwrap(throwable).getMessage();
            message = getResources().getQuantityString(R.plurals.delete_error, count, count, error);
            Log.e(TAG, message, throwable);
        }
        if (binding != null) {
            final CoordinatorLayout container = binding.mainContainer;
            Snackbar.make(container, message, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onTunnelImportFinished(final List<Tunnel> tunnels, final List<Throwable> throwables) {
        String message = null;

        for (final Throwable throwable : throwables) {
            final String error = ExceptionLoggers.unwrap(throwable).getMessage();
            message = getString(R.string.import_error, error);
            Log.e(TAG, message, throwable);
        }

        if (tunnels.size() == 1 && throwables.isEmpty())
            message = getString(R.string.import_success, tunnels.get(0).getName());
        else if (tunnels.isEmpty() && throwables.size() == 1)
            /* Use the exception message from above. */;
        else if (throwables.isEmpty())
            message = getString(R.string.import_total_success, tunnels.size());
        else if (!throwables.isEmpty())
            message = getString(R.string.import_partial_success, tunnels.size(), tunnels.size() + throwables.size());

        if (binding != null) {
            final CoordinatorLayout container = binding.mainContainer;
            Snackbar.make(container, message, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onViewStateRestored(final Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        binding.setFragment(this);
        binding.setTunnels(tunnelManager.getTunnels());
    }

    private final class ActionModeListener implements MultiChoiceModeListener {
        private Resources resources;
        private AbsListView tunnelList;

        private IntStream getCheckedPositions() {
            final SparseBooleanArray checkedItemPositions = tunnelList.getCheckedItemPositions();
            return IntStream.range(0, checkedItemPositions.size())
                    .filter(checkedItemPositions::valueAt)
                    .map(checkedItemPositions::keyAt);
        }

        @Override
        public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_action_delete:
                    // Must operate in two steps: positions change once we start deleting things.
                    final List<Tunnel> tunnelsToDelete = getCheckedPositions()
                            .mapToObj(pos -> (Tunnel) tunnelList.getItemAtPosition(pos))
                            .collect(Collectors.toList());
                    final CompletableFuture[] futures = StreamSupport.stream(tunnelsToDelete)
                            .map(Tunnel::delete)
                            .toArray(CompletableFuture[]::new);
                    CompletableFuture.allOf(futures)
                            .thenApply(x -> futures.length)
                            .whenComplete(TunnelListFragment.this::onTunnelDeletionFinished);
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
            actionMode = mode;
            if (getActivity() != null)
                resources = getActivity().getResources();
            tunnelList = binding.tunnelList;
            mode.getMenuInflater().inflate(R.menu.tunnel_list_action_mode, menu);
            return true;
        }

        @Override
        public void onDestroyActionMode(final ActionMode mode) {
            actionMode = null;
            resources = null;
        }

        @Override
        public void onItemCheckedStateChanged(final ActionMode mode, final int position,
                                              final long id, final boolean checked) {
            updateTitle(mode);
        }

        @Override
        public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
            updateTitle(mode);
            return false;
        }

        private void updateTitle(final ActionMode mode) {
            final int count = (int) getCheckedPositions().count();
            mode.setTitle(resources.getQuantityString(R.plurals.delete_title, count, count));
        }
    }

    private final class ListViewCallbacks
            implements OnItemClickListener, OnItemLongClickListener, OnTouchListener {
        @Override
        public void onItemClick(final AdapterView<?> parent, final View view,
                                final int position, final long id) {
            setSelectedTunnel((Tunnel) parent.getItemAtPosition(position));
        }

        @Override
        public boolean onItemLongClick(final AdapterView<?> parent, final View view,
                                       final int position, final long id) {
            if (actionMode != null)
                return false;
            if (binding != null)
                binding.tunnelList.setItemChecked(position, true);
            return true;
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouch(final View view, final MotionEvent motionEvent) {
            if (binding != null)
                binding.createMenu.collapse();
            return false;
        }
    }
}
