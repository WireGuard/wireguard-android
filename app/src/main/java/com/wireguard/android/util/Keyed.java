package com.wireguard.android.util;

/**
 * Interface for objects that have a identifying key of the given type.
 */

public interface Keyed<K> {
    K getKey();
}
