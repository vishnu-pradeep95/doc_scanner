---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Quality Gates
status: planning
last_updated: "2026-03-01"
progress:
  total_phases: 2
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-01 after v1.1 milestone started)

**Core value:** Every feature that exists must work flawlessly, feel delightful, and be verified — no rough edges, no untested flows.
**Current focus:** v1.1 Quality Gates — Phase 4 (Test Coverage) → Phase 5 (Release Readiness)

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Requirements defined, roadmap TBD
Last activity: 2026-03-01 — Milestone v1.1 started, REQUIREMENTS.md created

## Accumulated Context

### Key Decisions (Full log in PROJECT.md)

Critical decisions for v1.1 planning:

- Transitions must be set in `onCreate()` not `onViewCreated()` — transition system ignores post-creation changes
- `limitedParallelism(1)` for PdfRenderer serialization — use instead of Mutex
- Coil 2.7.0 (not 3.x) — Kotlin 1.9 compatibility
- `tools:text` for programmatically-set TextViews — not `android:text`
- Context capture: `val ctx = context ?: return@launch` at TOP of every `lifecycleScope.launch` body
- `?attr/colorOnSurface*` in TextAppearance styles — not direct color references

### Testing Decisions (from v1.1 research)

- JaCoCo counter type MUST be LINE (not BRANCH) — coroutines inflate BRANCH by 15-25% with synthetic state-machine branches
- Detekt MUST stay at 1.23.x — Detekt 2.x targets Kotlin 2.x and will fail with Kotlin 1.9.21
- coroutines-test MUST match coroutines-android version exactly (both 1.7.3) — version mismatch causes NoSuchMethodError
- MockK for Android instrumented tests requires TWO artifacts: `mockk-android` + `mockk-agent`
- CameraX and ML Kit are INCOMPATIBLE with Robolectric (UnsatisfiedLinkError) — instrumented tests only
- Navigation 2.7.x has known LeakCanary false positive (AbstractAppBarOnDestinationChangedListener) — library bug, not app code
- Use `UnconfinedTestDispatcher` + `runTest` (NOT deprecated `TestCoroutineDispatcher` / `runBlockingTest`)
- `InstantTaskExecutorRule` required in EVERY ViewModel test class or LiveData assertions return null
- Detekt baseline: generate ONCE from unmodified codebase, commit immediately, NEVER generate in CI

### Blockers/Concerns

- **Build environment**: WSL2 lacks Java/JDK — cannot run `./gradlew assembleDebug/Release`. RELEASE-04 (real-device E2E) requires host machine with Android Studio. All unit tests and static analysis (Detekt, Lint) CAN run in WSL2 after JDK is resolved or via Android Studio.
- **Library versions**: All versions verified March 2026 — MockK 1.14.7, Robolectric 4.16, Espresso 3.7.0, fragment-testing 1.8.9, LeakCanary 2.14, Detekt 1.23.8. See .planning/research/STACK.md for complete build.gradle.kts diff.
- **OcrProcessor interface**: PITFALLS.md recommends wrapping ML Kit behind an OcrProcessor interface before writing TEST-04. Verify at Phase 4 start whether ocr/OcrProcessor.kt already exists as an interface or needs one created.
- **Navigation 2.7.x library leak**: LeakCanary will fire for AbstractAppBarOnDestinationChangedListener — document as library bug immediately to avoid wasted investigation. Consider Navigation 2.8+ upgrade or LeakCanary named exclusion.

### Pending Todos

None.

## Session Continuity

Last session: 2026-03-01
Stopped at: v1.1 milestone initialized — REQUIREMENTS.md created, roadmap TBD
Resume file: None
