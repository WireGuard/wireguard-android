/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.jimberisolation.android.configStore

import android.content.Context
import android.util.Log
import com.jimberisolation.android.R
import com.jimberisolation.android.model.ObservableTunnel
import com.jimberisolation.config.BadConfigException
import com.jimberisolation.config.Config
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

data class TunnelInfo(val name: String, val daemonId: Number)

/**
 * Configuration store that uses a `wg-quick`-style file for each configured tunnel.
 */
class FileConfigStore(private val context: Context) : ConfigStore {
    @Throws(IOException::class)
    override fun create(name: String, daemonId: Number, config: Config): Config {
        Log.d(TAG, "Creating configuration for tunnel $name")
        val file = fileFor("daemon-$daemonId-name-$name")

        if (!file.createNewFile())
            throw IOException(context.getString(R.string.config_file_exists_error, file.name))
        FileOutputStream(file, false).use { it.write(config.toWgQuickString().toByteArray(StandardCharsets.UTF_8)) }
        return config
    }

    @Throws(IOException::class)
    override fun delete(tunnel: ObservableTunnel) {
        Log.d(TAG, "Deleting configuration for tunnel ${tunnel.name}")
        val file = fileFor("daemon-${tunnel.getDaemonId()}-name-${tunnel.name}")
        if (!file.delete())
            throw IOException(context.getString(R.string.config_delete_error, file.name))
    }

    override fun enumerate(): Set<TunnelInfo> {
        return context.fileList()
            .filter { it.endsWith(".conf") } // Filter for configuration files
            .mapNotNull { fileName ->
                // Use a regex to extract daemonId and name
                val regex = Regex("""daemon-(\d+)-name-(.+)\.conf""")
                val matchResult = regex.find(fileName)
                matchResult?.let {
                    val daemonId = it.groups[1]?.value?.toInt() // Extract daemonId and convert to Int
                    val name = it.groups[2]?.value // Extract the name
                    if (daemonId != null && name != null) {
                        TunnelInfo(name, daemonId) // Return a TunnelInfo object
                    } else {
                        null // In case of extraction failure, return null
                    }
                }
            }.toSet() // Convert to a Set to avoid duplicates
    }

    private fun fileFor(name: String): File {
        return File(context.filesDir, "$name.conf")
    }

    @Throws(BadConfigException::class, IOException::class)
    override fun load(tunnel: ObservableTunnel): Config {
        FileInputStream(fileFor("daemon-${tunnel.getDaemonId()}-name-${tunnel.name}")).use { stream -> return Config.parse(stream) }
    }

    @Throws(IOException::class)
    override fun rename(tunnel: ObservableTunnel, replacement: String) {
        Log.d(TAG, "Renaming configuration for tunnel ${tunnel.name} to $replacement")
        val file = fileFor("daemon-${tunnel.getDaemonId()}-name-${tunnel.name}")
        val replacementFile = fileFor("daemon-${tunnel.getDaemonId()}-name-${replacement}")
        if (!replacementFile.createNewFile()) throw IOException(context.getString(R.string.config_exists_error, replacement))
        if (!file.renameTo(replacementFile)) {
            if (!replacementFile.delete()) Log.w(TAG, "Couldn't delete marker file for new name $replacement")
            throw IOException(context.getString(R.string.config_rename_error, file.name))
        }
    }

    @Throws(IOException::class)
    override fun save(tunnel: ObservableTunnel, config: Config): Config {
        Log.d(TAG, "Saving configuration for tunnel ${tunnel.name}")
        val file = fileFor("daemon-${tunnel.getDaemonId()}-name-${tunnel.name}")
        if (!file.isFile)
            throw FileNotFoundException(context.getString(R.string.config_not_found_error, file.name))
        FileOutputStream(file, false).use { stream -> stream.write(config.toWgQuickString().toByteArray(StandardCharsets.UTF_8)) }
        return config
    }

    companion object {
        private const val TAG = "WireGuard/FileConfigStore"
    }
}
