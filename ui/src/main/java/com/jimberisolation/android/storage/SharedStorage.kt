package com.jimberisolation.android.storage

import android.content.Context
import android.content.SharedPreferences


data class WireguardKeyPair(
    val encodedPk: String,
    val baseEncodedPrivateKeyInX25519: String,
    val daemonName: String
)

class SharedStorage private constructor() {

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "com.jimberisolation.android.PREFERENCE_FILE_KEY"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val AUTHENTICATION_TOKEN_KEY = "authentication_token"
        private const val CURRENT_USER_ID = "current_user_id"
        private const val CURRENT_COMPANY = "current_company"

        private const val WIREGUARD_PK = "wireguard_encoded_pk"
        private const val WIREGUARD_SK = "wireguard_encoded_sk"
        private const val DAEMON_NAME = "wireguard_daemon_name"

        @Volatile
        private var INSTANCE: SharedStorage? = null

        fun initialize(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = SharedStorage().apply {
                            sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        }
                    }
                }
            }
        }

        fun getInstance(): SharedStorage {
            return INSTANCE ?: throw IllegalStateException("SharedStorage is not initialized, call initialize(context) first.")
        }
    }

    // Finalizer method
    protected fun finalize() {
        println("SharedStorage instance is being garbage collected.")
    }

    // Identifier is email
    // Function to save the refresh token
    fun saveRefreshToken(token: String) {
        val userId = this.getCurrentUserId();

        val editor = sharedPreferences.edit()
        editor.putString(REFRESH_TOKEN_KEY + "_" +  userId, token)
        editor.apply()
    }

    // Function to get the refresh token
    fun getRefreshToken(): String {
        val userId = this.getCurrentUserId();
        return sharedPreferences.getString(REFRESH_TOKEN_KEY +"_" + userId, null).toString()
    }

    // Function to save the authentication token
    fun saveAuthenticationToken(token: String) {
        val userId = this.getCurrentUserId();

        val editor = sharedPreferences.edit()
        editor.putString(AUTHENTICATION_TOKEN_KEY + "_" + userId, token)
        editor.apply()
    }

    // Function to get the authentication token
    fun getAuthenticationToken(): String {
        val userId = this.getCurrentUserId();
        return sharedPreferences.getString(AUTHENTICATION_TOKEN_KEY + "_" + userId, null).toString()
    }

    // Function to save the authentication token
    fun saveCurrentUsingUserId(userId: String) {
        val editor = sharedPreferences.edit()
        editor.putString(CURRENT_USER_ID, userId)
        editor.apply()
    }

    // Function to get the authentication token
    fun getCurrentUserId(): String {
        return sharedPreferences.getString(CURRENT_USER_ID, null).toString()
    }

    // Function to save the authentication token
    fun saveCurrentUsingCompany(company: String) {
        val editor = sharedPreferences.edit()
        editor.putString(CURRENT_COMPANY, company)
        editor.apply()
    }

    // Function to get the authentication token
    fun getCurrentCompany(): String {
        return sharedPreferences.getString(CURRENT_COMPANY, null).toString()
    }

    fun saveWireguardKeyPair(company: String, encodedPk: String, encodedSk: String, daemonName: String)  {
        val editor = sharedPreferences.edit()
        editor.putString(WIREGUARD_PK + "_" + company, encodedPk)
        editor.putString(WIREGUARD_SK + "_" + company, encodedSk)
        editor.putString(DAEMON_NAME + "_" + company, daemonName)

        editor.apply()
    }

    // Function to get the authentication token
    fun getWireguardKeyPair(company: String): WireguardKeyPair? {
        val pk = sharedPreferences.getString(WIREGUARD_PK + "_" + company, null).toString()
        val sk = sharedPreferences.getString(WIREGUARD_SK + "_" + company, null).toString()
        val daemonName = sharedPreferences.getString(DAEMON_NAME + "_" + company, null).toString()


        if((pk != "" && pk != "null") && (sk != "" && sk != "null")) {
            return WireguardKeyPair(pk, sk, daemonName)
        }

        return null;
    }

    // Function to get the authentication token
    fun clearAll() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply();
    }
}
