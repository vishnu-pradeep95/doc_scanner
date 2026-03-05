---
phase: 08-file-encryption-at-rest
plan: 03
subsystem: security
tags: [tink, streaming-aead, file-encryption, secure-delete, coil, pdf-renderer, camerax]

# Dependency graph
requires:
  - phase: 08-file-encryption-at-rest
    plan: 01
    provides: SecureFileManager singleton with encrypt/decrypt/secureDelete API
provides:
  - All UI fragments route file I/O through SecureFileManager
  - All adapters decrypt encrypted files before display
  - DocumentHistory uses secureDelete for all file removals (SEC-10 complete)
  - PdfViewerFragment decrypt-to-temp with cleanup in onDestroyView
affects: [08-04, 09-biometric-app-lock, 10-hardening-polish]

# Tech tracking
tech-stack:
  added: []
  patterns: [openInputStreamForUri helper for file:// vs content:// decryption routing, decrypt-to-temp-file pattern for PdfRenderer, lifecycleScope-based async decrypt for RecyclerView adapters]

key-files:
  created: []
  modified:
    - app/src/main/java/com/pdfscanner/app/ui/CameraFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PreviewFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PagesFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt
    - app/src/main/java/com/pdfscanner/app/adapter/PagesAdapter.kt
    - app/src/main/java/com/pdfscanner/app/adapter/HistoryAdapter.kt
    - app/src/main/java/com/pdfscanner/app/adapter/RecentDocumentsAdapter.kt
    - app/src/main/java/com/pdfscanner/app/data/DocumentHistory.kt

key-decisions:
  - "HistoryAdapter now renders PDF thumbnails via decrypt-to-temp + PdfRenderer instead of Coil URI loading (Coil cannot render PDFs or encrypted files)"
  - "PagesAdapter uses findViewTreeLifecycleOwner().lifecycleScope for async decrypt since RecyclerView adapters lack their own lifecycleScope"
  - "Encrypt-in-place after CameraX capture and CanHub crop is fire-and-forget (millisecond-fast, app-private storage)"

patterns-established:
  - "openInputStreamForUri: file:// URIs go through SecureFileManager.decryptFromFile, content:// URIs use contentResolver"
  - "Adapter decrypt pattern: findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) for async decryption in RecyclerView bind()"
  - "Decrypt-to-temp-file with finally-delete pattern for PdfRenderer in adapters and PdfViewerFragment"

requirements-completed: [SEC-09, SEC-10]

# Metrics
duration: 5min
completed: 2026-03-05
---

# Phase 8 Plan 3: UI Fragment & Adapter Encryption Integration Summary

**All 8 UI/adapter/data files route file I/O through SecureFileManager -- CameraX capture encrypt-in-place, encrypted bitmap display via decryptToBitmap, encrypted PDF viewing via decrypt-to-temp, secure delete in DocumentHistory and PreviewFragment retake**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-05T00:18:55Z
- **Completed:** 2026-03-05T00:23:56Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- CameraFragment encrypts captures in-place immediately after CameraX OnImageSavedCallback
- PreviewFragment decrypts encrypted scans for display, encrypts crop output, uses secureDelete on retake
- PagesFragment decrypts scan images for PDF generation, encrypts PDF output and rotated images
- PdfViewerFragment decrypts to temp file for PdfRenderer with automatic cleanup in onDestroyView
- PagesAdapter and HistoryAdapter load thumbnails through SecureFileManager decrypt APIs
- RecentDocumentsAdapter decrypts PDFs to temp for PdfRenderer thumbnail rendering
- DocumentHistory.removeDocument and clearHistory use secureDelete (SEC-10 complete)

## Task Commits

Each task was committed atomically:

1. **Task 1: Encrypt/decrypt in UI fragments** - `f0e10ed` (feat)
2. **Task 2: Decrypt in adapters and wire secure delete** - `ac987ed` (feat)

## Files Created/Modified
- `app/src/main/java/com/pdfscanner/app/ui/CameraFragment.kt` - Post-capture encrypt-in-place via lifecycleScope
- `app/src/main/java/com/pdfscanner/app/ui/PreviewFragment.kt` - Decrypt for display, encrypt crop output, secure delete on retake
- `app/src/main/java/com/pdfscanner/app/ui/PagesFragment.kt` - Decrypt scans for PDF generation, encrypt PDF output and rotated images
- `app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt` - Decrypt-to-temp for PdfRenderer with cleanup in onDestroyView
- `app/src/main/java/com/pdfscanner/app/adapter/PagesAdapter.kt` - Coil loads decrypted bitmaps for scan thumbnails
- `app/src/main/java/com/pdfscanner/app/adapter/HistoryAdapter.kt` - Decrypt-to-temp PDF for PdfRenderer thumbnail generation
- `app/src/main/java/com/pdfscanner/app/adapter/RecentDocumentsAdapter.kt` - Decrypt-to-temp PDF for PdfRenderer thumbnails with cleanup
- `app/src/main/java/com/pdfscanner/app/data/DocumentHistory.kt` - Secure delete in removeDocument and clearHistory (SEC-10)

## Decisions Made
- HistoryAdapter now generates PDF thumbnails via decrypt-to-temp + PdfRenderer instead of using Coil with `Uri.fromFile()`. This is an improvement: Coil cannot render PDFs natively (it was always showing the error placeholder), and now encrypted PDFs get proper first-page thumbnail rendering.
- PagesAdapter uses `findViewTreeLifecycleOwner()?.lifecycleScope` for async decryption since RecyclerView adapters do not have their own lifecycleScope. Falls back to direct Coil URI loading if no lifecycle owner is available.
- Encrypt-in-place after CameraX capture and CanHub crop output is fire-and-forget (not awaited before navigation). The encryption is millisecond-fast for single JPEGs and files are in app-private storage.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] HistoryAdapter PDF thumbnail rendering via PdfRenderer**
- **Found during:** Task 2 (HistoryAdapter)
- **Issue:** Plan mentioned "check if it's loading a PDF file or an image" -- inspection revealed it was loading PDF files via Coil which cannot render PDFs (always showed placeholder). With encryption, Coil can't even read the file header.
- **Fix:** Added `renderPdfThumbnail()` helper that decrypts to temp, opens with PdfRenderer, renders page 0, and cleans up. This provides actual PDF preview thumbnails instead of always-placeholder.
- **Files modified:** app/src/main/java/com/pdfscanner/app/adapter/HistoryAdapter.kt
- **Verification:** Build compiles cleanly
- **Committed in:** ac987ed (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical functionality)
**Impact on plan:** Plan anticipated this case ("Check which case applies, then implement accordingly"). The PDF thumbnail rendering is an improvement over the previous placeholder-only behavior. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All user-facing file I/O paths now route through SecureFileManager
- Plan 08-04 (Migration & Verification) can proceed with confidence that all read/write/delete paths are covered
- No blockers or concerns

## Self-Check: PASSED

All 8 modified files verified on disk. Both task commits (f0e10ed, ac987ed) verified in git log.

---
*Phase: 08-file-encryption-at-rest*
*Completed: 2026-03-05*
