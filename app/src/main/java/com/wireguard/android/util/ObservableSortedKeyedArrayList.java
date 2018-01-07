package com.wireguard.android.util;

import android.support.annotation.NonNull;

import java.util.AbstractList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * KeyedArrayList that enforces uniqueness and sorted order across the set of keys. This class uses
 * binary search to improve lookup and replacement times to O(log(n)). However, due to the
 * array-based nature of this class, insertion and removal of elements with anything but the largest
 * key still require O(n) time.
 */

public class ObservableSortedKeyedArrayList<K extends Comparable<? super K>,
        E extends Keyed<? extends K>> extends ObservableKeyedArrayList<K, E> {
    private final transient List<K> keyList = new KeyList<>(this);

    @Override
    public boolean add(final E e) {
        final int insertionPoint = getInsertionPoint(e);
        if (insertionPoint < 0) {
            // Skipping insertion is non-destructive if the new and existing objects are the same.
            if (e == get(-insertionPoint - 1))
                return false;
            throw new IllegalArgumentException("Element with same key already exists in list");
        }
        super.add(insertionPoint, e);
        return true;
    }

    @Override
    public void add(final int index, final E e) {
        final int insertionPoint = getInsertionPoint(e);
        if (insertionPoint < 0)
            throw new IllegalArgumentException("Element with same key already exists in list");
        if (insertionPoint != index)
            throw new IndexOutOfBoundsException("Wrong index given for element");
        super.add(index, e);
    }

    @Override
    public boolean addAll(@NonNull final Collection<? extends E> c) {
        boolean didChange = false;
        for (final E e : c)
            if (add(e))
                didChange = true;
        return didChange;
    }

    @Override
    public boolean addAll(int index, @NonNull final Collection<? extends E> c) {
        for (final E e : c)
            add(index++, e);
        return true;
    }

    private int getInsertionPoint(final E e) {
        return -Collections.binarySearch(keyList, e.getKey()) - 1;
    }

    @Override
    public int indexOfKey(final K key) {
        final int index = Collections.binarySearch(keyList, key);
        return index >= 0 ? index : -1;
    }

    @Override
    public int lastIndexOfKey(final K key) {
        // There can never be more than one element with the same key in the list.
        return indexOfKey(key);
    }

    @Override
    public E set(final int index, final E e) {
        if (e.getKey().compareTo(get(index).getKey()) != 0) {
            // Allow replacement if the new key would be inserted adjacent to the replaced element.
            final int insertionPoint = getInsertionPoint(e);
            if (insertionPoint < index || insertionPoint > index + 1)
                throw new IndexOutOfBoundsException("Wrong index given for element");
        }
        return super.set(index, e);
    }

    private static final class KeyList<K extends Comparable<? super K>,
            E extends Keyed<? extends K>> extends AbstractList<K> {
        private final ObservableSortedKeyedArrayList<K, E> list;

        private KeyList(final ObservableSortedKeyedArrayList<K, E> list) {
            this.list = list;
        }

        @Override
        public K get(final int index) {
            return list.get(index).getKey();
        }

        @Override
        public int size() {
            return list.size();
        }
    }
}
