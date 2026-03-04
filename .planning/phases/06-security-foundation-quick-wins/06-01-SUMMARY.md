---
phase: 06-security-foundation-quick-wins
plan: 01
subsystem: security
tags: [FLAG_SECURE, screenshot-prevention, temp-file-security, UUID, BuildConfig]

# Dependency graph
requires: []
provides:
  - FLAG_SECURE screenshot prevention on all screens (release builds only)
  - UUID-randomized temp file names across 3 creation sites
  - Hardened temp file cleanup with no age check and finally blocks
  - BuildConfig.DEBUG gate for conditional security features
affects: [07-input-encrypted-storage, 10-hardening-polish]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "UUID.randomUUID() for all temp file naming (unpredictable names)"
    - "Finally blocks for temp directory cleanup (defense-in-depth)"
    - "BuildConfig.DEBUG conditional for release-only security features"
    - "Startup cleanup deletes ALL matching temp files regardless of age"

key-files:
  created: []
  modified:
    - app/src/main/java/com/pdfscanner/app/MainActivity.kt
    - app/build.gradle.kts
    - app/src/main/java/com/pdfscanner/app/editor/NativePdfView.kt
    - app/src/main/java/com/pdfscanner/app/editor/PdfEditorFragment.kt
    - app/src/main/java/com/pdfscanner/app/util/PdfUtils.kt

key-decisions:
  - "FLAG_SECURE conditional on BuildConfig.DEBUG to preserve screenshot test capability"
  - "Startup cleanup removes ALL matching temp files regardless of age for zero-stale guarantee"
  - "Prefix check updated from pdf_view_temp_ to pdf_view_ to match new UUID naming pattern"

patterns-established:
  - "SEC-01: FLAG_SECURE in onCreate before super.onCreate, conditional on !BuildConfig.DEBUG"
  - "SEC-05: UUID.randomUUID() for all temp file names, finally blocks for cleanup"

requirements-completed: [SEC-01, SEC-05]

# Metrics
duration: 2min
completed: 2026-03-03
---

# Phase 06 Plan 01: FLAG_SECURE & Temp File Security Summary

**FLAG_SECURE screenshot prevention gated on BuildConfig.DEBUG, UUID-randomized temp file names across 3 sites, and hardened startup cleanup with no age check**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-04T02:09:29Z
- **Completed:** 2026-03-04T02:11:37Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- FLAG_SECURE set in MainActivity.onCreate() before super.onCreate(), preventing screenshots and Recents thumbnails in release builds while preserving debug screenshot capability
- All 3 temp file creation sites (NativePdfView, PdfEditorFragment, PdfUtils) now use UUID.randomUUID() instead of predictable System.currentTimeMillis()
- PdfUtils.compressPdf temp directory wrapped in finally block for defense-in-depth cleanup
- cleanupStaleTempFiles() hardened to delete ALL matching files on startup regardless of age

## Task Commits

Each task was committed atomically:

1. **Task 1: Add FLAG_SECURE screenshot prevention with BuildConfig gate** - `0056ed6` (feat)
2. **Task 2: Randomize temp file names with UUID and harden cleanup** - `f906a6f` (feat)

## Files Created/Modified
- `app/build.gradle.kts` - Added buildConfig = true to buildFeatures (required for BuildConfig.DEBUG with AGP 8.x)
- `app/src/main/java/com/pdfscanner/app/MainActivity.kt` - FLAG_SECURE in onCreate, WindowManager import, hardened cleanupStaleTempFiles()
- `app/src/main/java/com/pdfscanner/app/editor/NativePdfView.kt` - UUID temp file naming for PDF view copies
- `app/src/main/java/com/pdfscanner/app/editor/PdfEditorFragment.kt` - UUID temp file naming for editor copies
- `app/src/main/java/com/pdfscanner/app/util/PdfUtils.kt` - UUID temp file naming for compress pages, finally block cleanup

## Decisions Made
- FLAG_SECURE conditional on BuildConfig.DEBUG (locked decision from STATE.md) to preserve screenshot test capability in debug builds
- Startup cleanup removes ALL matching temp files regardless of age, ensuring zero stale files survive app restart
- Updated prefix check from `pdf_view_temp_` to `pdf_view_` to match new UUID naming pattern

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- SEC-01 and SEC-05 requirements complete
- Ready for plan 06-02 (remaining security quick wins)
- BuildConfig.DEBUG pattern established for future release-only security features

## Self-Check: PASSED

All 5 modified files exist. Both task commits (0056ed6, f906a6f) verified in git log. Summary file created.

---
*Phase: 06-security-foundation-quick-wins*
*Completed: 2026-03-03*
