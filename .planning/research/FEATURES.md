# Feature Research: Security Hardening for High-Sensitivity Document Scanner

**Domain:** Android Document Scanner App -- Security Hardening (v1.2)
**Researched:** 2026-03-03
**Confidence:** HIGH (official Android docs, OWASP Mobile Top 10 2024, direct codebase inspection, current web sources)

## Context

This research answers: **what security features are non-negotiable vs. differentiating vs. counterproductive for an Android document scanner that handles IDs, medical records, and contracts?**

The app already has:
- App-private storage (`filesDir` for scans/, processed/, pdfs/)
- FileProvider with scoped paths (tightened from `/` to `.` in v1.1)
- Backup exclusion rules (scans, processed, pdfs excluded from cloud/device backup)
- ProGuard/R8 with minification + obfuscation enabled
- Single exported component (MainActivity with LAUNCHER intent)
- No storage permissions (uses app-private dirs only)

What the app does NOT have:
- No encryption at rest (files are plaintext in app-private dirs)
- No FLAG_SECURE (screenshots freely captured)
- No biometric/PIN app lock
- No secure file deletion (standard `File.delete()` only)
- No logging discipline (49 `Log.*` calls across 9 files in production code)
- No network security configuration XML
- No clipboard protection
- No root/tamper detection
- SharedPreferences storing document history in plaintext JSON

The threat model: a lost/stolen phone with an unlocked bootloader or a rooted device can read all app-private files. A shoulder-surfer can screenshot sensitive documents. A malicious app with accessibility permissions can read screen content. Backup extraction can recover document metadata.

---

## OWASP Mobile Top 10 2024 Coverage Map

Every security feature below is mapped to the relevant OWASP Mobile Top 10 2024 risk category.

| OWASP 2024 | Risk | Relevance to This App | Coverage Status |
|------------|------|------------------------|----------------|
| M1 | Improper Credential Usage | LOW -- no backend auth, no API keys in code | N/A |
| M2 | Inadequate Supply Chain | MEDIUM -- ML Kit, CameraX, CanHub dependencies | Partially covered by R8/ProGuard |
| M3 | Insecure Authentication/Authorization | HIGH -- no app lock, no biometric gate | **NOT COVERED** |
| M4 | Insufficient Input/Output Validation | MEDIUM -- navigation args, imported file paths | Partially covered (SafeArgs) |
| M5 | Insecure Communication | LOW -- offline-only app, no network calls | Partially covered by default |
| M6 | Inadequate Privacy Controls | HIGH -- document images, PDF metadata, history | **NOT COVERED** |
| M7 | Insufficient Binary Protections | MEDIUM -- R8 obfuscation exists | Partially covered |
| M8 | Security Misconfiguration | MEDIUM -- logging in production, no network config | **NOT COVERED** |
| M9 | Insecure Data Storage | HIGH -- plaintext files, plaintext SharedPrefs | **NOT COVERED** |
| M10 | Insufficient Cryptography | HIGH -- zero encryption at rest | **NOT COVERED** |

**Coverage gap summary:** M3, M6, M8, M9, M10 are the critical gaps. This milestone must address all five.

---

## Feature Landscape

### Table Stakes (Non-Negotiable for Banking-Grade Document Security)

These are what any security-conscious user, enterprise reviewer, or app store security audit expects. Missing these signals the app is unsuitable for sensitive documents.

| Feature | Why Expected | Complexity | OWASP | Dependencies |
|---------|--------------|------------|-------|--------------|
| **SEC-01: FLAG_SECURE on sensitive screens** | Screenshots of scanned IDs/medical docs can be shared maliciously. Every banking and medical app sets FLAG_SECURE. Recents screen also shows thumbnail of sensitive content without this flag. | LOW | M6, M9 | MainActivity.kt -- set on window. Must decide: all screens vs. only document-viewing screens. |
| **SEC-02: Biometric/PIN app lock** | A lost unlocked phone exposes all scanned documents. BiometricPrompt with DEVICE_CREDENTIAL fallback is the standard approach. Banking apps, HIPAA-compliant scanners (EncryptScan), and password managers all require this. | HIGH | M3 | New: BiometricPrompt dependency (`androidx.biometric:biometric:1.1.0`). New: AppLockActivity or lock overlay in MainActivity. New: Lock state management in EncryptedSharedPreferences or DataStore. SettingsFragment needs toggle. |
| **SEC-03: Production log stripping** | 49 `Log.*` calls across 9 source files. `Log.d(TAG, "Sharing PDF: ${document.name}, URI: $uri")` in HistoryFragment leaks file paths and document names to logcat. Any app with READ_LOGS (pre-Android 4.1) or ADB access can read these. | LOW | M8 | ProGuard rule: `-assumenosideeffects class android.util.Log { ... }` strips Log.d/Log.v/Log.i in release builds. Keep Log.w and Log.e for crash diagnostics. No code changes needed. |
| **SEC-04: Network security configuration** | Although the app is offline-only, a network_security_config.xml that explicitly disables cleartext traffic is defense-in-depth. Prevents accidental HTTP if network features are added later. Also stops library dependencies from making cleartext requests. Android 9+ disables cleartext by default, but explicit config documents intent and covers API 24-28. | LOW | M5, M8 | New XML file: `res/xml/network_security_config.xml`. One line in AndroidManifest: `android:networkSecurityConfig="@xml/network_security_config"`. |
| **SEC-05: Secure temp file handling** | Temp files in cacheDir (crop results, PDF compression intermediates) contain document images. Current cleanup is best-effort with 1-hour threshold. Temp files should be cleaned immediately after use and use randomized names to prevent prediction. | LOW | M9 | Audit PdfUtils.kt (pdf_compress_temp), CropImageActivity cache usage, PdfPageExtractor temp files. Ensure `finally` blocks delete temps. Use `File.createTempFile()` for randomized names. |
| **SEC-06: Exported component audit** | Only MainActivity should be exported. CropImageActivity (CanHub) has no `android:exported` attribute -- Android 12+ defaults to `false` for activities without intent-filters, but explicit is better. FileProvider is correctly `exported="false"`. Verify no implicit exports. | LOW | M4, M8 | Manifest review. Add explicit `android:exported="false"` to CropImageActivity. Verify all components. |
| **SEC-07: Intent data validation** | Navigation arguments (pdfPath, pdfName) pass file paths as strings via SafeArgs. PdfViewerFragment opens `File(args.pdfPath)` without validating the path is within app-private storage. A crafted navigation argument could theoretically point to any readable file. Import flows accept URIs from external apps without validating MIME types. | MEDIUM | M4 | Add path validation in PdfViewerFragment, PdfEditorFragment. Verify imported URIs resolve to expected content types. Validate all SafeArgs string parameters that become file paths. |
| **SEC-08: SharedPreferences metadata protection** | `document_history` SharedPreferences stores JSON with document names, file paths, page counts, and timestamps in plaintext. On a rooted device, this reveals what documents exist and where they are stored. `pdf_scanner_prefs` stores theme/filter preferences (low sensitivity). | MEDIUM | M9, M10 | Migrate `document_history` prefs to encrypted storage. Options: (1) Tink with Android Keystore for direct encryption, (2) community fork `encryptedprefs` library for drop-in EncryptedSharedPreferences replacement, (3) DataStore with Tink encryption layer. `pdf_scanner_prefs` can remain plaintext (no sensitive data). |

### Differentiators (Competitive Advantage for Security-Focused Scanner)

These separate a "has basic security" app from one that demonstrates banking-grade security posture. Valued by security-conscious users and enterprise deployment.

| Feature | Value Proposition | Complexity | OWASP | Dependencies |
|---------|-------------------|------------|-------|--------------|
| **SEC-09: File encryption at rest** | App-private storage is only protected by Linux filesystem permissions. On rooted devices or via ADB backup, document images and PDFs are fully readable. Encryption at rest with AES-256-GCM using Android Keystore-backed keys makes files unreadable even with physical device access. | HIGH | M9, M10 | Google Tink library (`com.google.crypto.tink:tink-android:1.15.0`) for Streaming AEAD encryption. All file read/write paths in the app must be wrapped with encrypt/decrypt streams. Impacts: CameraFragment (save), ImageProcessor (filter output), PdfUtils (PDF generation), PdfViewerFragment (read), sharing flow (decrypt-to-temp for FileProvider). Existing files need migration path. |
| **SEC-10: Secure file deletion (overwrite before delete)** | Standard `File.delete()` only removes the filesystem reference. Data remains on flash storage until overwritten. For documents containing SSNs, medical info, or legal contracts, recoverable deletion is a liability. | MEDIUM | M6, M9 | Utility function that overwrites file content with random bytes before deletion. Must handle: document deletion (HistoryFragment), temp file cleanup (MainActivity), page removal (PagesFragment). Note: Flash storage wear-leveling means overwrite is not 100% guaranteed on modern NAND, but it defeats casual forensics and is the standard mobile approach. |
| **SEC-11: Clipboard protection** | Document names and text from OCR results could be copied to clipboard, where other apps can read it. Android 12+ shows a toast when clipboard is accessed. Android 13+ auto-clears clipboard after 1 hour. Marking clipboard content as sensitive prevents keyboard preview. | LOW | M6 | Use `ClipDescription.EXTRA_IS_SENSITIVE` flag when placing text on clipboard. For views containing document content, consider disabling long-press copy where appropriate. Minimal code change. |
| **SEC-12: Accessibility data protection** | Accessibility services can read all on-screen text. Malicious accessibility apps can extract document names, file sizes, and metadata from the history list. Android's `accessibilityDataSensitive` attribute (API 34+) blocks untrusted accessibility services from reading view content. | LOW | M6 | Add `android:accessibilityDataSensitive="auto"` to sensitive views in layouts (document names, file paths, page counts). Gracefully degrades on older APIs. |
| **SEC-13: Auto-lock on background** | If app is backgrounded (user switches to another app), it should re-require authentication after a configurable timeout. Without this, an app lock is trivially bypassed by anyone who picks up a phone with the app already open in recents. | MEDIUM | M3 | Requires: lifecycle observer on Application or ProcessLifecycleOwner to detect app going to background. Timer-based re-lock (configurable: immediate, 30s, 1min, 5min). State persistence for lock timestamp. Depends on SEC-02 (app lock). |
| **SEC-14: Debug/root detection** | On rooted devices, app-private files are accessible to any app with root. Debug builds with `isDebuggable = true` allow ADB data extraction. Detecting these conditions and warning the user (not blocking -- that causes false positives) is a security signal. | MEDIUM | M7 | Check `Build.TAGS.contains("test-keys")`, check for su binary, check `Settings.Secure.ADB_ENABLED`. Show one-time warning dialog, not a hard block. Store dismissal in prefs. No external dependency needed. |

### Anti-Features (Commonly Pursued, Wrong for This Scope)

Features that seem like good security practice but are wrong for this project's constraints and goals.

| Anti-Feature | Why Tempting | Why Problematic | What to Do Instead |
|--------------|-------------|-----------------|-------------------|
| **Full-disk encryption reliance** | "Android already encrypts the disk, so app-level encryption is redundant." | Android FDE/FBE only protects when the device is powered off. Once the user unlocks their phone, all app-private files are readable. FBE credential-encrypted storage requires the device to be locked, but app-private `filesDir` uses device-encrypted (DE) storage by default. App-level encryption is needed for at-rest protection while the phone is unlocked. | Implement SEC-09 (file encryption at rest) using Tink + Android Keystore. |
| **Certificate pinning** | Prevents MITM attacks by pinning server certificates. | The app is offline-only with zero network calls. Certificate pinning adds complexity (pin rotation, backup pins, expiration) with zero security benefit. Even Google's 2025 guidance recommends against pinning for most use cases. | Skip entirely. SEC-04 (network security config) that disables cleartext is sufficient defense-in-depth. |
| **Custom encryption implementation** | "Roll AES encryption directly for full control." | Custom crypto is the #1 cause of M10 (Insufficient Cryptography). Off-by-one in IV generation, ECB mode instead of GCM, key derivation mistakes -- all common in custom implementations. | Use Google Tink, which is maintained by Google Security engineers, battle-tested in Google Pay and Firebase, and handles key management, IV generation, and mode selection correctly. |
| **Blocking rooted devices entirely** | Banks sometimes refuse to run on rooted devices. | False positives from custom ROMs, developer devices, and enterprise MDM solutions. Blocking users who chose to root their device is hostile UX for a document scanner (unlike banking where regulatory compliance mandates it). | SEC-14: Warn about root detection, do not block. Let the user make an informed decision. |
| **DRM-style content protection** | Prevent any export/sharing of scanned documents. | The app's core value proposition includes sharing PDFs via email, messaging, and cloud storage. Restricting sharing defeats the purpose. Users need to get their documents out of the app. | FLAG_SECURE (SEC-01) prevents screenshots. The share flow itself is intentional user action and should not be restricted. |
| **Biometric-only auth (no PIN fallback)** | "Biometric is more secure than PIN." | Not all devices have biometric hardware. Users with accessibility needs may not be able to use fingerprint/face recognition. Android BiometricPrompt with `DEVICE_CREDENTIAL` fallback is the correct approach -- it allows PIN/pattern/password as an alternative. | SEC-02: BiometricPrompt with `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)`. |
| **Per-document passwords** | Each PDF gets its own password, like password-protected PDFs. | Android's native PdfDocument API cannot create encrypted PDFs (requires iTextPDF or PDFBox, both out of scope per PROJECT.md). App-level encryption (SEC-09) protects all files uniformly. Per-document passwords add UX friction without meaningful security improvement over whole-app encryption. | SEC-02 (app lock) + SEC-09 (encryption at rest) provide uniform protection. Individual PDF passwords are a v2+ feature when a real PDF library is added. |
| **Encrypted database (SQLCipher/Room)** | Encrypt the document history database. | The app uses SharedPreferences, not SQLite/Room. Adding Room + SQLCipher for 50 document entries is massive over-engineering. The migration effort and dependency overhead are disproportionate. | SEC-08: Encrypt SharedPreferences using Tink or the community `encryptedprefs` fork. Same protection, 1/10th the effort. |

---

## Feature Dependencies

```
[SEC-01: FLAG_SECURE]
    (independent -- single line in MainActivity.onCreate())

[SEC-02: Biometric/PIN app lock]
    |
    +--enables--> [SEC-13: Auto-lock on background]
    |                  (auto-lock is meaningless without app lock)
    |
    +--uses-----> [SEC-08: SharedPreferences encryption]
                       (lock enabled/timeout stored in encrypted prefs)

[SEC-03: Log stripping]
    (independent -- ProGuard rule only, no code changes)

[SEC-04: Network security config]
    (independent -- XML + manifest attribute)

[SEC-05: Secure temp file handling]
    (independent -- code audit + finally blocks)

[SEC-06: Exported component audit]
    (independent -- manifest review)

[SEC-07: Intent data validation]
    (independent -- code changes in fragments)

[SEC-08: SharedPreferences encryption]
    |
    +--required-by--> [SEC-02: App lock state storage]
    +--required-by--> [SEC-09: Encryption key preference storage]

[SEC-09: File encryption at rest]
    |
    +--requires--> [SEC-08: Encrypted prefs for key metadata]
    +--impacts---> ALL file I/O paths (camera save, filter, PDF gen,
                   PDF view, share, import, temp files)
    +--requires--> Migration path for existing unencrypted files

[SEC-10: Secure file deletion]
    (independent -- utility function, called from delete paths)

[SEC-11: Clipboard protection]
    (independent -- flag on ClipData)

[SEC-12: Accessibility data protection]
    (independent -- layout XML attribute, API 34+)

[SEC-13: Auto-lock on background]
    +--requires--> [SEC-02: App lock must exist first]

[SEC-14: Root/debug detection]
    (independent -- utility check + dialog)
```

### Critical Dependency Chain

The most complex dependency is **SEC-09 (file encryption)**, which:
1. Requires SEC-08 (encrypted prefs) to store encryption metadata
2. Touches every file I/O path in the app (camera, filter, PDF, viewer, share)
3. Requires a migration strategy for existing unencrypted files
4. Must be done AFTER simpler security features are stable
5. Is the single highest-risk feature in the milestone

**Recommended build order:**
1. Low-risk, independent features first (SEC-01, SEC-03, SEC-04, SEC-05, SEC-06)
2. Input validation (SEC-07)
3. SharedPreferences encryption (SEC-08) -- enables SEC-02
4. App lock (SEC-02) + auto-lock (SEC-13)
5. File encryption at rest (SEC-09) -- highest risk, do last
6. Polish features (SEC-10, SEC-11, SEC-12, SEC-14)

---

## MVP Recommendation

### Must-Have (Ship-Blocking for Security Milestone)

These features must be completed for the security milestone to be considered successful. Without them, the app cannot claim to be suitable for sensitive documents.

1. **SEC-01: FLAG_SECURE** -- Prevents screenshots of sensitive documents. Lowest effort, highest immediate impact.
2. **SEC-02: Biometric/PIN app lock** -- Prevents unauthorized access when phone is unlocked. Table stakes for any document app handling IDs/medical/legal.
3. **SEC-03: Production log stripping** -- Prevents information leakage via logcat. One ProGuard rule.
4. **SEC-04: Network security config** -- Defense-in-depth, explicit security posture. One XML file.
5. **SEC-05: Secure temp file handling** -- Prevents temp files from accumulating sensitive data. Code audit scope.
6. **SEC-06: Exported component audit** -- Closes potential attack surface. Manifest-only change.
7. **SEC-07: Intent data validation** -- Prevents path traversal and malformed input. Code changes in 2-3 fragments.
8. **SEC-08: SharedPreferences encryption** -- Protects document metadata (names, paths, timestamps). Required for SEC-02 state storage.
9. **SEC-13: Auto-lock on background** -- Makes app lock meaningful (prevents bypass via recents).

### Should-Have (Complete if Time Allows)

10. **SEC-09: File encryption at rest** -- The gold standard for data protection. HIGH complexity due to touching all I/O paths. May require its own focused phase.
11. **SEC-10: Secure file deletion** -- Overwrite before delete. Medium effort, meaningful for forensics defense.
12. **SEC-11: Clipboard protection** -- Low effort, covers an edge case.
13. **SEC-12: Accessibility data protection** -- Low effort, API 34+ only.

### Defer to Future Milestone

14. **SEC-14: Root/debug detection** -- Warning-only, low urgency. Nice for enterprise but not user-blocking.

---

## Complexity Budget

| Feature | Lines of Code (est.) | New Dependencies | Files Modified | Risk |
|---------|---------------------|-----------------|----------------|------|
| SEC-01 | ~5 | None | 1 (MainActivity) | LOW |
| SEC-02 | ~300-400 | `androidx.biometric:biometric:1.1.0` | 4-6 new + 2 existing | HIGH |
| SEC-03 | ~5 | None | 1 (proguard-rules.pro) | LOW |
| SEC-04 | ~15 | None | 2 (new XML + manifest) | LOW |
| SEC-05 | ~40-60 | None | 3-4 (PdfUtils, MainActivity, PdfPageExtractor) | LOW |
| SEC-06 | ~3 | None | 1 (AndroidManifest.xml) | LOW |
| SEC-07 | ~50-80 | None | 3-4 (PdfViewerFragment, PdfEditorFragment, HomeFragment) | MEDIUM |
| SEC-08 | ~100-150 | Tink or `encryptedprefs` | 2-3 (DocumentHistory, new EncryptedPrefs wrapper) | MEDIUM |
| SEC-09 | ~400-600 | `com.google.crypto.tink:tink-android` | 8-12 (every file I/O path) | HIGH |
| SEC-10 | ~30-50 | None | 3-4 (new utility + delete call sites) | LOW |
| SEC-11 | ~10-15 | None | 1-2 (clipboard usage sites) | LOW |
| SEC-12 | ~10-20 | None | 3-4 (layout XMLs) | LOW |
| SEC-13 | ~100-150 | None | 2-3 (new lifecycle observer + lock check) | MEDIUM |
| SEC-14 | ~60-80 | None | 2-3 (new utility + dialog) | LOW |

**Total estimate:** ~1,150-1,650 new lines of Kotlin + ~30 lines of XML configuration

---

## Implementation Notes for Key Features

### SEC-01: FLAG_SECURE

```kotlin
// In MainActivity.onCreate(), BEFORE setContentView()
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)
```

Decision point: Apply globally (all screens including settings/home) or conditionally (only when viewing documents). Recommendation: **apply globally**. The home screen shows recent document thumbnails and names, which are sensitive. The overhead of conditional FLAG_SECURE (toggling per-fragment) is not worth the complexity.

Consider making this a user-configurable setting in SettingsFragment for users who want to take screenshots of their own documents.

### SEC-02: Biometric App Lock

Use `androidx.biometric:biometric:1.1.0` (stable, Kotlin 1.9 compatible).

```kotlin
val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Unlock PDF Scanner")
    .setSubtitle("Authenticate to access your documents")
    .setAllowedAuthenticators(
        BiometricManager.Authenticators.BIOMETRIC_STRONG
        or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )
    .build()
```

Architecture: Lock check in `MainActivity.onResume()`. If locked, show full-screen lock overlay that consumes all touch events. On successful auth, remove overlay. Store lock-enabled state and timeout in encrypted prefs (SEC-08).

### SEC-03: ProGuard Log Stripping

```proguard
# Strip verbose, debug, and info logs in release builds
# Keep warn and error for crash diagnostics
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
```

Note: This strips the method calls but not necessarily the string arguments. For maximum security, also avoid string concatenation in log calls (use lambdas or guard with `if (BuildConfig.DEBUG)`).

### SEC-08: SharedPreferences Encryption

The official `androidx.security:security-crypto` library was deprecated in April 2025. Options:

1. **Google Tink directly** (RECOMMENDED): Use Tink's AEAD primitive with Android Keystore to encrypt/decrypt the JSON string before storing in regular SharedPreferences. Most control, most future-proof.
2. **Community fork `encryptedprefs`** by Ed Holloway-George: Drop-in EncryptedSharedPreferences replacement. Less maintenance certainty.
3. **DataStore + Tink**: Modern approach but requires migrating from SharedPreferences API. Higher effort for this scope.

Recommendation: **Tink directly** wrapping the existing SharedPreferences. The `document_history` prefs store a single JSON string -- encrypting that one value with Tink AEAD is straightforward.

### SEC-09: File Encryption at Rest

Use Tink's Streaming AEAD for large files (scanned images, PDFs):

```kotlin
// Encrypt
val streamingAead = keysetHandle.getPrimitive(StreamingAead::class.java)
val ciphertextStream = streamingAead.newEncryptingStream(
    FileOutputStream(encryptedFile), associatedData
)
// Write bitmap/PDF data to ciphertextStream

// Decrypt
val plaintextStream = streamingAead.newDecryptingStream(
    FileInputStream(encryptedFile), associatedData
)
// Read bitmap/PDF data from plaintextStream
```

Critical implementation concerns:
- **Migration**: Existing unencrypted files must be encrypted on first app launch after update. Show progress indicator during migration.
- **Sharing**: When sharing via FileProvider, decrypt to a temp file, share, then securely delete the temp. The temp file exists only during the share flow.
- **Performance**: Streaming AEAD processes in chunks. No full-file buffering needed. Overhead is ~5-10% for large files.
- **Key management**: Use Android Keystore master key. Key is hardware-backed on devices with StrongBox/TEE.
- **Error handling**: If Keystore is wiped (factory reset, secure boot change), encrypted files become unrecoverable. Need graceful error handling.

---

## OWASP Coverage After Implementation

| OWASP 2024 | Before | After (all features) | Covering Features |
|------------|--------|---------------------|-------------------|
| M1 | N/A | N/A | No backend auth needed |
| M2 | Partial | Partial | R8 obfuscation (existing) |
| M3 | NOT COVERED | COVERED | SEC-02 (app lock), SEC-13 (auto-lock) |
| M4 | Partial | COVERED | SEC-07 (intent validation), SEC-06 (export audit) |
| M5 | Partial | COVERED | SEC-04 (network security config) |
| M6 | NOT COVERED | COVERED | SEC-01 (FLAG_SECURE), SEC-10 (secure delete), SEC-11 (clipboard), SEC-12 (accessibility) |
| M7 | Partial | Partial+ | SEC-14 (root detection), existing R8 |
| M8 | NOT COVERED | COVERED | SEC-03 (log stripping), SEC-04 (network config), SEC-06 (component audit) |
| M9 | NOT COVERED | COVERED | SEC-05 (temp files), SEC-08 (encrypted prefs), SEC-09 (file encryption) |
| M10 | NOT COVERED | COVERED | SEC-08 (Tink for prefs), SEC-09 (Tink for files) |

**Result**: 5 critical gaps (M3, M6, M8, M9, M10) fully closed. M7 improved but not fully addressed (full binary protection requires additional obfuscation tools beyond R8, which is out of scope).

---

## Feature Prioritization Matrix

| Feature | Security Impact | Implementation Cost | Priority |
|---------|----------------|---------------------|----------|
| SEC-01: FLAG_SECURE | HIGH | LOW | **P0** |
| SEC-03: Log stripping | HIGH | LOW | **P0** |
| SEC-04: Network security config | MEDIUM | LOW | **P0** |
| SEC-06: Exported component audit | MEDIUM | LOW | **P0** |
| SEC-05: Secure temp files | MEDIUM | LOW | **P1** |
| SEC-07: Intent validation | HIGH | MEDIUM | **P1** |
| SEC-08: Encrypted SharedPrefs | HIGH | MEDIUM | **P1** |
| SEC-02: App lock (biometric/PIN) | HIGH | HIGH | **P1** |
| SEC-13: Auto-lock on background | HIGH | MEDIUM | **P1** |
| SEC-11: Clipboard protection | LOW | LOW | **P2** |
| SEC-12: Accessibility protection | LOW | LOW | **P2** |
| SEC-10: Secure file deletion | MEDIUM | MEDIUM | **P2** |
| SEC-09: File encryption at rest | CRITICAL | HIGH | **P2** |
| SEC-14: Root/debug detection | LOW | MEDIUM | **P3** |

**Priority key:**
- **P0**: Zero-effort, high-impact. Do first, in a single plan.
- **P1**: Core security features. The heart of the milestone.
- **P2**: Hardening polish. Complete if schedule allows; SEC-09 may warrant its own phase.
- **P3**: Nice-to-have. Defer if timeline is tight.

---

## Sources

### Official Documentation (HIGH confidence)
- Android Developers: FLAG_SECURE -- https://developer.android.com/security/fraud-prevention/activities
- Android Developers: Network Security Configuration -- https://developer.android.com/privacy-and-security/security-config
- Android Developers: Secure Clipboard Handling -- https://developer.android.com/privacy-and-security/risks/secure-clipboard-handling
- Android Developers: Log Info Disclosure -- https://developer.android.com/privacy-and-security/risks/log-info-disclosure
- Android Developers: Secure User Authentication (BiometricPrompt) -- https://developer.android.com/security/fraud-prevention/authentication
- Android Developers: Deep Link Security -- https://developer.android.com/privacy-and-security/risks/unsafe-use-of-deeplinks
- Android Developers: Cryptography -- https://developer.android.com/privacy-and-security/cryptography
- Google Developers: Tink Encryption -- https://developers.google.com/tink/encrypt-data
- OWASP Mobile Top 10 2024 -- https://owasp.org/www-project-mobile-top-10/

### Library Documentation (HIGH confidence)
- AndroidX Security Crypto (deprecated notice) -- https://developer.android.com/jetpack/androidx/releases/security
- EncryptedSharedPreferences API Reference -- https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences
- Community fork: encrypted-shared-preferences -- https://github.com/ed-george/encrypted-shared-preferences

### Industry Analysis (MEDIUM confidence)
- ProGuard Log Stripping (droidcon 2025) -- https://www.droidcon.com/2025/04/01/efficient-logging-in-kotlin-with-proguard-optimization/
- OWASP MASTG: Remove Logging Code -- https://mas.owasp.org/MASTG/best-practices/MASTG-BEST-0002/
- Secure Mobile Biometric Auth Best Practices -- https://blog.ostorlab.co/secure-mobile-biometric-authentication.html
- EncryptedSharedPreferences Migration Guide 2026 -- https://www.droidcon.com/2025/12/16/goodbye-encryptedsharedpreferences-a-2026-migration-guide/
- Android Intent Security Best Practices -- https://securecodingpractices.com/android-intent-security-best-practices-filters-permissions-validation/
- Android Clipboard Content Protection (Microsoft) -- https://www.microsoft.com/en-us/security/blog/2023/03/06/protecting-android-clipboard-content-from-unintended-exposure/
- NowSecure: Secure Deletion Best Practices -- https://books.nowsecure.com/secure-mobile-development/en/coding-practices/understand-secure-deletion-of-data.html
- HIPAA-Compliant Document Scanning (EncryptScan) -- https://encryptscan.com/
- Android Security Enhancements 2025 (Google Blog) -- https://android-developers.googleblog.com/2025/12/enhancing-android-security-stop-malware.html

### Codebase Inspection (HIGH confidence)
- Direct inspection of: AndroidManifest.xml, MainActivity.kt, DocumentHistory.kt, AppPreferences.kt, PdfUtils.kt, ScannerViewModel.kt, PdfViewerFragment.kt, SettingsFragment.kt, HomeFragment.kt, CameraFragment.kt, HistoryFragment.kt, proguard-rules.pro, file_paths.xml, data_extraction_rules.xml, backup_rules.xml, build.gradle.kts
- Log call count: 49 calls across 9 files (grep verified)
- Exported components: 1 exported (MainActivity), 1 implicit (CropImageActivity), 1 non-exported (FileProvider)
- Encryption status: Zero encryption at rest confirmed
- FLAG_SECURE: Not present anywhere in codebase

---

*Feature research for: Android Document Scanner v1.2 -- Security Hardening*
*Researched: 2026-03-03*
