package com.jimberisolation.android.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject


data class DaemonKeyPair(
    val daemonName: String,
    val daemonId: Int,
    val company: String,
    val userId: Int,
    val pk: String,
    val sk: String,
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
        private const val WIREGUARD_PK = "pk"
        private const val WIREGUARD_SK = "sk"
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

    fun saveWireguardKeyPair(keyPair: DaemonKeyPair) {
        val editor = sharedPreferences.edit()

        val jsonString = sharedPreferences.getString(WIREGUARD_KEYPAIR, "[]")
        val jsonArray = JSONArray(jsonString!!)

        val newKeyPair = JSONObject().apply {
            put(WIREGUARD_PK, keyPair.pk)
            put(WIREGUARD_SK, keyPair.sk)
            put(WIREGUARD_DAEMON_NAME, keyPair.daemonName)
            put(WIREGUARD_DAEMON_ID, keyPair.daemonId)
            put(WIREGUARD_COMPANY, keyPair.company)
            put(WIREGUARD_USER_ID, keyPair.userId)
        }

        var found = false

        for (i in 0 until jsonArray.length()) {
            val existingKeyPair = jsonArray.getJSONObject(i)
            if (existingKeyPair[WIREGUARD_DAEMON_ID] == keyPair.daemonId) {
                jsonArray.put(i, newKeyPair)
                found = true
                break
            }
        }

        if (!found) {
            jsonArray.put(newKeyPair)
        }

        editor.putString(WIREGUARD_KEYPAIR, jsonArray.toString())
        editor.apply()
    }


    fun getWireguardKeyPairOfDaemonId(company: String, daemonId: Int): DaemonKeyPair? {
        val jsonString = sharedPreferences.getString(WIREGUARD_KEYPAIR, "[]") ?: return null
        val jsonArray = JSONArray(jsonString)

        for (i in 0 until jsonArray.length()) {
            val keyPairObject = jsonArray.getJSONObject(i)
            if(keyPairObject[WIREGUARD_DAEMON_ID] == daemonId){
                val parsedKeyPair = DaemonKeyPair(
                    keyPairObject.getString(WIREGUARD_DAEMON_NAME),
                    keyPairObject.getInt(WIREGUARD_DAEMON_ID),
                    keyPairObject.getString(WIREGUARD_COMPANY),
                    keyPairObject.getInt(WIREGUARD_USER_ID),
                    keyPairObject.getString(WIREGUARD_PK),
                    keyPairObject.getString(WIREGUARD_SK))

                return parsedKeyPair
            };
        }

        return null;
    }


    fun getWireguardKeyPairsOfUserId(userId: Number): List<DaemonKeyPair>? {
        val jsonString = sharedPreferences.getString(WIREGUARD_KEYPAIR, null) ?: return null
        val jsonArray = JSONArray(jsonString)

        val keyPairs = mutableListOf<DaemonKeyPair>()
        for (i in 0 until jsonArray.length()) {
            val keyPairObject = jsonArray.getJSONObject(i)
            if(keyPairObject[WIREGUARD_USER_ID] != userId) continue

            val parsedKeyPair = DaemonKeyPair(
                keyPairObject.getString(WIREGUARD_DAEMON_NAME),
                keyPairObject.getInt(WIREGUARD_DAEMON_ID),
                keyPairObject.getString(WIREGUARD_COMPANY),
                keyPairObject.getInt(WIREGUARD_USER_ID),
                keyPairObject.getString(WIREGUARD_PK),
                keyPairObject.getString(WIREGUARD_SK))

            keyPairs.add(parsedKeyPair)
        }

        return keyPairs
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
