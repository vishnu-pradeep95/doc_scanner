---
phase: 02-design-system
plan: "02"
subsystem: ui
tags: [coil, image-loading, recyclerview, adapter, thumbnail, bitmap, android]

# Dependency graph
requires:
  - phase: 01-stability
    provides: Fixed fragment lifecycles and coroutine safety patterns used as reference
provides:
  - Coil 3.4.0 integrated as app-wide image loading library
  - PagesAdapter using Coil for lifecycle-safe thumbnail loading
  - HistoryAdapter showing document thumbnail via Coil with fallback
  - item_document.xml with imageDocumentThumbnail ImageView
affects:
  - 02-design-system subsequent plans (UI polish)
  - Phase 3 (PDF thumbnail rendering upgrade using PdfRenderer)

# Tech tracking
tech-stack:
  added:
    - "io.coil-kt.coil3:coil:3.4.0 — Kotlin-first image loading with automatic LruCache, lifecycle cancellation, and placeholder/error drawables"
  patterns:
    - "ImageView.load(uri) { crossfade(true); placeholder(R.drawable.x); error(R.drawable.x) } — standard Coil call pattern for all thumbnail loading"
    - "Coil auto-cancels requests when ImageView detaches — no manual coroutine scope needed in adapters"

key-files:
  created: []
  modified:
    - app/build.gradle.kts
    - app/src/main/java/com/pdfscanner/app/adapter/PagesAdapter.kt
    - app/src/main/java/com/pdfscanner/app/adapter/HistoryAdapter.kt
    - app/src/main/res/layout/item_document.xml

key-decisions:
  - "Use Coil 3.4.0 base artifact only — no coil-compose or coil-network (loads local file URIs, no network needed)"
  - "HistoryAdapter PDF thumbnail: load file URI via Coil with error drawable fallback — Coil cannot render PDF pages (PdfRenderer thumbnail is Phase 3 scope)"
  - "Replace static mascot ImageView in item_document.xml with imageDocumentThumbnail (id'd) so Coil can target it"
  - "Remove adapterScope CoroutineScope from PagesAdapter — Coil handles its own coroutine/lifecycle management"

patterns-established:
  - "Coil pattern: all thumbnail ImageViews use .load(uri) with placeholder + error drawables for consistent UX"
  - "No manual LruCache in adapters — Coil provides app-level disk + memory cache automatically"

requirements-completed: [DSYS-03]

# Metrics
duration: 2min
completed: 2026-03-01
---

# Phase 2 Plan 02: Coil Image Loading Integration Summary

**Coil 3.4.0 replaces manual BitmapFactory + LruCache in PagesAdapter, eliminating OOM risk and adapterScope leak; HistoryAdapter gains document thumbnail via Coil with ic_cartoon_document fallback**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-01T03:55:15Z
- **Completed:** 2026-03-01T03:56:55Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Removed 107 lines of manual bitmap loading code (LruCache, adapterScope, loadThumbnail, calculateInSampleSize) from PagesAdapter
- PagesAdapter now uses `binding.imageThumbnail.load(uri)` with crossfade, placeholder, and error drawable — zero OOM risk, zero lifecycle leaks
- HistoryAdapter loads document thumbnail into `imageDocumentThumbnail` view via Coil; gracefully falls back to `ic_cartoon_document` if file missing or unrenderable
- item_document.xml updated: static mascot icon replaced with id'd `imageDocumentThumbnail` ImageView that Coil can target

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Coil dependency to build.gradle.kts** - `2504043` (chore)
2. **Task 2: Replace PagesAdapter manual bitmap loading with Coil + add HistoryAdapter thumbnail** - `7388cb2` (feat)

**Plan metadata:** _(final docs commit — see below)_

## Files Created/Modified
- `app/build.gradle.kts` - Added `io.coil-kt.coil3:coil:3.4.0` in IMAGE LOADING section
- `app/src/main/java/com/pdfscanner/app/adapter/PagesAdapter.kt` - Removed LruCache/adapterScope/BitmapFactory; replaced with Coil `.load()` call
- `app/src/main/java/com/pdfscanner/app/adapter/HistoryAdapter.kt` - Added Coil imports and imageDocumentThumbnail loading with file-exists guard
- `app/src/main/res/layout/item_document.xml` - Replaced static mascot icon with `imageDocumentThumbnail` ImageView (56x72dp, centerCrop)

## Decisions Made
- Used Coil 3.4.0 base artifact only (no coil-compose, no coil-network) — app is View-based and loads only local file URIs
- HistoryAdapter PDF thumbnail: Coil cannot render PDF pages natively; loads file URI and falls back to error drawable (consistent document icon). Real PDF page thumbnails are Phase 3 scope (PdfRenderer)
- Replaced the existing static mascot `ImageView` (no id) in item_document.xml with an id'd `imageDocumentThumbnail` so HistoryAdapter view binding can reference it
- adapterScope CoroutineScope removed from PagesAdapter — Coil manages its own coroutines and automatically cancels inflight requests when the bound ImageView detaches from window

## Deviations from Plan

None - plan executed exactly as written. The item_document.xml already had an ImageView in the leftmost position (static mascot, no id); replaced it with the id'd thumbnail view as the plan anticipated.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Coil is now the established image loading pattern for the app — all future thumbnail views should use `.load()` with placeholder/error drawables
- Phase 3 can upgrade HistoryAdapter thumbnail to real PDF page rendering using PdfRenderer + Coil custom fetcher
- No blockers for proceeding to 02-03

---
*Phase: 02-design-system*
*Completed: 2026-03-01*

## Self-Check: PASSED

- FOUND: app/build.gradle.kts
- FOUND: PagesAdapter.kt
- FOUND: HistoryAdapter.kt
- FOUND: item_document.xml
- FOUND: 02-02-SUMMARY.md
- FOUND: commit 2504043 (chore: Coil dependency)
- FOUND: commit 7388cb2 (feat: Coil adapter refactoring)
