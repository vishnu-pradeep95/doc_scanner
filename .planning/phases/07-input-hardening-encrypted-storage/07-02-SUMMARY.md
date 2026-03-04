---
phase: 07-input-hardening-encrypted-storage
plan: 02
subsystem: security
tags: [encrypted-sharedpreferences, tink, android-keystore, security-crypto, aead, migration]

# Dependency graph
requires:
  - phase: 06-security-foundation
    provides: "ProGuard/R8 configuration, security manifest hardening"
provides:
  - "SecurePreferences singleton with EncryptedSharedPreferences, KeyStore fallback, and migration"
  - "DocumentHistoryRepository wired to encrypted storage with history_ prefix"
  - "AppPreferences wired to encrypted storage with app_ prefix"
  - "Backup exclusion rules for encrypted prefs files"
affects: [08-file-encryption, 10-hardening-polish]

# Tech tracking
tech-stack:
  added: [androidx.security:security-crypto:1.1.0, Tink 1.7.0 (transitive)]
  patterns: [singleton-with-keystore-fallback, prefix-namespaced-merged-prefs, sentinel-key-idempotent-migration]

key-files:
  created:
    - app/src/main/java/com/pdfscanner/app/util/SecurePreferences.kt
  modified:
    - app/build.gradle.kts
    - app/proguard-rules.pro
    - app/src/main/java/com/pdfscanner/app/data/DocumentHistory.kt
    - app/src/main/java/com/pdfscanner/app/util/AppPreferences.kt
    - app/src/main/res/xml/backup_rules.xml
    - app/src/main/res/xml/data_extraction_rules.xml
    - app/src/main/AndroidManifest.xml
    - app/src/test/java/com/pdfscanner/app/data/DocumentHistoryRepositoryTest.kt
    - app/src/test/java/com/pdfscanner/app/util/AppPreferencesTest.kt

key-decisions:
  - "R8 dontwarn for Tink error-prone annotations committed alongside dependency (KEY DECISION from STATE.md)"
  - "SecurePreferences falls back to unencrypted prefs after 3 KeyStore retries (per research recommendation for API 24-27 OEM bugs)"
  - "Both old prefs files merged into single encrypted file with prefix namespacing (history_, app_)"
  - "Sentinel key in same apply() transaction ensures crash-safe idempotent migration"

patterns-established:
  - "SecurePreferences.getInstance(context) — single entry point for all encrypted prefs access"
  - "Prefix namespacing — history_ and app_ prefixes prevent key collisions in merged prefs file"
  - "SecurePreferences.resetForTesting() — singleton reset for test isolation under Robolectric"

requirements-completed: [SEC-08]

# Metrics
duration: 5min
completed: 2026-03-04
---

# Phase 7 Plan 2: Encrypted SharedPreferences Summary

**EncryptedSharedPreferences with Tink AEAD, KeyStore 3-retry fallback, idempotent migration from unencrypted prefs, and backup exclusion**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-04T20:09:19Z
- **Completed:** 2026-03-04T20:15:08Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments
- SecurePreferences singleton with MasterKey/EncryptedSharedPreferences creation, 3-retry KeyStore with exponential backoff, and silent unencrypted fallback
- Idempotent migration from both old prefs files (document_history, pdf_scanner_prefs) with sentinel key in same apply() transaction
- DocumentHistoryRepository and AppPreferences wired to use SecurePreferences with prefix-namespaced keys
- Encrypted prefs files excluded from Android backup (both legacy and modern backup rules)
- Existing unit tests updated and passing under new encrypted storage path

## Task Commits

Each task was committed atomically:

1. **Task 1: Add security-crypto dependency, R8 rules, and create SecurePreferences singleton** - `b751531` (feat)
2. **Task 2: Wire consumers to SecurePreferences and update backup exclusion rules** - `34eb924` (feat)

## Files Created/Modified
- `app/src/main/java/com/pdfscanner/app/util/SecurePreferences.kt` - EncryptedSharedPreferences singleton with migration and KeyStore fallback
- `app/build.gradle.kts` - Added security-crypto:1.1.0 dependency
- `app/proguard-rules.pro` - Added R8 dontwarn rule for Tink error-prone annotations
- `app/src/main/java/com/pdfscanner/app/data/DocumentHistory.kt` - Wired to SecurePreferences with history_ prefix
- `app/src/main/java/com/pdfscanner/app/util/AppPreferences.kt` - Wired to SecurePreferences with app_ prefix
- `app/src/main/res/xml/backup_rules.xml` - Added secure_prefs exclusions
- `app/src/main/res/xml/data_extraction_rules.xml` - Added secure_prefs exclusions in both sections
- `app/src/main/AndroidManifest.xml` - Fixed pre-existing manifest merger conflict (tools:replace)
- `app/src/test/java/com/pdfscanner/app/data/DocumentHistoryRepositoryTest.kt` - Updated setup to reset SecurePreferences singleton
- `app/src/test/java/com/pdfscanner/app/util/AppPreferencesTest.kt` - Updated setup to reset SecurePreferences singleton

## Decisions Made
- R8 dontwarn for Tink error-prone annotations committed in same commit as dependency (honors KEY DECISION from STATE.md)
- SecurePreferences falls back to unencrypted prefs after 3 KeyStore retries with exponential backoff (for API 24-27 OEM KeyStore bugs)
- Both old prefs files merged into single encrypted file with prefix namespacing (history_, app_) to avoid key collisions
- Sentinel key written in same apply() transaction as all migrated data for crash-safe idempotency
- Old unencrypted prefs files retained through v1.2 cycle (cleanup scheduled for Phase 10 audit)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed pre-existing manifest merger conflict for CropImageActivity**
- **Found during:** Task 1 (build verification)
- **Issue:** CropImageActivity android:exported="false" conflicted with library's android:exported="true", preventing compilation
- **Fix:** Added `tools:replace="android:exported"` attribute to the CropImageActivity declaration
- **Files modified:** app/src/main/AndroidManifest.xml
- **Verification:** Build compiles successfully after fix
- **Committed in:** b751531 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Pre-existing build blocker that prevented any compilation. Fix was minimal and necessary.

## Issues Encountered
- Pre-existing untracked InputValidator.kt and InputValidatorTest.kt files (from 07-01 plan, not yet executed) caused compilation errors. These were temporarily moved during test verification and restored afterward. Logged as out-of-scope — they belong to 07-01 execution.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Encrypted SharedPreferences foundation complete for Phase 8 (File Encryption)
- SecurePreferences singleton pattern established for any future encrypted storage needs
- Old unencrypted prefs files retained for data safety — Phase 10 audit will handle cleanup

---
*Phase: 07-input-hardening-encrypted-storage*
*Completed: 2026-03-04*
