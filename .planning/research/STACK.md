# Technology Stack: Security Hardening Additions

**Project:** PDF Scanner -- Security Hardening (v1.2)
**Domain:** Android security hardening for high-sensitivity document handling
**Researched:** 2026-03-03
**Confidence:** HIGH (versions verified via official docs and Maven Central, March 2026)

## Scope

This file covers ONLY the new v1.2 security hardening additions: encryption at rest, screenshot protection, secure logging, dependency vulnerability scanning, network security, clipboard hardening, and tapjacking protection.

The existing validated stack (Kotlin 1.9.21, CameraX 1.3.1, Coil 2.7.0, ML Kit, Navigation 2.7.x, Material 3, coroutines 1.7.3, MVVM/LiveData, MockK 1.14.7, Robolectric 4.16, Espresso 3.7.0, JaCoCo, Detekt 1.23.8, LeakCanary 2.14) is out of scope -- do not re-research those.

## Current Security Posture

What the app already has (from v1.0/v1.1):

- App-private storage in `filesDir` (scans/, processed/, pdfs/) -- no storage permissions needed
- FileProvider with tightened `cache-path` (path="." not path="/")
- `dataExtractionRules` and `fullBackupContent` excluding document directories
- R8 minification + resource shrinking enabled for release builds
- ProGuard/R8 keep rules for ML Kit, GMS, SafeArgs
- `android:debuggable="false"` in release (AGP default)

What the app is missing (v1.2 targets):

- Zero encryption at rest -- documents stored as plain files
- 49 raw `android.util.Log` calls that emit in release builds
- No screenshot/screen recording protection
- No dependency vulnerability scanning
- No network security config (cleartext not explicitly blocked on API 24-27)
- No tapjacking protection
- No clipboard security for OCR text
- `android:allowBackup="true"` still enabled
- No Application subclass (needed for Timber, runtime checks)

---

## Recommended Stack Additions

### Encryption at Rest -- File-Level

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Google Tink (tink-android) | 1.20.0 | AES-256-GCM streaming encryption for scanned documents in scans/, processed/, pdfs/ | Google's recommended cryptographic library for Android. Fully supported from API 24 (matches minSdk). Shades protobuf-lite internally -- zero dependency conflicts with ML Kit or GMS. Provides `StreamingAead` for encrypting large files via InputStream/OutputStream without loading entire content into memory. No ProGuard rules needed (official docs confirm this). Pure Java library -- no Kotlin version constraints with 1.9.21. Released December 10, 2024. |

**Why Tink and not raw AndroidKeyStore:**
- Raw AndroidKeyStore requires ~80-120 lines of boilerplate per encrypt/decrypt operation (KeyGenerator, Cipher, IV management, GCM tag handling)
- Tink wraps AndroidKeyStore with a misuse-resistant API: `StreamingAead` for large files, `Aead` for small data
- Tink handles IV generation, key rotation, and format versioning -- eliminating the most common cryptographic footguns (IV reuse, wrong padding, missing authentication)
- No built-in streaming support in raw KeyStore for large files (scanned documents can be 10+ MB)

**Why NOT EncryptedFile from security-crypto:**
- `EncryptedFile` wraps Tink internally anyway
- `EncryptedFile` reads the entire file into memory before decrypting -- unacceptable for large multi-page scans
- Tink's `StreamingAead` operates on streams, keeping memory usage constant regardless of file size
- security-crypto is deprecated (all APIs deprecated April 2025, stable 1.1.0 released July 2025 as final version)

### Encryption at Rest -- Key-Value Storage

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| androidx.security:security-crypto | 1.1.0 | EncryptedSharedPreferences for document history JSON and app settings | Final stable release (July 30, 2025). All APIs formally deprecated in favor of raw Keystore, but 1.1.0 is fully functional and will remain so -- the library is feature-complete. For simple key-value encryption, EncryptedSharedPreferences is the lowest-friction option: drop-in replacement for SharedPreferences with identical API surface. Wraps Tink internally. |

**Why EncryptedSharedPreferences despite deprecation:**
- "Deprecated" means "no future features," not "broken" or "removed"
- 1.1.0 is the first stable release (after years in alpha) -- Google explicitly shipped it to stable before closing the library
- The alternative (DataStore + manual Tink encryption) adds coroutine complexity and a new storage paradigm for 2-3 key-value entries
- For document history JSON and settings flags, EncryptedSharedPreferences is exactly right-sized
- If Google removes it in a future AndroidX release (unlikely for years), migration to DataStore+Tink is straightforward

**Why NOT DataStore + Tink for key-value:**
- App currently uses SharedPreferences (DocumentHistoryRepository, AppPreferences)
- Swapping to DataStore changes the concurrency model (suspend/Flow vs synchronous)
- Ripple effect through ViewModel and repository layers for minimal security benefit over EncryptedSharedPreferences
- DataStore is the right choice for new projects; EncryptedSharedPreferences is the right choice for migrating existing SharedPreferences

### Secure Logging

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| Timber | 5.0.1 | Replace 49 direct `android.util.Log` calls with centralized, environment-aware logging | Tree-based architecture: plant `DebugTree` in debug builds, plant nothing in release. No logging reaches logcat in production. Tags auto-derived from calling class names. Pure Java library -- no Kotlin version issues. Industry standard (Jake Wharton). |

**Current problem:** 49 `Log.*()` calls across 9 source files (`PdfPageExtractor`, `PdfUtils`, `ImageUtils`, `MainActivity`, `HistoryFragment`, `CameraFragment`, `PdfViewerFragment`, `NativePdfView`, `PdfEditorFragment`) all emit to logcat in release builds. These log file paths, processing steps, and potentially document names -- information useful to an attacker with ADB access.

**Migration plan:**
1. Add Timber dependency
2. Create `PdfScannerApp : Application()` (none exists), plant `Timber.DebugTree()` only when `BuildConfig.DEBUG`
3. Register Application class in AndroidManifest: `android:name=".PdfScannerApp"`
4. Replace all `Log.d(TAG, msg)` calls with `Timber.d(msg)` (49 replacements across 9 files)
5. Remove `TAG` companion object constants (Timber auto-generates tags)
6. Add Detekt `ForbiddenImport` rule for `android.util.Log` to prevent regression
7. In release: no Tree planted = all Timber calls are no-ops (zero logcat output)

### Dependency Vulnerability Scanning

| Technology | Version | Purpose | Why |
|------------|---------|---------|-----|
| OWASP dependency-check-gradle | 12.2.0 | Scan all dependencies against National Vulnerability Database (NVD) for known CVEs | Industry-standard SCA (Software Composition Analysis) tool. Gradle plugin integrates as `./gradlew dependencyCheckAnalyze`. Reports HTML/JSON. Java 11+ required (project uses Java 17). Published to Gradle Plugin Portal. Latest release January 9, 2026. |

**Configuration recommendations:**
- `failBuildOnCVSS = 7.0f` -- HIGH severity or above fails the build
- `suppressionFile` for known false positives (ML Kit and GMS often trigger low-severity findings)
- Do NOT run on every build (first run downloads NVD data: 5-20 minutes; subsequent runs: 1-3 minutes)
- Run manually: `./gradlew dependencyCheckAnalyze`
- Promote to CI task when QUAL-02 (GitHub Actions) lands in v2+

---

## Platform APIs (No Library Needed)

These security hardening features use Android platform APIs directly. No additional dependencies required.

### Screenshot and Screen Recording Prevention

| API | Min SDK | Purpose |
|-----|---------|---------|
| `WindowManager.LayoutParams.FLAG_SECURE` | API 1 | Prevent screenshots, screen recording, and Recent Apps thumbnail leaks |

**Implementation:** Single line in `MainActivity.onCreate()` before `setContentView()`:
```kotlin
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)
```

**Scope:** One flag on `MainActivity.window` protects ALL 8 fragments because the app uses single-activity architecture with Navigation Component.

**What it prevents:**
- Standard screenshots (shows black)
- Screen recording (black region where window appears)
- Recent Apps thumbnail (blank/solid color instead of document preview)
- Google Assistant screen capture
- Media projection capture (Cast, third-party recorders)

**What it does NOT prevent:**
- Physical camera pointed at screen (out of scope for software)
- ADB screencap (requires USB debugging -- developer mode only)

### Tapjacking Protection

| API | Min SDK | Purpose |
|-----|---------|---------|
| `android:filterTouchesWhenObscured="true"` | API 9 | Block touch events when an overlay covers the view |

**Implementation:** Add attribute to root ViewGroup of sensitive layouts, or apply globally through a base style.

**Priority targets:** Share/export buttons, delete confirmation, camera capture button, any action that processes sensitive document data.

**Note on TapTrap (2025):** A novel animation-based tapjacking attack (presented at USENIX Security 2025) bypasses `filterTouchesWhenObscured` by using activity transition animations rather than overlays. Mitigation requires Android OS-level patches, not app-level code. The traditional overlay defense remains valuable against the vast majority of real-world tapjacking attacks.

### Secure Clipboard Handling

| API | Min SDK | Purpose |
|-----|---------|---------|
| `ClipDescription.EXTRA_IS_SENSITIVE` | API 24 (compat) / API 33 (native) | Flag copied content as sensitive to hide clipboard preview |

**Implementation:** When copying OCR text results:
```kotlin
val clipData = ClipData.newPlainText("OCR Result", recognizedText)
clipData.description.extras = PersistableBundle().apply {
    putBoolean("android.content.extra.IS_SENSITIVE", true)
}
clipboardManager.setPrimaryClip(clipData)
```

**Effect:** Keyboard apps and clipboard managers will not show the copied text in their preview UI. On API 33+, Android automatically clears sensitive clipboard content after ~60 seconds.

**Scope:** OCR text recognition results are the primary clipboard-sensitive data in this app.

### Network Security Configuration

| API | Min SDK | Purpose |
|-----|---------|---------|
| `res/xml/network_security_config.xml` | API 24 | Block cleartext (HTTP) traffic explicitly |

**Implementation:** Create XML config, reference in AndroidManifest via `android:networkSecurityConfig` attribute.

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <!-- Debug-only: allow cleartext to localhost for testing tools -->
    <debug-overrides>
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

**Why NOT certificate pinning:**
- App is offline-first -- no app-owned backend server
- ML Kit models are bundled, not downloaded at runtime
- Document sharing uses `content://` URIs via FileProvider, not network
- Only network traffic is Google Play Services (ML Kit initialization), which uses its own internal pinning
- Pinning Google service certificates would break on cert rotation with zero security benefit

### allowBackup Hardening

| Setting | Current | Recommended | Why |
|---------|---------|-------------|-----|
| `android:allowBackup` | `true` | `false` | High-sensitivity documents must not leak via ADB backup or cloud backup. Current exclusion rules (scans/, processed/, pdfs/) help but do not cover SharedPreferences (which will contain encrypted document metadata). Setting `allowBackup="false"` is the most defensive posture. |

**Impact:** Users lose automatic backup of app preferences. Acceptable trade-off for a high-sensitivity document app -- documents are ephemeral working files, not permanent storage.

### Debuggable Runtime Check

| Check | Purpose |
|-------|---------|
| `ApplicationInfo.FLAG_DEBUGGABLE` check in `Application.onCreate()` | Detect repackaged APKs with debuggable=true |

**Implementation** (in the new `PdfScannerApp` Application class):
```kotlin
if (!BuildConfig.DEBUG && (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
    // Repackaged APK detected -- terminate or restrict functionality
    throw SecurityException("Tampered APK detected")
}
```

**Note:** This is a lightweight integrity check, not full tamper detection. A determined attacker can patch out this check. It catches casual repackaging only.

---

## What NOT to Add

| Technology | Why Not | What to Do Instead |
|------------|---------|-------------------|
| Root detection (RootBeer 0.0.8) | False positives alienate power users; easily bypassed by Magisk Hide; app protects local files (not server accounts) -- a rooted user accessing their own files is not the primary threat | Encryption at rest is the correct defense. Even on a rooted device, encrypted files require the app's key. |
| Play Integrity API | Requires backend server to verify attestation tokens; no backend exists (offline-first) | Defer to v2+ if backend is added |
| DexGuard / commercial obfuscation | R8 already enabled; paid product; obfuscation is not security -- encryption of actual data is what matters | R8 minification + Tink encryption |
| Bouncy Castle / SpongyCastle | Tink provides all needed primitives; Bouncy Castle conflicts with Android's bundled copy; lower-level API increases misuse risk | Tink 1.20.0 |
| SQLCipher / encrypted database | App uses SharedPreferences + file storage, not SQLite; no database exists to encrypt | EncryptedSharedPreferences for key-value; Tink for files |
| Frida / runtime instrumentation detection | Same arms-race category as root detection; no DRM or IAP to protect; encryption at rest is the correct defense | Tink encryption |
| ed-george/encrypted-shared-preferences (community fork) | Unnecessary -- official 1.1.0 stable covers all needs; third-party fork adds supply chain risk for a security-critical dependency | Official security-crypto 1.1.0 |
| DataStore + Tink for key-value | Adds coroutine complexity and a new storage paradigm for 2-3 key-value entries; migration burden through ViewModel/repository layers | EncryptedSharedPreferences (drop-in replacement for existing SharedPreferences) |

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not Alternative |
|----------|-------------|-------------|---------------------|
| File encryption | Tink 1.20.0 (tink-android) | Raw AndroidKeyStore + Cipher | 80-120 lines boilerplate; no streaming; easy IV/GCM misuse |
| File encryption | Tink 1.20.0 | EncryptedFile (security-crypto) | Loads entire file into memory; unacceptable for large multi-page scans |
| Key-value encryption | security-crypto 1.1.0 | DataStore + Tink | Overkill for 2-3 SharedPreferences files; changes concurrency model |
| Key-value encryption | security-crypto 1.1.0 | Raw Keystore + Cipher wrapping SharedPreferences | Reinventing EncryptedSharedPreferences from scratch |
| Logging | Timber 5.0.1 | Custom Log wrapper class | Timber is battle-tested, auto-generates tags, tree architecture for environment-specific behavior |
| Logging | Timber 5.0.1 | R8/ProGuard rules to strip `Log.*` calls | Fragile -- depends on exact method signatures; requires `-assumenosideeffects` which R8 may not honor for all call patterns |
| Dep scanning | OWASP dependency-check 12.2.0 | Snyk / Dependabot | Require cloud accounts or CI infrastructure; OWASP runs locally |
| Dep scanning | OWASP dependency-check 12.2.0 | Gradle Versions Plugin | Checks for version updates, not vulnerabilities -- different purpose |

---

## Version Compatibility Matrix

| New Dependency | Kotlin 1.9.21 | Min SDK 24 | Java 17 | Protobuf Conflict? | ProGuard Rules? |
|----------------|---------------|------------|---------|---------------------|-----------------|
| tink-android 1.20.0 | Compatible (pure Java) | Fully supported (API 24+) | Compatible (requires Java 11+) | No -- shades protobuf-lite internally | None needed (official docs confirm) |
| security-crypto 1.1.0 | Compatible (pure Java) | Requires API 23+ | Compatible | No -- uses Tink internally | None needed (consumer rules bundled in AAR) |
| Timber 5.0.1 | Compatible (pure Java) | API 14+ | Compatible | No | None needed (consumer rules bundled in AAR) |
| dependency-check-gradle 12.2.0 | N/A (Gradle plugin, not runtime code) | N/A | Requires Java 11+ (project uses 17) | No | N/A |

**Key compatibility note:** All recommended additions are pure Java libraries. None introduce Kotlin version constraints, which is critical given the Kotlin 1.9.21 lock and the coroutines force-resolution strategy in `build.gradle.kts`.

---

## Integration Points with Existing Stack

### Tink + Existing File Storage Pipeline

```
CURRENT (unencrypted):
  CameraX capture -> File(filesDir/scans/X.jpg) -> ImageProcessor -> File(filesDir/processed/X.jpg) -> PdfUtils -> File(filesDir/pdfs/X.pdf)

ENCRYPTED:
  CameraX capture -> Tink.encrypt(outputStream) -> filesDir/scans/X.jpg.enc -> Tink.decrypt(inputStream) -> ImageProcessor -> Tink.encrypt() -> filesDir/processed/X.jpg.enc -> Tink.decrypt() -> PdfUtils -> Tink.encrypt() -> filesDir/pdfs/X.pdf.enc
```

- Tink's `StreamingAead` provides `newEncryptingStream(OutputStream)` and `newDecryptingStream(InputStream)` -- wraps existing Java I/O
- Key stored in AndroidKeyStore (hardware-backed on devices with TEE/StrongBox)
- Encrypted files are opaque blobs -- Coil cannot load them directly
- **Coil integration:** Decrypt to a temporary ByteArray or temp file, load into Coil, wipe temp data. Alternatively, write a custom Coil `Fetcher` that decrypts on-the-fly.
- **FileProvider sharing:** Decrypt to a temp file in `cacheDir`, share via FileProvider, delete temp file after share completes

### EncryptedSharedPreferences + Existing SharedPreferences

- `DocumentHistoryRepository` uses `SharedPreferences` for document history JSON
- `AppPreferences` uses `SharedPreferences` for user settings
- Swap `context.getSharedPreferences(name, mode)` with `EncryptedSharedPreferences.create(name, masterKeyAlias, context, ...)` -- same API
- **Migration path:** On first launch after update, read any existing unencrypted SharedPreferences data, write to EncryptedSharedPreferences, delete the old unencrypted file
- MasterKey generation: `MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()`

### Timber + Existing 49 Log Calls

- Global find-and-replace: `Log.d(TAG,` -> `Timber.d(` across 9 files
- Remove `companion object { private const val TAG = "..." }` from each class
- **Detekt enforcement:** Add `ForbiddenImport` rule for `android.util.Log` in `detekt.yml` to prevent regression
- No behavioral change in debug builds (same logcat output via DebugTree)
- Release builds: zero logcat output (no Tree planted)

### FLAG_SECURE + Existing Single-Activity Architecture

- One line in `MainActivity.onCreate()` before `super.onCreate()` / `setContentView()`
- Covers all 8 fragments automatically
- No impact on CameraX preview (FLAG_SECURE applies to the window compositor, not camera hardware access)
- No impact on ML Kit document scanner (it launches its own Activity with separate window)

### Network Security Config + Existing AndroidManifest

- Create `res/xml/network_security_config.xml`
- Add `android:networkSecurityConfig="@xml/network_security_config"` to `<application>` in AndroidManifest
- No code changes -- purely declarative

### OWASP dependency-check + Existing Gradle Build

- Add plugin to root `build.gradle.kts`
- New Gradle task: `./gradlew dependencyCheckAnalyze`
- HTML report at `build/reports/dependency-check-report.html`
- Does NOT affect `./gradlew assembleRelease` or any existing tasks
- First run: 5-20 minutes (NVD download). Subsequent: 1-3 minutes.

---

## Complete Dependency Configuration

### app/build.gradle.kts -- Dependencies to ADD

```kotlin
dependencies {
    // ===== SECURITY HARDENING (v1.2) =====

    // File encryption -- AES-256-GCM streaming via Tink
    // API 24+ fully supported; shades protobuf-lite internally; no ProGuard rules needed
    implementation("com.google.crypto.tink:tink-android:1.20.0")

    // Key-value encryption -- drop-in replacement for SharedPreferences
    // Deprecated but stable (1.1.0 released July 2025); wraps Tink internally
    implementation("androidx.security:security-crypto:1.1.0")

    // Secure logging -- replaces 49 android.util.Log calls
    // Debug: DebugTree logs to logcat; Release: no Tree = zero output
    implementation("com.jakewharton.timber:timber:5.0.1")
}
```

### root build.gradle.kts -- Plugin to ADD

```kotlin
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.6" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
    id("org.owasp.dependencycheck") version "12.2.0" apply false  // ADD
}
```

### app/build.gradle.kts -- Plugin to ADD

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
    id("io.gitlab.arturbosch.detekt")
    id("org.owasp.dependencycheck")  // ADD
}
```

### OWASP dependency-check configuration (in app/build.gradle.kts)

```kotlin
dependencyCheck {
    failBuildOnCVSS = 7.0f  // Fail on HIGH severity or above
    formats = listOf("HTML", "JSON")
    suppressionFile = "$rootDir/config/dependency-check-suppression.xml"
    analyzers {
        // Disable analyzers not relevant to Android/Gradle projects
        nodeEnabled = false
        assemblyEnabled = false
    }
}
```

### AndroidManifest.xml Changes

```xml
<application
    android:name=".PdfScannerApp"
    android:allowBackup="false"
    android:networkSecurityConfig="@xml/network_security_config"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    ... >
```

Changes from current:
1. ADD `android:name=".PdfScannerApp"` (new Application subclass for Timber init + security checks)
2. CHANGE `android:allowBackup` from `"true"` to `"false"`
3. ADD `android:networkSecurityConfig="@xml/network_security_config"`

### New Files to Create

| File | Purpose |
|------|---------|
| `app/src/main/java/com/pdfscanner/app/PdfScannerApp.kt` | Application subclass: Timber init, debuggable check |
| `app/src/main/res/xml/network_security_config.xml` | Block cleartext traffic |
| `config/dependency-check-suppression.xml` | OWASP false positive suppressions |
| Security utility classes (encryption helpers) | Tink key management, encrypt/decrypt wrappers |

**No new repositories needed** -- all artifacts are on Maven Central or Google Maven (already configured in `settings.gradle.kts`).

---

## ProGuard/R8 Considerations

| Library | Rules Needed? | Detail |
|---------|---------------|--------|
| tink-android 1.20.0 | No | "Tink Android requires no proguard configuration" per official setup docs. Shaded protobuf is pre-configured. |
| security-crypto 1.1.0 | No | Consumer rules bundled in AAR. Auto-applied by R8. |
| Timber 5.0.1 | No | Consumer rules bundled in AAR. Auto-applied by R8. |
| OWASP dep-check | N/A | Build-time Gradle plugin -- not in APK. |

**Existing `proguard-rules.pro` requires ZERO changes** for security hardening libraries.

---

## APK Size Impact Estimate

| Library | Approximate Size (after R8) | Notes |
|---------|-----------------------------|-------|
| tink-android 1.20.0 | ~800 KB - 1.2 MB | Includes shaded protobuf-lite; R8 strips unused Tink primitives (e.g., JWT, hybrid encryption) |
| security-crypto 1.1.0 | ~50 KB | Thin wrapper; most of the weight is Tink (already counted above) |
| Timber 5.0.1 | ~20 KB | Very small library |
| **Total new** | **~900 KB - 1.3 MB** | Acceptable for a security-critical app handling IDs, medical docs, contracts |

---

## OWASP MASVS Alignment

The recommended stack maps to OWASP MASVS v2 categories:

| MASVS Category | Coverage | Stack Component |
|----------------|----------|-----------------|
| MASVS-STORAGE | Encryption at rest, secure backup config | Tink, EncryptedSharedPreferences, allowBackup=false |
| MASVS-CRYPTO | Proper key management, strong algorithms | Tink (AES-256-GCM), AndroidKeyStore |
| MASVS-NETWORK | Cleartext traffic blocked | network_security_config.xml |
| MASVS-PLATFORM | Tapjacking, clipboard, screenshot protection | filterTouchesWhenObscured, EXTRA_IS_SENSITIVE, FLAG_SECURE |
| MASVS-CODE | Dependency scanning, secure logging, obfuscation | OWASP dependency-check, Timber, R8 (existing) |
| MASVS-AUTH | N/A | App has no user accounts or authentication |
| MASVS-RESILIENCE | Anti-debug check, R8 obfuscation | FLAG_DEBUGGABLE runtime check, R8 (existing) |
| MASVS-PRIVACY | N/A for v1.2 | No analytics, no tracking, no PII collection beyond documents |

---

## Sources

### HIGH Confidence (Official Documentation, Verified March 2026)
- [AndroidX Security Releases](https://developer.android.com/jetpack/androidx/releases/security) -- security-crypto 1.1.0 stable released July 30, 2025; all APIs deprecated
- [Tink Java Setup Guide (Google Developers)](https://developers.google.com/tink/setup/java) -- tink-android 1.20.0, API 24+ fully supported, no ProGuard needed
- [Tink Java GitHub Releases](https://github.com/tink-crypto/tink-java/releases) -- v1.20.0 released December 10, 2024; Protobuf 4.33.0
- [Android FLAG_SECURE -- Secure Sensitive Activities](https://developer.android.com/security/fraud-prevention/activities) -- official screenshot/recording prevention
- [Android Tapjacking Prevention](https://developer.android.com/privacy-and-security/risks/tapjacking) -- filterTouchesWhenObscured guidance
- [Android Secure Clipboard Handling](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling) -- EXTRA_IS_SENSITIVE flag
- [Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config) -- cleartext traffic and CA config
- [Android Log Info Disclosure](https://developer.android.com/privacy-and-security/risks/log-info-disclosure) -- secure logging guidance

### MEDIUM Confidence (Multiple Sources Agree)
- [OWASP dependency-check-gradle Plugin Portal](https://plugins.gradle.org/plugin/org.owasp.dependencycheck) -- v12.2.0, published January 9, 2026
- [Timber GitHub Repository](https://github.com/JakeWharton/timber) -- v5.0.1 stable, latest on Maven Central
- [OWASP MASVS v2](https://mas.owasp.org/MASVS/) -- Mobile Application Security Verification Standard
- [Goodbye EncryptedSharedPreferences: A 2026 Migration Guide (droidcon)](https://www.droidcon.com/2025/12/16/goodbye-encryptedsharedpreferences-a-2026-migration-guide/) -- confirms Tink as recommended replacement

### LOW Confidence (Estimates, Needs Validation During Implementation)
- APK size estimates are approximate; actual impact depends on R8 tree-shaking aggressiveness
- Tink StreamingAead memory footprint during concurrent encrypt/decrypt of multiple pages needs profiling
- EncryptedSharedPreferences first-open latency (MasterKey generation) needs measurement on low-end devices

---

*Stack research for: Security hardening additions to Android document scanner*
*Researched: 2026-03-03*
