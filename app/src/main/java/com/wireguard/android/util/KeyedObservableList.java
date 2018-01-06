package com.wireguard.android.util;

import android.databinding.ObservableList;

/**
 * A list that is both keyed and observable.
 */

public interface KeyedObservableList<K, E extends Keyed<? extends K>>
        extends KeyedList<K, E>, ObservableList<E> {
}
