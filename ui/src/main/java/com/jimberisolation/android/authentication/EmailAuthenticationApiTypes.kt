/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.authentication

data class VerificationCodeApiRequest(
    val email: String,
)

data class AuthenticationWithVerificationCodeApiRequest(
    val email: String,
    val token: Int
)