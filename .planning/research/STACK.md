# Technology Stack: Testing, Polish & Quality Assurance

**Project:** PDF Scanner - Polish & Quality Pass
**Researched:** 2026-02-28
**Focus:** Testing stack, linting, performance profiling, UI polish validation
**Note:** WebSearch and WebFetch were unavailable during research. Versions are based on training data (cutoff May 2025) and marked accordingly. Verify versions against Maven Central / Google Maven before adding to build.gradle.kts.

## Current State

The app has **zero tests** -- no unit tests, no instrumented tests, no lint configuration beyond defaults. The existing test dependencies are minimal boilerplate:

```kotlin
// Current (insufficient)
testImplementation("junit:junit:4.13.2")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
```

The app uses LiveData + ViewModel + Coroutines with no dependency injection framework. This means testing will need to handle ViewModel construction manually and mock dependencies directly.

---

## Recommended Stack

### Unit Testing (src/test/)

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| JUnit 4 | 4.13.2 | Test framework | Already in project. JUnit 5 adds complexity on Android with no benefit for this project's scope. Keep it simple. | HIGH |
| MockK | 1.13.12+ | Kotlin-first mocking | Built for Kotlin (coroutines, extension functions, sealed classes). Mockito-kotlin exists but MockK is idiomatic and handles `suspend fun` natively. | MEDIUM (verify version) |
| kotlinx-coroutines-test | 1.7.3 | Coroutine testing | Must match app's coroutines version (1.7.3). Provides `runTest`, `TestDispatcher`, `advanceUntilIdle`. Essential for testing ViewModel coroutine logic. | HIGH |
| Turbine | 1.1.0+ | Flow/LiveData testing | Clean assertion DSL for Flow emissions. Even though app uses LiveData, useful if any Flow usage exists or is introduced. Lightweight. | MEDIUM (verify version) |
| AndroidX Arch Core Testing | 2.2.0 | LiveData testing | Provides `InstantTaskExecutorRule` to make LiveData synchronous in unit tests. Required since app uses LiveData extensively. | HIGH |
| Robolectric | 4.12+ | Android framework in JVM tests | Allows testing Android-dependent code (Context, SharedPreferences, Bitmap) without a device. Critical for testing ImageProcessor, PdfUtils, AppPreferences, SoundManager. | MEDIUM (verify version) |
| Truth | 1.4.2+ | Assertions | Google's fluent assertion library. Clearer failure messages than JUnit assertions. Standard in Android ecosystem. | MEDIUM (verify version) |

### Instrumented Testing (src/androidTest/)

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Espresso | 3.6.1 | UI testing | Industry standard for Android UI tests. Synchronizes with UI thread automatically. Already partially in project (outdated version). | MEDIUM (verify version) |
| AndroidX Test Core | 1.6.1 | Test utilities | ActivityScenario, ApplicationProvider. Foundation for instrumented tests. | MEDIUM (verify version) |
| AndroidX Test Runner | 1.6.1 | Test runner | Required for instrumented test execution. | MEDIUM (verify version) |
| AndroidX Test Rules | 1.6.1 | Test rules | ActivityScenarioRule, GrantPermissionRule (camera permissions). | MEDIUM (verify version) |
| AndroidX Fragment Testing | 1.8.0+ | Fragment testing | launchFragmentInContainer for isolated fragment tests. Critical for testing CameraFragment, HomeFragment, etc. in isolation. | MEDIUM (verify version) |
| Espresso Intents | 3.6.1 | Intent verification | Verify intents fired (share, file picker). The app uses FileProvider sharing extensively. | MEDIUM (verify version) |
| Espresso Contrib | 3.6.1 | RecyclerView testing | RecyclerViewActions for testing page list, document history. App uses RecyclerView heavily. | MEDIUM (verify version) |
| UI Automator | 2.3.0 | Cross-app testing | For testing share sheet, file picker, permission dialogs. Espresso cannot reach outside the app process. | MEDIUM (verify version) |

### Screenshot Testing

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Roborazzi | 1.20+ | Screenshot testing on JVM | Catches visual regressions (spacing, colors, icon sizing) without a device. Runs on JVM via Robolectric. Faster CI than Paparazzi. Actively maintained by the Android community. | LOW (verify version, evaluate vs Paparazzi) |

**Rationale for screenshot testing:** The project requirements explicitly call for a "UI consistency audit -- spacing, typography, colors, icon sizing." Screenshot tests are the only automated way to enforce this over time.

### Static Analysis & Linting

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Android Lint (built-in) | AGP 8.x | Standard lint checks | Already available via AGP. Enable `abortOnError true` for release builds. Configure `warningsAsErrors` for key categories. Zero setup cost. | HIGH |
| Detekt | 1.23.6+ | Kotlin static analysis | Catches code smells, complexity, formatting issues. More Kotlin-aware than ktlint. Includes rules for coroutine anti-patterns. Use the `detekt-formatting` plugin to subsume ktlint rules. | MEDIUM (verify version) |
| Accessibility Scanner / lint checks | built-in | Accessibility validation | Android Lint includes accessibility checks (missing contentDescription, small touch targets). Enable `a11y` lint category. No extra dependency needed. | HIGH |

### Performance Profiling

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| Android Studio Profiler | built-in | CPU, memory, network profiling | Manual tool for investigating jank, memory leaks, slow operations. Use during polish pass to identify bottlenecks in camera startup, PDF rendering, filter application. | HIGH |
| LeakCanary | 2.14+ | Memory leak detection | Auto-detects Activity/Fragment leaks during development. Install in debug only. Critical for camera lifecycle (CameraX bindings) and fragment navigation. | MEDIUM (verify version) |
| Baseline Profiles | AGP 8.x | Startup & runtime optimization | Generates AOT-compiled profiles for critical user journeys. Measurably improves cold start time and scrolling. Requires a separate `benchmark` module. | HIGH (concept), MEDIUM (setup details) |
| Macrobenchmark | 1.2.4+ | Performance benchmarking | Measures startup time, frame timing, scrolling performance on real devices. Use to validate that polish changes don't regress performance. | MEDIUM (verify version) |

### Code Coverage

| Technology | Version | Purpose | Why | Confidence |
|------------|---------|---------|-----|------------|
| JaCoCo | AGP built-in | Code coverage reports | Enable via `testCoverageEnabled = true` in debug build type. Set coverage thresholds in CI. Target 70% for util/, 50% for viewmodel/. | HIGH |

---

## What NOT to Use

| Technology | Why Not |
|------------|---------|
| JUnit 5 (Jupiter) | Adds build complexity on Android (requires extra plugins). JUnit 4 is fully supported, works with all AndroidX Test libs. No benefit for this project's scope. |
| Mockito / Mockito-Kotlin | Works, but MockK is more idiomatic for Kotlin. Mockito struggles with `suspend fun`, final classes (Kotlin default), and object declarations. |
| Hilt/Dagger for testing | App has no DI framework. Adding one just for testing is overkill. Construct ViewModels manually in tests. |
| Compose Testing | App uses Views + XML layouts, not Compose. Compose test artifacts are irrelevant. |
| Firebase Test Lab | Useful for multi-device testing but adds cloud dependency and cost. Use local emulators for this polish pass. Revisit for pre-release validation later. |
| Paparazzi | Older screenshot testing lib. Roborazzi is newer, more actively maintained, and better integrated with Robolectric. However, this is LOW confidence -- evaluate both if screenshot testing is pursued. |
| ktlint (standalone) | Detekt with `detekt-formatting` plugin covers ktlint rules plus much more. One tool instead of two. |
| Cucumber / BDD | Over-engineered for a single-developer project. Plain JUnit + Espresso is sufficient. |

---

## Dependency Configuration

```kotlin
// In app/build.gradle.kts

android {
    buildTypes {
        debug {
            // Enable code coverage for debug builds
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    testOptions {
        unitTests {
            // Include Android resources for Robolectric
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // === UNIT TESTING (src/test/) ===

    // JUnit 4 (already present)
    testImplementation("junit:junit:4.13.2")

    // MockK - Kotlin mocking
    testImplementation("io.mockk:mockk:1.13.12")

    // Coroutines testing - must match app version
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    // LiveData testing - InstantTaskExecutorRule
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    // Truth assertions
    testImplementation("com.google.truth:truth:1.4.2")

    // Robolectric - Android framework on JVM
    testImplementation("org.robolectric:robolectric:4.12.2")

    // AndroidX Test core (for Robolectric)
    testImplementation("androidx.test:core-ktx:1.6.1")

    // Turbine - Flow testing (optional, add when needed)
    // testImplementation("app.cash.turbine:turbine:1.1.0")

    // === INSTRUMENTED TESTING (src/androidTest/) ===

    // AndroidX Test
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")

    // Espresso
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.6.1")

    // Fragment testing
    debugImplementation("androidx.fragment:fragment-testing:1.8.1")

    // MockK for Android instrumented tests
    androidTestImplementation("io.mockk:mockk-android:1.13.12")

    // Truth for instrumented tests
    androidTestImplementation("com.google.truth:truth:1.4.2")

    // UI Automator (permission dialogs, share sheet)
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    // === DEBUG-ONLY ===

    // LeakCanary - memory leak detection
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
```

### Detekt Setup

```kotlin
// In project-level build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.6" apply false
}

// In app/build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.6")
}
```

### Android Lint Configuration

```xml
<!-- app/lint.xml -->
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <!-- Treat accessibility issues as errors -->
    <issue id="ContentDescription" severity="error" />
    <issue id="TouchTargetSizeCheck" severity="error" />

    <!-- Treat hardcoded text as error (forces strings.xml) -->
    <issue id="HardcodedText" severity="error" />

    <!-- Unused resources -->
    <issue id="UnusedResources" severity="warning" />

    <!-- Obsolete SDK checks -->
    <issue id="OldTargetApi" severity="error" />
</lint>
```

```kotlin
// In app/build.gradle.kts android {} block
lint {
    abortOnError = true
    warningsAsErrors = false
    xmlReport = true
    htmlReport = true
    lintConfig = file("lint.xml")
    baseline = file("lint-baseline.xml")  // Generate baseline first, then fix incrementally
}
```

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Mocking | MockK | Mockito-Kotlin | MockK handles Kotlin idioms (coroutines, sealed classes, objects, final classes) natively. Mockito requires extra config for final classes. |
| Assertions | Truth | AssertJ, Hamcrest | Truth is Google's standard for Android, consistent with AndroidX Test ecosystem. AssertJ is fine but adds another convention. |
| Static Analysis | Detekt | ktlint + Android Lint only | Detekt covers formatting (via plugin) plus complexity, code smells, coroutine anti-patterns. One tool instead of two. |
| Screenshot Testing | Roborazzi | Paparazzi | Both are viable. Roborazzi runs on Robolectric (same as unit tests), Paparazzi uses layoutlib. LOW confidence on this recommendation -- evaluate both. |
| Leak Detection | LeakCanary | Manual profiling | LeakCanary is automatic, catches leaks during development that manual profiling would miss. Zero-effort once installed. |
| Unit Testing Framework | JUnit 4 | JUnit 5 | JUnit 5 on Android requires `android-junit5` plugin and has quirks with AndroidX Test. JUnit 4 works everywhere with no friction. |

---

## Testing Strategy by Code Layer

This maps the recommended tools to the actual project structure.

| Layer | Files | Test Type | Key Tools |
|-------|-------|-----------|-----------|
| **ViewModel** | `ScannerViewModel.kt` | Unit (JVM) | JUnit 4, MockK, coroutines-test, arch core-testing (InstantTaskExecutorRule) |
| **Utilities** | `ImageProcessor.kt`, `PdfUtils.kt`, `PdfPageExtractor.kt` | Unit (JVM + Robolectric) | JUnit 4, Robolectric (for Bitmap, Context), Truth |
| **Preferences** | `AppPreferences.kt` | Unit (Robolectric) | JUnit 4, Robolectric (SharedPreferences) |
| **Sound/Animation** | `SoundManager.kt`, `AnimationHelper.kt` | Unit (Robolectric) | JUnit 4, Robolectric, MockK |
| **Document Scanner** | `DocumentScanner.kt` | Unit (MockK) | JUnit 4, MockK (mock ML Kit APIs) |
| **Fragments (UI)** | All 7 fragments | Instrumented | Espresso, Fragment Testing, Espresso Contrib (RecyclerView) |
| **Navigation** | Fragment transitions | Instrumented | Espresso, Navigation Testing |
| **Permissions** | Camera, Storage | Instrumented | UI Automator (GrantPermissionRule or manual grant) |
| **Sharing/Intents** | Share PDFs, import | Instrumented | Espresso Intents, UI Automator |

---

## Quality Gate Recommendations

| Gate | Tool | Threshold | When |
|------|------|-----------|------|
| Lint passes | Android Lint | Zero errors (warnings allowed via baseline) | Every build |
| Detekt passes | Detekt | Default rules, no suppression except documented | Every build |
| Unit tests pass | JUnit 4 + MockK | 100% pass rate | Every build |
| Code coverage | JaCoCo | 70% line coverage for util/, 50% for viewmodel/ | CI / pre-merge |
| No memory leaks | LeakCanary | Zero leaks in debug runs | Development |
| Instrumented tests pass | Espresso | 100% pass rate | Pre-release |
| Accessibility | Lint a11y checks | Zero contentDescription errors, 48dp touch targets | Every build |

---

## Version Confidence Notes

All versions listed are from training data with cutoff May 2025. The Android ecosystem moves quickly. Before adding to `build.gradle.kts`:

1. **Check Google Maven** (`https://maven.google.com`) for AndroidX libraries
2. **Check Maven Central** for MockK, Truth, LeakCanary, Detekt, Robolectric
3. **Specific items to verify:**
   - MockK 1.13.12 -- confirm latest on Maven Central
   - Robolectric 4.12.2 -- confirm latest stable
   - Espresso 3.6.1 / AndroidX Test 1.6.1 -- confirm latest on Google Maven
   - LeakCanary 2.14 -- confirm latest on Maven Central
   - Detekt 1.23.6 -- confirm latest Gradle plugin version
   - Truth 1.4.2 -- confirm latest on Maven Central

---

## Sources

- Android developer documentation (developer.android.com) -- HIGH confidence for architecture concepts
- Training data knowledge of AndroidX Test, Espresso, MockK, Robolectric ecosystems -- MEDIUM confidence for specific versions
- Project build.gradle.kts analysis -- HIGH confidence for current project state
- **WebSearch and WebFetch were unavailable** -- version numbers should be verified against Maven repositories before use
