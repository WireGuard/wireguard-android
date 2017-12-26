package com.wireguard.android.databinding;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableList;
import android.databinding.ViewDataBinding;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;

import com.wireguard.android.BR;

import java.lang.ref.WeakReference;

/**
 * A generic ListAdapter backed by an ObservableList.
 */

class ObservableListAdapter<T> extends BaseAdapter implements ListAdapter {
    private final OnListChangedCallback<T> callback = new OnListChangedCallback<>(this);
    private final int layoutId;
    private final LayoutInflater layoutInflater;
    private ObservableList<T> list;

    ObservableListAdapter(final Context context, final int layoutId, final ObservableList<T> list) {
        this.layoutId = layoutId;
        layoutInflater = LayoutInflater.from(context);
        setList(list);
    }

    @Override
    public int getCount() {
        return list != null ? list.size() : 0;
    }

    @Override
    public T getItem(final int position) {
        if (list == null || position < 0 || position >= list.size())
            return null;
        return list.get(position);
    }

    @Override
    public long getItemId(final int position) {
        if (list == null || position < 0 || position >= list.size())
            return -1;
        return list.get(position).hashCode();
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        ViewDataBinding binding = DataBindingUtil.getBinding(convertView);
        if (binding == null)
            binding = DataBindingUtil.inflate(layoutInflater, layoutId, parent, false);
        binding.setVariable(BR.item, getItem(position));
        binding.executePendingBindings();
        return binding.getRoot();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    void setList(final ObservableList<T> newList) {
        if (list != null)
            list.removeOnListChangedCallback(callback);
        list = newList;
        if (list != null) {
            list.addOnListChangedCallback(callback);
        }
        notifyDataSetChanged();
    }

    private static class OnListChangedCallback<U>
            extends ObservableList.OnListChangedCallback<ObservableList<U>> {

        private final WeakReference<ObservableListAdapter<U>> weakAdapter;

        private OnListChangedCallback(final ObservableListAdapter<U> adapter) {
            weakAdapter = new WeakReference<>(adapter);
        }

        @Override
        public void onChanged(final ObservableList<U> sender) {
            final ObservableListAdapter<U> adapter = weakAdapter.get();
            if (adapter != null)
                adapter.notifyDataSetChanged();
            else
                sender.removeOnListChangedCallback(this);
        }

        @Override
        public void onItemRangeChanged(final ObservableList<U> sender, final int positionStart,
                                       final int itemCount) {
            onChanged(sender);
        }

        @Override
        public void onItemRangeInserted(final ObservableList<U> sender, final int positionStart,
                                        final int itemCount) {
            onChanged(sender);
        }

        @Override
        public void onItemRangeMoved(final ObservableList<U> sender, final int fromPosition,
                                     final int toPosition, final int itemCount) {
            onChanged(sender);
        }

        @Override
        public void onItemRangeRemoved(final ObservableList<U> sender, final int positionStart,
                                       final int itemCount) {
            onChanged(sender);
        }
    }
}
