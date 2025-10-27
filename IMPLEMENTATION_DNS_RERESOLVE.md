# Automatic DNS Re-Resolution Implementation

## Overview

This document describes the implementation of automatic DNS re-resolution for WireGuard peers with stale handshakes, inspired by the `reresolve-dns.sh` script from wireguard-tools.

## Problem Statement

When using dynamic DNS endpoints, the IP address of a peer may change while the tunnel is active. WireGuard only resolves DNS hostnames during tunnel startup, so if the endpoint IP changes, the tunnel cannot establish new connections until manually restarted.

The original `reresolve-dns.sh` script solves this by periodically checking handshake timestamps and re-running `wg set` to trigger endpoint re-resolution when handshakes become stale (>135 seconds old).

## Design Decisions

### 1. **Implementation Location**

**Decision**: Implement as a periodic check in the `TunnelManager` using Kotlin coroutines.

**Rationale**:
- `TunnelManager` already manages tunnel lifecycle and state
- Kotlin coroutines provide efficient background execution without additional WorkManager dependency
- Runs in the application's coroutine scope, automatically cleaned up with the app
- Direct access to tunnel state and backend operations

**Alternatives Considered**:
- ❌ WorkManager: Overkill for this feature, adds complexity and battery usage concerns
- ❌ Foreground Service: Would require notification and increases battery consumption
- ❌ AlarmManager: Less reliable due to Doze mode restrictions

### 2. **Check Interval**

**Decision**: Check every **30 seconds** (configurable constant).

**Rationale**:
- Matches the recommendation from the original `reresolve-dns.sh` README
- Frequent enough to catch stale handshakes quickly
- Infrequent enough to minimize battery impact
- WireGuard's default persistent keepalive is 25 seconds, so 30 seconds is reasonable

### 3. **Stale Handshake Threshold**

**Decision**: Consider handshake stale after **135 seconds** without update.

**Rationale**:
- Matches the exact threshold from `reresolve-dns.sh` (line 19)
- This is ~5.4x the default keepalive interval, allowing for temporary network issues
- Prevents false positives during brief connectivity problems
- Conservative enough to avoid unnecessary DNS queries

### 4. **Re-Resolution Mechanism**

**Decision**: Call `Backend.setState()` with the current config to trigger re-resolution.

**Rationale**:
- Leverages existing DNS resolution logic in `GoBackend.java:275-291`
- No need to duplicate DNS resolution code
- Maintains existing retry logic (10 attempts with 1s delay)
- Thread-safe and properly coordinated through TunnelManager

**Implementation**:
```kotlin
// For each peer with stale handshake:
backend.setState(tunnel, Tunnel.State.UP, tunnel.config)
```

This causes `GoBackend.setStateInternal()` to re-resolve DNS for all peer endpoints.

### 5. **User Control**

**Decision**: Add a user preference toggle, **enabled by default**.

**Rationale**:
- Most users with dynamic DNS endpoints want this behavior
- Power users may want to disable it (e.g., for debugging, battery concerns)
- Follows Android best practices for background operations
- Transparent to users who don't need it

**UI Location**: Settings → Advanced → "Automatic endpoint re-resolution"

### 6. **Scope and Lifecycle**

**Decision**: Monitor all active (UP) tunnels, stop when app is destroyed.

**Rationale**:
- Only running tunnels need DNS re-resolution
- Checking DOWN tunnels wastes resources
- Tied to `Application.coroutineScope` lifecycle
- Automatically stops when app is killed

### 7. **Error Handling**

**Decision**: Log errors but continue checking; don't bring tunnel down on failure.

**Rationale**:
- DNS re-resolution failures are non-fatal (tunnel continues with cached IP)
- Transient network issues shouldn't break the tunnel
- Users can see issues in logs if needed
- Preserves existing tunnel functionality

### 8. **Performance Considerations**

**Decision**:
- Single coroutine checks all tunnels sequentially
- Skip check if no tunnels are UP
- Use `withTimeoutOrNull` to prevent hanging

**Rationale**:
- Minimal resource usage (one timer, one coroutine)
- No battery impact when no tunnels active
- Timeout prevents indefinite blocking on backend calls

## Architecture

### Component Diagram

```
Application
    │
    ├─→ TunnelManager
    │       │
    │       ├─→ startHandshakeMonitor() [New]
    │       │       │
    │       │       └─→ Coroutine (30s interval)
    │       │               │
    │       │               ├─→ Check UserKnobs.enableDnsReresolve
    │       │               ├─→ Get all UP tunnels
    │       │               ├─→ getStatistics() for each
    │       │               └─→ If handshake > 135s:
    │       │                       └─→ setState(UP, config) [triggers DNS]
    │       │
    │       └─→ Existing tunnel management
    │
    └─→ UserKnobs [New preference]
            └─→ enableDnsReresolve: Flow<Boolean>
```

### Data Flow

1. **Startup**: `Application.onCreate()` → `TunnelManager.onCreate()` → `startHandshakeMonitor()`
2. **Periodic Check**:
   ```
   Every 30s:
   → Check if feature enabled (UserKnobs)
   → Get running tunnels
   → For each tunnel:
       → Get statistics
       → For each peer:
           → Calculate: currentTime - latestHandshakeEpochMillis
           → If > 135000ms:
               → Log warning
               → Call setState(UP, config)
               → DNS resolution happens in GoBackend
   ```
3. **Shutdown**: `Application.onTerminate()` → coroutine cancelled

## Implementation Files

### New Files
1. **None** - all changes are to existing files

### Modified Files

1. **`ui/src/main/java/com/wireguard/android/model/TunnelManager.kt`**
   - Add `startHandshakeMonitor()` function
   - Add constants: `HANDSHAKE_CHECK_INTERVAL_MS`, `STALE_HANDSHAKE_THRESHOLD_MS`
   - Call monitor from `onCreate()`

2. **`ui/src/main/java/com/wireguard/android/util/UserKnobs.kt`**
   - Add `enableDnsReresolve` preference with default `true`

3. **`ui/src/main/res/xml/preferences.xml`**
   - Add SwitchPreferenceCompat for "Automatic endpoint re-resolution"

4. **`ui/src/main/res/values/strings.xml`**
   - Add title and summary strings for new preference

## Testing Strategy

### Manual Testing
1. Set up tunnel with dynamic DNS endpoint
2. Enable the feature
3. Monitor logs for "Handshake stale" warnings
4. Verify DNS re-resolution occurs
5. Check that tunnel remains connected after IP change

### Edge Cases
- Tunnel with no handshakes (newly started)
- Tunnel with persistent keepalive disabled
- Multiple tunnels running simultaneously
- Feature disabled in settings
- App in background/foreground
- Network connectivity issues during re-resolution

## Performance Impact

**Expected Impact**: Negligible

- **CPU**: Minimal (one coroutine, runs 30s intervals)
- **Memory**: <1KB (single coroutine + state)
- **Network**: Only DNS queries when handshake stale
- **Battery**: Negligible (much less than 1% drain)

**Measurement**: Use Android Profiler to verify coroutine overhead < 0.1ms per check.

## Security Considerations

- No new permissions required
- Uses existing DNS resolution path (already trusted)
- No exposure of tunnel configuration
- Logs do not contain sensitive information

## Future Enhancements

1. Make check interval configurable per-tunnel
2. Make stale threshold configurable
3. Add statistics counter for re-resolution events
4. Notification when endpoint IP changes
5. Exponential backoff for repeated failures

## References

- Original script: https://github.com/diraneyya/wireguard-tools/blob/patch-1/contrib/reresolve-dns/reresolve-dns.sh
- WireGuard specification: https://www.wireguard.com/papers/wireguard.pdf
- Android Kotlin Coroutines: https://developer.android.com/kotlin/coroutines
