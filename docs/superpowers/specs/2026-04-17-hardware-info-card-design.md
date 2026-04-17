# Hardware Info Card in VehicleDataTab

**Date:** 2026-04-17
**Status:** Approved

## Goal

Display Android hardware information (SoC, CPU, decoder, etc.) in the settings panel so the user can identify whether the head unit runs Intel or Qualcomm without root access. This helps diagnose performance issues with the CarPlay projection pipeline.

## Scope

Three files modified, no new files created:

1. **PlatformDetector.kt** — add SoC fields + observable StateFlow
2. **VehicleDataTab.kt** — add Hardware Info card
3. **CarlinkManager.kt** — no changes needed (already calls `detect()`)

## Design

### PlatformDetector.kt

**New fields in `PlatformInfo`:**

| Field | Type | Source | Notes |
|-------|------|--------|-------|
| `socManufacturer` | `String` | `Build.SOC_MANUFACTURER` | API 31+, available since minSdk 32 |
| `socModel` | `String` | `Build.SOC_MODEL` | API 31+ |
| `androidVersion` | `String` | `Build.VERSION.RELEASE` | e.g. "12", "13" |
| `cpuCoreCount` | `Int` | `Runtime.getRuntime().availableProcessors()` | Already computed locally in AppExecutors |

**New observable state:**

```kotlin
private val _platformInfo = MutableStateFlow<PlatformInfo?>(null)
val platformInfo: StateFlow<PlatformInfo?> = _platformInfo.asStateFlow()
```

The existing `detect()` method stores its result in `_platformInfo.value` before returning. No behavioral change — callers still get the return value as before, but now the UI can also observe it.

### VehicleDataTab.kt

**New "Hardware Info" card** placed above the existing "Vehicle Properties" card. Always visible (not gated by vehicle connection status) since hardware info is available immediately.

Uses the existing `VehicleCard` composable with `Icons.Default.Info` icon.

**Rows displayed:**

| Label | Value | Example |
|-------|-------|---------|
| SoC | `"{socManufacturer} {socModel}"` | "Qualcomm SM8350" or "Intel Atom x7-A3960" |
| CPU | `"{cpuArch}, {cpuCoreCount} cores"` | "x86_64, 4 cores" |
| Android | `androidVersion` | "12" |
| Display | `"{displayWidth} x {displayHeight}"` | "2400 x 960" |
| Video Decoder | `hardwareH264DecoderName ?: "Software"` | "OMX.Intel.VideoDecoder.AVC" |
| Audio Sample Rate | `"{nativeSampleRate} Hz"` | "48000 Hz" |

Rows are simple label/value text pairs without status icons (these are always-available system properties, not car sensor data with availability states).

**Observation:** Uses `PlatformDetector.platformInfo.collectAsStateWithLifecycle()` — same pattern as `VehicleDataManager.state`.

### Files NOT changed

- **CarlinkManager.kt** — already calls `PlatformDetector.detect(context)`, which will now also publish to the StateFlow as a side effect. No code change needed.
- **AppExecutors.java** — no changes.
- **VehicleDataManager.kt** — no changes.

## Non-goals

- No performance tuning based on detected hardware (future work)
- No deep diagnostics (Build.BOARD, Build.DEVICE, etc.) — available in logs
- No new settings tab
