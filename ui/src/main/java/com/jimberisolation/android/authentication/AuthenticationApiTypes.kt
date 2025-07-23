/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.authentication

data class UserAuthenticationApiResult(
    val id: Int,
    val email: String,
    val company: Company
)

data class RefreshTokenApiResult(
    val accessToken: String,
)

data class AuthenticationApiRequest(
    val idToken: String
)
