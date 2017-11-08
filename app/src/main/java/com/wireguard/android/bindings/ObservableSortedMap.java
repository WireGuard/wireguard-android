package com.wireguard.android.bindings;

import android.databinding.ObservableMap;

import java.util.SortedMap;

/**
 * Interface for maps that are both observable and sorted.
 */

public interface ObservableSortedMap<K, V> extends ObservableMap<K, V>, SortedMap<K, V> {
    // No additional methods.
}
