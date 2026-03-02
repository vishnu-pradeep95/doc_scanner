---
phase: 05-release-readiness
plan: 01
subsystem: testing
tags: [detekt, static-analysis, leakcanary, kotlin, android, quality]

# Dependency graph
requires:
  - phase: 04-test-coverage
    provides: working test infrastructure and JaCoCo coverage; confirmed Detekt 1.23.x constraint
provides:
  - Detekt 1.23.8 static analysis passing clean (zero new blocking errors via baseline)
  - LeakCanary 2.14 as debugImplementation dependency (auto-installs via ContentProvider)
  - config/detekt/detekt.yml — generated default config (1066 lines)
  - config/detekt/detekt-baseline.xml — baseline capturing all pre-existing violations
  - KNOWN_LEAKS.md — triage document for Navigation 2.7.x AbstractAppBarOnDestinationChangedListener leak
affects: [05-02-performance, 05-03-e2e-release]

# Tech tracking
tech-stack:
  added:
    - io.gitlab.arturbosch.detekt:detekt:1.23.8 (static analysis)
    - io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8 (formatting check, no autoCorrect)
    - com.squareup.leakcanary:leakcanary-android:2.14 (debug memory leak detection)
  patterns:
    - "Detekt baseline pattern: generate from unmodified codebase FIRST, commit immediately — suppresses pre-existing violations without hiding new ones"
    - "detekt-formatting as check-only (no autoCorrect) to avoid conflicts with baseline generation"
    - "LeakCanary auto-install via ContentProvider — zero Application subclass changes required"

key-files:
  created:
    - config/detekt/detekt.yml (1066-line generated default Detekt config)
    - config/detekt/detekt-baseline.xml (baseline capturing pre-existing violations)
    - KNOWN_LEAKS.md (triage for Navigation 2.7.x library leak)
  modified:
    - build.gradle.kts (detekt plugin declared with apply false)
    - app/build.gradle.kts (detekt plugin applied, detekt{} block, detektPlugins + debugImplementation dependencies)

key-decisions:
  - "Detekt MUST stay at 1.23.x — 2.x requires Kotlin 2.x (project locked at 1.9.21)"
  - "detekt-formatting runs as check-only (no autoCorrect=true) to avoid baseline generation conflicts"
  - "LeakCanary integration requires only debugImplementation — no Application subclass changes needed"
  - "Navigation 2.7.x AbstractAppBarOnDestinationChangedListener leak documented as library bug; do NOT upgrade to Navigation 2.8.x (migration risk across 8 fragments)"
  - "Baseline generated from unmodified codebase before any Phase 5 code changes (correct ordering)"

patterns-established:
  - "Pattern 1: Detekt baseline — run detektBaseline ONCE on unmodified codebase, commit immediately; never regenerate mid-phase"
  - "Pattern 2: Static analysis gate — ./gradlew detekt must pass clean in CI before any PR merge"

requirements-completed: [RELEASE-01, RELEASE-08]

# Metrics
duration: 3min
completed: 2026-03-02
---

# Phase 5 Plan 01: Detekt Static Analysis and LeakCanary Setup Summary

**Detekt 1.23.8 configured with pre-existing violation baseline (BUILD SUCCESSFUL, zero new errors); LeakCanary 2.14 added as debugImplementation for debug-build memory leak detection; Navigation 2.7.x library leak triaged and documented in KNOWN_LEAKS.md**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-03-02T03:26:53Z
- **Completed:** 2026-03-02T03:29:13Z
- **Tasks:** 4 (3 auto-tasks + 1 human-verify checkpoint — user approved 2026-03-01)
- **Files modified:** 5

## Accomplishments

- Detekt 1.23.8 integrated into root and app build files; `./gradlew detekt` passes BUILD SUCCESSFUL with zero new blocking errors
- `config/detekt/detekt-baseline.xml` (539 entries) generated from unmodified codebase, capturing all pre-existing violations
- LeakCanary 2.14 added as `debugImplementation` — zero code changes required beyond dependency declaration (ContentProvider auto-install)
- `KNOWN_LEAKS.md` documents the Navigation 2.7.x `AbstractAppBarOnDestinationChangedListener` library leak with triage rationale and optional suppression snippet

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Detekt plugin to root and app build files** - `7a9e54b` (feat)
2. **Task 2: Generate Detekt config and baseline on unmodified codebase** - `91122ec` (chore)
3. **Task 3: Document Navigation 2.7.x LeakCanary triage** - `edca7f4` (docs)
4. **Task 4: RELEASE-08 device verification** - USER APPROVED (human-verify checkpoint resolved)

**Plan metadata:** `[committed with STATE.md + ROADMAP.md update]`

## Files Created/Modified

- `/home/vishnu/projects/doc_scanner/build.gradle.kts` — Detekt plugin declared (`id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false`)
- `/home/vishnu/projects/doc_scanner/app/build.gradle.kts` — Detekt plugin applied, `detekt {}` config block, `detektPlugins` + `debugImplementation` dependencies
- `/home/vishnu/projects/doc_scanner/config/detekt/detekt.yml` — 1066-line generated default Detekt configuration
- `/home/vishnu/projects/doc_scanner/config/detekt/detekt-baseline.xml` — 539-entry baseline capturing all pre-existing violations
- `/home/vishnu/projects/doc_scanner/KNOWN_LEAKS.md` — Triage document for Navigation 2.7.x library leak

## Decisions Made

- Detekt stays at 1.23.x (confirmed — 2.x requires Kotlin 2.x; project is locked at Kotlin 1.9.21 per prior phases)
- `detekt-formatting` runs as check/report only — `autoCorrect` NOT set, avoids conflicts with baseline generation
- LeakCanary integration is purely via dependency declaration; ContentProvider handles initialization automatically
- Navigation 2.7.x `AbstractAppBarOnDestinationChangedListener` leak treated as library bug, not app code failure; Navigation NOT upgraded to 2.8.x due to migration risk

## Deviations from Plan

None — plan executed exactly as written. All steps ran in the prescribed order (config generation, then baseline generation, then detekt verification).

## Issues Encountered

None. Detekt task registered successfully on first attempt. Baseline generated cleanly with 539 pre-existing violations captured. LeakCanary dependency resolved without version conflicts (minSdk 24 satisfies LeakCanary 2.14's minSdk 21 requirement).

## User Setup Required

None — Task 4 (RELEASE-08) device verification completed. User exercised all 8 fragment flows on physical device, confirmed zero retained app-code leaks. Navigation 2.7.x `AbstractAppBarOnDestinationChangedListener` leak (if present) matched KNOWN_LEAKS.md triage. User typed "approved" to close the checkpoint.

## Next Phase Readiness

- RELEASE-01 fully satisfied: `./gradlew detekt` passes clean, baseline in place, detekt-formatting is check-only
- RELEASE-08 fully satisfied: LeakCanary dependency added, leak documented, zero app-code leaks confirmed on device (user approved 2026-03-01)
- Phase 5 Plan 02 complete (05-02-SUMMARY.md exists); Phase 5 Plan 03 (ProGuard/R8 + release APK E2E) is the remaining plan

---
*Phase: 05-release-readiness*
*Completed: 2026-03-01 (all 4 tasks complete including device checkpoint)*
