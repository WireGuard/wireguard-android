package com.jimberisolation.android.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.jimberisolation.android.authentication.User
import org.json.JSONArray
import org.json.JSONObject

data class DaemonKeyPair(
    val daemonName: String,
    val daemonId: Int,
    val userId: Int,
    val companyName: String,
    val baseEncodedPkEd25519: String,
    val baseEncodedSkEd25519: String,
)

class SharedStorage private constructor() {

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "com.jimberisolation.android.PREFERENCE_FILE_KEY"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
        private const val AUTHENTICATION_TOKEN_KEY = "authentication_token"

        private const val CURRENT_USER = "current_user"
        private const val CURRENT_USER_ID = "current_user_id"
        private const val CURRENT_USER_EMAIL = "current_user_email"

        private const val WIREGUARD_KEYPAIR = "wireguard_keypair"

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

    fun getAuthenticationToken(): String {
        return sharedPreferences.getString(AUTHENTICATION_TOKEN_KEY, null).toString()
    }

    fun clearAuthenticationToken() {
        val editor = sharedPreferences.edit()
        editor.putString(AUTHENTICATION_TOKEN_KEY, "")
        editor.apply()
    }

    fun saveCurrentUser(user: User) {
        val editor = sharedPreferences.edit()

        val usr = JSONObject().apply {
            put(CURRENT_USER_ID, user.id)
            put(CURRENT_USER_EMAIL, user.email)
        }

        editor.putString(CURRENT_USER, usr.toString())
        editor.apply()
    }

    fun getCurrentUser(): User? {
        try {
            val userString = sharedPreferences.getString(CURRENT_USER, null).toString();

            val userObj = JSONObject(userString)

            val usr = User(userObj[CURRENT_USER_ID] as Int, userObj[CURRENT_USER_EMAIL].toString())
            return usr;
        }
        catch(e: Exception) {
            Log.e("ERROR IN GET CURRENT USER", e.message.toString())
            return null;
        }
    }

    private fun clearCurrentUser() {
        val editor = sharedPreferences.edit()
        editor.putString(CURRENT_USER, null)
        editor.apply()
    }

    fun saveDaemonKeyPair(kp: DaemonKeyPair) {
        val editor = sharedPreferences.edit()

        val jsonString = sharedPreferences.getString(WIREGUARD_KEYPAIR, "[]")
        val jsonArray = JSONArray(jsonString!!)

        val newKeyPair = JSONObject().apply {
            put(WIREGUARD_DAEMON_NAME, kp.daemonName)
            put(WIREGUARD_DAEMON_ID, kp.daemonId)
            put(WIREGUARD_USER_ID, kp.userId)
            put(WIREGUARD_COMPANY, kp.companyName)
            put(WIREGUARD_PK_ED25519, kp.baseEncodedPkEd25519)
            put(WIREGUARD_SK_ED25519, kp.baseEncodedSkEd25519)
        }

        for (i in 0 until jsonArray.length()) {
            val existingKeyPair = jsonArray.getJSONObject(i)

            if (existingKeyPair[WIREGUARD_DAEMON_ID] == kp.daemonId) {
                jsonArray.remove(i)
            }

            if (existingKeyPair[WIREGUARD_USER_ID] == kp.userId) {
                jsonArray.remove(i)
            }

            break;
        }

        jsonArray.put(newKeyPair)
        editor.putString(WIREGUARD_KEYPAIR, jsonArray.toString())
        editor.apply()
    }

    fun getDaemonKeyPairByUserId(userId: Int) : DaemonKeyPair? {
        val jsonString = sharedPreferences.getString(WIREGUARD_KEYPAIR, null) ?: return null
        val jsonArray = JSONArray(jsonString)

        for (i in 0 until jsonArray.length()) {
            val keyPairObject = jsonArray.getJSONObject(i)
            if(keyPairObject[WIREGUARD_USER_ID] == userId) {
                val kp = DaemonKeyPair(
                    keyPairObject.getString(WIREGUARD_DAEMON_NAME),
                    keyPairObject.getInt(WIREGUARD_DAEMON_ID),
                    keyPairObject.getInt(WIREGUARD_USER_ID),
                    keyPairObject.getString(WIREGUARD_COMPANY),
                    keyPairObject.getString(WIREGUARD_PK_ED25519),
                    keyPairObject.getString(WIREGUARD_SK_ED25519))

                return kp;
            }
        }

        return null;
    }

    fun getDaemonKeyPairByDaemonId(daemonId: Int) : DaemonKeyPair? {
        val jsonString = sharedPreferences.getString(WIREGUARD_KEYPAIR, null) ?: return null
        val jsonArray = JSONArray(jsonString)

        for (i in 0 until jsonArray.length()) {
            val keyPairObject = jsonArray.getJSONObject(i)
            if(keyPairObject[WIREGUARD_DAEMON_ID] == daemonId) {
                val kp = DaemonKeyPair(
                    keyPairObject.getString(WIREGUARD_DAEMON_NAME),
                    keyPairObject.getInt(WIREGUARD_DAEMON_ID),
                    keyPairObject.getInt(WIREGUARD_USER_ID),
                    keyPairObject.getString(WIREGUARD_COMPANY),
                    keyPairObject.getString(WIREGUARD_PK_ED25519),
                    keyPairObject.getString(WIREGUARD_SK_ED25519))

                return kp;
            }
        }

        return null;
    }


    fun clearUserLoginData() {
        val editor = sharedPreferences.edit()

        clearCurrentUser();
        clearRefreshToken()
        clearAuthenticationToken()

        editor.apply();
    }

    // Function to get the authentication token
    fun clearDaemonKeys(daemonId: Int) {
        val jsonString = sharedPreferences.getString(WIREGUARD_KEYPAIR, null) ?: return
        val jsonArray = JSONArray(jsonString)

        val editor = sharedPreferences.edit()

        var i = 0
        while (i < jsonArray.length()) {
            val keyPairObject = jsonArray.getJSONObject(i)
            if (keyPairObject[WIREGUARD_DAEMON_ID] == daemonId) {
                jsonArray.remove(i)
                break;
            }
            i++;
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
