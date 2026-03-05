---
phase: 08-file-encryption-at-rest
plan: 01
subsystem: security
tags: [tink, streaming-aead, android-keystore, file-encryption, secure-delete, aes256-gcm]

# Dependency graph
requires:
  - phase: 07-input-hardening-encrypted-storage
    provides: SecurePreferences singleton pattern, KeyStore fallback pattern
provides:
  - SecureFileManager singleton with StreamingAead encrypt/decrypt/migrate/secureDelete API
  - tink-android:1.20.0 dependency (upgrades transitive 1.8.0 from security-crypto)
  - ProGuard keep rules for Tink reflection-loaded classes
  - Backup exclusion for file encryption keyset SharedPreferences
affects: [08-02, 08-03, 08-04, 09-biometric-app-lock, 10-hardening-polish]

# Tech tracking
tech-stack:
  added: [com.google.crypto.tink:tink-android:1.20.0]
  patterns: [StreamingAead with AndroidKeysetManager, encrypt-in-place with atomic rename, secure delete with SecureRandom overwrite]

key-files:
  created:
    - app/src/main/java/com/pdfscanner/app/util/SecureFileManager.kt
  modified:
    - app/build.gradle.kts
    - app/proguard-rules.pro
    - app/src/main/res/xml/data_extraction_rules.xml
    - app/src/main/res/xml/backup_rules.xml

key-decisions:
  - "Used KeyTemplates.get() API for AndroidKeysetManager (PredefinedStreamingAeadParameters returns Parameters type, not KeyTemplate; AndroidKeysetManager.Builder requires KeyTemplate)"
  - "Suppressed getPrimitive(Class) deprecation warning (Configuration-based alternative is package-private in Tink 1.20.0)"

patterns-established:
  - "SecureFileManager singleton: same object/double-checked-locking pattern as SecurePreferences"
  - "Encrypt-in-place with idempotency: decrypt-first check before encryption prevents double-encryption on crash recovery"
  - "Graceful degradation: all encrypt/decrypt methods fall back to plaintext I/O when KeyStore unavailable"

requirements-completed: [SEC-09, SEC-10]

# Metrics
duration: 4min
completed: 2026-03-05
---

# Phase 8 Plan 1: SecureFileManager Core Summary

**Tink StreamingAead singleton with AES256-GCM-HKDF-4KB via AndroidKeysetManager, separate doc_file_master_key alias, encrypt/decrypt/migrate/secureDelete API with graceful KeyStore fallback**

## Performance

- **Duration:** 4 min
- **Started:** 2026-03-05T00:11:29Z
- **Completed:** 2026-03-05T00:15:49Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Added tink-android:1.20.0 direct dependency with ProGuard keep rules (committed together per KEY DECISION from STATE.md)
- Created SecureFileManager singleton with full encryption API: encryptToFile, encryptBitmapToFile, encryptPdfToFile, decryptFromFile, decryptToTempFile, decryptToBitmap, encryptFileInPlace, secureDelete, migrateExistingFiles
- Configured backup exclusion for file_encryption_keyset.xml in both data_extraction_rules.xml and backup_rules.xml
- All methods implement graceful degradation (plaintext fallback when KeyStore unavailable)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add tink-android dependency, ProGuard keep rules, and backup exclusions** - `d9e5f22` (chore)
2. **Task 2: Create SecureFileManager singleton with full encryption API** - `4ac8f15` (feat)

## Files Created/Modified
- `app/build.gradle.kts` - Added tink-android:1.20.0 dependency in SECURITY section
- `app/proguard-rules.pro` - Added SEC-09 Tink keep rules and protobuf dontwarn
- `app/src/main/res/xml/data_extraction_rules.xml` - Added file_encryption_keyset.xml exclusion in cloud-backup and device-transfer sections
- `app/src/main/res/xml/backup_rules.xml` - Added file_encryption_keyset.xml exclusion
- `app/src/main/java/com/pdfscanner/app/util/SecureFileManager.kt` - Centralized file encryption/decryption/migration/secure-delete singleton

## Decisions Made
- Used `KeyTemplates.get("AES256_GCM_HKDF_4KB")` instead of `PredefinedStreamingAeadParameters.AES256_GCM_HKDF_4KB` because AndroidKeysetManager.Builder.withKeyTemplate() requires a KeyTemplate type, not a Parameters type. PredefinedStreamingAeadParameters returns AesGcmHkdfStreamingParameters which is not assignable to KeyTemplate.
- Suppressed `getPrimitive(Class)` deprecation warning because the non-deprecated `getPrimitive(Configuration, Class)` alternative requires a Configuration instance from `StreamingAeadConfigurationV0/V1`, which are package-private in Tink 1.20.0 and not accessible from application code.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Tink API mismatch: PredefinedStreamingAeadParameters vs KeyTemplates**
- **Found during:** Task 2 (SecureFileManager creation)
- **Issue:** Plan specified `PredefinedStreamingAeadParameters.AES256_GCM_HKDF_4KB` for withKeyTemplate(), but this returns `AesGcmHkdfStreamingParameters`, not `KeyTemplate`. AndroidKeysetManager.Builder.withKeyTemplate() accepts only `com.google.crypto.tink.KeyTemplate` or `com.google.crypto.tink.proto.KeyTemplate`.
- **Fix:** Used `KeyTemplates.get("AES256_GCM_HKDF_4KB")` which returns the correct `com.google.crypto.tink.KeyTemplate` type. Plan already mentioned this as a fallback option.
- **Files modified:** app/src/main/java/com/pdfscanner/app/util/SecureFileManager.kt
- **Verification:** Build compiles cleanly, correct key template resolved
- **Committed in:** 4ac8f15 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Plan anticipated this possibility ("If PredefinedStreamingAeadParameters is unavailable at compile time, fall back to KeyTemplates.get"). No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- SecureFileManager singleton is ready for all subsequent plans in Phase 8
- Plan 08-02 (Write Path Integration) can use encryptToFile, encryptBitmapToFile, encryptPdfToFile, encryptFileInPlace
- Plan 08-03 (Read Path Integration) can use decryptFromFile, decryptToTempFile, decryptToBitmap
- Plan 08-04 (Migration & Secure Delete Integration) can use migrateExistingFiles, secureDelete
- No blockers or concerns

## Self-Check: PASSED

All 5 created/modified files verified on disk. Both task commits (d9e5f22, 4ac8f15) verified in git log.

---
*Phase: 08-file-encryption-at-rest*
*Completed: 2026-03-05*
