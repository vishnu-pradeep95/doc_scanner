---
phase: 03-performance-polish
plan: 01
subsystem: ui
tags: [edge-to-edge, WindowInsets, CoordinatorLayout, Snackbar, undo, Material3]

# Dependency graph
requires:
  - phase: 02-design-system
    provides: Theme.PDFScanner.Cartoon with Material3 base, fragment layouts established

provides:
  - "WindowCompat.enableEdgeToEdge(window) active in MainActivity"
  - "All 8 fragments handle system bar insets via ViewCompat.setOnApplyWindowInsetsListener"
  - "fragment_pages.xml has CoordinatorLayout root (Snackbar swipe-to-dismiss enabled)"
  - "Single-page delete: Snackbar with Undo (no AlertDialog)"
  - "Bulk-page delete: Snackbar with Undo (no MaterialAlertDialogBuilder confirmation)"
  - "ScannerViewModel.insertPage() and insertPages() for undo restoration"

affects:
  - 03-02-performance-polish
  - 03-03-performance-polish

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "WindowCompat.enableEdgeToEdge(window) in onCreate() before setContentView()"
    - "ViewCompat.setOnApplyWindowInsetsListener + updatePadding for each system-bar-adjacent view"
    - "Snackbar.make(binding.root).setAction(R.string.undo) for recoverable destructive actions"
    - "CoordinatorLayout as fragment root for Snackbar anchor and swipe-to-dismiss"

key-files:
  created: []
  modified:
    - app/src/main/java/com/pdfscanner/app/MainActivity.kt
    - app/src/main/res/values/themes_cartoon.xml
    - app/src/main/java/com/pdfscanner/app/ui/HomeFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/CameraFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PreviewFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PagesFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/HistoryFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/SettingsFragment.kt
    - app/src/main/java/com/pdfscanner/app/editor/PdfEditorFragment.kt
    - app/src/main/res/layout/fragment_pages.xml
    - app/src/main/java/com/pdfscanner/app/viewmodel/ScannerViewModel.kt
    - app/src/main/res/values/strings.xml

key-decisions:
  - "enableEdgeToEdge() called after super.onCreate() but before binding inflation — required order for window flag application"
  - "Remove android:statusBarColor and android:navigationBarColor from theme; keep windowLightStatusBar and windowLightNavigationBar (control icon tint, not bar color)"
  - "HomeFragment has no dedicated toolbar ID — top inset applied to CoordinatorLayout root, bottom to recyclerRecentDocs"
  - "CameraFragment: bottom inset only to controlsLayout — full-screen PreviewView must not be padded"
  - "PagesFragment: 5 inset listeners (both toolbars + both button layouts + recyclerPages) to cover both normal/selection modes"
  - "CoordinatorLayout wraps existing ConstraintLayout in fragment_pages.xml — all constraint references preserved inside inner ConstraintLayout"
  - "Snackbar undo pattern: commit deletion immediately, restore on undo tap — avoids dialog interruption"
  - "insertPages() sorts entries ascending before inserting so each prior insertion does not shift subsequent indices"
  - "pages_deleted plural resource used for bulk-delete message (1 page deleted / N pages deleted)"

patterns-established:
  - "Snackbar undo for all recoverable destructive actions (no confirmation dialogs)"
  - "CoordinatorLayout as root for any fragment that shows Snackbars"
  - "Edge-to-edge inset pattern: setOnApplyWindowInsetsListener returns windowInsets (not CONSUMED) so siblings also receive insets"

requirements-completed:
  - PERF-03
  - PERF-05

# Metrics
duration: 5min
completed: 2026-03-01
---

# Phase 03 Plan 01: Edge-to-Edge Display + Snackbar Undo for Page Delete Summary

**Edge-to-edge display enabled across all 8 fragments via WindowCompat.enableEdgeToEdge + ViewCompat.setOnApplyWindowInsetsListener; both page-delete flows (single and bulk) replaced with Snackbar + Undo using ScannerViewModel.insertPage/insertPages**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-01T18:33:31Z
- **Completed:** 2026-03-01T18:38:47Z
- **Tasks:** 4
- **Files modified:** 13

## Accomplishments
- MainActivity calls WindowCompat.enableEdgeToEdge(window) before setContentView; theme no longer overrides bar colors
- All 8 fragments handle WindowInsets — toolbar padded for status bar, bottom bars padded for nav bar, RecyclerViews scroll under nav bar with clipToPadding=false
- fragment_pages.xml upgraded to CoordinatorLayout root (enables Snackbar swipe-to-dismiss); all existing ConstraintLayout children preserved unchanged
- PagesFragment single-page delete: immediate removal + Snackbar with Undo via viewModel.insertPage()
- PagesFragment bulk-page delete: all selected pages deleted immediately + Snackbar with Undo via viewModel.insertPages() — no MaterialAlertDialogBuilder confirmation dialog

## Task Commits

Each task was committed atomically:

1. **Task 1: Enable edge-to-edge + remove theme bar color overrides** - `f9be609` (feat)
2. **Task 2: Add WindowInsets listeners to all 8 fragments** - `6c4f690` (feat)
3. **Task 3: Snackbar undo for single-page delete + CoordinatorLayout root** - `9568f9b` (feat)
4. **Task 4: Snackbar undo for bulk-page delete** - `04e1c48` (feat)

## Files Created/Modified
- `app/src/main/java/com/pdfscanner/app/MainActivity.kt` - Added WindowCompat.enableEdgeToEdge(window)
- `app/src/main/res/values/themes_cartoon.xml` - Removed android:statusBarColor and android:navigationBarColor
- `app/src/main/java/com/pdfscanner/app/ui/HomeFragment.kt` - WindowInsets: root (top) + recyclerRecentDocs (bottom)
- `app/src/main/java/com/pdfscanner/app/ui/CameraFragment.kt` - WindowInsets: controlsLayout (bottom only)
- `app/src/main/java/com/pdfscanner/app/ui/PreviewFragment.kt` - WindowInsets: toolbar (top) + buttonsLayout (bottom)
- `app/src/main/java/com/pdfscanner/app/ui/PagesFragment.kt` - WindowInsets (5 views) + Snackbar undo for both delete flows
- `app/src/main/java/com/pdfscanner/app/ui/HistoryFragment.kt` - WindowInsets: toolbar (top) + recyclerHistory (bottom)
- `app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt` - WindowInsets: toolbar (top) + navigationBar (bottom)
- `app/src/main/java/com/pdfscanner/app/ui/SettingsFragment.kt` - WindowInsets: toolbar (top) + root ScrollView (bottom)
- `app/src/main/java/com/pdfscanner/app/editor/PdfEditorFragment.kt` - WindowInsets: toolbar (top) + toolbarBottom (bottom)
- `app/src/main/res/layout/fragment_pages.xml` - Wrapped ConstraintLayout in CoordinatorLayout
- `app/src/main/java/com/pdfscanner/app/viewmodel/ScannerViewModel.kt` - Added insertPage() and insertPages()
- `app/src/main/res/values/strings.xml` - Added page_deleted string and pages_deleted plural

## Decisions Made
- enableEdgeToEdge() called after super.onCreate() but before binding inflation — required order for window flag application
- Remove android:statusBarColor and android:navigationBarColor from theme; keep windowLightStatusBar and windowLightNavigationBar (control icon tint, not bar color)
- HomeFragment has no dedicated toolbar ID — top inset applied to CoordinatorLayout root, bottom to recyclerRecentDocs
- CameraFragment: bottom inset only to controlsLayout — full-screen PreviewView must not be padded
- CoordinatorLayout wraps existing ConstraintLayout in fragment_pages.xml — all constraint references preserved inside inner ConstraintLayout
- Snackbar undo pattern: commit deletion immediately, restore on undo tap — avoids dialog interruption per Material3 UX guidance
- insertPages() sorts entries ascending before inserting so earlier insertions don't shift subsequent target indices

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Edge-to-edge display fully enabled — all fragments inset-aware
- Snackbar undo pattern established for all recoverable destructive actions
- PERF-03 (edge-to-edge) and PERF-05 (Snackbar undo) requirements fully satisfied
- Ready for Phase 3 Plan 02 (haptic feedback + PDF page caching)

---
*Phase: 03-performance-polish*
*Completed: 2026-03-01*
