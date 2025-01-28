/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.daemon

import android.util.Log
import com.jimberisolation.android.util.getCookieString
import org.json.JSONObject

suspend fun getExistingDaemons(userId: Int, company: String): Result<List<Daemon>> {
    return try {
        val cookies = getCookieString();

        val response = ApiClient.apiService.getExistingDaemons(userId, company, cookies)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            errorBody?.let {
                val jsonObject = JSONObject(it)
                val message = jsonObject.getString("message")

                return Result.failure(Exception(message))
            }
        }

        val result = response.body() ?: return Result.failure(NullPointerException("Response body is null"))
        val daemons = result.map { d ->
            Daemon(
                daemonId = d.id,
                name = d.name,
                ipAddress = d.ipAddress
            )
        }

        Result.success(daemons)
    } catch (e: Exception) {
        Log.e("GET_EXISTING_DAEMONS", "ERROR IN GET EXISTING DAEMONS", e)
        Result.failure(e)
    }
}

suspend fun createDaemon(userId: Int, company: String, daemonData: CreateDaemonApiRequest): Result<Daemon> {
    return try {
        val cookies = getCookieString()

        val response = ApiClient.apiService.createDaemon(userId, company, daemonData, cookies)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            errorBody?.let {
                val jsonObject = JSONObject(it)
                val message = jsonObject.getString("message")

                return Result.failure(Exception(message))
            }
        }

        val result = response.body() ?: return Result.failure(NullPointerException("Response body is null"))
        val daemon = Daemon(daemonId = result.id, ipAddress = result.ipAddress, name = result.name)

        Result.success(daemon)
    } catch (e: Exception) {
        Log.e("CREATE_DAEMON", "ERROR IN CREATE DAEMON", e)
        Result.failure(e)
    }
}

suspend fun deleteDaemon(userId: Int, company: String, daemonId: String): Result<DeletedDaemon> {
    return try {
        val cookies = getCookieString()

        val response = ApiClient.apiService.deleteDaemon(userId, company, daemonId, cookies)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            errorBody?.let {
                val jsonObject = JSONObject(it)
                val message = jsonObject.getString("message")

                return Result.failure(Exception(message))
            }
        }

        val result = response.body() ?: return Result.failure(NullPointerException("Response body is null"))
        val daemon =  DeletedDaemon(daemonId = result.id)

        Result.success(daemon)
    } catch (e: Exception) {
        Log.e("CREATE_DAEMON", "ERROR IN DELETE DAEMON", e)
        Result.failure(e)
    }
}