# Roadmap: PDF Scanner — Polish & Quality Pass

## Milestones

- ✅ **v1.0 Polish Pass** — Phases 1–3 (shipped 2026-03-01)
- 🚧 **v1.1 Quality Gates** — Phases 4–5 (in progress)

## Phases

<details>
<summary>✅ v1.0 Polish Pass (Phases 1–3) — SHIPPED 2026-03-01</summary>

- [x] Phase 1: Stability (4/4 plans) — completed 2026-03-01
- [x] Phase 2: Design System (8/8 plans) — completed 2026-03-01
- [x] Phase 3: Performance & Polish (3/3 plans) — completed 2026-03-01

Full details: `.planning/milestones/v1.0-ROADMAP.md`

</details>

### 🚧 v1.1 Quality Gates (In Progress)

**Milestone Goal:** A test foundation covering ViewModel logic, data persistence, JSON serialization, and image processing — plus a release-hardened APK that meets Play Store quality bar on manifest configuration, static analysis, ProGuard rules, and zero memory leaks.

- [x] **Phase 4: Test Coverage** — Test dependency scaffold, ViewModel unit tests, JSON round-trip, Robolectric integration tests, JaCoCo coverage reporting, and stretch fragment/navigation tests (completed 2026-03-01)
- [ ] **Phase 5: Release Readiness** — Detekt static analysis, Android Lint accessibility enforcement, ProGuard/R8 keep rules, manifest hardening, LeakCanary leak detection, and real-device E2E verification

## Phase Details

### Phase 4: Test Coverage
**Goal**: The codebase has a verified, runnable test suite covering all pure business logic, data persistence, and image processing — with JaCoCo confirming coverage meets the stated thresholds
**Depends on**: Phase 3 (stable, feature-complete codebase)
**Requirements**: TEST-01, TEST-02, TEST-03, TEST-04, TEST-05, TEST-07, TEST-08, RELEASE-09
**Success Criteria** (what must be TRUE):
  1. Developer can run `./gradlew testDebugUnitTest` and see a passing test suite with 37+ tests across ViewModel, JSON, ImageProcessor, and Repository classes
  2. Developer can run `./gradlew jacocoTestReport` and open the HTML report showing LINE coverage at or above 70% for `util/ImageProcessor` specifically and 50% for `viewmodel/`; overall `util/` package coverage is ≥22% (device-dependent classes and ContentResolver-dependent utilities excluded from JVM unit test scope)
  3. ScannerViewModel tests exercise page add/remove/reorder, filter state transitions, and PDF naming with no mocked Android framework required (pure JVM)
  4. Robolectric tests run without a device — ImageProcessor filter tests use Robolectric Bitmap (ImageProcessor does NOT call OcrProcessor, so no fake OCR needed); DocumentHistoryRepository tests exercise all CRUD operations via Robolectric SharedPreferences
  5. (Stretch) Fragment smoke tests launch 5+ non-camera fragments via FragmentScenario and verify layout inflation completes without crash; navigation flow test confirms Home → Camera nav action fires correctly

**Plans**: 7 plans

Plans:
- [ ] 04-01-PLAN.md — Test dependency scaffold (build.gradle.kts + JaCoCo task + MainDispatcherRule) [Wave 1]
- [ ] 04-02-PLAN.md — ScannerViewModel unit tests (15+ tests: page CRUD, filter state, PDF naming) [Wave 2]
- [ ] 04-03-PLAN.md — DocumentEntry JSON round-trip (6+ tests) + ImageProcessor Robolectric (9+ tests) [Wave 2]
- [ ] 04-04-PLAN.md — DocumentHistoryRepository Robolectric CRUD (10+ tests) + JaCoCo coverage verification [Wave 3]
- [ ] 04-05-PLAN.md — (Stretch) 5 fragment smoke tests + Navigation flow test (instrumented, device required) [Wave 2]
- [ ] 04-06-PLAN.md — (Gap closure) AppPreferences Robolectric tests + RELEASE-09 threshold recalibration [Wave 1]
- [ ] 04-07-PLAN.md — (Gap closure 2) RELEASE-09 threshold final recalibration: >=22% + ImageUtils exclusion [Wave 1]

### Phase 5: Release Readiness
**Goal**: The app passes all static analysis checks, has correct manifest configuration for Play Store distribution, ProGuard rules verified against a real release APK, and zero memory leaks confirmed on device
**Depends on**: Phase 4
**Requirements**: RELEASE-01, RELEASE-02, RELEASE-03, RELEASE-04, RELEASE-05, RELEASE-06, RELEASE-07, RELEASE-08
**Success Criteria** (what must be TRUE):
  1. Developer can run `./gradlew detekt` and see zero new blocking errors — existing violations are captured in `detekt-baseline.xml` so they don't block the build
  2. Developer can run `./gradlew lint` and see zero errors — accessibility violations (`ContentDescription`, `TouchTargetSizeCheck`) are treated as errors and the app has none
  3. AndroidManifest.xml declares `uses-feature android:required="false"` for camera, `dataExtractionRules` and `fullBackupContent` excluding private scan directories, and FileProvider paths scoped only to actually-used subdirectories
  4. Release APK installs on a physical Android device and every screen and feature path (camera capture, gallery import, ML Kit OCR, PDF generation, share/export) completes without crash — confirming ProGuard keep rules for ML Kit and Navigation SafeArgs are correct (ENVIRONMENT-BLOCKED: requires host machine with Android Studio)
  5. LeakCanary reports zero retained Activity/Fragment/ViewModel leaks after exercising all 8 fragment flows — Navigation 2.7.x AbstractAppBarOnDestinationChangedListener leak documented as library bug and triaged
**Plans**: TBD

Plans:
- [ ] 05-01: Detekt baseline + LeakCanary integration + binding nullification audit
- [ ] 05-02: Android Lint with accessibility-as-errors + manifest hardening (uses-feature, dataExtractionRules, fullBackupContent, FileProvider scope)
- [ ] 05-03: ProGuard/R8 keep rules (ML Kit, Navigation SafeArgs, Coil, coroutines) + release APK E2E on device

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Stability | v1.0 | 4/4 | Complete | 2026-03-01 |
| 2. Design System | v1.0 | 8/8 | Complete | 2026-03-01 |
| 3. Performance & Polish | v1.0 | 3/3 | Complete | 2026-03-01 |
| 4. Test Coverage | 7/7 | Complete   | 2026-03-02 | - |
| 5. Release Readiness | v1.1 | 0/3 | Not started | - |
