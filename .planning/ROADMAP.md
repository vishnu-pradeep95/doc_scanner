# Roadmap: PDF Scanner — Polish & Quality Pass

## Overview

This is a systematic quality pass on a feature-complete Android document scanner app (Phase 10). The app works but has crash-prone patterns, inconsistent UI, no test coverage, and incomplete release configuration. The roadmap follows a strict dependency chain: fix crashes first so polish work is not wasted on broken screens, then establish a design system so UI fixes are systematic, then add performance polish, then lock in correct behavior with tests, and finally prepare for Play Store release. Every phase delivers a verifiable quality improvement.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Stability** - Fix crashes, resource leaks, data integrity, and process death recovery
- [ ] **Phase 2: Design System** - Establish and apply consistent typography, spacing, feedback patterns, and image loading
- [ ] **Phase 3: Performance & Polish** - Navigation transitions, haptic feedback, edge-to-edge display, and smooth rendering
- [ ] **Phase 4: Test Coverage** - Unit tests, integration tests, and navigation flow tests locking in correct behavior
- [ ] **Phase 5: Release Readiness** - Static analysis, ProGuard rules, device compatibility, leak detection, and final QA

## Phase Details

### Phase 1: Stability
**Goal**: App does not crash, leak resources, or lose data under any normal usage pattern
**Depends on**: Nothing (first phase)
**Requirements**: BUG-01, BUG-02, BUG-03, BUG-04, BUG-05, BUG-06, BUG-07, BUG-08
**Success Criteria** (what must be TRUE):
  1. User can navigate between all screens during long-running operations (PDF generation, OCR, filter application) without crashes
  2. User can scan, edit, and generate a 10+ page PDF without the app running out of memory
  3. User can force-kill the app during a batch scan and resume with pages intact on relaunch
  4. User can import images from gallery with correct orientation regardless of EXIF data
  5. PDF Editor undo/redo either works end-to-end or buttons are not visible
**Plans**: 4 plans

Plans:
- [x] 01-01-PLAN.md — ScannerViewModel immutable collections + SavedStateHandle (BUG-04, BUG-05)
- [x] 01-02-PLAN.md — Resource leak fixes, temp cleanup, undo/redo removal (BUG-02, BUG-03, BUG-07)
- [x] 01-03-PLAN.md — Fragment crash safety (high-risk) + EXIF import fix (BUG-01, BUG-08)
- [x] 01-04-PLAN.md — Fragment crash safety (remaining) + bitmap memory caps (BUG-01, BUG-06)

### Phase 2: Design System
**Goal**: Every screen follows the same typography scale, spacing grid, and feedback patterns — the cartoon theme feels intentional, not ad-hoc
**Depends on**: Phase 1
**Requirements**: DSYS-01, DSYS-02, DSYS-03, DSYS-04, DSYS-05, DSYS-06, DSYS-07
**Success Criteria** (what must be TRUE):
  1. All screens use the same heading, body, and caption text sizes from a defined Nunito type scale — no hardcoded or inconsistent font sizes
  2. All user feedback appears as Snackbar or inline messages — no Toast is used anywhere in the app
  3. All images and thumbnails load smoothly via Coil with proper placeholders — no blank frames or recycled bitmap artifacts in lists
  4. Dark mode renders correctly on every screen — no unreadable text, invisible icons, or wrong background colors
  5. All user-facing strings (including content descriptions) come from strings.xml — no hardcoded English text in code or layouts
**Plans**: TBD

Plans:
- [ ] 02-01: TBD
- [ ] 02-02: TBD

### Phase 3: Performance & Polish
**Goal**: The app feels responsive and delightful — smooth transitions, tactile feedback, modern edge-to-edge display, and clear progress indicators
**Depends on**: Phase 2
**Requirements**: PERF-01, PERF-02, PERF-03, PERF-04, PERF-05, PERF-06
**Success Criteria** (what must be TRUE):
  1. Screen transitions use Material motion animations — no jarring instant swaps between fragments
  2. User feels a haptic pulse when the camera captures a document
  3. App content extends behind the system bars with proper padding — no content clipped by status bar or navigation bar on any API level
  4. PDF generation shows "Page X of Y" progress — user knows exactly how far along the operation is
  5. Deleting a page or discarding a scan shows a Snackbar with an undo action — user can recover from accidental destructive actions
**Plans**: TBD

Plans:
- [ ] 03-01: TBD
- [ ] 03-02: TBD

### Phase 4: Test Coverage
**Goal**: Automated tests verify that all stability fixes and UI patterns from Phases 1-3 remain correct as the codebase evolves
**Depends on**: Phase 3
**Requirements**: TEST-01, TEST-02, TEST-03, TEST-04, TEST-05, TEST-06, TEST-07, TEST-08
**Success Criteria** (what must be TRUE):
  1. Running `./gradlew test` executes unit tests for ScannerViewModel, DocumentEntry, ImageProcessor, and DocumentHistoryRepository — all pass
  2. Running `./gradlew connectedAndroidTest` executes instrumented tests for PdfUtils operations and fragment smoke tests — all pass
  3. ScannerViewModel tests cover page add, remove, reorder, filter state, and PDF naming — minimum 15 passing tests
  4. Navigation flow test verifies the complete scan path (Camera -> Preview -> Pages -> PDF created) completes without crash
  5. Test dependencies are properly configured in build.gradle.kts and do not affect production APK size
**Plans**: TBD

Plans:
- [ ] 04-01: TBD
- [ ] 04-02: TBD

### Phase 5: Release Readiness
**Goal**: The app passes all quality gates and is ready for Play Store submission — no crashes in release build, no policy violations, no leaked resources
**Depends on**: Phase 4
**Requirements**: RELEASE-01, RELEASE-02, RELEASE-03, RELEASE-04, RELEASE-05, RELEASE-06, RELEASE-07, RELEASE-08, RELEASE-09
**Success Criteria** (what must be TRUE):
  1. Running `./gradlew check` executes Detekt and Android Lint with zero blocking errors
  2. Release APK built with R8/ProGuard runs all core feature paths (scan, filter, crop, PDF, edit, OCR, merge, split, compress, share) on a real device without crashes
  3. App is installable on devices without a camera (tablets, Chromebooks) — camera feature marked as not required
  4. LeakCanary reports zero retained Activity or Fragment leaks during a full usage session in debug build
  5. JaCoCo coverage report shows at least 70% line coverage for util/ and 50% for viewmodel/
**Plans**: TBD

Plans:
- [ ] 05-01: TBD
- [ ] 05-02: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Stability | 4/4 | Complete | 2026-03-01 |
| 2. Design System | 0/? | Not started | - |
| 3. Performance & Polish | 0/? | Not started | - |
| 4. Test Coverage | 0/? | Not started | - |
| 5. Release Readiness | 0/? | Not started | - |
