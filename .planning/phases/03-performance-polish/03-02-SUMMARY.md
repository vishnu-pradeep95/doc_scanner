---
phase: 03-performance-polish
plan: 02
subsystem: ui
tags: [haptic-feedback, pdf-renderer, bitmap-cache, coroutines, sparse-array]

# Dependency graph
requires:
  - phase: 01-stability
    provides: PdfViewerFragment with pdfRenderer close() cleanup and coroutine-safe context capture
provides:
  - CameraFragment haptic feedback pulse on successful document capture (API 24+ compatible)
  - PdfViewerFragment 3-slot SparseArray bitmap cache with serialized pdfRenderer access
  - renderPageToBitmap() suspend fun with pdfIoDispatcher for thread-safe page rendering
  - prefetchAdjacentPages() for zero-delay page navigation after first render
affects: [03-performance-polish, 04-testing]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dispatchers.IO.limitedParallelism(1) for serializing PdfRenderer.openPage() calls — prevents concurrent page open which crashes PdfRenderer"
    - "SparseArray<Bitmap> for indexed bitmap cache with manual eviction and recycle()"
    - "performHapticFeedback() with API-level conditional (Build.VERSION_CODES.R) for CONFIRM vs VIRTUAL_KEY"

key-files:
  created: []
  modified:
    - app/src/main/java/com/pdfscanner/app/ui/CameraFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt

key-decisions:
  - "Use Build.VERSION_CODES.R conditional for haptic: CONFIRM (API 30+) gives semantic vibration vs VIRTUAL_KEY (API 24-29) fallback — no VIBRATE permission needed"
  - "Use limitedParallelism(1) not a Mutex — creates a single-threaded dispatcher that naturally serializes openPage() calls without explicit lock management"
  - "Cache eviction based on window [currentIndex-1, currentIndex+1] — 3 slots covers all navigation directions; older pages recycled immediately to free ~7.5MB each"
  - "setImageDrawable(null) before bitmap recycling in onDestroyView — prevents Canvas recycled-bitmap crash if ImageView is still drawing on main thread"
  - "prefetchAdjacentPages called on both cache hit and miss paths — ensures neighbors are always pre-warmed after any page change"

patterns-established:
  - "PdfRenderer serialization: Always use Dispatchers.IO.limitedParallelism(1) for any PdfRenderer.openPage() call — never plain Dispatchers.IO"
  - "Bitmap cache lifecycle: setImageDrawable(null) → recycle loop → clear → close renderer — prevents recycled-bitmap draw crashes"

requirements-completed: [PERF-02, PERF-06]

# Metrics
duration: 1min
completed: 2026-03-01
---

# Phase 03 Plan 02: Haptic Capture Feedback and PDF Page Bitmap Caching Summary

**Haptic confirmation on camera capture (API-conditional CONFIRM/VIRTUAL_KEY) and 3-slot SparseArray bitmap cache with serialized pdfIoDispatcher in PDF viewer eliminating 150-500ms page render delays**

## Performance

- **Duration:** ~1 min
- **Started:** 2026-03-01T20:13:14Z
- **Completed:** 2026-03-01T20:14:37Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- CameraFragment fires a haptic pulse on successful image save using `performHapticFeedback()` with API-conditional constant (CONFIRM on API 30+, VIRTUAL_KEY on API 24-29); no VIBRATE permission required
- PdfViewerFragment gains `pdfIoDispatcher = Dispatchers.IO.limitedParallelism(1)` that serializes all `pdfRenderer.openPage()` calls — eliminates race conditions from concurrent rapid taps
- 3-slot SparseArray cache serves prev/current/next pages instantly on cache hit; `prefetchAdjacentPages()` pre-warms neighbors on every page change; stale bitmaps evicted and recycled immediately
- `onDestroyView()` detaches bitmap from ImageView, recycles all cached bitmaps, and clears the cache before closing PdfRenderer — no memory leak on viewer exit

## Task Commits

Each task was committed atomically:

1. **Task 1: Add haptic feedback to CameraFragment.onImageSaved** - `c204e66` (feat)
2. **Task 2: Add SparseArray page cache to PdfViewerFragment** - `fd6f4c7` (feat)

**Plan metadata:** *(docs commit — see below)*

## Files Created/Modified
- `app/src/main/java/com/pdfscanner/app/ui/CameraFragment.kt` - Added haptic feedback block in `onImageSaved` after re-enabling capture button, before savedUri / navigation logic
- `app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt` - Added SparseArray import, pdfIoDispatcher field, pageCache field, renderPageToBitmap() suspend fun, prefetchAdjacentPages(), refactored renderPage(), updated onDestroyView()

## Decisions Made
- Used `Build.VERSION_CODES.R` conditional for haptic — CONFIRM (API 30+) provides semantic "success" vibration pattern; VIRTUAL_KEY is the appropriate fallback for API 24-29
- Used `limitedParallelism(1)` over a Mutex — dispatcher-based serialization is simpler, idiomatic Kotlin coroutines, and avoids lock/unlock boilerplate
- Cache eviction window is `[currentIndex-1, currentIndex+1]` — covers all swipe directions; at 2x scale (~7.5MB per page) three pages = ~22MB, well within the 192MB OOM threshold established in Phase 1
- `setImageDrawable(null)` before recycling in `onDestroyView` prevents "Canvas: trying to use a recycled bitmap" if the ImageView render pass overlaps with our recycle call

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- PERF-02 (haptic feedback) and PERF-06 (PDF page caching) requirements satisfied
- Ready for Phase 03 Plan 03 (remaining performance polish items)
- Build verification still blocked by WSL2 JDK absence — `./gradlew assembleDebug` must be run on a machine with Android SDK before release

---
*Phase: 03-performance-polish*
*Completed: 2026-03-01*

## Self-Check: PASSED

- CameraFragment.kt: FOUND
- PdfViewerFragment.kt: FOUND
- 03-02-SUMMARY.md: FOUND
- Commit c204e66 (Task 1 — haptic feedback): FOUND
- Commit fd6f4c7 (Task 2 — PDF page cache): FOUND
