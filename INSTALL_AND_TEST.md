# Installation and Testing Guide

## ✅ Build Complete!

Your WireGuard Android app with Phase 1 diagnostic improvements has been successfully built.

**APK Location:** `ui/build/outputs/apk/debug/ui-debug.apk`
**APK Size:** 24 MB
**Build Time:** 44 seconds

---

## Installation Options

### Option 1: Install via ADB (Recommended)

```bash
# Make sure your Android device is connected and USB debugging is enabled
adb devices

# Install the APK (will replace existing WireGuard app)
adb install -r ui/build/outputs/apk/debug/ui-debug.apk
```

### Option 2: Manual Installation

```bash
# Copy to your device
adb push ui/build/outputs/apk/debug/ui-debug.apk /sdcard/Download/

# Then on your device:
# 1. Open Files app
# 2. Navigate to Downloads folder
# 3. Tap ui-debug.apk
# 4. Allow installation from unknown sources if prompted
# 5. Tap "Install"
```

### Option 3: Transfer via File Sharing

You can also copy the APK to your device using:
- Google Drive
- Dropbox
- Email attachment
- Direct USB file transfer

Then install from the downloaded file.

---

## Testing the New Features

### 1. Test Log Level Filtering

1. Open WireGuard app
2. Go to **Settings** → **View application log**
3. Tap the menu icon (⋮) in the top right
4. Select **"Minimum log level"**
5. Try different levels:
   - **Info (I)** - Recommended for troubleshooting (default)
   - **Warning (W)** - Only shows warnings and errors
   - **Error (E)** - Only shows errors

**You should see the log refresh with only messages at or above your selected level.**

---

### 2. Test Automatic Handshake Monitoring

1. Turn on your VPN tunnel
2. Open log viewer (Settings → View application log)
3. Set log level to **Info (I)**
4. Wait and watch for these messages every 30 seconds:

```
I/WireGuard/TunnelManager: Tunnel 'YourTunnel': peer endpoint=vpn.example.com:51820, handshake_age=XXs
```

**Expected behavior:**
- Messages appear every 30 seconds
- Handshake age increments over time
- If handshake > 135s, you'll see a WARNING about stale handshake

---

### 3. Test DNS Resolution Logging

1. Start your tunnel (or restart if already running)
2. Check logs for DNS resolution messages:

```
I/WireGuard/GoBackend: DNS resolved: your-hostname -> IP_ADDRESS:PORT
```

**What to look for:**
- Does the resolved IP match what you expect?
- Compare with your MacBook: `nslookup your-vpn-hostname`
- If IPs differ, that's your problem!

---

### 4. Test Diagnostic Dump Intent (from Termux)

**Install Termux if you don't have it:**
https://f-droid.org/en/packages/com.termux/

**Run diagnostic dump:**
```bash
# Trigger the diagnostic dump
am broadcast -a com.wireguard.android.action.DUMP_DIAGNOSTICS com.wireguard.android

# View the output
logcat -s WireGuard/TunnelManager:I WireGuard/GoBackend:I
```

**Expected output:**
```
I/WireGuard/TunnelManager: === WIREGUARD DIAGNOSTICS DUMP ===
I/WireGuard/TunnelManager: Total tunnels: 1
I/WireGuard/TunnelManager: --- Tunnel: YourTunnel ---
I/WireGuard/TunnelManager:   State: UP
I/WireGuard/TunnelManager:   Peers: 1
I/WireGuard/TunnelManager:   --- Peer: ABC123... ---
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

---

## Troubleshooting Scenarios

### Scenario 1: Handshakes Never Happen

**Symptoms:**
```
I/WireGuard/TunnelManager: handshake=NONE (waiting for first handshake)
```

**Possible causes:**
1. Wrong resolved IP address
2. Firewall blocking connection
3. Incorrect WireGuard configuration (keys, allowed IPs)

**Next steps:**
- Check if resolved IP matches expected IP
- Try pinging the resolved IP from your device
- Verify server is accessible on that IP:port

---

### Scenario 2: Handshakes Become Stale

**Symptoms:**
```
W/WireGuard/TunnelManager: Handshake STALE for tunnel 'MyVPN': handshake_age=156s
I/WireGuard/TunnelManager: DNS re-resolution triggered successfully
```

**Possible causes:**
1. Server IP changed (dynamic DNS)
2. Network connectivity issue
3. DNS cache returning old IP

**Next steps:**
- Check if resolved IP changes after re-resolution
- Compare resolved IPs before and after stale handshake
- Verify your dynamic DNS is updating correctly

---

### Scenario 3: DNS Returns Wrong IP

**Symptoms:**
```
I/WireGuard/GoBackend: DNS resolved: vpn.example.com -> 192.168.1.100:51820
```
But you know the correct IP should be `203.0.113.42`

**Possible causes:**
1. Android using different DNS server than MacBook
2. Split DNS / VPN DNS issues
3. DNS propagation delay

**Next steps:**
- Check Android DNS settings
- Compare with MacBook: `nslookg vpn.example.com`
- Try using IP address directly instead of hostname (temporary test)

---

### Scenario 4: DNS Cache Issue

**Symptoms:**
- Handshake goes stale
- Re-resolution triggered multiple times
- Same IP returned every time (even though it should change)

**Root cause:** DNS cache in `InetEndpoint.java` (1-minute TTL)

**This is a known issue. If you confirm this is the problem, we can proceed with Phase 2 to fix it.**

---

## Other Available Commands

### Refresh Tunnel States
```bash
am broadcast -a com.wireguard.android.action.REFRESH_TUNNEL_STATES com.wireguard.android
```

### Control Tunnels (requires "Allow remote control intents" enabled in Settings)
```bash
# Turn tunnel on
am broadcast -a com.wireguard.android.action.SET_TUNNEL_UP -e tunnel "TunnelName" com.wireguard.android

# Turn tunnel off
am broadcast -a com.wireguard.android.action.SET_TUNNEL_DOWN -e tunnel "TunnelName" com.wireguard.android
```

---

## Viewing Logs Efficiently

### From Termux
```bash
# Watch all WireGuard logs in real-time
logcat -s WireGuard:I

# Watch specific components
logcat -s WireGuard/TunnelManager:I WireGuard/GoBackend:I

# Filter by log level
logcat -s WireGuard:I  # Info and above
logcat -s WireGuard:W  # Warning and above
logcat -s WireGuard:E  # Error only

# Clear logs and start fresh
logcat -c && logcat -s WireGuard:I
```

### From ADB (on your computer)
```bash
# Same commands as above, but prefix with adb:
adb logcat -s WireGuard/TunnelManager:I WireGuard/GoBackend:I
```

---

## What to Report Back

After testing, please share:

1. **Diagnostic dump output** (the full output from `DUMP_DIAGNOSTICS` intent)
2. **Resolved IP addresses** shown in logs
3. **Expected IP address** (what does your MacBook resolve? Run `nslookup your-hostname`)
4. **Handshake behavior:**
   - Do handshakes happen at all?
   - Do they stay fresh or go stale?
   - How often do they go stale?
5. **Any error messages** you see in the logs

This information will help us determine:
- Is DNS resolution the problem?
- Is it the DNS cache issue?
- Is it something else entirely?

---

## Next Steps

Based on your testing results, we can:

**If DNS cache is the problem (Phase 2):**
- Reduce DNS cache TTL from 60s to 10-15s
- Add force-refresh parameter to bypass cache
- Add UI notification when DNS re-resolves

**If handshakes never happen:**
- Investigate firewall/network issues
- Check WireGuard configuration
- Verify server accessibility

**If wrong IPs are resolved:**
- Investigate Android DNS configuration
- Consider using IP addresses instead of hostnames
- Check dynamic DNS update mechanism

---

## Java Setup Summary

For future builds, Java 17 is now configured:

```bash
# Verify Java version
java -version
# Should show: openjdk version "17.0.17"

# Check JAVA_HOME
echo $JAVA_HOME
# Should show: /opt/homebrew/Cellar/openjdk@17/17.0.17/libexec/openjdk.jdk/Contents/Home

# Both Java 11 and 17 are available:
/usr/libexec/java_home -V
```

To switch back to Java 11 temporarily (if ever needed):
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 11)
```

---

## Files Modified Summary

All changes are documented in `PHASE1_DIAGNOSTICS.md`

**Modified files:**
1. `tunnel/src/main/java/com/wireguard/android/backend/GoBackend.java`
2. `ui/src/main/java/com/wireguard/android/model/TunnelManager.kt`
3. `ui/src/main/java/com/wireguard/android/activity/LogViewerActivity.kt`
4. `ui/src/main/AndroidManifest.xml`
5. `ui/src/main/res/menu/log_viewer.xml`
6. `ui/src/main/res/values/strings.xml`

---

Good luck with testing! Let me know what you find. 🚀
