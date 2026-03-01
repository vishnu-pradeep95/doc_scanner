# Technology Stack: Testing & Release Readiness

**Project:** PDF Scanner — v1.1 Testing & Release Pass
**Domain:** Android testing infrastructure + static analysis + release tooling
**Researched:** 2026-03-01
**Confidence:** HIGH (versions web-verified March 2026)

## Scope

This file covers ONLY the new v1.1 additions: testing dependencies, static analysis, and release tooling.
The existing validated stack (CameraX 1.3.1, Coil 2.7.0, ML Kit, Navigation 2.7.x, Material 3, Kotlin 1.9.21,
coroutines 1.7.3, MVVM/LiveData) is out of scope — do not re-research those.

## Current State

Zero tests. Minimal boilerplate in `app/build.gradle.kts`:

```kotlin
// Existing (insufficient — update these)
testImplementation("junit:junit:4.13.2")
androidTestImplementation("androidx.test.ext:junit:1.1.5")          // outdated
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")  // outdated
```

The app uses LiveData + ViewModel + Coroutines with NO dependency injection framework. ViewModels must be
constructed manually in tests; no Hilt/Dagger setup required.

---

## Recommended Stack

### Unit Testing (src/test/ — testImplementation)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| JUnit 4 | 4.13.2 | Test framework | Already present. JUnit 5 requires `android-junit5` plugin with known friction in AndroidX Test. Keep JUnit 4. |
| MockK | 1.14.7 | Kotlin-first mocking | Handles `suspend fun`, final classes (Kotlin default), sealed classes, coroutines natively. Mockito cannot mock final classes without extra config. |
| kotlinx-coroutines-test | 1.7.3 | Coroutine test dispatchers | **MUST match app's coroutines version (1.7.3)**. Provides `runTest`, `TestCoroutineDispatcher`, `advanceUntilIdle`. Mismatched versions cause `NoSuchMethodError` at runtime. |
| androidx.arch.core:core-testing | 2.2.0 | LiveData sync in JVM tests | Provides `InstantTaskExecutorRule` — makes LiveData post synchronously on the main thread. Required since `ScannerViewModel` uses LiveData extensively. |
| Robolectric | 4.16 | Android APIs on JVM | Allows unit-testing Android-dependent code (Context, Bitmap, SharedPreferences) without a device. Critical for `ImageProcessor`, `PdfUtils`, `DocumentHistoryRepository`, `AppPreferences`. |
| com.google.truth:truth | 1.4.4 | Fluent assertions | Clearer failure messages than JUnit asserts (`assertThat(x).isEqualTo(y)`). Standard in Android/Google ecosystem. |

### Instrumented Testing (src/androidTest/ — androidTestImplementation)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| androidx.test.ext:junit-ktx | 1.3.0 | JUnit AndroidX runner | Kotlin extensions for `ActivityScenario`, `JUnit4` annotations. Supersedes `junit:1.1.5`. |
| androidx.test:runner | 1.6.2 | Instrumented test runner | Required — `AndroidJUnitRunner` is the `testInstrumentationRunner`. Already configured. |
| androidx.test:core-ktx | 1.6.1 | Test utilities | `ApplicationProvider`, `ActivityScenario`. Foundation for all instrumented tests. |
| androidx.test:rules | 1.6.1 | JUnit rules | `GrantPermissionRule` (camera), `ActivityScenarioRule`. |
| espresso-core | 3.7.0 | UI interaction testing | Industry standard, auto-synchronizes with UI thread. Replaces existing 3.5.1. |
| espresso-intents | 3.7.0 | Intent verification | Verify FileProvider share intents, gallery picker intents. App uses these for PDF sharing. |
| espresso-contrib | 3.7.0 | RecyclerView actions | `RecyclerViewActions.scrollToPosition()` etc. App uses RecyclerView for page list and document history. |
| MockK Android | 1.14.7 | Mocking in instrumented tests | Same MockK API on device. Use `mockk-android` + `mockk-agent` artifacts for Android instrumented tests. |

### Fragment Testing (debugImplementation)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| androidx.fragment:fragment-testing | 1.8.9 | Isolated fragment launching | `launchFragmentInContainer<HomeFragment>()` — tests each fragment without launching full Activity. Requires `debugImplementation` for manifest merge. |

### Debug-Only Tools (debugImplementation)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| LeakCanary | 2.14 | Memory leak detection | Automatically detects Activity/Fragment/ViewModel leaks during development. Install debug-only — zero production APK impact. Critical for camera lifecycle (CameraX bindings) and 8-fragment navigation stack. |

### Static Analysis (project-level plugin, no APK impact)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Detekt | 1.23.8 | Kotlin static analysis | Compatible with Kotlin 1.9.x. Catches code smells, complexity violations, coroutine anti-patterns. Use `detekt-formatting` plugin to replace ktlint (one tool instead of two). |
| detekt-formatting | 1.23.8 | Code formatting rules | Sub-library of Detekt — applies ktlint rules via Detekt pipeline. Applied as `detektPlugins(...)` dependency, not `implementation`. |
| Android Lint | AGP built-in | Platform lint checks | Already present via AGP 8.13.2. Configure `lint.xml` + baseline file. Treat accessibility checks (`ContentDescription`) as errors for RELEASE-02. |

### Code Coverage (AGP built-in — no extra dependency)

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| JaCoCo | AGP built-in | Line/branch coverage reports | AGP 8.x bundles JaCoCo. Enable via `enableUnitTestCoverage = true` in debug build type. Run via `./gradlew createDebugUnitTestCoverageReport`. No separate plugin needed. |

---

## Complete Dependency Configuration

This is the authoritative diff to apply to `app/build.gradle.kts`.

```kotlin
// =====================================================
// app/build.gradle.kts — ADDITIONS for v1.1
// =====================================================

android {
    defaultConfig {
        // Already present — verify this is set:
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // ADD: Enable JaCoCo coverage reports for debug builds
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    testOptions {
        unitTests {
            // ADD: Required for Robolectric to access Android resources
            isIncludeAndroidResources = true
        }
    }
}

dependencies {

    // ===================================================
    // UNIT TESTING (src/test/) — testImplementation
    // ===================================================

    // JUnit 4 — already present, no change needed
    testImplementation("junit:junit:4.13.2")

    // MockK — Kotlin-first mocking (suspend fun, final classes, sealed classes)
    testImplementation("io.mockk:mockk:1.14.7")

    // Coroutines test — MUST match app's coroutines version (1.7.3)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // LiveData testing — provides InstantTaskExecutorRule
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    // Robolectric — Android APIs on JVM (Context, Bitmap, SharedPreferences)
    testImplementation("org.robolectric:robolectric:4.16")

    // AndroidX Test core for Robolectric (ApplicationProvider, etc.)
    testImplementation("androidx.test:core-ktx:1.6.1")

    // Truth — fluent assertion library
    testImplementation("com.google.truth:truth:1.4.4")

    // ===================================================
    // INSTRUMENTED TESTING (src/androidTest/) — androidTestImplementation
    // ===================================================

    // AndroidX Test foundation
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")

    // Espresso — UI testing (update from existing 3.5.1)
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.7.0")

    // MockK for Android instrumented tests — two artifacts required
    androidTestImplementation("io.mockk:mockk-android:1.14.7")
    androidTestImplementation("io.mockk:mockk-agent:1.14.7")

    // Truth for instrumented tests
    androidTestImplementation("com.google.truth:truth:1.4.4")

    // ===================================================
    // FRAGMENT TESTING — debugImplementation
    // Requires debugImplementation (not androidTestImplementation)
    // so the test manifest is included in debug builds
    // ===================================================

    debugImplementation("androidx.fragment:fragment-testing:1.8.9")

    // ===================================================
    // DEBUG-ONLY TOOLS — debugImplementation
    // ===================================================

    // LeakCanary — memory leak detection (debug builds only)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
```

### Detekt Setup

Detekt is a project-level plugin — NOT an app dependency.

```kotlin
// In ROOT build.gradle.kts — add to existing plugins block:
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.6" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false  // ADD
}
```

```kotlin
// In app/build.gradle.kts — add to existing plugins block:
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
    id("io.gitlab.arturbosch.detekt")  // ADD
}

// Add detekt configuration block in app/build.gradle.kts:
detekt {
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    baseline = file("$rootDir/config/detekt/detekt-baseline.xml")
}

// Add detekt-formatting plugin in app/build.gradle.kts dependencies:
dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}
```

### Android Lint Configuration

```xml
<!-- app/lint.xml — create this file -->
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <!-- RELEASE-02: Treat accessibility issues as errors -->
    <issue id="ContentDescription" severity="error" />
    <issue id="TouchTargetSizeCheck" severity="error" />
    <issue id="KeyboardInaccessibleWidget" severity="error" />

    <!-- Hardcoded strings — already cleaned in v1.0 (DSYS-05), keep as warning -->
    <issue id="HardcodedText" severity="warning" />

    <!-- Unused resources — informational -->
    <issue id="UnusedResources" severity="warning" />
</lint>
```

```kotlin
// In app/build.gradle.kts android {} block — add lint config:
lint {
    abortOnError = true
    warningsAsErrors = false
    xmlReport = true
    htmlReport = true
    lintConfig = file("lint.xml")
    baseline = file("lint-baseline.xml")
}
```

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| JUnit 5 (Jupiter) | Requires `android-junit5` third-party plugin; known friction with AndroidX Test instrumented runners. No meaningful benefit over JUnit 4 for this project. | JUnit 4 (4.13.2) — works everywhere with zero friction |
| Mockito / Mockito-Kotlin | Cannot mock Kotlin final classes without `mockito-inline` workaround. `suspend fun` support is bolted on. | MockK 1.14.7 — designed for Kotlin from the ground up |
| Hilt/Dagger for testing | App has no DI framework. Adding one just for testability is major scope creep. | Construct ViewModels manually with fake dependencies |
| Compose Testing libs | App uses View-based XML layouts, not Compose. | Espresso + Fragment Testing |
| Roborazzi / Paparazzi (screenshot testing) | Explicitly called out of scope in PROJECT.md: "Screenshot regression tests — low confidence tooling, pursue in v2" | Not applicable for v1.1 |
| Turbine (Flow testing) | App uses LiveData, not Flow. `InstantTaskExecutorRule` + `observeForever` is sufficient. | androidx.arch.core:core-testing |
| Detekt 2.0.x alpha | Still in alpha as of March 2026. Built against Kotlin 2.x — incompatible with project's Kotlin 1.9.21. | Detekt 1.23.8 |
| LeakCanary 3.0 alpha | Still in alpha. 2.14 is the current stable. | LeakCanary 2.14 |
| `io.mockk:mockk` for Android instrumented tests | JVM-only artifact fails at runtime on device — missing Android-specific bytecode manipulation. | `io.mockk:mockk-android` + `io.mockk:mockk-agent` (both required) |
| Third-party JaCoCo plugins | AGP 8.x includes JaCoCo natively via `enableUnitTestCoverage = true`. Third-party plugins (vanniktech, arturdm) add complexity with no benefit for single-module projects. | AGP built-in JaCoCo |
| ktlint (standalone) | Detekt with `detekt-formatting` covers all ktlint rules plus Kotlin code smell detection. Two tools for the same job. | `detekt-formatting` |

---

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| MockK 1.14.7 | Mockito-Kotlin 5.x | If team already has Mockito expertise and the codebase uses Java interop heavily |
| Robolectric 4.16 | Device-based instrumented tests | For tests that need exact GPU/camera behavior; Robolectric simulates but doesn't perfectly replicate |
| Detekt 1.23.8 | Android Lint only | If Kotlin-specific smell detection (coroutine anti-patterns, complexity) is not required |
| JaCoCo (AGP built-in) | Kover (JetBrains) | For KMP or Kotlin-only modules where AGP is not involved |
| LeakCanary 2.14 | Android Studio Profiler | When you need to investigate a specific allocation path rather than catch unknown leaks automatically |

---

## Version Compatibility Matrix

| Library | Version | Compatible With | Notes |
|---------|---------|-----------------|-------|
| `io.mockk:mockk:1.14.7` | Kotlin 1.9.21 | HIGH confidence | 1.14.x series targets Kotlin 1.9+ |
| `org.robolectric:robolectric:4.16` | compileSdk 35 | HIGH confidence | 4.16 supports SDK 35 and 36 |
| `io.gitlab.arturbosch.detekt:1.23.8` | Kotlin 1.9.21 | HIGH confidence | 1.23.x series explicitly supports Kotlin 1.9.x; DO NOT upgrade to Detekt 2.x with Kotlin 1.9 |
| `kotlinx-coroutines-test:1.7.3` | `kotlinx-coroutines-android:1.7.3` | HIGH confidence — version MUST match | Use the exact same version as `implementation` coroutines |
| `fragment-testing:1.8.9` | `fragment-ktx:1.6.2` | MEDIUM confidence | fragment-testing 1.8.x is forward-compatible but test with the project's fragment-ktx version |
| `espresso-core:3.7.0` | `androidx.test.ext:junit-ktx:1.3.0` | HIGH confidence | Both released together July 2025 as part of same test suite |
| `leakcanary-android:2.14` | Kotlin 1.9, minSdk 24 | HIGH confidence | 2.14 stable, no 3.x stable exists as of March 2026 |

---

## JaCoCo Coverage Report Command

```bash
# Generate unit test coverage report (no device needed)
./gradlew createDebugUnitTestCoverageReport

# Output location:
# app/build/reports/coverage/test/debug/index.html

# Coverage thresholds from PROJECT.md (enforce manually or in CI):
# util/ package: 70% line coverage
# viewmodel/ package: 50% line coverage
```

---

## Testing Strategy by Code Layer

Maps the stack to the actual project files.

| Layer | Key Files | Test Type | Tools |
|-------|-----------|-----------|-------|
| ViewModel | `ScannerViewModel.kt` | Unit (JVM) | JUnit 4 + MockK + coroutines-test + InstantTaskExecutorRule |
| Utilities | `ImageProcessor.kt`, `PdfUtils.kt`, `PdfPageExtractor.kt` | Unit (JVM + Robolectric) | JUnit 4 + Robolectric + Truth |
| Repository | `DocumentHistoryRepository.kt` | Unit (Robolectric) | JUnit 4 + Robolectric (SharedPreferences/filesDir) |
| Preferences | `AppPreferences.kt` | Unit (Robolectric) | JUnit 4 + Robolectric |
| JSON round-trip | `DocumentEntry.kt` | Unit (JVM) | JUnit 4 + Truth |
| Fragments (UI) | All 8 fragments | Instrumented | Espresso + fragment-testing + MockK Android |
| Navigation | Fragment transitions | Instrumented | Espresso + Navigation Testing |
| Memory leaks | All Activities/Fragments | Debug runtime | LeakCanary (automatic) |
| Code quality | All .kt files | Static analysis | Detekt + Android Lint |
| Coverage | `util/`, `viewmodel/` | Reporting | JaCoCo (AGP built-in) |

---

## Sources

- [MockK releases — GitHub](https://github.com/mockk/mockk/releases) — version 1.14.7 confirmed latest stable (HIGH confidence)
- [Robolectric releases — GitHub](https://github.com/robolectric/robolectric/releases) — 4.16 confirmed latest stable, SDK 36 support (HIGH confidence)
- [AndroidX Test releases — developer.android.com](https://developer.android.com/jetpack/androidx/releases/test) — Espresso 3.7.0, junit-ktx 1.3.0 confirmed July 2025 stable (HIGH confidence)
- [AndroidX Fragment releases](https://developer.android.com/jetpack/androidx/releases/fragment) — fragment-testing 1.8.9 confirmed February 2026 stable (HIGH confidence)
- [LeakCanary changelog — square.github.io](https://square.github.io/leakcanary/changelog/) — 2.14 latest stable; 3.0 alpha only (HIGH confidence)
- [Detekt releases — GitHub](https://github.com/detekt/detekt/releases) — 1.23.8 released February 2025, Kotlin 1.9 compatible, 2.0 still alpha (HIGH confidence)
- [Detekt compatibility table — detekt.dev](https://detekt.dev/docs/introduction/compatibility/) — 1.23.x supports Kotlin 1.9 confirmed
- [Gradle JaCoCo plugin docs — docs.gradle.org](https://docs.gradle.org/current/userguide/jacoco_plugin.html) — built into AGP 8.x via `enableUnitTestCoverage` (HIGH confidence)
- [Maven Central io.mockk:mockk-android](https://central.sonatype.com/artifact/io.mockk/mockk-android/versions) — 1.14.7 confirmed on Maven Central (HIGH confidence)
- Project `app/build.gradle.kts` analysis — existing plugin versions and existing test deps (HIGH confidence)

---

*Stack research for: Android testing infrastructure and release readiness tooling*
*Researched: 2026-03-01*
