/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.daemon

data class Daemon(
    val daemonId: Int,
    val name: String,
    val ipAddress: String,
    val privateKey: String? = null
)

data class DeletedDaemon(
    val daemonId: Int,
)

data class NetworkIsolationDaemon(
    val daemonId: Int,
    val companyName: String,
    val configurationString: String,
)

data class DaemonInfo(
    val daemonId: Int,
    val isApproved: Boolean,
    val name: String
)