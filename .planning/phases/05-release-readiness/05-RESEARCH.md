# Phase 5: Release Readiness - Research

**Researched:** 2026-03-01
**Domain:** Android static analysis (Detekt + Lint), manifest hardening, ProGuard/R8 keep rules, LeakCanary memory leak detection
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
User selected no areas to discuss — all implementation decisions delegated to Claude. Follow the success criteria from ROADMAP.md exactly:
- Detekt with detekt-baseline.xml so existing violations don't block the build
- Android Lint with `ContentDescription` and `TouchTargetSizeCheck` as errors, zero errors
- Manifest: camera `uses-feature android:required="false"`, `dataExtractionRules`, `fullBackupContent` excluding private scan dirs, FileProvider paths scoped to actually-used subdirectories
- ProGuard keep rules for ML Kit, Navigation SafeArgs, Coil, coroutines
- LeakCanary: zero retained Activity/Fragment/ViewModel leaks; Navigation 2.7.x AbstractAppBarOnDestinationChangedListener leak documented as library bug and triaged

### Claude's Discretion
All implementation decisions delegated to Claude.

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| RELEASE-01 | Detekt 1.23.8 + detekt-formatting configured with baseline file; `./gradlew detekt` passes with zero new blocking errors (existing violations captured in baseline) | Detekt plugin ID, Kotlin DSL config, baseline generation workflow documented below |
| RELEASE-02 | Android Lint with lint.xml — accessibility errors promoted to build errors; `./gradlew lint` passes | lint{} Kotlin DSL block, lint.xml structure, ContentDescription/TouchTargetSizeCheck severity configuration documented below |
| RELEASE-03 | ProGuard/R8 keep rules in proguard-rules.pro for ML Kit, Navigation SafeArgs, Coil, coroutines; verified via release APK inspection | Keep rules for each library documented; Coil/coroutines ship consumer rules automatically with R8 |
| RELEASE-04 | Release APK installed on physical Android device; every screen manually exercised — ENVIRONMENT-BLOCKED in WSL2 | WSL2 block confirmed; emulator fallback guidance documented; tasks should note block and provide checklist |
| RELEASE-05 | `<uses-feature android:required="false" />` for camera added to AndroidManifest.xml | Current manifest has `required="true"` — single attribute change documented |
| RELEASE-06 | `dataExtractionRules` (API 31+) and `fullBackupContent` added to AndroidManifest.xml excluding private scan dirs | XML structure for both files + manifest attributes documented with domain mappings |
| RELEASE-07 | FileProvider file_paths.xml cache-path tightened from `path="/"` to specific subdirs | Current overly-broad path identified; correction pattern documented |
| RELEASE-08 | LeakCanary 2.14 added as debugImplementation; zero retained leaks; Navigation 2.7.x leak documented | Integration documented; Navigation leak confirmed as real library bug (not auto-classified); triage pattern documented |
</phase_requirements>

---

## Summary

Phase 5 addresses four distinct work areas: static analysis tooling (Detekt + Android Lint), AndroidManifest hardening for Play Store distribution, ProGuard/R8 keep rules verification, and memory leak detection via LeakCanary. Each area is well-understood with documented APIs and clear configuration patterns.

The Detekt and Android Lint work is purely additive — no existing code changes are required, only build configuration and XML config files. The manifest hardening involves three specific changes: one attribute flip (`required="false"`), two new XML resource files (`data_extraction_rules.xml` and `backup_rules.xml`), and tightening one FileProvider path. ProGuard is largely automatic via R8 consumer rules bundled in Coil and coroutines; ML Kit and Navigation SafeArgs are the only libraries requiring explicit keep rules. LeakCanary requires only a single `debugImplementation` dependency line, with the Navigation 2.7.x `AbstractAppBarOnDestinationChangedListener` leak requiring a documented triage decision rather than a code fix.

All binding nullification (`_binding = null` in `onDestroyView()`) is already implemented correctly across all 11 fragments in the project — no binding audit work is needed. The ENVIRONMENT-BLOCKED gate (RELEASE-04, physical device APK verification) should be planned with a documented checklist and emulator fallback, not blocked entirely.

**Primary recommendation:** Execute plans in roadmap order (01: Detekt + LeakCanary, 02: Lint + manifest, 03: ProGuard + release APK). Each plan is independently verifiable with a Gradle task.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Detekt | 1.23.8 | Kotlin static analysis | Latest 1.23.x; 2.x requires Kotlin 2.x which project cannot use (locked at 1.9.21) |
| detekt-formatting | 1.23.8 | Ktlint-based formatting rules via Detekt | Paired with Detekt; same version always |
| LeakCanary | 2.14 | Memory leak detection (debug builds only) | Square's standard Android leak detector; auto-detects Activity/Fragment/ViewModel leaks |
| Android Lint | Built into AGP 8.13.2 | XML + code static analysis | Built-in; no additional dependency needed |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| R8 | Built into AGP | Code shrinking + ProGuard-compatible rules | Always active when `isMinifyEnabled = true` (already set) |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Detekt 1.23.8 | Detekt 2.x | 2.x requires Kotlin 2.x; project locked to Kotlin 1.9.21 — would break build |
| LeakCanary 2.14 | ObjectWatcher manual instrumentation | LeakCanary is zero-config for standard Fragment/Activity leaks; manual is only needed for custom objects |

**Installation (additions to app/build.gradle.kts):**
```kotlin
// Root build.gradle.kts plugins block
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

// app/build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
```

---

## Architecture Patterns

### Recommended File Structure
```
app/
├── proguard-rules.pro           # Append ML Kit + SafeArgs keep rules here
└── src/main/
    ├── AndroidManifest.xml      # Add uses-feature, dataExtractionRules, fullBackupContent attrs
    └── res/xml/
        ├── file_paths.xml       # Tighten cache-path from "/" to specific subdir
        ├── data_extraction_rules.xml   # NEW: API 31+ backup exclusions
        └── backup_rules.xml            # NEW: API 30 and below backup exclusions

config/
└── detekt/
    ├── detekt.yml               # NEW: detekt config (generated via detektGenerateConfig)
    └── detekt-baseline.xml      # NEW: generated via ./gradlew detektBaseline
```

### Pattern 1: Detekt Plugin Configuration (Kotlin DSL)

**What:** Apply Detekt to the app module with formatting and baseline
**When to use:** Single-module Android app (this project's structure)

```kotlin
// In root build.gradle.kts — add detekt plugin declaration:
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.6" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

// In app/build.gradle.kts — apply and configure:
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
    id("io.gitlab.arturbosch.detekt")   // ADD
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/detekt-baseline.xml")
    source.setFrom(
        "src/main/java",
        "src/test/java",
        "src/androidTest/java"
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(false)
    }
    exclude("**/build/**")
    exclude("**/generated/**")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
    // ... existing deps
}
```

**Baseline generation workflow:**
1. Run `./gradlew detektGenerateConfig` → creates `config/detekt/detekt.yml`
2. Run `./gradlew detektBaseline` → creates `config/detekt/detekt-baseline.xml` capturing all existing violations
3. Commit both files immediately — baseline must be generated from unmodified codebase
4. Subsequent `./gradlew detekt` runs fail only on NEW violations not in the baseline

### Pattern 2: Android Lint Configuration

**What:** Promote accessibility checks to errors; use lint.xml for issue severity control
**When to use:** Any project targeting accessibility compliance

```kotlin
// In app/build.gradle.kts, inside android {} block:
android {
    // ... existing config
    lint {
        abortOnError = true           // Fail build on lint errors
        lintConfig = file("lint.xml") // Point to our config file
        htmlReport = true
        htmlOutput = file("build/reports/lint/lint-results.html")
    }
}
```

```xml
<!-- app/lint.xml (place at app/ module root, next to build.gradle.kts) -->
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <!-- Accessibility: missing contentDescription on non-text views -->
    <issue id="ContentDescription" severity="error" />

    <!-- Accessibility: touch targets smaller than 48dp -->
    <issue id="TouchTargetSizeCheck" severity="error" />

    <!-- If the codebase has existing violations in other checks,
         they can be suppressed here to keep the build clean.
         Only add these if ./gradlew lint reveals pre-existing issues. -->
</lint>
```

**Run command:** `./gradlew lint` (output: `app/build/reports/lint/lint-results.html`)

### Pattern 3: AndroidManifest Hardening

**What:** Three specific manifest changes for Play Store distribution readiness
**When to use:** Before any release to Play Store

**Change 1 — camera uses-feature (RELEASE-05):**
```xml
<!-- BEFORE (current state): -->
<uses-feature android:name="android.hardware.camera" android:required="true" />

<!-- AFTER (required for Play Store tablet/Chromebook eligibility): -->
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

**Change 2 — backup exclusions (RELEASE-06):**
Add two attributes to `<application>` tag:
```xml
<application
    android:allowBackup="true"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:fullBackupContent="@xml/backup_rules"
    ... >
```

Create `app/src/main/res/xml/data_extraction_rules.xml` (API 31+):
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <!-- Exclude private scan directories — contain user document data -->
        <exclude domain="file" path="scans/" />
        <exclude domain="file" path="processed/" />
        <exclude domain="file" path="pdfs/" />
        <!-- cache/ is auto-excluded by Android; explicit for clarity -->
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="scans/" />
        <exclude domain="file" path="processed/" />
        <exclude domain="file" path="pdfs/" />
    </device-transfer>
</data-extraction-rules>
```

Create `app/src/main/res/xml/backup_rules.xml` (API 30 and below):
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="file" path="scans/" />
    <exclude domain="file" path="processed/" />
    <exclude domain="file" path="pdfs/" />
</full-backup-content>
```

Note: `getCacheDir()` is automatically excluded from backup by Android — no explicit exclusion needed for cache.
Note: `domain="file"` maps to `context.filesDir` (internal storage `files/` directory).

**Change 3 — FileProvider scope (RELEASE-07):**
```xml
<!-- BEFORE (current state in file_paths.xml) — too broad: -->
<cache-path name="cache" path="/" />

<!-- AFTER — scope to actually-used cache subdirectory: -->
<!-- CanHub cropper writes to cacheDir/cropped/ or directly to cacheDir root
     Check app code for actual cache subdir usage before choosing path -->
<cache-path name="cache" path="." />
<!-- or if only specific subdir is used: -->
<cache-path name="cache" path="cropped/" />
```

### Pattern 4: ProGuard/R8 Keep Rules

**What:** Explicit keep rules for libraries that use reflection, JNI, or are accessed by class name
**When to use:** Any library not bundling its own consumer-rules.pro in its AAR

```proguard
# app/proguard-rules.pro — APPEND these rules to existing CameraX + CanHub rules

# ===== ML Kit Text Recognition =====
# ML Kit does not bundle sufficient consumer rules for R8 full mode
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ===== ML Kit Document Scanner (GMS-based) =====
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ===== Navigation SafeArgs Generated Classes =====
# SafeArgs generates *Args and *Directions classes by class name reference in nav graph
# These classes use primitive types only (string/integer) so no custom class keeps needed,
# but the generated classes themselves must not be renamed/removed
-keepnames class com.pdfscanner.app.**.*Args { *; }
-keepnames class com.pdfscanner.app.**.*Directions { *; }

# ===== Coil (io.coil-kt:coil:2.7.0) =====
# R8 handles Coil automatically — consumer rules are bundled in the AAR.
# No explicit rules needed when using R8 (default since AGP 3.4.0).
# (If using ProGuard legacy toolchain, add ServiceLoader keeps — not applicable here)

# ===== Kotlin Coroutines (kotlinx-coroutines-android:1.7.3) =====
# ProGuard/R8 rules are bundled into the kotlinx-coroutines-android module.
# No explicit rules needed — consumer rules ship with the artifact.

# ===== Kotlin Serialization / Reflection =====
# Not used in this project; no rules needed.
```

### Pattern 5: LeakCanary Integration

**What:** Debug-only memory leak detection with automatic Activity/Fragment/ViewModel watching
**When to use:** Always in debug builds; zero-config for standard use cases

```kotlin
// app/build.gradle.kts dependencies block:
debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
```

No code changes needed — LeakCanary auto-installs via ContentProvider at app startup.

**Verification:** Filter Logcat for tag `LeakCanary` — startup message confirms it's running.

**Navigation 2.7.x Leak Triage:**
The `AbstractAppBarOnDestinationChangedListener` leak in Navigation 2.7.6 is a real leak in library code (not a false positive). It was reported and confirmed in LeakCanary issue #2566. LeakCanary does NOT automatically classify this as a library leak — it will appear as a real leak trace.

The appropriate triage is to document it in a `KNOWN_LEAKS.md` or in-code comment and explicitly NOT upgrade Navigation (Navigation 2.8.x adds significant migration risk). The project decision (from STATE.md) is to document and triage, not fix.

To add a custom reference matcher excluding this leak from failure reports:
```kotlin
// In a debug-only Application class or DebugLeakConfig.kt:
// (Only needed if you want to suppress it from leak count — for triage, documentation is sufficient)
LeakCanary.config = LeakCanary.config.copy(
    referenceMatchers = AndroidReferenceMatchers.appDefaults +
        LibraryLeakReferenceMatcher(
            pattern = instanceField(
                "androidx.navigation.ui.AbstractAppBarOnDestinationChangedListener",
                "context"  // the strong Context reference
            ),
            description = "Navigation 2.7.x library leak — AbstractAppBarOnDestinationChangedListener " +
                "holds strong Context reference. Filed as library bug. " +
                "Upgrading to Navigation 2.8.x resolves but adds migration risk. " +
                "Tracked as known library leak, not app code."
        )
)
```

### Anti-Patterns to Avoid

- **Running detektBaseline after code changes:** Baseline captures all current violations; if run after adding new code, new issues are silently swallowed. Run it once on the current unmodified codebase and commit immediately.
- **Using `abortOnError = false` for lint:** This defeats the purpose — lint errors must fail the build. Use `lint.xml` to suppress specific pre-existing issues instead.
- **Adding broad ML Kit keeps:** `-keep class com.google.mlkit.** { *; }` is correct; don't add `-keep class com.google.** { *; }` which would include all of Google Play Services and balloon APK size.
- **Treating the Navigation leak as an app bug:** The AbstractAppBarOnDestinationChangedListener leak is in library code. Investigate LeakCanary output for OTHER leaks first, then document the Navigation one separately.
- **Expecting LeakCanary on emulator:** LeakCanary works on emulators, but the RELEASE-04 physical device E2E test requires real hardware. Keep these gates separate.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Kotlin static analysis | Custom Checkstyle/PMD config | Detekt 1.23.8 | Kotlin-native rules; understands suspend functions, coroutines, sealed classes |
| Memory leak detection | Manual heap dump analysis | LeakCanary 2.14 | Automatic heuristic identification of Activity/Fragment/ViewModel leaks with annotated traces |
| Backup exclusion XML | Custom backup agent | `dataExtractionRules` + `fullBackupContent` XML | Android system handles backup; XML declarative exclusions are the official API |
| Baseline tracking | Comment-suppressing existing issues | `detekt-baseline.xml` | Baseline is the official Detekt mechanism; suppressions in code are noisy and harder to audit |

**Key insight:** All four problem domains have official first-party solutions. Custom solutions would miss edge cases that Android's toolchain handles automatically (e.g., LeakCanary tracking GC roots through ClassLoader boundaries, or detekt understanding Kotlin-specific constructs like data class `copy()` generated code).

---

## Common Pitfalls

### Pitfall 1: Detekt Baseline Generated on Dirty Codebase
**What goes wrong:** `./gradlew detektBaseline` runs after changes are made; new violations are captured in baseline and never fail future runs.
**Why it happens:** Forgetting that baseline is meant to snapshot the current state only.
**How to avoid:** Run `detektBaseline` as the VERY FIRST step on an unmodified `master` branch before making any Phase 5 changes. Commit the baseline file immediately with a clear commit message.
**Warning signs:** Baseline XML contains many entries for code that was just written.

### Pitfall 2: Android Lint TouchTargetSizeCheck ID Mismatch
**What goes wrong:** The lint check ID in `lint.xml` doesn't exactly match the official check ID, so the severity configuration is silently ignored.
**Why it happens:** Lint check IDs are case-sensitive and may differ from the human-readable name.
**How to avoid:** Run `./gradlew lint` once without the config, look at the HTML report for exact issue IDs, then use those exact strings in `lint.xml`.
**Warning signs:** `./gradlew lint` passes even after adding `severity="error"` for checks that appear in the report as warnings.

### Pitfall 3: cache-path FileProvider Scope Too Broad Breaks Sharing
**What goes wrong:** After tightening `cache-path` from `path="/"` to a specific subdir, the CanHub cropper or other feature fails to share a file because the file is in a different cache subdir.
**Why it happens:** The actual path where CanHub/ImageCapture writes temp files may not match assumptions.
**How to avoid:** Before changing `file_paths.xml`, search the codebase for all `FileProvider.getUriForFile()` calls and verify what cache subdirectory each feature actually uses.
**Warning signs:** `FileUriExposedException` or `IllegalArgumentException: Failed to find configured root` in Logcat after tightening the path.

### Pitfall 4: ML Kit Keep Rules Too Narrow
**What goes wrong:** Release APK crashes with `ClassNotFoundException` for an ML Kit class that was renamed/removed by R8.
**Why it happens:** ML Kit uses reflection internally to load models and processors; R8 removes classes not reachable via normal call graph.
**How to avoid:** Keep `com.google.mlkit.**` (entire package) and `com.google.android.gms.**` (for GMS-based scanner). These are both needed.
**Warning signs:** Release build works in debug but crashes with `ClassNotFoundException` or `NoSuchMethodException` at ML Kit initialization.

### Pitfall 5: LeakCanary 2.14 Requires minSdk 21+
**What goes wrong:** LeakCanary build fails or has runtime issues if project minSdk is below 21.
**Why it happens:** LeakCanary 2.x dropped support for API < 21.
**How to avoid:** Project minSdk is 24 — no issue. Just document this as a non-concern.
**Warning signs:** N/A for this project.

### Pitfall 6: detekt-formatting Conflicts with Auto-Baseline
**What goes wrong:** Running `detektBaseline` while detekt-formatting is configured causes ambiguous signatures because formatting changes the code before analysis.
**Why it happens:** Official detekt documentation states "Auto formatting cannot be combined with the baseline."
**How to avoid:** Configure detekt-formatting as a detektPlugins dependency (which runs as a check/report, not auto-formatter). Do NOT use `autoCorrect = true` in the detekt config when using baseline.
**Warning signs:** Baseline regeneration produces different results on consecutive runs.

### Pitfall 7: PdfViewerFragment binding nullification order
**What goes wrong:** `_binding = null` appears after `super.onDestroyView()` in PdfViewerFragment (line 268 vs 252) — this is the wrong order.
**Why it happens:** binding should be nulled BEFORE calling super, or at minimum consistently.
**How to avoid:** The standard pattern is: null the binding BEFORE `super.onDestroyView()`. Check PdfViewerFragment.kt line 251-268 during LeakCanary audit.
**Warning signs:** LeakCanary reports a Fragment View leak for PdfViewerFragment specifically.

---

## Code Examples

Verified patterns from official sources and project inspection:

### Detekt Full Configuration (Kotlin DSL)
```kotlin
// Source: https://plugins.gradle.org/plugin/io.gitlab.arturbosch.detekt + official docs

// root/build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

// app/build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/detekt-baseline.xml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    exclude("**/build/**", "**/generated/**")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}
```

### Baseline Generation Workflow
```bash
# Step 1: Generate default config (optional but recommended)
./gradlew detektGenerateConfig
# Creates: config/detekt/detekt.yml

# Step 2: Generate baseline (captures all current violations)
./gradlew detektBaseline
# Creates: config/detekt/detekt-baseline.xml

# Step 3: Commit both files
git add config/detekt/
git commit -m "chore(detekt): add baseline and config — captures pre-existing violations"

# Step 4: Verify clean run
./gradlew detekt
# Should pass with zero new blocking errors
```

### Lint Configuration (Kotlin DSL + lint.xml)
```kotlin
// Source: https://developer.android.com/studio/write/lint
// app/build.gradle.kts inside android {} block:
lint {
    abortOnError = true
    lintConfig = file("lint.xml")
    htmlReport = true
    htmlOutput = file("$buildDir/reports/lint/lint-results.html")
}
```

```xml
<!-- app/lint.xml — Source: Android official docs -->
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <issue id="ContentDescription" severity="error" />
    <issue id="TouchTargetSizeCheck" severity="error" />
</lint>
```

### ProGuard Rules (complete additions)
```proguard
# Source: ML Kit docs + project code analysis (all SafeArgs args are primitive types)

# ML Kit Text Recognition (bundled model)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ML Kit Document Scanner (GMS-based, uses GMS task machinery)
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Navigation SafeArgs generated classes
# All nav graph args use string/integer (confirmed by nav_graph.xml inspection)
# Keep generated *Args and *Directions classes by name pattern
-keepnames class com.pdfscanner.app.**.*Args { *; }
-keepnames class com.pdfscanner.app.**.*Directions { *; }

# Coil 2.7.0: consumer rules auto-bundled via R8 — no explicit rules needed
# Coroutines 1.7.3: consumer rules auto-bundled via kotlinx-coroutines-android — no explicit rules needed
```

### LeakCanary Integration
```kotlin
// app/build.gradle.kts dependencies:
// Source: https://square.github.io/leakcanary/getting_started/
debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
```

### Binding Nullification Audit Results
All 11 fragments already correctly implement the pattern:
- `HomeFragment` — _binding = null in onDestroyView() ✓
- `CameraFragment` — _binding = null in onDestroyView() ✓
- `PreviewFragment` — _binding = null in onDestroyView() ✓
- `HistoryFragment` — _binding = null in onDestroyView() ✓
- `PdfViewerFragment` — _binding = null in onDestroyView() ✓ (verify order: null BEFORE super)
- `SettingsFragment` — _binding = null in onDestroyView() ✓
- `PagesFragment` — _binding = null in onDestroyView() ✓
- `PdfEditorFragment` — _binding = null in onDestroyView() ✓
- `TextInputDialogFragment` — _binding = null in onDestroyView() ✓
- `SignatureDialogFragment` — _binding = null in onDestroyView() ✓
- `StampPickerDialogFragment` — _binding = null in onDestroyView() ✓

No binding leak remediation is needed — existing code is correct.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| ProGuard (legacy) | R8 (default) | AGP 3.4.0 (2019) | R8 is faster, more aggressive; consumer rules from libraries auto-apply |
| `android:allowBackup` only | `dataExtractionRules` + `fullBackupContent` | API 31 (2021) | Lint warns if `dataExtractionRules` is missing on API 31+ targets |
| Manual leak investigation | LeakCanary with heap analysis | LeakCanary 2.0 (2019) | Automated annotated traces; identifies GC root chain |
| Detekt 1.x config format | Detekt 1.23.x stable | 2023 | 1.23.8 is the final 1.x release; next is 2.x which requires Kotlin 2.x |
| `lintOptions {}` block | `lint {}` block | AGP 7.0 (2021) | `lintOptions` deprecated; use `lint {}` in Kotlin DSL |

**Deprecated/outdated:**
- `lintOptions {}` in build.gradle: replaced by `lint {}` — using old form triggers deprecation warning in AGP 8.x
- `toolVersion` in detekt block: in Detekt 1.23.x, version is set via plugin declaration in `plugins {}` block, not a separate property
- `android:allowBackup="false"` as the backup solution: the correct approach for API 31+ is `dataExtractionRules` with selective exclusions, not disabling backup entirely

---

## Open Questions

1. **FileProvider cache path actual subdir usage**
   - What we know: The current `cache-path path="/"` gives access to entire cacheDir
   - What's unclear: Exactly which subdirectory CanHub cropper writes its temp files to
   - Recommendation: Search for `FileProvider.getUriForFile` calls in the codebase before tightening; if CanHub uses `cacheDir` root directly, use `path="."` instead of a subdir

2. **RELEASE-04 emulator fallback viability**
   - What we know: WSL2 lacks JDK/Android Studio; physical device E2E is environment-blocked
   - What's unclear: Whether an emulator accessible from WSL2 can be used as a partial substitute
   - Recommendation: Plan should explicitly note the environment block, provide a manual checklist for when access to a device is available, and mark RELEASE-04 as "completed when host machine test is performed" — do not block the phase merge on it

3. **Detekt config generation — whether to customize detekt.yml**
   - What we know: `detektGenerateConfig` creates a ~700-line default config
   - What's unclear: Whether any rules need explicit disabling for Android ViewBinding generated code
   - Recommendation: Use `buildUponDefaultConfig = true` with default config; only customize if `./gradlew detekt` reveals false positives in generated binding code. Exclude `**/build/**` and `**/generated/**` to handle most generated code issues.

---

## Sources

### Primary (HIGH confidence)
- https://plugins.gradle.org/plugin/io.gitlab.arturbosch.detekt — exact plugin ID `io.gitlab.arturbosch.detekt`, version 1.23.8 confirmed
- https://detekt.dev/docs/introduction/baseline/ — baseline.xml structure, `detektBaseline` task, Kotlin DSL baseline configuration
- https://square.github.io/leakcanary/getting_started/ — `debugImplementation 'com.squareup.leakcanary:leakcanary-android:2.14'`, zero-code-change integration
- https://square.github.io/leakcanary/recipes/ — `referenceMatchers` API, `AndroidReferenceMatchers.appDefaults`, `LibraryLeakReferenceMatcher` configuration
- https://developer.android.com/identity/data/autobackup — exact XML structure for `data_extraction_rules.xml` and `backup_rules.xml`, domain mappings, auto-exclusion of cacheDir
- https://developer.android.com/studio/write/lint — `lint {}` Kotlin DSL block, `lint.xml` severity configuration, `abortOnError`
- Project source inspection — all 11 fragments confirmed to have correct `_binding = null` in `onDestroyView()`; nav_graph.xml confirms all SafeArgs args are primitive types

### Secondary (MEDIUM confidence)
- https://github.com/square/leakcanary/issues/2566 — Navigation 2.7.x AbstractAppBarOnDestinationChangedListener leak confirmed as real library code leak (not a false positive); closed 2024-05-30 with recommendation to file Jetpack Navigation bug
- https://github.com/Kotlin/kotlinx.coroutines/blob/master/README.md — "R8 and ProGuard rules are bundled into the kotlinx-coroutines-android module" (no manual rules needed)
- https://coil-kt.github.io/coil/faq/ — "You do not need to add any custom rules for Coil if you use R8" (R8 handles automatically)

### Tertiary (LOW confidence)
- WebSearch results for ML Kit ProGuard rules — multiple sources recommend `-keep class com.google.mlkit.** { *; }` and `-keep class com.google.android.gms.** { *; }` but official docs don't explicitly list these. Consumer rules in AAR may handle this automatically; explicit rules are a safe fallback.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Detekt 1.23.8 forced by Kotlin 1.9.21 constraint (confirmed); LeakCanary 2.14 from REQUIREMENTS.md; AGP lint built-in
- Architecture patterns: HIGH — All patterns verified from official documentation and project code inspection
- ProGuard rules: MEDIUM — Coil and coroutines confirmed auto-bundled; ML Kit explicit keeps are best-practice recommendation, not confirmed via official docs (official docs silent on the topic); SafeArgs keep rules are derived from primitive-only nav graph args (confirmed)
- Pitfalls: HIGH — All pitfalls derived from either official docs, confirmed issue reports, or direct project code inspection

**Research date:** 2026-03-01
**Valid until:** 2026-04-01 (stable tooling; Detekt 1.23.x is in maintenance mode)
