---
created: 2026-03-02T01:06:48.834Z
title: Run Phase 4 human verification tests
area: testing
files:
  - app/src/androidTest/java/com/pdfscanner/app/ui/
  - .planning/phases/04-test-coverage/04-VERIFICATION.md
---

## Problem

Phase 4 (Test Coverage) verification completed with status `human_needed`. All 8/8 automated must-haves passed, but two items require a device/clean environment that isn't available in WSL2:

1. **Instrumented tests** — WSL2 blocks USB passthrough to Android device/emulator, so `connectedDebugAndroidTest` could not be run during verification.
2. **Clean-build JVM run** — JaCoCo XML data exists and is current (session timestamp 2026-03-01T23:49:03Z), but a fresh clean-state run was not confirmed.

Phase 4 is blocked from being marked complete until these are confirmed.

## Solution

On a machine with a connected Android device or emulator (API 24+):

1. Run `./gradlew connectedDebugAndroidTest` — expect **6 tests, 0 failures** (5 fragment smoke tests + 1 navigation flow test)
2. Run `./gradlew clean testDebugUnitTest jacocoTestReport` — expect **61 tests, 0 failures**; JaCoCo HTML report should show util/ LINE coverage >=22%

Once both pass, return to the execute-phase session and type **"approved"** to complete Phase 4 and advance to Phase 5 (Release Readiness).
