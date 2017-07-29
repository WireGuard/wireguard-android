package com.wireguard.android;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableList;
import android.databinding.ViewDataBinding;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;

/**
 * A generic ListAdapter backed by an ObservableList.
 */

class ObservableListAdapter<T> extends BaseAdapter implements ListAdapter {
    private final int layoutId;
    private final LayoutInflater layoutInflater;
    private ObservableList<T> list;
    private final OnListChangedCallback<ObservableList<T>> callback = new OnListChangedCallback<>();

    ObservableListAdapter(Context context, int layoutId, ObservableList<T> list) {
        this.layoutInflater = LayoutInflater.from(context);
        this.layoutId = layoutId;
        setList(list);
    }

    @Override
    public int getCount() {
        return list != null ? list.size() : 0;
    }

    @Override
    public T getItem(int position) {
        return list != null ? list.get(position) : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewDataBinding binding = DataBindingUtil.getBinding(convertView);
        if (binding == null)
            binding = DataBindingUtil.inflate(layoutInflater, layoutId, parent, false);
        binding.setVariable(BR.item, getItem(position));
        binding.executePendingBindings();
        return binding.getRoot();
    }

    public void setList(ObservableList<T> newList) {
        if (list != null)
            list.removeOnListChangedCallback(callback);
        list = newList;
        if (list != null) {
            list.addOnListChangedCallback(callback);
        }
    }

    private class OnListChangedCallback<L extends ObservableList<T>>
            extends ObservableList.OnListChangedCallback<L> {
        @Override
        public void onChanged(L sender) {
            ObservableListAdapter.this.notifyDataSetChanged();
        }

        @Override
        public void onItemRangeChanged(L sender, int positionStart, int itemCount) {
            ObservableListAdapter.this.notifyDataSetChanged();
        }

        @Override
        public void onItemRangeInserted(L sender, int positionStart, int itemCount) {
            ObservableListAdapter.this.notifyDataSetChanged();
        }

        @Override
        public void onItemRangeMoved(L sender, int fromPosition, int toPosition,
                                     int itemCount) {
            ObservableListAdapter.this.notifyDataSetChanged();
        }

        @Override
        public void onItemRangeRemoved(L sender, int positionStart, int itemCount) {
            ObservableListAdapter.this.notifyDataSetChanged();
        }
    }
}
