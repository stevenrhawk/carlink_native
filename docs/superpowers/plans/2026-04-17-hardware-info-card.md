# Hardware Info Card Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Display SoC, CPU, decoder, and display info in the VehicleDataTab settings panel so users can identify their head unit hardware without root.

**Architecture:** Add 4 new fields to `PlatformInfo` data class + a `StateFlow` in `PlatformDetector` to expose detection results to the UI. Add a Hardware Info card to `VehicleDataTab` that observes the flow. Two files modified, zero new files.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.coroutines StateFlow, android.os.Build API 31+

**Spec:** `docs/superpowers/specs/2026-04-17-hardware-info-card-design.md`

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `app/src/main/kotlin/com/carlink/platform/PlatformDetector.kt` | Modify | Add `socManufacturer`, `socModel`, `androidVersion`, `cpuCoreCount` to `PlatformInfo`. Add `StateFlow<PlatformInfo?>` and publish from `detect()`. |
| `app/src/main/kotlin/com/carlink/ui/settings/VehicleDataTab.kt` | Modify | Add Hardware Info card above Vehicle Properties card. Observe `PlatformDetector.platformInfo`. |

---

### Task 1: Add new fields and StateFlow to PlatformDetector

**Files:**
- Modify: `app/src/main/kotlin/com/carlink/platform/PlatformDetector.kt`

- [ ] **Step 1: Add StateFlow imports**

Add these imports at the top of `PlatformDetector.kt`, after the existing imports:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
```

- [ ] **Step 2: Add StateFlow to PlatformDetector object**

Inside the `PlatformDetector` object, after the `TAG` constant, add:

```kotlin
private val _platformInfo = MutableStateFlow<PlatformInfo?>(null)
val platformInfo: StateFlow<PlatformInfo?> = _platformInfo.asStateFlow()
```

- [ ] **Step 3: Add new fields to PlatformInfo data class**

Add four new parameters to the `PlatformInfo` data class, after the existing `displayHeight` parameter:

```kotlin
val socManufacturer: String = "",
val socModel: String = "",
val androidVersion: String = "",
val cpuCoreCount: Int = 0,
```

Update the `toString()` method to include the new fields:

```kotlin
override fun toString(): String =
    "PlatformInfo(arch=$cpuArch, intel=$isIntel, gm=$isGmAaos, " +
        "soc=$socManufacturer $socModel, cores=$cpuCoreCount, android=$androidVersion, " +
        "hwDecoder=${hardwareH264DecoderName ?: "software"}, " +
        "nativeRate=${nativeSampleRate}Hz, mfr=$manufacturer, product=$product, device=$device)"
```

- [ ] **Step 4: Populate new fields in detect() and publish to StateFlow**

In the `detect()` method, after the existing `val hardware = Build.HARDWARE ?: ""` line, add:

```kotlin
val socManufacturer = Build.SOC_MANUFACTURER ?: ""
val socModel = Build.SOC_MODEL ?: ""
val androidVersion = Build.VERSION.RELEASE ?: ""
val cpuCoreCount = Runtime.getRuntime().availableProcessors()
```

In the `PlatformInfo(...)` constructor call inside `detect()`, add the new fields after `displayHeight = displayHeight`:

```kotlin
socManufacturer = socManufacturer,
socModel = socModel,
androidVersion = androidVersion,
cpuCoreCount = cpuCoreCount,
```

Right before the `return info` statement, add:

```kotlin
_platformInfo.value = info
```

- [ ] **Step 5: Verify the project compiles**

Run: `cd d:/android-dev/carplay-exploartion/carlink_native && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/carlink/platform/PlatformDetector.kt
git commit -m "feat: add SoC, Android version, core count to PlatformInfo + StateFlow"
```

---

### Task 2: Add Hardware Info card to VehicleDataTab

**Files:**
- Modify: `app/src/main/kotlin/com/carlink/ui/settings/VehicleDataTab.kt`

- [ ] **Step 1: Add import for PlatformDetector**

Add this import to `VehicleDataTab.kt`, after the existing `com.carlink.platform` imports:

```kotlin
import com.carlink.platform.PlatformDetector
```

- [ ] **Step 2: Observe PlatformInfo in VehicleDataTab composable**

Inside the `VehicleDataTab()` composable, after the existing `val vehicleData by VehicleDataManager.state.collectAsStateWithLifecycle()` line, add:

```kotlin
val platformInfo by PlatformDetector.platformInfo.collectAsStateWithLifecycle()
```

- [ ] **Step 3: Add HardwareInfoCard composable**

Add this composable function after the existing `VehicleCard` composable (at the bottom of the file):

```kotlin
@Composable
private fun HardwareInfoCard(info: PlatformDetector.PlatformInfo) {
    val colorScheme = MaterialTheme.colorScheme

    VehicleCard(
        title = "Hardware Info",
        icon = Icons.Default.Info,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HardwareRow("SoC", "${info.socManufacturer} ${info.socModel}".trim())
            HardwareRow("CPU", "${info.cpuArch}, ${info.cpuCoreCount} cores")
            HardwareRow("Android", info.androidVersion)
            HardwareRow(
                "Display",
                if (info.displayWidth > 0) "${info.displayWidth} x ${info.displayHeight}" else "Unknown",
            )
            HardwareRow("Video Decoder", info.hardwareH264DecoderName ?: "Software")
            HardwareRow("Audio Sample Rate", "${info.nativeSampleRate} Hz")
        }
    }
}

@Composable
private fun HardwareRow(label: String, value: String) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
```

- [ ] **Step 4: Place the HardwareInfoCard at the top of the column**

Inside the `VehicleDataTab()` composable, at the very beginning of the `Column` content (before the `when (vehicleData.status)` block), add:

```kotlin
platformInfo?.let { info ->
    HardwareInfoCard(info)
}
```

- [ ] **Step 5: Verify the project compiles**

Run: `cd d:/android-dev/carplay-exploartion/carlink_native && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/carlink/ui/settings/VehicleDataTab.kt
git commit -m "feat: add Hardware Info card to VehicleDataTab settings panel"
```
