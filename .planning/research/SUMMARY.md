# Project Research Summary

**Project:** PDF Scanner -- Polish & Quality Pass
**Domain:** Android Document Scanner App (existing app, post-feature-complete quality pass)
**Researched:** 2026-02-28
**Confidence:** HIGH

## Executive Summary

This is a feature-complete Android document scanner app (Phase 10) built with Single Activity + Navigation Component + MVVM that needs a systematic quality pass before Play Store submission. The app has zero tests, 70 Toast calls used as the primary feedback mechanism, 154 unsafe `requireContext()` calls in coroutine callbacks, no image loading library, incomplete ProGuard rules, and unbounded temp file accumulation. The cartoon mascot theme is a genuine differentiator, but inconsistent spacing, typography, and several crash-prone code paths undermine it. The core scanning and PDF generation functionality works but is fragile under stress (OOM on 10+ page documents, resource leaks in error paths, race conditions in shared mutable LiveData).

The recommended approach is a phased quality pass that prioritizes stability before polish. Fix crashes and resource leaks first (the `requireContext()` pattern alone accounts for the most common crash class in production Android apps). Then establish a design system (typography scale, spacing grid, Snackbar patterns) so UI fixes are systematic rather than ad-hoc. Add an image loading library (Coil) early because it simultaneously solves memory management, thumbnail caching, and scroll performance. Only after the app is stable and visually consistent should test coverage be added -- writing tests against broken behavior wastes effort. The testing stack is straightforward: JUnit 4 + MockK + Robolectric for unit tests, Espresso for integration, with LeakCanary and Detekt for ongoing quality enforcement.

The key risks are: (1) OOM crashes during PDF generation with many pages -- the app allocates up to 48MB per uncompressed bitmap and the magic filter triples memory usage; (2) Fragment detachment crashes from the 154 `requireContext()` calls in coroutine contexts; (3) ProGuard stripping ML Kit and Play Services classes in release builds, which will only be discovered when testing the release APK; and (4) the `android:required="true"` camera declaration silently filtering the app from tablets and Chromebooks on the Play Store.

## Key Findings

### Recommended Stack

The testing and quality stack builds on the existing JUnit 4 + Espresso foundation already declared in build.gradle.kts. No framework migration is needed. See [STACK.md](./STACK.md) for full dependency configuration.

**Core technologies:**
- **MockK 1.13.12**: Kotlin-first mocking -- handles suspend functions, sealed classes, and final classes natively (verify version on Maven Central)
- **Robolectric 4.12+**: Runs Android-dependent tests (Bitmap, SharedPreferences) on JVM -- critical for ImageProcessor and DocumentHistoryRepository tests without device
- **Detekt 1.23.6 + detekt-formatting**: Kotlin static analysis covering code smells, complexity, and formatting in one tool (replaces standalone ktlint)
- **LeakCanary 2.14**: Auto-detects Activity/Fragment memory leaks in debug -- essential given CameraX lifecycle binding and fragment navigation patterns
- **Coil** (not in STACK.md but identified in FEATURES.md as critical): Kotlin-first image loading library for thumbnail caching, memory management, and smooth loading transitions
- **AndroidX Arch Core Testing 2.2.0**: `InstantTaskExecutorRule` for synchronous LiveData in unit tests -- required since the app uses LiveData extensively
- **JaCoCo** (AGP built-in): Code coverage with targets of 70% for utilities, 50% for ViewModels

**Version warning:** All versions are from training data (cutoff May 2025). WebSearch was unavailable during research. Verify every version against Maven Central / Google Maven before adding to build.gradle.kts.

### Expected Features

This is a polish pass, not a feature build. "Features" here are quality attributes. See [FEATURES.md](./FEATURES.md) for the full audit.

**Must fix (Play Store launch blockers):**
- Crash freedom on all core flows -- zero test coverage currently, 52 catch blocks catching generic Exception
- Memory management for bitmap operations -- no image loading library, manual bitmap handling causes OOM
- Fix Toast-as-loading-indicator pattern -- broken UX in HomeFragment
- Process death recovery -- no SavedStateHandle; in-progress scans lost when Android kills the app
- ProGuard rules for ML Kit and Play Services -- release builds will crash without them
- Move hardcoded contentDescription strings to resources -- accessibility and i18n requirement
- Remove emoji from programmatic strings

**Should fix (portfolio quality):**
- Typography scale and spacing system (8dp grid) applied consistently
- Replace Toast spam (70 calls) with Snackbar/inline feedback
- Navigation transitions using Material motion patterns
- Edge-to-edge display with proper inset handling
- Dark mode visual verification on all screens
- Haptic feedback on capture (Adobe Scan, Microsoft Lens, Google Drive all have this)
- Camera `uses-feature required="false"` for tablet/Chromebook compatibility

**Defer (v2+):**
- First-launch onboarding
- Skeleton/shimmer loading placeholders
- Predictive back gesture (Android 14+)
- Dynamic color theming
- Searchable PDFs, cloud sync, new export formats
- Internationalization

### Architecture Approach

The app follows a clean Single Activity + Navigation Component + MVVM architecture that is well-suited for testing without restructuring. ScannerViewModel (shared across fragments via `activityViewModels()`) and PdfEditorViewModel handle state; 7 fragments compose the UI; utility classes (ImageProcessor, PdfUtils, DocumentHistoryRepository) contain the business logic. See [ARCHITECTURE.md](./ARCHITECTURE.md) for test directory structure and patterns.

**Major components and test strategy:**
1. **ScannerViewModel** -- page list management, filter state, PDF naming. Fully unit-testable on JVM with InstantTaskExecutorRule. Highest priority.
2. **ImageProcessor** -- bitmap filter functions (Enhanced, B&W, Magic, Sharpen). Unit-testable via Robolectric.
3. **PdfUtils** -- merge, split, compress operations. Requires real device/emulator for PdfRenderer. Integration test territory.
4. **DocumentHistoryRepository** -- SharedPreferences + JSON CRUD. Testable via Robolectric.
5. **7 Fragments** -- UI layer with View Binding and LiveData observation. Espresso + FragmentScenario for integration tests.
6. **3 Custom Views** (AnnotationCanvasView, SignaturePadView, NativePdfView) -- Canvas-based, need real device for meaningful tests.

**Test pyramid target:** ~60% unit (40-60 tests), ~30% integration (15-20 tests), ~10% manual E2E (~5 smoke tests).

### Critical Pitfalls

See [PITFALLS.md](./PITFALLS.md) for full analysis with code-level detail.

1. **OOM during PDF generation** -- 12MP camera images are ~48MB uncompressed each; magic filter triples memory. Fix: explicit `bitmap.recycle()`, cap decoded dimensions to PDF output size, use `inBitmap` for reuse.
2. **Fragment detachment crashes** -- 154 `requireContext()`/`requireActivity()` calls; coroutines complete after fragment detach. Fix: systematic replacement with `context ?: return` in all coroutine callbacks.
3. **Leaked PdfRenderer and temp files** -- manual `close()` instead of `use {}` blocks; temp files never cleaned up. Fix: convert to `use {}`, add cache cleanup routine.
4. **Incomplete ProGuard rules** -- ML Kit, Play Services, and Navigation Safe Args classes will be stripped in release. Fix: add keep rules, test release APK on device.
5. **Mutable LiveData race conditions** -- `LiveData<MutableList<Uri>>` allows direct mutation without notification and concurrent modification. Fix: switch to immutable list pattern (`currentList + newUri`).
6. **Camera hardware requirement** -- `required="true"` blocks tablets and Chromebooks from Play Store. Fix: change to `required="false"`, handle missing camera at runtime.

## Implications for Roadmap

Based on combined research, the dependency chain is clear: stability before polish, polish before tests, tests validate the fixes. Here is the suggested phase structure:

### Phase 1: Critical Bug Fixes and Stability

**Rationale:** The app has crash-prone patterns that must be fixed before any other work. Polishing UI on a crashing app wastes effort. Tests written against broken behavior need rewriting.
**Delivers:** Crash-free core flows, proper resource management, data integrity in ViewModel.
**Addresses:** Crash freedom (P1), process death recovery (P1), back navigation during operations, EXIF rotation handling.
**Avoids:** Fragment detachment crashes (Pitfall 3), leaked resources (Pitfall 2), mutable LiveData races (Pitfall 6), OOM during PDF generation (Pitfall 1).

Key tasks:
- Replace all 154 `requireContext()` in coroutine callbacks with `context ?: return`
- Convert PdfRenderer/ParcelFileDescriptor to `use {}` blocks
- Add temp file cleanup routine (delete stale files >1hr on app startup)
- Refactor ScannerViewModel to immutable collections
- Add SavedStateHandle for process death recovery
- Cap bitmap decode dimensions for PDF generation, add explicit `recycle()`
- Remove or implement undo/redo buttons in PDF editor (no "coming soon" placeholders)

### Phase 2: Design System and UI Consistency

**Rationale:** Before fixing individual UI issues, define the system. A typography scale and spacing grid applied systematically prevents new inconsistencies. This phase also adds Coil for image loading, which is a prerequisite for performance work.
**Delivers:** Consistent visual language, proper image loading with caching, Snackbar-based feedback pattern.
**Addresses:** Typography scale (P2), spacing system (P2), Toast replacement (P2), image loading library (P1 -- memory), content descriptions (P1), emoji removal (P1).
**Avoids:** Creating new inconsistencies by fixing screens ad-hoc without a design system.

Key tasks:
- Define Material type scale in styles.xml (heading, body, caption sizes)
- Define spacing constants (8dp grid) and apply across all layouts
- Add Coil dependency for all image/thumbnail loading
- Replace Toast spam with Snackbar/inline feedback (70 Toast calls across 7 files)
- Move all hardcoded contentDescription strings to strings.xml
- Remove emoji from programmatic strings
- Verify dark mode on all screens

### Phase 3: Performance and Polish

**Rationale:** With stability fixed and Coil in place, most performance issues (thumbnail caching, scroll jank, memory spikes) are already mitigated. This phase focuses on remaining performance work and UX polish that differentiates from competitors.
**Delivers:** Smooth 60fps scrolling, fast camera startup, navigation animations, haptic feedback, edge-to-edge display.
**Addresses:** Camera startup optimization (P2), scroll performance (P2), navigation transitions (P2), haptic feedback (P2), edge-to-edge (P2), determinate progress indicators (P2).
**Avoids:** Performance traps from PITFALLS.md (full-res bitmap loading in RecyclerView, PdfRenderer re-rendering on every page change).

Key tasks:
- Verify Coil integration eliminates thumbnail jank
- Add Material motion transitions for navigation
- Implement haptic feedback on capture
- Add edge-to-edge display with WindowInsets handling
- Replace indeterminate progress with "Page X of Y" for PDF generation
- Snackbar with undo for destructive actions (replace confirmation dialogs)
- Cache PdfRenderer pages (previous/current/next) for smooth swiping

### Phase 4: Testing Infrastructure and Coverage

**Rationale:** Tests should be written against stable, correct behavior. The fixes from Phases 1-3 are now the baseline to lock in with automated tests. Start with unit tests (highest value, fastest feedback), then integration.
**Delivers:** Test suite with ~60 unit tests and ~20 integration tests, CI-ready quality gates.
**Uses:** JUnit 4, MockK, Robolectric, coroutines-test, arch core-testing, Espresso, FragmentScenario.
**Implements:** Test pyramid from ARCHITECTURE.md (60% unit, 30% integration, 10% manual).

Key tasks:
- Add all test dependencies to build.gradle.kts
- ScannerViewModel unit tests (page CRUD, filter state, PDF naming)
- DocumentEntry JSON round-trip tests
- ImageProcessor filter tests via Robolectric
- DocumentHistoryRepository CRUD tests
- PdfUtils instrumented tests (merge, split, compress with real PDFs)
- Fragment smoke tests (HomeFragment navigation, PagesFragment rendering)
- Navigation flow tests with TestNavHostController

### Phase 5: Static Analysis, Release Readiness, and Play Store Prep

**Rationale:** Final quality gate before Play Store submission. Linting catches remaining issues, ProGuard testing catches release-only crashes, manifest review catches device compatibility problems.
**Delivers:** Release-ready APK, proper ProGuard rules, device compatibility, lint-clean codebase.
**Addresses:** ProGuard rules (P1), camera uses-feature (P2), accessibility lint (P1), allowBackup security.
**Avoids:** ProGuard stripping (Pitfall 5), camera hardware blocking (Pitfall 4), Play Store policy violations.

Key tasks:
- Configure Detekt with detekt-formatting plugin
- Configure Android Lint with lint.xml (accessibility errors, hardcoded text)
- Generate lint baseline, fix errors incrementally
- Add complete ProGuard rules (ML Kit, Play Services, Navigation Safe Args)
- Build and test release APK on real device -- every feature path
- Change camera `uses-feature` to `required="false"`
- Set `allowBackup="false"` or configure `fullBackupContent` to exclude scans
- Review FileProvider paths in file_paths.xml
- Add LeakCanary to debug builds
- Set JaCoCo coverage thresholds (70% util, 50% viewmodel)

### Phase Ordering Rationale

- **Stability before polish:** Fixing crashes in Phase 1 before UI work in Phase 2 prevents wasted effort on screens that crash. The fragment detachment pattern alone could cause crashes on any screen during any long-running operation.
- **Design system before screen-by-screen fixes:** Defining typography and spacing constants in Phase 2 before applying them prevents creating new inconsistencies. FEATURES.md explicitly calls this out.
- **Image loading library early (Phase 2):** Coil solves memory management, thumbnail caching, and scroll performance simultaneously. Many "performance issues" from Phase 3 will already be fixed.
- **Tests after fixes (Phase 4):** Writing tests against broken behavior is waste. Tests in Phase 4 lock in the correct behavior from Phases 1-3.
- **Release prep last (Phase 5):** ProGuard, lint, and Play Store readiness are verification steps that make sense after the code is stable and polished.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1 (Bug Fixes):** The `requireContext()` audit touches 154 call sites across 12 files. Needs careful per-file analysis to determine which calls are in coroutine contexts vs. safe synchronous contexts. Research-phase recommended.
- **Phase 3 (Performance):** Edge-to-edge display and WindowInsets handling varies significantly across API levels 24-34. Needs API-level-specific research.
- **Phase 5 (Release Readiness):** ProGuard rules for ML Kit and Play Services should be researched against current library documentation. Play Store policy for scanner apps needs fresh review.

Phases with standard patterns (skip research-phase):
- **Phase 2 (Design System):** Material type scale and 8dp grid are well-documented patterns. Coil integration has extensive documentation.
- **Phase 4 (Testing):** ARCHITECTURE.md provides concrete test patterns, directory structure, and example code. Standard Android testing patterns throughout.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM-HIGH | Concepts are HIGH confidence (established Android testing ecosystem). Specific library versions are MEDIUM -- WebSearch unavailable, versions from May 2025 training data need verification against Maven. |
| Features | HIGH | Based on Google Core App Quality guidelines (verified via WebFetch), direct codebase audit, and well-known competitor UX patterns. |
| Architecture | HIGH | Based on direct analysis of all 31 Kotlin source files and established Android testing patterns. Test strategy maps directly to actual code structure. |
| Pitfalls | HIGH | All critical pitfalls identified from direct code inspection (154 requireContext calls counted, 52 catch blocks verified, bitmap math calculated from actual code). |

**Overall confidence:** HIGH

### Gaps to Address

- **Library version verification:** All dependency versions need checking against Maven Central / Google Maven before adding to build.gradle.kts. This is a 30-minute task at the start of Phase 4.
- **Screenshot testing tool choice:** LOW confidence on Roborazzi vs Paparazzi recommendation. Evaluate both if screenshot testing is pursued in Phase 4.
- **Play Store policy (current):** Scanner apps face specific policy scrutiny. Training data may be outdated on current enforcement patterns. Verify against current Play Store developer policy before Phase 5.
- **Coil vs Glide:** FEATURES.md recommends Coil (Kotlin-first, lightweight). STACK.md does not include it since its focus was testing tools. Decision should be confirmed in Phase 2 planning.
- **PDF operation quality:** All PDF merge/split/compress operations are lossy (render-to-image). This is documented technical debt. Fixing it requires a real PDF library (iTextPdf, Apache PDFBox) and is HIGH effort -- defer to v2 but consider adding an in-app warning about lossy operations.

## Sources

### Primary (HIGH confidence)
- Direct codebase analysis -- all 31 Kotlin source files, build.gradle.kts, AndroidManifest.xml, layouts, proguard-rules.pro
- Google Core App Quality Guidelines (developer.android.com/docs/quality-guidelines/core-app-quality)
- Android developer documentation on testing (developer.android.com/training/testing)
- Material Design 3 Guidelines

### Secondary (MEDIUM confidence)
- AndroidX Test, Espresso, MockK, Robolectric ecosystem knowledge (training data, established APIs)
- Competitor analysis (Adobe Scan, Microsoft Lens, Google Drive scanner UX patterns)
- Android accessibility guidelines (48dp touch targets, content descriptions)

### Tertiary (LOW confidence)
- Specific library version numbers (training data cutoff May 2025, WebSearch unavailable)
- Roborazzi vs Paparazzi recommendation (limited evaluation data)
- Play Store scanner app policy enforcement patterns (may have changed since training data)

---
*Research completed: 2026-02-28*
*Ready for roadmap: yes*
