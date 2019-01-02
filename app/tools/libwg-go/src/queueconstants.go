/* SPDX-License-Identifier: GPL-2.0
 *
 * Copyright (C) 2017-2019 WireGuard LLC. All Rights Reserved.
 */

package main

/* Reduce memory consumption for Android */

const (
        QueueOutboundSize          = 1024
        QueueInboundSize           = 1024
        QueueHandshakeSize         = 1024
        MaxSegmentSize             = 2200
        PreallocatedBuffersPerPool = 4096
)
