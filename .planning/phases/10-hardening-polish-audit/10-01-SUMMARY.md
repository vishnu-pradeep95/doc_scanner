---
phase: 10-hardening-polish-audit
plan: 01
subsystem: security
tags: [clipboard, accessibility, root-detection, android-api]

# Dependency graph
requires:
  - phase: 09-biometric-app-lock
    provides: MainActivity with biometric lock infrastructure and SecurePreferences
provides:
  - Sensitive clipboard marking for OCR text (SEC-11)
  - Accessibility data protection on document name views and thumbnails (SEC-12)
  - Root/debuggable device detection utility with one-time warning dialog (SEC-14)
affects: [10-02-cross-cutting-audit]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "PersistableBundle extras on ClipData for sensitive content marking"
    - "accessibilityDataSensitive XML attribute with tools:ignore for backward compat"
    - "RootDetector heuristic pattern: test-keys, su binary, FLAG_DEBUGGABLE"
    - "One-time dismissible dialog via SecurePreferences boolean flag"

key-files:
  created:
    - app/src/main/java/com/pdfscanner/app/util/RootDetector.kt
  modified:
    - app/src/main/java/com/pdfscanner/app/ui/PagesFragment.kt
    - app/src/main/java/com/pdfscanner/app/MainActivity.kt
    - app/src/main/res/layout/item_document.xml
    - app/src/main/res/layout/item_recent_document.xml
    - app/src/main/res/layout/dialog_ocr_result.xml
    - app/src/main/res/layout/item_page.xml
    - app/src/main/res/values/strings.xml

key-decisions:
  - "No API version guard on EXTRA_IS_SENSITIVE -- compile-time constant, no-op on older APIs"
  - "tools:ignore=UnusedAttribute on accessibilityDataSensitive for lint compat with minSdk 24"
  - "RootDetector is warn-only -- never blocks functionality (per SEC-14 requirement)"
  - "Root warning guarded by !BuildConfig.DEBUG to avoid false positives from FLAG_DEBUGGABLE in debug builds"

patterns-established:
  - "Sensitive clipboard: PersistableBundle with EXTRA_IS_SENSITIVE on ClipData.description.extras"
  - "Accessibility protection: android:accessibilityDataSensitive=yes on views showing user content"
  - "Root detection: lightweight heuristic checks in singleton object, called from Activity onCreate"

requirements-completed: [SEC-11, SEC-12, SEC-14]

# Metrics
duration: 2min
completed: 2026-03-05
---

# Phase 10 Plan 01: Security Hardening Summary

**Sensitive clipboard marking, accessibility data protection on 4 layout views, and root/debuggable device detection with one-time warning dialog**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-05T04:09:20Z
- **Completed:** 2026-03-05T04:11:47Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- PagesFragment.copyToClipboard() marks OCR text ClipData with EXTRA_IS_SENSITIVE, hiding it from clipboard preview on API 33+
- 4 layout files (item_document, item_recent_document, dialog_ocr_result, item_page) now protect sensitive views with accessibilityDataSensitive="yes"
- RootDetector.kt performs 3 heuristic checks (test-keys, su binary, FLAG_DEBUGGABLE)
- MainActivity shows one-time non-blocking root warning dialog in release builds only, persisted via SecurePreferences

## Task Commits

Each task was committed atomically:

1. **Task 1: Sensitive clipboard + accessibility data protection (SEC-11, SEC-12)** - `1ab70af` (feat)
2. **Task 2: Root/debuggable detection with one-time warning dialog (SEC-14)** - `48af1d3` (feat)

## Files Created/Modified
- `app/src/main/java/com/pdfscanner/app/util/RootDetector.kt` - Root/debuggable detection utility with 3 heuristic checks
- `app/src/main/java/com/pdfscanner/app/ui/PagesFragment.kt` - Sensitive clipboard marking in copyToClipboard()
- `app/src/main/java/com/pdfscanner/app/MainActivity.kt` - Root warning dialog wired into onCreate with !BuildConfig.DEBUG guard
- `app/src/main/res/layout/item_document.xml` - accessibilityDataSensitive on textDocumentName
- `app/src/main/res/layout/item_recent_document.xml` - accessibilityDataSensitive on textDocName
- `app/src/main/res/layout/dialog_ocr_result.xml` - accessibilityDataSensitive on textOcrResult + tools namespace
- `app/src/main/res/layout/item_page.xml` - accessibilityDataSensitive on imageThumbnail
- `app/src/main/res/values/strings.xml` - Security warning dialog strings

## Decisions Made
- No API version guard on EXTRA_IS_SENSITIVE -- it is a compile-time constant that is safely ignored on older APIs
- Added tools:ignore="UnusedAttribute" on accessibilityDataSensitive for lint compatibility with minSdk 24
- RootDetector is warn-only, never blocks functionality (matches SEC-14 requirement)
- Root warning dialog guarded by !BuildConfig.DEBUG to avoid false positives from FLAG_DEBUGGABLE in debug builds

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added missing tools namespace to dialog_ocr_result.xml**
- **Found during:** Task 1 (Accessibility data protection)
- **Issue:** dialog_ocr_result.xml did not declare xmlns:tools, but tools:ignore="UnusedAttribute" was needed
- **Fix:** Added xmlns:tools="http://schemas.android.com/tools" to root LinearLayout
- **Files modified:** app/src/main/res/layout/dialog_ocr_result.xml
- **Verification:** Build compiles successfully
- **Committed in:** 1ab70af (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Minor fix to add missing XML namespace. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All three security requirements (SEC-11, SEC-12, SEC-14) implemented
- Ready for 10-02 cross-cutting audit plan
- Project compiles cleanly with all changes

---
*Phase: 10-hardening-polish-audit*
*Completed: 2026-03-05*
