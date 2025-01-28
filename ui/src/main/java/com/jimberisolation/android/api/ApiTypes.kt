/*
 * Copyright © 2017-2024 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.api

data class Company(
    val name: String
)

data class RefreshResult(
    val accessToken: String,
)

data class UserAuthenticationResult(
    val id: Int,
    val email: String,
    val company: Company
)

data class CloudControllerResult(
    // NC information encrypted with the public key of the daemon
    val ncInformation: String,
)

data class CloudControllerData(
    val routerPublicKey: String,
    val ipAddress: String,
    val endpointAddress: String
)

data class GetDaemonsNameResult(
    val id: Int,
    val name: String,
    val ipAddress: String
)

data class CreateDaemonData(
    val publicKey: String,
    val name: String
)

data class CreatedDaemonResult(
    val id: Int,
    val ipAddress: String,
)

data class DeleteDaemonResult(
    val id: Int,
)

data class GetEmailVerificationCodeData(
    val email: String,
)

data class EmailVerificationData(
    val email: String,
    val token: Int
)

data class CreateDaemonResult(
    val wireguardConfig: String,
    val company: String,
    val daemonId: Int
)