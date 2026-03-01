---
phase: 02-design-system
plan: 03
subsystem: ui
tags: [snackbar, material-design, toast, feedback, android, kotlin, extensions]

# Dependency graph
requires:
  - phase: 01-stability
    provides: stable fragment lifecycle management that makes snackbar safe to show

provides:
  - Fragment.showSnackbar extension function (String and @StringRes overloads)
  - Zero Toast.makeText() calls in the codebase
  - 23 new string resources for formerly hardcoded Toast messages

affects: [03-performance, 04-testing, 05-release]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Fragment extension functions in Extensions.kt for shared UI utilities"
    - "showSnackbar guards against detached fragment (view == null)"
    - "Snackbar.LENGTH_LONG for error messages, LENGTH_SHORT (default) for success/info"
    - "Dynamic messages use getString(R.string.format_string, arg) before showSnackbar call"

key-files:
  created:
    - app/src/main/java/com/pdfscanner/app/ui/Extensions.kt
  modified:
    - app/src/main/java/com/pdfscanner/app/ui/HomeFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/HistoryFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PagesFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/CameraFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PreviewFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt
    - app/src/main/java/com/pdfscanner/app/editor/PdfEditorFragment.kt
    - app/src/main/res/values/strings.xml

key-decisions:
  - "showSnackbar extension on Fragment uses view?.let{} guard — safe when fragment is detached (view == null)"
  - "PdfEditorFragment (editor package) uses explicit import: import com.pdfscanner.app.ui.showSnackbar"
  - "PdfViewerFragment uses explicit import too — same package isolation pattern"
  - "Anonymous object callbacks (OnImageSavedCallback) need this@CameraFragment.showSnackbar() qualification"
  - "Dynamic messages (e.g., 'Imported N pages') use getString(R.string.format_str, arg) before showSnackbar — keeps extension API clean"
  - "Hardcoded strings in HistoryFragment and CameraFragment extracted to strings.xml — 20 new string resources added"

patterns-established:
  - "Extensions.kt in ui/ package: home for all Fragment extension utilities"
  - "showSnackbar(@StringRes Int) for static messages — avoids getString call at call site"
  - "showSnackbar(String) for dynamic/formatted messages — caller uses getString()"

requirements-completed: [DSYS-04]

# Metrics
duration: 6min
completed: 2026-03-01
---

# Phase 2 Plan 3: Toast-to-Snackbar Migration Summary

**Fragment.showSnackbar extension replaces all 68 Toast.makeText() calls across 7 fragments with Material Snackbar, with 20 new string resources extracted from hardcoded Toast messages**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-01T03:55:28Z
- **Completed:** 2026-03-01T04:02:15Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- Created Extensions.kt with two showSnackbar overloads: `String` and `@StringRes Int`, both guarded against detached fragments
- Replaced all 68 Toast.makeText() calls across 7 fragment files with showSnackbar()
- Extracted 20 hardcoded string literals from Toast calls into strings.xml resources
- Zero Toast.makeText() calls remain anywhere in the app

## Task Commits

Each task was committed atomically:

1. **Task 1: Create Extensions.kt and extract PdfEditorFragment hardcoded strings** - `912836a` (feat)
2. **Task 2: Replace all Toast.makeText() calls with showSnackbar() across all 7 files** - `286a7aa` (feat)

**Plan metadata:** (docs commit follows)

## Files Created/Modified
- `app/src/main/java/com/pdfscanner/app/ui/Extensions.kt` - New file: Fragment.showSnackbar extension with String and @StringRes overloads
- `app/src/main/res/values/strings.xml` - Added 3 PdfEditor error strings + 20 Snackbar feedback strings
- `app/src/main/java/com/pdfscanner/app/ui/HomeFragment.kt` - 16 Toast -> showSnackbar (removed Toast import)
- `app/src/main/java/com/pdfscanner/app/ui/HistoryFragment.kt` - 23 Toast -> showSnackbar (removed Toast import)
- `app/src/main/java/com/pdfscanner/app/ui/PagesFragment.kt` - 14 Toast -> showSnackbar (added Snackbar import)
- `app/src/main/java/com/pdfscanner/app/ui/CameraFragment.kt` - 7 Toast -> showSnackbar (this@CameraFragment qualifier for anonymous object)
- `app/src/main/java/com/pdfscanner/app/ui/PreviewFragment.kt` - 3 Toast -> showSnackbar
- `app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt` - 1 Toast -> showSnackbar (explicit import from ui package)
- `app/src/main/java/com/pdfscanner/app/editor/PdfEditorFragment.kt` - 4 Toast -> showSnackbar (explicit import from ui package)

## Decisions Made
- `showSnackbar` extension guards against detached fragment via `view?.let{}` — prevents crash when fragment is not attached
- PdfEditorFragment and PdfViewerFragment are in different packages so they use explicit `import com.pdfscanner.app.ui.showSnackbar`
- In CameraFragment's `OnImageSavedCallback` anonymous object, must qualify with `this@CameraFragment.showSnackbar()` since anonymous objects don't inherit the enclosing class receiver
- Hardcoded strings extracted to strings.xml with format string variants (`%s` args) for dynamic error messages
- Used `Snackbar.LENGTH_LONG` explicitly for all error messages; omitted for success/info (defaults to LENGTH_SHORT)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added string resources for hardcoded Toast literals in HistoryFragment, CameraFragment, PreviewFragment, PagesFragment**
- **Found during:** Task 2 (Toast replacement across all 7 files)
- **Issue:** Plan specified hardcoded strings in PdfEditorFragment (3 strings), but HistoryFragment had 5 additional hardcoded strings, CameraFragment had 4, PreviewFragment had 3, PagesFragment had 2, HomeFragment had 3
- **Fix:** Created 20 new string resource entries (format strings for dynamic messages, plain strings for static ones)
- **Files modified:** app/src/main/res/values/strings.xml
- **Committed in:** 286a7aa (Task 2 commit)

**2. [Rule 1 - Bug] Fixed context null guard in coroutine continuations after showSnackbar**
- **Found during:** Task 2 (replacing multiline Toast blocks in coroutine contexts)
- **Issue:** Several Toast call sites had `val currentCtx = context ?: return@launch` before the Toast call, but the context was only used for the Toast, not for anything else — so the entire guard was redundant once showSnackbar handles null view internally
- **Fix:** Removed redundant `currentCtx` captures where only the Toast used them; kept them where the context was used for other operations (e.g., repository initialization)
- **Files modified:** HomeFragment.kt, PagesFragment.kt, HistoryFragment.kt
- **Committed in:** 286a7aa (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 missing critical strings, 1 bug/redundant guard)
**Impact on plan:** Both fixes are correctness improvements aligned with the plan's intent. No scope creep.

## Issues Encountered
- Anonymous object syntax in CameraFragment's `OnImageSavedCallback` required `this@CameraFragment.showSnackbar()` qualification — resolved automatically
- PdfEditorFragment already had Snackbar import (used directly in some places) — kept Snackbar import for LENGTH_LONG usage, added showSnackbar import alongside it

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All fragment feedback is now Snackbar-based; Material Design spec compliance achieved
- Extensions.kt is established as the home for fragment utility functions
- Ready for Phase 2 Plan 4: Typography system or next design system plan

---
*Phase: 02-design-system*
*Completed: 2026-03-01*

## Self-Check: PASSED

- Extensions.kt: FOUND at app/src/main/java/com/pdfscanner/app/ui/Extensions.kt
- 02-03-SUMMARY.md: FOUND at .planning/phases/02-design-system/02-03-SUMMARY.md
- Task 1 commit 912836a: VERIFIED in git log
- Task 2 commit 286a7aa: VERIFIED in git log
- Zero Toast.makeText() calls: VERIFIED (grep returns 0)
