package com.wireguard.android;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableMap;
import android.databinding.ViewDataBinding;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;

/**
 * A generic ListAdapter backed by a TreeMap that adds observability.
 */

class ObservableMapAdapter<K extends Comparable<K>, V> extends BaseAdapter implements ListAdapter {
    private ArrayList<K> keys;
    private final int layoutId;
    private final LayoutInflater layoutInflater;
    private ObservableSortedMap<K, V> map;
    private final OnMapChangedCallback<K, V> callback = new OnMapChangedCallback<>(this);

    ObservableMapAdapter(final Context context, final int layoutId,
                         final ObservableSortedMap<K, V> map) {
        layoutInflater = LayoutInflater.from(context);
        this.layoutId = layoutId;
        setMap(map);
    }

    @Override
    public int getCount() {
        return map != null ? map.size() : 0;
    }

    @Override
    public V getItem(final int position) {
        if (map == null || position < 0 || position >= map.size())
            return null;
        return map.get(getKeys().get(position));
    }

    @Override
    public long getItemId(final int position) {
        if (map == null || position < 0 || position >= map.size())
            return -1;
        return getKeys().get(position).hashCode();
    }

    public int getItemPosition(final K key) {
        if (map == null)
            return -1;
        return Collections.binarySearch(getKeys(), key);
    }

    private ArrayList<K> getKeys() {
        if (keys == null)
            keys = new ArrayList<>(map.keySet());
        return keys;
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

    public void setMap(final ObservableSortedMap<K, V> newMap) {
        if (map != null)
            map.removeOnMapChangedCallback(callback);
        keys = null;
        map = newMap;
        if (map != null) {
            map.addOnMapChangedCallback(callback);
        }
    }

    private static class OnMapChangedCallback<K extends Comparable<K>, V>
            extends ObservableMap.OnMapChangedCallback<ObservableSortedMap<K, V>, K, V> {

        private final WeakReference<ObservableMapAdapter<K, V>> weakAdapter;

        private OnMapChangedCallback(final ObservableMapAdapter<K, V> adapter) {
            weakAdapter = new WeakReference<>(adapter);
        }

        @Override
        public void onMapChanged(final ObservableSortedMap<K, V> sender, final K key) {
            final ObservableMapAdapter<K, V> adapter = weakAdapter.get();
            if (adapter != null) {
                adapter.keys = null;
                adapter.notifyDataSetChanged();
            } else {
                sender.removeOnMapChangedCallback(this);
            }
        }
    }
}
