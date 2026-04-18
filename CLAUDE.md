# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Carlink Native is a Kotlin Android app that bridges iPhone CarPlay and Android Auto projection to Android Automotive OS (AAOS) via a Carlinkit CPC200-CCPA USB adapter. This is a native rewrite of the original Flutter-based Carlink app. The upstream project's sole target hardware is a 2024 Silverado gminfo3.7 Intel AAOS radio. We are modifying it for our target: a 2024 Chevrolet Blazer EV.

**Application ID:** `com.trimline.carplay`

## Build Commands

```bash
./gradlew build                    # Build debug APK
./gradlew assembleRelease          # Build signed release APK (requires external keystore)
./gradlew bundleRelease            # Build signed release AAB
./gradlew clean                    # Clean build artifacts
./gradlew lint                     # Run Android lint
./gradlew publishReleaseBundle     # Upload AAB to Play Store internal track
./release.sh                       # Full release pipeline: bump version, build AAB, upload
./release.sh --dry-run             # Build only, skip Play Store upload
./release.sh 1.2.0                 # Release with new versionName
```

## Build Requirements

- **JDK 17** (Gradle resolves via toolchains or JAVA_HOME)
- **Android SDK** with compileSdk 36, minSdk 32
- **Keystore** at `D:/android-dev/android-keystore` (release builds only)
- **Keystore password** in `D:/android-dev/carplay-exploartion/.env` as `KEYSTORE_PASSWORD="..."`
- **Play Store service account** JSON at `D:/android-dev/carplay-exploartion/trimline-fire-*.json`

## Architecture

### Single-Activity Compose App

`MainActivity` is the only Activity (singleTop). The UI uses an **overlay pattern**: `MainScreen` (video projection) is always rendered, and `SettingsScreen` slides on top as an overlay. This is intentional — `VideoSurface` uses `SurfaceTexture` tied to a `MediaCodec` decoder, so disposing it would break the video pipeline. When the settings overlay closes, `recoverVideoFromOverlay()` flushes the codec since SurfaceFlinger may have stopped consuming frames.

### Central Orchestrator

`CarlinkManager` coordinates all subsystems: USB communication, protocol handling, video decoding, audio playback, media session, navigation state, and GNSS forwarding. It is exposed to Compose via `mutableStateOf<CarlinkManager?>()`.

### Key Subsystems

- **USB Protocol** (`protocol/`): `AdapterDriver` handles CPC200-CCPA communication with smart initialization modes (FULL / MINIMAL_PLUS_CHANGES / MINIMAL_ONLY), heartbeat keepalive, and message serialization/parsing.
- **Video Pipeline** (`ui/components/`): H.264 decoding via hardware `MediaCodec` → `SurfaceView` (HWC overlay). Features keyframe gating (no P-frames before first IDR), stall detection (200ms timeout → reset + keyframe request), and ring buffer for USB jitter.
- **Audio Pipeline** (`audio/`): `DualStreamAudioManager` runs separate `AudioTrack` instances per stream type (Media, Navigation, Voice/Siri, Phone) with per-stream ring buffers. `AudioBusRouter` handles AAOS audio bus routing.
- **AAOS Integration** (`cluster/`, `media/`, `service/voice/`): `CarlinkClusterService` provides vehicle data via `CarHardwareManager` and cluster navigation via Car App Library Templates Host. `CarlinkMediaBrowserService` integrates with AAOS media UI. Voice interaction services handle Siri passthrough.
- **Navigation** (`navigation/`): `NavigationStateManager` with `ManeuverMapper`, `ManeuverIconRenderer`, `TripBuilder`, and `DistanceFormatter` for cluster nav display.
- **Settings** (`ui/settings/`): DataStore-backed preferences with sync cache for critical paths (display mode, resolution). Display mode changes trigger full reinit: stop adapter → apply system bars → recalculate resolution → new CarlinkManager.

### Display Mode System

4 modes control system bar visibility: SYSTEM_UI_VISIBLE, STATUS_BAR_HIDDEN, NAV_BAR_HIDDEN, FULLSCREEN_IMMERSIVE. Changes affect viewport dimensions and SafeArea calculations for the adapter. Video resolution auto-resets to AUTO when display mode changes.

### Design Philosophy

From README: "Projection streams are live UI state, not video playback." Video is best-effort and disposable (drop late frames, reset on corruption, never wait). Audio is a continuous time signal (buffer aggressively, never stall, never block video). Correctness is defined by latency, not completeness.

### Vehicle Data via CarHardwareManager (AAOS)

`VehicleDataManager` subscribes to `androidx.car.app.hardware.CarInfo` via the cluster `CarAppService`. The permissions it requires (`android.car.permission.CAR_INFO`, `CAR_ENERGY`, `CAR_ENERGY_PORTS`, `CAR_SPEED`, `READ_CAR_DISPLAY_UNITS`) are declared `signature|privileged` in AOSP's `packages/services/Car`. **On a retail GM AAOS head unit this data is unreachable** — an unprivileged APK is silently denied with no user-visible prompt. The manifest declarations exist for parity with Home Assistant's known-working layout and to unblock the data if ever sideloaded into `/system/priv-app` with a matching allowlist. `VehicleDataManager.startCollecting` checks `checkSelfPermission` up front and surfaces an explicit error instead of leaving the UI stuck on "Waiting...". `CAR_MILEAGE` is intentionally omitted per Play Store policy.

### Qualcomm SA8155P Optimizations

Target Blazer EV head unit is Qualcomm SA8155P (arm64, `c2.qti.avc.decoder`). `H264Renderer.initCodec` probes for `c2.qti.*` by codec name and enables `KEY_LOW_LATENCY`, `KEY_PRIORITY=0`, and `KEY_OPERATING_RATE=120` only on that path — the conservative bare-format config used for Intel `gminfo3.7` is preserved as the fallback to avoid reintroducing the P-frame corruption documented in `revisions.txt`. `abiFilters = ["arm64-v8a"]` trims the APK since no other ABI is ever used. CPU-affinity pinning of USB-read / codec / audio threads is a deferred optimization — it requires adding NDK/JNI to the build pipeline and is not worth the complexity until profiling shows the scheduler is the bottleneck.

## Lint Suppressions

These are intentional and documented in `app/build.gradle.kts`:
- `DiscouragedApi`: `scheduleAtFixedRate` is used deliberately — coroutines and `scheduleWithFixedDelay` caused microphone timing issues (see `documents/revisions.txt` [19], [21])
- `Instantiatable`: False positive from Car App Library's `CarAppActivity` in the `app-automotive` AAR
- `InvalidUsesTagAttribute`: `"navigation"` is a valid `uses` tag for Car App Library nav apps

## Testing

No automated test suite exists. Test dependencies (JUnit, Espresso, Compose UI Test) are declared but unused. Testing is manual on physical hardware (2024 Silverado gminfo3.7) or Android Studio emulator with USB passthrough. iPhone CarPlay is the primary test path; Android Auto on Pixel is secondary.

## Version Management

`versionCode` is in `app/build.gradle.kts` (currently 111). The `release.sh` script auto-increments it. Detailed version history is in `documents/revisions.txt` with 100+ entries documenting the optimization journey.

## Documentation

Extensive protocol and hardware documentation lives in `documents/reference/` covering firmware architecture, USB protocol, audio/video protocols, command IDs, wireless CarPlay, emulator USB passthrough, and cluster media display.
