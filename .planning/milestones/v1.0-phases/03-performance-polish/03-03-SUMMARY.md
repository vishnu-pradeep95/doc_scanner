---
phase: 03-performance-polish
plan: "03"
subsystem: ui
tags: [material-motion, transitions, MaterialSharedAxis, MaterialFadeThrough, LinearProgressIndicator, android, kotlin]

# Dependency graph
requires:
  - phase: 03-01
    provides: edge-to-edge setup across all 8 fragments (required transitionGroup on CoordinatorLayout root in fragment_pages.xml)
provides:
  - Material motion transitions on all 8 fragment entry/exit paths (Z-axis for hierarchical, FadeThrough for lateral)
  - Determinate PDF progress indicator (LinearProgressIndicator with Page X of Y text)
  - motion_duration_large = 300ms integer resource
  - android:transitionGroup="true" on all 8 fragment root layouts
affects: [04-testing, any future fragment additions must follow transition placement rules]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Material motion transitions set in onCreate() (NEVER onViewCreated)"
    - "enterTransition/returnTransition in entering fragment's onCreate()"
    - "exitTransition/reenterTransition set at departing fragment's navigate() callsites"
    - "MaterialSharedAxis.Z for hierarchical navigation (Home→Camera, Camera→Preview/Pages, History→Viewer/Editor)"
    - "MaterialFadeThrough for lateral navigation (Home↔History, Home↔Settings, Pages→History)"
    - "View.post{} for UI updates from IO thread inside withContext(IO) — avoids nested withContext(Main)"
    - "onProgress callback pattern for determinate progress reporting from IO thread"

key-files:
  created:
    - app/src/main/res/values/integers.xml
  modified:
    - app/src/main/res/layout/fragment_home.xml
    - app/src/main/res/layout/fragment_camera.xml
    - app/src/main/res/layout/fragment_preview.xml
    - app/src/main/res/layout/fragment_pages.xml
    - app/src/main/res/layout/fragment_history.xml
    - app/src/main/res/layout/fragment_pdf_viewer.xml
    - app/src/main/res/layout/fragment_settings.xml
    - app/src/main/res/layout/fragment_pdf_editor.xml
    - app/src/main/java/com/pdfscanner/app/ui/HomeFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/CameraFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PreviewFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PagesFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/HistoryFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/SettingsFragment.kt
    - app/src/main/java/com/pdfscanner/app/editor/PdfEditorFragment.kt
    - app/src/main/res/values/strings.xml

key-decisions:
  - "Use fully-qualified class names (com.google.android.material.transition.*) in fragment files rather than imports to avoid import collisions across 8 files"
  - "View.post{} not withContext(Dispatchers.Main) for progress updates inside withContext(IO) — avoids nested dispatcher overhead"
  - "onProgress callback on generatePdf() rather than StateFlow/LiveData — simpler, no extra ViewModel plumbing needed"
  - "Pages→Camera exitTransition uses Z-axis backward (forward=false) since camera is parent in the hierarchy"

patterns-established:
  - "Transition placement: enterTransition/returnTransition in entering fragment onCreate(); exitTransition/reenterTransition at navigate() callsite in departing fragment"
  - "Lateral navigation (top-level peer screens): MaterialFadeThrough"
  - "Hierarchical navigation (parent→child drill-down): MaterialSharedAxis(Z, forward=true/false)"
  - "IO→UI thread progress reporting: View.post{} inside withContext(IO) block"

requirements-completed: [PERF-01, PERF-04]

# Metrics
duration: 4min
completed: 2026-03-01
---

# Phase 3 Plan 03: Material Motion Transitions + Determinate PDF Progress Summary

**Material SharedAxis/FadeThrough transitions on all 8 fragment navigation paths, plus LinearProgressIndicator with Page X of Y progress for PDF generation**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-01T20:22:18Z
- **Completed:** 2026-03-01T20:26:50Z
- **Tasks:** 3
- **Files modified:** 18

## Accomplishments

- All 8 fragment layouts gain `android:transitionGroup="true"` root attribute for atomic transition animation
- All 8 fragments implement `enterTransition` and `returnTransition` in `onCreate()` using appropriate Material motion classes
- Departing fragments set `exitTransition`/`reenterTransition` at every `navigate()` callsite for correct bidirectional animation
- Indeterminate ProgressBar in PagesFragment loading overlay replaced with `LinearProgressIndicator` (id=`progressIndicator`, determinate)
- `generatePdf()` gains optional `onProgress` callback parameter, called once per page before processing
- Both `createPdf()` and `createPdfFromSelection()` wire progress indicator max, initial progress, and per-page updates via `View.post{}`
- `pdf_progress` format string ("Page %1$d of %2$d…") added to strings.xml
- `motion_duration_large = 300` integer resource created in `res/values/integers.xml`

## Task Commits

Each task was committed atomically:

1. **Task 1: Create integers.xml and add transitionGroup to all fragment root views** - `a2d41c4` (chore)
2. **Task 2: Set Material motion transitions in all 8 fragment onCreate() methods** - `37da362` (feat)
3. **Task 3: Add determinate PDF progress indicator to PagesFragment** - `2e748b2` (feat)

## Files Created/Modified

- `app/src/main/res/values/integers.xml` - Created; motion_duration_large = 300
- `app/src/main/res/layout/fragment_home.xml` - Added android:transitionGroup="true" to CoordinatorLayout root
- `app/src/main/res/layout/fragment_camera.xml` - Added android:transitionGroup="true" to ConstraintLayout root
- `app/src/main/res/layout/fragment_preview.xml` - Added android:transitionGroup="true" to ConstraintLayout root
- `app/src/main/res/layout/fragment_pages.xml` - Added transitionGroup; replaced ProgressBar with LinearProgressIndicator
- `app/src/main/res/layout/fragment_history.xml` - Added android:transitionGroup="true" to ConstraintLayout root
- `app/src/main/res/layout/fragment_pdf_viewer.xml` - Added android:transitionGroup="true" to ConstraintLayout root
- `app/src/main/res/layout/fragment_settings.xml` - Added android:transitionGroup="true" to ScrollView root
- `app/src/main/res/layout/fragment_pdf_editor.xml` - Added android:transitionGroup="true" to ConstraintLayout root
- `app/src/main/java/com/pdfscanner/app/ui/HomeFragment.kt` - Added onCreate() with FadeThrough; exit/reenter transitions at callsites
- `app/src/main/java/com/pdfscanner/app/ui/CameraFragment.kt` - Added onCreate() with SharedAxis.Z; exit/reenter at 3 navigate callsites
- `app/src/main/java/com/pdfscanner/app/ui/PreviewFragment.kt` - Added onCreate() with SharedAxis.Z
- `app/src/main/java/com/pdfscanner/app/ui/PagesFragment.kt` - Added onCreate() with SharedAxis.Z; exit/reenter at callsites; progress wiring
- `app/src/main/java/com/pdfscanner/app/ui/HistoryFragment.kt` - Added onCreate() with FadeThrough; SharedAxis exit/reenter at 2 callsites
- `app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt` - Added onCreate() with SharedAxis.Z
- `app/src/main/java/com/pdfscanner/app/ui/SettingsFragment.kt` - Added onCreate() with FadeThrough
- `app/src/main/java/com/pdfscanner/app/editor/PdfEditorFragment.kt` - Added onCreate() with SharedAxis.Z
- `app/src/main/res/values/strings.xml` - Added pdf_progress format string

## Decisions Made

- Used fully-qualified class names (`com.google.android.material.transition.MaterialSharedAxis`) in fragment files to avoid import collisions across 8 files simultaneously
- `View.post{}` used instead of `withContext(Dispatchers.Main)` for progress updates inside `withContext(IO)` — avoids nested dispatcher overhead and is the correct pattern for View callbacks from IO threads
- `onProgress` callback on `generatePdf()` rather than StateFlow/LiveData — simpler, keeps progress reporting local to the generation function without extra ViewModel plumbing
- Pages→Camera `exitTransition` uses `MaterialSharedAxis(Z, forward=false)` to reverse the direction since Camera is the parent in the hierarchy

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Material motion transitions complete across all 8 fragments — PERF-01 and PERF-04 satisfied
- Phase 3 (03-performance-polish) is now complete: all 3 plans executed (03-01, 03-02, 03-03)
- Ready for Phase 4 (testing): all behavior is now stable with no rough edges
- Build verification still blocked in WSL2 (no Android SDK/JDK); run `./gradlew assembleDebug` on machine with Android SDK before release

---
*Phase: 03-performance-polish*
*Completed: 2026-03-01*
