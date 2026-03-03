---
phase: 04-test-coverage
plan: 05
subsystem: testing
tags: [fragment-testing, espresso, navigation-testing, instrumented-tests, smoke-tests, TestNavHostController]

# Dependency graph
requires:
  - phase: 04-01
    provides: "Test dependency scaffold including fragment-testing:1.8.9 (debugImplementation) and espresso-core:3.7.0"
provides:
  - "5 fragment smoke tests in androidTest/ui/ covering HomeFragment, PagesFragment, HistoryFragment, SettingsFragment, PreviewFragment"
  - "1 navigation flow test in androidTest/navigation/ verifying Home -> Camera nav action fires with correct destination"
  - "androidTest/AndroidManifest.xml with tools:overrideLibrary for mockk minSdk conflict"
  - "navigation-testing:2.7.6 dependency for TestNavHostController"
affects: []

# Tech tracking
tech-stack:
  added:
    - "androidx.navigation:navigation-testing:2.7.6 — TestNavHostController for navigation flow tests"
  patterns:
    - "Fragment smoke test pattern: launchFragmentInContainer<F>(themeResId = R.style.Theme_PDFScanner_Cartoon).use { scenario -> scenario.onFragment { assertThat(it.isResumed).isTrue() } }"
    - "NavArgs passing pattern: Bundle().apply { putString(\"imageUri\", \"file:///dev/null\"); putInt(\"editIndex\", -1) } passed as fragmentArgs to launchFragmentInContainer"
    - "TestNavHostController pattern: set graph + ViewModelStore before fragment launch, wire via Navigation.setViewNavController after onFragment callback"

key-files:
  created:
    - "app/src/androidTest/java/com/pdfscanner/app/ui/HomeFragmentTest.kt"
    - "app/src/androidTest/java/com/pdfscanner/app/ui/PagesFragmentTest.kt"
    - "app/src/androidTest/java/com/pdfscanner/app/ui/HistoryFragmentTest.kt"
    - "app/src/androidTest/java/com/pdfscanner/app/ui/SettingsFragmentTest.kt"
    - "app/src/androidTest/java/com/pdfscanner/app/ui/PreviewFragmentTest.kt"
    - "app/src/androidTest/java/com/pdfscanner/app/navigation/NavigationFlowTest.kt"
    - "app/src/androidTest/AndroidManifest.xml"
  modified:
    - "app/build.gradle.kts — added navigation-testing:2.7.6; removed incompatible mockk-android/mockk-agent"

key-decisions:
  - "mockk-android:1.14.7 and mockk-agent:1.14.7 are compiled with Kotlin 2.1.0 binary — incompatible with Kotlin 1.9.21; removed from androidTestImplementation since no instrumented test uses MockK"
  - "androidTest/AndroidManifest.xml created with tools:overrideLibrary for io.mockk.android and io.mockk.proxy.android to resolve minSdk 24 vs 26 manifest merger conflict (even after removal, kept for future re-addition)"
  - "PreviewFragment requires imageUri String navArg — passed as Bundle with putString(imageUri, file:///dev/null) so navArgs() does not throw MissingRequiredArgument exception"
  - "Navigation button confirmed as R.id.cardNewScan (MaterialCardView) from fragment_home.xml — NOT a Button or FAB; direct click via Espresso works on clickable CardView"
  - "Camera destination confirmed as R.id.cameraFragment from nav_graph.xml"
  - "App theme confirmed as Theme.PDFScanner.Cartoon (R.style.Theme_PDFScanner_Cartoon) from AndroidManifest.xml"

patterns-established:
  - "All fragment smoke tests use R.style.Theme_PDFScanner_Cartoon — prevents InflateException from Material3 components"
  - "CameraFragment explicitly excluded from all fragment scenario tests — instantiating it requires CameraX ProcessCameraProvider and crashes without real camera hardware"

requirements-completed: [TEST-07, TEST-08]

# Metrics
duration: 15min
completed: 2026-03-01
---

# Phase 4 Plan 05: Fragment Smoke Tests and Navigation Flow Summary

**6 instrumented test files: 5 fragment smoke tests (launchFragmentInContainer) + 1 navigation flow test (TestNavHostController Home->Camera) — code-complete, execution pending (WSL2 lacks device)**

## Performance

- **Duration:** 15 min
- **Started:** 2026-03-01T22:25:00Z
- **Completed:** 2026-03-01T22:40:00Z
- **Tasks:** 2
- **Files modified:** 9 (7 created, 2 modified)

## Accomplishments
- 5 fragment smoke tests written in `androidTest/ui/` covering all non-camera fragments
- 1 navigation flow test written using `TestNavHostController` — verifies `cardNewScan` click fires `action_home_to_camera` and lands at `cameraFragment` destination
- All 6 files compile successfully (`compileDebugAndroidTestKotlin` BUILD SUCCESSFUL)
- Layout XML and nav graph inspected to confirm actual view IDs (no guessed IDs)

## Task Commits

Each task was committed atomically:

1. **Task 1: Write 5 fragment smoke tests** - `b693121` (feat)
2. **Task 2: Write NavigationFlowTest** - `4558d04` (feat)

**Plan metadata:** (docs commit follows below)

## Files Created/Modified
- `app/src/androidTest/java/com/pdfscanner/app/ui/HomeFragmentTest.kt` - HomeFragment smoke test
- `app/src/androidTest/java/com/pdfscanner/app/ui/PagesFragmentTest.kt` - PagesFragment smoke test (empty-state path)
- `app/src/androidTest/java/com/pdfscanner/app/ui/HistoryFragmentTest.kt` - HistoryFragment smoke test
- `app/src/androidTest/java/com/pdfscanner/app/ui/SettingsFragmentTest.kt` - SettingsFragment smoke test
- `app/src/androidTest/java/com/pdfscanner/app/ui/PreviewFragmentTest.kt` - PreviewFragment smoke test (passes navArgs Bundle)
- `app/src/androidTest/java/com/pdfscanner/app/navigation/NavigationFlowTest.kt` - Navigation flow test Home -> Camera
- `app/src/androidTest/AndroidManifest.xml` - tools:overrideLibrary for mockk minSdk conflict
- `app/build.gradle.kts` - Added navigation-testing:2.7.6; removed incompatible mockk-android/mockk-agent

## Navigation Test Details

- **Button ID:** `R.id.cardNewScan` (MaterialCardView in `fragment_home.xml`)
- **Nav action:** `R.id.action_home_to_camera`
- **Destination ID:** `R.id.cameraFragment` (from `nav_graph.xml`)
- **Confirmed by:** Direct inspection of `app/src/main/res/layout/fragment_home.xml` and `app/src/main/res/navigation/nav_graph.xml`

## Execution Status

**Code-complete. Execution pending.**

These tests run in `androidTest/` and require a connected device or emulator:
```bash
./gradlew connectedDebugAndroidTest
```

WSL2 does not support USB passthrough for Android devices without additional configuration. The tests are ready to run on a host machine with Android Studio or a configured emulator.

## Decisions Made

- Used `R.style.Theme_PDFScanner_Cartoon` (not `R.style.Theme_PdfScanner`) — exact style name from `AndroidManifest.xml` and `res/values/themes_cartoon.xml`
- `PreviewFragment` requires `imageUri` and `editIndex` Bundle args — provided as `file:///dev/null` and `-1` respectively
- `mockk-android:1.14.7` removed from `androidTestImplementation` — compiled with Kotlin 2.1.0 binary, incompatible with Kotlin 1.9.21 project
- `navigation-testing:2.7.6` added to match Navigation component version already in use

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added androidTest/AndroidManifest.xml to resolve manifest merger failure**
- **Found during:** Task 1 (first compile attempt)
- **Issue:** `mockk-android:1.14.7` declares `minSdk 26`; app's `minSdk` is 24 — manifest merger fails
- **Fix:** Created `app/src/androidTest/AndroidManifest.xml` with `tools:overrideLibrary="io.mockk.android,io.mockk.proxy.android"`
- **Files modified:** `app/src/androidTest/AndroidManifest.xml` (new)
- **Verification:** Manifest merger succeeds, compile proceeds
- **Committed in:** b693121 (Task 1 commit)

**2. [Rule 3 - Blocking] Removed mockk-android/mockk-agent from androidTestImplementation**
- **Found during:** Task 1 (second compile attempt after manifest fix)
- **Issue:** `mockk-agent-android:1.14.7` module metadata declares Kotlin 2.1.0 binary — incompatible with Kotlin 1.9.21; compile fails with "Module was compiled with an incompatible version"
- **Fix:** Commented out `mockk-android:1.14.7` and `mockk-agent:1.14.7` from `androidTestImplementation` — no androidTest file uses MockK so removal is safe
- **Files modified:** `app/build.gradle.kts`
- **Verification:** `compileDebugAndroidTestKotlin` BUILD SUCCESSFUL
- **Committed in:** b693121 (Task 1 commit)

**3. [Rule 3 - Blocking] Added navigation-testing:2.7.6 dependency**
- **Found during:** Task 2 (NavigationFlowTest requires TestNavHostController)
- **Issue:** `TestNavHostController` from `androidx.navigation:navigation-testing` was not in build.gradle.kts
- **Fix:** Added `androidTestImplementation("androidx.navigation:navigation-testing:2.7.6")`
- **Files modified:** `app/build.gradle.kts`
- **Verification:** Import resolves, compile succeeds
- **Committed in:** b693121 (Task 1 commit, alongside all infra changes)

---

**Total deviations:** 3 auto-fixed (3 blocking)
**Impact on plan:** All auto-fixes required for compilation. No scope creep. Fragment test logic unchanged from plan.

## Issues Encountered

- `mockk-android:1.14.7` was added in 04-01 anticipating future androidTest use but is Kotlin 2.x binary — cannot coexist with Kotlin 1.9.21 project. Documented here as resolved by removal.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- All 6 instrumented test files are code-complete and ready to run on a connected device
- To execute: `./gradlew connectedDebugAndroidTest` (requires device or emulator)
- Phase 4 is now complete (5/5 plans done)

## Self-Check: PASSED

All 6 test files exist at expected paths. Both task commits (b693121, 4558d04) confirmed in git log.

---
*Phase: 04-test-coverage*
*Completed: 2026-03-01*
