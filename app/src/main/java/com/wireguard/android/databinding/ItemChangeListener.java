/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.databinding;

import androidx.databinding.DataBindingUtil;
import androidx.databinding.ObservableList;
import androidx.databinding.ViewDataBinding;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.wireguard.android.BR;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * Helper class for binding an ObservableList to the children of a ViewGroup.
 */

class ItemChangeListener<T> {
    private final OnListChangedCallback<T> callback = new OnListChangedCallback<>(this);
    private final ViewGroup container;
    private final int layoutId;
    private final LayoutInflater layoutInflater;
    @Nullable private ObservableList<T> list;

    ItemChangeListener(final ViewGroup container, final int layoutId) {
        this.container = container;
        this.layoutId = layoutId;
        layoutInflater = LayoutInflater.from(container.getContext());
    }

    private View getView(final int position, @Nullable final View convertView) {
        ViewDataBinding binding = convertView != null ? DataBindingUtil.getBinding(convertView) : null;
        if (binding == null) {
            binding = DataBindingUtil.inflate(layoutInflater, layoutId, container, false);
        }

        Objects.requireNonNull(list, "Trying to get a view while list is still null");

        binding.setVariable(BR.collection, list);
        binding.setVariable(BR.item, list.get(position));
        binding.executePendingBindings();
        return binding.getRoot();
    }

    void setList(@Nullable final ObservableList<T> newList) {
        if (list != null)
            list.removeOnListChangedCallback(callback);
        list = newList;
        if (list != null) {
            list.addOnListChangedCallback(callback);
            callback.onChanged(list);
        } else {
            container.removeAllViews();
        }
    }

    private static final class OnListChangedCallback<T>
            extends ObservableList.OnListChangedCallback<ObservableList<T>> {

        private final WeakReference<ItemChangeListener<T>> weakListener;

        private OnListChangedCallback(final ItemChangeListener<T> listener) {
            weakListener = new WeakReference<>(listener);
        }

        @Override
        public void onChanged(final ObservableList<T> sender) {
            final ItemChangeListener<T> listener = weakListener.get();
            if (listener != null) {
                // TODO: recycle views
                listener.container.removeAllViews();
                for (int i = 0; i < sender.size(); ++i)
                    listener.container.addView(listener.getView(i, null));
            } else {
                sender.removeOnListChangedCallback(this);
            }
        }

        @Override
        public void onItemRangeChanged(final ObservableList<T> sender, final int positionStart,
                                       final int itemCount) {
            final ItemChangeListener<T> listener = weakListener.get();
            if (listener != null) {
                for (int i = positionStart; i < positionStart + itemCount; ++i) {
                    final View child = listener.container.getChildAt(i);
                    listener.container.removeViewAt(i);
                    listener.container.addView(listener.getView(i, child));
                }
            } else {
                sender.removeOnListChangedCallback(this);
            }
        }

        @Override
        public void onItemRangeInserted(final ObservableList<T> sender, final int positionStart,
                                        final int itemCount) {
            final ItemChangeListener<T> listener = weakListener.get();
            if (listener != null) {
                for (int i = positionStart; i < positionStart + itemCount; ++i)
                    listener.container.addView(listener.getView(i, null));
            } else {
                sender.removeOnListChangedCallback(this);
            }
        }

        @Override
        public void onItemRangeMoved(final ObservableList<T> sender, final int fromPosition,
                                     final int toPosition, final int itemCount) {
            final ItemChangeListener<T> listener = weakListener.get();
            if (listener != null) {
                final View[] views = new View[itemCount];
                for (int i = 0; i < itemCount; ++i)
                    views[i] = listener.container.getChildAt(fromPosition + i);
                listener.container.removeViews(fromPosition, itemCount);
                for (int i = 0; i < itemCount; ++i)
                    listener.container.addView(views[i], toPosition + i);
            } else {
                sender.removeOnListChangedCallback(this);
            }
        }

        @Override
        public void onItemRangeRemoved(final ObservableList<T> sender, final int positionStart,
                                       final int itemCount) {
            final ItemChangeListener<T> listener = weakListener.get();
            if (listener != null) {
                listener.container.removeViews(positionStart, itemCount);
            } else {
                sender.removeOnListChangedCallback(this);
            }
        }
    }
}
