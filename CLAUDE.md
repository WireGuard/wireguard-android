# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WireGuard for Android - An Android VPN client implementation that opportunistically uses kernel WireGuard when available, falling back to userspace Go implementation (wireguard-go) otherwise.

**Minimum SDK:** 24 (Android 7.0)
**Target SDK:** 36
**Language:** Mixed Kotlin (UI) and Java (tunnel library)

## Building

Build the release APK:
```bash
./gradlew assembleRelease
```

Build debug variant:
```bash
./gradlew assembleDebug
```

Build specific module:
```bash
./gradlew :ui:assemble
./gradlew :tunnel:assemble
```

Clean build:
```bash
./gradlew clean
```

**Note:** macOS users may need [flock(1)](https://github.com/discoteq/flock) installed.

## Project Structure

This is a multi-module Gradle project with two main modules:

### `:tunnel` Module
The embeddable tunnel library (published to Maven Central as `com.wireguard.android:tunnel`). Contains:
- **Backend implementations** (`com.wireguard.android.backend`):
  - `Backend` - Interface for tunnel implementations
  - `GoBackend` - Userspace implementation using wireguard-go (primary backend)
  - `WgQuickBackend` - Kernel implementation using wg-quick (requires root)
- **Config parsing** (`com.wireguard.config`): Parses WireGuard configuration files (Interface, Peer, etc.)
- **Crypto utilities** (`com.wireguard.crypto`): Key generation and handling using Curve25519
- **Native code**: CMake build for wireguard-go shared libraries (libwg-go.so, libwg.so, libwg-quick.so)

Language: Pure Java (for library compatibility)
Min SDK: 21 (library is more permissive than app)

### `:ui` Module
The Android application UI layer. Contains:
- **Application** (`Application.kt`): Main entry point, determines backend (kernel vs userspace), initializes services
- **TunnelManager** (`model/TunnelManager.kt`): Central coordinator for tunnel lifecycle
  - Creates/deletes/renames tunnels
  - Manages tunnel state (UP/DOWN/TOGGLE)
  - Handles state restoration on boot
  - Coordinates with Backend implementations
  - Uses coroutines heavily (Dispatchers.IO for backend ops, Dispatchers.Main for UI updates)
- **ConfigStore** (`configStore/`): Persists tunnel configurations to filesystem
- **Activities & Fragments** (`activity/`, `fragment/`): Main UI, tunnel list, editor, detail views
- **Data binding**: Uses Android Data Binding extensively with custom observable collections
- **ViewModels** (`viewmodel/`): ConfigProxy, InterfaceProxy, PeerProxy for data binding

Language: Kotlin
Package: `com.wireguard.android`

## Architecture

### Backend Selection Flow
1. On app startup, `Application.onCreate()` calls `determineBackend()`
2. If kernel module enabled (via `UserKnobs.enableKernelModule`) AND kernel support detected:
   - Attempts to start `RootShell` and use `WgQuickBackend`
   - Falls back to `GoBackend` if root unavailable
3. Otherwise uses `GoBackend` (userspace implementation)
4. Backend is set once per app lifecycle

### Tunnel State Management
- All tunnel operations go through `TunnelManager`
- State changes are coordinated: `TunnelManager.setTunnelState()` → `Backend.setState()` → `Tunnel.onStateChange()`
- Statistics retrieved via `Backend.getStatistics()` which parses WireGuard protocol output
- Heavy use of Kotlin coroutines with context switching between IO and Main dispatchers
- State persisted via `UserKnobs.setRunningTunnels()` for restoration on boot

### Always-On VPN
- `GoBackend.VpnService` handles Android VPN service lifecycle
- `GoBackend.setAlwaysOnCallback()` registers callback for always-on VPN triggers
- Application responds to system-initiated VPN starts in `VpnService.onStartCommand()`

## Testing

Run unit tests:
```bash
./gradlew test
```

Run tests for specific module:
```bash
./gradlew :tunnel:test
```

**Note:** Test output events are configured to show PASSED, SKIPPED, and FAILED in tunnel module (see `tunnel/build.gradle.kts:29-31`).

## Linting

Run lint checks:
```bash
./gradlew lint
```

Lint is configured with:
- Disabled: `LongLogTag`, `NewApi` (tunnel), `LongLogTag` (ui)
- Warnings: `MissingTranslation`, `ImpliedQuantity` (ui)

## Publishing (tunnel library only)

The `:tunnel` module can be published to Maven:
```bash
./gradlew :tunnel:publishReleasePublicationToSonatypeUploadRepository
```

Create distribution zip:
```bash
./gradlew :tunnel:zipReleasePublication
```

Signing uses GPG command-line tool (configured in `tunnel/build.gradle.kts:140-143`).

## Native Code

Native libraries are built via CMake (configured in `tunnel/tools/CMakeLists.txt`):
- Targets: `libwg-go.so`, `libwg.so`, `libwg-quick.so`
- Build automatically triggered during Gradle build
- Uses `ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`
- Package name varies by build type (debug adds `.debug` suffix)

## Key Conventions

### Coroutines
- Use `applicationScope` from `Application.getCoroutineScope()` for app-lifetime operations
- Backend operations: `withContext(Dispatchers.IO)`
- UI updates: `withContext(Dispatchers.Main.immediate)`
- TunnelManager operations are suspend functions coordinating context switches

### Error Handling
- `Backend` methods throw `BackendException` with specific `Reason` enum values
- `ErrorMessages` utility converts exceptions to user-friendly strings
- `Config` parsing throws `BadConfigException` or `ParseException`

### Configuration
- User preferences stored via `DataStore<Preferences>` in `UserKnobs` and `AdminKnobs`
- Tunnel configs stored as files via `FileConfigStore`
- Config format: Standard WireGuard INI format parsed by `com.wireguard.config.Config`

## Dependencies

Key libraries:
- AndroidX: Core, AppCompat, Fragment, Lifecycle, Preference, DataStore
- Kotlin Coroutines (`kotlinx-coroutines-android`)
- Material Design Components
- ZXing (QR code scanning for config import)
- JUnit (testing)

## Build Variants

- **debug**: `com.wireguard.android.debug`, enables StrictMode
- **release**: Minified, shrunk resources, ProGuard enabled
- **googleplay**: Inherits from release (for Play Store builds)

## ProGuard

Release builds use ProGuard with `proguard-android-optimize.txt`. Excludes:
- `DebugProbesKt.bin`
- `kotlin-tooling-metadata.json`
- `META-INF/*.version`
