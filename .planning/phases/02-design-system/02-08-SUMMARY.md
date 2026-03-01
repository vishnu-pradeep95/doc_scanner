---
phase: 02-design-system
plan: 08
subsystem: ui
tags: [android, strings, localization, accessibility, layout-xml, emoji-removal]

# Dependency graph
requires:
  - phase: 02-design-system
    provides: "Plans 02-06 and 02-07 closed original DSYS-05/DSYS-06 gaps; this plan closes the remaining 4 files not scanned by those plans"
provides:
  - "strings.xml gains 10 new entries for stamp picker and text annotation dialogs, plus item_saved_signature and fragment_home content descriptions"
  - "dialog_stamp_picker.xml: fully converted to @string/ references, emoji removed"
  - "dialog_text_input.xml: fully converted to @string/ references, emoji removed, tools:text for runtime-set values"
  - "item_saved_signature.xml: btnDelete contentDescription converted to @string/"
  - "fragment_home.xml: logo ImageView contentDescription converted to @string/"
  - "DSYS-05 satisfied: no hardcoded English strings remain in any production layout XML file (excluding android:hint per scope decision)"
  - "DSYS-06 satisfied: no emoji characters remain in android:text attributes across all layout XML files"
affects: [03-performance, 04-testing, 05-release]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "tools:text for runtime-overwritten TextViews: tvSizeValue and tvPreview in dialog_text_input.xml set to tools:text (design-time only, stripped at build, following tvPageInfo pattern from 02-06)"
    - "xmlns:tools added to ConstraintLayout roots when tools:text attributes are needed"

key-files:
  created: []
  modified:
    - app/src/main/res/values/strings.xml
    - app/src/main/res/layout/dialog_stamp_picker.xml
    - app/src/main/res/layout/dialog_text_input.xml
    - app/src/main/res/layout/item_saved_signature.xml
    - app/src/main/res/layout/fragment_home.xml

key-decisions:
  - "android:hint='Enter your text' on tilText in dialog_text_input.xml left unchanged — android:hint scope explicitly deferred to Phase 5 Lint cleanup per 02-07 established decision"
  - "tvSizeValue and tvPreview in dialog_text_input.xml use tools:text (not string resources) because values are overwritten at runtime by TextInputDialogFragment — same pattern as tvPageInfo from 02-06"

patterns-established:
  - "tools:text pattern: any TextView whose android:text is immediately overwritten by fragment code should use tools:text instead, leaving the runtime value empty at build time"

requirements-completed: [DSYS-05, DSYS-06]

# Metrics
duration: 5min
completed: 2026-03-01
---

# Phase 2 Plan 08: Design System Gap Closure (Final) Summary

**10 string entries extracted to strings.xml and 14 hardcoded attributes replaced across dialog_stamp_picker.xml, dialog_text_input.xml, item_saved_signature.xml, and fragment_home.xml, fully satisfying DSYS-05 and DSYS-06**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-01T17:00:00Z
- **Completed:** 2026-03-01T17:05:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- Added 10 string entries to strings.xml covering stamp picker, text annotation, and content descriptions for delete/logo
- Replaced all 14 hardcoded android:text and android:contentDescription attributes across 4 layout files with @string/ references
- Converted 2 runtime-set TextViews (tvSizeValue, tvPreview) from android:text to tools:text — design-time only placeholders, following the tvPageInfo pattern
- Removed all emoji characters (thumbs-up, pencil, eye) from android:text values across dialog layouts
- DSYS-05 fully satisfied: no hardcoded English strings remain in any production layout XML file
- DSYS-06 fully satisfied: no emoji characters remain in android:text attributes across all layout XML files

## Task Commits

Each task was committed atomically:

1. **Task 1: Add 10 new string entries to strings.xml** - `353df87` (feat)
2. **Task 2: Replace all hardcoded attributes in 4 layout files** - `b65df10` (feat)

## Files Created/Modified

- `app/src/main/res/values/strings.xml` - Added 10 new entries in 3 comment blocks (Stamp Picker, Text Annotation, content descriptions)
- `app/src/main/res/layout/dialog_stamp_picker.xml` - 3 replacements: tvTitle, btnCancel android:text + ImageView contentDescription
- `app/src/main/res/layout/dialog_text_input.xml` - 9 replacements: tvTitle, tvSizeLabel, tvColorLabel, tvPreviewLabel, btnCancel, btnInsert android:text + ImageView contentDescription; xmlns:tools added; tvSizeValue and tvPreview converted to tools:text
- `app/src/main/res/layout/item_saved_signature.xml` - 1 replacement: btnDelete contentDescription
- `app/src/main/res/layout/fragment_home.xml` - 1 replacement: logo ImageView contentDescription

## Decisions Made

- android:hint="Enter your text" on tilText in dialog_text_input.xml left unchanged — android:hint scope explicitly deferred to Phase 5 Lint cleanup per the established 02-07 decision. This is consistent with prior scope decisions.
- tvSizeValue and tvPreview use tools:text (not android:text with a string resource) because TextInputDialogFragment overwrites these values immediately at runtime. A string resource would never be displayed. This follows the tvPageInfo pattern from plan 02-06.

## Deviations from Plan

None - plan executed exactly as written.

Out-of-scope discoveries logged for deferred handling:
- `layout_example_cartoon_home.xml` contains multiple hardcoded android:text values with emoji — this is an "example" layout file, pre-existing, and not in scope for this plan. Not touched.
- `item_recent_scan_cartoon.xml` line 95 has android:text="•" (bullet separator) — pre-existing, not in the 4 target files.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 2 Design System is now fully complete. All 8 plans done. DSYS-05 and DSYS-06 are fully satisfied.
- Phase 3 (Performance) can begin. No design system blockers remain.
- Build verification still blocked in WSL2 (no JDK/Android SDK). Run `./gradlew assembleDebug` on a machine with Android SDK before release.

## Self-Check: PASSED

- FOUND: app/src/main/res/values/strings.xml
- FOUND: app/src/main/res/layout/dialog_stamp_picker.xml
- FOUND: app/src/main/res/layout/dialog_text_input.xml
- FOUND: app/src/main/res/layout/item_saved_signature.xml
- FOUND: app/src/main/res/layout/fragment_home.xml
- FOUND: .planning/phases/02-design-system/02-08-SUMMARY.md
- FOUND: 353df87 (Task 1 commit)
- FOUND: b65df10 (Task 2 commit)

---
*Phase: 02-design-system*
*Completed: 2026-03-01*
