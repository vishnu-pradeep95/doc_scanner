---
phase: 08-file-encryption-at-rest
plan: 02
subsystem: security
tags: [file-encryption, streaming-aead, secure-file-io, pdf-encryption, bitmap-encryption, decrypt-to-temp]

# Dependency graph
requires:
  - phase: 08-file-encryption-at-rest
    provides: SecureFileManager singleton with encryptBitmapToFile, encryptPdfToFile, decryptToTempFile, decryptToBitmap, secureDelete
provides:
  - All utility class file I/O routed through SecureFileManager (ImageProcessor, ImageUtils, PdfUtils, PdfPageExtractor)
  - All editor component file I/O routed through SecureFileManager (PdfAnnotationRenderer, PdfEditorViewModel, NativePdfView, SignatureDialogFragment)
  - SecureFileManager.encryptBitmapToFile with configurable CompressFormat parameter (JPEG/PNG)
  - Decrypt-to-temp pattern for PdfRenderer in NativePdfView and PdfAnnotationRenderer
affects: [08-03, 08-04, 10-hardening-polish]

# Tech tracking
tech-stack:
  added: []
  patterns: [SecureFileManager write-path integration, decrypt-to-temp for PdfRenderer, configurable CompressFormat for bitmap encryption]

key-files:
  created: []
  modified:
    - app/src/main/java/com/pdfscanner/app/util/ImageProcessor.kt
    - app/src/main/java/com/pdfscanner/app/util/ImageUtils.kt
    - app/src/main/java/com/pdfscanner/app/util/PdfUtils.kt
    - app/src/main/java/com/pdfscanner/app/util/PdfPageExtractor.kt
    - app/src/main/java/com/pdfscanner/app/util/SecureFileManager.kt
    - app/src/main/java/com/pdfscanner/app/editor/PdfAnnotationRenderer.kt
    - app/src/main/java/com/pdfscanner/app/editor/PdfEditorViewModel.kt
    - app/src/main/java/com/pdfscanner/app/editor/NativePdfView.kt
    - app/src/main/java/com/pdfscanner/app/editor/SignatureDialogFragment.kt

key-decisions:
  - "Added CompressFormat parameter to SecureFileManager.encryptBitmapToFile (default JPEG) to support PNG signature files without creating a separate method"
  - "PdfAnnotationRenderer decrypt-to-temp only for file:// URIs (content:// URIs use contentResolver directly since external files are not encrypted by this app)"

patterns-established:
  - "Write-path pattern: replace FileOutputStream(...).use { bitmap.compress/pdfDoc.writeTo } with SecureFileManager.encryptBitmapToFile/encryptPdfToFile"
  - "Read-path pattern for PdfRenderer: SecureFileManager.decryptToTempFile -> ParcelFileDescriptor.open(temp) -> cleanup in finally/close"
  - "cacheDir temp files are NOT encrypted (anti-pattern avoidance per research)"

requirements-completed: [SEC-09]

# Metrics
duration: 4min
completed: 2026-03-05
---

# Phase 8 Plan 2: Write/Read Path Integration Summary

**All document I/O in 8 utility/editor files routed through SecureFileManager: encrypted bitmap/PDF writes, decrypt-to-temp for PdfRenderer, encrypted signature save/load with PNG format support**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-05T00:18:24Z
- **Completed:** 2026-03-05T00:22:17Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- Routed all bitmap writes in ImageProcessor, ImageUtils, PdfPageExtractor through SecureFileManager.encryptBitmapToFile
- Routed all PDF writes in PdfUtils (mergePdfs, splitPdf, extractPages, compressPdf) and PdfAnnotationRenderer through SecureFileManager.encryptPdfToFile
- Added decrypt-to-temp pattern in NativePdfView and PdfAnnotationRenderer for PdfRenderer input (with cleanup in close/finally)
- Encrypted signature save/load/delete in PdfEditorViewModel via encryptBitmapToFile(PNG), decryptToBitmap, secureDelete
- Replaced BitmapFactory.decodeFile in SignatureDialogFragment adapter with SecureFileManager.decryptToBitmap
- Added configurable CompressFormat parameter to SecureFileManager.encryptBitmapToFile for PNG signature support

## Task Commits

Each task was committed atomically:

1. **Task 1: Encrypt write paths and decrypt read paths in utility classes** - `d120b6c` (feat)
2. **Task 2: Encrypt write paths and decrypt read paths in editor components** - `600f59e` (feat)

## Files Created/Modified
- `app/src/main/java/com/pdfscanner/app/util/ImageProcessor.kt` - saveBitmapToFile uses encryptBitmapToFile
- `app/src/main/java/com/pdfscanner/app/util/ImageUtils.kt` - correctExifOrientation uses encryptBitmapToFile
- `app/src/main/java/com/pdfscanner/app/util/PdfUtils.kt` - All 4 PDF write sites use encryptPdfToFile; cacheDir temp left unencrypted
- `app/src/main/java/com/pdfscanner/app/util/PdfPageExtractor.kt` - extractPage uses encryptBitmapToFile
- `app/src/main/java/com/pdfscanner/app/util/SecureFileManager.kt` - encryptBitmapToFile gains format parameter
- `app/src/main/java/com/pdfscanner/app/editor/PdfAnnotationRenderer.kt` - Encrypted PDF output, decrypt-to-temp for PdfRenderer input
- `app/src/main/java/com/pdfscanner/app/editor/PdfEditorViewModel.kt` - Encrypted signature save (PNG), decrypt load, secure delete
- `app/src/main/java/com/pdfscanner/app/editor/NativePdfView.kt` - Decrypt-to-temp before PdfRenderer with cleanup in close()
- `app/src/main/java/com/pdfscanner/app/editor/SignatureDialogFragment.kt` - decryptToBitmap replaces BitmapFactory.decodeFile

## Decisions Made
- Added `format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG` parameter to `SecureFileManager.encryptBitmapToFile()` rather than creating a separate `encryptBitmapToFilePng()` method. This keeps the API clean while supporting both JPEG (documents) and PNG (signatures) formats.
- PdfAnnotationRenderer uses decrypt-to-temp only for `file://` URIs. `content://` URIs go through contentResolver directly since external files are not encrypted by this app.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All utility and editor file I/O now routes through SecureFileManager
- Plan 08-03 (UI fragment and adapter integration) can proceed with remaining read paths
- Plan 08-04 (Migration and lifecycle) can wire migrateExistingFiles into app startup
- No blockers or concerns

## Self-Check: PASSED

All 9 modified files verified on disk. Both task commits (d120b6c, 600f59e) verified in git log.

---
*Phase: 08-file-encryption-at-rest*
*Completed: 2026-03-05*
