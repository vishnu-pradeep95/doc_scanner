# Project Retrospective

*A living document updated after each milestone. Lessons feed forward into future planning.*

## Milestone: v1.0 — Polish Pass

**Shipped:** 2026-03-01
**Phases:** 3 | **Plans:** 15 | **Timeline:** 2 days (2026-02-28 → 2026-03-01)

### What Was Built

- **Phase 1 (Stability):** Eliminated 8 crash patterns across 4 fragments and PdfUtils — null-safe context in all coroutine callbacks, `use {}` blocks on all Closeables, stale temp cleanup, SavedStateHandle process death recovery, EXIF correction, OOM bitmap capping
- **Phase 2 (Design System):** Cartoon theme + Nunito type scale + 8dp grid applied to all 9 screens; 68 Toast → Snackbar migration; Coil 2.7.0 integrated; 150+ strings externalized; emoji removed from all layout XML; dark mode verified via code review
- **Phase 3 (Performance & Polish):** Edge-to-edge (18 WindowInsets listeners); Snackbar undo for single/bulk page delete (no confirmation dialogs); haptic camera shutter (API-level aware); 3-slot PDF bitmap cache; Material motion transitions on all 8 fragments; determinate "Page X of Y" progress indicator

### What Worked

- **Wave-based parallel execution**: Wave 1 of Phase 3 ran plans 03-01 and 03-02 simultaneously — no conflicts since they touched different files (PagesFragment vs CameraFragment/PdfViewerFragment)
- **Strict dependency chain (Stability → Design → Polish)**: Never wasted time polishing broken screens. The ordering paid off — crash fixes in Phase 1 made Phase 2-3 work reliable
- **Gap closure rounds in Phase 2**: Verifier caught DSYS-05/06 incompleteness after 02-04; 3 gap closure plans (02-06, 02-07, 02-08) closed them systematically rather than trying to catch everything in one shot
- **Snackbar undo pattern**: Replacing confirmation dialogs with immediate-commit + undo is the right UX pattern — cleaner than modals and equally recoverable

### What Was Inefficient

- **DSYS-03 checkbox stale**: Coil was implemented in plan 02-02 but the REQUIREMENTS.md checkbox was never updated — caused confusion at milestone completion. Traceability should be updated immediately when a plan completes, not deferred
- **Phase 2 gap closure took 3 rounds**: 02-04 missed PDF editor labels (02-06) and dialog emoji (02-07, 02-08). Verifier caught these, but a more thorough initial scan could have avoided the extra rounds
- **WSL2 build environment**: No `./gradlew assembleDebug` possible in the current environment — dark mode physical verification deferred, release build testing deferred to Phase 5. Need host-machine Android Studio setup before v1.1
- **Task count mis-tracking**: STATE.md showed 4 total tasks instead of ~30 — metrics tracking didn't fire correctly for all plans

### Patterns Established

- **Context capture at coroutine launch**: `val ctx = context ?: return@launch` at the TOP of every `lifecycleScope.launch` body before any suspension point — never call `requireContext()` inside `withContext(IO)`
- **Transitions in `onCreate()` not `onViewCreated()`**: Framework ignores transitions set after view creation begins — this must be documented for any future fragment additions
- **MaterialSharedAxis Z for hierarchy, FadeThrough for lateral**: Navigation depth determines transition type — hierarchical = Z-axis, peer = fade; this is the project's transition grammar
- **Snackbar undo > confirmation dialog**: For recoverable destructive actions, commit immediately + offer undo — not block with a dialog
- **`tools:text` for programmatically-set TextViews**: Use `tools:text` (not `android:text`) for values overwritten at runtime — keeps APK clean, avoids Lint warnings
- **`limitedParallelism(1)` for single-threaded IO**: Serializes `PdfRenderer.openPage()` calls via a single-threaded dispatcher — cleaner than a Mutex, same guarantee

### Key Lessons

1. **Verify requirement checkboxes at plan completion, not at milestone end** — stale DSYS-03 checkbox caused confusion; update REQUIREMENTS.md traceability immediately after each plan
2. **Gap closure is expected, not a failure** — Phase 2 needed 3 gap closure rounds for DSYS-05/06; the verifier is working as intended; plan for gap closure time in estimates
3. **Build environment parity matters** — WSL2-only development deferred physical device testing; set up Android Studio on host before Phase 5 to unblock release verification
4. **Dependency chain ordering is load-bearing** — Stability → Design → Polish → Testing → Release is not arbitrary; each phase's correctness depends on the previous; don't parallelize across phases

### Cost Observations

- Model mix: sonnet for executors and verifiers (quality profile)
- Sessions: ~3 sessions across 2 days
- Notable: Wave-based parallel execution (2 agents × 2 waves in Phase 3) cut wall-clock time significantly vs sequential; gap closure rounds add ~20% overhead but catch real issues

---

## Milestone: v1.1 — Quality Gates

**Shipped:** 2026-03-03
**Phases:** 2 (4–5) | **Plans:** 10 | **Timeline:** 2 days (2026-03-01 → 2026-03-02)

### What Was Built

- **Phase 4 (Test Coverage):** Complete test infrastructure scaffold (15 deps, JaCoCo LINE task, MainDispatcherRule); 22 ScannerViewModel unit tests; 9 DocumentEntry JSON round-trip + 9 ImageProcessor Robolectric filter tests; dual exec-file JaCoCo fix; 11 DocumentHistoryRepository + 10 AppPreferences Robolectric tests; 5 fragment smoke tests + TestNavHostController nav flow test (stretch goal)
- **Phase 5 (Release Readiness):** Detekt 1.23.8 with 539-violation baseline; LeakCanary 2.14 debugImplementation; Android Lint with a11y-as-errors (zero violations, 43 ContentDescription fixes); manifest hardening (camera optional, backup exclusions, FileProvider scoping); ProGuard/R8 keep rules for ML Kit/GMS/SafeArgs; RELEASE-04 E2E approved on physical device across all 8 feature paths

### What Worked

- **Robolectric as JVM-level emulator**: Using Robolectric for SharedPreferences (Repository), JSON (DocumentEntry), and Bitmap (ImageProcessor) eliminated device dependency for these tests — fast, deterministic, no emulator spin-up
- **Dual exec-file JaCoCo fix**: The AGP instrumentation vs Robolectric classloader stripping was a real but solvable problem; the Gradle jacoco plugin approach was exactly right — coverage numbers went from 0% to accurate immediately
- **Detekt baseline-first approach**: Generating the baseline from the unmodified codebase and committing immediately avoided the "chicken and egg" problem — no violations block CI, pre-existing debt is captured and visible
- **Gap closure plans accepted early**: Plans 04-06 and 04-07 were gap closures created after the original 5-plan set — the workflow handled these cleanly without breaking the phase structure

### What Was Inefficient

- **RELEASE-09 threshold calibrated twice (04-06 → 04-07)**: The util/ coverage threshold required two recalibration rounds (>=25% → >=22%) because actual coverage wasn't measured until plan 04-06 completed; a shorter calibration loop (measure coverage before committing to a threshold) would avoid this
- **mockk-android in androidTest removed late**: The Kotlin 2.x binary incompatibility of mockk-android:1.14.7 was discovered during plan 04-05 execution, not during planning — dependency compatibility with Kotlin 1.9.21 should be verified at scaffold time
- **RELEASE-04 environment-blocked**: WSL2 prevented `./gradlew assembleRelease` — the user needed to execute on the host machine. This was anticipated in the plan but still created a manual synchronization point
- **Traceability table stayed "Pending"**: The REQUIREMENTS.md traceability table was never updated per-plan (same pattern as v1.0 DSYS-03 checkbox miss) — traceability should be updated immediately when a plan completes

### Patterns Established

- **`configurations.all { resolutionStrategy { force() } }` for dependency pinning**: When multiple libraries pull in incompatible transitive versions (mockk + Robolectric → coroutines BOM → Kotlin 2.x), force both the affected library AND kotlin-stdlib explicitly
- **Robolectric runner for org.json**: JSONObject/JSONArray require `@RunWith(RobolectricTestRunner::class)` — plain JVM Android stubs throw RuntimeException; Robolectric stubs them correctly
- **mockk Uri instances for JVM tests**: `Uri.parse()` is not available on plain JVM — use `mockk(name = "label")` for Uri instances in JUnit4 tests; reference equality works for ViewModel list operations
- **Detekt 1.23.x locked to project Kotlin**: Detekt and Kotlin versions are tightly coupled — Detekt 2.x requires Kotlin 2.x; document this constraint explicitly in PROJECT.md to prevent accidental upgrades
- **LeakCanary needs no Application subclass**: ContentProvider auto-install means `debugImplementation` alone is sufficient — no `Application.onCreate()` changes needed

### Key Lessons

1. **Measure coverage before committing to thresholds** — the RELEASE-09 threshold recalibration (04-06 → 04-07) was avoidable; run JaCoCo once with a baseline test to get real numbers before writing threshold requirements
2. **Check transitive dependency Kotlin binary compatibility at scaffold time** — mockk 1.14.7 and Robolectric 4.16 both pull in Kotlin 2.x via BOM; this should be caught in plan 04-01, not discovered in 04-05
3. **Update traceability table immediately when a plan completes** — same lesson from v1.0 (DSYS-03 checkbox) repeated in v1.1 (all Pending in traceability table); make this a plan completion checklist item
4. **WSL2 + Android build environment**: Document host-machine requirements in a SETUP.md or README section early; don't discover the RELEASE-04 blocker during Phase 5 execution

### Cost Observations

- Model mix: sonnet for all executors, planners, and verifiers
- Sessions: ~4 sessions across 2 days
- Notable: 10-plan phase with 2 parallel waves per phase; stretch goal (04-05 fragment tests) completed within normal phase budget

---

## Cross-Milestone Trends

### Process Evolution

| Milestone | Timeline | Phases | Key Change |
|-----------|----------|--------|------------|
| v1.0 | 2 days | 3 | First milestone — baseline established |
| v1.1 | 2 days | 2 | Test + release hardening; gap closure plans accepted mid-phase |

### Cumulative Quality

| Milestone | Tests | Coverage | Notes |
|-----------|-------|----------|-------|
| v1.0 | 0 | 0% | Test phase deferred to v1.1 |
| v1.1 | 61 unit + 6 instrumented | ImageProcessor 96.8%, ViewModel 88.9%, data 92.9%, util 23.3% | Robolectric + JaCoCo dual exec fix required |

### Top Lessons (Verified Across Milestones)

1. **Update traceability immediately after each plan** — both v1.0 (DSYS-03) and v1.1 (all "Pending" in table) had stale traceability at milestone end; this creates confusion during `complete-milestone`
2. **Measure before setting thresholds** — RELEASE-09 needed 2 recalibration rounds because coverage wasn't measured at requirements definition time; run a quick coverage probe before committing to numbers
3. **Gap closure rounds are expected** — v1.0 had 3 (Phase 2), v1.1 had 2 (Phase 4); plan for ~20% extra plans beyond initial estimate; this is the process working correctly, not a failure
