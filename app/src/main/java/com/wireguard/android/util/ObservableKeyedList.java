/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android.util;

import android.databinding.ObservableList;

/**
 * A list that is both keyed and observable.
 */

public interface ObservableKeyedList<K, E extends Keyed<? extends K>>
        extends KeyedList<K, E>, ObservableList<E> {
}
