package com.wireguard.android;

import android.databinding.MapChangeRegistry;
import android.databinding.ObservableMap;
import android.support.annotation.NonNull;

import java.util.Map;
import java.util.TreeMap;

/**
 * Observable version of a TreeMap. Only notifies for changes made through methods, not iterators or
 * views. This behavior is in line with that of ObservableArrayMap.
 */

public class ObservableTreeMap<K, V> extends TreeMap<K, V> implements ObservableSortedMap<K, V> {
    private transient MapChangeRegistry listeners;

    @Override
    public void clear() {
        super.clear();
        notifyChange(null);
    }

    @Override
    public void addOnMapChangedCallback(
            final OnMapChangedCallback<? extends ObservableMap<K, V>, K, V> listener) {
        if (listeners == null)
            listeners = new MapChangeRegistry();
        listeners.add(listener);
    }

    private void notifyChange(final K key) {
        if (listeners != null)
            listeners.notifyChange(this, key);
    }

    @Override
    public V put(final K key, final V value) {
        final V oldValue = super.put(key, value);
        notifyChange(key);
        return oldValue;
    }

    @Override
    public void putAll(@NonNull final Map<? extends K, ? extends V> map) {
        super.putAll(map);
        for (final K key : map.keySet())
            notifyChange(key);
    }

    @Override
    public V remove(final Object key) {
        final V oldValue = super.remove(key);
        @SuppressWarnings("unchecked")
        final K k = (K) key;
        notifyChange(k);
        return oldValue;
    }

    @Override
    public void removeOnMapChangedCallback(
            final OnMapChangedCallback<? extends ObservableMap<K, V>, K, V> listener) {
        if (listeners != null)
            listeners.remove(listener);
    }
}
