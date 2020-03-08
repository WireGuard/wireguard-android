/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import java.util.regex.Pattern;

/**
 * Represents a WireGuard tunnel.
 */

public interface Tunnel {
    enum State {
        DOWN,
        TOGGLE,
        UP;

        public static State of(final boolean running) {
            return running ? UP : DOWN;
        }
    }

    int NAME_MAX_LENGTH = 15;
    Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_=+.-]{1,15}");

    static boolean isNameInvalid(final CharSequence name) {
        return !NAME_PATTERN.matcher(name).matches();
    }

    String getName();
}
