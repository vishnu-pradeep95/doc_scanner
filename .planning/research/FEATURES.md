# Feature Research: Testing & Release Readiness

**Domain:** Android Document Scanner App — Test Coverage + Play Store Release (v1.1)
**Researched:** 2026-03-01
**Confidence:** HIGH (official Android docs, direct codebase inspection, current web sources)

## Context

This is NOT a "what features to add" research. The app is feature-complete. This research answers: **what testing and release gate capabilities are non-negotiable vs. deferrable for a Kotlin/MVVM Android document scanner aiming for Play Store + portfolio quality?**

Every item below is a testing or release readiness task, not a product feature. Items are drawn directly from PROJECT.md (TEST-01 through RELEASE-09), then classified by actual necessity vs. gold-plating.

The existing test infrastructure is minimal boilerplate only — JUnit 4 + Espresso Core, no tests written. The existing release config has R8/ProGuard enabled, but the rules file covers only CameraX and CanHub; it is missing ML Kit, Navigation SafeArgs, and several other library keep-rules.

---

## Feature Landscape

### Table Stakes (Non-Negotiable for Any Quality Android App)

These are what a reviewer, interviewer, or technical user expects to see. Missing these signals that the app is not release-ready.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| **Test dependency setup** (TEST-01) | No tests can exist without the testing scaffold. All other test tasks depend on this. | LOW | Add: MockK, kotlinx-coroutines-test, Robolectric, AndroidX Arch Core testing (InstantTaskExecutorRule), FragmentScenario, Espresso Contrib/Intents. The existing JUnit 4 + Espresso Core is bare minimum and insufficient. |
| **ViewModel unit tests** (TEST-02) | ScannerViewModel is the core of the app — page CRUD, filter state, PDF naming, SavedStateHandle. Untested ViewModel = untested business logic. | MEDIUM | Requires MockK + kotlinx-coroutines-test + InstantTaskExecutorRule. Testable without Android device (pure JVM). Min 15 tests per PROJECT.md. |
| **DocumentEntry JSON round-trip** (TEST-03) | DocumentEntry.toJson() + fromJson() is the persistence serialization. Any JSON regression silently corrupts user history. | LOW | Pure Kotlin/JVM test — no Android dependencies needed. Easiest test to write, high bug-catch value. |
| **ImageProcessor filter tests** (TEST-04) | ImageProcessor creates Bitmaps — can only be tested with Robolectric (needs Android graphics stack). Output pixel verification catches silent regressions. | MEDIUM | Requires Robolectric. Tests run on JVM but with Android framework emulated. Min 8 tests. |
| **DocumentHistoryRepository CRUD** (TEST-05) | SharedPreferences-backed repository — real persistence layer. getAllDocuments(), addDocument(), removeDocument(), clearHistory() must be verified. | MEDIUM | Requires Robolectric for Context + SharedPreferences. Robolectric provides in-memory SharedPreferences. Min 8 tests. |
| **Android Lint configuration** (RELEASE-02) | Play Store reviewers and Android Studio both run Lint. `contentDescription` issues, accessibility violations, and hardcoded strings are Lint errors. The project already had 13 `contentDescription="@null"` and emoji in strings that Lint would flag. A lint.xml that promotes accessibility checks to errors is the standard approach. | LOW | Configure lint.xml with `abortOnError = true` in build.gradle.kts. Treat `ContentDescription`, `HardcodedText`, and `TouchTargetSizeCheck` as errors. |
| **ProGuard/R8 rules for libraries** (RELEASE-03) | Current proguard-rules.pro only covers CameraX and CanHub. ML Kit and Navigation SafeArgs are both confirmed to break in release builds without explicit keep rules. The app targets minifyEnabled = true for release. | MEDIUM | ML Kit: `-keep class com.google.mlkit.** { *; }`. Navigation SafeArgs: `-keepnames class * implements androidx.navigation.NavArgs`. Coil and coroutines also need verification. |
| **Backup rules configured** (RELEASE-06) | `android:allowBackup="true"` (current manifest) triggers a Lint warning and will back up private scan files, processed images, and cached PDFs — up to 25 MB limit. For a document scanner, private file paths backed up to Google and then restored to a different device path are broken on restore. | LOW | For Android 12+: add `dataExtractionRules` attribute pointing to `data_extraction_rules.xml`. Exclude `scans/`, `processed/`, `cache/` paths. Keep SharedPreferences (history metadata) included. For pre-12 compatibility also add `fullBackupContent` pointing to same exclusions. |
| **FileProvider scope audit** (RELEASE-07) | Current file_paths.xml exposes `scans/`, `processed/`, `pdfs/`, and the entire cache directory. The cache-path with `path="/"` is overly broad — any file in cache is potentially shareable. | LOW | Tighten cache-path to only the subdirectories actually used for temp crop files. Document the rationale. |
| **Camera uses-feature flag** (RELEASE-05) | `android:required="true"` (current manifest) blocks installation on devices without a camera — tablets, Chromebooks, foldables with no rear camera. The app has PDF viewer, history, and import features that work fine without a camera. | LOW | Change to `android:required="false"`. Add runtime camera availability check in CameraFragment before attempting to use CameraX. |
| **Release APK E2E verification** (RELEASE-04) | Release builds with R8 + ProGuard behave differently than debug builds. Known failure modes: reflection-dependent code stripped (ML Kit, Navigation), class names obfuscated in error messages, resources stripped incorrectly. Must verify on a real device, not emulator. | MEDIUM | Requires host machine with Android Studio + connected device. Cannot run in WSL2 environment. Blocked until build environment is available. |

### Differentiators (Competitive Advantage for Portfolio Quality)

These are what separates a "it has some tests" portfolio app from a demonstrably well-engineered one.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **PdfUtils instrumented tests** (TEST-06) | PdfUtils.mergePdfs(), splitPdf(), compressPdf() use PdfRenderer which requires actual Android SDK — cannot be Robolectric'd for real PDF rendering. Instrumented tests on device verify the core PDF manipulation pipeline. | HIGH | Requires an emulator or device. Need to bundle test PDF assets. Min 8 tests per PROJECT.md. Highest complexity of all test tasks because PdfRenderer is not fully emulated by Robolectric. |
| **Fragment smoke tests** (TEST-07) | FragmentScenario with launchFragmentInContainer() — verify each of the 8 fragments inflates without crash, key views are present, and nothing NPEs on launch. Not deep UI testing — existence and visibility checks only. | MEDIUM | Requires FragmentScenario + Espresso. Catches layout inflation errors, missing string resources, and View Binding issues that only surface at runtime. Min 5 fragments covered. |
| **Navigation flow test** (TEST-08) | Test the critical happy path: Camera launch -> capture -> Preview -> "Add Page" -> PagesFragment -> "Create PDF" -> PDF created. Uses TestNavHostController to verify navigation actions fire correctly. | HIGH | Most complex test — requires coordinating CameraX (usually mocked), navigation, and ViewModel state. TestNavHostController (Navigation 2.3+) is the official approach. |
| **JaCoCo coverage reporting** (RELEASE-09) | Coverage reports make the quality claim concrete and visible on GitHub. 70% line coverage for `util/` (ImageProcessor, PdfUtils, PdfPageExtractor) and 50% for `viewmodel/` are the PROJECT.md targets. | MEDIUM | Add `testCoverageEnabled = true` to debug buildType. Configure JaCoCo task in build.gradle.kts. Coverage enforcement can be a soft gate (report only) to avoid blocking builds on legitimate uncoverable code. |
| **LeakCanary in debug builds** (RELEASE-08) | Memory leak detection during manual testing. ActivityLeakWatcher and FragmentAndViewModelWatcher auto-detect retained instances. Zero retained Activity/Fragment leaks is the bar. | LOW | Add `debugImplementation("com.squareup.leakcanary:leakcanary-android:...")`. No code changes needed — LeakCanary installs itself via ContentProvider. Does NOT appear in release builds. |
| **Detekt static analysis** (RELEASE-01) | Detekt catches Kotlin-specific code smells: complex functions, magic numbers, unused variables, naming violations. With `detekt-formatting` plugin, also enforces consistent code style. Baseline file captures existing violations so only new code is gated. | MEDIUM | Add detekt Gradle plugin. Generate baseline.xml so existing issues don't block initial setup. Run as part of CI or pre-commit check. Zero NEW blocking errors is the target. |

### Anti-Features (Commonly Pursued, Wrong for This Scope)

Features that seem like good testing practice but are wrong for this project's constraints and goals.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| **Screenshot regression tests (Roborazzi/Paparazzi)** | Visual regressions are hard to catch manually; screenshot tests automate this. | Explicitly out of scope in PROJECT.md. Tooling confidence is LOW — Paparazzi has known issues with Compose, and Roborazzi requires a specific test runner setup. Flakiness risk is high with the mascot/cartoon theme that involves custom drawing. Both require significant ongoing maintenance for golden images. | Manual dark mode verification in Phase 5 real-device E2E test covers the visual regression concern adequately for v1.1. |
| **JUnit 5 (Jupiter)** | JUnit 5 is the modern standard with better test APIs and nested test support. | Android's integration with JUnit 5 is non-trivial — requires an additional Gradle plugin (`de.mannodermaus.android-junit5`) and does not work with Robolectric without extra configuration. JUnit 4 with MockK is idiomatic for this project. No benefit justifies the migration cost. | JUnit 4 + MockK covers all needed patterns. |
| **Mockito instead of MockK** | Mockito is the industry-dominant mocking library. | Mockito-Kotlin has rough edges with Kotlin `suspend` functions, default arguments, and extension functions. MockK is Kotlin-native and handles coroutines, `object` mocking, and top-level functions correctly. | MockK throughout. |
| **100% test coverage enforcement** | High coverage = high quality signal. | JaCoCo with strict coverage enforcement on an Android project blocks builds on legitimate uncoverable code (generated View Binding classes, Parcelable implementations, Android lifecycle methods). The 70%/50% targets in PROJECT.md are appropriate thresholds; strict enforcement should be report-only, not build-breaking. | Enforce with reporting, not build failure. Fail only if coverage drops significantly below threshold. |
| **UI Automator cross-process tests** | Testing the share flow requires leaving the app process to verify system share sheet. | UI Automator tests are brittle across Android versions and OEM shells. The share intent can be verified with Espresso Intents (`intending(hasAction(Intent.ACTION_SEND))`). Full cross-process testing is disproportionate for a portfolio app. | Espresso Intents for intent verification; skip full cross-process UI Automator tests. |
| **Turbine for Flow testing** | Turbine provides clean Flow emission assertions. | The app uses LiveData exclusively, not Flow. There is no Flow to test in the current codebase. Adding Turbine adds a dependency that tests nothing until a Flow migration happens. | `InstantTaskExecutorRule` + direct LiveData value inspection is sufficient for all LiveData testing. |

---

## Feature Dependencies

```
[TEST-01: Test dependencies]
    |
    +--requires--> [TEST-02: ScannerViewModel unit tests]
    |                  (needs MockK + coroutines-test + InstantTaskExecutorRule)
    |
    +--requires--> [TEST-03: DocumentEntry JSON tests]
    |                  (pure JVM, but logically grouped with test setup)
    |
    +--requires--> [TEST-04: ImageProcessor Robolectric tests]
    |                  (needs Robolectric from TEST-01 deps)
    |
    +--requires--> [TEST-05: DocumentHistoryRepository Robolectric tests]
    |                  (needs Robolectric + Context from TEST-01 deps)
    |
    +--requires--> [TEST-07: Fragment smoke tests]
    |                  (needs FragmentScenario from TEST-01 deps)
    |
    +--requires--> [TEST-08: Navigation flow test]
                       (needs FragmentScenario + TestNavHostController)

[TEST-06: PdfUtils instrumented tests]
    (independent — needs device/emulator, not JVM)

[RELEASE-01: Detekt]
    (independent — static analysis, no test dependency)

[RELEASE-02: Lint config]
    (independent — but reveals issues that tests should cover)

[RELEASE-03: ProGuard rules]
    |
    +--requires--> [RELEASE-04: Release E2E on real device]
                       (ProGuard rules must be in place before E2E test is meaningful)

[RELEASE-05: Camera uses-feature]
    (independent — manifest change only)

[RELEASE-06: Backup rules]
    (independent — manifest + XML change only)

[RELEASE-07: FileProvider scope]
    (independent — XML change only)

[RELEASE-08: LeakCanary]
    (independent — debugImplementation only, no code changes)

[RELEASE-09: JaCoCo coverage]
    |
    +--requires--> [TEST-02 through TEST-08]
                       (coverage reports are meaningless without tests to measure)
```

### Dependency Notes

- **TEST-01 blocks everything:** No test can be written until the test dependencies are in build.gradle.kts. This is Day 1 of Phase 4.
- **RELEASE-03 before RELEASE-04:** ProGuard rules must be complete before release build E2E testing — a broken release build wastes device testing time.
- **RELEASE-04 is environment-blocked:** The WSL2 build environment cannot run `./gradlew assembleRelease` or connect to a physical device. This task requires the host machine with Android Studio. This is the single highest-risk task in the milestone.
- **TEST-06 requires emulator or device:** PdfUtils tests cannot run on JVM via Robolectric because PdfRenderer needs actual hardware rendering context. This is the second-highest complexity task.
- **RELEASE-09 (JaCoCo) is an output of tests, not an input:** Configure it during Phase 4 alongside the tests so coverage reports generate automatically.

---

## MVP Definition

### Phase 4: Test Coverage (Launch With)

The minimum viable test suite — demonstrates engineering discipline, catches the highest-value bugs.

- [x] TEST-01: Test dependencies configured in build.gradle.kts
- [x] TEST-02: ScannerViewModel unit tests (page CRUD, filter state, SavedStateHandle, PDF naming) — min 15 tests
- [x] TEST-03: DocumentEntry JSON round-trip (toJson + fromJson) — min 6 tests
- [x] TEST-04: ImageProcessor filter tests via Robolectric — min 8 tests
- [x] TEST-05: DocumentHistoryRepository CRUD via Robolectric — min 8 tests
- [x] RELEASE-09: JaCoCo configured and reporting (report-only, no hard gate)

### Phase 4: Stretch (Add If Tests Are Running Cleanly)

- [ ] TEST-07: Fragment smoke tests (5 fragments minimum)
- [ ] TEST-08: Navigation flow test (Camera -> Preview -> Pages -> PDF)

### Phase 5: Release Readiness (All Non-Negotiable)

- [x] RELEASE-01: Detekt with baseline — zero new blocking errors
- [x] RELEASE-02: lint.xml with accessibility errors promoted to errors
- [x] RELEASE-03: ProGuard/R8 rules for ML Kit, NavSafeArgs, Coil
- [x] RELEASE-04: Release APK E2E on real device (requires host machine)
- [x] RELEASE-05: Camera `uses-feature required="false"` in manifest
- [x] RELEASE-06: dataExtractionRules + fullBackupContent excluding private files
- [x] RELEASE-07: FileProvider scope tightened to actual needed paths
- [x] RELEASE-08: LeakCanary added as debugImplementation

### Future Consideration (v2+)

- [ ] TEST-06: PdfUtils instrumented tests — HIGH complexity, needs emulator setup. Can be added in v1.1 if emulator is available, but deferred if environment setup takes time.
- [ ] Screenshot regression tests — OUT OF SCOPE per PROJECT.md. Revisit in v2.
- [ ] CI/CD pipeline with automatic test runs — not in v1.1 scope.
- [ ] JaCoCo hard enforcement gate — add only after coverage is established and stable.

---

## Feature Prioritization Matrix

| Feature | Portfolio/Reviewer Value | Implementation Cost | Priority |
|---------|--------------------------|---------------------|----------|
| TEST-01: Test dependencies | HIGH (enables everything) | LOW | P1 |
| TEST-02: ViewModel unit tests | HIGH (business logic) | MEDIUM | P1 |
| TEST-03: JSON round-trip | MEDIUM (data integrity) | LOW | P1 |
| TEST-04: ImageProcessor filter tests | MEDIUM (core algorithm) | MEDIUM | P1 |
| TEST-05: Repository CRUD | MEDIUM (data persistence) | MEDIUM | P1 |
| RELEASE-02: Lint config | HIGH (blocks store submission) | LOW | P1 |
| RELEASE-03: ProGuard rules | HIGH (release builds break without) | MEDIUM | P1 |
| RELEASE-05: Camera uses-feature | HIGH (device reach) | LOW | P1 |
| RELEASE-06: Backup rules | HIGH (data security) | LOW | P1 |
| RELEASE-07: FileProvider scope | MEDIUM (security hygiene) | LOW | P1 |
| RELEASE-08: LeakCanary | HIGH (portfolio signal) | LOW | P1 |
| RELEASE-04: E2E release build | HIGH (non-negotiable before publish) | MEDIUM | P1 (env-blocked) |
| RELEASE-01: Detekt | MEDIUM (code quality) | MEDIUM | P2 |
| RELEASE-09: JaCoCo coverage | MEDIUM (metrics) | MEDIUM | P2 |
| TEST-07: Fragment smoke tests | MEDIUM (UI sanity) | MEDIUM | P2 |
| TEST-08: Navigation flow test | MEDIUM (happy path) | HIGH | P2 |
| TEST-06: PdfUtils instrumented | MEDIUM (PDF core) | HIGH | P3 |

**Priority key:**
- P1: Non-negotiable for v1.1 milestone
- P2: Should complete in v1.1, can slip to early v1.2 if blocked
- P3: Deferred — environment constraints or effort-to-value ratio

---

## Play Store Reality vs. Idealism

### What Play Store Actually Requires

Play Store does NOT check for test coverage, Lint scores, or Detekt output. The actual enforced requirements as of 2026-03:

1. **Target API level**: Must target Android 14 (API 34) or higher. Current targetSdk = 34. Already compliant.
2. **New personal developer accounts**: 14 days of closed testing with 12+ testers before production access. Applies if account was created after November 2023.
3. **AAB format**: Google Play requires Android App Bundle (.aab), not APK. The `./gradlew bundleRelease` task generates this. No configuration change needed.
4. **64-bit support**: Required. Kotlin/Gradle handles this automatically for modern targets.
5. **Privacy policy**: Required if app requests dangerous permissions (CAMERA). A hosted privacy policy URL must be provided in Play Console.

### What Play Store Indirectly Enforces via Policy

- **Crash rate**: Google Play Console "vitals" flags apps with crash rates above 1.09% (bad behavior threshold). LeakCanary helps catch crashes before release.
- **ANR rate**: Flagged above 0.47%. Ensuring PDF operations happen on Dispatchers.IO (already done in v1.0) is the mitigation.
- **Accessibility**: Not strictly enforced at submission, but Lint errors for accessibility (missing content descriptions, small touch targets) indicate real user issues.

### What Is Portfolio-Required But Not Store-Required

Everything in the table stakes and differentiators sections above beyond the Play Store requirements is a **portfolio quality signal** — what a technical interviewer or code reviewer expects to see in a well-engineered app. The test suite, Detekt, JaCoCo, and release build verification are for demonstrating engineering discipline, not for passing automated Play Store checks.

---

## Non-Negotiable vs. Nice-to-Have Summary

### Absolutely Non-Negotiable (Cannot Ship Without)

| Task | Reason |
|------|--------|
| TEST-01 | All other tests depend on it |
| TEST-02 | ScannerViewModel is untested business logic |
| TEST-03 | Silent JSON regression = user data corruption |
| RELEASE-03 | ML Kit and NavSafeArgs break in release without keep rules |
| RELEASE-04 | Unverified release build = unknown quantity |
| RELEASE-05 | Tablet/Chromebook users blocked without this manifest change |
| RELEASE-06 | Private scan files backed up to Google = security issue |
| RELEASE-07 | Overly broad FileProvider = potential file exfiltration |

### High Value, Low Effort (Do These First After Blockers)

| Task | Reason |
|------|--------|
| TEST-03 | Easiest test to write, catches real serialization bugs |
| RELEASE-08 | Zero code changes — just a dependency addition |
| RELEASE-05 | One line in AndroidManifest.xml |
| RELEASE-02 | lint.xml + one build.gradle.kts line |

### High Value, Medium Effort (Core of Phase 4)

| Task | Reason |
|------|--------|
| TEST-02 | The payoff test — 15 ViewModel tests covering all public methods |
| TEST-04 | Robolectric setup pays off for TEST-05 too |
| TEST-05 | Repository is production persistence — must be verified |
| RELEASE-03 | Known-broken libraries without it |
| RELEASE-09 | Configure alongside tests; no extra setup if tests exist |

### Deferrable Without Shame (Scope Management)

| Task | Reason to Defer |
|------|-----------------|
| TEST-06 | HIGH complexity (needs device/emulator), environment uncertainty |
| TEST-07 | Medium effort, can add during Phase 5 if Phase 4 ahead of schedule |
| TEST-08 | HIGH complexity (camera mock + nav coordination) |
| RELEASE-01 | Detekt is useful but not a blocker; baseline approach means low risk |

---

## Sources

- **Android Developers: Local Unit Tests** — HIGH confidence. Current official guidance on JVM vs. instrumented test split, Robolectric recommendation.
  URL: https://developer.android.com/training/testing/local-tests
- **Android Developers: Test Navigation** — HIGH confidence. Official guidance on TestNavHostController for Fragment navigation testing.
  URL: https://developer.android.com/guide/navigation/testing
- **Android Developers: Auto Backup** — HIGH confidence. dataExtractionRules for Android 12+, fullBackupContent for pre-12.
  URL: https://developer.android.com/identity/data/autobackup
- **Android Developers: Lint** — HIGH confidence. lint.xml configuration, severity levels, abortOnError.
  URL: https://developer.android.com/studio/write/lint
- **Google Play: Target API requirements** — HIGH confidence. API 34 requirement confirmed for 2025+.
  URL: https://support.google.com/googleplay/android-developer/answer/11926878
- **Google Play: New personal developer accounts** — HIGH confidence. 12-tester closed testing requirement.
  URL: https://support.google.com/googleplay/android-developer/answer/14151465
- **Droidsonroids: ProGuard rules for Navigation SafeArgs** — MEDIUM confidence. Specific keep rule for NavArgs.
  URL: https://www.thedroidsonroids.com/blog/how-to-generate-proguard-r8-rules-for-navigation-component-arguments
- **Android Developers Blog: R8 Keep Rules** — HIGH confidence. Official guidance on keep rule configuration.
  URL: https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html
- **Detekt project** — HIGH confidence. Official docs for setup, baseline generation.
  URL: https://detekt.dev/
- **Direct codebase inspection** — HIGH confidence. Manifest, build.gradle.kts, proguard-rules.pro, file_paths.xml, ScannerViewModel.kt, DocumentHistory.kt, ImageProcessor.kt, PdfUtils.kt all read directly.

---

*Feature research for: Android Document Scanner v1.1 — Testing & Release Readiness*
*Researched: 2026-03-01*
