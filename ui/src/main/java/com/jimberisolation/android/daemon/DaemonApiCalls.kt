/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.daemon

import android.util.Log
import com.jimberisolation.android.util.generateSignedMessage
import com.jimberisolation.android.util.getCookieString
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

suspend fun createDaemon(userId: Int, company: String, daemonData: CreateDaemonApiRequest): Result<Daemon> {
    return try {
        val cookies = getCookieString()

        val response = ApiClient.apiService.createDaemon(userId, company, daemonData, cookies)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            errorBody?.let {
                val jsonObject = JSONObject(it)
                val message = jsonObject.getString("message")

                val jsonArray = JSONArray(message)
                val firstEl = jsonArray.get(0)

                return Result.failure(Exception(firstEl.toString()))
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

suspend fun deleteDaemon(daemonId: Number, company: String, sk: String): Result<DeletedDaemon> {
    return try {
        val timestampInSeconds = (System.currentTimeMillis() / 1000)
        val timestampBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(timestampInSeconds).array()

        val authorizationHeader = generateSignedMessage(timestampBuffer, sk);


        val response = ApiClient.apiService.deleteDaemon(daemonId, company, authorizationHeader)
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