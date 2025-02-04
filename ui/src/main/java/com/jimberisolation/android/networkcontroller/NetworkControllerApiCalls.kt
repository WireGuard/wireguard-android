/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.jimberisolation.android.networkcontroller

import android.util.Log
import com.jimberisolation.android.util.generateSignedMessage
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder


suspend fun getDaemonConnectionData(daemonId: Int, companyName: String, sk: String): Result<NetworkController> {
    return try {
        val timestampInSeconds = (System.currentTimeMillis() / 1000)
        val timestampBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(timestampInSeconds).array()

        val authorizationHeader = generateSignedMessage(timestampBuffer, sk);

        val response = ApiClient.apiService.getCloudControllerInformation(daemonId, companyName, authorizationHeader)
        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            errorBody?.let {
                val jsonObject = JSONObject(it)
                val message = jsonObject.getString("message")

                return Result.failure(Exception(message))
            }
        }

        val result = response.body() ?: return Result.failure(NullPointerException("Response body is null"))

        val networkController = NetworkController(routerPublicKey = result.routerPublicKey, ipAddress = result.ipAddress, endpointAddress = result.endpointAddress, allowedIps = result.allowedIps)
        Result.success(networkController)
    } catch (e: Exception) {
        Log.e("GET_CLOUD_CONTROLLER_PUBLIC_KEY", "ERROR IN GET_CLOUD_CONTROLLER_PUBLIC_KEY", e)
        Result.failure(e)
    }
}
