/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util

import android.os.Handler
import android.util.Log
import androidx.annotation.StringRes
import androidx.biometric.BiometricConstants
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.wireguard.android.R

object BiometricAuthenticator {
    private const val TAG = "WireGuard/BiometricAuthenticator"
    private val handler = Handler()

    sealed class Result {
        data class Success(val cryptoObject: BiometricPrompt.CryptoObject?) : Result()
        data class Failure(val code: Int?, val message: CharSequence) : Result()
        object HardwareUnavailableOrDisabled : Result()
        object Cancelled : Result()
    }

    fun authenticate(
            @StringRes dialogTitleRes: Int,
            fragmentActivity: FragmentActivity,
            callback: (Result) -> Unit
    ) {
        val biometricManager = BiometricManager.from(fragmentActivity)
        val authCallback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.d(TAG, "BiometricAuthentication error: errorCode=$errorCode, msg=$errString")
                callback(when (errorCode) {
                    BiometricConstants.ERROR_CANCELED, BiometricConstants.ERROR_USER_CANCELED,
                    BiometricConstants.ERROR_NEGATIVE_BUTTON -> {
                        Result.Cancelled
                    }
                    BiometricConstants.ERROR_HW_NOT_PRESENT, BiometricConstants.ERROR_HW_UNAVAILABLE,
                    BiometricConstants.ERROR_NO_BIOMETRICS, BiometricConstants.ERROR_NO_DEVICE_CREDENTIAL -> {
                        Result.HardwareUnavailableOrDisabled
                    }
                    else -> Result.Failure(errorCode, fragmentActivity.getString(R.string.biometric_auth_error_reason, errString))
                })
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                callback(Result.Failure(null, fragmentActivity.getString(R.string.biometric_auth_error)))
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                callback(Result.Success(result.cryptoObject))
            }
        }
        val biometricPrompt = BiometricPrompt(fragmentActivity, { handler.post(it) }, authCallback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(fragmentActivity.getString(dialogTitleRes))
                .setDeviceCredentialAllowed(true)
                .build()

        if (biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
            biometricPrompt.authenticate(promptInfo)
        } else {
            callback(Result.HardwareUnavailableOrDisabled)
        }
    }
}
