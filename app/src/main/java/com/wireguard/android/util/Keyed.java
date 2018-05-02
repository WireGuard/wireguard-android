/*
 * Copyright © 2018 Samuel Holland <samuel@sholland.org>
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package com.wireguard.android.util;

/**
 * Interface for objects that have a identifying key of the given type.
 */

public interface Keyed<K> {
    K getKey();
}
