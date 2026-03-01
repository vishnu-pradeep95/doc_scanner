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

## Cross-Milestone Trends

### Process Evolution

| Milestone | Timeline | Phases | Key Change |
|-----------|----------|--------|------------|
| v1.0 | 2 days | 3 | First milestone — baseline established |

### Cumulative Quality

| Milestone | Tests | Coverage | Notes |
|-----------|-------|----------|-------|
| v1.0 | 0 | 0% | Test phase deferred to v1.1 |

### Top Lessons (Verified Across Milestones)

1. *(Pending — single milestone so far)*
