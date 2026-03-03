# PDF Scanner — Android App

## What This Is

A feature-rich Android document scanner app (Kotlin, CameraX, Material Design 3) with a distinctive playful cartoon/mascot theme. v1.0 delivered a systematic stability, design system, and performance pass. v1.1 completed a full test foundation and Play Store release hardening layer — the app is now ready for Play Store submission.

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

### Validated (v1.1)

- ✓ Test dependency scaffold (MockK, Robolectric, Espresso, JaCoCo LINE task, MainDispatcherRule; Kotlin 1.9.21 pinned) — v1.1 (TEST-01)
- ✓ 22 ScannerViewModel unit tests: page CRUD, filter state, PDF naming — pure JVM — v1.1 (TEST-02)
- ✓ DocumentEntry JSON round-trip (9 tests, all fields, via Robolectric runner) — v1.1 (TEST-03)
- ✓ ImageProcessor Robolectric filter tests (9 tests, all FilterType values, 96.8% LINE coverage) — v1.1 (TEST-04)
- ✓ DocumentHistoryRepository Robolectric CRUD (11 tests covering create/read/update/delete/filter) — v1.1 (TEST-05)
- ✓ 5 fragment smoke tests via FragmentScenario (non-camera fragments) — v1.1 (TEST-07)
- ✓ Navigation flow test: Home → Camera via TestNavHostController — v1.1 (TEST-08)
- ✓ Detekt 1.23.8 with detekt-baseline.xml (539 violations), `./gradlew detekt` BUILD SUCCESSFUL — v1.1 (RELEASE-01)
- ✓ Android Lint with lint.xml — ContentDescription/TouchTargetSizeCheck as build errors; zero violations — v1.1 (RELEASE-02)
- ✓ ProGuard/R8 keep rules for ML Kit, GMS, Navigation SafeArgs in proguard-rules.pro — v1.1 (RELEASE-03)
- ✓ Release APK E2E approved on physical device — all 8 feature paths crash-free — v1.1 (RELEASE-04)
- ✓ Camera `uses-feature required="false"` — installable on tablets and Chromebooks — v1.1 (RELEASE-05)
- ✓ dataExtractionRules (API 31+) and fullBackupContent excluding scans/, processed/, pdfs/ — v1.1 (RELEASE-06)
- ✓ FileProvider cache-path tightened from `path="/"` to `path="."` — v1.1 (RELEASE-07)
- ✓ LeakCanary 2.14 debugImplementation; zero app-code leaks; Navigation 2.7.x library leak documented in KNOWN_LEAKS.md — v1.1 (RELEASE-08)
- ✓ JaCoCo: 70% LINE for util/ImageProcessor, 88.9% for viewmodel/, >=22% overall util/ — v1.1 (RELEASE-09)

### Active (next milestone)

*(No active requirements — define with `/gsd:new-milestone`)*

### Out of Scope

- TEST-06: PdfUtils instrumented tests — PdfRenderer requires real device/emulator; deferred to v2+
- QUAL-01: JaCoCo hard enforcement gate in CI — add after coverage is stable across multiple milestones
- QUAL-02: CI/CD pipeline (GitHub Actions) — v2+ milestone
- QUAL-03: Screenshot regression tests (Roborazzi/Paparazzi) — low tooling confidence; v2+
- Cloud sync, searchable PDFs, Google Drive — next major milestone
- New export formats (DOCX, TIFF) — future milestone
- WearOS / widgets — future milestone
- In-app purchases / Pro tier — not in scope
- Real PDF library (iTextPDF, PDFBox) — high effort, v2+
- Internationalization — v2+, English-only for v1
- Tablet-optimized layouts — v2+, phone-first
- Play Store submission — separate action after RELEASE-04; app is now ready

## Context

- **Tech stack**: Kotlin 1.9.21, CameraX 1.3.1, Coil 2.7.0, CanHub Image Cropper 4.5.0, ML Kit, Navigation Component 2.7.x, Material Design 3
- **Test stack**: MockK 1.14.7, Robolectric 4.16, Espresso 3.7.0, JaCoCo (LINE counter), Truth 1.4.4, navigation-testing 2.7.6
- **Static analysis**: Detekt 1.23.8 (baseline 539 violations), Android Lint (a11y as errors), LeakCanary 2.14
- **Architecture**: Single Activity + Navigation Component + MVVM (ScannerViewModel via `activityViewModels()`), LiveData, View Binding, Coroutines
- **File storage**: App-private storage in `filesDir` (scans/, processed/, pdfs/) — no storage permissions
- **Min SDK**: 24 (Android 7.0), Target SDK: 34, Compile SDK: 35
- **Current state**: v1.1 shipped — 5 phases, 25 plans, ~13,500 Kotlin LOC + ~16,200 XML LOC
- **App status**: Play Store quality bar met — ready for submission

## Constraints

- **Android only**: No cross-platform considerations
- **Existing architecture**: Work within MVVM + Navigation Component
- **Kotlin 1.9.21 locked**: Kotlin 2.x would require Detekt 2.x upgrade + broad regression risk; stay at 1.9.x until deliberate major upgrade

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Polish-only scope for v1.0 | Get existing features rock-solid before expanding | ✓ Good — 8 crash patterns eliminated, design system consistent |
| Phases 1-3 before testing | Avoid writing tests for broken behavior | ✓ Good — stable codebase ready for Phase 4 tests |
| Work within existing architecture | App structure is sound, don't risk breaking things | ✓ Good — no regressions introduced |
| Coil 2.7.0 (not 3.x) | Kotlin 1.9 compatibility — Coil 3.x requires Kotlin 2.0 | ✓ Good — stable integration |
| SavedStateHandle for page list | Bundle-safe process death recovery without custom Parcelable | ✓ Good — enum serialized as String name |
| Snackbar undo over confirmation dialogs | Less modal interruption, recoverable destructive actions | ✓ Good — applied to both single and bulk delete |
| MaterialSharedAxis Z for hierarchy, FadeThrough for lateral | Material motion convention — matches navigation depth | ✓ Good — consistent transition grammar |
| Transitions in onCreate() not onViewCreated() | Framework ignores transitions set after view creation begins | ✓ Good — documented pattern for future fragments |
| JaCoCo LINE counter (not BRANCH) | Coroutines inflate BRANCH by 15-25% with synthetic branches | ✓ Good — coverage numbers reflect actual code coverage |
| Detekt 1.23.x locked (not 2.x) | Detekt 2.x requires Kotlin 2.x; project locked at 1.9.21 | ✓ Good — stable analysis, no regression risk |
| Force coroutines + stdlib to 1.9.21 | MockK + Robolectric pull in BOM upgrading to Kotlin 2.x binaries | ✓ Good — prevents classpath incompatibility silently breaking tests |
| Robolectric JaCoCo dual exec-file fix | Robolectric classloader strips AGP's compile-time probes | ✓ Good — coverage numbers now accurate across all test types |
| Detekt baseline from unmodified codebase | Generate once, commit immediately — captures pre-existing debt | ✓ Good — 539 violations captured without blocking new work |
| Navigation 2.7.x leak: document not upgrade | 2.8.x migration risk across 8 fragments; LeakCanary exclusion sufficient | ✓ Good — KNOWN_LEAKS.md documents with suppression snippet |
| cache/ omitted from backup exclusion XML | Android auto-excludes cacheDir per AOSP; no explicit rule needed | ✓ Good — simpler manifest, correct behavior |
| ML Kit/GMS full keep rules (-keep not -keepnames) | Both use reflection to load internal class hierarchies | ✓ Good — release APK confirmed crash-free |

---
*Last updated: 2026-03-03 after v1.1 Quality Gates milestone*
