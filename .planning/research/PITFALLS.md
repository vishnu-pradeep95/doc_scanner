# Domain Pitfalls

**Domain:** Android Document Scanner App -- Security Hardening (v1.2)
**Researched:** 2026-03-03
**Confidence:** HIGH for EncryptedSharedPreferences/KeyStore/R8 pitfalls (multi-source verified, official bug trackers); MEDIUM for flash storage deletion and Tink streaming performance (limited Android-specific benchmarks); LOW for CI biometric emulation (rapidly evolving tooling)

> **Scope note:** This file covers pitfalls specific to adding security hardening to an existing Android app. The v1.1 PITFALLS.md (testing/release) remains valid for its scope. This file focuses exclusively on risks introduced by the v1.2 Security Hardening milestone: encrypted storage migration, biometric auth, file encryption, FLAG_SECURE, and R8 interactions with security libraries.

---

## Critical Pitfalls

Mistakes that cause data loss, crashes on production devices, or security regressions that defeat the purpose of hardening.

### Pitfall 1: EncryptedSharedPreferences Migration Silently Loses Existing Document History

**What goes wrong:**
The app stores document history as a JSON array in `SharedPreferences` (file: `document_history`, key: `documents`) and user settings in a second file (`pdf_scanner_prefs`). Migrating to `EncryptedSharedPreferences` requires creating a NEW encrypted prefs file -- it cannot encrypt an existing XML file in-place. If the migration code reads from the old file, writes to the new encrypted file, and then the app crashes or is killed mid-migration, the user loses their document history permanently. The old file may have been partially read, the new file may be incomplete, and there is no transactional guarantee.

Worse: `EncryptedSharedPreferences.create()` itself can crash on certain devices (see Pitfall 2), meaning the migration code never completes, but if you already deleted the old file, the data is gone.

The existing `DocumentHistoryRepository` stores `filePath` (absolute paths to PDFs in `filesDir`). These paths remain valid after migration -- only the metadata index is at risk. But losing the index means the user cannot find their documents through the app UI, even though the files still exist on disk.

**Why it happens:**
SharedPreferences does not support atomic migration between files. The Android framework provides no built-in migration utility for SharedPreferences-to-EncryptedSharedPreferences. Most online tutorials show a naive "read old, write new, delete old" pattern without crash safety.

**Consequences:**
- Users who upgrade from v1.1 to v1.2 lose their document history (up to 50 entries).
- User settings (theme, default filter) revert to defaults.
- PDFs still exist on disk but are invisible to the app.

**Prevention:**
1. **Never delete the old prefs file until encrypted write is confirmed.** Use a sentinel key in the encrypted file (e.g., `"migration_complete" = true`) to verify migration succeeded.
2. **Implement idempotent migration:** Check if migration already completed before attempting. If the sentinel key exists in encrypted prefs, skip migration.
3. **Keep the old file as backup** for at least one app version cycle (v1.2 migrates, v1.3 deletes old file).
4. **Migration code pattern:**
   ```kotlin
   fun migrateIfNeeded(context: Context) {
       val encrypted = getEncryptedPrefs(context) // may throw -- handle below
       if (encrypted.getBoolean("migration_complete", false)) return

       val old = context.getSharedPreferences("document_history", Context.MODE_PRIVATE)
       val json = old.getString("documents", null) ?: return

       encrypted.edit()
           .putString("documents", json)
           .putBoolean("migration_complete", true)
           .commit() // commit(), NOT apply() -- must be synchronous
       // DO NOT delete old file yet
   }
   ```
5. **Handle EncryptedSharedPreferences creation failure** (see Pitfall 2) by falling back to unencrypted prefs with a logged warning, rather than crashing.

**Detection:**
- Document history is empty after app update.
- `SharedPreferences` XML file exists but has no corresponding encrypted counterpart.
- Crash reports showing `SecurityException` or `KeyStoreException` during first launch after update.

**Phase to address:** First security phase -- encrypted storage migration must be the earliest task, with rollback safety.

---

### Pitfall 2: EncryptedSharedPreferences Crashes on API 24-27 Devices Due to KeyStore Instability

**What goes wrong:**
`EncryptedSharedPreferences.create()` depends on Android KeyStore to generate and store a master key (`_androidx_security_master_key_`). On API 24-27 devices (particularly Huawei, Honor, OPPO, and some Samsung models), the KeyStore implementation has known bugs where:
- The master key exists but becomes "unusable" after device restart, lock screen change, or OEM firmware update.
- `java.security.KeyStoreException: the master key android-keystore://_androidx_security_master_key_ exists but is unusable` crashes the app on startup.
- `javax.crypto.AEADBadTagException` occurs when the keyset stored in the prefs file was encrypted with a key that the KeyStore can no longer reproduce.

**This is documented in Tink issue #535** with 57% of crashes on Android 7 (API 24-25) and 26% on Android 6. Once triggered, the crash is persistent -- every subsequent app launch crashes at the same point.

**Why it happens:**
API 24-27 devices use software-backed or TEE-backed KeyStore implementations that vary wildly by OEM. There is no StrongBox (introduced in API 28). Some OEMs have buggy Keymaster HAL implementations that corrupt keys after device state changes. The `EncryptedSharedPreferences` library does not gracefully handle this -- catching the exception leaves the instance in an unusable `null` state.

**Consequences:**
- App becomes permanently unusable on affected devices (crash loop).
- No recovery path without clearing app data (losing all documents).
- The app's minSdk is 24, so these devices are in the support range.

**Prevention:**
1. **Wrap EncryptedSharedPreferences creation in try-catch with fallback:**
   ```kotlin
   fun getSecurePrefs(context: Context): SharedPreferences {
       return try {
           val masterKey = MasterKey.Builder(context)
               .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
               .build()
           EncryptedSharedPreferences.create(
               context,
               "secure_document_history",
               masterKey,
               EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
               EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
           )
       } catch (e: Exception) {
           // Log to analytics -- track how many devices hit this
           Log.e("Security", "EncryptedSharedPreferences failed, falling back", e)
           // Delete corrupted encrypted prefs and re-create
           context.deleteSharedPreferences("secure_document_history")
           try {
               // Retry once after clearing corrupted state
               val masterKey = MasterKey.Builder(context)
                   .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                   .build()
               EncryptedSharedPreferences.create(/* same params */)
           } catch (e2: Exception) {
               // Permanent fallback to unencrypted prefs
               context.getSharedPreferences("document_history", Context.MODE_PRIVATE)
           }
       }
   }
   ```
2. **Consider Tink + DataStore instead of EncryptedSharedPreferences.** EncryptedSharedPreferences is deprecated since `security-crypto:1.1.0-alpha07` (April 2025). The modern path is DataStore + Tink's `StreamingAead`, which manages its own keys without depending on the fragile MasterKey/KeyStore interaction. However, DataStore requires coroutines integration, which adds complexity.
3. **If using EncryptedSharedPreferences via the community fork** (`ed-george/encrypted-shared-preferences`), pin to a specific version and monitor the repo for KeyStore-related fixes.
4. **Test on real API 24-26 devices** (not just emulator) -- emulators use a software KeyStore that rarely exhibits these bugs.

**Detection:**
- Crash reports with `KeyStoreException` or `AEADBadTagException` concentrated on API 24-27.
- Crash occurs on app cold start, not during any specific user action.
- Affected devices are disproportionately Huawei, Honor, OPPO.

**Phase to address:** Encrypted storage phase -- must be addressed in architecture before writing migration code. The fallback strategy is a prerequisite.

---

### Pitfall 3: R8 Strips Tink/Security-Crypto Classes in Release Builds, Causing Crypto Crashes

**What goes wrong:**
The app already has R8 enabled (`isMinifyEnabled = true`) with keep rules for ML Kit, GMS, CameraX, and SafeArgs. Adding `security-crypto` or Tink introduces new classes that use reflection and protobuf internally. R8 strips:
- Tink's protobuf-generated message classes (e.g., `AesGcmKeyFormat.version_` field removed -- Tink issue #361).
- Error-prone annotations referenced by Tink (`com.google.errorprone.annotations.CanIgnoreReturnValue` -- tink-java issue #7).
- Security-crypto's `MasterKey` internal factory classes accessed via reflection.

The app works perfectly in debug (no R8), then crashes on the first encrypted operation in release.

**Why it happens:**
Tink 1.4.0+ and tink-java 1.9.0+ include bundled consumer ProGuard rules via `META-INF/proguard/`. However, older versions and some transitive dependency configurations do not propagate these rules correctly. The `security-crypto` library pulls a specific Tink version that may or may not include complete rules. Additionally, if the project uses `proguard-android-optimize.txt` (which this project does), aggressive optimization can override the consumer rules.

**Consequences:**
- `java.lang.NoSuchFieldError: No field version_ in AesGcmKeyFormat` at runtime in release builds.
- `ClassNotFoundException` for error-prone annotations (non-fatal but triggers R8 warnings that may mask real issues).
- Encrypted data written in debug cannot be read in release (different class structure after obfuscation).

**Prevention:**
1. **Add explicit keep rules for Tink and security-crypto:**
   ```proguard
   # Tink cryptographic library -- protobuf reflection
   -keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite {
     <fields>;
   }
   -keep class com.google.crypto.tink.** { *; }
   -dontwarn com.google.errorprone.annotations.**

   # AndroidX Security Crypto
   -keep class androidx.security.crypto.** { *; }
   ```
2. **Verify by running `./gradlew assembleRelease` and testing every encrypted operation** on a physical device.
3. **Use R8's `-printconfiguration` to dump merged rules** and verify Tink's consumer rules are included:
   ```proguard
   -printconfiguration /tmp/full-r8-config.txt
   ```
4. **Pin Tink version explicitly** in dependency resolution to avoid transitive version drift:
   ```kotlin
   configurations.all {
       resolutionStrategy.force("com.google.crypto.tink:tink-android:1.12.0")
   }
   ```

**Detection:**
- Release APK crashes on first encrypted read/write operation.
- `adb logcat | grep -E "(NoSuchField|ClassNotFound|tink|crypto)"` shows stripping errors.
- Debug build works, release build does not (the classic R8 symptom).

**Phase to address:** Encrypted storage phase AND release verification phase. Add keep rules when adding the dependency; verify in release build before moving on.

---

### Pitfall 4: Encryption Key Permanently Invalidated When User Changes Biometric or Lock Screen

**What goes wrong:**
If encryption keys are created with `setUserAuthenticationRequired(true)` and tied to biometric authentication, the Android KeyStore automatically and irreversibly invalidates the key when:
- A new fingerprint is enrolled (any API level with biometrics).
- All biometrics are removed (API 24+).
- The secure lock screen is disabled or reset (API 24+).
- A new face is enrolled (API 29+ with face unlock).

This throws `KeyPermanentlyInvalidatedException` on the next crypto operation. If encrypted files or preferences were protected by this key, they become permanently unreadable. For a document scanner storing encrypted PDFs, this means **all scanned documents become inaccessible** when the user adds a new fingerprint.

**Why it happens:**
This is a deliberate Android security feature -- if biometrics change, old keys should not be trusted because a new (potentially unauthorized) biometric was added. The `setInvalidatedByBiometricEnrollment(true)` default ensures keys cannot survive biometric enrollment changes.

**Consequences:**
- User adds a new fingerprint and all encrypted documents become permanently inaccessible.
- No recovery path -- the key is gone from the KeyStore.
- User perceives the app as having "deleted their documents."

**Prevention:**
1. **Separate authentication keys from encryption keys.** Use biometric auth as a gate (UI-level lock) but encrypt files with a key that does NOT require user authentication:
   ```kotlin
   // Authentication key -- for BiometricPrompt gate
   val authKeySpec = KeyGenParameterSpec.Builder("auth_key", PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
       .setUserAuthenticationRequired(true)
       .setInvalidatedByBiometricEnrollment(true) // OK -- we re-create this key
       .build()

   // Encryption key -- for file/prefs encryption
   val encKeySpec = KeyGenParameterSpec.Builder("enc_key", PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
       .setUserAuthenticationRequired(false) // NOT tied to biometrics
       .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
       .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
       .build()
   ```
2. **Handle `KeyPermanentlyInvalidatedException` gracefully** -- if the auth key is invalidated, delete it, regenerate, and re-prompt for biometric enrollment. Never let this exception reach an unhandled state.
3. **`setInvalidatedByBiometricEnrollment(false)`** is available on API 24+, but reduces security. Use it only if the key is NOT protecting sensitive data directly (e.g., a session token, not the document encryption key).
4. **Test this scenario explicitly:** Enroll a new fingerprint while the app has encrypted data, then reopen the app. This must be part of manual QA.

**Detection:**
- `KeyPermanentlyInvalidatedException` in crash logs after user changes biometric settings.
- Users report "all my documents are gone" after adding a new fingerprint.
- QA never tests the "add fingerprint mid-usage" scenario.

**Phase to address:** Biometric authentication phase AND file encryption phase -- architecture decision must be made before implementing either feature.

---

### Pitfall 5: SharedPreferences Backed Up to Google Cloud Despite Backup Exclusion Rules

**What goes wrong:**
The app's `data_extraction_rules.xml` and `backup_rules.xml` exclude `scans/`, `processed/`, and `pdfs/` from backup (domain: `file`). But SharedPreferences files (`document_history.xml`, `pdf_scanner_prefs.xml`) are in the `sharedpref` domain, which is NOT excluded. This means:
- Document history (names, file paths, timestamps) is backed up to Google Drive.
- User preferences are backed up.
- If the user switches devices, backed-up SharedPreferences contain absolute file paths that point to non-existent files on the new device.

For a high-sensitivity app handling IDs and medical documents, **document metadata leaking to cloud backup is a security issue** even if the documents themselves are excluded.

After encrypting SharedPreferences, backing up the encrypted file to Google Drive creates a new problem: the encryption key is device-specific (in the Android KeyStore), so the backed-up encrypted file is unreadable on any other device. On restore, the app tries to decrypt with a non-existent key and crashes.

**Why it happens:**
The v1.1 backup exclusion rules were designed to prevent document files from being backed up, but nobody considered that the SharedPreferences metadata index also contains sensitive information. The `sharedpref` domain requires its own explicit exclusion rules.

**Consequences:**
- Document metadata (names like "Medical_Report_2026.pdf", "Passport_Scan.pdf") backed up to Google cloud in plaintext.
- After encrypting prefs: restore from backup causes crash (encrypted file, no key).
- Device transfer includes stale metadata pointing to non-existent files.

**Prevention:**
1. **Add SharedPreferences to backup exclusions:**
   ```xml
   <!-- data_extraction_rules.xml (API 31+) -->
   <data-extraction-rules>
       <cloud-backup>
           <exclude domain="file" path="scans/" />
           <exclude domain="file" path="processed/" />
           <exclude domain="file" path="pdfs/" />
           <exclude domain="sharedpref" path="document_history.xml" />
           <exclude domain="sharedpref" path="pdf_scanner_prefs.xml" />
           <exclude domain="sharedpref" path="secure_document_history.xml" />
       </cloud-backup>
       <device-transfer>
           <!-- same exclusions -->
       </device-transfer>
   </data-extraction-rules>
   ```
   And the same pattern for `backup_rules.xml` (pre-API 31).
2. **Consider `android:allowBackup="false"`** for a high-sensitivity app. This is the nuclear option but eliminates the entire class of backup-related data leakage.
3. **Test backup behavior** with `adb shell bmgr backupnow <package>` and `adb shell bmgr restore <package>` to verify exclusions work.

**Detection:**
- `adb shell bmgr list transports` and manual backup/restore shows SharedPreferences files in the backup set.
- After factory reset + restore, app crashes trying to decrypt restored encrypted prefs.
- Document names visible in Google Drive backup data.

**Phase to address:** Encrypted storage phase -- update backup rules in the same commit that introduces encrypted SharedPreferences.

---

## Moderate Pitfalls

### Pitfall 6: FLAG_SECURE Breaks Screenshot Tests and Accessibility Services

**What goes wrong:**
Adding `window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)` to prevent screenshots of sensitive documents also:
- Blocks Espresso screenshot capture on test failure (all screenshots are black).
- Breaks Roborazzi/Paparazzi screenshot regression tests (if added in v2+).
- Disables screen recording for bug reports.
- Blocks accessibility services that rely on screen content reading (some screen readers).
- Prevents the app from appearing in the recent apps thumbnail (shows blank).

**Why it happens:**
FLAG_SECURE is a blunt instrument -- it tells the window compositor to never include this window in screen captures, period. There is no "except for testing" or "except for accessibility" override at the OS level.

**Prevention:**
1. **Apply FLAG_SECURE conditionally using a build-type flag:**
   ```kotlin
   // In Activity or Fragment
   if (!BuildConfig.DEBUG) {
       window.setFlags(FLAG_SECURE, FLAG_SECURE)
   }
   ```
   This allows screenshots in debug builds (for testing) while blocking them in release.
2. **Apply FLAG_SECURE only to sensitive screens**, not globally. The document viewer and camera preview need it; the settings screen and home screen do not.
3. **Clear FLAG_SECURE when navigating away from sensitive screens:**
   ```kotlin
   override fun onResume() {
       super.onResume()
       if (isShowingSensitiveContent) {
           requireActivity().window.addFlags(FLAG_SECURE)
       }
   }
   override fun onPause() {
       super.onPause()
       requireActivity().window.clearFlags(FLAG_SECURE)
   }
   ```
4. **Document the impact on accessibility** in release notes. Some assistive technologies may not work on secured screens.

**Detection:**
- Espresso test screenshots are entirely black.
- Recent apps shows a blank/white thumbnail for the app.
- QA cannot record screen for bug reports in release builds.

**Phase to address:** UI security phase. Must be coordinated with test infrastructure.

---

### Pitfall 7: File Encryption Performance Degrades Noticeably on Large Multi-Page PDFs

**What goes wrong:**
Encrypting files with `EncryptedFile` (Tink `StreamingAead` with `AES256_GCM_HKDF_4KB`) processes data in 4KB chunks. For a 50-page scanned PDF at 200 DPI, file sizes can reach 20-50 MB. On API 24-27 devices without hardware AES acceleration (older ARMv7 devices still in the minSdk 24 range), encryption adds measurable latency:
- TEE-backed KeyStore: ~7ms per MB (manageable).
- Software-only fallback: ~2-5ms per MB (acceptable).
- StrongBox (API 28+): ~209ms per 4MB chunk (unacceptable for large files -- would take 2.5+ seconds for a 50MB PDF).

The real-world problem is not encryption speed but the interaction with the existing code flow: `PdfUtils` generates PDFs synchronously, and if encryption is added to the save path, the UI freezes during PDF generation + encryption.

**Why it happens:**
The existing PDF generation in `PdfUtils.kt` already runs on a coroutine (`Dispatchers.IO`), but if encryption is added as a blocking operation within the same coroutine, the progress indicator (which shows "Page X of Y") cannot update during the encryption phase. Users see the progress complete, then the UI hangs for an additional 1-3 seconds while encryption finishes.

**Consequences:**
- Users perceive the app as frozen after PDF generation "completes."
- On low-end API 24 devices, large document encryption can trigger ANR if run on the main thread.
- StrongBox encryption (API 28+) is too slow for files; must use software or TEE keys.

**Prevention:**
1. **Never use StrongBox-backed keys for file encryption.** StrongBox is for small operations (key wrapping, authentication tokens). File encryption should use software-backed or TEE-backed keys:
   ```kotlin
   val keySpec = KeyGenParameterSpec.Builder("file_enc_key", ...)
       .setIsStrongBoxBacked(false) // Explicitly opt out of StrongBox
       .build()
   ```
2. **Use Tink's `StreamingAead` directly** (not via `EncryptedFile`) for better control over chunk processing and progress reporting.
3. **Update the progress indicator** to include an "Encrypting..." phase after PDF generation:
   ```kotlin
   // In ViewModel
   _progress.postValue("Generating PDF...")
   val pdfFile = PdfUtils.generatePdf(pages)
   _progress.postValue("Securing document...")
   encryptFile(pdfFile)
   _progress.postValue("Complete")
   ```
4. **Benchmark on target devices** before committing to a chunk size. The 4KB default is fine for most cases but can be tuned.

**Detection:**
- UI hangs for 1-3 seconds after progress bar reaches 100%.
- ANR reports on low-end devices during PDF save.
- Users on API 28+ devices with StrongBox report extremely slow saves (StrongBox accidentally used).

**Phase to address:** File encryption phase. Benchmark must happen before committing to an encryption approach.

---

### Pitfall 8: Biometric API Differences Across API 24-34 Cause Silent Failures or Crashes

**What goes wrong:**
The `androidx.biometric` library abstracts differences across API levels, but significant behavioral gaps remain:
- **API 24-27:** No `BiometricPrompt` at all. The library falls back to `FingerprintManager` (API 23+), but only fingerprint is supported -- no face or iris. `DEVICE_CREDENTIAL` fallback (PIN/pattern) is NOT available.
- **API 28:** `BiometricPrompt` introduced, but only supports `BIOMETRIC_STRONG`. `DEVICE_CREDENTIAL` alone is NOT supported.
- **API 29:** `BiometricManager` introduced for checking enrollment. `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` combination is NOT supported (throws `IllegalArgumentException`). Some emulators show cancellation errors when falling back to PIN.
- **API 30+:** All authenticator combinations work. `DEVICE_CREDENTIAL` alone is supported. This is the "golden path."

If the app uses `setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)` as a one-size-fits-all configuration, it crashes on API 28-29 and silently fails on API 24-27.

**Why it happens:**
The `androidx.biometric` library handles the dispatch to underlying APIs, but the API surface itself differs. The library throws `IllegalArgumentException` for unsupported combinations rather than silently degrading.

**Consequences:**
- Crash on API 28-29 devices when using certain authenticator combinations.
- Users on API 24-27 with no fingerprint sensor have no way to authenticate (no device credential fallback).
- Testing only on API 30+ emulator misses all these edge cases.

**Prevention:**
1. **Check API level and adjust authenticator type:**
   ```kotlin
   val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
       BiometricManager.Authenticators.BIOMETRIC_STRONG or
           BiometricManager.Authenticators.DEVICE_CREDENTIAL
   } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
       BiometricManager.Authenticators.BIOMETRIC_STRONG
       // On API 28-29, offer separate "Use PIN" button that launches KeyguardManager
   } else {
       BiometricManager.Authenticators.BIOMETRIC_WEAK
       // API 24-27: fingerprint only, with separate PIN fallback
   }
   ```
2. **Use `BiometricManager.canAuthenticate(authenticators)`** to check availability before showing the prompt. Handle `BIOMETRIC_ERROR_NO_HARDWARE`, `BIOMETRIC_ERROR_NONE_ENROLLED`, and `BIOMETRIC_ERROR_HW_UNAVAILABLE` distinctly.
3. **For API 24-27 PIN fallback**, use `KeyguardManager.createConfirmDeviceCredentialIntent()` (deprecated in API 29 but functional on 24-28):
   ```kotlin
   if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
       val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
       val intent = km.createConfirmDeviceCredentialIntent("Verify identity", "")
       if (intent != null) startActivityForResult(intent, REQUEST_CODE_KEYGUARD)
   }
   ```
4. **Test on at least three API levels:** API 26 (pre-BiometricPrompt), API 29 (partial support), and API 33+ (full support).

**Detection:**
- `IllegalArgumentException` crash on API 28-29 with `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`.
- Users on API 24-27 report they cannot unlock the app (no fingerprint, no PIN fallback).
- BiometricPrompt callback receives `ERROR_CANCELED` on API 29 emulators.

**Phase to address:** Biometric authentication phase. API-level branching must be designed before implementation.

---

### Pitfall 9: "Secure Deletion" of Temp Files Does Not Work on Flash Storage

**What goes wrong:**
The app already cleans temp files on startup (1hr threshold, implemented in v1.0 BUG-03). A natural security hardening step is to add "secure deletion" by overwriting files with zeros before deleting:
```kotlin
// DOES NOT WORK on flash storage
fun secureDelete(file: File) {
    val length = file.length()
    RandomAccessFile(file, "rw").use { raf ->
        raf.seek(0)
        raf.write(ByteArray(length.toInt())) // overwrite with zeros
    }
    file.delete()
}
```
On Android's flash storage (eMMC/UFS), the Flash Translation Layer (FTL) implements wear leveling, which redirects writes to new physical blocks. The overwrite writes zeros to a NEW physical location while the original data remains in the OLD location until garbage collection reclaims it. The "secure delete" provides zero additional security on flash storage.

**Why it happens:**
Flash storage fundamentally cannot support in-place overwrite. The FTL abstraction makes it look like a sequential-access device, but physically, the data is written elsewhere. This is true for all Android devices -- they all use flash storage (eMMC, UFS, or NVMe).

**Consequences:**
- False sense of security -- developers believe temp files are securely erased.
- Wasted CPU cycles and I/O bandwidth on useless overwrite operations.
- Slower temp file cleanup (writing zeros to large scan images adds seconds).
- Potential flash wear from unnecessary write cycles.

**Prevention:**
1. **Do not implement overwrite-based secure deletion.** It wastes resources and provides no security benefit on flash.
2. **Instead, encrypt files at rest.** If temp files are encrypted, deletion removes the ability to read them because the key is in the KeyStore (not on flash). This is "cryptographic erasure" -- the data on flash is meaningless without the key.
3. **If regulatory compliance requires "secure deletion," document** that Android's file-based encryption (FBE, enabled by default since API 29) encrypts all app data at the filesystem level, and deleting a file removes its FBE key association.
4. **Continue the existing cleanup pattern** (delete on startup, 1hr threshold) but do not add overwrite logic.

**Detection:**
- Code review shows `RandomAccessFile` overwrite patterns in security-related code.
- Performance regression during temp file cleanup (I/O spike on startup).
- Security audit falsely passes because "secure deletion is implemented."

**Phase to address:** File encryption phase. Decide on "encrypt then delete" vs. "overwrite then delete" early -- the correct answer is always encrypt-then-delete on flash.

---

### Pitfall 10: Over-Securing Causes UX Friction That Makes the App Unusable

**What goes wrong:**
Adding every security feature simultaneously -- biometric prompt on every app open, FLAG_SECURE on all screens, re-authentication on every document view, encrypted file names that obscure document titles, mandatory timeout after 30 seconds -- turns a "delightful" scanner app into a miserable experience. Users who scan grocery receipts and school notes alongside occasional IDs do not want banking-app friction for every interaction.

The project description says "treat like banking app," but banking apps handle money -- documents have varying sensitivity levels. A one-size-fits-all approach drives users to uninstall.

**Why it happens:**
Security engineers think in terms of threat models; users think in terms of "can I scan this document quickly." When security is added without considering the user journey, every security control becomes a speed bump. The cumulative effect of 5 small friction points (unlock, decrypt, view, re-authenticate, re-encrypt on close) is a workflow that takes 15 seconds instead of 2.

**Consequences:**
- Users uninstall the app because it is "too annoying."
- Users disable biometric authentication entirely (reducing security below baseline).
- Negative Play Store reviews about "too many prompts."
- Development team spends time building features that actively harm adoption.

**Prevention:**
1. **Make authentication opt-in, not mandatory.** Add a "Require unlock to open app" toggle in Settings. Default to OFF. Users handling sensitive documents can enable it.
2. **Use reasonable session timeouts.** After biometric unlock, keep the session valid for 5 minutes (not 30 seconds). Re-authenticate only after the app has been in background for the timeout period.
3. **Apply FLAG_SECURE selectively** -- only on the document viewer and PDF preview, not on the home screen, settings, or camera preview.
4. **Encrypt files by default** (transparent to user) but make biometric lock optional. Encryption protects against device theft without adding UX friction.
5. **Test the full workflow** with a real user who is not a developer. Time the "scan a document and view it" flow with all security features enabled. If it takes more than 2x the unsecured flow, reduce friction.

**Detection:**
- The "scan and view" workflow takes more than 2x longer with security enabled.
- Users disable security features within the first week.
- A/B testing shows decreased engagement after security update.
- Play Store reviews mention "too many popups" or "too slow."

**Phase to address:** Biometric authentication phase AND integration/polish phase. Security features should be designed with opt-in UX from the start.

---

## Minor Pitfalls

### Pitfall 11: EncryptedSharedPreferences Blocks the Main Thread

**What goes wrong:**
`EncryptedSharedPreferences` performs encryption/decryption synchronously during `getString()`, `putString()`, etc. The existing `DocumentHistoryRepository.getAllDocuments()` is called from the main thread in some fragments (e.g., `HomeFragment` populating the recent documents list). After migration to encrypted prefs, this call adds 10-50ms of encryption overhead per read, potentially causing StrictMode violations and dropped frames on low-end devices.

**Prevention:**
Move all SharedPreferences reads to `Dispatchers.IO` via coroutines. The existing `DocumentHistoryRepository` already has a `getInstance(context)` pattern -- add `suspend` to read methods and call from `viewModelScope.launch(Dispatchers.IO)`. Alternatively, migrate to DataStore (which is async by design).

**Phase to address:** Encrypted storage phase.

---

### Pitfall 12: Security Library Version Conflicts with Kotlin 1.9.21

**What goes wrong:**
The project pins Kotlin to 1.9.21 with forced resolution. The `security-crypto` library (and its Tink dependency) may transitively pull in Kotlin 2.x stdlib or coroutines 1.8+ libraries. The existing `resolutionStrategy.force()` block handles coroutines and stdlib, but Tink may introduce new transitive dependencies (e.g., `protobuf-kotlin`, `tink-android`) that expect Kotlin 2.x binary compatibility.

**Prevention:**
1. After adding any security dependency, run `./gradlew dependencies --configuration releaseRuntimeClasspath | grep kotlin` and verify all Kotlin artifacts are 1.9.x.
2. Add any new Kotlin-based transitive dependencies to the `force()` block.
3. Prefer `security-crypto:1.0.0` (stable, Kotlin 1.x compatible) over `1.1.0-alpha07` if using EncryptedSharedPreferences.

**Phase to address:** Dependency setup phase -- first task.

---

### Pitfall 13: Biometric Prompt Appears Behind Dialog Fragments

**What goes wrong:**
If a `DialogFragment` is showing when `BiometricPrompt.authenticate()` is called, the biometric prompt may appear behind the dialog on some devices, making it untouchable. This is a known z-ordering issue on API 28-29 where BiometricPrompt uses a separate window.

**Prevention:**
Dismiss any visible `DialogFragment` before showing `BiometricPrompt`. Use `parentFragmentManager.fragments.filterIsInstance<DialogFragment>().forEach { it.dismiss() }` as a safety check.

**Phase to address:** Biometric authentication phase.

---

### Pitfall 14: Testing Encrypted Storage in CI Without Device Access

**What goes wrong:**
Unit tests with Robolectric cannot test `EncryptedSharedPreferences` or `MasterKey` because they depend on the Android KeyStore system service, which Robolectric does not emulate. Tests that call `EncryptedSharedPreferences.create()` in Robolectric will throw `ProviderException` or return unusable instances.

Similarly, biometric authentication cannot be tested in standard CI emulators without manual fingerprint enrollment configuration.

**Prevention:**
1. **Abstract encrypted storage behind an interface:**
   ```kotlin
   interface SecureDocumentStore {
       fun getAllDocuments(): List<DocumentEntry>
       fun addDocument(entry: DocumentEntry)
   }
   class EncryptedDocumentStore(context: Context) : SecureDocumentStore { /* real impl */ }
   class FakeDocumentStore : SecureDocumentStore { /* test impl using plain HashMap */ }
   ```
2. Unit tests inject `FakeDocumentStore`. Integration tests on real devices/emulators test `EncryptedDocumentStore`.
3. For biometric testing in CI: use `adb -e emu finger touch 1` to simulate fingerprint on emulator, but note this only works with API 24+ emulators that have a fingerprint sensor configured.
4. **Do not mock the KeyStore itself** -- the behavior is too complex and OEM-specific to mock correctly. Test the integration on real devices as part of release verification.

**Phase to address:** Encrypted storage phase (interface design) and test phase (fake implementation).

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Severity | Mitigation |
|-------------|---------------|----------|------------|
| Encrypted SharedPreferences migration | Data loss during migration (Pitfall 1) | CRITICAL | Idempotent migration with sentinel key; keep old file as backup |
| Encrypted SharedPreferences on API 24-27 | KeyStore crash loop (Pitfall 2) | CRITICAL | Try-catch with fallback to unencrypted prefs; test on real devices |
| Adding security-crypto/Tink dependency | R8 strips crypto classes (Pitfall 3) | CRITICAL | Add keep rules immediately when adding dependency; verify in release build |
| Biometric-tied encryption keys | Key invalidated on fingerprint change (Pitfall 4) | CRITICAL | Separate auth keys from encryption keys; handle KeyPermanentlyInvalidatedException |
| Backup rules for encrypted prefs | Metadata leaks to cloud backup (Pitfall 5) | CRITICAL | Add sharedpref domain exclusions to backup rules |
| FLAG_SECURE implementation | Breaks screenshot tests (Pitfall 6) | MODERATE | Conditional on BuildConfig.DEBUG; apply per-screen, not globally |
| File encryption for PDFs | Performance degradation on large files (Pitfall 7) | MODERATE | Avoid StrongBox for files; add "Encrypting..." progress phase |
| Biometric prompt API branching | Crashes on API 28-29 (Pitfall 8) | MODERATE | API-level branching for authenticator types; test on 3+ API levels |
| Temp file secure deletion | False security from overwrite (Pitfall 9) | MODERATE | Use encrypt-then-delete; do not implement overwrite patterns |
| UX friction from security features | Users disable or uninstall (Pitfall 10) | MODERATE | Opt-in biometric lock; reasonable timeouts; selective FLAG_SECURE |
| EncryptedSharedPreferences performance | Main thread blocking (Pitfall 11) | MINOR | Move reads to Dispatchers.IO; consider DataStore migration |
| Kotlin 1.9.21 version conflicts | Build failure from transitive Kotlin 2.x | MINOR | Verify dependency tree after adding security libs; extend force() block |
| BiometricPrompt z-ordering | Prompt hidden behind dialogs (Pitfall 13) | MINOR | Dismiss dialogs before showing biometric prompt |
| CI testing without KeyStore | Cannot test encrypted code in Robolectric (Pitfall 14) | MINOR | Interface abstraction + fake implementation for unit tests |

---

## "Looks Done But Isn't" Checklist

- [ ] **Migration safety:** Encrypted prefs migration has a sentinel key and does NOT delete the old prefs file in the same release. Verify by: checking for `"migration_complete"` key in code; confirming old file survives migration.
- [ ] **KeyStore fallback:** `EncryptedSharedPreferences.create()` is wrapped in try-catch with fallback to unencrypted prefs. Verify by: searching for bare `EncryptedSharedPreferences.create()` calls without exception handling.
- [ ] **R8 keep rules for Tink:** `proguard-rules.pro` contains keep rules for `com.google.crypto.tink.**` and Tink's shaded protobuf. Verify by: `grep "tink" app/proguard-rules.pro`.
- [ ] **Backup exclusions updated:** `data_extraction_rules.xml` and `backup_rules.xml` exclude `sharedpref` domain for all prefs files. Verify by: reading both XML files for `domain="sharedpref"` entries.
- [ ] **Auth key vs. encryption key separation:** Biometric-tied keys are NOT used for file/prefs encryption. Verify by: checking `KeyGenParameterSpec` for `setUserAuthenticationRequired(false)` on encryption keys.
- [ ] **FLAG_SECURE conditional:** `FLAG_SECURE` is not applied in debug builds. Verify by: `grep FLAG_SECURE` shows `BuildConfig.DEBUG` check.
- [ ] **No overwrite-based secure deletion:** No `RandomAccessFile` overwrite patterns in production code. Verify by: `grep RandomAccessFile` returns no security-related hits.
- [ ] **Biometric API branching:** `setAllowedAuthenticators()` call is API-level-aware, not one-size-fits-all. Verify by: `Build.VERSION.SDK_INT` check before authenticator configuration.
- [ ] **Kotlin version stability:** `./gradlew dependencies` shows no Kotlin 2.x artifacts in runtime classpath. Verify by: checking dependency tree after adding security libraries.
- [ ] **Release APK tested with encryption:** All encrypted operations verified on physical device with release build (not just debug). Verify by: release testing checklist includes encrypted read/write/migration scenarios.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Data loss during migration (Pitfall 1) | HIGH | If old prefs file still exists: re-read and re-migrate. If deleted: scan filesDir for orphaned PDFs and rebuild index from file metadata. |
| KeyStore crash loop (Pitfall 2) | HIGH | Push hotfix that wraps creation in try-catch with fallback. Users on crash loop must clear app data. |
| R8 strips crypto classes (Pitfall 3) | MEDIUM | Add keep rules, rebuild release APK, push update. No data loss -- encrypted data is fine, just need the classes to read it. |
| Key permanently invalidated (Pitfall 4) | CATASTROPHIC | If encryption key is gone, encrypted data is permanently lost. Only prevention works -- there is no recovery from `KeyPermanentlyInvalidatedException` for the data encrypted with that key. |
| Backup leaks metadata (Pitfall 5) | LOW | Update backup rules, push update. Already-backed-up data cannot be recalled from Google Drive. |
| Over-secured UX (Pitfall 10) | LOW | Add Settings toggle to disable biometric lock, reduce timeouts, push update. |

---

## Sources

- [Tink Issue #535: EncryptedSharedPreferences crashes on initialization](https://github.com/google/tink/issues/535) -- 57% on Android 7, 26% on Android 6 (HIGH confidence -- official bug tracker)
- [Tink Issue #361: R8 strips protobuf fields from AesGcmKeyFormat](https://github.com/google/tink/issues/361) (HIGH confidence -- official bug tracker)
- [tink-java Issue #7: Missing classes with R8 full mode](https://github.com/tink-crypto/tink-java/issues/7) (HIGH confidence -- official bug tracker)
- [Google Issue Tracker #176215143: EncryptedSharedPreferences crash on KeyStore failure](https://issuetracker.google.com/issues/176215143) (HIGH confidence -- official tracker)
- [Google Issue Tracker #370009394: Keystore crash while creating EncryptedSharedPreferences](https://issuetracker.google.com/issues/370009394) (HIGH confidence)
- [Android Developers: KeyPermanentlyInvalidatedException](https://developer.android.com/reference/android/security/keystore/KeyPermanentlyInvalidatedException) (HIGH confidence)
- [Android Developers: Biometric authentication dialog](https://developer.android.com/identity/sign-in/biometric-auth) (HIGH confidence)
- [Android Developers: Back up user data with Auto Backup](https://developer.android.com/identity/data/autobackup) (HIGH confidence)
- [Android Developers: Android Keystore system](https://developer.android.com/privacy-and-security/keystore) (HIGH confidence)
- [Stytch Blog: Android Keystore pitfalls and best practices](https://stytch.com/blog/android-keystore-pitfalls-and-best-practices/) (MEDIUM confidence)
- [ProAndroidDev: Goodbye EncryptedSharedPreferences -- 2026 Migration Guide](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a) (MEDIUM confidence)
- [KINTO Tech Blog: Migrate from EncryptedSharedPreferences to Tink and DataStore](https://blog.kinto-technologies.com/posts/2025-06-16-encrypted-shared-preferences-migration/) (MEDIUM confidence -- includes performance benchmarks)
- [ProAndroidDev: Android KeyStore StrongBox vs hardware-backed keys](https://proandroiddev.com/android-keystore-what-is-the-difference-between-strongbox-and-hardware-backed-keys-4c276ea78fd0) (MEDIUM confidence)
- [ed-george/encrypted-shared-preferences: Community fork post-deprecation](https://github.com/ed-george/encrypted-shared-preferences) (MEDIUM confidence)
- [OWASP MASTG: Android KeyStore knowledge base](https://mas.owasp.org/MASTG/knowledge/android/MASVS-STORAGE/MASTG-KNOW-0043/) (HIGH confidence)
- [Android Developers Blog: Configure and troubleshoot R8 Keep Rules (Nov 2025)](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html) (HIGH confidence)
- [Tink documentation: Encrypt large files or data streams](https://developers.google.com/tink/encrypt-large-files-or-data-streams) (HIGH confidence -- official Google docs)
- [Google Play Console: FLAG_SECURE and REQUIRE_SECURE_ENV](https://support.google.com/googleplay/android-developer/answer/14638385) (HIGH confidence)

---
*Pitfalls research for: Android Document Scanner -- Security Hardening (v1.2)*
*Researched: 2026-03-03*
