/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.databinding;

import androidx.databinding.BindingAdapter;
import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableList;
import androidx.databinding.ViewDataBinding;
import androidx.databinding.adapters.ListenerUtil;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wireguard.android.BR;
import com.wireguard.android.R;
import com.wireguard.android.databinding.ObservableKeyedRecyclerViewAdapter.RowConfigurationHandler;
import com.wireguard.android.util.ObservableKeyedList;
import com.wireguard.android.widget.ToggleSwitch;
import com.wireguard.android.widget.ToggleSwitch.OnBeforeCheckedChangeListener;
import com.wireguard.config.Attribute;
import com.wireguard.config.InetNetwork;
import com.wireguard.util.Keyed;

import java9.util.Optional;

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
    public static <E>
    void setItems(final LinearLayout view,
                  @Nullable final ObservableList<E> oldList, final int oldLayoutId,
                  @Nullable final ObservableList<E> newList, final int newLayoutId) {
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

    @BindingAdapter({"items", "layout"})
    public static <E>
    void setItems(final LinearLayout view,
                  @Nullable final Iterable<E> oldList, final int oldLayoutId,
                  @Nullable final Iterable<E> newList, final int newLayoutId) {
        if (oldList == newList && oldLayoutId == newLayoutId)
            return;
        view.removeAllViews();
        if (newList == null)
            return;
        final LayoutInflater layoutInflater = LayoutInflater.from(view.getContext());
        for (final E item : newList) {
            final ViewDataBinding binding =
                    DataBindingUtil.inflate(layoutInflater, newLayoutId, view, false);
            binding.setVariable(BR.collection, newList);
            binding.setVariable(BR.item, item);
            binding.executePendingBindings();
            view.addView(binding.getRoot());
        }
    }

    @BindingAdapter(requireAll = false, value = {"items", "layout", "configurationHandler"})
    public static <K, E extends Keyed<? extends K>>
    void setItems(final RecyclerView view,
                  @Nullable final ObservableKeyedList<K, E> oldList, final int oldLayoutId,
                  final RowConfigurationHandler oldRowConfigurationHandler,
                  @Nullable final ObservableKeyedList<K, E> newList, final int newLayoutId,
                  final RowConfigurationHandler newRowConfigurationHandler) {
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

    @BindingAdapter("android:text")
    public static void setText(final TextView view, final Optional<?> text) {
        view.setText(text.map(Object::toString).orElse(""));
    }

    @BindingAdapter("android:text")
    public static void setText(final TextView view, @Nullable final Iterable<InetNetwork> networks) {
        view.setText(networks != null ? Attribute.join(networks) : "");
    }
}
