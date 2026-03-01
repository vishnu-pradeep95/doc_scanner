---
phase: 04-test-coverage
plan: 01
subsystem: testing
tags: [jacoco, mockk, robolectric, coroutines-test, truth, espresso, android-testing, kotlin-1.9]

# Dependency graph
requires: []
provides:
  - "JaCoCo coverage task (jacocoTestReport) with LINE counter and generated-class exclusions"
  - "MainDispatcherRule.kt shared test utility for coroutine-based ViewModel tests"
  - "Full test dependency scaffold: mockk, robolectric, coroutines-test, core-testing, truth, espresso 3.7.0"
  - "enableUnitTestCoverage = true in debug build type"
  - "Robolectric isIncludeAndroidResources = true for XML resource access"
  - "Test directory skeleton for data/, util/, androidTest/ui/, androidTest/navigation/"
affects: [04-02, 04-03, 04-04, 04-05]

# Tech tracking
tech-stack:
  added:
    - "io.mockk:mockk:1.14.7 — unit test mocking"
    - "io.mockk:mockk-android:1.14.7 — instrumented test mocking"
    - "io.mockk:mockk-agent:1.14.7 — mockk byte buddy agent"
    - "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3 — coroutine test utilities"
    - "androidx.arch.core:core-testing:2.2.0 — InstantTaskExecutorRule for LiveData"
    - "org.robolectric:robolectric:4.16 — Android API simulation on JVM"
    - "androidx.test:core-ktx:1.6.1 — AndroidX test core utilities"
    - "com.google.truth:truth:1.4.4 — fluent assertion library"
    - "androidx.test.ext:junit-ktx:1.3.0 — AndroidX JUnit extensions"
    - "androidx.test:runner:1.6.2 — instrumented test runner"
    - "androidx.test:rules:1.6.1 — instrumented test rules"
    - "androidx.test.espresso:espresso-core:3.7.0 — UI test framework"
    - "androidx.test.espresso:espresso-intents:3.7.0 — intent testing"
    - "androidx.test.espresso:espresso-contrib:3.7.0 — RecyclerView and accessibility extras"
    - "androidx.fragment:fragment-testing:1.8.9 — fragment isolation testing (debug only)"
  patterns:
    - "JaCoCo with LINE counter (not BRANCH) — avoids 15-25% inflation from Kotlin coroutine synthetic branches"
    - "MainDispatcherRule pattern — TestWatcher replaces Dispatchers.Main with UnconfinedTestDispatcher for duration of each test"
    - "configurations.all { resolutionStrategy { force() } } — pinning coroutines to 1.7.3 to prevent BOM-driven upgrade to Kotlin 2.x incompatible versions"

key-files:
  created:
    - "app/src/test/java/com/pdfscanner/app/viewmodel/MainDispatcherRule.kt"
    - "app/src/test/java/com/pdfscanner/app/data/.gitkeep"
    - "app/src/test/java/com/pdfscanner/app/util/.gitkeep"
    - "app/src/androidTest/java/com/pdfscanner/app/ui/.gitkeep"
    - "app/src/androidTest/java/com/pdfscanner/app/navigation/.gitkeep"
  modified:
    - "app/build.gradle.kts — test deps, JaCoCo task, coverage flags, testOptions, resolution strategy"
    - "app/src/main/java/com/pdfscanner/app/MainActivity.kt — fixed WindowCompat API (pre-existing bug)"

key-decisions:
  - "Force coroutines to 1.7.3 via configurations.all resolutionStrategy: mockk and Robolectric pull in kotlinx-coroutines BOM that upgrades to 1.10.1 (Kotlin 2.1.0 binary), incompatible with project's Kotlin 1.9.21"
  - "Also force kotlin-stdlib to 1.9.21 to prevent transitive Kotlin 2.x stdlib from entering classpath"
  - "JaCoCo uses LINE counter (not BRANCH) per STATE.md locked decision — coroutines inflate BRANCH by 15-25%"
  - "MainDispatcherRule uses UnconfinedTestDispatcher (NOT deprecated TestCoroutineDispatcher)"

patterns-established:
  - "MainDispatcherRule: use @get:Rule val mainDispatcherRule = MainDispatcherRule() in every ViewModel test"
  - "JaCoCo report path: app/build/reports/jacoco/jacocoTestReport/html/index.html (run ./gradlew testDebugUnitTest jacocoTestReport)"
  - "AGP native JaCoCo: enableUnitTestCoverage=true in debug build type — no third-party JaCoCo plugin needed"

requirements-completed: [TEST-01, RELEASE-09]

# Metrics
duration: 6min
completed: 2026-03-01
---

# Phase 4 Plan 01: Test Infrastructure Setup Summary

**Full test scaffold with JaCoCo LINE-counter coverage, MainDispatcherRule coroutine helper, and forced coroutines 1.7.3 pinning to prevent Kotlin 2.x BOM conflicts from mockk and Robolectric**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-01T22:18:22Z
- **Completed:** 2026-03-01T22:24:Z
- **Tasks:** 2
- **Files modified:** 7 (5 created, 2 modified)

## Accomplishments

- Added 15 test/instrumented-test dependencies covering mocking (mockk), coroutine testing, Robolectric, truth assertions, and full Espresso 3.7.0 suite
- Configured JaCoCo `jacocoTestReport` task with LINE counter, generated-class exclusions (R, BuildConfig, Binding, SafeArgs), and correct exec file path
- Created `MainDispatcherRule.kt` using `UnconfinedTestDispatcher` — ready to import with `@get:Rule` in all ViewModel tests
- Created test directory skeleton for all packages subsequent plans will use

## Task Commits

Each task was committed atomically:

1. **Task 1: Add test dependencies + coverage config to build.gradle.kts** - `86b7618` (feat)
2. **Task 2: Create MainDispatcherRule shared test utility** - `a3d2678` (feat)

## Files Created/Modified

- `app/build.gradle.kts` — Added JacocoReport import, enableUnitTestCoverage/enableAndroidTestCoverage in debug block, testOptions with isIncludeAndroidResources=true, full test dep scaffold (15 deps), jacocoTestReport task with LINE counter, configurations.all resolution strategy
- `app/src/test/java/com/pdfscanner/app/viewmodel/MainDispatcherRule.kt` — JUnit TestWatcher replacing Dispatchers.Main for coroutine tests
- `app/src/test/java/com/pdfscanner/app/data/.gitkeep` — placeholder for upcoming DocumentEntry/DocumentHistoryRepository tests
- `app/src/test/java/com/pdfscanner/app/util/.gitkeep` — placeholder for upcoming ImageProcessor tests
- `app/src/androidTest/java/com/pdfscanner/app/ui/.gitkeep` — placeholder for fragment tests
- `app/src/androidTest/java/com/pdfscanner/app/navigation/.gitkeep` — placeholder for navigation flow tests
- `app/src/main/java/com/pdfscanner/app/MainActivity.kt` — Bug fix: WindowCompat.enableEdgeToEdge() replaced with setDecorFitsSystemWindows(window, false)

## Decisions Made

- Force coroutines 1.7.3 across all configurations via `configurations.all { resolutionStrategy { force() } }` — this is necessary because mockk 1.14.7 and Robolectric 4.16 pull in a `kotlinx-coroutines-bom:1.10.1` which is compiled with Kotlin 2.1.0 metadata; the project uses Kotlin 1.9.21 which can only read metadata up to 2.0.0.
- Force `kotlin-stdlib:1.9.21` for the same reason — prevents `kotlin-stdlib:2.1.20` from entering the classpath.
- JaCoCo uses LINE counter (locked decision from STATE.md): Kotlin coroutines generate synthetic BRANCH nodes that inflate coverage by 15-25%.
- JacocoReport import added at top of file (before plugins {}): required for Kotlin DSL type inference of `tasks.register<JacocoReport>`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed pre-existing WindowCompat.enableEdgeToEdge() unresolved reference**
- **Found during:** Task 1 (compiling after build.gradle.kts changes)
- **Issue:** `MainActivity.kt:79` called `WindowCompat.enableEdgeToEdge(window)` — this method does not exist on `WindowCompat`. Build was already failing before this plan's changes.
- **Fix:** Replaced with `WindowCompat.setDecorFitsSystemWindows(window, false)` which is the correct API for edge-to-edge layout in core-ktx 1.12.0.
- **Files modified:** `app/src/main/java/com/pdfscanner/app/MainActivity.kt`
- **Verification:** `./gradlew :app:compileDebugKotlin` passes with warnings only.
- **Committed in:** `86b7618` (Task 1 commit)

**2. [Rule 3 - Blocking] Added configurations.all resolutionStrategy to force coroutines 1.7.3**
- **Found during:** Task 2 (compileDebugUnitTestKotlin after creating MainDispatcherRule.kt)
- **Issue:** `kotlinx-coroutines-test:1.7.3` was being upgraded to `1.10.1` (via BOM constraint from mockk/Robolectric transitive deps). Version 1.10.1 is compiled with Kotlin 2.1.0 binary metadata, incompatible with Kotlin 1.9.21 compiler (reads metadata up to 2.0.0). This caused Unresolved reference errors for `setMain`, `resetMain`, `UnconfinedTestDispatcher`.
- **Fix:** Added `configurations.all { resolutionStrategy { force(...) } }` to force `kotlinx-coroutines-*:1.7.3` and `kotlin-stdlib:1.9.21` across all configurations.
- **Files modified:** `app/build.gradle.kts`
- **Verification:** `./gradlew :app:compileDebugUnitTestKotlin` passes with no errors.
- **Committed in:** `a3d2678` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 pre-existing bug, 1 blocking dependency conflict)
**Impact on plan:** Both auto-fixes were required for compilation to succeed. No scope creep.

## Issues Encountered

- mockk 1.14.7 and Robolectric 4.16 transitively pull in `kotlinx-coroutines-bom:1.10.1`, which forces all coroutines modules to 1.10.1 (Kotlin 2.1.0 binary). This is invisible until test sources using coroutines-test APIs try to compile. Resolution: forced pinning via `configurations.all`.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- Test infrastructure is complete. All subsequent Phase 4 plans (04-02 through 04-05) can now write and compile unit tests.
- `./gradlew testDebugUnitTest jacocoTestReport` will produce coverage once tests are written.
- JaCoCo output path: `app/build/reports/jacoco/jacocoTestReport/html/index.html`
- MainDispatcherRule available at `com.pdfscanner.app.viewmodel.MainDispatcherRule`
- Blocker noted in STATE.md: WSL2 requires JDK installed for `./gradlew assembleRelease`. Unit tests CAN run once any test file exists.

---
*Phase: 04-test-coverage*
*Completed: 2026-03-01*
