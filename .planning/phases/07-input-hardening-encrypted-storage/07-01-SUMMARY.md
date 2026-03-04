---
phase: 07-input-hardening-encrypted-storage
plan: 01
subsystem: security
tags: [input-validation, path-traversal, mime-validation, nav-args, content-uri]

# Dependency graph
requires:
  - phase: 06-security-foundation
    provides: "Security baseline (FLAG_SECURE, ProGuard, network config)"
provides:
  - "InputValidator utility with path traversal and MIME type validation"
  - "All fragment entry points validated before processing nav args"
  - "Import flows validated before processing content URIs"
affects: [07-02, 08-file-encryption, 10-hardening-polish]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Centralized input validation via object singleton", "Early-return guard pattern in onViewCreated"]

key-files:
  created:
    - app/src/main/java/com/pdfscanner/app/util/InputValidator.kt
    - app/src/test/java/com/pdfscanner/app/util/InputValidatorTest.kt
  modified:
    - app/src/main/java/com/pdfscanner/app/ui/PreviewFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt
    - app/src/main/java/com/pdfscanner/app/editor/PdfEditorFragment.kt
    - app/src/main/java/com/pdfscanner/app/ui/HomeFragment.kt

key-decisions:
  - "Security-neutral error messages ('Document not available', 'Unsupported file type') to prevent info leakage"
  - "content:// URIs bypass path validation -- access mediated by ContentResolver"
  - "application/octet-stream explicitly rejected per banking-app security stance"
  - "Inline strings for security messages -- intentionally NOT in strings.xml"

patterns-established:
  - "InputValidator.isPathWithinAppStorage: canonical path resolution against filesDir for raw paths"
  - "InputValidator.isUriPathWithinAppStorage: scheme-aware validation (file:// checked, content:// passed)"
  - "InputValidator.isAllowedMimeType: allowlist approach (image/*, application/pdf only)"
  - "Early-return guard in onViewCreated: validate -> showSnackbar -> navigateUp -> return"

requirements-completed: [SEC-07]

# Metrics
duration: 10min
completed: 2026-03-04
---

# Phase 7 Plan 1: Input Validation Summary

**Centralized InputValidator utility preventing path traversal and MIME type attacks, wired into all 4 fragment entry points with security-neutral error messages**

## Performance

- **Duration:** 10 min
- **Started:** 2026-03-04T20:09:21Z
- **Completed:** 2026-03-04T20:19:41Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- InputValidator object with 3 public methods: isPathWithinAppStorage, isUriPathWithinAppStorage, isAllowedMimeType
- 17 unit tests covering path traversal attacks, valid paths, MIME allowlist, octet-stream rejection, null MIME handling
- PreviewFragment, PdfViewerFragment, PdfEditorFragment validate nav arg paths before any processing
- HomeFragment validates MIME types in both handleImportResult and handleGalleryResult before processing URIs

## Task Commits

Each task was committed atomically:

1. **Task 1: Create InputValidator utility with unit tests (TDD)**
   - `8b2e57d` (test: add failing tests for InputValidator)
   - `2340074` (feat: implement InputValidator with path and MIME validation)
2. **Task 2: Wire path validation into fragment entry points and MIME validation into imports** - `3c4eefd` (feat)

## Files Created/Modified
- `app/src/main/java/com/pdfscanner/app/util/InputValidator.kt` - Centralized path + MIME validation utility (3 public methods)
- `app/src/test/java/com/pdfscanner/app/util/InputValidatorTest.kt` - 17 unit tests for all validation scenarios
- `app/src/main/java/com/pdfscanner/app/ui/PreviewFragment.kt` - Added isUriPathWithinAppStorage guard in onViewCreated
- `app/src/main/java/com/pdfscanner/app/ui/PdfViewerFragment.kt` - Added isPathWithinAppStorage guard in onViewCreated
- `app/src/main/java/com/pdfscanner/app/editor/PdfEditorFragment.kt` - Added isUriPathWithinAppStorage guard in onViewCreated
- `app/src/main/java/com/pdfscanner/app/ui/HomeFragment.kt` - Added isAllowedMimeType filter in handleImportResult and handleGalleryResult

## Decisions Made
- Security-neutral error messages ("Document not available", "Unsupported file type") -- no info leakage about why validation failed
- content:// URIs always pass path validation since ContentResolver mediates access
- application/octet-stream explicitly rejected per banking-app security stance decision
- Error message strings kept inline (not in strings.xml) -- security messages should not be translatable/discoverable
- Used Robolectric ContentProvider registration for MIME type testing

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed AndroidManifest CropImageActivity merger conflict**
- **Found during:** Task 1 (build verification)
- **Issue:** Manifest merger failed on CropImageActivity android:exported attribute conflict with CanHub library
- **Fix:** Added tools:replace="android:exported" to CropImageActivity element
- **Files modified:** app/src/main/AndroidManifest.xml
- **Verification:** Build completes successfully
- **Committed in:** 3c4eefd (Task 2 commit)

**2. [Rule 1 - Bug] Fixed nested comment syntax in InputValidator KDoc**
- **Found during:** Task 1 (implementation)
- **Issue:** KDoc comment containing `image/*` created nested block comment (`/*` inside `/** */`), causing compilation error
- **Fix:** Replaced `image/*` with `image/[star]` in KDoc and removed `/*` from inline comments
- **Files modified:** app/src/main/java/com/pdfscanner/app/util/InputValidator.kt
- **Verification:** Compilation succeeds

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both auto-fixes necessary for compilation. No scope creep.

## Issues Encountered
- Stale Gradle build cache caused phantom compilation errors after clean the project compiled correctly
- Robolectric ShadowContentResolver does not have registerContentProviderByAuthority -- used Robolectric.buildContentProvider with ProviderInfo instead

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- InputValidator is ready for use by any future fragment or utility needing path/MIME validation
- Ready for 07-02 (EncryptedSharedPreferences migration)
- No blockers or concerns

---
*Phase: 07-input-hardening-encrypted-storage*
*Completed: 2026-03-04*

## Self-Check: PASSED

All files exist, all commits verified.
