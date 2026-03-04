# Phase 6: Security Foundation & Quick Wins - Research

**Researched:** 2026-03-03
**Domain:** Android application security hardening (screenshot prevention, log stripping, network config, temp file hygiene, exported component audit)
**Confidence:** HIGH

## Summary

Phase 6 covers five independent, well-documented Android security hardening tasks with minimal interdependency. All five requirements (SEC-01, SEC-03, SEC-04, SEC-05, SEC-06) use standard Android platform APIs with no third-party library additions required. The patterns are established, widely documented, and verified against official Android developer documentation.

The project currently has zero security hardening: no FLAG_SECURE, no log stripping rules, no network security config, temp files use `System.currentTimeMillis()` naming (predictable), and the `CropImageActivity` lacks an explicit `android:exported="false"` attribute. All fixes are surgical and low-risk.

**Primary recommendation:** Implement all five requirements as independent, parallel-safe tasks. Each touches different files with no overlap, making this phase ideal for single-wave execution.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SEC-01 | FLAG_SECURE on all screens to prevent screenshots and Recents thumbnails | Set `FLAG_SECURE` in `MainActivity.onCreate()` conditional on `!BuildConfig.DEBUG`; single Activity architecture means one location covers all 8 fragments. Requires enabling `buildFeatures { buildConfig = true }` in build.gradle.kts (AGP 8.x default is false). |
| SEC-03 | Production log calls (Log.v/d/i) stripped via ProGuard rule; Log.w/e retained | Add `-assumenosideeffects` rule to `proguard-rules.pro` for `Log.v`, `Log.d`, `Log.i`. R8 (enabled, AGP 8.13.2) handles stripping including string concatenation arguments when Kotlin 1.9.21+ (project uses 1.9.21). |
| SEC-04 | Network security config XML explicitly disables cleartext traffic | Create `res/xml/network_security_config.xml` with `cleartextTrafficPermitted="false"` and reference it via `android:networkSecurityConfig` in AndroidManifest. Defense-in-depth for offline app. |
| SEC-05 | Temp files use randomized names, cleaned up in finally blocks | Three temp file sites identified: `NativePdfView.kt` (line 120), `PdfEditorFragment.kt` (line 245), `PdfUtils.kt` (line 387). Replace `System.currentTimeMillis()` with `UUID.randomUUID()`. Add `finally` blocks where missing. Tighten startup cleanup to delete ALL matching temp files regardless of age. |
| SEC-06 | All non-launcher components explicitly marked `android:exported="false"` | `CropImageActivity` (line 98-100 in AndroidManifest) is missing `android:exported="false"`. `FileProvider` already has it. `MainActivity` correctly has `android:exported="true"` with launcher intent filter. |
</phase_requirements>

## Standard Stack

### Core

No new libraries required. All implementations use platform APIs:

| API / Tool | Version | Purpose | Why Standard |
|------------|---------|---------|--------------|
| `WindowManager.LayoutParams.FLAG_SECURE` | Platform API (all API levels) | Prevent screenshots and Recents thumbnails | Official Android security flag; used by banking apps, password managers |
| R8 / ProGuard `-assumenosideeffects` | AGP 8.13.2 (R8 bundled) | Strip verbose/debug/info log calls from release APK | Official code shrinking mechanism; R8 handles string concat removal since AGP 7.3+ |
| Network Security Configuration | Platform API 24+ | Block cleartext HTTP traffic | Official Android security config; replaces deprecated `usesCleartextTraffic` manifest attribute |
| `UUID.randomUUID()` | Java stdlib | Cryptographically random temp file names | Uses SecureRandom internally; standard practice for unpredictable file names |
| `android:exported` manifest attribute | Platform (required since API 31) | Control component accessibility from external apps | Mandatory for Play Store; explicit is always safer than relying on defaults |

### Supporting

| Tool | Purpose | When to Use |
|------|---------|-------------|
| `BuildConfig.DEBUG` | Conditional FLAG_SECURE (skip in debug for screenshot tests) | Must enable `buildFeatures { buildConfig = true }` in `build.gradle.kts` since AGP 8.x defaults to false |
| `adb shell dumpsys` | Verify network security config in release builds | Verification step for SEC-04 |
| `logcat --pid` | Verify log stripping in release builds | Verification step for SEC-03 |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `FLAG_SECURE` in `onCreate()` | `registerActivityLifecycleCallbacks` in Application class | Overkill for single-Activity app; adds complexity with no benefit |
| `BuildConfig.DEBUG` | `ApplicationInfo.FLAG_DEBUGGABLE` | More robust for multi-module apps, but this is a single-module app; BuildConfig is simpler |
| ProGuard `-assumenosideeffects` | Timber library with `Tree` removal | Would require migrating all 40+ Log calls to Timber; SEC-03 only asks to strip, not refactor |
| `UUID.randomUUID()` | `SecureRandom` + hex encoding | UUID internally uses SecureRandom; UUID.randomUUID().toString() is more readable |
| `network_security_config.xml` | `android:usesCleartextTraffic="false"` in manifest | Manifest attribute is ignored when network security config is present (API 24+); XML config is the modern standard and more extensible |

## Architecture Patterns

### Pattern 1: FLAG_SECURE in Single Activity (SEC-01)

**What:** Set FLAG_SECURE on the window in `MainActivity.onCreate()`, conditional on release builds.
**When to use:** Single-Activity architecture where all fragments inherit the Activity's window.
**Why it works:** All 8 fragments (Home, Camera, Preview, Pages, PdfViewer, History, Settings, PdfEditor) are hosted in `MainActivity`. Setting the flag once covers every screen.

**Example:**
```kotlin
// In MainActivity.onCreate(), BEFORE setContentView()
// Source: https://developer.android.com/security/fraud-prevention/activities
if (!BuildConfig.DEBUG) {
    window.setFlags(
        WindowManager.LayoutParams.FLAG_SECURE,
        WindowManager.LayoutParams.FLAG_SECURE
    )
}
```

**Critical detail from STATE.md:** "FLAG_SECURE conditional on BuildConfig.DEBUG to preserve screenshot test capability" is an explicit project decision.

**AGP 8.x requirement:** Must add `buildConfig = true` to `buildFeatures` block in `app/build.gradle.kts`:
```kotlin
buildFeatures {
    viewBinding = true
    buildConfig = true  // Required for BuildConfig.DEBUG; AGP 8.x default is false
}
```

### Pattern 2: R8 Log Stripping via ProGuard Rules (SEC-03)

**What:** Use `-assumenosideeffects` to tell R8 that `Log.v()`, `Log.d()`, `Log.i()` have no side effects, allowing R8 to remove the calls entirely in release builds.
**When to use:** Any release build where verbose/debug/info logs should not appear in logcat.

**Example:**
```proguard
# ===== SEC-03: Strip verbose/debug/info log calls from release builds =====
# R8 removes the method calls AND associated string concatenation (AGP 7.3+ / R8 3.3.70+)
# Log.w and Log.e are intentionally retained for crash diagnostics.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
```

**Key facts:**
- R8 (AGP 8.13.2) handles StringBuilder/string concatenation removal automatically since AGP 7.3+
- Kotlin 1.9.21 generates StringBuilder bytecode (not `Intrinsics.stringPlus()`), which R8 optimizes correctly
- This only applies to minified release builds (`isMinifyEnabled = true`, already set in `build.gradle.kts`)
- The 6 files with `import android.util.Log` and 2 files using `android.util.Log.*` directly are all covered
- `Log.w` calls (3 in codebase) and `Log.e` calls (15 in codebase) are intentionally preserved

### Pattern 3: Network Security Configuration XML (SEC-04)

**What:** Create `network_security_config.xml` to explicitly block all cleartext HTTP traffic at the platform level.
**When to use:** Defense-in-depth for any app, even offline apps, to prevent accidental or malicious cleartext connections.

**Example:**
```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- SEC-04: Explicitly block all cleartext (HTTP) traffic -->
    <!-- Defense-in-depth for offline app — prevents any component from making unencrypted connections -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

**AndroidManifest addition:**
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

**Note:** For API 28+ (targetSdk 34), cleartext is already blocked by default. This makes it explicit for API 24-27 (minSdk 24) and serves as defense-in-depth.

### Pattern 4: Randomized Temp File Names with Finally Cleanup (SEC-05)

**What:** Replace predictable `System.currentTimeMillis()` temp file names with `UUID.randomUUID()` and ensure cleanup in `finally` blocks.
**When to use:** Any temp file that could contain sensitive document data.

**Current temp file sites (3 locations):**

| File | Current Pattern | Line |
|------|----------------|------|
| `NativePdfView.kt` | `pdf_view_temp_${System.currentTimeMillis()}.pdf` | 120 |
| `PdfEditorFragment.kt` | `temp_edit_${System.currentTimeMillis()}.pdf` | 245 |
| `PdfUtils.kt` | `pdf_compress_temp/page_$i.jpg` (predictable names in temp dir) | 387-405 |

**Example pattern:**
```kotlin
// BEFORE (predictable):
val tempFile = File(context.cacheDir, "pdf_view_temp_${System.currentTimeMillis()}.pdf")

// AFTER (randomized):
val tempFile = File(context.cacheDir, "pdf_view_${UUID.randomUUID()}.pdf")
```

**Finally block pattern:**
```kotlin
var tempFile: File? = null
try {
    tempFile = File(context.cacheDir, "pdf_view_${UUID.randomUUID()}.pdf")
    // ... use tempFile ...
} finally {
    tempFile?.delete()
}
```

**Startup cleanup hardening:** The existing `cleanupStaleTempFiles()` in `MainActivity.kt` (line 137) only deletes files older than 1 hour. For SEC-05, tighten to delete ALL matching temp files on startup (regardless of age) to ensure no stale files survive app restart.

### Pattern 5: Explicit exported="false" Audit (SEC-06)

**What:** Audit AndroidManifest.xml and add explicit `android:exported="false"` to all non-launcher components.
**When to use:** Every component that should not be accessible from external apps.

**Current manifest state:**
| Component | Type | Current `exported` | Required |
|-----------|------|-------------------|----------|
| `.MainActivity` | Activity (launcher) | `true` (correct) | `true` -- has launcher intent filter |
| `com.canhub.cropper.CropImageActivity` | Activity (library) | **not set** | `false` -- internal crop UI only |
| `androidx.core.content.FileProvider` | Provider | `false` (correct) | `false` -- already set |

**Fix needed:** Add `android:exported="false"` to `CropImageActivity`:
```xml
<activity
    android:name="com.canhub.cropper.CropImageActivity"
    android:exported="false"
    android:theme="@style/Theme.PDFScanner.Crop" />
```

**Note on LeakCanary:** LeakCanary 2.14 (`debugImplementation`) auto-registers its `ContentProvider` via manifest merger, but only in debug builds. It does not appear in the release manifest, so no action needed.

### Anti-Patterns to Avoid

- **Setting FLAG_SECURE in onResume():** The Recents screenshot is captured BEFORE `onPause()` is called. FLAG_SECURE must be set in `onCreate()` before `setContentView()`.
- **Using `-assumenosideeffects` with wildcard (`*`) method matching:** This can strip methods from `Object` superclass, causing crashes. Always use explicit method signatures (`v(...)`, `d(...)`, `i(...)`).
- **Relying on `-assumenosideeffects` in debug builds:** This rule only takes effect when `isMinifyEnabled = true` (release builds). Debug builds will still show all logs (correct behavior).
- **Using `System.currentTimeMillis()` for temp file names:** Predictable -- an attacker on the same device can guess the filename. Use `UUID.randomUUID()`.
- **Deleting temp files only in `try` block:** If an exception occurs before the delete call, the file persists. Always use `finally`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Screenshot prevention | Custom `onWindowFocusChanged()` overlay hack | `FLAG_SECURE` | Platform-level enforcement; cannot be bypassed by screencap tools |
| Log stripping | Custom `if (DEBUG)` wrappers around every Log call | R8 `-assumenosideeffects` | Zero runtime overhead; compiler removes calls entirely |
| Cleartext blocking | Custom OkHttp interceptor | `network_security_config.xml` | Platform-enforced at socket level; covers all HTTP clients including WebView |
| Random temp names | `Random().nextInt()` or custom hash | `UUID.randomUUID()` | Uses `SecureRandom` internally; 128-bit randomness; standard API |

**Key insight:** All five requirements are solved by platform APIs that require zero runtime code beyond a few lines in `onCreate()`. The bulk of the work is configuration (ProGuard rules, XML files, manifest attributes), not application logic.

## Common Pitfalls

### Pitfall 1: FLAG_SECURE Timing

**What goes wrong:** Screenshots/Recents thumbnails still visible despite FLAG_SECURE code.
**Why it happens:** FLAG_SECURE set after `setContentView()` or in `onResume()` -- the system captures the Recents screenshot before lifecycle callbacks fire.
**How to avoid:** Set FLAG_SECURE in `onCreate()` BEFORE `super.onCreate()` or at minimum before `setContentView()`. In this project, set it after `applyAppStyle()` but before `super.onCreate()`.
**Warning signs:** Recents shows app content in thumbnail during testing.

### Pitfall 2: R8 assumenosideeffects Not Working

**What goes wrong:** Log.d/v/i calls still appear in logcat from release APK.
**Why it happens:** `isMinifyEnabled` is `false`, or the rule uses incorrect method signatures, or testing with a debug build.
**How to avoid:** Verify `isMinifyEnabled = true` (already set). Use the `(...)` wildcard for parameters (matches all overloads). Test with `./gradlew assembleRelease` and filter logcat by app PID.
**Warning signs:** Any Log.v/d/i output when running `adb logcat --pid=$(adb shell pidof com.pdfscanner.app) | grep -E "^(V|D|I)/"`.

### Pitfall 3: BuildConfig.DEBUG Not Generated (AGP 8.x)

**What goes wrong:** Compilation error -- `Unresolved reference: BuildConfig`.
**Why it happens:** AGP 8.0+ defaults `buildFeatures.buildConfig` to `false`. The project does not currently enable it.
**How to avoid:** Add `buildConfig = true` to the `buildFeatures` block in `app/build.gradle.kts`.
**Warning signs:** Build failure after adding `BuildConfig.DEBUG` reference in `MainActivity.kt`.

### Pitfall 4: Network Security Config Ignored on API 24-27

**What goes wrong:** Cleartext traffic allowed despite config file existing.
**Why it happens:** The `android:networkSecurityConfig` attribute is not added to the `<application>` tag in AndroidManifest.xml.
**How to avoid:** Always add both the XML file AND the manifest reference. Verify with `adb shell dumpsys connectivity | grep -i cleartext` or by attempting an HTTP request.
**Warning signs:** No error/exception when making HTTP request from the app on API 24-27 device.

### Pitfall 5: Temp File Cleanup Race Condition

**What goes wrong:** Temp file deleted while still in use by PdfRenderer.
**Why it happens:** `finally` block runs while PdfRenderer still holds a file descriptor open.
**How to avoid:** Close PdfRenderer/FileDescriptor BEFORE deleting temp file. The `finally` block should close resources first, then delete. NativePdfView already handles this in `closeCurrentPage()` (line 375-388) -- the pattern is: close page -> close renderer -> close fd -> delete temp file.
**Warning signs:** `IOException: No such file or directory` in logcat when navigating between PDF pages.

## Code Examples

### SEC-01: FLAG_SECURE in MainActivity

```kotlin
// Source: https://developer.android.com/security/fraud-prevention/activities
// In MainActivity.onCreate(), after applyAppStyle() and before super.onCreate()

import android.view.WindowManager

override fun onCreate(savedInstanceState: Bundle?) {
    val prefs = AppPreferences(this)
    applyAppStyle(prefs)
    AppCompatDelegate.setDefaultNightMode(prefs.getThemeMode())

    // SEC-01: Prevent screenshots and Recents thumbnails in release builds
    // Conditional on BuildConfig.DEBUG to preserve screenshot test capability
    if (!BuildConfig.DEBUG) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    super.onCreate(savedInstanceState)
    // ... rest of onCreate
}
```

### SEC-03: ProGuard Log Stripping Rule

```proguard
# ===== SEC-03: Strip verbose/debug/info log calls from release builds =====
# R8 removes the method calls AND associated string concatenation (AGP 7.3+).
# Log.w and Log.e are intentionally retained for crash diagnostics.
# Only effective when isMinifyEnabled = true (release builds).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
```

### SEC-04: Network Security Config

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- SEC-04: Explicitly block all cleartext (HTTP) traffic -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

### SEC-05: Randomized Temp File with Finally Cleanup

```kotlin
// Source: standard Java/Kotlin pattern
import java.util.UUID

// Randomized name (replaces System.currentTimeMillis())
val tempFile = File(context.cacheDir, "pdf_view_${UUID.randomUUID()}.pdf")

// Cleanup in finally block
var tempFile: File? = null
try {
    tempFile = File(context.cacheDir, "temp_edit_${UUID.randomUUID()}.pdf")
    tempFile.outputStream().use { outputStream ->
        inputStream.copyTo(outputStream)
    }
    // ... use tempFile ...
} catch (e: Exception) {
    // handle error
} finally {
    tempFile?.delete()
}
```

### SEC-05: Hardened Startup Cleanup

```kotlin
// In MainActivity.cleanupStaleTempFiles()
// Change: Remove age check -- delete ALL matching temp files on startup
private fun cleanupStaleTempFiles() {
    try {
        val cacheDir = cacheDir
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val name = file.name
                if (name.startsWith("pdf_view_") ||
                    name.startsWith("temp_edit_") ||
                    name.startsWith("pdf_compress_temp")) {
                    file.delete()
                }
            }
        }
        // Also clean pdf_compress_temp directory completely
        val compressTemp = File(cacheDir, "pdf_compress_temp")
        if (compressTemp.exists() && compressTemp.isDirectory) {
            compressTemp.listFiles()?.forEach { it.delete() }
            compressTemp.delete()
        }
    } catch (e: Exception) {
        android.util.Log.w("MainActivity", "Temp cleanup failed", e)
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `android:usesCleartextTraffic="false"` in manifest | `network_security_config.xml` | API 24 (2016) | XML config is more flexible; manifest attr ignored when config exists |
| ProGuard (manual shrinking) | R8 (automatic, bundled with AGP) | AGP 3.4.0 (2019) | R8 handles string concat removal; same ProGuard rule syntax |
| `BuildConfig` auto-generated | `buildFeatures { buildConfig = true }` opt-in | AGP 8.0 (2023) | Must explicitly enable for BuildConfig.DEBUG access |
| `FLAG_SECURE` only option | `setRecentsScreenshotEnabled(false)` (API 33+) | API 33 (2022) | Separate Recents control; FLAG_SECURE still needed for general screenshot prevention |

**Deprecated/outdated:**
- `android:usesCleartextTraffic` manifest attribute: Superseded by `network_security_config.xml` on API 24+. Still works but is less flexible.
- Implicit `android:exported` defaults: Since API 31, components with intent filters MUST declare `exported` explicitly. Components without intent filters default to `false` but explicit declaration is best practice.

## Open Questions

1. **PdfUtils.kt temp directory cleanup in `compressPdf()`**
   - What we know: The method creates `pdf_compress_temp/page_$i.jpg` files, deletes them after each page, and deletes the directory at the end. But if an exception occurs mid-loop, intermediate page files may survive.
   - What's unclear: Whether the current `page_$i.jpg` naming within the temp directory is a concern (directory itself has predictable name, but is in app-private `cacheDir`).
   - Recommendation: Wrap the entire operation in a `finally` block that deletes the temp directory and all contents. Replace `page_$i.jpg` with `${UUID.randomUUID()}.jpg` for consistency, but this is LOW priority since the directory is app-private.

2. **LeakCanary ContentProvider in debug manifest**
   - What we know: LeakCanary 2.14 (`debugImplementation`) registers via `ContentProvider` auto-init. This only appears in debug builds (manifest merger scoping).
   - What's unclear: Nothing -- this is confirmed out of scope for SEC-06.
   - Recommendation: No action needed. Document in PLAN.md that debug-only components are excluded from the audit.

## Sources

### Primary (HIGH confidence)
- [Android FLAG_SECURE documentation](https://developer.android.com/security/fraud-prevention/activities) - FLAG_SECURE implementation pattern, timing requirements
- [Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config) - network_security_config.xml format, manifest reference
- [Android android:exported risks](https://developer.android.com/privacy-and-security/risks/android-exported) - Exported component security guidance
- [Android Log Info Disclosure](https://developer.android.com/privacy-and-security/risks/log-info-disclosure) - Official log stripping guidance
- [Android Cleartext Communications](https://developer.android.com/privacy-and-security/risks/cleartext-communications) - Cleartext traffic prevention

### Secondary (MEDIUM confidence)
- [ProguardStripsLog GitHub research](https://github.com/allets/ProguardStripsLog) - R8 log stripping behavior verification with string concatenation
- [R8LoggerRemoval comparison](https://github.com/Thorbear/R8LoggerRemoval) - R8 vs ProGuard log removal behavior
- [Jake Wharton: R8 Optimization - Value Assumption](https://jakewharton.com/r8-optimization-value-assumption/) - R8 optimization details for assumenosideeffects

### Tertiary (LOW confidence)
- None -- all findings verified against primary or secondary sources.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All platform APIs, no third-party dependencies, extensively documented
- Architecture: HIGH - Single-Activity architecture confirmed; all patterns verified against codebase inspection
- Pitfalls: HIGH - All pitfalls documented with codebase-specific line numbers and verified against official docs

**Research date:** 2026-03-03
**Valid until:** 2026-04-03 (stable platform APIs; no version-sensitivity)
