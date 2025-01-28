/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.daemon

// Daemon
data class GetDaemonApiResult(
    val id: Int,
    val name: String,
    val ipAddress: String
)

data class CreateDaemonApiResult(
    val id: Int,
    val ipAddress: String,
    val name: String
)

data class DeleteDaemonApiResult(
    val id: Int,
)

data class CreateDaemonApiRequest(
    val publicKey: String,
    val name: String
)