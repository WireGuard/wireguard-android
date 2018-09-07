/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.databinding;

import android.databinding.BindingAdapter;
import android.databinding.ObservableList;
import android.databinding.adapters.ListenerUtil;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputFilter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wireguard.android.R;
import com.wireguard.android.databinding.ObservableKeyedRecyclerViewAdapter.RowConfigurationHandler;
import com.wireguard.android.util.ObservableKeyedList;
import com.wireguard.android.widget.ToggleSwitch;
import com.wireguard.android.widget.ToggleSwitch.OnBeforeCheckedChangeListener;
import com.wireguard.util.Keyed;

/**
 * Static methods for use by generated code in the Android data binding library.
 */

@SuppressWarnings("unused")
public final class BindingAdapters {
    private BindingAdapters() {
        // Prevent instantiation.
    }

    @BindingAdapter("checked")
    public static void setChecked(final ToggleSwitch view, final boolean checked) {
        view.setCheckedInternal(checked);
    }

    @BindingAdapter("filter")
    public static void setFilter(final TextView view, final InputFilter filter) {
        view.setFilters(new InputFilter[]{filter});
    }

    @BindingAdapter({"items", "layout"})
    public static <E> void setItems(final LinearLayout view,
                                    final ObservableList<E> oldList, final int oldLayoutId,
                                    final ObservableList<E> newList, final int newLayoutId) {
        if (oldList == newList && oldLayoutId == newLayoutId)
            return;
        ItemChangeListener<E> listener = ListenerUtil.getListener(view, R.id.item_change_listener);
        // If the layout changes, any existing listener must be replaced.
        if (listener != null && oldList != null && oldLayoutId != newLayoutId) {
            listener.setList(null);
            listener = null;
            // Stop tracking the old listener.
            ListenerUtil.trackListener(view, null, R.id.item_change_listener);
        }
        // Avoid adding a listener when there is no new list or layout.
        if (newList == null || newLayoutId == 0)
            return;
        if (listener == null) {
            listener = new ItemChangeListener<>(view, newLayoutId);
            ListenerUtil.trackListener(view, listener, R.id.item_change_listener);
        }
        // Either the list changed, or this is an entirely new listener because the layout changed.
        listener.setList(newList);
    }

    @BindingAdapter(requireAll = false, value = {"items", "layout", "configurationHandler"})
    public static <K, E extends Keyed<? extends K>>
    void setItems(final RecyclerView view,
                  final ObservableKeyedList<K, E> oldList, final int oldLayoutId, final RowConfigurationHandler oldRowConfigurationHandler,
                  final ObservableKeyedList<K, E> newList, final int newLayoutId, final RowConfigurationHandler newRowConfigurationHandler) {
        if (view.getLayoutManager() == null)
            view.setLayoutManager(new LinearLayoutManager(view.getContext(), RecyclerView.VERTICAL, false));

        if (oldList == newList && oldLayoutId == newLayoutId)
            return;
        // The ListAdapter interface is not generic, so this cannot be checked.
        @SuppressWarnings("unchecked") ObservableKeyedRecyclerViewAdapter<K, E> adapter =
                (ObservableKeyedRecyclerViewAdapter<K, E>) view.getAdapter();
        // If the layout changes, any existing adapter must be replaced.
        if (adapter != null && oldList != null && oldLayoutId != newLayoutId) {
            adapter.setList(null);
            adapter = null;
        }
        // Avoid setting an adapter when there is no new list or layout.
        if (newList == null || newLayoutId == 0)
            return;
        if (adapter == null) {
            adapter = new ObservableKeyedRecyclerViewAdapter<>(view.getContext(), newLayoutId, newList);
            view.setAdapter(adapter);
        }

        adapter.setRowConfigurationHandler(newRowConfigurationHandler);
        // Either the list changed, or this is an entirely new listener because the layout changed.
        adapter.setList(newList);
    }

    @BindingAdapter("onBeforeCheckedChanged")
    public static void setOnBeforeCheckedChanged(final ToggleSwitch view,
                                                 final OnBeforeCheckedChangeListener listener) {
        view.setOnBeforeCheckedChangeListener(listener);
    }

}
