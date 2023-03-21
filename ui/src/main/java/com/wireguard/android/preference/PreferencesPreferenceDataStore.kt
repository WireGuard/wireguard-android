/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.preference.PreferenceDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PreferencesPreferenceDataStore(private val coroutineScope: CoroutineScope, private val dataStore: DataStore<Preferences>) : PreferenceDataStore() {
    override fun putString(key: String?, value: String?) {
        if (key == null) return
        val pk = stringPreferencesKey(key)
        coroutineScope.launch {
            dataStore.edit {
                if (value == null) it.remove(pk)
                else it[pk] = value
            }
        }
    }

    override fun putStringSet(key: String?, values: Set<String?>?) {
        if (key == null) return
        val pk = stringSetPreferencesKey(key)
        val filteredValues = values?.filterNotNull()?.toSet()
        coroutineScope.launch {
            dataStore.edit {
                if (filteredValues == null || filteredValues.isEmpty()) it.remove(pk)
                else it[pk] = filteredValues
            }
        }
    }

    override fun putInt(key: String?, value: Int) {
        if (key == null) return
        val pk = intPreferencesKey(key)
        coroutineScope.launch {
            dataStore.edit {
                it[pk] = value
            }
        }
    }

    override fun putLong(key: String?, value: Long) {
        if (key == null) return
        val pk = longPreferencesKey(key)
        coroutineScope.launch {
            dataStore.edit {
                it[pk] = value
            }
        }
    }

    override fun putFloat(key: String?, value: Float) {
        if (key == null) return
        val pk = floatPreferencesKey(key)
        coroutineScope.launch {
            dataStore.edit {
                it[pk] = value
            }
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (key == null) return
        val pk = booleanPreferencesKey(key)
        coroutineScope.launch {
            dataStore.edit {
                it[pk] = value
            }
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        if (key == null) return defValue
        val pk = stringPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValue }.first()
        }
    }

    override fun getStringSet(key: String?, defValues: Set<String?>?): Set<String?>? {
        if (key == null) return defValues
        val pk = stringSetPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValues }.first()
        }
    }

    override fun getInt(key: String?, defValue: Int): Int {
        if (key == null) return defValue
        val pk = intPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValue }.first()
        }
    }

    override fun getLong(key: String?, defValue: Long): Long {
        if (key == null) return defValue
        val pk = longPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValue }.first()
        }
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        if (key == null) return defValue
        val pk = floatPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValue }.first()
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (key == null) return defValue
        val pk = booleanPreferencesKey(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValue }.first()
        }
    }
}
