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

import com.commonsware.cwac.crossport.design.widget.CoordinatorLayout;
import com.commonsware.cwac.crossport.design.widget.Snackbar;
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

import java.util.List;

import java9.util.concurrent.CompletableFuture;
import java9.util.concurrent.CompletionStage;
import java9.util.function.Function;
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
        final CompletionStage<String> nameStage = asyncWorker.supplyAsync(() -> {
            final String[] columns = {OpenableColumns.DISPLAY_NAME};
            String name = null;
            try (final Cursor cursor = contentResolver.query(uri, columns, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0))
                    name = cursor.getString(0);
            }
            if (name == null)
                name = Uri.decode(uri.getLastPathSegment());
            if (name.indexOf('/') >= 0)
                name = name.substring(name.lastIndexOf('/') + 1);
            if (name.endsWith(".conf"))
                name = name.substring(0, name.length() - ".conf".length());
            Log.d(TAG, "Import mapped URI " + uri + " to tunnel name " + name);
            return name;
        });
        asyncWorker.supplyAsync(() -> Config.from(contentResolver.openInputStream(uri)))
                .thenCombine(nameStage, (config, name) -> tunnelManager.create(name, config))
                .thenCompose(Function.identity())
                .whenComplete(this::onTunnelImportFinished);
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
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
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

    public void onRequestCreateConfig(@SuppressWarnings("unused") final View view) {
        startActivity(new Intent(getActivity(), TunnelCreatorActivity.class));
        binding.createMenu.collapse();
    }

    public void onRequestImportConfig(@SuppressWarnings("unused") final View view) {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT);
        binding.createMenu.collapse();
    }

    @Override
    public void onSelectedTunnelChanged(final Tunnel oldTunnel, final Tunnel newTunnel) {
        // Do nothing.
    }

    private void onTunnelDeletionFinished(final Integer count, final Throwable throwable) {
        final String message;
        final String plural = count == 1 ? "" : "s";
        if (throwable == null) {
            message = "Successfully deleted " + count + " tunnel" + plural;
        } else {
            message = "Unable to delete tunnel" + plural + ": "
                    + ExceptionLoggers.unwrap(throwable).getMessage();
            Log.e(TAG, "Cannot delete tunnel" + plural, throwable);
        }
        if (binding != null) {
            final CoordinatorLayout container = binding.mainContainer;
            Snackbar.make(container, message, Snackbar.LENGTH_LONG).show();
        }
    }

    private void onTunnelImportFinished(final Tunnel tunnel, final Throwable throwable) {
        final String message;
        if (throwable == null) {
            message = "Successfully imported tunnel '" + tunnel.getName() + '\'';
        } else {
            message = "Cannot import tunnel: "
                    + ExceptionLoggers.unwrap(throwable).getMessage();
            Log.e(TAG, "Cannot import tunnel", throwable);
        }
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
            mode.setTitle(resources.getQuantityString(R.plurals.list_delete_title, count, count));
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
            binding.tunnelList.setItemChecked(position, true);
            return true;
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouch(final View view, final MotionEvent motionEvent) {
            binding.createMenu.collapse();
            return false;
        }
    }
}
