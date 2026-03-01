# Project Research Summary

**Project:** PDF Scanner -- v1.1 Testing & Release Pass
**Domain:** Android testing infrastructure + static analysis + release tooling
**Researched:** 2026-03-01
**Confidence:** HIGH

## Executive Summary

This milestone adds a complete testing foundation and Play Store release readiness layer to a feature-complete Android document scanner. The app is already built on a solid MVVM + Single Activity + Navigation Component + CameraX + ML Kit stack -- no architectural changes are needed. The work is exclusively additive: write tests for existing code, add static analysis tooling, fix manifest and ProGuard gaps, and verify the release build on a real device. The recommended approach is to tackle testing first (Phase 4 in two internal waves: JVM unit tests, then Robolectric and integration tests), then release hardening (Phase 5), with a mandatory real-device E2E gate before any Play Store submission.

The primary risk is environment fragmentation: some test categories (CameraX, ML Kit, PdfUtils with PdfRenderer) cannot run on the JVM and require a physical device or emulator. This is not a design problem -- it is a well-understood Android testing boundary. The research identifies exactly which components fall into which test environment, enabling the roadmap to assign tests to the correct phase and environment without surprises. A secondary risk is ProGuard/R8 stripping ML Kit and Navigation SafeArgs classes in release builds -- this is a known failure mode with a documented fix that must be applied and verified on a real device before submission.

The testing stack is straightforward: MockK for Kotlin-native mocking, Robolectric for JVM-based Android class access, JUnit 4 throughout (JUnit 5 introduces instrumentation friction with no benefit here), Espresso plus FragmentScenario for UI tests, and JaCoCo via AGP built-in for coverage reporting. Detekt with a baseline file handles static analysis without blocking the initial rollout. All version selections are verified stable as of March 2026 with HIGH confidence against official release channels.

## Key Findings

### Recommended Stack

The existing production stack (CameraX 1.3.1, Coil 2.7.0, ML Kit, Navigation 2.7.x, Material 3, Kotlin 1.9.21, Coroutines 1.7.3) needs no changes. All additions are in the test and debug configuration only -- zero production APK impact.

**Core technologies:**
- MockK 1.14.7: Kotlin-first mocking -- handles `suspend fun`, final classes (Kotlin default), sealed classes, and coroutines natively; Mockito cannot do this without painful workarounds
- kotlinx-coroutines-test 1.7.3: Coroutine test dispatchers -- MUST match the app's coroutines version exactly; version mismatch causes `NoSuchMethodError` at runtime
- androidx.arch.core:core-testing 2.2.0: Provides `InstantTaskExecutorRule` -- makes LiveData post synchronously in JVM unit tests; without it every `liveData.value` assertion returns null
- Robolectric 4.16: Android API access on JVM -- enables testing Bitmap, SharedPreferences, and Context-dependent code without a device; supports compileSdk 35/36
- Espresso 3.7.0 + espresso-intents + espresso-contrib: UI interaction testing -- industry standard, auto-synchronizes with UI thread; intents artifact verifies FileProvider share flows; contrib adds RecyclerView actions
- androidx.fragment:fragment-testing 1.8.9: `launchFragmentInContainer<T>()` -- tests each fragment in isolation without launching the full Activity; requires `debugImplementation` for manifest merge
- LeakCanary 2.14: Automatic memory leak detection in debug builds -- zero production APK impact, installs via ContentProvider, catches Activity/Fragment/ViewModel leaks during manual testing
- Detekt 1.23.8 + detekt-formatting: Kotlin static analysis -- 1.23.x is the correct series for Kotlin 1.9.21; Detekt 2.x is alpha and incompatible with Kotlin 1.9
- JaCoCo: Coverage reporting via AGP built-in (`enableUnitTestCoverage = true`) -- no third-party plugin needed for a single-module project

**Critical version constraints:**
- Detekt must stay at 1.23.x -- Detekt 2.x targets Kotlin 2.x and will fail to compile with Kotlin 1.9.21
- coroutines-test must match coroutines-android version exactly (both 1.7.3)
- MockK Android instrumented tests require two artifacts: `mockk-android` + `mockk-agent`; the JVM-only `mockk` artifact fails silently on device

See `.planning/research/STACK.md` for the authoritative `app/build.gradle.kts` diff with all dependency declarations.

### Expected Features

This milestone has no product features -- the app is feature-complete. Every task is a testing or release gate.

**Must have -- table stakes (P1):**
- TEST-01: Test dependency scaffold -- blocks all other tests; must be Day 1 of Phase 4
- TEST-02: ScannerViewModel unit tests (15+ tests) -- core business logic is currently entirely untested
- TEST-03: DocumentEntry JSON round-trip -- silent serialization regression equals user data corruption
- RELEASE-02: Android Lint with accessibility errors promoted to errors -- required for any quality release
- RELEASE-03: ProGuard/R8 keep rules for ML Kit and Navigation SafeArgs -- known to cause release-build crashes without these
- RELEASE-04: Release APK E2E on a real device -- unverified release build is an unknown quantity (ENVIRONMENT-BLOCKED: requires host machine with Android Studio, not WSL2)
- RELEASE-05: Camera `uses-feature required="false"` -- without this, tablets and Chromebooks cannot install the app from Play Store
- RELEASE-06: `dataExtractionRules` + `fullBackupContent` excluding private scan files -- private file paths backed up to Google and restored to a different device path are broken
- RELEASE-07: FileProvider scope tightened -- current `cache-path path="/"` is overly broad and exposes all cached files

**Should have -- differentiators (P2):**
- TEST-04: ImageProcessor filter tests via Robolectric (8+ tests) -- core image algorithm coverage
- TEST-05: DocumentHistoryRepository CRUD via Robolectric (8+ tests) -- production persistence layer
- TEST-07: Fragment smoke tests (5+ fragments) -- catches layout inflation errors and View Binding NPEs
- TEST-08: Navigation flow test (Camera -> Preview -> Pages -> PDF) -- happy path verification
- RELEASE-01: Detekt with baseline -- zero new blocking errors
- RELEASE-08: LeakCanary debug build -- zero retained Activity/Fragment leaks
- RELEASE-09: JaCoCo coverage reporting -- 70% line coverage for `util/`, 50% for `viewmodel/`

**Defer to v2+:**
- TEST-06: PdfUtils instrumented tests -- HIGH complexity; PdfRenderer requires device/emulator; environment uncertainty makes this a scope risk for v1.1
- Screenshot regression tests -- explicitly out of scope per PROJECT.md; tooling confidence is LOW
- CI/CD pipeline -- not in v1.1 scope
- JaCoCo hard enforcement gate -- add only after coverage is established and stable for several milestones

See `.planning/research/FEATURES.md` for the full prioritization matrix and Play Store submission reality check.

### Architecture Approach

The test architecture follows the standard Android test pyramid: approximately 60% JVM unit tests covering ViewModels, data models, and utility pure logic; approximately 30% integration and instrumented tests covering Fragments, Navigation, and file I/O; and approximately 10% manual E2E covering camera, ML Kit, and full flows on device. The existing codebase needs zero structural refactoring to achieve the core test targets -- `ScannerViewModel`, `DocumentEntry`, and most utility logic are testable as-is. The only structural gap is that ML Kit calls within `ImageProcessor` must be wrapped behind an `OcrProcessor` interface so unit tests can inject a `FakeOcrProcessor` instead of invoking the native TFLite runtime (which crashes on JVM).

**Major test component boundaries:**
1. JVM unit tests (src/test/) -- ScannerViewModel, DocumentEntry, pure utility logic; runs on any machine including WSL2
2. Robolectric unit tests (src/test/ with @RunWith annotation) -- ImageProcessor (Bitmap), DocumentHistoryRepository (SharedPreferences); runs on JVM with Android framework stubs
3. Instrumented tests (src/androidTest/) -- PdfUtils (PdfRenderer), Fragment smoke tests, Navigation flow; requires emulator or physical device
4. Static analysis and lint -- Detekt + Android Lint; runs on any machine, no device required
5. Manual E2E -- camera capture, ML Kit OCR, share PDF; requires physical device on host machine

**Build configuration changes required:**
- `app/build.gradle.kts`: Add all test dependencies; `enableUnitTestCoverage = true`; custom `jacocoTestReport` task with generated-class exclusions; Detekt plugin; lint block
- Root `build.gradle.kts`: Add `io.gitlab.arturbosch.detekt` plugin declaration
- New files: `app/lint.xml`, `app/detekt.yml`, `app/detekt-baseline.xml` (generated), additions to `app/proguard-rules.pro`
- `AndroidManifest.xml`: `uses-feature required="false"`, `dataExtractionRules`, `fullBackupContent` attributes

See `.planning/research/ARCHITECTURE.md` for the complete test directory structure, all code-level patterns with examples, and the full JaCoCo task configuration.

### Critical Pitfalls

Ten pitfalls were identified. The five most likely to cause wasted work or silent failures:

1. **JaCoCo counter type ambiguity** -- "70% coverage" is meaningless without specifying LINE vs BRANCH vs INSTRUCTION. Kotlin coroutines inflate the BRANCH counter by 15+ percentage points due to synthetic state-machine branches. Prevention: configure `jacocoTestCoverageVerification` with `counter = "LINE"` explicitly before writing a single test. Also add generated-class exclusions for `R`, `BuildConfig`, `*Args`, `*Directions`, and `*Binding` classes or coverage numbers will be misleadingly low.

2. **CameraX and ML Kit are incompatible with Robolectric** -- Any test that instantiates `ProcessCameraProvider` or calls ML Kit APIs in a Robolectric context will crash with `UnsatisfiedLinkError` because native `.so` libraries cannot be loaded on JVM. Prevention: CameraFragment tests must be instrumented (androidTest), never Robolectric. Wrap ML Kit behind an `OcrProcessor` interface and inject `FakeOcrProcessor` in all unit tests.

3. **Missing ProGuard rules cause silent release-build crashes** -- ML Kit and Navigation SafeArgs classes are stripped by R8 in release builds. The crash does not appear in debug builds because debug skips R8. Prevention: add explicit keep rules for `com.google.mlkit.**`, native JNI methods, and `**.*Args`/`**.*Directions` Safe Args classes. Verify by installing the release APK on a physical device before submission.

4. **`InstantTaskExecutorRule` missing causes all LiveData assertions to return null** -- Without this rule, `LiveData.setValue()` fails silently or throws `getMainLooper not mocked` on JVM. Prevention: add `@get:Rule val instantExecutorRule = InstantTaskExecutorRule()` to every ViewModel test class; this is a prerequisite for TEST-02.

5. **Deprecated coroutine test API** -- Online tutorials still use deprecated `TestCoroutineDispatcher` and `runBlockingTest` (both deprecated since coroutines-test 1.6). These cause test-ordering bugs that pass locally but fail under different conditions. Prevention: use `UnconfinedTestDispatcher` + `runTest` from the start; do not copy older patterns.

Additional pitfall worth flagging: **LeakCanary will fire for a known Navigation 2.7.x library bug** (`AbstractAppBarOnDestinationChangedListener` holding a Context reference). This is not app code. Prevention: triage it immediately as a library leak to avoid wasted investigation. Fix options: upgrade to Navigation 2.8+, manually remove the listener in `onDestroyView()`, or add a named exclusion to LeakCanary configuration.

See `.planning/research/PITFALLS.md` for all 10 pitfalls with code samples, recovery costs, and a "Looks Done But Isn't" checklist.

## Implications for Roadmap

The research maps directly onto two ordered phases. Within each phase, task order is constrained by explicit dependencies identified in the research.

### Phase 4: Test Coverage

**Rationale:** All tests depend on TEST-01 (dependency scaffold), so that must be the first task. JVM unit tests (TEST-02, TEST-03) can run in WSL2 immediately after scaffold setup -- no device needed. Robolectric tests (TEST-04, TEST-05) come next and share the Robolectric setup cost. Fragment and navigation tests (TEST-07, TEST-08) are the final wave because they require instrumented infrastructure and have the highest implementation complexity. JaCoCo configuration (RELEASE-09) should be wired in alongside the first tests, not retrofitted at the end.
**Delivers:** A test suite with 37+ tests covering ViewModel business logic, data persistence, JSON serialization, and image processing; a JaCoCo coverage report at the target thresholds.
**Addresses:** TEST-01, TEST-02, TEST-03, TEST-04, TEST-05, RELEASE-09 (core); TEST-07, TEST-08 (stretch)
**Avoids:**
- Configure JaCoCo counter type and generated-class exclusions before the first test run (Pitfalls 1 and 2)
- Use `UnconfinedTestDispatcher` + `runTest` from the start; never use `TestCoroutineDispatcher` (Pitfall 8)
- Add `InstantTaskExecutorRule` to every ViewModel test class (Pitfall 9)
- Do NOT use Robolectric for anything touching CameraX or ML Kit native APIs (Pitfalls 3 and 4)
- Establish the `OcrProcessor` interface boundary before writing TEST-04 (Pitfall 4)

**Recommended internal order:**
1. TEST-01: Add all test dependencies to build.gradle.kts + configure JaCoCo task with exclusions + establish MainDispatcherRule
2. TEST-02: ScannerViewModel unit tests (JVM, MockK, InstantTaskExecutorRule, coroutines-test)
3. TEST-03: DocumentEntry JSON round-trip (pure JVM, no Android dependencies)
4. TEST-04: ImageProcessor Robolectric tests (Robolectric for Bitmap; fake OcrProcessor for ML Kit boundary)
5. TEST-05: DocumentHistoryRepository Robolectric CRUD (Robolectric for SharedPreferences)
6. RELEASE-09: Run coverage report; verify exclusions are correct; document threshold baseline
7. (Stretch) TEST-07: Fragment smoke tests via FragmentScenario on device/emulator
8. (Stretch) TEST-08: Navigation flow test with TestNavHostController

### Phase 5: Release Readiness

**Rationale:** Release hardening tasks are mostly independent of each other and of the test phase. ProGuard rules (RELEASE-03) must precede the real-device E2E verification (RELEASE-04) -- a broken release build wastes device testing time. The Detekt baseline (RELEASE-01) must be generated from the unmodified codebase before any fixes; generate it as Phase 5's very first action. LeakCanary (RELEASE-08) should be installed early in the phase to catch binding leaks during manual testing of the other tasks.
**Delivers:** A release-ready APK verified on a physical device with clean Lint, Detekt, ProGuard, backup rules, and manifest configuration.
**Addresses:** RELEASE-01, RELEASE-02, RELEASE-03, RELEASE-04, RELEASE-05, RELEASE-06, RELEASE-07, RELEASE-08
**Avoids:**
- Generate Detekt baseline exactly once from the completely unmodified codebase; commit immediately (Pitfall 10)
- Triage the Navigation 2.7.x LeakCanary false positive on first encounter; do not investigate as app code (Pitfall 6)
- Audit all 8 fragments for `_binding = null` in `onDestroyView()` before the LeakCanary run (Pitfall 7)
- Test release APK on a physical device -- ProGuard bugs cannot be caught by unit tests (Pitfall 5)

**Recommended internal order:**
1. RELEASE-01: Generate Detekt baseline from unmodified codebase; commit `detekt-baseline.xml` immediately
2. RELEASE-08: Add LeakCanary + audit all 8 fragments for binding nullification pattern
3. RELEASE-02: Create `lint.xml` with accessibility checks as errors + wire into `build.gradle.kts`
4. RELEASE-05: Fix `uses-feature required="false"` in AndroidManifest.xml
5. RELEASE-06: Add `dataExtractionRules` + `fullBackupContent` to exclude private scan/processed/pdf directories
6. RELEASE-07: Tighten FileProvider paths in `file_paths.xml` to only actually-used subdirectories
7. RELEASE-03: Complete ProGuard/R8 keep rules for ML Kit, Navigation SafeArgs, Coil, coroutines
8. RELEASE-04: Build release APK; install on physical device; exercise every screen and feature path (ENVIRONMENT-BLOCKED: host machine with Android Studio required)

### Phase Ordering Rationale

- Tests before release hardening because: tests may surface bugs that are easier to fix before release build configuration hardens; RELEASE-09 (JaCoCo) has no meaning without tests existing first; LeakCanary findings during Phase 5 may reveal binding leaks that fragment smoke tests in Phase 4 would have caught earlier.
- TEST-01 is the universal blocker for Phase 4 -- no test can be written until the dependency scaffold is in `build.gradle.kts`.
- RELEASE-04 is the terminal gate for Phase 5 -- it cannot happen until RELEASE-03 is complete AND requires a host machine environment that WSL2 cannot provide.
- TEST-06 (PdfUtils instrumented tests) is deliberately deferred to v2+ due to HIGH implementation complexity (PdfRenderer requires real device/emulator, not Robolectric) and environment uncertainty relative to the v1.1 scope.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 4, TEST-08 (Navigation flow test):** Coordinating a CameraX mock plus TestNavHostController plus ViewModel state in a single integration test is HIGH complexity. The pattern is documented, but the specific interaction with this app's 8-fragment navigation graph needs careful scoping during task creation. Consider reducing scope to testing navigation action firing without exercising actual camera capture.
- **Phase 5, RELEASE-04 (Release APK E2E):** This task is environment-blocked and requires a separate planning note documenting the exact host machine steps (Android Studio setup, device connection, ADB triage commands). The `adb logcat | grep -E "(ClassNotFound|NoSuchMethod|UnsatisfiedLink)"` pattern from PITFALLS.md should be included in the task specification.

Phases with well-documented patterns that can skip research-phase:
- **Phase 4, TEST-01 through TEST-05:** Exact dependency versions, complete `build.gradle.kts` diffs, and concrete code examples are fully specified in STACK.md and ARCHITECTURE.md. No additional research is needed before implementation.
- **Phase 5, RELEASE-01 through RELEASE-03, RELEASE-05 through RELEASE-08:** All patterns are fully specified with exact XML and Kotlin code in STACK.md, FEATURES.md, and PITFALLS.md. These are mechanical implementation tasks.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All versions web-verified March 2026 against GitHub releases, Maven Central, and official Android documentation. Compatibility matrix explicitly documented in STACK.md. |
| Features | HIGH | Based on direct codebase inspection of AndroidManifest.xml, proguard-rules.pro, build.gradle.kts, and all source files. Tasks derived from PROJECT.md with no guesswork. |
| Architecture | HIGH | Test patterns are established Android community standards per official docs and AndroidX test libraries. Test-to-component mapping derived from actual codebase analysis. |
| Pitfalls | HIGH for Kotlin, JaCoCo, MockK, ProGuard (multi-source verified); MEDIUM for CameraX and ML Kit test environment behavior (fewer authoritative sources available). |

**Overall confidence:** HIGH

### Gaps to Address

- **RELEASE-04 environment dependency:** The release APK E2E test requires the host machine with Android Studio and a connected physical device. WSL2 cannot run `./gradlew assembleRelease` with USB device connectivity. This is the highest-risk item in the milestone: if the host machine is unavailable or misconfigured, Phase 5 cannot complete. Mitigation: document the host machine requirements explicitly in the RELEASE-04 task specification and identify a concrete test device before Phase 5 begins.

- **TEST-06 PdfUtils instrumented tests:** PdfRenderer behavior on emulator versus physical device can differ. If deferred to v2+, document which PDF operations (merge, split, compress) are currently unverified in the codebase as a known gap.

- **Navigation 2.7.x vs 2.8.x upgrade decision:** The LeakCanary pitfall for `AbstractAppBarOnDestinationChangedListener` can be resolved by upgrading Navigation to 2.8+. The research did not determine whether Navigation 2.8.x is a drop-in upgrade for this app's navigation graph and SafeArgs configuration. If Phase 5 selects the upgrade path over the manual listener removal workaround, that compatibility should be validated first.

- **OcrProcessor interface boundary:** PITFALLS.md recommends wrapping ML Kit behind an `OcrProcessor` interface to enable unit test mocking. ARCHITECTURE.md shows `ocr/OcrProcessor.kt` may already exist as a class. Verify the current interface boundary at the start of Phase 4 -- this determines whether TEST-04 requires a refactoring step before test writing can begin.

## Sources

### Primary (HIGH confidence)
- MockK releases -- GitHub (version 1.14.7 confirmed latest stable, March 2026)
- Robolectric releases -- GitHub (4.16 confirmed, SDK 35/36 support)
- AndroidX Test releases -- developer.android.com (Espresso 3.7.0, junit-ktx 1.3.0, July 2025 stable)
- AndroidX Fragment releases -- developer.android.com (fragment-testing 1.8.9, February 2026 stable)
- LeakCanary changelog -- square.github.io (2.14 latest stable; 3.0 alpha only as of March 2026)
- Detekt releases -- GitHub (1.23.8, February 2025; 1.23.x is the Kotlin 1.9 compatible series)
- JaCoCo coverage counters -- eclemma.org official documentation
- JaCoCo coroutine branch inflation -- GitHub issues #1045 and #1353
- Navigation 2.7.x library leak -- LeakCanary GitHub issue #2566
- Fragment ViewBinding leak -- LeakCanary GitHub issue #2341
- ML Kit known issues -- developers.google.com/ml-kit/known-issues (ProGuard and AGP 7.0+)
- Android Developers: Local Unit Tests, Test Navigation, Auto Backup, Lint -- official guidance
- Google Play: Target API requirements and closed testing requirements -- official policy
- Testing Kotlin coroutines on Android -- official Android developer documentation
- TestCoroutineDispatcher migration guide -- kotlinx.coroutines GitHub repository
- Direct codebase inspection -- AndroidManifest.xml, build.gradle.kts, proguard-rules.pro, file_paths.xml, ScannerViewModel.kt, DocumentHistory.kt, ImageProcessor.kt, PdfUtils.kt (all read directly)

### Secondary (MEDIUM confidence)
- ProGuard rules for Navigation SafeArgs -- community blog (koral.dev), multi-source corroborated pattern
- ML Kit ProGuard issue -- googlesamples/mlkit GitHub issue #213
- Robolectric native library UnsatisfiedLinkError -- robolectric/robolectric GitHub issue #9099

---
*Research completed: 2026-03-01*
*Ready for roadmap: yes*
