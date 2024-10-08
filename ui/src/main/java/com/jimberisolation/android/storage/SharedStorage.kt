package com.jimberisolation.android.storage

import android.content.Context
import android.content.SharedPreferences

class SharedStorage private constructor() {

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "com.jimberisolation.android.PREFERENCE_FILE_KEY"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val AUTHENTICATION_TOKEN_KEY = "authentication_token"
        private const val CURRENT_USING_EMAIL_KEY = "current_using_email"

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
        val email = this.getCurrentUsingEmail();

        val editor = sharedPreferences.edit()
        editor.putString(REFRESH_TOKEN_KEY + "_" +  email, token)
        editor.apply()
    }

    // Function to get the refresh token
    fun getRefreshToken(): String {
        val email = this.getCurrentUsingEmail();
        return sharedPreferences.getString(REFRESH_TOKEN_KEY +"_" + email, null).toString()
    }

    // Function to save the authentication token
    fun saveAuthenticationToken(token: String) {
        val email = this.getCurrentUsingEmail();

        val editor = sharedPreferences.edit()
        editor.putString(AUTHENTICATION_TOKEN_KEY + "_" + email, token)
        editor.apply()
    }

    // Function to get the authentication token
    fun getAuthenticationToken(): String {
        val email = this.getCurrentUsingEmail();
        return sharedPreferences.getString(AUTHENTICATION_TOKEN_KEY + "_" + email, null).toString()
    }

    // Function to save the authentication token
    fun saveCurrentUsingEmail(email: String) {
        val editor = sharedPreferences.edit()
        editor.putString(CURRENT_USING_EMAIL_KEY + "_" + email, email)
        editor.apply()
    }

    // Function to get the authentication token
    fun getCurrentUsingEmail(): String {
        return sharedPreferences.getString(CURRENT_USING_EMAIL_KEY, null).toString()
    }
}
