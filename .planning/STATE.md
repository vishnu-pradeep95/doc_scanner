# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-28)

**Core value:** Every feature that exists must work flawlessly, feel delightful, and be verified — no rough edges, no untested flows.
**Current focus:** Phase 1: Stability

## Current Position

Phase: 1 of 5 (Stability)
Plan: 3 of 4 in current phase
Status: In progress
Last activity: 2026-02-28 — Completed 01-03: Fragment crash safety (high-risk) + EXIF orientation correction

Progress: [███░░░░░░░] 15%

## Performance Metrics

**Velocity:**
- Total plans completed: 3
- Average duration: 4 min
- Total execution time: 0.20 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-stability | 3 | 12 min | 4 min |

**Recent Trend:**
- Last 5 plans: 2 min, 5 min, 5 min
- Trend: -

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Roadmap: 5 phases following Stability -> Design System -> Performance -> Testing -> Release dependency chain (research-validated ordering)
- Roadmap: Tests come after fixes (Phase 4 after Phases 1-3) to avoid testing broken behavior
- 01-01: Store FilterType as enum.name String in SavedStateHandle (FilterType is not Parcelable; name-string is stable and Bundle-safe; avoids breaking on enum reordering)
- 01-01: Use savedStateHandle.getLiveData() directly as backing store (no separate MutableLiveData field) to eliminate dual-source-of-truth risk
- 01-01: Keep currentCaptureUri, pdfUri, isLoading as plain MutableLiveData (transient session state that has no useful meaning across process death)
- 01-01: No custom ViewModelFactory needed -- fragment-ktx's SavedStateViewModelFactory auto-injects SavedStateHandle when constructor requests it
- 01-02: Use Kotlin use {} blocks for PdfRenderer/ParcelFileDescriptor — guaranteed close on exception paths
- 01-02: Individual try-catch per resource in close() methods — partial cleanup if one resource fails
- 01-02: Capture requireContext().applicationContext before coroutine launch — avoids Activity context leak on IO thread
- 01-02: Remove undo/redo buttons entirely rather than disabling — non-functional UI unacceptable in portfolio app
- 01-02: 1-hour threshold for stale temp cleanup — conservative enough for active sessions, aggressive enough for orphaned files
- 01-03: Capture ctx at coroutine launch top (not inside withContext blocks) to avoid race where context becomes null between IO return and Main resume
- 01-03: Pass ctx as explicit parameter to IO-thread helper functions — prevents requireContext() on background thread
- 01-03: EXIF correction runs on main thread in handleGalleryResult (acceptable for small image counts); inside coroutine in handleImportResult
- 01-03: showImportProgress made null-safe — _binding/context checks rather than pushing responsibility to callers

### Pending Todos

None yet.

### Blockers/Concerns

- Library version verification needed: All dependency versions from research are based on May 2025 training data. Verify against Maven Central before adding to build.gradle.kts (affects Phase 4 primarily).
- Phase 1 scope: 154 requireContext() call sites audited for PagesFragment, HistoryFragment, HomeFragment (01-03 complete). Remaining audit needed for 01-04 (other fragments).
- Build verification blocked: WSL2 environment lacks Java/JDK installation. Run `./gradlew assembleDebug` on a machine with Android SDK before release.

## Session Continuity

Last session: 2026-02-28
Stopped at: Completed 01-03-PLAN.md (fragment crash safety high-risk, EXIF correction)
Resume file: None
