package com.wireguard.android.bindings;

import android.databinding.DataBindingUtil;
import android.databinding.ObservableList;
import android.databinding.ViewDataBinding;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.wireguard.android.BR;

import java.lang.ref.WeakReference;

/**
 * Helper class for binding an ObservableList to the children of a ViewGroup.
 */

class ItemChangeListener<T> {
    private final OnListChangedCallback<T> callback = new OnListChangedCallback<>(this);
    private final ViewGroup container;
    private final int layoutId;
    private final LayoutInflater layoutInflater;
    private ObservableList<T> list;

    ItemChangeListener(final ViewGroup container, final int layoutId) {
        this.container = container;
        this.layoutId = layoutId;
        layoutInflater = LayoutInflater.from(container.getContext());
    }

    private View getView(final int position, final View convertView) {
        ViewDataBinding binding = DataBindingUtil.getBinding(convertView);
        if (binding == null)
            binding = DataBindingUtil.inflate(layoutInflater, layoutId, container, false);
        binding.setVariable(BR.item, list.get(position));
        binding.executePendingBindings();
        return binding.getRoot();
    }

    public void setList(final ObservableList<T> newList) {
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

    private static class OnListChangedCallback<T>
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
