/*
 * Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.util;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

/**
 * A keyed list where all elements are sorted by the comparator returned by {@code comparator()}
 * applied to their keys.
 */

public interface SortedKeyedList<K, E extends Keyed<? extends K>> extends KeyedList<K, E> {
    Comparator<? super K> comparator();

    @Nullable
    K firstKey();

    Set<K> keySet();

    @Nullable
    K lastKey();

    Collection<E> values();
}
