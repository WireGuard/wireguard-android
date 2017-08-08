package com.wireguard.android;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableArrayMap;
import android.databinding.ObservableMap;
import android.databinding.ViewDataBinding;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;

import java.lang.ref.WeakReference;

/**
 * A generic ListAdapter backed by an ObservableMap.
 */

class ObservableArrayMapAdapter<K, V> extends BaseAdapter implements ListAdapter {
    private final int layoutId;
    private final LayoutInflater layoutInflater;
    private ObservableArrayMap<K, V> map;
    private final OnMapChangedCallback<K, V> callback = new OnMapChangedCallback<>(this);

    ObservableArrayMapAdapter(Context context, int layoutId, ObservableArrayMap<K, V> map) {
        this.layoutInflater = LayoutInflater.from(context);
        this.layoutId = layoutId;
        setMap(map);
    }

    @Override
    public int getCount() {
        return map != null ? map.size() : 0;
    }

    @Override
    public V getItem(int position) {
        return map != null ? map.get(map.keyAt(position)) : null;
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

    public void setMap(ObservableArrayMap<K, V> newMap) {
        if (map != null)
            map.removeOnMapChangedCallback(callback);
        map = newMap;
        if (map != null) {
            map.addOnMapChangedCallback(callback);
        }
    }

    private static class OnMapChangedCallback<K, V>
            extends ObservableMap.OnMapChangedCallback<ObservableMap<K, V>, K, V> {

        private final WeakReference<ObservableArrayMapAdapter<K, V>> weakAdapter;

        private OnMapChangedCallback(ObservableArrayMapAdapter<K, V> adapter) {
            weakAdapter = new WeakReference<>(adapter);
        }

        @Override
        public void onMapChanged(ObservableMap<K, V> sender, K key) {
            final ObservableArrayMapAdapter<K, V> adapter = weakAdapter.get();
            if (adapter != null)
                adapter.notifyDataSetChanged();
            else
                sender.removeOnMapChangedCallback(this);
        }
    }
}
