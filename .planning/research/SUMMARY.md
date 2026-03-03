# Project Research Summary

**Project:** PDF Scanner -- Security Hardening (v1.2)
**Domain:** Android security hardening for high-sensitivity document handling
**Researched:** 2026-03-03
**Confidence:** HIGH

## Executive Summary

This project adds banking-grade security hardening to an existing, functional Android document scanner (single-activity, MVVM, Kotlin 1.9.21, CameraX, ML Kit). The app is production-ready from a features standpoint but has five critical OWASP Mobile Top 10 2024 gaps: no encryption at rest (M9, M10), no screenshot protection (M6), no app lock (M3), production logging enabled (M8), and missing network security config (M8). The security hardening work is entirely additive -- the existing MVVM architecture does not need restructuring. A new `security/` package sits between the repository/utility layer and the filesystem, wrapping all I/O with Tink StreamingAead encryption. The ViewModel layer stays completely unaware of encryption. The three new runtime libraries (Tink 1.20.0, security-crypto 1.1.0, Timber 5.0.1) are all pure-Java, introducing no Kotlin version conflicts with the project's pinned Kotlin 1.9.21.

The recommended implementation order follows a strict dependency chain established by the architecture research: quick-win independent features first (FLAG_SECURE, Timber, network security config), then the encryption foundation (SecurityManager + SecureFileManager + SecurePreferences migration), then full file encryption integration across all I/O paths, then the biometric app lock, and finally a security audit and hardening polish pass. This order is not arbitrary -- reversing it causes rework. For example, implementing biometric auth before encrypted SharedPreferences means lock state cannot be securely stored; implementing file encryption before SecureFileManager exists means every I/O site must be touched twice.

The highest risks are concentrated in the encrypted storage migration (data loss if not idempotent and crash-safe), KeyStore instability on API 24-27 devices (persistent crash loops unless EncryptedSharedPreferences creation is wrapped in fallback logic), and the architectural decision to keep biometric authentication keys strictly separated from file encryption keys (mixing them causes permanent, unrecoverable data loss when users enroll new fingerprints). A secondary risk is UX over-friction -- adding every security feature at maximum aggressiveness drives users to uninstall. Encryption should be transparent and automatic; biometric app lock must be opt-in via Settings.

---

## Key Findings

### Recommended Stack

Three new runtime dependencies and one build-time plugin are needed. All runtime additions are pure-Java libraries with no Kotlin version constraints.

**Core technologies:**
- **Google Tink (tink-android 1.20.0):** AES-256-GCM streaming file encryption. The only correct choice for large-file encryption on Android -- raw AndroidKeyStore requires 80-120 lines of boilerplate with high IV-misuse risk; EncryptedFile (security-crypto) reads entire files into memory (unacceptable for multi-page scans). Tink's StreamingAead processes files in 4KB chunks at constant memory overhead. No ProGuard rules needed (official docs confirm). Shades protobuf-lite internally -- no conflict with ML Kit's protobuf.
- **androidx.security:security-crypto 1.1.0:** EncryptedSharedPreferences for migrating the two existing plaintext SharedPreferences files (document_history, pdf_scanner_prefs). Deprecated as of July 2025 but stable -- the final release before Google closed the library. Drop-in replacement for SharedPreferences API, eliminating the need to rewrite all ViewModel/repository call sites to use coroutine-based DataStore. Right-sized for migrating existing synchronous SharedPreferences usage with 2-3 key-value files.
- **Timber 5.0.1:** Centralized, environment-aware logging to replace 49 direct `android.util.Log` calls across 9 source files (PdfPageExtractor, PdfUtils, ImageUtils, MainActivity, HistoryFragment, CameraFragment, PdfViewerFragment, NativePdfView, PdfEditorFragment). In release builds, no Tree is planted so all calls are no-ops. Detekt ForbiddenImport rule prevents regression. ~20 KB after R8.
- **OWASP dependency-check-gradle 12.2.0 (build-time only, not in APK):** CVE scanning against the National Vulnerability Database. Configured to fail the build on CVSS >= 7.0. Run manually (`./gradlew dependencyCheckAnalyze`); first run downloads NVD data (5-20 minutes); subsequent runs 1-3 minutes. Does not affect assembleRelease or existing tasks.

Platform-level features (FLAG_SECURE, tapjacking protection via filterTouchesWhenObscured, network security config XML, clipboard EXTRA_IS_SENSITIVE, debuggable runtime check, allowBackup=false) use Android APIs directly with no additional dependencies.

**APK size impact:** approximately 900 KB to 1.3 MB increase total (Tink + security-crypto + Timber, after R8 tree-shaking). Acceptable for a security-critical app.

**Existing proguard-rules.pro requires additions:** explicit keep rules for `com.google.crypto.tink.**` and Tink's shaded protobuf classes must be added when Tink is introduced. Without them, R8 strips protobuf reflection fields and the release build crashes on first encrypted operation -- the classic "works in debug, crashes in release" failure.

### Expected Features

**Must have (ship-blocking for the security milestone):**
- **SEC-01: FLAG_SECURE** -- prevents screenshots of scanned IDs/medical docs; one line in MainActivity before setContentView(), covers all 8 fragments automatically via single-activity architecture
- **SEC-02: Biometric/PIN app lock** -- BiometricPrompt with DEVICE_CREDENTIAL fallback; must be opt-in via Settings toggle; requires API-level branching (three distinct behavioral tiers: API 24-27, API 28-29, API 30+)
- **SEC-03: Production log stripping via Timber** -- replace 49 `android.util.Log` calls; zero release logcat output; Detekt ForbiddenImport enforcement
- **SEC-04: Network security config** -- explicit cleartext=false XML; matters most for API 24-28 where Android 9's default does not apply
- **SEC-05: Secure temp file handling** -- audit and enforce finally-block cleanup; randomized file names via File.createTempFile(); no overwrite-based "secure deletion" (provably ineffective on flash storage due to wear leveling)
- **SEC-06: Exported component audit** -- add explicit android:exported="false" to CropImageActivity (CanHub); all other components already correctly configured
- **SEC-07: Intent data validation** -- path validation in PdfViewerFragment/PdfEditorFragment against app-private storage canonical paths; content:// URI-only for external imports; validate SafeArgs string parameters that become file paths
- **SEC-08: SharedPreferences encryption** -- migrate document_history and pdf_scanner_prefs to EncryptedSharedPreferences; crash-safe, idempotent migration with sentinel key; old file retained through v1.2 cycle
- **SEC-13: Auto-lock on background** -- ProcessLifecycleOwner tracks background time; re-authenticate after configurable timeout (recommended default: 5 minutes); depends on SEC-02 existing first

**Should have (complete if schedule allows):**
- **SEC-09: File encryption at rest** -- the highest-complexity feature; touches every file I/O path (CameraX capture, ImageProcessor, PdfUtils, PdfViewer, share flow, import flow, Coil thumbnail loading); requires migration of existing unencrypted files with progress UI; explicitly the highest-risk feature in the milestone and may warrant its own focused phase
- **SEC-11: Clipboard protection** -- EXTRA_IS_SENSITIVE flag on OCR text copies; low effort, ~10-15 lines
- **SEC-12: Accessibility data protection** -- android:accessibilityDataSensitive="auto" on sensitive layout views; API 34+ only, graceful degradation on older APIs
- **SEC-10: Secure file deletion** -- on flash storage, overwrite-based deletion is ineffective; if SEC-09 is implemented, all deletions are already cryptographically secure (deleting encrypted data with the key in KeyStore is equivalent to secure erasure)

**Defer to future milestone:**
- SEC-14: Root/debug detection -- warning-only (not blocking), low urgency, no regulatory requirement for this app category
- Per-document PDF passwords -- requires iTextPDF or PDFBox (out of scope per PROJECT.md)
- Play Integrity API -- requires a backend server; this is an offline-first app with no backend
- Biometric per-document unlocking (CryptoObject per PDF) -- v2+ enhancement
- Root detection via RootBeer -- false positives on developer devices; encryption at rest is the correct defense regardless of root status

### Architecture Approach

Security integrates as a new `security/` package that wraps around existing I/O operations without restructuring MVVM. The key principle: encryption sits between the repository/utility layer and the filesystem, not inside fragments or ViewModel. The ViewModel passes opaque URIs and requires zero changes. All file I/O is funneled through SecureFileManager (enforcing encryption consistently, making accidental bypasses impossible). SharedPreferences access is funneled through SecurePreferences. FLAG_SECURE is controlled via a single NavController OnDestinationChangedListener in MainActivity. BiometricPrompt is hosted in fragments (lifecycle-aware) -- never in Activity.onCreate() which crashes before the view exists.

**Major new components:**
1. **SecurityManager** -- Tink keyset initialization, Android Keystore master key lifecycle. Critical architecture decision here: biometric-tied keys (auth gate) must be strictly separate from file encryption keys. If the encryption key has `setUserAuthenticationRequired(true)`, Android permanently invalidates it when the user enrolls a new fingerprint, making all encrypted files unreadable forever.
2. **SecureFileManager** -- StreamingAead encrypt/decrypt streams; encryptInPlace() for post-CameraX-capture; decryptToFile() for PdfRenderer (which requires a seekable FileDescriptor, incompatible with streaming decryption -- a critical compatibility constraint); custom Coil Fetcher for thumbnail loading from encrypted JPEG files
3. **BiometricHelper** -- BiometricPrompt wrapper with API-level-aware authenticator configuration. Three distinct code paths are required: API 24-27 (FingerprintManager + KeyguardManager PIN fallback), API 28-29 (BIOMETRIC_STRONG only + separate PIN button), API 30+ (BIOMETRIC_STRONG or DEVICE_CREDENTIAL -- the "golden path")
4. **SecurePreferences** -- EncryptedSharedPreferences wrapper with crash-safe migration (sentinel key, old file preserved through release cycle, try-catch fallback to unencrypted on persistent KeyStore failure on API 24-27 devices)
5. **IntentValidator** -- validates content:// URI scheme for external imports; canonical path validation to ensure SafeArgs file paths resolve within app-private storage (prevents path traversal)
6. **PdfScannerApp (Application subclass)** -- new; required for Timber.plant(), debuggable runtime check; registered in AndroidManifest via android:name=".PdfScannerApp"

**Components with zero changes:** ScannerViewModel (encryption-unaware), nav_graph.xml, file_paths.xml, FileProvider.

**Components with moderate changes:** CameraFragment (encrypt-in-place after CameraX capture), PdfUtils (wrap I/O), PdfViewerFragment/PdfEditorFragment (decrypt-to-temp for PdfRenderer), HomeFragment (biometric gate).

### Critical Pitfalls

Five critical pitfalls (data loss or permanent crash-loop severity) from PITFALLS.md:

1. **SharedPreferences migration data loss** -- naive "read old, write new, delete old" loses all document history if the app is killed mid-migration. Prevention: idempotent migration with a `migration_complete` sentinel key; synchronous `.commit()` (not `.apply()`); retain the old unencrypted prefs file through the entire v1.2 release cycle without deleting it. See PITFALLS.md Pitfall 1.

2. **KeyStore crash loop on API 24-27** -- EncryptedSharedPreferences.create() throws `KeyStoreException` or `AEADBadTagException` on Huawei, Honor, OPPO, and certain Samsung devices at API 24-27. The crash is persistent (every subsequent cold start). Prevention: wrap all EncryptedSharedPreferences creation in try-catch; on exception, delete corrupted prefs file and retry once; on second failure, fall back to unencrypted prefs. Must test on real API 24-26 hardware (emulators use software KeyStore and rarely exhibit this failure). See PITFALLS.md Pitfall 2.

3. **R8 strips Tink protobuf reflection classes in release builds** -- debug works perfectly; release APK crashes on first encrypted operation with `NoSuchFieldError` in AesGcmKeyFormat. Prevention: add explicit keep rules for `com.google.crypto.tink.**` and `com.google.crypto.tink.shaded.protobuf.**` to proguard-rules.pro in the same commit that adds the Tink dependency; verify every encrypted operation in a release APK on a physical device before proceeding. See PITFALLS.md Pitfall 3.

4. **Encryption key permanently invalidated on biometric enrollment** -- if file encryption keys are created with `setUserAuthenticationRequired(true)`, Android KeyStore irreversibly invalidates them when any new fingerprint is enrolled. All encrypted documents become permanently unreadable with no recovery path. Prevention: architecture must separate auth keys (biometric-tied, intentionally invalidatable) from encryption keys (`setUserAuthenticationRequired(false)`). This decision must be made before any implementation begins and cannot be retrofitted. See PITFALLS.md Pitfall 4.

5. **SharedPreferences metadata leaks to cloud backup** -- current backup exclusion rules cover document files but not the SharedPreferences domain. Document names ("Passport_Scan.pdf", "Medical_Report.pdf") back up to Google Drive in plaintext. After encrypting prefs, restoring from backup causes a crash (encrypted file, no matching KeyStore key on the new device). Prevention: add `domain="sharedpref"` exclusions for all prefs files to both data_extraction_rules.xml and backup_rules.xml in the same commit that introduces encrypted prefs. See PITFALLS.md Pitfall 5.

---

## Implications for Roadmap

The architecture research provides an explicit build order based on strict dependencies. The feature research provides priority tiers. Together they suggest five phases:

### Phase 1: Security Foundation and Quick Wins

**Rationale:** Independent, low-risk security features with no new complex dependencies or architecture changes. These are all P0 priority with LOW implementation risk. Bundling them delivers immediate, visible security improvement while the complex encryption infrastructure is being designed. Includes creating the PdfScannerApp Application subclass (needed for Timber and the debuggable check) and OWASP dependency scanning setup.

**Delivers:** PdfScannerApp Application class; Timber replacing 49 Log.* calls with Detekt ForbiddenImport enforcement; FLAG_SECURE on sensitive screens via OnDestinationChangedListener (conditional on BuildConfig.DEBUG to avoid breaking screenshot tests); network security config XML; android:allowBackup="false"; CropImageActivity exported=false; OWASP dependency-check-gradle plugin configured; first CVE scan run and suppression file created for ML Kit/GMS false positives.

**Addresses:** SEC-01 (FLAG_SECURE), SEC-03 (Timber), SEC-04 (network config), SEC-06 (exported audit); OWASP M5, M6 (partial), M7 (partial), M8 (partial).

**Avoids:** Pitfall 6 (FLAG_SECURE conditional on BuildConfig.DEBUG -- debug builds retain screenshot capability for test tooling); Pitfall 12 (verify dependency tree after adding Timber and OWASP plugin shows no Kotlin 2.x artifacts).

**Research flag:** Standard patterns -- skip phase research. All implementations are well-documented Android APIs with established patterns. Single plan execution is appropriate.

### Phase 2: Encrypted Storage Foundation

**Rationale:** The prerequisite for everything else in the milestone. SecurityManager and SecureFileManager must exist before any file I/O can be encrypted. SecurePreferences must exist before the biometric app lock can store its state. The SharedPreferences migration, backup rule updates, and R8 keep rules must all land in this phase together -- they are a single atomic security unit. This is the highest-risk phase in the milestone outside of SEC-09 (file encryption), due to migration safety complexity and KeyStore instability on older devices.

**Delivers:** SecurityManager (Tink keyset + Android Keystore master key, auth/encryption key separation architecture); SecureFileManager (encrypt/decrypt stream wrapper); SecurePreferences with crash-safe migration (sentinel key, old file retained, try-catch fallback); IntentValidator; SecureDeletion utility (encrypt-then-delete pattern, not overwrite-then-delete); R8 keep rules for Tink and security-crypto; data_extraction_rules.xml and backup_rules.xml updated with sharedpref domain exclusions; SEC-05 audit (temp file handling) and SEC-07 (intent/path validation).

**Addresses:** SEC-05 (temp files), SEC-07 (intent validation), SEC-08 (encrypted SharedPreferences); OWASP M4, M9 (partial), M10 (partial).

**Avoids:** Pitfall 1 (idempotent migration, sentinel key, old file retained); Pitfall 2 (KeyStore crash-loop fallback required before any migration code); Pitfall 3 (R8 keep rules added in same commit as Tink dependency); Pitfall 4 (auth/encryption key separation is a foundational architecture decision, not retrofittable); Pitfall 5 (sharedpref domain exclusions in same commit as encrypted prefs); Pitfall 11 (SharedPreferences reads moved to Dispatchers.IO to avoid main-thread blocking after encryption overhead is added).

**Research flag:** Needs phase research. The migration safety pattern, KeyStore fallback architecture, and the auth-key/encryption-key separation decision are complex enough that detailed task-level planning is warranted before implementation begins. The R8 keep-rule configuration also has enough edge cases to merit pre-implementation research.

### Phase 3: File Encryption at Rest

**Rationale:** The highest-complexity feature in the milestone. Depends on Phase 2 (SecureFileManager must exist). Touches every file I/O path in the app -- 8 to 12 files modified. Must include a migration strategy for existing unencrypted files. Two critical compatibility constraints require specific workarounds: PdfRenderer requires a seekable FileDescriptor (incompatible with streaming decryption -- must decrypt to a temp file); Coil cannot load ciphertext (requires a custom Fetcher or decrypt-to-temp approach). CameraX writes plaintext to disk during capture (no way to inject an encrypted OutputStream into the pipeline) -- post-capture encrypt-in-place in the onImageSaved callback is the solution.

**Delivers:** Full AES-256-GCM file encryption across scans/, processed/, pdfs/ directories; CameraFragment encrypt-in-place after CameraX capture; ImageProcessor/ImageUtils encrypted I/O via SecureFileManager; PdfUtils encrypted output; custom Coil Fetcher for thumbnail decryption; PdfViewerFragment/PdfEditorFragment decrypt-to-temp for PdfRenderer; CanHub cropper decrypt-to-temp pattern; share flow decrypt-to-temp for FileProvider sharing; migration pass for existing unencrypted files with "Securing documents..." progress UI.

**Addresses:** SEC-09 (file encryption at rest); OWASP M9 (fully closed), M10 (fully closed).

**Avoids:** Pitfall 4 (encryption key architecture established in Phase 2 -- no `setUserAuthenticationRequired(true)` on encryption keys); Pitfall 7 (no StrongBox-backed keys for file encryption -- StrongBox adds 209ms per 4MB chunk, unacceptable for large scans; "Encrypting..." progress phase added; benchmark on target API 24-27 devices before committing to chunk size); Pitfall 9 (no overwrite-based "secure deletion" -- encrypt-then-delete is correct on flash storage and is now automatic when files are encrypted).

**Research flag:** Needs phase research. The CameraX post-capture encrypt-in-place flow, custom Coil Fetcher implementation, PdfRenderer decrypt-to-temp lifecycle management, and CanHub cropper integration each have non-obvious implementation details. Benchmark data for AES encryption throughput on API 24-27 ARMv7 devices is needed before finalizing Tink chunk size configuration. This phase may warrant being split into separate plans: encryption infrastructure integration (CameraX, ImageProcessor, PdfUtils) and viewer compatibility (PdfRenderer, Coil, share flow).

### Phase 4: Biometric App Lock

**Rationale:** Depends on Phase 2 (SecurePreferences must exist to store lock-enabled state, session timeout, and last-background timestamp). The biometric implementation is the most complex UI feature in the milestone -- API-level branching across three distinct behavioral tiers, lifecycle-aware session management via ProcessLifecycleOwner, and UX design critical to adoption. Must be opt-in via Settings toggle; a forced app lock drives users to uninstall according to PITFALLS.md Pitfall 10.

**Delivers:** BiometricHelper with API-level-aware authenticator configuration (three code paths); HomeFragment biometric gate triggered only when user has opted in; SettingsFragment security section (app lock toggle, session timeout selector with options: immediate / 30s / 1min / 5min); ProcessLifecycleOwner-based auto-lock on background (SEC-13); all lock state and timeout preferences stored in encrypted SharedPreferences (from Phase 2).

**Addresses:** SEC-02 (biometric/PIN app lock), SEC-13 (auto-lock on background); OWASP M3 (fully closed).

**Avoids:** Pitfall 8 (API-level branching: BIOMETRIC_WEAK for API 24-27 + KeyguardManager PIN fallback; BIOMETRIC_STRONG only for API 28-29 + separate PIN button; BIOMETRIC_STRONG or DEVICE_CREDENTIAL for API 30+; test on all three tiers with real devices); Pitfall 10 (opt-in toggle in Settings; 5-minute default timeout; FLAG_SECURE already selectively applied from Phase 1 -- not on settings or home screen); Pitfall 13 (dismiss visible DialogFragments before showing BiometricPrompt to avoid z-ordering issues on API 28-29).

**Research flag:** Needs phase research. The API 28-29 IllegalArgumentException for BIOMETRIC_STRONG or DEVICE_CREDENTIAL, and the KeyguardManager PIN fallback pattern for API 24-27, are documented but have enough edge cases on specific OEM devices that detailed task planning is warranted before implementation.

### Phase 5: Hardening Polish and Security Audit

**Rationale:** Cross-cutting verification and polish pass across all features added in Phases 1-4. Adds remaining low-priority features and performs a full security audit to verify no plaintext leaks survive. The PITFALLS.md "Looks Done But Isn't" checklist becomes the exit criteria for this phase.

**Delivers:** SEC-11 (EXTRA_IS_SENSITIVE on OCR text clipboard copies); SEC-12 (android:accessibilityDataSensitive="auto" on sensitive layout views); tapjacking protection (filterTouchesWhenObscured on share/delete/capture action buttons); "Looks Done But Isn't" checklist verification (migration sentinel key confirmed, KeyStore fallback confirmed, R8 keep rules confirmed, backup exclusions confirmed, auth/encryption key separation confirmed, FLAG_SECURE conditional on DEBUG confirmed, no RandomAccessFile overwrite patterns, Kotlin version stability); release APK tested with all encrypted operations on a physical device.

**Addresses:** SEC-11, SEC-12; OWASP M6 (fully closed); M7 (partial improvement via debuggable check and existing R8).

**Avoids:** All remaining pitfalls via the checklist-driven audit. Pitfall 6 (release APK encryption operations verified on physical device). Pitfall 14 (SecureDocumentStore interface abstraction for unit testing verified to be in place).

**Research flag:** Standard patterns -- skip phase research. This is verification and low-complexity feature additions; no new patterns need to be researched.

### Phase Ordering Rationale

- **Phase 1 before Phases 2-4:** Quick wins are independent of the encryption foundation. Shipping them first delivers visible security improvement while complex work is planned, and avoids interleaving low-risk and high-risk changes in the same plan.
- **Phase 2 strictly before Phases 3 and 4:** SecurityManager and SecureFileManager are prerequisites for file encryption (Phase 3). SecurePreferences is a prerequisite for biometric state storage (Phase 4). There is no valid reordering of these dependencies.
- **Phase 3 before Phase 4:** File encryption before biometric lock is preferred because the encryption key architecture (established in Phase 2, exercised in Phase 3) must be validated before biometric key interactions are added. These phases are largely independent and could be parallelized by two developers working simultaneously.
- **Phase 5 last:** Verification and polish can only happen after all security features are implemented. The "Looks Done But Isn't" checklist spans all prior phases.

### Research Flags

Phases needing `/gsd:research-phase` during planning:
- **Phase 2 (Encrypted Storage Foundation):** Migration safety patterns, KeyStore fallback architecture, and the auth-key/encryption-key separation decision are complex enough that pre-implementation research is warranted. R8 keep-rule configuration has documented gotchas.
- **Phase 3 (File Encryption at Rest):** CameraX post-capture encrypt-in-place, custom Coil Fetcher, PdfRenderer decrypt-to-temp lifecycle, and CanHub cropper integration each have non-obvious implementation details. Device benchmark data is needed for Tink chunk size decisions.
- **Phase 4 (Biometric App Lock):** Three-tier API-level branching for BiometricPrompt authenticators has documented edge cases on API 28-29 that need careful implementation planning.

Phases with standard patterns (skip research-phase):
- **Phase 1 (Security Foundation and Quick Wins):** FLAG_SECURE, Timber, network security config, component audit -- all well-documented Android APIs with established patterns.
- **Phase 5 (Hardening Polish and Security Audit):** Checklist-driven verification and low-complexity feature additions (clipboard, accessibility) do not require upfront research.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All library versions verified via official docs and Maven Central (March 2026). Compatibility matrix verified. Tink 1.20.0 no-ProGuard claim verified via official setup docs. Security-crypto deprecation status and stable-release behavior confirmed via AndroidX releases page. |
| Features | HIGH | OWASP Mobile Top 10 2024 mapping verified against official OWASP documentation. Feature set derived from direct codebase inspection (confirmed 49 Log calls grep-verified, zero FLAG_SECURE, zero encryption, explicit exported component inventory). |
| Architecture | HIGH | All pattern choices (SecureFileManager facade, OnDestinationChangedListener for FLAG_SECURE, fragment-hosted BiometricPrompt, decrypt-to-temp for PdfRenderer) validated against official Android documentation. PdfRenderer FileDescriptor constraint is a documented platform limitation. Coil custom Fetcher pattern is a supported extension point. |
| Pitfalls | HIGH (critical) / MEDIUM (moderate) | Critical pitfalls sourced from official Google issue trackers (Tink GitHub #535, #361; tink-java GitHub #7; Google Issue Tracker #176215143, #370009394; Android developer docs for KeyPermanentlyInvalidatedException). Flash storage secure-deletion ineffectiveness is well-established computer science. Biometric API-level behavior sourced from official Android docs. APK size estimates and per-device encryption throughput are LOW confidence (approximate). |

**Overall confidence:** HIGH

### Gaps to Address

- **API 24-27 KeyStore instability on real OEM hardware:** The failure modes on Huawei, Honor, and OPPO devices at API 24-27 are documented in official bug trackers but the exact device/firmware combinations that trigger them cannot be fully validated without physical hardware. The fallback strategy is designed conservatively; it must be validated on real API 24-26 devices during Phase 2 execution before the migration is shipped to users.
- **Tink StreamingAead throughput on API 24-27 ARMv7 devices:** The 4KB chunk size recommendation is based on general guidance and published benchmarks from KINTO Tech Blog. App-specific latency for the actual file sizes produced by this scanner (JPEG scans, multi-page PDFs) needs measurement during Phase 3 to confirm whether a "Securing document..." progress step is needed and what chunk size is optimal.
- **EncryptedSharedPreferences first-open latency on low-end devices:** MasterKey generation time needs measurement during Phase 2 to determine whether it can safely execute on the main thread or must move to a coroutine. If it exceeds 16ms it will cause a dropped frame.
- **Biometric behavior on API 29 OEM devices:** The IllegalArgumentException for `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` on API 29 is documented in official Android docs, but the KeyguardManager PIN fallback behavior as a substitute on specific OEM devices needs real-device confirmation during Phase 4.
- **Coil Fetcher vs. decrypt-to-temp tradeoff for thumbnails:** Both approaches are architecturally sound. The custom Fetcher is cleaner but requires more implementation effort. The decrypt-to-temp approach is simpler but creates transient plaintext on disk. This decision should be made during Phase 3 research with consideration for the specific thumbnail sizes and caching behavior of the existing Coil integration.

---

## Sources

### Primary (HIGH confidence)
- [AndroidX Security Releases](https://developer.android.com/jetpack/androidx/releases/security) -- security-crypto 1.1.0 stable released July 30, 2025; deprecation confirmed
- [Tink Java Setup Guide](https://developers.google.com/tink/setup/java) -- tink-android 1.20.0, API 24+ support, no ProGuard needed
- [Tink Java GitHub Releases](https://github.com/tink-crypto/tink-java/releases) -- v1.20.0 released December 10, 2024
- [Tink issue #535](https://github.com/google/tink/issues/535) -- EncryptedSharedPreferences KeyStore instability on API 24-27
- [Tink issue #361](https://github.com/google/tink/issues/361) -- R8 strips protobuf fields from AesGcmKeyFormat
- [tink-java issue #7](https://github.com/tink-crypto/tink-java/issues/7) -- R8 missing classes
- [Google Issue Tracker #176215143](https://issuetracker.google.com/issues/176215143) -- EncryptedSharedPreferences crash on KeyStore failure
- [Google Issue Tracker #370009394](https://issuetracker.google.com/issues/370009394) -- Keystore crash on EncryptedSharedPreferences creation
- Android Developers: FLAG_SECURE, Network Security Config, Secure Clipboard, Log Info Disclosure, BiometricPrompt, Deep Link Security, Cryptography, Android Keystore, Auto Backup, KeyPermanentlyInvalidatedException, Tapjacking Prevention
- [OWASP Mobile Top 10 2024](https://owasp.org/www-project-mobile-top-10/)
- [OWASP MASVS v2](https://mas.owasp.org/MASVS/)
- [OWASP MASTG Android KeyStore](https://mas.owasp.org/MASTG/knowledge/android/MASVS-STORAGE/MASTG-KNOW-0043/)
- [OWASP dependency-check-gradle Plugin Portal](https://plugins.gradle.org/plugin/org.owasp.dependencycheck) -- v12.2.0
- [Android Developers Blog: R8 Keep Rules (Nov 2025)](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html)
- Direct codebase inspection -- AndroidManifest.xml, all source files; 49 Log calls confirmed via grep; zero FLAG_SECURE, zero encryption confirmed

### Secondary (MEDIUM confidence)
- [Droidcon: Goodbye EncryptedSharedPreferences -- A 2026 Migration Guide](https://www.droidcon.com/2025/12/16/goodbye-encryptedsharedpreferences-a-2026-migration-guide/) -- confirms Tink as replacement path
- [KINTO Tech Blog: EncryptedSharedPreferences to Tink and DataStore](https://blog.kinto-technologies.com/posts/2025-06-16-encrypted-shared-preferences-migration/) -- performance benchmark data
- [ProAndroidDev: Android KeyStore StrongBox vs hardware-backed keys](https://proandroiddev.com/android-keystore-what-is-the-difference-between-strongbox-and-hardware-backed-keys-4c276ea78fd0)
- [Stytch Blog: Android Keystore pitfalls and best practices](https://stytch.com/blog/android-keystore-pitfalls-and-best-practices/)
- [Preventing screenshots on Android (tomasrepcik.dev)](https://tomasrepcik.dev/blog/2023/2023-12-09-android-securing-screen/) -- FLAG_SECURE in single-activity apps
- [Android Medium: BiometricPrompt with CryptoObject](https://medium.com/androiddevelopers/using-biometricprompt-with-cryptoobject-how-and-why-aace500ccdb7)
- [ed-george/encrypted-shared-preferences](https://github.com/ed-george/encrypted-shared-preferences) -- maintained community fork post-deprecation

### Tertiary (LOW confidence, needs validation during implementation)
- APK size estimates (Tink + security-crypto + Timber after R8) -- approximate; actual depends on R8 tree-shaking
- Tink StreamingAead throughput on API 24-27 ARMv7 devices -- inferred from general AES benchmarks, not app-specific measurements
- EncryptedSharedPreferences first-open MasterKey generation latency on low-end devices -- needs measurement

---
*Research completed: 2026-03-03*
*Ready for roadmap: yes*
