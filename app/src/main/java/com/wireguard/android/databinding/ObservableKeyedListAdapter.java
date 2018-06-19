/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android.databinding;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableList;
import android.databinding.ViewDataBinding;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.wireguard.android.BR;
import com.wireguard.util.Keyed;
import com.wireguard.android.util.ObservableKeyedList;

import java.lang.ref.WeakReference;

/**
 * A generic {@code ListAdapter} backed by a {@code ObservableKeyedList}.
 */

class ObservableKeyedListAdapter<K, E extends Keyed<? extends K>> extends BaseAdapter {
    private final OnListChangedCallback<E> callback = new OnListChangedCallback<>(this);
    private final int layoutId;
    private final LayoutInflater layoutInflater;
    private ObservableKeyedList<K, E> list;

    ObservableKeyedListAdapter(final Context context, final int layoutId,
                               final ObservableKeyedList<K, E> list) {
        this.layoutId = layoutId;
        layoutInflater = LayoutInflater.from(context);
        setList(list);
    }

    @Override
    public int getCount() {
        return list != null ? list.size() : 0;
    }

    @Override
    public E getItem(final int position) {
        if (list == null || position < 0 || position >= list.size())
            return null;
        return list.get(position);
    }

    @Override
    public long getItemId(final int position) {
        final K key = getKey(position);
        return key != null ? key.hashCode() : -1;
    }

    private K getKey(final int position) {
        final E item = getItem(position);
        return item != null ? item.getKey() : null;
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        ViewDataBinding binding = DataBindingUtil.getBinding(convertView);
        if (binding == null)
            binding = DataBindingUtil.inflate(layoutInflater, layoutId, parent, false);
        binding.setVariable(BR.collection, list);
        binding.setVariable(BR.key, getKey(position));
        binding.setVariable(BR.item, getItem(position));
        binding.executePendingBindings();
        return binding.getRoot();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    void setList(final ObservableKeyedList<K, E> newList) {
        if (list != null)
            list.removeOnListChangedCallback(callback);
        list = newList;
        if (list != null) {
            list.addOnListChangedCallback(callback);
        }
        notifyDataSetChanged();
    }

    private static final class OnListChangedCallback<E extends Keyed<?>>
            extends ObservableList.OnListChangedCallback<ObservableList<E>> {

        private final WeakReference<ObservableKeyedListAdapter<?, E>> weakAdapter;

        private OnListChangedCallback(final ObservableKeyedListAdapter<?, E> adapter) {
            weakAdapter = new WeakReference<>(adapter);
        }

        @Override
        public void onChanged(final ObservableList<E> sender) {
            final ObservableKeyedListAdapter adapter = weakAdapter.get();
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
}
