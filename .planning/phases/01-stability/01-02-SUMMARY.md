---
phase: 01-stability
plan: 02
subsystem: pdf-resources
tags: [resource-leak, memory-management, temp-files, ui-cleanup]
dependency_graph:
  requires: []
  provides: [safe-pdf-resource-management, temp-file-cleanup, clean-pdf-editor-ui]
  affects: [PdfUtils, PdfAnnotationRenderer, NativePdfView, PdfViewerFragment, PdfEditorFragment, MainActivity]
tech_stack:
  added: []
  patterns: [kotlin-use-blocks, individual-try-catch-resource-cleanup, startup-cleanup]
key_files:
  created: []
  modified:
    - app/src/main/java/com/pdfscanner/app/util/PdfUtils.kt
    - app/src/main/java/com/pdfscanner/app/editor/PdfAnnotationRenderer.kt
    - app/src/main/java/com/pdfscanner/app/editor/NativePdfView.kt
    - app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt
    - app/src/main/java/com/pdfscanner/app/editor/PdfEditorFragment.kt
    - app/src/main/res/layout/fragment_pdf_editor.xml
    - app/src/main/java/com/pdfscanner/app/MainActivity.kt
decisions:
  - "Use Kotlin use {} blocks for all PdfRenderer/ParcelFileDescriptor resources to guarantee close on exception paths"
  - "Individual try-catch per resource in NativePdfView.close() and PdfViewerFragment.onDestroyView() allows partial cleanup if one resource fails"
  - "Capture requireContext().applicationContext before coroutine launch in saveAnnotatedPdf() to avoid Activity context leak on IO thread"
  - "Removed undo/redo buttons entirely rather than disabling them — non-functional UI is unacceptable in portfolio app"
  - "1-hour threshold for stale temp cleanup: conservative enough to not delete files from active sessions, aggressive enough to clean orphaned files"
metrics:
  duration_minutes: 5
  completed_date: "2026-03-01"
  tasks_completed: 2
  files_modified: 7
  commits: 2
---

# Phase 1 Plan 2: PDF Resource Leak Fixes and Undo/Redo Removal Summary

**One-liner:** Replaced manual close() calls with Kotlin use {} blocks in all 5 PDF operations, added temp file tracking and startup cleanup, removed non-functional undo/redo buttons from PDF Editor.

## What Was Built

### Task 1: PdfUtils and PdfAnnotationRenderer Resource Safety (commit: 61d920e)

All five methods in `PdfUtils.kt` now use Kotlin's `use {}` blocks for guaranteed resource cleanup:

- `mergePdfs()`: pfd, renderer, and each `openPage()` wrapped in nested `use {}` blocks
- `splitPdf()`: pfd and renderer in `use {}`, early-return for single-page PDFs now works correctly inside the block
- `extractPages()`: pfd and renderer in `use {}`, each page in `openPage().use {}`
- `compressPdf()`: pfd and renderer in `use {}`, each page in `openPage().use {}`
- `getPageCount()`: converted to idiomatic `?.use {}` chain returning `renderer.pageCount`

In `PdfAnnotationRenderer.renderAnnotatedPdf()`: `inputPfd` and `renderer` now wrapped in nested `use {}` blocks; each `openPage()` wrapped in `use {}` (critical: PdfRenderer only allows one open page at a time).

### Task 2: Temp File Cleanup, Robust Close, UI Cleanup (commit: 9793430)

**NativePdfView.kt:**
- Added `private var tempFile: File? = null` class field
- `loadPdf(context, uri)` now sets `this.tempFile = tempFile` after creating the temp copy
- `close()` refactored to individual try-catch per resource (currentPage, pdfRenderer, fileDescriptor) so a failure in one doesn't block cleanup of the others; adds `tempFile?.delete()` before nulling the field

**PdfViewerFragment.kt:**
- `onDestroyView()` now uses individual try-catch blocks per resource (pdfRenderer, fileDescriptor) and nulls references after close

**PdfEditorFragment.kt:**
- Removed `btnUndo` and `btnRedo` `setOnClickListener` blocks from `setupToolbar()` — no more "coming soon" toasts
- `saveAnnotatedPdf()`: captures `requireContext().applicationContext` and `args.pdfUri` into local variables BEFORE `lifecycleScope.launch(Dispatchers.IO)` to prevent Activity context leak

**fragment_pdf_editor.xml:**
- Removed `btnUndo` and `btnRedo` ImageButton elements from the Action Buttons Row

**MainActivity.kt:**
- Added `import java.io.File`
- Added `cleanupStaleTempFiles()` private function called from `onCreate()` after `setContentView()`
- Cleans `pdf_view_temp_*`, `temp_edit_*`, `pdf_compress_temp*` files in cacheDir older than 1 hour
- Also cleans individual files inside the `pdf_compress_temp/` directory and removes the directory if empty
- Wrapped in try-catch: cleanup is best-effort, never crashes the app

## Verification Results

| Check | Expected | Result |
|-------|----------|--------|
| `renderer.close()` in PdfUtils.kt | 0 | 0 |
| `pfd.close()` in PdfUtils.kt | 0 | 0 |
| `btnUndo` in PdfEditorFragment.kt | 0 | 0 |
| `btnRedo` in PdfEditorFragment.kt | 0 | 0 |
| `btnUndo` in fragment_pdf_editor.xml | 0 | 0 |
| `cleanupStaleTempFiles` in MainActivity.kt | 2 (decl + call) | 2 |
| `tempFile?.delete()` in NativePdfView.kt | 1 | 1 |

Note: `./gradlew assembleDebug` could not be run — the WSL2 environment has no Linux `gradlew` script (only `gradlew.bat`), no Java, and no Android SDK installed. Compilation verification relies on syntactic correctness of the Kotlin changes. The code changes follow identical patterns to existing passing code in the repository.

## Deviations from Plan

None - plan executed exactly as written.

## Requirements Satisfied

- BUG-02: PdfRenderer and ParcelFileDescriptor resources now use try-with-resources (Kotlin `use {}`) throughout all PDF operations
- BUG-03: Stale temp files cleaned up on app startup; NativePdfView tracks and deletes its own temp file on close
- BUG-07: Undo/redo buttons removed from both XML layout and Kotlin code

## Self-Check: PASSED

- `61d920e` commit exists: fix(01-02): convert PdfUtils and PdfAnnotationRenderer to use {} blocks
- `9793430` commit exists: fix(01-02): temp file cleanup, robust resource close, remove undo/redo
- All 7 modified files verified with grep checks
- All must_have artifact patterns confirmed present
