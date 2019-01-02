/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.util;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * A list containing elements that can be looked up by key. A {@code KeyedList} cannot contain
 * {@code null} elements.
 */

public interface KeyedList<K, E extends Keyed<? extends K>> extends List<E> {
    boolean containsAllKeys(Collection<K> keys);

    boolean containsKey(K key);

    @Nullable
    E get(K key);

    @Nullable
    E getLast(K key);

    int indexOfKey(K key);

    int lastIndexOfKey(K key);
}
