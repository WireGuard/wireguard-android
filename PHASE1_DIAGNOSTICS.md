# Phase 1: Emergency Diagnostics Implementation

## Summary
This document describes the diagnostic and troubleshooting improvements added to the WireGuard Android app to help identify why tunnels aren't working properly.

## Changes Implemented

### 1. Enhanced DNS Resolution Logging (`GoBackend.java:289-292`)
**What:** Added logging of resolved IP addresses whenever DNS resolution occurs.

**Location:** `tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java`

**Log Example:**
```
I/WireGuard/GoBackend: DNS resolved: example.com -> 203.0.113.42:51820
```

**Why:** Previously, the app would resolve DNS but never log what IP address was returned. Now you can see exactly what IP the tunnel is trying to connect to and whether it changes between re-resolutions.

---

### 2. Detailed Handshake Monitoring (`TunnelManager.kt:287-323`)
**What:** Enhanced handshake monitoring with detailed logging every 30 seconds.

**Location:** `ui/src/main/java/com/wireguard/android/model/TunnelManager.kt`

**Log Examples:**
```
I/WireGuard/TunnelManager: Tunnel 'MyVPN': peer endpoint=vpn.example.com:51820, handshake_age=45s
W/WireGuard/TunnelManager: Handshake STALE for tunnel 'MyVPN': endpoint=vpn.example.com:51820, handshake_age=156s, threshold=135s. Triggering DNS re-resolution...
I/WireGuard/TunnelManager: DNS re-resolution triggered successfully for tunnel 'MyVPN'
I/WireGuard/TunnelManager: Tunnel 'MyVPN': peer endpoint=vpn.example.com:51820, handshake=NONE (waiting for first handshake)
```

**Why:**
- Shows handshake age every 30 seconds so you can track if handshakes are happening
- Clearly indicates when handshakes become stale (>135s)
- Shows when DNS re-resolution is triggered
- Indicates peers waiting for first handshake

---

### 3. Diagnostic Dump Intent Action (`TunnelManager.kt:259-333`)
**What:** New intent action to dump complete diagnostic information on demand.

**Location:** `ui/src/main/java/com/wireguard/android/model/TunnelManager.kt`

**Usage from Termux/adb:**
```bash
# Dump diagnostics to logcat
am broadcast -a com.wireguard.android.action.DUMP_DIAGNOSTICS com.wireguard.android

# View the output
logcat -s WireGuard/TunnelManager:I
```

**Output Example:**
```
I/WireGuard/TunnelManager: === WIREGUARD DIAGNOSTICS DUMP ===
I/WireGuard/TunnelManager: Total tunnels: 1
I/WireGuard/TunnelManager: --- Tunnel: MyVPN ---
I/WireGuard/TunnelManager:   State: UP
I/WireGuard/TunnelManager:   Peers: 1
I/WireGuard/TunnelManager:   --- Peer: ABC123XYZ789... ---
I/WireGuard/TunnelManager:     Endpoint hostname: vpn.example.com:51820
I/WireGuard/TunnelManager:     Resolved IP: 203.0.113.42:51820
I/WireGuard/TunnelManager:     RX bytes: 1048576
I/WireGuard/TunnelManager:     TX bytes: 524288
I/WireGuard/TunnelManager:     Last handshake: 45s ago
I/WireGuard/TunnelManager:     Handshake status: FRESH
I/WireGuard/TunnelManager: DNS re-resolve enabled: true
I/WireGuard/TunnelManager: Handshake check interval: 30s
I/WireGuard/TunnelManager: Stale handshake threshold: 135s
I/WireGuard/TunnelManager: === END DIAGNOSTICS DUMP ===
```

**Why:** Provides a complete snapshot of tunnel state, resolved IPs, handshake status, and configuration at any time without restarting the app.

---

### 4. Log Level Filtering (`LogViewerActivity.kt`)
**What:** Added UI controls to filter logs by minimum level (Verbose, Debug, Info, Warning, Error).

**Location:** `ui/src/main/java/com/wireguard/android/activity/LogViewerActivity.kt`

**How to Use:**
1. Open WireGuard app
2. Navigate to Settings → View application log
3. Tap the menu (⋮) in the top right
4. Select "Minimum log level"
5. Choose your desired level:
   - **Verbose (V)**: Shows everything
   - **Debug (D)**: Shows debug and above
   - **Info (I)**: Shows info, warnings, errors (DEFAULT)
   - **Warning (W)**: Shows only warnings and errors
   - **Error (E)**: Shows only errors

**Why:** The logs were too cluttered with verbose/debug messages. Now you can focus on Info/Warning messages to see important events like DNS resolution and handshake monitoring without noise.

---

### 5. AndroidManifest Update
**What:** Added DUMP_DIAGNOSTICS action to intent filter.

**Location:** `ui/src/main/AndroidManifest.xml:134`

**Why:** Allows the diagnostic dump to be triggered via broadcast intent from external apps (Termux, Tasker, etc.).

---

## Available Intent Actions

All these work from Termux or adb:

```bash
# Refresh tunnel states (read current state from backend)
am broadcast -a com.wireguard.android.action.REFRESH_TUNNEL_STATES com.wireguard.android

# Dump diagnostics to logcat
am broadcast -a com.wireguard.android.action.DUMP_DIAGNOSTICS com.wireguard.android

# Set tunnel up (requires "Allow remote control intents" enabled in Settings)
am broadcast -a com.wireguard.android.action.SET_TUNNEL_UP -e tunnel "MyVPN" com.wireguard.android

# Set tunnel down (requires "Allow remote control intents" enabled in Settings)
am broadcast -a com.wireguard.android.action.SET_TUNNEL_DOWN -e tunnel "MyVPN" com.wireguard.android
```

**Note:** The original command `am start` doesn't work because these are broadcast receivers, not activities. Use `am broadcast` instead.

---

## How to Build

### Prerequisites
You need Java 17 or later to build this project. The current system has Java 11.

**Install Java 17 on macOS:**
```bash
# Using Homebrew
brew install openjdk@17

# Set JAVA_HOME for this session
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Or add to ~/.zshrc for permanent:
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 17)' >> ~/.zshrc
```

### Build Commands
```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# The APK will be at:
# ui/build/outputs/apk/debug/ui-debug.apk
# or
# ui/build/outputs/apk/release/ui-release.apk
```

### Install on Device
```bash
# Via adb
adb install -r ui/build/outputs/apk/debug/ui-debug.apk

# Or copy to device and install manually
```

---

## Troubleshooting Workflow

### Step 1: Start the Tunnel
Turn on your VPN tunnel in the WireGuard app.

### Step 2: Monitor Automatic Logs
Open the log viewer (Settings → View application log) and set minimum level to **Info (I)**.

Look for these patterns every 30 seconds:
```
I/WireGuard/TunnelManager: Tunnel 'MyVPN': peer endpoint=..., handshake_age=XXs
I/WireGuard/GoBackend: DNS resolved: hostname -> IP:port
```

### Step 3: Dump Diagnostics
From Termux, run:
```bash
am broadcast -a com.wireguard.android.action.DUMP_DIAGNOSTICS com.wireguard.android
```

Then check logcat:
```bash
logcat -s WireGuard/TunnelManager:I WireGuard/GoBackend:I
```

### Step 4: Analyze the Output
Look for:

#### Problem: Handshake Never Happens
```
I/WireGuard/TunnelManager: handshake=NONE (waiting for first handshake)
```
**Possible causes:**
- Wrong resolved IP (check DNS resolved log)
- Firewall blocking connection
- Incorrect peer public key or preshared key

#### Problem: Handshake Becomes Stale
```
W/WireGuard/TunnelManager: Handshake STALE for tunnel 'MyVPN': handshake_age=156s
```
**Possible causes:**
- DNS IP changed but re-resolution not working
- Network connectivity issue
- Server went down

#### Problem: DNS Resolution Returns Wrong IP
```
I/WireGuard/GoBackend: DNS resolved: vpn.example.com -> 192.168.1.100:51820
```
Compare this IP with what you expect. If it's wrong:
- Check your DNS server settings
- Try resolving manually: `nslookup vpn.example.com`
- Verify your dynamic DNS is updating correctly

#### Problem: DNS Resolution Returns Same Stale IP
```
# Multiple re-resolution attempts all return the same IP
I/WireGuard/GoBackend: DNS resolved: vpn.example.com -> 203.0.113.42:51820
I/WireGuard/GoBackend: DNS resolved: vpn.example.com -> 203.0.113.42:51820
I/WireGuard/GoBackend: DNS resolved: vpn.example.com -> 203.0.113.42:51820
```
**Root cause:** DNS caching in `InetEndpoint.java` (1-minute TTL)
**Solution:** This is a known issue. See "Known Issues" below.

---

## Known Issues

### DNS Cache Prevents Frequent Re-Resolution
**Location:** `tunnel/src/main/java/com/wireguard/config/InetEndpoint.java:95`

**Problem:** DNS results are cached for 1 minute, but handshake monitoring runs every 30 seconds. This means:
- Monitor detects stale handshake at T=0s → triggers re-resolve → gets cached IP
- Monitor runs again at T=30s → triggers re-resolve → still gets cached IP (from T=0s)
- Only at T=60s+ will a fresh DNS lookup occur

**Impact:** If your DNS changes rapidly (e.g., failover scenarios), the re-resolution may use stale cached IPs.

**Potential Fix (Future):** Reduce DNS cache TTL from 1 minute to 10 seconds, or add a "force refresh" parameter to bypass the cache when triggered by stale handshake detection.

---

## Next Steps (Phase 2)

If diagnostics reveal the DNS cache is the problem, consider:

1. **Reduce DNS cache TTL** in `InetEndpoint.java:95` from 1 minute to 10-15 seconds
2. **Add force refresh parameter** to `getResolved()` method to bypass cache
3. **Add UI notification** when DNS re-resolution occurs with the new IP
4. **Add "Force Reconnect" button** in UI that bypasses all caching

If handshakes simply aren't happening at all, investigate:
1. Firewall rules on device or server
2. NAT traversal issues
3. Incorrect WireGuard configuration (keys, allowed IPs)

---

## Files Modified

1. `tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java` - DNS resolution logging
2. `ui/src/main/java/com/wireguard/android/model/TunnelManager.kt` - Handshake monitoring, diagnostics dump
3. `ui/src/main/java/com/wireguard/android/activity/LogViewerActivity.kt` - Log level filtering
4. `ui/src/main/AndroidManifest.xml` - DUMP_DIAGNOSTICS intent action
5. `ui/src/main/res/menu/log_viewer.xml` - Log level filter menu
6. `ui/src/main/res/values/strings.xml` - Log level filter strings

---

## Testing Checklist

- [ ] Build succeeds with Java 17+
- [ ] App installs on device
- [ ] Log viewer shows filtered logs (test all 5 levels)
- [ ] REFRESH_TUNNEL_STATES intent works from Termux
- [ ] DUMP_DIAGNOSTICS intent works and logs output
- [ ] DNS resolved IP appears in logs when tunnel starts
- [ ] Handshake age logs appear every 30 seconds
- [ ] Stale handshake warning appears after 135s
- [ ] DNS re-resolution triggers when handshake stale

---

## Original Problem Statement

The tunnel was not working on Android but worked on MacBook Pro from the same network. Possible causes:
1. DNS resolution returning wrong IP on Android
2. Handshakes not happening due to connectivity issue
3. DNS re-resolution feature not triggering properly

**These changes provide visibility into all three potential issues.**
