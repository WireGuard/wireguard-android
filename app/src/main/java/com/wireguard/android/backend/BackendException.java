/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.backend;

public final class BackendException extends Exception {
    public enum Reason {
        UNKNOWN_KERNEL_MODULE_NAME,
        WG_QUICK_CONFIG_ERROR_CODE,
        TUNNEL_MISSING_CONFIG,
        VPN_NOT_AUTHORIZED,
        UNABLE_TO_START_VPN,
        TUN_CREATION_ERROR,
        GO_ACTIVATION_ERROR_CODE

    }
    private final Reason reason;
    private final Object[] format;
    public BackendException(final Reason reason, final Object ...format) {
        this.reason = reason;
        this.format = format;
    }
    public Reason getReason() {
        return reason;
    }
    public Object[] getFormat() {
        return format;
    }
}
