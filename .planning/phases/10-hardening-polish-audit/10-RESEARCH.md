# Phase 10: Hardening Polish & Audit - Research

**Researched:** 2026-03-04
**Domain:** Android security hardening (clipboard, accessibility, root detection, cross-cutting audit)
**Confidence:** HIGH

## Summary

Phase 10 addresses three discrete security features (SEC-11, SEC-12, SEC-14) plus a cross-cutting audit to verify no plaintext leaks survive from Phases 6-9. All three features are low-complexity, well-documented Android platform APIs that require no external libraries.

SEC-11 (sensitive clipboard) is a 3-line change to the existing `copyToClipboard()` method in PagesFragment. SEC-12 (accessibility data sensitive) requires adding an XML attribute to layout files containing document names/paths, with an API 34+ version guard. SEC-14 (root/debuggable detection) is a lightweight utility class checking `Build.TAGS`, su binary paths, and `ApplicationInfo.FLAG_DEBUGGABLE`, with a one-time dismissible warning dialog.

The audit component involves verifying the release APK on a physical device: confirming R8 strips Log.d/i/v calls (SEC-03), encrypted files on disk have no plaintext headers, and all security features from Phases 6-9 function correctly end-to-end.

**Primary recommendation:** Implement all three features in a single plan (they are independent, low-complexity changes), then dedicate a second plan to the cross-cutting security audit and verification.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SEC-11 | Clipboard content marked as sensitive via ClipDescription.EXTRA_IS_SENSITIVE | Exact API documented; single call site identified in PagesFragment.copyToClipboard(); compileSdk 35 supports the constant directly |
| SEC-12 | Sensitive views protected from untrusted accessibility services via accessibilityDataSensitive | API 34 attribute confirmed; compileSdk 35 / targetSdk 34 already support it; identified 4 layout files with sensitive TextViews |
| SEC-14 | Root/debuggable device detection with one-time warning dialog | No-library approach documented; 4 lightweight checks identified; dialog dismissal persisted in SecurePreferences |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Android Platform APIs | API 33-35 | ClipDescription.EXTRA_IS_SENSITIVE, View.accessibilityDataSensitive | No library needed; platform APIs only |
| android.os.Build | Platform | Root/debuggable detection via Build.TAGS | Standard Android API, no dependency |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| SecurePreferences (existing) | N/A | Persist "root warning dismissed" flag | Already in project; stores one-time dialog dismissal |
| MaterialAlertDialogBuilder (existing) | material:1.11.0 | Root warning dialog | Already in project; consistent dialog styling |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Manual root checks | RootBeer library | RootBeer adds a dependency for what is 4 simple checks; project decision explicitly says warn-only, not block, so lightweight manual checks suffice |
| Play Integrity API | SafetyNet/Play Integrity | Requires Google Play Services, network call, backend verification server; overkill for a non-blocking warning |

**Installation:**
```bash
# No new dependencies required - all platform APIs and existing libraries
```

## Architecture Patterns

### SEC-11: Sensitive Clipboard

**What:** Mark OCR text copied to clipboard as sensitive so Android 13+ hides it from clipboard preview.

**Where to change:** Single method in `PagesFragment.kt` line 347-352.

**Current code:**
```kotlin
private fun copyToClipboard(text: String) {
    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("OCR Text", text)
    clipboard.setPrimaryClip(clip)
    showSnackbar(R.string.text_copied)
}
```

**Required change:**
```kotlin
// Source: https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling
private fun copyToClipboard(text: String) {
    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("OCR Text", text)
    // SEC-11: Mark clipboard content as sensitive — hides preview on API 33+
    clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    clipboard.setPrimaryClip(clip)
    showSnackbar(R.string.text_copied)
}
```

**Key detail:** `ClipDescription.EXTRA_IS_SENSITIVE` constant is available since compileSdk 33. This project compiles with SDK 35, so the constant resolves directly. No version guard needed -- the flag is simply ignored on API < 33 devices.

### SEC-12: Accessibility Data Sensitive

**What:** Protect views showing document names, file paths, and OCR text from untrusted accessibility services.

**Where to change:** XML layout files containing sensitive TextViews. The attribute `android:accessibilityDataSensitive` was added in API 34.

**Sensitive views identified:**
1. `item_document.xml` -- `textDocumentName` (shows document name)
2. `item_recent_document.xml` -- `textDocName` (shows document name)
3. `dialog_ocr_result.xml` -- `textOcrResult` (shows OCR-recognized text)
4. `item_page.xml` -- any view showing page content (thumbnail images)

**Implementation pattern (XML):**
```xml
<!-- Only effective on API 34+; ignored on earlier versions -->
<TextView
    android:id="@+id/textDocumentName"
    android:accessibilityDataSensitive="yes"
    ... />
```

**Key details:**
- The attribute `android:accessibilityDataSensitive` was added in API 34 (confirmed via ApiSince=34)
- This project has `compileSdk = 35`, so the attribute compiles without issue
- On API < 34 devices, the attribute is silently ignored (standard Android behavior for unknown XML attributes)
- The attribute accepts: `auto` (0, default), `yes` (1), `no` (2)
- When set to `yes`, only accessibility services with `isAccessibilityTool=true` can read the view's content
- No `tools:targetApi` annotation needed since `compileSdk >= 34`

**Note on `tools:targetApi`:** Since the manifest already has `tools:targetApi="34"` on the `<application>` tag, and `compileSdk = 35`, lint should not flag this attribute. If it does, adding `tools:ignore="UnusedAttribute"` on individual views suppresses the warning for API < 34.

### SEC-14: Root/Debuggable Detection

**What:** Detect rooted or debuggable device environment and show a one-time, non-blocking, dismissible warning dialog.

**Architecture:**
```
util/
  RootDetector.kt     # Static checks, returns Boolean
MainActivity.kt       # Shows dialog on cold start if detected + not dismissed
SecurePreferences      # Stores KEY_ROOT_WARNING_DISMISSED boolean
```

**Detection checks (4 lightweight heuristics):**

```kotlin
object RootDetector {

    /**
     * Returns true if any root/debug indicator is detected.
     * Not foolproof -- intended as advisory (warn-only per SEC-14).
     */
    fun isDeviceCompromised(context: Context): Boolean {
        return checkTestKeys() || checkSuBinary() || checkDebuggable(context)
    }

    /** Check 1: Build signed with test-keys (custom ROM or AOSP build) */
    private fun checkTestKeys(): Boolean {
        val tags = android.os.Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    /** Check 2: su binary exists in common paths */
    private fun checkSuBinary(): Boolean {
        val paths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/data/local/bin/su", "/data/local/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/vendor/bin/su"
        )
        return paths.any { java.io.File(it).exists() }
    }

    /** Check 3: App is debuggable (re-signed or tampered APK) */
    private fun checkDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
```

**Important:** The `FLAG_DEBUGGABLE` check will trigger on debug builds. Must guard the dialog with `!BuildConfig.DEBUG` (same pattern as FLAG_SECURE in SEC-01):

```kotlin
// In MainActivity.onCreate(), after biometric setup
if (!BuildConfig.DEBUG && RootDetector.isDeviceCompromised(this)) {
    val prefs = SecurePreferences.getInstance(this)
    if (!prefs.getBoolean("${SecurePreferences.APP_PREFIX}root_warning_dismissed", false)) {
        showRootWarningDialog()
    }
}
```

**Dialog pattern:**
```kotlin
private fun showRootWarningDialog() {
    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.security_warning_title)
        .setMessage(R.string.security_warning_rooted_message)
        .setPositiveButton(R.string.i_understand) { _, _ ->
            SecurePreferences.getInstance(this).edit()
                .putBoolean("${SecurePreferences.APP_PREFIX}root_warning_dismissed", true)
                .apply()
        }
        .setCancelable(false)  // Must acknowledge
        .show()
}
```

### Anti-Patterns to Avoid
- **Blocking rooted devices:** Project decision explicitly says warn-only. Never call `finish()` or disable functionality based on root detection. False positives from custom ROMs would lock out legitimate users.
- **Network-based root detection:** Play Integrity API requires backend verification, network access, and Play Services. This app is offline-only.
- **Checking FLAG_DEBUGGABLE in debug builds:** Debug builds are always debuggable. Guard with `!BuildConfig.DEBUG`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Clipboard sensitivity | Custom clipboard manager | `ClipDescription.EXTRA_IS_SENSITIVE` platform API | 3-line addition to existing code |
| Accessibility view protection | Custom AccessibilityDelegate | `android:accessibilityDataSensitive="yes"` XML attribute | Platform handles enforcement; no code needed |
| Root detection | Full root detection framework | 3-4 simple heuristic checks | Warn-only use case doesn't justify RootBeer/Play Integrity complexity |

**Key insight:** All three security features are platform-level APIs or simple heuristics. No custom frameworks or libraries needed.

## Common Pitfalls

### Pitfall 1: EXTRA_IS_SENSITIVE on API < 33
**What goes wrong:** Using the constant `ClipDescription.EXTRA_IS_SENSITIVE` compiles fine (it's a static final), but the behavior is only enforced on API 33+.
**Why it happens:** Developers add an API version check thinking the constant won't compile.
**How to avoid:** No version check needed. The constant resolves at compile time. On API < 33, the extras are set but the system ignores them (no-op). This is the correct behavior.
**Warning signs:** Unnecessary `if (Build.VERSION.SDK_INT >= 33)` guard around the clipboard code.

### Pitfall 2: accessibilityDataSensitive lint warnings
**What goes wrong:** Lint may warn about using an API 34 attribute when minSdk is 24.
**Why it happens:** Default lint rules flag attributes not available on minSdk.
**How to avoid:** The attribute is silently ignored on older APIs (standard XML behavior). Add `tools:targetApi="34"` on the specific views if lint complains, or the existing `tools:targetApi="34"` on `<application>` in AndroidManifest may suffice.
**Warning signs:** Build warnings during `./gradlew lint`.

### Pitfall 3: Root detection false positive in debug builds
**What goes wrong:** `FLAG_DEBUGGABLE` is always set in debug builds, causing the root warning to appear during development.
**Why it happens:** Debug APKs are inherently debuggable.
**How to avoid:** Always wrap root detection + dialog logic in `if (!BuildConfig.DEBUG)`.
**Warning signs:** Root warning dialog appearing on development devices.

### Pitfall 4: Log.w/Log.e leaking file names in release
**What goes wrong:** SEC-03 strips Log.v/d/i via ProGuard, but Log.w and Log.e are intentionally retained.
**Why it happens:** Design decision to keep warning/error logs for crash diagnostics.
**How to avoid:** This is acceptable per project decision. The file names in Log.w messages (e.g., "Encryption failed for photo.pdf") are minimal metadata, not document content. Logcat access requires USB debugging or ADB shell, which requires physical access or developer mode.
**Warning signs:** Overly paranoid about Log.w -- do not strip them, they are needed for diagnostics.

### Pitfall 5: Root warning dialog showing before biometric prompt
**What goes wrong:** If root warning dialog shows simultaneously with BiometricPrompt, UX is confusing.
**Why it happens:** Both checks run in `onCreate`/`onResume`.
**How to avoid:** Show root warning dialog AFTER biometric authentication succeeds. Check after `isAuthenticated = true` or in a post-auth callback. Alternatively, show it only if lock is not enabled (simpler).

## Code Examples

### Complete SEC-11 Implementation
```kotlin
// Source: https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling
// In PagesFragment.kt — replace existing copyToClipboard()
private fun copyToClipboard(text: String) {
    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = android.content.ClipData.newPlainText("OCR Text", text)
    // SEC-11: Mark as sensitive — hides clipboard preview on API 33+
    clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
    }
    clipboard.setPrimaryClip(clip)
    showSnackbar(R.string.text_copied)
}
```

### SEC-12 XML Attribute
```xml
<!-- Add to TextViews showing document names in item_document.xml, item_recent_document.xml -->
<TextView
    android:id="@+id/textDocumentName"
    android:accessibilityDataSensitive="yes"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    ... />

<!-- Add to OCR result text in dialog_ocr_result.xml -->
<TextView
    android:id="@+id/textOcrResult"
    android:accessibilityDataSensitive="yes"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    ... />
```

### SEC-14 Dialog with One-Time Dismissal
```kotlin
// In MainActivity.onCreate(), AFTER biometric auth setup, guarded by !BuildConfig.DEBUG
if (!BuildConfig.DEBUG) {
    checkRootedDevice()
}

private fun checkRootedDevice() {
    if (!RootDetector.isDeviceCompromised(this)) return
    val prefs = SecurePreferences.getInstance(this)
    val key = "${SecurePreferences.APP_PREFIX}root_warning_dismissed"
    if (prefs.getBoolean(key, false)) return

    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.security_warning_title)
        .setMessage(R.string.security_warning_rooted_message)
        .setPositiveButton(R.string.i_understand) { _, _ ->
            prefs.edit().putBoolean(key, true).apply()
        }
        .setCancelable(false)
        .show()
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| No clipboard sensitivity | `EXTRA_IS_SENSITIVE` flag | API 33 (Android 13) | Clipboard preview hidden on supported devices |
| No accessibility data protection | `accessibilityDataSensitive` attribute | API 34 (Android 14) | Blocks untrusted accessibility services from reading marked views |
| RootBeer / SafetyNet | Lightweight manual checks (warn-only) | Ongoing | No dependency, no network, fits offline app paradigm |
| Play Integrity API | Not applicable | N/A | App is offline-only; Play Integrity requires network + backend |

**Deprecated/outdated:**
- SafetyNet Attestation: Deprecated in favor of Play Integrity API. Neither is applicable here (offline app, warn-only use case).

## Audit Checklist (Cross-Cutting Verification)

The audit component of this phase verifies all security features from Phases 6-9 on a release APK:

### Log Leak Verification
- [ ] Build release APK: `./gradlew assembleRelease`
- [ ] Install on physical device
- [ ] Run `adb logcat -s PdfPageExtractor SecureFileManager ImageUtils NativePdfView PdfEditor` and exercise all features
- [ ] Confirm: No Log.v/d/i output appears (R8 stripped them per SEC-03)
- [ ] Confirm: Only Log.w/e appear (retained for crash diagnostics, per design decision)
- [ ] Review Log.w messages: verify they contain only file names (metadata), not document content

### Plaintext Leak Verification
- [ ] `adb shell run-as com.pdfscanner.app ls files/scans/` -- all files should be encrypted (no readable JPEG/PNG headers)
- [ ] `adb shell run-as com.pdfscanner.app cat shared_prefs/secure_prefs.xml` -- should be encrypted (Tink AEAD)
- [ ] `adb shell run-as com.pdfscanner.app ls files/pdfs/` -- PDF files should be encrypted
- [ ] Verify old unencrypted prefs files are present but empty or contain no sensitive data

### Feature Verification Matrix
| SEC | Feature | Verification Method |
|-----|---------|-------------------|
| SEC-01 | FLAG_SECURE | Recents shows blank; screenshot fails |
| SEC-03 | Log stripping | adb logcat shows no v/d/i output |
| SEC-04 | No cleartext traffic | Network config XML review |
| SEC-05 | Temp file cleanup | Restart app, check cacheDir is clean |
| SEC-06 | No exported components | Only MainActivity exported |
| SEC-07 | Path validation | Import file from outside app storage |
| SEC-08 | Encrypted SharedPrefs | Inspect secure_prefs.xml on device |
| SEC-09 | Encrypted files | Inspect files/ directory on device |
| SEC-10 | Secure delete | Delete document, verify file gone |
| SEC-11 | Sensitive clipboard | Copy OCR text, check clipboard preview |
| SEC-12 | Accessibility protection | Enable TalkBack, verify attribute in layout dump |
| SEC-13 | Lock timeout | Background app, verify re-auth after timeout |
| SEC-14 | Root detection | Test on rooted device or emulator |

## Open Questions

1. **Log.w file name leakage -- acceptable?**
   - What we know: Log.w messages include `file.name` (e.g., "Encryption failed for document.pdf"). Per SEC-03 design, Log.w/e are retained.
   - What's unclear: Whether file names alone constitute a meaningful information leak.
   - Recommendation: Accept as-is. File names are metadata, not content. Logcat access requires ADB (physical access or developer mode). The project decision to retain Log.w/e for diagnostics is sound.

2. **Root warning dialog timing with biometric lock**
   - What we know: Both root detection and biometric prompt run during app launch.
   - What's unclear: Whether showing root dialog before or after auth is better UX.
   - Recommendation: Show root warning AFTER biometric auth succeeds. If lock is not enabled, show on `onCreate()`. This prevents the dialog from appearing behind the lock overlay or causing a confusing double-prompt.

## Sources

### Primary (HIGH confidence)
- [Android Secure Clipboard Handling](https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling) - ClipDescription.EXTRA_IS_SENSITIVE API and code examples
- [ClipDescription API Reference](https://developer.android.com/reference/android/content/ClipDescription) - EXTRA_IS_SENSITIVE constant documentation
- [View API Reference](https://developer.android.com/reference/android/view/View) - accessibilityDataSensitive attribute, API 34
- [Microsoft .NET Android API Reference](https://learn.microsoft.com/en-us/dotnet/api/android.views.view.accessibilitydatasensitiveyes?view=net-android-35.0) - Confirmed ApiSince=34 for ACCESSIBILITY_DATA_SENSITIVE_YES

### Secondary (MEDIUM confidence)
- [Android Developers Blog - Enhancing Android Security](https://android-developers.googleblog.com/2025/12/enhancing-android-security-stop-malware.html) - accessibilityDataSensitive usage patterns and XML examples
- [Indusface Root Detection Guide](https://www.indusface.com/learning/how-to-implement-root-detection-in-android-applications/) - Root detection techniques without libraries
- [Android API 13 Behavior Changes](https://developer.android.com/about/versions/13/behavior-changes-all) - Clipboard auto-clear and sensitivity behavior

### Tertiary (LOW confidence)
- Various Medium articles on root detection -- used for corroboration only, not as primary source

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All platform APIs, no external dependencies, well-documented
- Architecture: HIGH - Exact code locations identified, changes are minimal and isolated
- Pitfalls: HIGH - Based on direct codebase analysis and known Android platform behavior

**Research date:** 2026-03-04
**Valid until:** 2026-04-04 (stable platform APIs, unlikely to change)
