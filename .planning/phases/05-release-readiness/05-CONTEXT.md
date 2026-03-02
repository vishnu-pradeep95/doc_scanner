# Phase 5: Release Readiness - Context

**Gathered:** 2026-03-01
**Status:** Ready for planning

<domain>
## Phase Boundary

The app passes all static analysis checks (detekt + Android lint), has correct manifest configuration for Play Store distribution, ProGuard/R8 keep rules verified against a release APK, and zero memory leaks confirmed on device via LeakCanary.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
User selected no areas to discuss — all implementation decisions delegated to Claude. Follow the success criteria from ROADMAP.md exactly:
- Detekt with detekt-baseline.xml so existing violations don't block the build
- Android Lint with `ContentDescription` and `TouchTargetSizeCheck` as errors, zero errors
- Manifest: camera `uses-feature android:required="false"`, `dataExtractionRules`, `fullBackupContent` excluding private scan dirs, FileProvider paths scoped to actually-used subdirectories
- ProGuard keep rules for ML Kit, Navigation SafeArgs, Coil, coroutines
- LeakCanary: zero retained Activity/Fragment/ViewModel leaks; Navigation 2.7.x AbstractAppBarOnDestinationChangedListener leak documented as library bug

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `app/proguard-rules.pro`: Already has keep rules for CameraX and CanHub cropper — new ML Kit / SafeArgs / Coil rules append here
- `app/src/main/res/xml/file_paths.xml`: FileProvider paths cover scans/, processed/, pdfs/, and cache/ (broad `path="/"`) — cache path needs tightening to actually-used subdirs

### Established Patterns
- `isMinifyEnabled = true` + `isShrinkResources = true` already set for release builds in `app/build.gradle.kts`
- ProGuard already references `proguard-android-optimize.txt` + `proguard-rules.pro`
- No detekt plugin or lint.xml exists yet — both need to be added from scratch
- No LeakCanary dependency anywhere yet

### Integration Points
- `AndroidManifest.xml`: `android:required="true"` on camera uses-feature (must change to `false`); `allowBackup="true"` with no `dataExtractionRules` / `fullBackupContent` attrs
- Navigation 2.7.6 in use — has known `AbstractAppBarOnDestinationChangedListener` leak; document and triage, do not block on it
- Libraries requiring ProGuard keep rules: `com.google.mlkit:text-recognition:16.0.0`, `com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1`, `androidx.navigation:navigation-fragment-ktx:2.7.6`, `io.coil-kt:coil:2.7.0`, `kotlinx-coroutines-android:1.7.3`
- Release APK E2E on physical device is ENVIRONMENT-BLOCKED — plan should note this and provide emulator fallback guidance

</code_context>

<specifics>
## Specific Ideas

No specific requirements — open to standard approaches.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 05-release-readiness*
*Context gathered: 2026-03-01*
