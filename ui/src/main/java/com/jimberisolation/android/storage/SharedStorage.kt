package com.jimberisolation.android.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject


data class SharedStorageKeyPair(
    val daemonName: String,
    val daemonId: Int,
    val company: String,
    val userId: Int,
    val pkCurveX25519: String,
    val skCurveX25519: String,
    val pkEd25519: String,
    val skEd25519: String,
)

class SharedStorage private constructor() {

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "com.jimberisolation.android.PREFERENCE_FILE_KEY"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val AUTHENTICATION_TOKEN_KEY = "authentication_token"
        private const val CURRENT_USER_ID = "current_user_id"
        private const val CURRENT_COMPANY = "current_company"

        private const val WIREGUARD_KEYPAIR = "wireguard_keypair"
        private const val WIREGUARD_PK_CURVE_X25519 = "pk_curve_x25519"
        private const val WIREGUARD_SK_CURVE_X25519  = "sk_curve_x25519"

        private const val WIREGUARD_PK_ED25519 = "pk_ed25519"
        private const val WIREGUARD_SK_ED25519  = "sk_ed25519"

        private const val WIREGUARD_DAEMON_NAME = "name"
        private const val WIREGUARD_DAEMON_ID = "id"
        private const val WIREGUARD_USER_ID = "userId"
        private const val WIREGUARD_COMPANY = "company"

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

    fun saveRefreshToken(token: String) {
        val editor = sharedPreferences.edit()
        editor.putString(REFRESH_TOKEN_KEY, token)
        editor.apply()
    }

    fun getRefreshToken(): String {
        return sharedPreferences.getString(REFRESH_TOKEN_KEY, null).toString()
    }

    fun clearRefreshToken() {
        val editor = sharedPreferences.edit()
        editor.putString(REFRESH_TOKEN_KEY, "")
        editor.apply()
    }

    fun saveAuthenticationToken(token: String) {
        val editor = sharedPreferences.edit()
        editor.putString(AUTHENTICATION_TOKEN_KEY, token)
        editor.apply()
    }

    // Function to get the authentication token
    fun getAuthenticationToken(): String {
        return sharedPreferences.getString(AUTHENTICATION_TOKEN_KEY, null).toString()
    }

    fun clearAuthenticationToken() {
        val editor = sharedPreferences.edit()
        editor.putString(AUTHENTICATION_TOKEN_KEY, "")
        editor.apply()
    }

    // Function to save the authentication token
    fun saveCurrentUsingUserId(userId: Int) {
        val editor = sharedPreferences.edit()
        editor.putInt(CURRENT_USER_ID, userId)
        editor.apply()
    }

    // Function to get the authentication token
    fun getCurrentUserId(): Int {
        return sharedPreferences.getInt(CURRENT_USER_ID, 0)
    }

    fun clearCurrentUserId() {
        val editor = sharedPreferences.edit()
        editor.putInt(CURRENT_USER_ID, 0)
        editor.apply()
    }


    // Function to save the authentication token
    fun saveCurrentUsingCompany(company: String) {
        val editor = sharedPreferences.edit()
        editor.putString(CURRENT_COMPANY, company)
        editor.apply()
    }

    // Function to save the authentication token
    fun clearCurrentUsingCompany() {
        val editor = sharedPreferences.edit()
        editor.putString(CURRENT_COMPANY, null)
        editor.apply()
    }


    // Function to get the authentication token
    fun getCurrentCompany(): String {
        return sharedPreferences.getString(CURRENT_COMPANY, null).toString()
    }

    fun saveSharedStorageKeyPair(keyPair: SharedStorageKeyPair) {
        val editor = sharedPreferences.edit()

        val jsonString = sharedPreferences.getString(WIREGUARD_KEYPAIR, "[]")
        val jsonArray = JSONArray(jsonString!!)

        val newKeyPair = JSONObject().apply {
            put(WIREGUARD_PK_CURVE_X25519, keyPair.pkCurveX25519)
            put(WIREGUARD_SK_CURVE_X25519, keyPair.skCurveX25519)
            put(WIREGUARD_DAEMON_NAME, keyPair.daemonName)
            put(WIREGUARD_DAEMON_ID, keyPair.daemonId)
            put(WIREGUARD_COMPANY, keyPair.company)
            put(WIREGUARD_USER_ID, keyPair.userId)
            put(WIREGUARD_PK_ED25519, keyPair.pkEd25519)
            put(WIREGUARD_SK_ED25519, keyPair.skEd25519)
        }

        for (i in 0 until jsonArray.length()) {
            val existingKeyPair = jsonArray.getJSONObject(i)

            if (existingKeyPair[WIREGUARD_USER_ID] == keyPair.userId) {
                // Remove the old one
                jsonArray.remove(i)

                break
            }
        }
        jsonArray.put(newKeyPair)
        editor.putString(WIREGUARD_KEYPAIR, jsonArray.toString())
        editor.apply()
    }

    fun getWireguardKeyPairOfUserId(userId: Number): SharedStorageKeyPair? {
        val jsonString = sharedPreferences.getString(WIREGUARD_KEYPAIR, null) ?: return null
        val jsonArray = JSONArray(jsonString)

        for (i in 0 until jsonArray.length()) {
            val keyPairObject = jsonArray.getJSONObject(i)
            if(keyPairObject[WIREGUARD_USER_ID] == userId) {
                val kp = SharedStorageKeyPair(
                    keyPairObject.getString(WIREGUARD_DAEMON_NAME),
                    keyPairObject.getInt(WIREGUARD_DAEMON_ID),
                    keyPairObject.getString(WIREGUARD_COMPANY),
                    keyPairObject.getInt(WIREGUARD_USER_ID),
                    keyPairObject.getString(WIREGUARD_PK_CURVE_X25519),
                    keyPairObject.getString(WIREGUARD_SK_CURVE_X25519),
                    keyPairObject.getString(WIREGUARD_PK_ED25519),
                    keyPairObject.getString(WIREGUARD_SK_ED25519))

                return kp;
            }
        }

        return null;
    }


    // Function to get the authentication token
    fun clearUserLoginData() {
        val editor = sharedPreferences.edit()

        clearCurrentUserId();
        clearCurrentUsingCompany();
        clearRefreshToken()
        clearAuthenticationToken()

        editor.apply();
    }

    // Function to get the authentication token
    fun clearDaemonKeys(daemonId: Int) {
        val jsonString = sharedPreferences.getString(WIREGUARD_KEYPAIR, null) ?: return
        val jsonArray = JSONArray(jsonString)

        val editor = sharedPreferences.edit()

        for (i in 0 until jsonArray.length()) {
            val keyPairObject = jsonArray.getJSONObject(i)
            if(keyPairObject[WIREGUARD_DAEMON_ID] == daemonId){
                jsonArray.remove(i)
            };
        }

        editor.putString(WIREGUARD_KEYPAIR, jsonArray.toString()).apply()
        editor.apply()
    }

    // Function to get the authentication token
    fun clearAll() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply();
    }

    // Function to get the authentication token
    fun getAll(): MutableMap<String, *>? {
        return sharedPreferences.all;
    }
}
