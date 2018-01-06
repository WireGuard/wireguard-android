package com.wireguard.android.util;

import java.util.Collection;
import java.util.List;

/**
 * A list containing elements that can be looked up by key. A {@code KeyedList} cannot contain
 * {@code null} elements.
 */

public interface KeyedList<K, E extends Keyed<? extends K>> extends List<E> {
    boolean containsAllKeys(Collection<K> keys);

    boolean containsKey(K key);

    E get(K key);

    E getLast(K key);

    int indexOfKey(K key);

    int lastIndexOfKey(K key);
}
