/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.model

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.wireguard.android.Application.Companion.get
import com.wireguard.android.Application.Companion.getBackend
import com.wireguard.android.Application.Companion.getTunnelManager
import com.wireguard.android.BR
import com.wireguard.android.R
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.configStore.ConfigStore
import com.wireguard.android.databinding.ObservableSortedKeyedArrayList
import com.wireguard.android.util.ErrorMessages
import com.wireguard.android.util.UserKnobs
import com.wireguard.android.util.applicationScope
import com.wireguard.config.Config
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Represents the visual connection state of a tunnel for UI purposes
 */
enum class ConnectionState {
    /** Tunnel is down - no icon should be shown */
    DOWN,
    /** Tunnel is up but establishing connection (first 30s or no handshake yet) */
    CONNECTING,
    /** Tunnel is up and has active handshakes */
    CONNECTED,
    /** Tunnel is up but handshakes are stale (disconnected) */
    DISCONNECTED
}

/**
 * Maintains and mediates changes to the set of available WireGuard tunnels,
 */
class TunnelManager(private val configStore: ConfigStore) : BaseObservable() {
    private val tunnels = CompletableDeferred<ObservableSortedKeyedArrayList<String, ObservableTunnel>>()
    private val context: Context = get()
    private val tunnelMap: ObservableSortedKeyedArrayList<String, ObservableTunnel> = ObservableSortedKeyedArrayList(TunnelComparator)
    private var haveLoaded = false
    private val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val tunnelNoHandshakeStartTime: MutableMap<String, Long> = mutableMapOf()

    init {
        // Create notification channel for stale handshake warnings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STALE_HANDSHAKE_CHANNEL_ID,
                context.getString(R.string.notification_channel_stale_handshake),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_stale_handshake_description)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showStaleHandshakeNotification(tunnelName: String, handshakeAgeSeconds: Long?, isNone: Boolean = false) {
        val contentText = if (isNone) {
            context.getString(R.string.notification_no_handshake_text, tunnelName)
        } else {
            context.getString(R.string.notification_stale_handshake_text, tunnelName, handshakeAgeSeconds!!)
        }

        val notification = NotificationCompat.Builder(context, STALE_HANDSHAKE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_disconnected)
            .setContentTitle(context.getString(R.string.notification_stale_handshake_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true) // Makes it persistent (can't be dismissed)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(STALE_HANDSHAKE_NOTIFICATION_ID, notification)
    }

    private fun dismissStaleHandshakeNotification() {
        notificationManager.cancel(STALE_HANDSHAKE_NOTIFICATION_ID)
    }

    /**
     * Determines the visual connection state for a tunnel.
     * Only returns non-DOWN states when tunnel is UP.
     */
    suspend fun getConnectionState(tunnel: ObservableTunnel): ConnectionState = withContext(Dispatchers.IO) {
        // If tunnel is down, no state to show
        if (tunnel.state != Tunnel.State.UP) {
            return@withContext ConnectionState.DOWN
        }

        // Tunnel is UP - determine connection health
        val currentTime = System.currentTimeMillis()
        val backend = getBackend()
        val config = try {
            tunnel.config ?: getTunnelConfig(tunnel)
        } catch (e: Exception) {
            return@withContext ConnectionState.CONNECTING
        }

        val statistics = try {
            backend.getStatistics(tunnel)
        } catch (e: Exception) {
            // If we can't get statistics, assume connecting
            return@withContext ConnectionState.CONNECTING
        }

        // Get the first peer's statistics (most tunnels have one peer)
        val firstPeer = config.peers.firstOrNull() ?: return@withContext ConnectionState.CONNECTING
        val peerStats = statistics?.peer(firstPeer.publicKey)

        if (peerStats == null) {
            // No peer statistics available - connecting
            return@withContext ConnectionState.CONNECTING
        }

        val latestHandshakeEpoch = peerStats.latestHandshakeEpochMillis

        when {
            latestHandshakeEpoch == 0L -> {
                // Handshake is NONE/NEVER - check if we're in initial 30s
                val noneStartTime = tunnelNoHandshakeStartTime[tunnel.name] ?: currentTime
                val timeSinceNone = currentTime - noneStartTime
                val timeSinceNoneSeconds = timeSinceNone / 1000

                if (timeSinceNoneSeconds < 30) {
                    ConnectionState.CONNECTING
                } else {
                    // Past 30 seconds with no handshake - disconnected
                    ConnectionState.DISCONNECTED
                }
            }
            else -> {
                // We have a handshake - check if it's stale
                val timeSinceHandshake = currentTime - latestHandshakeEpoch
                val handshakeAgeSeconds = timeSinceHandshake / 1000

                when {
                    handshakeAgeSeconds < 135 -> {
                        // Handshake is recent - connected
                        ConnectionState.CONNECTED
                    }
                    else -> {
                        // Handshake is stale (>135s) - disconnected
                        ConnectionState.DISCONNECTED
                    }
                }
            }
        }
    }

    private fun addToList(name: String, config: Config?, state: Tunnel.State): ObservableTunnel {
        val tunnel = ObservableTunnel(this, name, config, state)
        tunnelMap.add(tunnel)
        return tunnel
    }

    suspend fun getTunnels(): ObservableSortedKeyedArrayList<String, ObservableTunnel> = tunnels.await()

    suspend fun create(name: String, config: Config?): ObservableTunnel = withContext(Dispatchers.Main.immediate) {
        if (Tunnel.isNameInvalid(name))
            throw IllegalArgumentException(context.getString(R.string.tunnel_error_invalid_name))
        if (tunnelMap.containsKey(name))
            throw IllegalArgumentException(context.getString(R.string.tunnel_error_already_exists, name))
        addToList(name, withContext(Dispatchers.IO) { configStore.create(name, config!!) }, Tunnel.State.DOWN)
    }

    suspend fun delete(tunnel: ObservableTunnel) = withContext(Dispatchers.Main.immediate) {
        val originalState = tunnel.state
        val wasLastUsed = tunnel == lastUsedTunnel
        // Make sure nothing touches the tunnel.
        if (wasLastUsed)
            lastUsedTunnel = null
        tunnelMap.remove(tunnel)
        try {
            if (originalState == Tunnel.State.UP)
                withContext(Dispatchers.IO) { getBackend().setState(tunnel, Tunnel.State.DOWN, null) }
            try {
                withContext(Dispatchers.IO) { configStore.delete(tunnel.name) }
            } catch (e: Throwable) {
                if (originalState == Tunnel.State.UP)
                    withContext(Dispatchers.IO) { getBackend().setState(tunnel, Tunnel.State.UP, tunnel.config) }
                throw e
            }
        } catch (e: Throwable) {
            // Failure, put the tunnel back.
            tunnelMap.add(tunnel)
            if (wasLastUsed)
                lastUsedTunnel = tunnel
            throw e
        }
    }

    @get:Bindable
    var lastUsedTunnel: ObservableTunnel? = null
        private set(value) {
            if (value == field) return
            field = value
            notifyPropertyChanged(BR.lastUsedTunnel)
            applicationScope.launch { UserKnobs.setLastUsedTunnel(value?.name) }
        }

    suspend fun getTunnelConfig(tunnel: ObservableTunnel): Config = withContext(Dispatchers.Main.immediate) {
        tunnel.onConfigChanged(withContext(Dispatchers.IO) { configStore.load(tunnel.name) })!!
    }

    fun onCreate() {
        applicationScope.launch {
            try {
                onTunnelsLoaded(withContext(Dispatchers.IO) { configStore.enumerate() }, withContext(Dispatchers.IO) { getBackend().runningTunnelNames })
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    private fun onTunnelsLoaded(present: Iterable<String>, running: Collection<String>) {
        for (name in present)
            addToList(name, null, if (running.contains(name)) Tunnel.State.UP else Tunnel.State.DOWN)
        applicationScope.launch {
            val lastUsedName = UserKnobs.lastUsedTunnel.first()
            if (lastUsedName != null)
                lastUsedTunnel = tunnelMap[lastUsedName]
            haveLoaded = true
            restoreState(true)
            tunnels.complete(tunnelMap)

            // Start the handshake monitor for automatic DNS re-resolution
            startHandshakeMonitor()
        }
    }

    private fun refreshTunnelStates() {
        applicationScope.launch {
            try {
                val running = withContext(Dispatchers.IO) { getBackend().runningTunnelNames }
                for (tunnel in tunnelMap)
                    tunnel.onStateChanged(if (running.contains(tunnel.name)) Tunnel.State.UP else Tunnel.State.DOWN)
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    suspend fun restoreState(force: Boolean) {
        if (!haveLoaded || (!force && !UserKnobs.restoreOnBoot.first()))
            return
        val previouslyRunning = UserKnobs.runningTunnels.first()
        if (previouslyRunning.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                tunnelMap.filter { previouslyRunning.contains(it.name) }.map { async(Dispatchers.IO + SupervisorJob()) { setTunnelState(it, Tunnel.State.UP) } }
                    .awaitAll()
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    suspend fun saveState() {
        UserKnobs.setRunningTunnels(tunnelMap.filter { it.state == Tunnel.State.UP }.map { it.name }.toSet())
    }

    suspend fun setTunnelConfig(tunnel: ObservableTunnel, config: Config): Config = withContext(Dispatchers.Main.immediate) {
        tunnel.onConfigChanged(withContext(Dispatchers.IO) {
            getBackend().setState(tunnel, tunnel.state, config)
            configStore.save(tunnel.name, config)
        })!!
    }

    suspend fun setTunnelName(tunnel: ObservableTunnel, name: String): String = withContext(Dispatchers.Main.immediate) {
        if (Tunnel.isNameInvalid(name))
            throw IllegalArgumentException(context.getString(R.string.tunnel_error_invalid_name))
        if (tunnelMap.containsKey(name)) {
            throw IllegalArgumentException(context.getString(R.string.tunnel_error_already_exists, name))
        }
        val originalState = tunnel.state
        val wasLastUsed = tunnel == lastUsedTunnel
        // Make sure nothing touches the tunnel.
        if (wasLastUsed)
            lastUsedTunnel = null
        tunnelMap.remove(tunnel)
        var throwable: Throwable? = null
        var newName: String? = null
        try {
            if (originalState == Tunnel.State.UP)
                withContext(Dispatchers.IO) { getBackend().setState(tunnel, Tunnel.State.DOWN, null) }
            withContext(Dispatchers.IO) { configStore.rename(tunnel.name, name) }
            newName = tunnel.onNameChanged(name)
            if (originalState == Tunnel.State.UP)
                withContext(Dispatchers.IO) { getBackend().setState(tunnel, Tunnel.State.UP, tunnel.config) }
        } catch (e: Throwable) {
            throwable = e
            // On failure, we don't know what state the tunnel might be in. Fix that.
            getTunnelState(tunnel)
        }
        // Add the tunnel back to the manager, under whatever name it thinks it has.
        tunnelMap.add(tunnel)
        if (wasLastUsed)
            lastUsedTunnel = tunnel
        if (throwable != null)
            throw throwable
        newName!!
    }

    suspend fun setTunnelState(tunnel: ObservableTunnel, state: Tunnel.State): Tunnel.State = withContext(Dispatchers.Main.immediate) {
        var newState = tunnel.state
        var throwable: Throwable? = null
        try {
            newState = withContext(Dispatchers.IO) { getBackend().setState(tunnel, state, tunnel.getConfigAsync()) }
            if (newState == Tunnel.State.UP)
                lastUsedTunnel = tunnel
            else if (newState == Tunnel.State.DOWN) {
                // Tunnel is now down - dismiss any warning notifications and clear tracking
                dismissStaleHandshakeNotification()
                tunnelNoHandshakeStartTime.remove(tunnel.name)
            }
        } catch (e: Throwable) {
            throwable = e
        }
        tunnel.onStateChanged(newState)

        // Update connection state immediately based on new tunnel state
        if (newState == Tunnel.State.DOWN) {
            tunnel.onConnectionStateChanged(ConnectionState.DOWN)
        } else if (newState == Tunnel.State.UP) {
            // Immediately get and set connection state when tunnel goes UP
            // This will show CONNECTING icon right away
            val connectionState = getConnectionState(tunnel)
            tunnel.onConnectionStateChanged(connectionState)
        }

        saveState()
        if (throwable != null)
            throw throwable
        newState
    }

    class IntentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            applicationScope.launch {
                val manager = getTunnelManager()
                if (intent == null) return@launch
                val action = intent.action ?: return@launch
                if ("com.wireguard.android.action.REFRESH_TUNNEL_STATES" == action) {
                    manager.refreshTunnelStates()
                    return@launch
                }
                if ("com.wireguard.android.action.DUMP_DIAGNOSTICS" == action) {
                    manager.dumpDiagnostics()
                    return@launch
                }
                if (!UserKnobs.allowRemoteControlIntents.first())
                    return@launch
                val state = when (action) {
                    "com.wireguard.android.action.SET_TUNNEL_UP" -> Tunnel.State.UP
                    "com.wireguard.android.action.SET_TUNNEL_DOWN" -> Tunnel.State.DOWN
                    else -> return@launch
                }
                val tunnelName = intent.getStringExtra("tunnel") ?: return@launch
                val tunnels = manager.getTunnels()
                val tunnel = tunnels[tunnelName] ?: return@launch
                try {
                    manager.setTunnelState(tunnel, state)
                } catch (e: Throwable) {
                    Toast.makeText(context, ErrorMessages[e], Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    suspend fun getTunnelState(tunnel: ObservableTunnel): Tunnel.State = withContext(Dispatchers.Main.immediate) {
        tunnel.onStateChanged(withContext(Dispatchers.IO) { getBackend().getState(tunnel) })
    }

    suspend fun getTunnelStatistics(tunnel: ObservableTunnel): Statistics = withContext(Dispatchers.Main.immediate) {
        tunnel.onStatisticsChanged(withContext(Dispatchers.IO) { getBackend().getStatistics(tunnel) })!!
    }

    suspend fun getDiagnostics(): String = withContext(Dispatchers.IO) {
        val builder = StringBuilder()
        builder.appendLine("=== WIREGUARD DIAGNOSTICS ===")
        builder.appendLine()

        try {
            val allTunnels = tunnelMap
            builder.appendLine("Total tunnels: ${allTunnels.size}")
            builder.appendLine()

            for (tunnel in allTunnels) {
                val state = tunnel.state
                builder.appendLine("--- Tunnel: ${tunnel.name} ---")
                builder.appendLine("  State: $state")

                if (state == Tunnel.State.UP) {
                    try {
                        val statistics = getBackend().getStatistics(tunnel)
                        val config = tunnel.getConfigAsync()
                        val currentTime = System.currentTimeMillis()

                        builder.appendLine("  Peers: ${config.peers.size}")
                        for (peer in config.peers) {
                            val publicKey = peer.publicKey
                            val endpoint = peer.endpoint.orElse(null)
                            val peerStats = statistics.peer(publicKey)

                            builder.appendLine("  --- Peer: ${publicKey.toBase64().substring(0, 16)}... ---")
                            if (endpoint != null) {
                                builder.appendLine("    Endpoint hostname: ${endpoint.host}:${endpoint.port}")

                                // Try to get resolved IP
                                val resolved = endpoint.getResolved().orElse(null)
                                if (resolved != null) {
                                    builder.appendLine("    Resolved IP: ${resolved.host}:${resolved.port}")
                                } else {
                                    builder.appendLine("    Resolved IP: NOT RESOLVED")
                                }
                            } else {
                                builder.appendLine("    Endpoint: NONE")
                            }

                            if (peerStats != null) {
                                val rxBytes = peerStats.rxBytes
                                val txBytes = peerStats.txBytes
                                val handshakeEpoch = peerStats.latestHandshakeEpochMillis

                                builder.appendLine("    RX bytes: $rxBytes")
                                builder.appendLine("    TX bytes: $txBytes")

                                if (handshakeEpoch > 0) {
                                    val handshakeAge = (currentTime - handshakeEpoch) / 1000
                                    builder.appendLine("    Last handshake: ${handshakeAge}s ago")
                                    builder.appendLine("    Handshake status: ${if (handshakeAge > STALE_HANDSHAKE_THRESHOLD_MS / 1000) "STALE" else "FRESH"}")
                                } else {
                                    builder.appendLine("    Last handshake: NEVER")
                                }
                            } else {
                                builder.appendLine("    Statistics: NOT AVAILABLE")
                            }
                        }
                        builder.appendLine()
                    } catch (e: Exception) {
                        builder.appendLine("  Error: ${e.message}")
                        builder.appendLine()
                    }
                } else {
                    builder.appendLine()
                }
            }

            builder.appendLine("--- Configuration ---")
            builder.appendLine("DNS re-resolve enabled: ${UserKnobs.enableDnsReresolve.first()}")
            builder.appendLine("Handshake check interval: ${HANDSHAKE_CHECK_INTERVAL_MS / 1000}s")
            builder.appendLine("Stale handshake threshold: ${STALE_HANDSHAKE_THRESHOLD_MS / 1000}s")
            builder.appendLine()
            builder.appendLine("=== END DIAGNOSTICS ===")
        } catch (e: Exception) {
            builder.appendLine("Error generating diagnostics: ${e.message}")
        }

        return@withContext builder.toString()
    }

    private suspend fun dumpDiagnostics() {
        val diagnostics = getDiagnostics()
        // Log each line separately for logcat
        diagnostics.lines().forEach { line ->
            Log.i(TAG, line)
        }
    }

    private fun startHandshakeMonitor() {
        applicationScope.launch {
            while (true) {
                delay(HANDSHAKE_CHECK_INTERVAL_MS)
                try {
                    // Check if feature is enabled
                    if (!UserKnobs.enableDnsReresolve.first()) {
                        continue
                    }

                    // Get all running tunnels
                    val runningTunnels = tunnelMap.filter { it.state == Tunnel.State.UP }
                    if (runningTunnels.isEmpty()) {
                        // No tunnels running - dismiss any existing warning notifications
                        dismissStaleHandshakeNotification()
                        // Clear tracking state
                        tunnelNoHandshakeStartTime.clear()
                        continue
                    }

                    val currentTime = System.currentTimeMillis()

                    // Check each running tunnel
                    for (tunnel in runningTunnels) {
                        try {
                            // Update connection state for UI
                            val connectionState = getConnectionState(tunnel)
                            withContext(Dispatchers.Main.immediate) {
                                tunnel.onConnectionStateChanged(connectionState)
                            }

                            val statistics = withContext(Dispatchers.IO) {
                                getBackend().getStatistics(tunnel)
                            }
                            val config = tunnel.getConfigAsync()

                            // Check each peer for stale handshakes
                            for (peer in config.peers) {
                                val publicKey = peer.publicKey
                                val peerStats = statistics.peer(publicKey)
                                val endpoint = peer.endpoint.orElse(null)

                                if (peerStats != null && peerStats.latestHandshakeEpochMillis > 0) {
                                    val timeSinceHandshake = currentTime - peerStats.latestHandshakeEpochMillis
                                    val handshakeAgeSeconds = timeSinceHandshake / 1000

                                    // Handshake exists - clear NONE tracking
                                    tunnelNoHandshakeStartTime.remove(tunnel.name)

                                    // Log handshake age at INFO level for monitoring
                                    if (endpoint != null) {
                                        Log.i(TAG, "Tunnel '${tunnel.name}': peer endpoint=${endpoint.host}:${endpoint.port}, " +
                                                "handshake_age=${handshakeAgeSeconds}s")
                                    }

                                    if (timeSinceHandshake > STALE_HANDSHAKE_THRESHOLD_MS) {
                                        if (endpoint != null && endpoint.host.isNotEmpty()) {
                                            Log.w(TAG, "Handshake STALE for tunnel '${tunnel.name}': " +
                                                    "endpoint=${endpoint.host}:${endpoint.port}, " +
                                                    "handshake_age=${handshakeAgeSeconds}s, " +
                                                    "threshold=${STALE_HANDSHAKE_THRESHOLD_MS / 1000}s. Triggering DNS re-resolution...")

                                            // Show persistent notification warning about stale handshake
                                            showStaleHandshakeNotification(tunnel.name, handshakeAgeSeconds, isNone = false)

                                            // Trigger DNS re-resolution by calling setState with current config
                                            // This will cause GoBackend to re-resolve all peer endpoints
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    getBackend().setState(tunnel, Tunnel.State.UP, config)
                                                    Log.i(TAG, "DNS re-resolution triggered successfully for tunnel '${tunnel.name}'")
                                                } catch (e: Exception) {
                                                    Log.e(TAG, "Failed to re-resolve DNS for tunnel '${tunnel.name}': ${e.message}", e)
                                                }
                                            }

                                            // Only re-resolve once per check cycle
                                            break
                                        }
                                    } else {
                                        // Handshake is fresh - dismiss any existing warning notification
                                        dismissStaleHandshakeNotification()
                                    }
                                } else if (endpoint != null) {
                                    // No handshake yet - track how long this has been happening
                                    Log.i(TAG, "Tunnel '${tunnel.name}': peer endpoint=${endpoint.host}:${endpoint.port}, " +
                                            "handshake=NONE (waiting for first handshake)")

                                    // Record the first time we saw NONE state for this tunnel
                                    val noneStartTime = tunnelNoHandshakeStartTime.getOrPut(tunnel.name) { currentTime }
                                    val timeSinceNone = currentTime - noneStartTime

                                    // If handshake has been NONE for too long, show notification
                                    if (timeSinceNone > NO_HANDSHAKE_THRESHOLD_MS) {
                                        Log.w(TAG, "Handshake NONE for too long on tunnel '${tunnel.name}': " +
                                                "${timeSinceNone / 1000}s without any handshake. Connection may be blocked.")
                                        showStaleHandshakeNotification(tunnel.name, null, isNone = true)
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            Log.e(TAG, "Error checking handshakes for tunnel '${tunnel.name}'", e)
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error in handshake monitor: ${Log.getStackTraceString(e)}")
                }
            }
        }
    }

    companion object {
        private const val TAG = "WireGuard/TunnelManager"

        // Check handshakes every 30 seconds (as recommended by reresolve-dns.sh)
        private const val HANDSHAKE_CHECK_INTERVAL_MS = 30_000L

        // Consider handshake stale after 135 seconds (matches reresolve-dns.sh threshold)
        private const val STALE_HANDSHAKE_THRESHOLD_MS = 135_000L

        // Alert if no handshake completes after 30 seconds of tunnel being UP
        // This is much shorter than STALE threshold because never establishing a connection
        // is a more immediate problem than losing an existing connection
        private const val NO_HANDSHAKE_THRESHOLD_MS = 30_000L

        // Notification constants
        private const val STALE_HANDSHAKE_NOTIFICATION_ID = 1001
        private const val STALE_HANDSHAKE_CHANNEL_ID = "wireguard_stale_handshake"
    }
}
