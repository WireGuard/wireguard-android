/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.wireguard.android.util

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * Persists a mapping of interface name to user-facing display name.
 * Display names may contain any Unicode characters including emoji.
 * The underlying interface name remains restricted to [a-zA-Z0-9_=+.-]{1,15}.
 */
object DisplayNameStore {
    private const val TAG = "WireGuard/DisplayNameStore"
    private const val FILENAME = "display_names.json"

    private var cache: MutableMap<String, String>? = null

    private fun file(context: Context): File = File(context.filesDir, FILENAME)

    private fun ensureLoaded(context: Context): MutableMap<String, String> {
        cache?.let { return it }
        val map = mutableMapOf<String, String>()
        val f = file(context)
        if (f.exists()) {
            try {
                val json = JSONObject(f.readText(StandardCharsets.UTF_8))
                for (key in json.keys()) {
                    map[key] = json.getString(key)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load display names", e)
            }
        }
        cache = map
        return map
    }

    private fun persist(context: Context, map: Map<String, String>) {
        val json = JSONObject()
        for ((k, v) in map) {
            json.put(k, v)
        }
        try {
            file(context).writeText(json.toString(), StandardCharsets.UTF_8)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to persist display names", e)
        }
    }

    fun getDisplayName(context: Context, interfaceName: String): String? {
        return ensureLoaded(context)[interfaceName]
    }

    fun setDisplayName(context: Context, interfaceName: String, displayName: String?) {
        val map = ensureLoaded(context)
        if (displayName == null || displayName == interfaceName) {
            map.remove(interfaceName)
        } else {
            map[interfaceName] = displayName
        }
        persist(context, map)
    }

    fun rename(context: Context, oldInterfaceName: String, newInterfaceName: String) {
        val map = ensureLoaded(context)
        val displayName = map.remove(oldInterfaceName)
        if (displayName != null) {
            map[newInterfaceName] = displayName
        }
        persist(context, map)
    }

    fun delete(context: Context, interfaceName: String) {
        val map = ensureLoaded(context)
        if (map.remove(interfaceName) != null) {
            persist(context, map)
        }
    }

    /**
     * Generate a valid Linux interface name from a display name that may contain
     * Unicode/emoji characters. Returns null if the display name is already a valid
     * interface name.
     */
    fun generateInterfaceName(displayName: String): String? {
        // If already valid, no generation needed
        if (displayName.matches(Regex("[a-zA-Z0-9_=+.-]{1,15}"))) {
            return null
        }

        // Transliterate: keep ASCII alphanumeric and allowed chars, skip the rest
        val sb = StringBuilder()
        for (c in displayName) {
            if (Character.isLetterOrDigit(c) && c.code < 128) {
                sb.append(c)
            } else if ("_=+.-".indexOf(c) >= 0) {
                sb.append(c)
            }
        }

        // If we got something usable, use it (truncated)
        val base = if (sb.isNotEmpty()) {
            sb.toString().take(11)
        } else {
            "tun"
        }

        // Append a short hash to avoid collisions
        val hash = displayName.hashCode().toUInt().toString(36).take(4)
        val name = "${base}_$hash"
        return if (name.length > 15) name.take(15) else name
    }
}
