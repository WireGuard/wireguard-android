/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

import com.wireguard.util.NonNullForAll;

/**
 * A subclass of {@link Exception} that encapsulates the reasons for a failure originating in
 * implementations of {@link Backend}.
 */
@NonNullForAll
public final class BackendException extends Exception {
    private final Object[] format;
    private final Reason reason;

    /**
     * Public constructor for BackendException.
     *
     * @param reason The {@link Reason} which caused this exception to be thrown
     * @param format Format string values used when converting exceptions to user-facing strings.
     */
    public BackendException(final Reason reason, final Object... format) {
        this.reason = reason;
        this.format = format;
    }

    /**
     * Get the format string values associated with the instance.
     *
     * @return Array of {@link Object} for string formatting purposes
     */
    public Object[] getFormat() {
        return format;
    }

    /**
     * Get the reason for this exception.
     *
     * @return Associated {@link Reason} for this exception.
     */
    public Reason getReason() {
        return reason;
    }

    /**
     * Enum class containing all known reasons for why a {@link BackendException} might be thrown.
     */
    public enum Reason {
        UNKNOWN_KERNEL_MODULE_NAME,
        WG_QUICK_CONFIG_ERROR_CODE,
        TUNNEL_MISSING_CONFIG,
        VPN_NOT_AUTHORIZED,
        UNABLE_TO_START_VPN,
        TUN_CREATION_ERROR,
        GO_ACTIVATION_ERROR_CODE,
        DNS_RESOLUTION_FAILURE,
    }
}
