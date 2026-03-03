# Milestones

## v1.1 Quality Gates (Shipped: 2026-03-03)

**Phases completed:** 2 phases (4–5), 10 plans
**Timeline:** 2026-03-01 → 2026-03-02 (2 days)
**Requirements shipped:** 16/16 (TEST-01–08 + RELEASE-01–09)
**LOC at ship:** ~13,500 Kotlin + ~16,200 XML

**Key accomplishments:**
- Built complete test infrastructure: 15 dependencies, JaCoCo LINE coverage task, MainDispatcherRule — pinned Kotlin 1.9.21 to block incompatible MockK/Robolectric BOM upgrade to Kotlin 2.x
- 22 ScannerViewModel unit tests covering page CRUD, filter state, and PDF naming — pure JVM, no device or Android framework required
- DocumentEntry JSON round-trip (9 tests) + ImageProcessor Robolectric filter tests (9 tests) at 96.8% LINE coverage; confirmed no ML Kit boundary in ImageProcessor
- Robolectric + JaCoCo dual exec-file fix; 11 DocumentHistoryRepository CRUD + 10 AppPreferences tests; 61-test suite at 88.9% ViewModel / 92.9% data layer coverage
- 5 fragment smoke tests + TestNavHostController navigation flow test (stretch goal) — all instrumented tests compile clean
- Detekt 1.23.8 with baseline (539 pre-existing violations), LeakCanary 2.14, Lint zero-error with a11y as build errors, manifest hardening, ProGuard rules for ML Kit/GMS/SafeArgs — RELEASE-04 E2E approved on physical device across all 8 feature paths

**Archive:** `.planning/milestones/v1.1-ROADMAP.md`, `.planning/milestones/v1.1-REQUIREMENTS.md`

---

## v1.0 Polish Pass (Shipped: 2026-03-01)

**Phases completed:** 3 phases (1–3), 15 plans, ~30 tasks
**Timeline:** 2026-02-28 → 2026-03-01 (2 days)
**Requirements shipped:** 20/21 (DSYS-03 Coil implemented; checkbox was stale)

**Key accomplishments:**
- Eliminated 8 crash patterns: null context in coroutines, PdfRenderer/ParcelFileDescriptor leaks, OOM on 10+ page docs, process death data loss via SavedStateHandle
- Applied Cartoon design system to all 9 screens: Nunito type scale, 8dp spacing grid, Material3 dark-mode-safe colors via `?attr/colorOnSurface*`
- Integrated Coil for lifecycle-safe image/thumbnail loading; migrated 68 Toast calls to Snackbar with Fragment extension
- Externalized 150+ hardcoded strings and all contentDescriptions to `strings.xml`; emoji removed from all layout `android:text` attributes
- Enabled edge-to-edge display with 18 WindowInsets listeners across all 8 fragments; Snackbar undo replaced all destructive confirmation dialogs
- Material motion transitions (SharedAxis Z + FadeThrough) on all 8 fragment paths; haptic camera shutter; 3-slot PDF bitmap cache; determinate "Page X of Y" progress indicator

**Known Gaps (deferred to v1.1):**
- TEST-01–TEST-08: Unit + instrumented test coverage (Phase 4 scope)
- RELEASE-01–RELEASE-09: Static analysis, ProGuard, device compatibility, Play Store QA (Phase 5 scope)

**Archive:** `.planning/milestones/v1.0-ROADMAP.md`, `.planning/milestones/v1.0-REQUIREMENTS.md`

---

