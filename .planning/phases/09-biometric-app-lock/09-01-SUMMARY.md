---
phase: 09-biometric-app-lock
plan: 01
subsystem: security
tags: [biometric, biometric-prompt, app-lock, secure-preferences, settings-ui]

# Dependency graph
requires:
  - phase: 06-security-foundation
    provides: SecurePreferences with APP_PREFIX key namespacing
provides:
  - AppLockManager singleton for lock state, timeout, and auth checks
  - Security section in Settings UI with lock toggle and timeout selector
  - API-tiered authenticator selection (WEAK|CREDENTIAL on <30, STRONG|CREDENTIAL on 30+)
affects: [09-02-biometric-app-lock, 10-hardening-polish]

# Tech tracking
tech-stack:
  added: [androidx.biometric:biometric:1.1.0, androidx.lifecycle:lifecycle-process:2.6.2]
  patterns: [API-tiered authenticator flags, BiometricManager availability check before toggle, SecurePreferences for lock state persistence]

key-files:
  created:
    - app/src/main/java/com/pdfscanner/app/util/AppLockManager.kt
    - app/src/main/res/drawable/ic_lock.xml
    - app/src/main/res/drawable/ic_timer.xml
  modified:
    - app/build.gradle.kts
    - app/src/main/res/layout/fragment_settings.xml
    - app/src/main/java/com/pdfscanner/app/ui/SettingsFragment.kt
    - app/src/main/res/values/strings.xml

key-decisions:
  - "AppLockManager uses object singleton pattern (matches SecurePreferences convention)"
  - "getAllowedAuthenticators uses API-level split: BIOMETRIC_WEAK|DEVICE_CREDENTIAL on <30, BIOMETRIC_STRONG|DEVICE_CREDENTIAL on 30+ to prevent IllegalArgumentException"
  - "canAuthenticate returns true for both BIOMETRIC_SUCCESS and BIOMETRIC_ERROR_NONE_ENROLLED (hardware present but no enrollment)"
  - "Lock toggle hidden entirely when BIOMETRIC_ERROR_NO_HARDWARE or BIOMETRIC_ERROR_HW_UNAVAILABLE"

patterns-established:
  - "AppLockManager.getAllowedAuthenticators() is the single source of truth for authenticator flags across all biometric usage"
  - "Security settings section pattern: SwitchMaterial toggle with conditional child rows"

requirements-completed: [SEC-02, SEC-13]

# Metrics
duration: 3min
completed: 2026-03-05
---

# Phase 9 Plan 01: AppLockManager + Settings UI Summary

**AppLockManager singleton with API-tiered biometric authenticator selection and Settings Security section with lock toggle and timeout selector**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-05T01:36:07Z
- **Completed:** 2026-03-05T01:39:31Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Created AppLockManager utility with 9 public methods for lock state management, timeout tracking, and API-tiered authenticator selection
- Added Security section to Settings layout with SwitchMaterial toggle and auto-lock timeout row
- Wired toggle with BiometricManager availability check, device security required dialog, and timeout selector dialog
- Added biometric:1.1.0 and lifecycle-process:2.6.2 dependencies with zero new warnings

## Task Commits

Each task was committed atomically:

1. **Task 1: Add biometric dependency and create AppLockManager utility** - `84ffb24` (feat)
2. **Task 2: Add Security section to Settings layout and wire toggle + timeout logic** - `fa6aeca` (feat)

## Files Created/Modified
- `app/src/main/java/com/pdfscanner/app/util/AppLockManager.kt` - Lock state management singleton with API-tiered authenticator selection
- `app/build.gradle.kts` - Added biometric:1.1.0 and lifecycle-process:2.6.2 dependencies
- `app/src/main/res/drawable/ic_lock.xml` - Material Design lock icon vector drawable
- `app/src/main/res/drawable/ic_timer.xml` - Material Design timer icon vector drawable
- `app/src/main/res/values/strings.xml` - 17 new string resources for Security settings
- `app/src/main/res/layout/fragment_settings.xml` - Security section with lock toggle and timeout row
- `app/src/main/java/com/pdfscanner/app/ui/SettingsFragment.kt` - Lock toggle handler, timeout dialog, device security check

## Decisions Made
- AppLockManager uses object singleton pattern matching SecurePreferences convention
- getAllowedAuthenticators uses API-level split at Build.VERSION_CODES.R to prevent IllegalArgumentException on API 28-29
- canAuthenticate returns true for BIOMETRIC_ERROR_NONE_ENROLLED so the toggle is visible but shows enrollment guidance dialog
- Lock row hidden entirely on devices with no authentication hardware (BIOMETRIC_ERROR_NO_HARDWARE)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- AppLockManager is complete and ready for Plan 02 to wire enforcement into MainActivity
- ProcessLifecycleOwner observer (Plan 02) will call AppLockManager.recordBackgroundTime()
- BiometricPrompt display (Plan 02) will use AppLockManager.getAllowedAuthenticators() and shouldRequireAuth()

## Self-Check: PASSED

All created files verified present. All commits (84ffb24, fa6aeca) verified in git log.

---
*Phase: 09-biometric-app-lock*
*Completed: 2026-03-05*
