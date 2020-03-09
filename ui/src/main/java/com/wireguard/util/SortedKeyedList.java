/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import androidx.annotation.Nullable;

/**
 * A keyed list where all elements are sorted by the comparator returned by {@code comparator()}
 * applied to their keys.
 */

@NonNullForAll
public interface SortedKeyedList<K, E extends Keyed<? extends K>> extends KeyedList<K, E> {
    Comparator<? super K> comparator();

    @Nullable
    K firstKey();

    Set<K> keySet();

    @Nullable
    K lastKey();

    Collection<E> values();
}
