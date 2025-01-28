/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.util

import com.jimberisolation.android.authentication.extractToken
import com.jimberisolation.android.storage.SharedStorage

fun saveDataToLocalStorage(cookies: String, userId: Int, company: String) {
    val authToken = extractToken(cookies, "Authentication")
    val refreshToken = extractToken(cookies, "Refresh")

    val sharedStorage = SharedStorage.getInstance()

    sharedStorage.saveCurrentUserId(userId)
    sharedStorage.saveRefreshToken(refreshToken ?: "")
    sharedStorage.saveAuthenticationToken(authToken ?: "")
}

fun getCookieString(): String {
    val authToken = SharedStorage.getInstance().getAuthenticationToken();
    val refreshToken = SharedStorage.getInstance().getRefreshToken();

    return "Authentication=$authToken; Refresh=$refreshToken";
}