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

data class TunnelInfo(val name: String, val daemonId: Int, val userId: Int)


data class CreateTunnelData(val name: String, val daemonId: Int, val userId: Int)

/**
 * Configuration store that uses a `wg-quick`-style file for each configured tunnel.
 */
class FileConfigStore(private val context: Context) : ConfigStore {
    @Throws(IOException::class)
    override fun create(createTunnelData: CreateTunnelData, config: Config): Config {
        Log.d(TAG, "Creating configuration for tunnel ${createTunnelData.name}")
        val file = fileFor("userId-${createTunnelData.userId}-daemon-${createTunnelData.daemonId}-name-${createTunnelData.name}")

        if (!file.createNewFile())
            throw IOException(context.getString(R.string.config_file_exists_error, file.name))
        FileOutputStream(file, false).use { it.write(config.toWgQuickString().toByteArray(StandardCharsets.UTF_8)) }
        return config
    }

    @Throws(IOException::class)
    override fun delete(tunnel: ObservableTunnel) {
        Log.d(TAG, "Deleting configuration for tunnel ${tunnel.name}")
        val file = fileFor("userId-${tunnel.getUserId()}-daemon-${tunnel.getDaemonId()}-name-${tunnel.name}")
        if (!file.delete())
            throw IOException(context.getString(R.string.config_delete_error, file.name))
    }

    override fun enumerate(userId: Int): Set<TunnelInfo> {
        return context.fileList()
            .filter { it.endsWith(".conf") }
            .mapNotNull { fileName ->
                val regex = Regex("""userId-(\d+)-daemon-(\d+)-name-(.+)\.conf""")
                val matchResult = regex.find(fileName)
                matchResult?.let {
                    val extractedUserId = it.groups[1]?.value?.toInt()
                    val daemonId = it.groups[2]?.value?.toInt()
                    val name = it.groups[3]?.value
                    if (extractedUserId != null && extractedUserId == userId && daemonId != null && name != null) {
                        TunnelInfo(name, daemonId, extractedUserId)
                    } else {
                        null
                    }
                }
            }.toSet()
    }

    private fun fileFor(name: String): File {
        return File(context.filesDir, "$name.conf")
    }

    @Throws(BadConfigException::class, IOException::class)
    override fun load(tunnel: ObservableTunnel): Config {
        FileInputStream(fileFor("userId-${tunnel.getUserId()}-daemon-${tunnel.getDaemonId()}-name-${tunnel.name}")).use { stream -> return Config.parse(stream) }
    }

    @Throws(IOException::class)
    override fun rename(tunnel: ObservableTunnel, replacement: String) {
        Log.d(TAG, "Renaming configuration for tunnel ${tunnel.name} to $replacement")
        val file = fileFor("userId-${tunnel.getUserId()}-daemon-${tunnel.getDaemonId()}-name-${tunnel.name}")
        val replacementFile = fileFor("userId-${tunnel.getUserId()}-daemon-${tunnel.getDaemonId()}-name-${replacement}")
        if (!replacementFile.createNewFile()) throw IOException(context.getString(R.string.config_exists_error, replacement))
        if (!file.renameTo(replacementFile)) {
            if (!replacementFile.delete()) Log.w(TAG, "Couldn't delete marker file for new name $replacement")
            throw IOException(context.getString(R.string.config_rename_error, file.name))
        }
    }

    @Throws(IOException::class)
    override fun save(tunnel: ObservableTunnel, config: Config): Config {
        Log.d(TAG, "Saving configuration for tunnel ${tunnel.name}")
        val file = fileFor("userId-${tunnel.getUserId()}-daemon-${tunnel.getDaemonId()}-name-${tunnel.name}")
        if (!file.isFile)
            throw FileNotFoundException(context.getString(R.string.config_not_found_error, file.name))
        FileOutputStream(file, false).use { stream -> stream.write(config.toWgQuickString().toByteArray(StandardCharsets.UTF_8)) }
        return config
    }

    companion object {
        private const val TAG = "WireGuard/FileConfigStore"
    }
}
