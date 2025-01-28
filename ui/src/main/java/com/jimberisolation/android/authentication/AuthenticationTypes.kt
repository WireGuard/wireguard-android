/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.authentication

enum class AuthenticationType {
    Google,
    Microsoft
}

data class AuthenticationToken(
    val accessToken: String
)

data class UserAuthentication(
    val userId: Int,
    val companyName: String
)

data class Company(
    val name: String
)