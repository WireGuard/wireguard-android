/*
 * Copyright © 2020 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.preference

import androidx.datastore.DataStore
import androidx.datastore.preferences.Preferences
import androidx.datastore.preferences.edit
import androidx.datastore.preferences.preferencesKey
import androidx.datastore.preferences.preferencesSetKey
import androidx.datastore.preferences.remove
import androidx.preference.PreferenceDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PreferencesPreferenceDataStore(private val coroutineScope: CoroutineScope, private val dataStore: DataStore<Preferences>) : PreferenceDataStore() {
    override fun putString(key: String?, value: String?) {
        if (key == null) return
        val pk = preferencesKey<String>(key)
        coroutineScope.launch {
            dataStore.edit {
                if (value == null) it.remove(pk)
                else it[pk] = value
            }
        }
    }

    override fun putStringSet(key: String?, values: Set<String?>?) {
        if (key == null) return
        val pk = preferencesSetKey<String>(key)
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
        val pk = preferencesKey<Int>(key)
        coroutineScope.launch {
            dataStore.edit {
                it[pk] = value
            }
        }
    }

    override fun putLong(key: String?, value: Long) {
        if (key == null) return
        val pk = preferencesKey<Long>(key)
        coroutineScope.launch {
            dataStore.edit {
                it[pk] = value
            }
        }
    }

    override fun putFloat(key: String?, value: Float) {
        if (key == null) return
        val pk = preferencesKey<Float>(key)
        coroutineScope.launch {
            dataStore.edit {
                it[pk] = value
            }
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        if (key == null) return
        val pk = preferencesKey<Boolean>(key)
        coroutineScope.launch {
            dataStore.edit {
                it[pk] = value
            }
        }
    }

    override fun getString(key: String?, defValue: String?): String? {
        if (key == null) return defValue
        val pk = preferencesKey<String>(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValue }.first()
        }
    }

    override fun getStringSet(key: String?, defValues: Set<String?>?): Set<String?>? {
        if (key == null) return defValues
        val pk = preferencesSetKey<String>(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValues }.first()
        }
    }

    override fun getInt(key: String?, defValue: Int): Int {
        if (key == null) return defValue
        val pk = preferencesKey<Int>(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValue }.first()
        }
    }

    override fun getLong(key: String?, defValue: Long): Long {
        if (key == null) return defValue
        val pk = preferencesKey<Long>(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValue }.first()
        }
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        if (key == null) return defValue
        val pk = preferencesKey<Float>(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValue }.first()
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        if (key == null) return defValue
        val pk = preferencesKey<Boolean>(key)
        return runBlocking {
            dataStore.data.map { it[pk] ?: defValue }.first()
        }
    }
}
