package com.wireguard.android.databinding;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.databinding.ObservableMap;
import android.databinding.ViewDataBinding;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.wireguard.android.BR;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A generic ListAdapter backed by a TreeMap that adds observability.
 */

public class ObservableMapAdapter<K extends Comparable<K>, V> extends BaseAdapter {
    private final OnMapChangedCallback<K, V> callback = new OnMapChangedCallback<>(this);
    private final int layoutId;
    private final LayoutInflater layoutInflater;
    private List<K> keys;
    private ObservableNavigableMap<K, V> map;

    ObservableMapAdapter(final Context context, final int layoutId,
                         final ObservableNavigableMap<K, V> map) {
        this.layoutId = layoutId;
        layoutInflater = LayoutInflater.from(context);
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
        return map.get(getKey(position));
    }

    @Override
    public long getItemId(final int position) {
        if (map == null || position < 0 || position >= map.size())
            return -1;
        //final V item = getItem(position);
        //return item != null ? item.hashCode() : -1;
        final K key = getKey(position);
        return key.hashCode();
    }

    private K getKey(final int position) {
        return getKeys().get(position);
    }

    private List<K> getKeys() {
        if (keys == null)
            keys = new ArrayList<>(map.keySet());
        return keys;
    }

    public int getPosition(final K key) {
        if (map == null || key == null)
            return -1;
        return Collections.binarySearch(getKeys(), key);
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        ViewDataBinding binding = DataBindingUtil.getBinding(convertView);
        if (binding == null)
            binding = DataBindingUtil.inflate(layoutInflater, layoutId, parent, false);
        binding.setVariable(BR.collection, map);
        binding.setVariable(BR.key, getKey(position));
        binding.setVariable(BR.item, getItem(position));
        binding.executePendingBindings();
        return binding.getRoot();
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    void setMap(final ObservableNavigableMap<K, V> newMap) {
        if (map != null)
            map.removeOnMapChangedCallback(callback);
        keys = null;
        map = newMap;
        if (map != null) {
            map.addOnMapChangedCallback(callback);
        }
        notifyDataSetChanged();
    }

    private static final class OnMapChangedCallback<K extends Comparable<K>, V>
            extends ObservableMap.OnMapChangedCallback<ObservableNavigableMap<K, V>, K, V> {

        private final WeakReference<ObservableMapAdapter<K, V>> weakAdapter;

        private OnMapChangedCallback(final ObservableMapAdapter<K, V> adapter) {
            weakAdapter = new WeakReference<>(adapter);
        }

        @Override
        public void onMapChanged(final ObservableNavigableMap<K, V> sender, final K key) {
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
