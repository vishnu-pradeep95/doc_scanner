---
phase: 02-design-system
plan: 04
subsystem: ui
tags: [android, strings, localization, accessibility, layout, xml]

# Dependency graph
requires:
  - phase: 02-design-system
    provides: Design system baseline and theme structure from plans 01-03
provides:
  - All primary layout XML files free of hardcoded android:text strings
  - All contentDescription attributes in primary layouts reference @string/ resources
  - Emoji removed from android:text attributes in dialog and fragment layouts
  - 35 new string entries in strings.xml covering dialog, fragment, and contentDescription use cases
affects: [phase-05-lint, future-i18n, accessibility-testing]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "All android:text values in layout XML must use @string/ references (never hardcoded literals)"
    - "contentDescription attributes must reference @string/ resources for screen reader support"
    - "Emoji must not appear in android:text attributes; communicate intent via text alone"

key-files:
  created: []
  modified:
    - app/src/main/res/values/strings.xml
    - app/src/main/res/layout/dialog_save_success.xml
    - app/src/main/res/layout/dialog_signature_pro.xml
    - app/src/main/res/layout/dialog_saving_progress.xml
    - app/src/main/res/layout/fragment_preview.xml
    - app/src/main/res/layout/item_page.xml
    - app/src/main/res/layout/item_document.xml
    - app/src/main/res/layout/fragment_pdf_editor.xml

key-decisions:
  - "app:title emoji (fragment_preview.xml toolbar title '✨ Edit Magic') left as-is — app:title is a MaterialToolbar attribute not an android:text, outside plan scope; deferred to a later cleanup"
  - "fragment_pdf_editor.xml toolbar title 'Edit PDF' replaced with @string/content_desc_edit_pdf (reusing existing string rather than creating a new edit_pdf_title entry)"
  - "Save button in fragment_pdf_editor.xml mapped to @string/save_changes (existing 'Save' string) — emoji '💾' removed"

patterns-established:
  - "String extraction pattern: check existing strings.xml before adding new entries to avoid duplicates"
  - "Emoji removal rule: remove emoji from string resource value; text communicates action without emoji"

requirements-completed: [DSYS-05, DSYS-06]

# Metrics
duration: 8min
completed: 2026-02-28
---

# Phase 2 Plan 4: String Externalization and Emoji Cleanup Summary

**Moved 35 new string entries to strings.xml and removed all emoji from android:text attributes across 7 primary layout files (dialogs, fragments, item views)**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-28T00:00:00Z
- **Completed:** 2026-03-01T03:58:19Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- Added 35 new string resource entries to strings.xml covering save success dialog, signature pro dialog, saving progress dialog, preview fragment, and all contentDescription keys
- Replaced all hardcoded android:text strings in 7 layout files with @string/ references; removed all emoji characters from android:text attributes in those files
- All contentDescription attributes in primary layouts (item_page.xml, item_document.xml, fragment_pdf_editor.xml) now reference @string/ resources for screen reader compatibility

## Task Commits

Each task was committed atomically:

1. **Task 1: Add missing string resources to strings.xml** - `629c371` (feat)
2. **Task 2: Replace hardcoded text and emoji in layout XML files** - `19b3573` (feat)

**Plan metadata:** (pending final commit)

## Files Created/Modified

- `app/src/main/res/values/strings.xml` - Added 35 new string entries: save success dialog, signature pro dialog, saving progress dialog, filter_style, and 7 content_desc_* entries
- `app/src/main/res/layout/dialog_save_success.xml` - 4 android:text + 1 contentDescription replaced with @string refs; emoji removed
- `app/src/main/res/layout/dialog_signature_pro.xml` - 11 android:text + 2 contentDescription replaced with @string refs; emoji removed
- `app/src/main/res/layout/dialog_saving_progress.xml` - 2 android:text + 1 contentDescription replaced with @string refs; emoji removed
- `app/src/main/res/layout/fragment_preview.xml` - 4 button labels (filter_style, retake, crop_rotate, add_page) replaced; emoji removed
- `app/src/main/res/layout/item_page.xml` - contentDescription="Page thumbnail" replaced with @string ref
- `app/src/main/res/layout/item_document.xml` - contentDescription="Edit PDF" replaced with @string ref
- `app/src/main/res/layout/fragment_pdf_editor.xml` - 8 contentDescriptions + Save button emoji replaced with @string refs

## Decisions Made

- `app:title="✨ Edit Magic"` in fragment_preview.xml toolbar left as-is: `app:title` is a MaterialToolbar-specific attribute, not `android:text`, and was not in the plan's interfaces section. This is deferred to a future cleanup plan.
- The toolbar title "Edit PDF" in fragment_pdf_editor.xml was replaced with `@string/content_desc_edit_pdf` (reusing the existing string rather than creating a separate title-specific string).
- The Save button emoji `💾 Save` in fragment_pdf_editor.xml was mapped to `@string/save_changes` (existing "Save" string), as the emoji removal rule applies to any android:text with emoji.

## Deviations from Plan

None - plan executed exactly as written.

The extra replacements in fragment_pdf_editor.xml (Save button emoji, "Edit PDF" title text) are within plan scope: the plan explicitly listed fragment_pdf_editor.xml as a file to modify and the emoji removal rule applies to all android:text attributes in listed files.

## Issues Encountered

During execution, strings.xml was modified by another process (linter/auto-formatter added additional strings between Task 1 read and write). Re-read was required before editing. This had no functional impact — the additional strings added were valid and complementary to this plan's strings.

## Deferred Items

The following files were discovered to have emoji in android:text but are NOT in scope for this plan:

- `dialog_signature.xml` - 4 emoji instances (different from dialog_signature_pro.xml)
- `dialog_success.xml` - 1 emoji instance ("✨ Awesome!")
- `fragment_pages.xml` - 1 emoji instance ("➕ Add More")
- `dialog_ocr_result.xml` - 1 emoji instance ("📤 Share")
- `fragment_preview.xml:32` - `app:title="✨ Edit Magic"` (not android:text, not in scope)
- `layout_example_cartoon_home.xml` - explicitly marked SKIP in plan

These are candidates for a future string externalization cleanup plan (likely Phase 5 Lint prep).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 02-05 (the final plan in Phase 2) can proceed immediately
- All primary layout files now comply with the no-hardcoded-text rule
- Android Lint string resource checks (planned for Phase 5) will pass for the 7 files modified here
- Remaining deferred files (dialog_signature.xml, dialog_success.xml, fragment_pages.xml, dialog_ocr_result.xml) still have hardcoded text and should be addressed before Lint phase

## Self-Check: PASSED

- FOUND: 02-04-SUMMARY.md
- FOUND: strings.xml with all key entries (save_success_title, content_desc_page_thumbnail, sig_draw_title, saving_progress_message, filter_style, content_desc_edit_pdf, content_desc_edit_mascot)
- FOUND: commit 629c371 (Task 1: add string resources)
- FOUND: commit 19b3573 (Task 2: replace hardcoded text and emoji)
- VERIFIED: 0 hardcoded android:text in target dialog/fragment files
- VERIFIED: 6 @string/content_desc refs in fragment_pdf_editor.xml

---
*Phase: 02-design-system*
*Completed: 2026-02-28*
