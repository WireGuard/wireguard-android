/*
 * Copyright © 2017-2018 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util;

import android.databinding.ObservableList;

import com.wireguard.util.Keyed;
import com.wireguard.util.KeyedList;

/**
 * A list that is both keyed and observable.
 */

public interface ObservableKeyedList<K, E extends Keyed<? extends K>>
        extends KeyedList<K, E>, ObservableList<E> {
}
