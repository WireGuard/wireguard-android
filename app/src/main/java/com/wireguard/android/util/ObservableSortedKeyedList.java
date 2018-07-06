/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util;

import com.wireguard.util.Keyed;
import com.wireguard.util.SortedKeyedList;

/**
 * A list that is both sorted/keyed and observable.
 */

public interface ObservableSortedKeyedList<K, E extends Keyed<? extends K>>
        extends ObservableKeyedList<K, E>, SortedKeyedList<K, E> {
}
