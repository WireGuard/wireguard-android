package com.wireguard.android.databinding;

import android.databinding.ObservableMap;

import java.util.NavigableMap;

/**
 * Interface for maps that are both observable and sorted.
 */

public interface ObservableNavigableMap<K, V> extends NavigableMap<K, V>, ObservableMap<K, V> {
    // No additional methods.
}
