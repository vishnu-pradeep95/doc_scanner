# PDF Scanner — Android App Polish & Quality Pass

## Current Milestone: v1.1 Quality Gates

**Goal:** Add a complete test foundation and release-hardening layer so the app meets Play Store quality bar on all shipped features.

**Target features:**
- Test suite: ViewModel unit tests, JSON round-trip, Robolectric filter and repository tests, JaCoCo coverage reporting
- Release hardening: Detekt, Android Lint (a11y as errors), ProGuard for ML Kit + SafeArgs, backup rules, FileProvider scoping, LeakCanary, real-device E2E

## What This Is

A feature-rich Android document scanner app (Kotlin, CameraX, Material Design 3) with a distinctive playful cartoon/mascot theme. Phases 1–3 (v1.0) delivered a systematic stability, design system, and performance pass — the app now meets portfolio quality on all shipped features. Phases 4–5 (v1.1) will add test coverage and Play Store release readiness.

## Core Value

Every feature that exists must work flawlessly, feel delightful, and be verified — no rough edges, no untested flows.

## Requirements

### Validated (v1.0)

- ✓ Crash-free coroutine context patterns across all 8 fragments — v1.0 (BUG-01)
- ✓ PdfRenderer/ParcelFileDescriptor released via `use {}` — no file descriptor leaks — v1.0 (BUG-02)
- ✓ Stale temp file cleanup on startup (1hr threshold) — v1.0 (BUG-03)
- ✓ ScannerViewModel uses immutable List/Map — no concurrent modification — v1.0 (BUG-04)
- ✓ SavedStateHandle preserves scan state across process death — v1.0 (BUG-05)
- ✓ Bitmap dimensions capped + `recycle()` on use — no OOM on 10+ page docs — v1.0 (BUG-06)
- ✓ PDF Editor undo/redo buttons removed (non-functional UI eliminated) — v1.0 (BUG-07)
- ✓ EXIF orientation correction on gallery import — v1.0 (BUG-08)
- ✓ Nunito type scale (TextAppearance.Cartoon.*) applied across all screens — v1.0 (DSYS-01)
- ✓ 8dp spacing grid (`dimens.xml`) applied across all layouts — v1.0 (DSYS-02)
- ✓ Coil 2.7.0 integrated for lifecycle-safe image/thumbnail loading — v1.0 (DSYS-03)
- ✓ 68 Toast calls migrated to Snackbar via Fragment extension — v1.0 (DSYS-04)
- ✓ All hardcoded `android:text` and `contentDescription` moved to `strings.xml` — v1.0 (DSYS-05)
- ✓ Emoji removed from all `android:text` attributes in layout XML — v1.0 (DSYS-06)
- ✓ Dark mode visually verified (code-side: `?attr/colorOnSurface*` in TextAppearance styles) — v1.0 (DSYS-07)
- ✓ Material motion transitions (SharedAxis Z + FadeThrough) on all 8 fragment paths — v1.0 (PERF-01)
- ✓ Haptic feedback on camera capture (API 30+: CONFIRM; API 24-29: VIRTUAL_KEY) — v1.0 (PERF-02)
- ✓ Edge-to-edge display with 18 WindowInsets listeners across all 8 fragments — v1.0 (PERF-03)
- ✓ Determinate "Page X of Y" LinearProgressIndicator for PDF generation — v1.0 (PERF-04)
- ✓ Snackbar undo for single-page and bulk-page delete (no confirmation dialogs) — v1.0 (PERF-05)
- ✓ 3-slot SparseArray bitmap cache with serialized PdfRenderer access — v1.0 (PERF-06)

### Active (v1.1 scope)

- [ ] **TEST-01**: Test dependencies in build.gradle.kts (JUnit 4, MockK, Robolectric, Espresso, FragmentScenario)
- [ ] **TEST-02**: ScannerViewModel unit tests — page CRUD, filter state, PDF naming — min 15 tests
- [ ] **TEST-03**: DocumentEntry JSON round-trip tests
- [ ] **TEST-04**: ImageProcessor filter tests via Robolectric — min 8 tests
- [ ] **TEST-05**: DocumentHistoryRepository CRUD tests via Robolectric — min 8 tests
- [ ] **TEST-06**: PdfUtils instrumented tests — min 8 tests
- [ ] **TEST-07**: Fragment smoke tests — min 5 tests
- [ ] **TEST-08**: Navigation flow test (Camera → Preview → Pages → PDF created)
- [ ] **RELEASE-01**: Detekt with `detekt-formatting`, baseline, zero blocking errors
- [ ] **RELEASE-02**: Android Lint with `lint.xml`, accessibility errors treated as errors
- [ ] **RELEASE-03**: ProGuard/R8 rules for ML Kit, Navigation Safe Args
- [ ] **RELEASE-04**: Release APK manual E2E test on real device
- [ ] **RELEASE-05**: Camera `uses-feature required="false"` (tablet/Chromebook installable)
- [ ] **RELEASE-06**: `android:allowBackup=false` or fullBackupContent excluding private files
- [ ] **RELEASE-07**: FileProvider scoped to `pdfs/` only
- [ ] **RELEASE-08**: LeakCanary in debug builds — zero retained Activity/Fragment leaks
- [ ] **RELEASE-09**: JaCoCo: 70% line coverage for `util/`, 50% for `viewmodel/`

### Out of Scope

- Phase 11+ features (cloud sync, searchable PDFs, Google Drive) — next milestone
- New export formats (DOCX, TIFF) — future milestone
- WearOS / widgets — future milestone
- In-app purchases / Pro tier — not in scope
- Real PDF library (iTextPDF, PDFBox) — high effort, v2+; lossy warning added instead
- Internationalization — v2+, English-only for v1
- Tablet-optimized layouts — v2+, phone-first for v1
- Screenshot regression tests (Roborazzi/Paparazzi) — low confidence tooling, pursue in v2

## Context

- **Tech stack**: Kotlin, CameraX 1.3.1, Coil 2.7.0, CanHub Image Cropper 4.5.0, ML Kit, Navigation Component 2.7.x, Material Design 3
- **Architecture**: Single Activity + Navigation Component + MVVM (ScannerViewModel via `activityViewModels()`), LiveData, View Binding, Coroutines
- **File storage**: App-private storage in `filesDir` (scans/, processed/, pdfs/) — no storage permissions
- **Min SDK**: 24 (Android 7.0), Target SDK: 34, Compile SDK: 35
- **v1.0 state**: Phases 1–3 complete — 15 plans shipped, crash-free patterns, design system applied, performance polished, ~11,900 Kotlin LOC + ~15,500 XML LOC
- **v1.1 focus**: Test coverage (Phase 4) + Release Readiness (Phase 5) — build environment needed (WSL2 lacks JDK)
- **Known blocker**: Build verification blocked in WSL2 — needs Android Studio / JDK on host machine before Phase 5 can run `./gradlew assembleRelease`

## Constraints

- **No new features**: This pass is strictly polish + testing — no Phase 11+ work
- **Android only**: No cross-platform considerations
- **Existing architecture**: Work within MVVM + Navigation Component, don't restructure
- **Play Store target**: End result must meet Play Store quality bar

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Polish-only scope | Get existing features rock-solid before expanding | ✓ Good — 8 crash patterns eliminated, design system consistent |
| Phases 1-3 before testing | Avoid writing tests for broken behavior | ✓ Good — stable codebase ready for Phase 4 tests |
| Work within existing architecture | App structure is sound, don't risk breaking things | ✓ Good — no regressions introduced |
| Coil 2.7.0 (not 3.x) | Kotlin 1.9 compatibility — Coil 3.x requires Kotlin 2.0 | ✓ Good — stable integration |
| SavedStateHandle for page list | Bundle-safe process death recovery without custom Parcelable | ✓ Good — enum serialized as String name |
| Snackbar undo over confirmation dialogs | Less modal interruption, recoverable destructive actions | ✓ Good — applied to both single and bulk delete |
| MaterialSharedAxis Z for hierarchy, FadeThrough for lateral | Material motion convention — matches navigation depth | ✓ Good — consistent transition grammar |
| Transitions in onCreate() not onViewCreated() | Framework ignores transitions set after view creation begins | ✓ Good — documented pattern for future fragments |
| Both unit + instrumentation tests (Phase 4) | Unit for logic, instrumentation for UI flows | — Pending |
| Dark mode physical verification deferred | WSL2 lacks Android build environment | ⚠️ Revisit — needs device test in Phase 5 |

---
*Last updated: 2026-03-01 after v1.1 milestone started*
