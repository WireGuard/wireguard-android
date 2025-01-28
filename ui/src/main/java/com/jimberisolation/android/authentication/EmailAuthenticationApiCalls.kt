/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.authentication

import android.util.Log
import com.jimberisolation.android.util.saveDataToLocalStorage
import org.json.JSONObject

suspend fun sendVerificationEmail(email: String): Result<Boolean> {
    return try {
        val verificationCodeApiRequest = VerificationCodeApiRequest(email)

        val response = ApiClient.apiService.sendVerificationEmail(verificationCodeApiRequest)
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
        Log.e("EMAIL_VERIFICATION_CODE", "ERROR IN EMAIL VERIFICATION CODE REQUEST", e)
        Result.failure(e)
    }
}

suspend fun verifyEmailWithToken(emailVerificationData: AuthenticationWithVerificationCodeApiRequest): Result<UserAuthentication> {
    return try {
        val response = ApiClient.apiService.verifyEmailWithToken(emailVerificationData)

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
        Log.e("EMAIL_WITH_TOKEN", "ERROR IN EMAIL VERIFY WITH CODE CODE REQUEST", e)
        Result.failure(e)
    }
}
