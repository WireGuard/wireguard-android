/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.updater

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.wireguard.android.Application
import com.wireguard.android.BuildConfig
import com.wireguard.android.activity.MainActivity
import com.wireguard.android.util.UserKnobs
import com.wireguard.android.util.applicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private const val UPDATE_URL_FMT = "https://download.wireguard.com/android-client/%s"
    private const val APK_NAME_PREFIX = BuildConfig.APPLICATION_ID + "-"
    private const val APK_NAME_SUFFIX = ".apk"
    private const val LATEST_FILE = "latest.sig"
    private const val RELEASE_PUBLIC_KEY_BASE64 = "RWTAzwGRYr3EC9px0Ia3fbttz8WcVN6wrOwWp2delz4el6SI8XmkKSMp"
    private val CURRENT_VERSION by lazy { Version(BuildConfig.VERSION_NAME) }

    private val updaterScope = CoroutineScope(Job() + Dispatchers.IO)

    private fun installer(context: Context): String = try {
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

    fun installerIsGooglePlay(context: Context): Boolean = installer(context) == "com.android.vending"

    sealed class Progress {
        object Complete : Progress()
        class Available(val version: String) : Progress() {
            fun update() {
                applicationScope.launch {
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
                applicationScope.launch {
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
                updaterScope.launch {
                    downloadAndUpdateWrapErrors()
                }
            }
        }

        class Corrupt(private val betterFile: String?) : Progress() {
            val downloadUrl: String
                get() = UPDATE_URL_FMT.format(betterFile ?: "")
        }
    }

    private val mutableState = MutableStateFlow<Progress>(Progress.Complete)
    val state = mutableState.asStateFlow()

    private suspend fun emitProgress(progress: Progress, force: Boolean = false) {
        if (force || mutableState.firstOrNull()?.javaClass != progress.javaClass)
            mutableState.emit(progress)
    }

    private class Sha256Digest(hex: String) {
        val bytes: ByteArray

        init {
            if (hex.length != 64)
                throw InvalidParameterException("SHA256 hashes must be 32 bytes long")
            bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private class Version(version: String) : Comparable<Version> {
        val parts: ULongArray

        init {
            val strParts = version.split(".")
            if (strParts.isEmpty())
                throw InvalidParameterException("Version has no parts")
            parts = ULongArray(strParts.size)
            for (i in parts.indices) {
                parts[i] = strParts[i].toULong()
            }
        }

        override fun toString(): String {
            return parts.joinToString(".")
        }

        override fun compareTo(other: Version): Int {
            for (i in 0 until max(parts.size, other.parts.size)) {
                val lhsPart = if (i < parts.size) parts[i] else 0UL
                val rhsPart = if (i < other.parts.size) other.parts[i] else 0UL
                if (lhsPart > rhsPart)
                    return 1
                else if (lhsPart < rhsPart)
                    return -1
            }
            return 0
        }
    }

    private class Update(val fileName: String, val version: Version, val hash: Sha256Digest)

    private fun versionOfFile(name: String): Version? {
        if (!name.startsWith(APK_NAME_PREFIX) || !name.endsWith(APK_NAME_SUFFIX))
            return null
        return try {
            Version(name.substring(APK_NAME_PREFIX.length, name.length - APK_NAME_SUFFIX.length))
        } catch (_: Throwable) {
            null
        }
    }

    private fun verifySignedFileList(signifyDigest: String): List<Update> {
        val updates = ArrayList<Update>(1)
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
        for (line in lines[2].split("\n").dropLastWhile { it.isEmpty() }) {
            val components = line.split("  ", limit = 2)
            if (components.size != 2)
                throw InvalidParameterException("Invalid file list format: too few components")
            /* If version is null, it's not a file we understand, but still a legitimate entry, so don't throw. */
            val version = versionOfFile(components[1]) ?: continue
            updates.add(Update(components[1], version, Sha256Digest(components[0])))
        }
        return updates
    }

    private fun checkForUpdates(): Update? {
        val connection = URL(UPDATE_URL_FMT.format(LATEST_FILE)).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", Application.USER_AGENT)
        connection.connect()
        if (connection.responseCode != HttpURLConnection.HTTP_OK)
            throw IOException(connection.responseMessage)
        var fileListBytes = ByteArray(1024 * 512 /* 512 KiB */)
        connection.inputStream.use {
            val len = it.read(fileListBytes)
            if (len <= 0)
                throw IOException("File list is empty")
            fileListBytes = fileListBytes.sliceArray(0 until len)
        }
        return verifySignedFileList(fileListBytes.decodeToString()).maxByOrNull { it.version }
    }

    private suspend fun downloadAndUpdate() = withContext(Dispatchers.IO) {
        val receiver = InstallReceiver()
        val context = Application.get().applicationContext
        val pendingIntent = withContext(Dispatchers.Main) {
            ContextCompat.registerReceiver(context, receiver, IntentFilter(receiver.sessionId), ContextCompat.RECEIVER_NOT_EXPORTED)
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(receiver.sessionId).setPackage(context.packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }

        emitProgress(Progress.Rechecking)
        val update = checkForUpdates()
        if (update == null || update.version <= CURRENT_VERSION) {
            emitProgress(Progress.Complete)
            return@withContext
        }

        emitProgress(Progress.Downloading(0UL, 0UL), true)
        val connection = URL(UPDATE_URL_FMT.format(update.fileName)).openConnection() as HttpURLConnection
        connection.setRequestProperty("User-Agent", Application.USER_AGENT)
        connection.connect()
        if (connection.responseCode != HttpURLConnection.HTTP_OK)
            throw IOException("Update could not be fetched: ${connection.responseCode}")

        var downloadedByteLen: ULong = 0UL
        val totalByteLen = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) connection.contentLengthLong else connection.contentLength).toLong().toULong()
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
            if (!digest.digest().contentEquals(update.hash.bytes))
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

    private var updating = false
    private suspend fun downloadAndUpdateWrapErrors() {
        if (updating)
            return
        updating = true
        try {
            downloadAndUpdate()
        } catch (e: Throwable) {
            Log.e(TAG, "Update failure", e)
            emitProgress(Progress.Failure(e))
        }
        updating = false
    }

    private class InstallReceiver : BroadcastReceiver() {
        val sessionId = UUID.randomUUID().toString()

        override fun onReceive(context: Context, intent: Intent) {
            if (sessionId != intent.action)
                return

            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_INVALID)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0)
                    val userIntervention = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)!!
                    applicationScope.launch {
                        emitProgress(Progress.NeedsUserIntervention(userIntervention, id))
                    }
                }

                PackageInstaller.STATUS_SUCCESS -> {
                    applicationScope.launch {
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
                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Installation error $status"
                    applicationScope.launch {
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
        if (BuildConfig.DEBUG)
            return

        val context = Application.get()

        if (installerIsGooglePlay(context))
            return

        if (if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            } else {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            }.requestedPermissions?.contains(Manifest.permission.REQUEST_INSTALL_PACKAGES) != true
        ) {
            if (installer(context).isNotEmpty()) {
                updaterScope.launch {
                    val update = try {
                        checkForUpdates()
                    } catch (_: Throwable) {
                        null
                    }
                    emitProgress(Progress.Corrupt(update?.fileName))
                }
            }
            return
        }

        updaterScope.launch {
            if (UserKnobs.updaterNewerVersionSeen.firstOrNull()?.let { Version(it) > CURRENT_VERSION } == true)
                return@launch

            var waitTime = 15
            while (true) {
                try {
                    val update = checkForUpdates() ?: continue
                    if (update.version > CURRENT_VERSION) {
                        Log.i(TAG, "Update available: ${update.version}")
                        UserKnobs.setUpdaterNewerVersionSeen(update.version.toString())
                        return@launch
                    }
                } catch (_: Throwable) {
                }
                delay(waitTime.minutes)
                waitTime = 45
            }
        }

        UserKnobs.updaterNewerVersionSeen.onEach { ver ->
            if (
                ver != null &&
                Version(ver) > CURRENT_VERSION &&
                UserKnobs.updaterNewerVersionConsented.firstOrNull()?.let { Version(it) > CURRENT_VERSION } != true
            )
                emitProgress(Progress.Available(ver))
        }.launchIn(applicationScope)

        UserKnobs.updaterNewerVersionConsented.onEach { ver ->
            if (ver != null && Version(ver) > CURRENT_VERSION)
                updaterScope.launch {
                    downloadAndUpdateWrapErrors()
                }
        }.launchIn(applicationScope)
    }

    class AppUpdatedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED)
                return

            if (installer(context) != context.packageName)
                return

            /* TODO: does not work because of restrictions placed on broadcast receivers. */
            val start = Intent(context, MainActivity::class.java)
            start.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(start)
        }
    }
}
