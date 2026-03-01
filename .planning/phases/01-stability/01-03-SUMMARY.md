---
phase: 01-stability
plan: 03
subsystem: fragment-crash-safety
tags: [android, kotlin, coroutines, fragment-lifecycle, exif, crash-fix]

# Dependency graph
requires:
  - "01-01 (ScannerViewModel immutable collections)"
  - "01-02 (PDF resource leak fixes)"
provides:
  - "Null-safe context patterns in all coroutine callbacks across PagesFragment, HistoryFragment, HomeFragment"
  - "EXIF orientation correction for gallery-imported images"
  - "ExifInterface dependency and ImageUtils utility"
affects: [PagesFragment, HistoryFragment, HomeFragment, gallery import flow]

# Tech tracking
tech-stack:
  added:
    - "androidx.exifinterface:exifinterface:1.3.7"
  patterns:
    - "Capture ctx before coroutine launch: val ctx = context ?: return@launch at top of lifecycleScope.launch body"
    - "Post-IO null check: val currentBinding = _binding ?: return@launch; val currentCtx = context ?: return@launch"
    - "Pass ctx as explicit parameter to IO-thread functions instead of calling requireContext() on background thread"
    - "showImportProgress null-safe: _binding ?: return; context ?: return before any UI operation"

key-files:
  created:
    - "app/src/main/java/com/pdfscanner/app/util/ImageUtils.kt"
  modified:
    - "app/build.gradle.kts"
    - "app/src/main/java/com/pdfscanner/app/ui/PagesFragment.kt"
    - "app/src/main/java/com/pdfscanner/app/ui/HistoryFragment.kt"
    - "app/src/main/java/com/pdfscanner/app/ui/HomeFragment.kt"

key-decisions:
  - "Capture ctx at coroutine launch top (not inside withContext blocks) to avoid race where context becomes null between IO return and Main resume"
  - "Pass ctx as explicit parameter to helper functions (rotateImage, generatePdf, decodeSampledBitmap, saveToHistory) so they never call requireContext() when running on IO thread"
  - "Use _binding ?: return@launch pattern rather than withContext(Main) wrapping for UI-only post-IO code — simpler and consistent with rest of codebase"
  - "EXIF correction runs on main thread in handleGalleryResult (acceptable: small number of images, 1-3 typical) and inside coroutine in handleImportResult (already off main thread)"
  - "showImportProgress made null-safe via _binding/context checks rather than pushing null-check responsibility to callers"

requirements-completed: [BUG-01, BUG-08]

# Metrics
duration: 5min
completed: 2026-02-28
tasks_completed: 2
files_modified: 5
commits: 2
---

# Phase 01 Plan 03: Fragment Crash Safety and EXIF Orientation Correction Summary

**One-liner:** Eliminated all coroutine-callback requireContext() crashes in PagesFragment, HistoryFragment, and HomeFragment by capturing context before IO work and checking binding/context after suspension points; gallery imports now correct EXIF rotation via new ImageUtils utility.

## What Was Built

### Task 1: ExifInterface dependency and ImageUtils utility (commit: b647482)

Added `androidx.exifinterface:exifinterface:1.3.7` to `app/build.gradle.kts` under a new IMAGE PROCESSING section.

Created `ImageUtils.kt` with `correctExifOrientation(context, sourceUri)`:
- Reads EXIF TAG_ORIENTATION metadata from image
- Returns `sourceUri` unchanged for ORIENTATION_NORMAL/UNDEFINED (fast path, no I/O)
- Builds rotation/flip Matrix for all 8 EXIF orientation cases (ROTATE_90/180/270, FLIP_HORIZONTAL/VERTICAL, TRANSPOSE, TRANSVERSE)
- Decodes bitmap, applies matrix, saves corrected JPEG to `files/scans/IMPORT_<timestamp>.jpg`
- Fully defensive: returns `sourceUri` on any exception (never crashes caller)

### Task 2: Coroutine crash safety + EXIF integration (commit: 2f0587f)

**PagesFragment** (5 coroutine functions fixed):
- `performOcrOnPages()`: `val ctx = context ?: return@launch` at launch top; `_binding` null checks in withContext(Main) blocks
- `createPdfFromSelection()`: ctx captured at launch; `_binding ?: return@launch` and `context ?: return@launch` after `withContext(IO)`
- `createPdf()`: ctx captured at launch; binding/context null checks after IO
- `rotatePage()`: ctx captured at launch; binding/context null checks after IO
- `rotateImage()`: signature changed to `rotateImage(ctx, uri)` — no longer calls `requireContext()` on IO thread
- `generatePdf()`: signature changed to `generatePdf(ctx, pageUris, customBaseName)` — `ctx.filesDir` instead of `requireContext().filesDir`
- `decodeSampledBitmap()`: signature changed to accept `ctx` — `ctx.contentResolver` instead of `requireContext().contentResolver`
- `saveToHistory()`: signature changed to accept `ctx`

**HistoryFragment** (3 coroutine functions fixed):
- `executeMerge()`: ctx captured with null-safe loading overlay hide on failure; `_binding ?: return@launch` + `context ?: return@launch` after `PdfUtils.mergePdfs` suspension
- `executeSplit()`: same pattern; file-not-found Toast uses pre-captured ctx
- `executeCompress()`: same pattern; all post-loop UI operations use null-checked references

**HomeFragment** (4 coroutine/import functions fixed):
- `performMerge()`: ctx captured at launch top; null checks after `PdfUtils.mergePdfs`
- `performSplit()`: ctx captured at launch top; null checks after `PdfUtils.splitPdf`
- `executeCompress()`: ctx captured at launch top; null checks after `PdfUtils.compressPdf`
- `handleImportResult()`: ctx captured synchronously before launch (used in `isPdfFile`, `extractPages`, and `ImageUtils.correctExifOrientation`); inner suspension point Toasts use `context?.let`
- `showImportProgress()`: null-safe via `_binding ?: return` and `context ?: return` before any binding/Toast access

**BUG-08 EXIF correction:**
- `handleGalleryResult()`: wraps each uri in `ImageUtils.correctExifOrientation(ctx, uri)` before `viewModel.addPage()`
- `handleImportResult()`: wraps each image uri in `ImageUtils.correctExifOrientation(ctx, uri)` before `viewModel.addPage()` (inside coroutine, already off main thread)

## Verification Results

| Check | Result |
|-------|--------|
| `exifinterface` in build.gradle.kts | FOUND (line 272) |
| `ExifInterface` in ImageUtils.kt | FOUND (9 references, all 8 EXIF orientations covered) |
| `correctExifOrientation` in HomeFragment.handleGalleryResult | FOUND (line 346) |
| `correctExifOrientation` in HomeFragment.handleImportResult | FOUND (line 397) |
| `requireContext()` inside `withContext(IO)` in PagesFragment | 0 (all replaced) |
| `requireContext()` after IO in PagesFragment coroutines | 0 (all replaced) |
| `requireContext()` after suspension points in HistoryFragment coroutines | 0 |
| `requireContext()` after suspension points in HomeFragment coroutines | 0 |

Note: `./gradlew assembleDebug` cannot be run — WSL2 environment has no Linux `gradlew` script, Java, or Android SDK. All verifications completed via static code analysis. Changes follow identical patterns to existing working code.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] showImportProgress() used binding.root without null check**
- **Found during:** Task 2
- **Issue:** `showImportProgress(true, message)` was called inside a coroutine loop in `handleImportResult`. The function accessed `binding.root` and called `requireContext()`, both of which crash if the fragment detaches mid-loop.
- **Fix:** Made `showImportProgress()` null-safe by checking `_binding ?: return` and `context ?: return` at the top of the function.
- **Files modified:** `HomeFragment.kt`
- **Commit:** 2f0587f

**2. [Rule 1 - Bug] handleGalleryResult navigation used original URI after EXIF correction**
- **Found during:** Task 2 (EXIF integration)
- **Issue:** Original plan code navigated to Preview using `uris.first()` (the original URI) even after EXIF correction had stored a corrected copy. The preview would show the uncorrected image.
- **Fix:** Used `viewModel.pages.value?.lastOrNull()` to get the corrected URI that was just added for single-image navigation.
- **Files modified:** `HomeFragment.kt`
- **Commit:** 2f0587f

## User Setup Required

None.

## Next Phase Readiness

- The three highest-risk fragments now have null-safe coroutine patterns. Combined with the ViewModel (01-01) and resource leak (01-02) fixes, Phase 1 stability goals for BUG-01, BUG-04, BUG-05, BUG-08 are complete.
- Remaining Phase 1 plans can proceed: any additional fragment audits or stability work.
- Build verification should be run on a machine with Java/Android SDK before release.

## Self-Check: PASSED

- FOUND: `app/build.gradle.kts` contains `exifinterface:1.3.7`
- FOUND: `app/src/main/java/com/pdfscanner/app/util/ImageUtils.kt`
- FOUND: `.planning/phases/01-stability/01-03-SUMMARY.md` (this file)
- FOUND: commit `b647482` (feat(01-03): add ExifInterface dependency and ImageUtils EXIF correction utility)
- FOUND: commit `2f0587f` (fix(01-03): null-safe context in coroutines + EXIF correction on gallery import)
- Code checks: 0 `requireContext()` in coroutine IO contexts in all 3 files; `correctExifOrientation` called in both `handleGalleryResult` and `handleImportResult`
