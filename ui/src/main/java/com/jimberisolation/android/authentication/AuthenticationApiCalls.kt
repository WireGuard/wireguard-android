/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.authentication

import android.util.Log
import com.jimberisolation.android.storage.SharedStorage
import com.jimberisolation.android.util.getCookieString
import com.jimberisolation.android.util.saveDataToLocalStorage
import org.json.JSONObject

suspend fun getUserAuthentication(idToken: String, authenticationType: AuthenticationType): Result<UserAuthentication> {
    val type = when (authenticationType) {
        AuthenticationType.Google -> "google"
        AuthenticationType.Microsoft -> "microsoft"
    }

    return try {
        val authRequest = AuthenticationApiRequest(idToken)

        val response = ApiClient.apiService.getUserAuthentication(type, authRequest)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            errorBody?.let {
                val jsonObject = JSONObject(it)
                val message = jsonObject.getString("message")

                return Result.failure(Exception(message))
            }
        }

        val result = response.body() ?: return Result.failure(NullPointerException("Response body is null"))
        val cookies = response.headers().values("Set-Cookie")

        saveDataToLocalStorage(cookies.joinToString("; "), result.id, result.company.name)

        val userAuthentication = UserAuthentication(userId = result.id, companyName = result.company.name)
        Result.success(userAuthentication)

    } catch (e: Exception) {
        Log.e("USER_AUTHENTICATION", "ERROR IN USER AUTHENTICATION", e)
        Result.failure(e)
    }
}

suspend fun refreshToken(): Result<AuthenticationToken> {
    return try {
        val cookies = getCookieString()

        val response = ApiClient.apiService.refreshToken(cookies)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            errorBody?.let {
                val jsonObject = JSONObject(it)
                val message = jsonObject.getString("message")

                return Result.failure(Exception(message))
            }
        }

        val result = response.body() ?: return Result.failure(NullPointerException("Response body is null"))
        SharedStorage.getInstance().saveAuthenticationToken(result.accessToken)

        val authenticationToken = AuthenticationToken(result.accessToken)
        Result.success(authenticationToken)

    } catch (e: Exception) {
        Log.e("REFRESH_TOKEN", "ERROR IN GET REFRESH TOKEN", e)
        Result.failure(e)
    }
}


suspend fun logout(): Result<Boolean> {
    return try {
        val cookies = getCookieString()

        val response = ApiClient.apiService.logout(cookies)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            errorBody?.let {
                val jsonObject = JSONObject(it)
                val message = jsonObject.getString("message")

                return Result.failure(Exception(message))
            }
        }

        val result = response.body() ?: return Result.failure(NullPointerException("Response body is null"))
        Result.success(result)

    } catch (e: Exception) {
        Log.e("LOGOUT", "ERROR IN LOGOUT", e)
        Result.failure(e)
    }
}