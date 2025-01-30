/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.networkcontroller

// Network controller
data class NetworkControllerApiResult(
    val routerPublicKey: String,
    val ipAddress: String,
    val endpointAddress: String,
    val allowedIps: String
)
