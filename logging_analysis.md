# WireGuard Android Logging System Analysis

## Overview
The WireGuard Android app uses Android's built-in `android.util.Log` class for logging. The logging architecture is distributed across the tunnel library (Java) and UI layer (Kotlin), with consistent TAG naming conventions and logging patterns.

## Key Logging Components

### 1. TAG Constants (Log Tags)
All log entries use the format: `WireGuard/<ComponentName>`

**Tunnel Module (Java):**
- `WireGuard/GoBackend` - Userspace WireGuard implementation
- `WireGuard/WgQuickBackend` - Kernel WireGuard implementation
- `WireGuard/RootShell` - Root command execution
- `WireGuard/ToolsInstaller` - WireGuard tools installation
- `WireGuard/SharedLibraryLoader` - Native library loading

**UI Module (Kotlin):**
- `WireGuard/Application` - App initialization
- `WireGuard/TunnelManager` - Tunnel lifecycle management
- `WireGuard/ObservableTunnel` - Tunnel state observables
- `WireGuard/LogViewerActivity` - Log viewer UI
- `WireGuard/FileConfigStore` - Configuration persistence
- `WireGuard/QuickTileService` - Quick settings tile
- `WireGuard/TunnelEditorFragment` - Tunnel editor UI
- `WireGuard/BootShutdownReceiver` - Boot/shutdown events
- `WireGuard/BiometricAuthenticator` - Biometric authentication
- `WireGuard/KernelModuleEnablerPreference` - Kernel module preferences
- `WireGuard/ZipExporterPreference` - Configuration export
- `WireGuard/TunnelImporter` - Configuration import
- `WireGuard/Updater` - App update checking
- `QrCodeFromFileScanner` - QR code scanning (shorter tag)

### 2. Log Levels Used
- **Log.v()** - VERBOSE (RootShell command execution details)
- **Log.d()** - DEBUG (Library loading, tool extraction, service startup)
- **Log.i()** - INFO (Tunnel state changes, DNS re-resolution)
- **Log.w()** - WARNING (DNS resolution failures, already running/down tunnels)
- **Log.e()** - ERROR (Exceptions and failures)

## Key Logging Locations

### GoBackend.java (Userspace VPN Implementation)
File: `/Users/orwa/repos/wireguard-android/tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java`

**DNS Resolution Logging (Lines 275-291):**
```java
dnsRetry: for (int i = 0; i < DNS_RESOLUTION_RETRIES; ++i) {
    for (final Peer peer : config.getPeers()) {
        final InetEndpoint ep = peer.getEndpoint().orElse(null);
        if (ep == null)
            continue;
        if (ep.getResolved().orElse(null) == null) {
            if (i < DNS_RESOLUTION_RETRIES - 1) {
                Log.w(TAG, "DNS host \"" + ep.getHost() + "\" failed to resolve; trying again");
                Thread.sleep(1000);
                continue dnsRetry;
            } else
                throw new BackendException(Reason.DNS_RESOLUTION_FAILURE, ep.getHost());
        }
    }
    break;
}
```

**Tunnel State Changes (Line 245):**
```java
Log.i(TAG, "Bringing tunnel " + tunnel.getName() + ' ' + state);
```

**VPN Service Startup (Lines 256, 341):**
```java
Log.d(TAG, "Requesting to start VpnService");
Log.d(TAG, "Go backend " + wgVersion());
```

**Configuration Constants (Lines 43-44):**
```java
private static final int DNS_RESOLUTION_RETRIES = 10;
private static final String TAG = "WireGuard/GoBackend";
```

### TunnelManager.kt (Tunnel Lifecycle Management)
File: `/Users/orwa/repos/wireguard-android/ui/src/main/java/com/wireguard/android/model/TunnelManager.kt`

**Automatic DNS Re-resolution Feature (Lines 255-321):**
```kotlin
private fun startHandshakeMonitor() {
    applicationScope.launch {
        while (true) {
            delay(HANDSHAKE_CHECK_INTERVAL_MS)
            try {
                // Check if feature is enabled
                if (!UserKnobs.enableDnsReresolve.first()) {
                    continue
                }

                val runningTunnels = tunnelMap.filter { it.state == Tunnel.State.UP }
                if (runningTunnels.isEmpty()) {
                    continue
                }

                val currentTime = System.currentTimeMillis()

                // Check each running tunnel
                for (tunnel in runningTunnels) {
                    try {
                        val statistics = withContext(Dispatchers.IO) {
                            getBackend().getStatistics(tunnel)
                        }
                        val config = tunnel.getConfigAsync()

                        // Check each peer for stale handshakes
                        for (peer in config.peers) {
                            val publicKey = peer.publicKey
                            val peerStats = statistics.peer(publicKey)

                            if (peerStats != null && peerStats.latestHandshakeEpochMillis > 0) {
                                val timeSinceHandshake = currentTime - peerStats.latestHandshakeEpochMillis

                                if (timeSinceHandshake > STALE_HANDSHAKE_THRESHOLD_MS) {
                                    val endpoint = peer.endpoint.orElse(null)
                                    if (endpoint != null && endpoint.host.isNotEmpty()) {
                                        Log.w(TAG, "Handshake stale for tunnel '${tunnel.name}', peer ${publicKey.toBase64()} " +
                                                "(${timeSinceHandshake / 1000}s old). Re-resolving endpoint ${endpoint.host}...")

                                        // Trigger DNS re-resolution
                                        withContext(Dispatchers.IO) {
                                            try {
                                                getBackend().setState(tunnel, Tunnel.State.UP, config)
                                                Log.i(TAG, "Successfully triggered DNS re-resolution for tunnel '${tunnel.name}'")
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Failed to re-resolve DNS for tunnel '${tunnel.name}': ${e.message}")
                                            }
                                        }

                                        // Only re-resolve once per check cycle
                                        break
                                    }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error checking handshakes for tunnel '${tunnel.name}': ${Log.getStackTraceString(e)}")
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in handshake monitor: ${Log.getStackTraceString(e)}")
            }
        }
    }
}
```

**Handshake Monitor Constants (Lines 323-331):**
```kotlin
companion object {
    private const val TAG = "WireGuard/TunnelManager"

    // Check handshakes every 30 seconds (as recommended by reresolve-dns.sh)
    private const val HANDSHAKE_CHECK_INTERVAL_MS = 30_000L

    // Consider handshake stale after 135 seconds (matches reresolve-dns.sh threshold)
    private const val STALE_HANDSHAKE_THRESHOLD_MS = 135_000L
}
```

### LogViewerActivity.kt (Real-time Log Viewer)
File: `/Users/orwa/repos/wireguard-android/ui/src/main/java/com/wireguard/android/activity/LogViewerActivity.kt`

**Log Buffer Management (Lines 212-213):**
```kotlin
val MAX_LINES = (1 shl 16) - 1      // 65,535 lines
val MAX_BUFFERED_LINES = (1 shl 14) - 1  // 16,383 lines
```

**Logcat Integration (Line 195):**
```kotlin
val builder = ProcessBuilder().command("logcat", "-b", "all", "-v", "threadtime", "*:V")
```

**Log Parsing (Lines 277-283):**
```kotlin
private fun parseLine(line: String): LogLine? {
    val m: Matcher = THREADTIME_LINE.matcher(line)
    return if (m.matches()) {
        LogLine(m.group(2)!!.toInt(), m.group(3)!!.toInt(), parseTime(m.group(1)!!), m.group(4)!!, m.group(5)!!, m.group(6)!!)
    } else {
        null
    }
}

// Pattern for parsing logcat threadtime format:
// 05-26 11:02:36.886 5689 5689 D AndroidRuntime: CheckJNI is OFF.
private val THREADTIME_LINE: Pattern =
    Pattern.compile("^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})(?:\\s+[0-9A-Za-z]+)?\\s+(\\d+)\\s+(\\d+)\\s+([A-Z])\\s+(.+?)\\s*: (.*)$")
```

### RootShell.java (Root Command Execution)
File: `/Users/orwa/repos/wireguard-android/tunnel/src/main/java/com/wireguard/android/util/RootShell.java`

**Verbose Logging of Shell Commands (Lines 93-128):**
```java
Log.v(TAG, "executing: " + command);
// ... command execution ...
Log.v(TAG, "stdout: " + line);
// ... output processing ...
Log.v(TAG, "stderr: " + line);
Log.v(TAG, "exit: " + errnoStdout);
```

**Root Shell Error Logging (Lines 164, 170):**
```java
Log.w(TAG, "Root check did not return correct UID: " + uid);
Log.w(TAG, "Root check returned an error: " + line);
```

### WgQuickBackend.java (Kernel WireGuard)
File: `/Users/orwa/repos/wireguard-android/tunnel/src/main/java/com/wireguard/android/backend/WgQuickBackend.java`

**Tunnel Enumeration Logging (Line 70):**
```java
Log.w(TAG, "Unable to enumerate running tunnels", e);
```

**Tunnel State Change Logging (Line 181):**
```java
Log.i(TAG, "Bringing tunnel " + tunnel.getName() + ' ' + state);
```

## Android Log.d/Log.i Length Limits

### Important Finding:
Android's `Log.d()`, `Log.i()`, `Log.w()`, `Log.e()` methods have a maximum message length limit:
- **4096 characters (4KB)** is the documented limit for individual log messages
- Messages longer than this will be silently truncated by the Android Logging system

### Current Implementation:
The WireGuard Android app does NOT implement custom log message splitting or truncation handling. Log messages are passed directly to Android's Log class:

```java
Log.i(TAG, "Bringing tunnel " + tunnel.getName() + ' ' + state);
Log.w(TAG, "DNS host \"" + ep.getHost() + "\" failed to resolve; trying again");
```

### Potential Issues:
1. **Long Peer Endpoint Names**: If peer endpoint hosts or full log messages exceed 4096 characters, they will be truncated in logcat output
2. **Detailed Error Messages**: Exception stack traces via `Log.getStackTraceString(e)` could exceed the limit
3. **Configuration Dumps**: Any logging of large config objects would be truncated

Example from TunnelManager:
```kotlin
Log.w(TAG, "Handshake stale for tunnel '${tunnel.name}', peer ${publicKey.toBase64()} " +
        "(${timeSinceHandshake / 1000}s old). Re-resolving endpoint ${endpoint.host}...")
```

This message could theoretically exceed 4096 bytes if `tunnel.name` and `endpoint.host` are very long.

## Log Storage and Viewing

### LogViewerActivity Architecture:
- Uses `ProcessBuilder` to spawn `logcat` process with options:
  - `-b all` - Show all buffers
  - `-v threadtime` - Use threadtime format
  - `*:V` - Show all messages at VERBOSE level and above

- **Real-time Streaming**: Reads from logcat stdout in a coroutine on `Dispatchers.IO`
- **Circular Buffer**: Maintains two circular arrays:
  - `rawLogLines` - Raw logcat strings (up to 65,535 lines)
  - `logLines` - Parsed LogLine objects with PID, TID, timestamp, level, tag, and message
- **UI Updates**: Batches log updates and notifies RecyclerView adapter on `Dispatchers.Main.immediate`
- **Export**: Can export all captured logs as plain text file or share via content provider

### Log Filtering:
The LogViewerActivity uses logcat's native filtering:
```kotlin
builder.environment()["LC_ALL"] = "C"  // Ensure consistent character encoding
```

There is no explicit tag filtering in the app - all logs are captured and displayed.

## Verbosity Settings

The app does NOT have configurable logging verbosity settings. All modules use:
- **VERBOSE (Log.v)**: Shell command execution details (RootShell)
- **DEBUG (Log.d)**: Library/tool extraction, service startup
- **INFO (Log.i)**: Tunnel state changes
- **WARNING (Log.w)**: Recoverable errors (DNS failures, tunnel already running)
- **ERROR (Log.e)**: Fatal errors with stack traces

Debug mode enables additional Android system checks:
```kotlin
if (BuildConfig.DEBUG) {
    StrictMode.setVmPolicy(VmPolicy.Builder().detectAll().penaltyLog().build())
    StrictMode.setThreadPolicy(ThreadPolicy.Builder().detectAll().penaltyLog().build())
}
```

## Summary

| Aspect | Details |
|--------|---------|
| **Log Framework** | Android's `android.util.Log` |
| **Tag Format** | `WireGuard/<ComponentName>` |
| **Max Message Length** | 4096 characters (Android limit) |
| **Truncation Handling** | NONE - relies on Android's truncation |
| **Log Viewer** | Real-time logcat viewer in LogViewerActivity |
| **Storage** | Circular buffer (65,535 lines max) |
| **Filtering** | Native logcat filtering at capture level |
| **Verbosity Control** | No configurable settings; fixed per component |
| **DNS Logging** | TunnelManager monitors handshakes every 30 seconds |
| **Stale Threshold** | 135 seconds for triggering re-resolution |
