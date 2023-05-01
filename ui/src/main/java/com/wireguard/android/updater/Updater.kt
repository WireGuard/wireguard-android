/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.wireguard.android.Application
import com.wireguard.android.BuildConfig
import com.wireguard.android.util.UserKnobs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.InvalidParameterException
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object Updater {
    private const val TAG = "WireGuard/Updater"
    private const val LATEST_VERSION_URL = "https://download.wireguard.com/android-client/latest.sig"
    private const val APK_PATH_URL = "https://download.wireguard.com/android-client/%s"
    private const val APK_NAME_PREFIX = BuildConfig.APPLICATION_ID + "-"
    private const val APK_NAME_SUFFIX = ".apk"
    private const val RELEASE_PUBLIC_KEY_BASE64 = "RWTAzwGRYr3EC9px0Ia3fbttz8WcVN6wrOwWp2delz4el6SI8XmkKSMp"
    private val CURRENT_VERSION = BuildConfig.VERSION_NAME.removeSuffix("-debug")

    sealed class Progress {
        object Complete : Progress()
        class Available(val version: String) : Progress() {
            fun update() {
                Application.getCoroutineScope().launch {
                    UserKnobs.setUpdaterNewerVersionConsented(version)
                }
            }
        }

        object Rechecking : Progress()
        class Downloading(val bytesDownloaded: ULong, val bytesTotal: ULong) : Progress()
        object Installing : Progress()
        class NeedsUserIntervention(val intent: Intent, private val id: Int) : Progress() {

            private suspend fun installerActive(): Boolean {
                if (mutableState.firstOrNull() != this@NeedsUserIntervention)
                    return true
                try {
                    if (Application.get().packageManager.packageInstaller.getSessionInfo(id)?.isActive == true)
                        return true
                } catch (_: SecurityException) {
                    return true
                }
                return false
            }

            fun markAsDone() {
                Application.getCoroutineScope().launch {
                    if (installerActive())
                        return@launch
                    delay(7.seconds)
                    if (installerActive())
                        return@launch
                    emitProgress(Failure(Exception("Ignored by user")))
                }
            }
        }

        class Failure(val error: Throwable) : Progress() {
            fun retry() {
                Application.getCoroutineScope().launch {
                    downloadAndUpdateWrapErrors()
                }
            }
        }
    }

    private val mutableState = MutableStateFlow<Progress>(Progress.Complete)
    val state = mutableState.asStateFlow()

    private suspend fun emitProgress(progress: Progress, force: Boolean = false) {
        if (force || mutableState.firstOrNull()?.javaClass != progress.javaClass)
            mutableState.emit(progress)
    }

    private fun versionIsNewer(lhs: String, rhs: String): Boolean {
        val lhsParts = lhs.split(".")
        val rhsParts = rhs.split(".")
        if (lhsParts.isEmpty() || rhsParts.isEmpty())
            throw InvalidParameterException("Version is empty")

        for (i in 0 until max(lhsParts.size, rhsParts.size)) {
            val lhsPart = if (i < lhsParts.size) lhsParts[i].toULong() else 0UL
            val rhsPart = if (i < rhsParts.size) rhsParts[i].toULong() else 0UL
            if (lhsPart == rhsPart)
                continue
            return lhsPart > rhsPart
        }
        return false
    }

    private fun versionOfFile(name: String): String? {
        if (!name.startsWith(APK_NAME_PREFIX) || !name.endsWith(APK_NAME_SUFFIX))
            return null
        return name.substring(APK_NAME_PREFIX.length, name.length - APK_NAME_SUFFIX.length)
    }

    private fun verifySignedFileList(signifyDigest: String): Map<String, Sha256Digest> {
        val publicKeyBytes = Base64.decode(RELEASE_PUBLIC_KEY_BASE64, Base64.DEFAULT)
        if (publicKeyBytes == null || publicKeyBytes.size != 32 + 10 || publicKeyBytes[0] != 'E'.code.toByte() || publicKeyBytes[1] != 'd'.code.toByte())
            throw InvalidKeyException("Invalid public key")
        val lines = signifyDigest.split("\n", limit = 3)
        if (lines.size != 3)
            throw InvalidParameterException("Invalid signature format: too few lines")
        if (!lines[0].startsWith("untrusted comment: "))
            throw InvalidParameterException("Invalid signature format: missing comment")
        val signatureBytes = Base64.decode(lines[1], Base64.DEFAULT)
        if (signatureBytes == null || signatureBytes.size != 64 + 10)
            throw InvalidParameterException("Invalid signature format: wrong sized or missing signature")
        for (i in 0..9) {
            if (signatureBytes[i] != publicKeyBytes[i])
                throw InvalidParameterException("Invalid signature format: wrong signer")
        }
        if (!Ed25519.verify(
                lines[2].toByteArray(StandardCharsets.UTF_8),
                signatureBytes.sliceArray(10 until 10 + 64),
                publicKeyBytes.sliceArray(10 until 10 + 32)
            )
        )
            throw SecurityException("Invalid signature")
        val hashes: MutableMap<String, Sha256Digest> = HashMap()
        for (line in lines[2].split("\n").dropLastWhile { it.isEmpty() }) {
            val components = line.split("  ", limit = 2)
            if (components.size != 2)
                throw InvalidParameterException("Invalid file list format: too few components")
            hashes[components[1]] = Sha256Digest(components[0])
        }
        return hashes
    }

    private class Sha256Digest(hex: String) {
        val bytes: ByteArray

        init {
            if (hex.length != 64)
                throw InvalidParameterException("SHA256 hashes must be 32 bytes long")
            bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }

    private fun checkForUpdates(): Pair<String, Sha256Digest> {
        val connection = URL(LATEST_VERSION_URL).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", Application.USER_AGENT)
        connection.connect()
        if (connection.responseCode != HttpURLConnection.HTTP_OK)
            throw IOException("File list could not be fetched: ${connection.responseCode}")
        var fileListBytes = ByteArray(1024 * 512 /* 512 KiB */)
        connection.inputStream.use {
            val len = it.read(fileListBytes)
            if (len <= 0)
                throw IOException("File list is empty")
            fileListBytes = fileListBytes.sliceArray(0 until len)
        }
        val fileList = verifySignedFileList(fileListBytes.decodeToString())
        if (fileList.isEmpty())
            throw InvalidParameterException("File list is empty")
        var newestFile: String? = null
        var newestVersion: String? = null
        var newestFileHash: Sha256Digest? = null
        for (file in fileList) {
            val fileVersion = versionOfFile(file.key)
            try {
                if (fileVersion != null && (newestVersion == null || versionIsNewer(fileVersion, newestVersion))) {
                    newestVersion = fileVersion
                    newestFile = file.key
                    newestFileHash = file.value
                }
            } catch (_: Throwable) {
            }
        }
        if (newestFile == null || newestFileHash == null)
            throw InvalidParameterException("File list is empty")
        return Pair(newestFile, newestFileHash)
    }

    private suspend fun downloadAndUpdate() = withContext(Dispatchers.IO) {
        val receiver = InstallReceiver()
        val context = Application.get().applicationContext
        val pendingIntent = withContext(Dispatchers.Main) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(receiver.sessionId),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(receiver.sessionId).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        emitProgress(Progress.Rechecking)
        val update = checkForUpdates()
        val updateVersion = versionOfFile(checkForUpdates().first) ?: throw Exception("No versions returned")
        if (!versionIsNewer(updateVersion, CURRENT_VERSION)) {
            emitProgress(Progress.Complete)
            return@withContext
        }

        emitProgress(Progress.Downloading(0UL, 0UL), true)
        val connection = URL(APK_PATH_URL.format(update.first)).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", Application.USER_AGENT)
        connection.connect()
        if (connection.responseCode != HttpURLConnection.HTTP_OK)
            throw IOException("Update could not be fetched: ${connection.responseCode}")

        var downloadedByteLen: ULong = 0UL
        val totalByteLen =
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) connection.contentLengthLong else connection.contentLength).toLong()
                .toULong()
        val fileBytes = ByteArray(1024 * 32 /* 32 KiB */)
        val digest = MessageDigest.getInstance("SHA-256")
        emitProgress(Progress.Downloading(downloadedByteLen, totalByteLen), true)

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        params.setAppPackageName(context.packageName) /* Enforces updates; disallows new apps. */
        val session = installer.openSession(installer.createSession(params))
        var sessionFailure = true
        try {
            val installDest = session.openWrite(receiver.sessionId, 0, -1)

            installDest.use { dest ->
                connection.inputStream.use { src ->
                    while (true) {
                        val readLen = src.read(fileBytes)
                        if (readLen <= 0)
                            break

                        digest.update(fileBytes, 0, readLen)
                        dest.write(fileBytes, 0, readLen)

                        downloadedByteLen += readLen.toUInt()
                        emitProgress(Progress.Downloading(downloadedByteLen, totalByteLen), true)

                        if (downloadedByteLen >= 1024UL * 1024UL * 100UL /* 100 MiB */)
                            throw IOException("File too large")
                    }
                }
            }

            emitProgress(Progress.Installing)
            if (!digest.digest().contentEquals(update.second.bytes))
                throw SecurityException("Update has invalid hash")
            sessionFailure = false
        } finally {
            if (sessionFailure) {
                session.abandon()
                session.close()
            }
        }
        session.commit(pendingIntent.intentSender)
        session.close()
    }

    private suspend fun downloadAndUpdateWrapErrors() {
        try {
            downloadAndUpdate()
        } catch (e: Throwable) {
            Log.e(TAG, "Update failure", e)
            emitProgress(Progress.Failure(e))
        }
    }

    private class InstallReceiver : BroadcastReceiver() {
        val sessionId = UUID.randomUUID().toString()

        override fun onReceive(context: Context, intent: Intent) {
            if (sessionId != intent.action)
                return

            when (val status =
                intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_INVALID)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0)
                    val userIntervention = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)!!
                    Application.getCoroutineScope().launch {
                        emitProgress(Progress.NeedsUserIntervention(userIntervention, id))
                    }
                }

                PackageInstaller.STATUS_SUCCESS -> {
                    Application.getCoroutineScope().launch {
                        emitProgress(Progress.Complete)
                    }
                    context.applicationContext.unregisterReceiver(this)
                }

                else -> {
                    val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0)
                    try {
                        context.applicationContext.packageManager.packageInstaller.abandonSession(id)
                    } catch (_: SecurityException) {
                    }
                    val message =
                        intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Installation error $status"
                    Application.getCoroutineScope().launch {
                        val e = Exception(message)
                        Log.e(TAG, "Update failure", e)
                        emitProgress(Progress.Failure(e))
                    }
                    context.applicationContext.unregisterReceiver(this)
                }
            }
        }
    }

    fun monitorForUpdates() {
        if (installerIsGooglePlay())
            return

        Application.getCoroutineScope().launch(Dispatchers.IO) {
            if (UserKnobs.updaterNewerVersionSeen.firstOrNull()?.let { versionIsNewer(it, CURRENT_VERSION) } == true)
                return@launch

            var waitTime = 15
            while (true) {
                try {
                    val updateVersion = versionOfFile(checkForUpdates().first) ?: throw IllegalStateException("No versions returned")
                    if (versionIsNewer(updateVersion, CURRENT_VERSION)) {
                        Log.i(TAG, "Update available: $updateVersion")
                        UserKnobs.setUpdaterNewerVersionSeen(updateVersion)
                        return@launch
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to check for updates", e)
                }
                delay(waitTime.minutes)
                waitTime = 45
            }
        }

        UserKnobs.updaterNewerVersionSeen.onEach { ver ->
            if (ver != null && versionIsNewer(
                    ver,
                    CURRENT_VERSION
                ) && UserKnobs.updaterNewerVersionConsented.firstOrNull()
                    ?.let { versionIsNewer(it, CURRENT_VERSION) } != true
            )
                emitProgress(Progress.Available(ver))
        }.launchIn(Application.getCoroutineScope())

        UserKnobs.updaterNewerVersionConsented.onEach { ver ->
            if (ver != null && versionIsNewer(ver, CURRENT_VERSION))
                downloadAndUpdateWrapErrors()
        }.launchIn(Application.getCoroutineScope())
    }

    fun installer(): String {
        val context = Application.get().applicationContext
        return try {
            val packageName = context.packageName
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName ?: ""
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName) ?: ""
            }
        } catch (_: Throwable) {
            ""
        }
    }

    fun installerIsGooglePlay(): Boolean = installer() == "com.android.vending"
}