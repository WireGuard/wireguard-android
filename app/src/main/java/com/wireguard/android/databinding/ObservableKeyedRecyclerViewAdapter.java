/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.databinding;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableList;
import android.databinding.ViewDataBinding;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.wireguard.android.BR;
import com.wireguard.android.util.ObservableKeyedList;
import com.wireguard.util.Keyed;

import java.lang.ref.WeakReference;

/**
 * A generic {@code RecyclerView.Adapter} backed by a {@code ObservableKeyedList}.
 */

public class ObservableKeyedRecyclerViewAdapter<K, E extends Keyed<? extends K>> extends Adapter<ObservableKeyedRecyclerViewAdapter.ViewHolder> {

    private final OnListChangedCallback<E> callback = new OnListChangedCallback<>(this);
    private final int layoutId;
    private final LayoutInflater layoutInflater;
    @Nullable private ObservableKeyedList<K, E> list;
    @Nullable private RowConfigurationHandler rowConfigurationHandler;

    ObservableKeyedRecyclerViewAdapter(final Context context, final int layoutId,
                                       final ObservableKeyedList<K, E> list) {
        this.layoutId = layoutId;
        layoutInflater = LayoutInflater.from(context);
        setList(list);
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    @Nullable
    private E getItem(final int position) {
        if (list == null || position < 0 || position >= list.size())
            return null;
        return list.get(position);
    }

    @Override
    public long getItemId(final int position) {
        final K key = getKey(position);
        return key != null ? key.hashCode() : -1;
    }

    @Nullable
    private K getKey(final int position) {
        final E item = getItem(position);
        return item != null ? item.getKey() : null;
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new ViewHolder(DataBindingUtil.inflate(layoutInflater, layoutId, parent, false));
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        holder.binding.setVariable(BR.collection, list);
        holder.binding.setVariable(BR.key, getKey(position));
        holder.binding.setVariable(BR.item, getItem(position));
        holder.binding.executePendingBindings();

        if (rowConfigurationHandler != null) {
            E item = getItem(position);
            if (item != null) {
                rowConfigurationHandler.onConfigureRow(holder.binding, item, position);
            }
        }
    }

    void setList(@Nullable final ObservableKeyedList<K, E> newList) {
        if (list != null)
            list.removeOnListChangedCallback(callback);
        list = newList;
        if (list != null) {
            list.addOnListChangedCallback(callback);
        }
        notifyDataSetChanged();
    }

    void setRowConfigurationHandler(final RowConfigurationHandler rowConfigurationHandler) {
        this.rowConfigurationHandler = rowConfigurationHandler;
    }

    private static final class OnListChangedCallback<E extends Keyed<?>>
            extends ObservableList.OnListChangedCallback<ObservableList<E>> {

        private final WeakReference<ObservableKeyedRecyclerViewAdapter<?, E>> weakAdapter;

        private OnListChangedCallback(final ObservableKeyedRecyclerViewAdapter<?, E> adapter) {
            weakAdapter = new WeakReference<>(adapter);
        }

        @Override
        public void onChanged(final ObservableList<E> sender) {
            final ObservableKeyedRecyclerViewAdapter adapter = weakAdapter.get();
            if (adapter != null)
                adapter.notifyDataSetChanged();
            else
                sender.removeOnListChangedCallback(this);
        }

        @Override
        public void onItemRangeChanged(final ObservableList<E> sender, final int positionStart,
                                       final int itemCount) {
            onChanged(sender);
        }

        @Override
        public void onItemRangeInserted(final ObservableList<E> sender, final int positionStart,
                                        final int itemCount) {
            onChanged(sender);
        }

        @Override
        public void onItemRangeMoved(final ObservableList<E> sender, final int fromPosition,
                                     final int toPosition, final int itemCount) {
            onChanged(sender);
        }

        @Override
        public void onItemRangeRemoved(final ObservableList<E> sender, final int positionStart,
                                       final int itemCount) {
            onChanged(sender);
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ViewDataBinding binding;

        public ViewHolder(final ViewDataBinding binding) {
            super(binding.getRoot());

            this.binding = binding;
        }
    }

    public interface RowConfigurationHandler<B extends ViewDataBinding, T> {
        void onConfigureRow(B binding, T item, int position);
    }

}
