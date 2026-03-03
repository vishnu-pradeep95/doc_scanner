---
phase: 05-release-readiness
plan: "03"
subsystem: infra
tags: [proguard, r8, mlkit, android, release-build, safeargs, gms]

# Dependency graph
requires:
  - phase: 05-01
    provides: Detekt static analysis gate + LeakCanary integration
  - phase: 05-02
    provides: Lint gate configured, AndroidManifest hardened, backup exclusion XMLs created

provides:
  - ProGuard/R8 keep rules for ML Kit (com.google.mlkit.**), GMS (com.google.android.gms.**), and Navigation SafeArgs (*Args/*Directions)
  - Explicit documentation that Coil 2.7.0 and Kotlin Coroutines 1.7.3 consumer rules are AUTO-BUNDLED in their AARs (no explicit rules needed)
  - Release APK E2E checklist (RELEASE-04) verified on physical device — all 8 feature paths confirmed crash-free
  - RELEASE-03 requirement fully met

affects:
  - future release builds (any new ML Kit or GMS dependency will need keep rules reviewed)
  - Phase 5 completion — this is the final plan in the project

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ProGuard keep rules appended in a clearly labeled Phase 5 block — never replace existing rules, only append"
    - "AAR auto-bundled consumer rules (Coil, Coroutines) documented as NOT needing explicit keep rules — prevents future over-specification"

key-files:
  created: []
  modified:
    - app/proguard-rules.pro

key-decisions:
  - "ML Kit and GMS keep rules added with -keep class ... { *; } (not -keepnames) because both use reflection-loaded class hierarchies that require full member retention"
  - "SafeArgs generated classes use -keepnames (not -keep) — class names must survive obfuscation but members can be minified"
  - "Coil 2.7.0 and Kotlin Coroutines 1.7.3 explicitly documented as auto-bundled — no manual rules needed; this prevents future developers from adding redundant keep rules"
  - "RELEASE-04 E2E approved by user on physical device — all 8 feature paths (launch, camera, gallery, OCR, filters, PDF generation, PDF viewer, share/export) confirmed crash-free in release APK"

patterns-established:
  - "ProGuard rule sections labeled with phase comments for traceability"
  - "Auto-bundled consumer rules documented inline to prevent future redundancy"

requirements-completed: [RELEASE-03, RELEASE-04]

# Metrics
duration: 10min
completed: 2026-03-02
---

# Phase 5 Plan 03: ProGuard/R8 Keep Rules + Release APK E2E Summary

**ML Kit, GMS, and Navigation SafeArgs ProGuard keep rules added to proguard-rules.pro; release APK E2E verified on physical device with all 8 feature paths crash-free**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-03-02T03:40:00Z
- **Completed:** 2026-03-02T04:00:00Z
- **Tasks:** 2 (1 auto + 1 human-verify checkpoint)
- **Files modified:** 1

## Accomplishments

- Appended `-keep class com.google.mlkit.** { *; }` and `-keep class com.google.android.gms.** { *; }` rules to prevent R8 from stripping reflection-loaded ML Kit model processor classes in release builds
- Added `-keepnames class com.pdfscanner.app.**.*Args` and `*Directions` rules to preserve Navigation SafeArgs generated class names through obfuscation
- Explicitly documented that Coil 2.7.0 and Kotlin Coroutines 1.7.3 consumer rules are auto-bundled in their AARs — no manual rules needed
- All existing CameraX and CanHub Cropper keep rules preserved (only appended, never modified)
- RELEASE-04 release APK E2E checklist executed and approved by user on physical device — all 8 feature paths (HomeFragment, camera capture, gallery import, ML Kit OCR, filter application, PDF generation, PDF viewer, share/export) confirmed crash-free

## Task Commits

Each task was committed atomically:

1. **Task 1: Append ML Kit, GMS, and SafeArgs keep rules to proguard-rules.pro** - `53325ae` (chore)
2. **Task 2: Release APK E2E verification — RELEASE-04** - user-verified checkpoint (no code changes needed)

**Plan metadata:** (see final docs commit)

## Files Created/Modified

- `/home/vishnu/projects/doc_scanner/app/proguard-rules.pro` - Appended Phase 5 ProGuard keep rules block: ML Kit, GMS, SafeArgs; Coil + Coroutines documented as auto-bundled

## Decisions Made

- ML Kit and GMS use `-keep class ... { *; }` (full keep) not `-keepnames` — both libraries use reflection to load internal class hierarchies; stripping members would cause runtime crashes
- SafeArgs generated classes use `-keepnames` — only class names need to survive obfuscation for nav graph XML references; member minification is safe
- Coil 2.7.0 and Kotlin Coroutines 1.7.3 explicitly called out as not needing manual rules — prevents future over-specification
- RELEASE-04 was environment-blocked in WSL2 (no JDK/Android Studio) but user executed E2E on host machine and approved — all 8 feature paths confirmed working

## Deviations from Plan

None — plan executed exactly as written. Task 1 appended rules as specified; Task 2 was a human-verify checkpoint approved by user.

## Issues Encountered

- **RELEASE-04 environment block (WSL2):** `./gradlew assembleRelease` is blocked in WSL2 due to missing JDK/Android Studio. This was anticipated in the plan — the checklist was provided for host machine execution. User performed E2E on host machine and approved.

## User Setup Required

None — no external service configuration required. The ProGuard rules take effect automatically on the next release build.

## Next Phase Readiness

**Phase 5 is complete.** All release readiness requirements are met:

- RELEASE-01: Detekt static analysis gate (05-01)
- RELEASE-02: Lint gate configured, ContentDescription violations fixed (05-02)
- RELEASE-03: ProGuard/R8 keep rules for ML Kit, GMS, SafeArgs (this plan)
- RELEASE-04: Release APK E2E verified on physical device — all 8 feature paths confirmed (this plan)
- RELEASE-05: AndroidManifest hardened — camera required=false, permissions minimized (05-02)
- RELEASE-06: Backup exclusion XMLs (data_extraction_rules.xml, backup_rules.xml) created (05-02)
- RELEASE-07: FileProvider cache-path scope tightened to path="." (05-02)
- RELEASE-08: LeakCanary integrated — zero app-code leaks, library leak documented in KNOWN_LEAKS.md (05-01)
- RELEASE-09: JaCoCo coverage thresholds met (04-04, 04-07)

The project is ready for Play Store submission.

---
*Phase: 05-release-readiness*
*Completed: 2026-03-02*
