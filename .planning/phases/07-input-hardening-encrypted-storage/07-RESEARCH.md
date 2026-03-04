# Phase 7: Input Hardening & Encrypted Storage - Research

**Researched:** 2026-03-04
**Domain:** Android security -- input validation, path traversal prevention, encrypted SharedPreferences, Android KeyStore
**Confidence:** HIGH

## Summary

Phase 7 addresses two security domains: (1) validating all external input -- navigation args containing file paths and imported content URIs -- against path traversal and MIME type attacks, and (2) encrypting SharedPreferences at rest using `androidx.security:security-crypto` (which wraps Tink AEAD with Android Keystore-backed keys).

The codebase has three navigation fragments receiving file path args (PreviewFragment/imageUri, PdfViewerFragment/pdfPath, PdfEditorFragment/pdfUri) and one import entry point (HomeFragment) that accepts content URIs. All file storage uses `filesDir` subdirectories (scans/, processed/, pdfs/), providing a clear canonical boundary for path validation. Two SharedPreferences files exist: `document_history` (stores absolute file paths in JSON -- sensitive) and `pdf_scanner_prefs` (stores theme/filter preferences). The user decided to merge both into a single EncryptedSharedPreferences file with prefix-namespaced keys.

**Primary recommendation:** Use `androidx.security:security-crypto:1.1.0` for EncryptedSharedPreferences (deprecated but stable, functional, and internally uses Tink 1.7.0 with bundled ProGuard rules). Create a centralized `InputValidator` utility in `util/` and a `SecurePreferences` singleton managing encrypted storage with KeyStore fallback. Migration runs on first access with sentinel key for idempotency.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Navigate back + Snackbar on path validation failure (consistent with existing Snackbar pattern from v1.0)
- Error messages are security-neutral: generic "Document not available" -- no information leakage about validation mechanism
- Path validation checks storage boundary only (not file existence -- that's already handled by DocumentEntry.exists() and other existing checks)
- Centralized InputValidator utility class in util/ package (consistent with ImageUtils, PdfUtils pattern)
- Image imports: accept image/* (covers JPEG, PNG, WebP, HEIC from camera/gallery)
- PDF imports: MIME check only via contentResolver.getType() -- PdfRenderer already fails gracefully on corrupted files
- application/octet-stream URIs: reject per banking-app security stance -- users can use gallery picker instead of file managers
- MIME failure message: "Unsupported file type" -- clear but doesn't reveal accepted types
- Encrypt BOTH prefs files (document_history + pdf_scanner_prefs) -- banking-app stance, encrypt everything
- Merge into single EncryptedSharedPreferences file -- simpler key management, one migration path, keys namespaced by prefix
- Migration is silent -- SharedPreferences are small, near-instant migration, no UI needed
- Old unencrypted files retained through v1.2 cycle (already decided) -- delete in Phase 10 audit
- Silent fallback to unencrypted prefs -- no user notification, log for diagnostics only
- 3 retries with brief delay before declaring persistent failure
- Retry KeyStore each app launch -- if system update fixes it, encryption kicks in automatically
- Fallback scope: prefs only (this phase) -- Phase 8 file encryption handles its own fallback separately

### Claude's Discretion
- InputValidator internal implementation (canonicalization approach, etc.)
- Exact retry delay strategy for KeyStore attempts
- Migration sentinel key naming and verification logic
- R8 keep rules specifics for Tink classes
- Prefs key prefix scheme for merged file

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SEC-07 | Navigation args containing file paths validated against app-private storage boundaries; imported URIs validated for expected MIME types | Path canonicalization via File.getCanonicalPath() + startsWith(filesDir); MIME validation via contentResolver.getType() with image/* and application/pdf allowlist; InputValidator utility class pattern |
| SEC-08 | Document history SharedPreferences encrypted at rest using Tink AEAD with Android Keystore-backed keys | security-crypto:1.1.0 provides EncryptedSharedPreferences with MasterKey (AES256_GCM keystore-backed); migration via sentinel key; KeyStore fallback with retry logic |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| androidx.security:security-crypto | 1.1.0 | EncryptedSharedPreferences + MasterKey | Stable release (July 2025); wraps Tink 1.7.0 internally; provides SharedPreferences-compatible API so DocumentHistoryRepository/AppPreferences need minimal changes |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| (no additional deps) | -- | Path validation, MIME validation | Use Android platform APIs only: File.getCanonicalPath(), contentResolver.getType() |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| security-crypto:1.1.0 | Tink 1.20.0 + DataStore directly | More future-proof (security-crypto is deprecated), but requires DataStore migration (coroutines, Flow), much larger code change, breaks SharedPreferences API compatibility that DocumentHistoryRepository relies on |
| security-crypto:1.1.0 | ed-george/encrypted-shared-preferences fork | Community fork for ongoing support, but third-party dependency for a security-critical component |

**Decision rationale:** The CONTEXT.md locks the decision to EncryptedSharedPreferences. `security-crypto:1.1.0` is deprecated but fully functional and will receive no breaking changes. The app targets v1.2 security hardening; a DataStore migration can be considered for a future milestone. The SharedPreferences-compatible API means `DocumentHistoryRepository` and `AppPreferences` need only their `getSharedPreferences()` call swapped.

**Installation:**
```kotlin
// In app/build.gradle.kts dependencies block
implementation("androidx.security:security-crypto:1.1.0")
```

**Note:** `security-crypto:1.1.0` transitively includes `com.google.crypto.tink:tink-android:1.7.0`. Tink Android bundles its own consumer ProGuard rules -- no manual R8 keep rules required for Tink classes. However, R8 in full mode (AGP 8.0+) may flag missing `com.google.errorprone.annotations` classes. Add dontwarn rules defensively.

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/java/com/pdfscanner/app/
  util/
    InputValidator.kt          # NEW: centralized path + MIME validation
    SecurePreferences.kt       # NEW: EncryptedSharedPreferences singleton + migration
    AppPreferences.kt          # MODIFIED: constructor takes SharedPreferences
    PdfPageExtractor.kt        # EXISTING: isPdfFile() reference pattern
  data/
    DocumentHistoryRepository.kt  # MODIFIED: uses SecurePreferences
  ui/
    PreviewFragment.kt         # MODIFIED: validate args.imageUri
    PdfViewerFragment.kt       # MODIFIED: validate args.pdfPath
    HomeFragment.kt            # MODIFIED: validate imported URIs
  editor/
    PdfEditorFragment.kt       # MODIFIED: validate args.pdfUri
```

### Pattern 1: Centralized Path Validation (InputValidator)
**What:** Single utility class that validates file paths against the app's private storage boundary using canonicalization.
**When to use:** Every fragment that receives a file path via navigation args, before processing the path.
**Example:**
```kotlin
// Source: Android Developers path traversal guide
// https://developer.android.com/privacy-and-security/risks/path-traversal
object InputValidator {

    /**
     * Validates that a file path resolves within the app's private storage.
     * Uses canonical path to defeat ../ traversal attacks.
     *
     * @param path The path string to validate (from nav args)
     * @param context Application context for filesDir resolution
     * @return true if the canonical path starts with filesDir canonical path
     */
    fun isPathWithinAppStorage(path: String, context: Context): Boolean {
        return try {
            val file = File(path)
            val canonicalPath = file.canonicalPath
            val appStorageCanonical = context.filesDir.canonicalPath
            canonicalPath.startsWith(appStorageCanonical)
        } catch (e: Exception) {
            // IOException from canonicalization = invalid path
            false
        }
    }

    /**
     * Validates that a URI path (for file:// URIs) is within app storage.
     * For content:// URIs, path validation is not applicable.
     */
    fun isUriPathWithinAppStorage(uriString: String, context: Context): Boolean {
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return false
                isPathWithinAppStorage(path, context)
            }
            "content" -> true  // content:// URIs are mediated by ContentResolver
            else -> false
        }
    }

    /**
     * Validates MIME type of a content URI against an allowlist.
     * Returns false for null MIME types and application/octet-stream.
     */
    fun isAllowedMimeType(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri) ?: return false
        return mimeType.startsWith("image/") || mimeType == "application/pdf"
    }
}
```

### Pattern 2: Encrypted SharedPreferences Singleton (SecurePreferences)
**What:** Thread-safe singleton that creates EncryptedSharedPreferences with MasterKey, handles KeyStore failures with retry+fallback, and migrates unencrypted data.
**When to use:** Replace all `context.getSharedPreferences()` calls in DocumentHistoryRepository and AppPreferences.
**Example:**
```kotlin
// Source: Android developer docs + Tink issue #535 fallback pattern
object SecurePreferences {
    private const val ENCRYPTED_PREFS_NAME = "secure_prefs"
    private const val SENTINEL_KEY = "_migration_complete"
    private const val MAX_RETRIES = 3
    private const val RETRY_DELAY_MS = 100L

    @Volatile
    private var instance: SharedPreferences? = null

    fun getInstance(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: createPreferences(context.applicationContext).also {
                migrateIfNeeded(context.applicationContext, it)
                instance = it
            }
        }
    }

    private fun createPreferences(context: Context): SharedPreferences {
        // Try encrypted prefs with retry
        repeat(MAX_RETRIES) { attempt ->
            try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.w("SecurePreferences", "KeyStore attempt ${attempt + 1} failed", e)
                if (attempt < MAX_RETRIES - 1) {
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        // Persistent failure: fall back to unencrypted
        Log.w("SecurePreferences", "KeyStore persistently failed, using unencrypted prefs")
        return context.getSharedPreferences(ENCRYPTED_PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
    }

    private fun migrateIfNeeded(context: Context, target: SharedPreferences) {
        if (target.getBoolean(SENTINEL_KEY, false)) return  // Already migrated

        // Migrate document_history
        val oldHistory = context.getSharedPreferences("document_history", Context.MODE_PRIVATE)
        // Migrate pdf_scanner_prefs
        val oldPrefs = context.getSharedPreferences("pdf_scanner_prefs", Context.MODE_PRIVATE)

        val editor = target.edit()

        // Copy with prefix namespacing
        oldHistory.all.forEach { (key, value) ->
            copyPrefValue(editor, "history_$key", value)
        }
        oldPrefs.all.forEach { (key, value) ->
            copyPrefValue(editor, "app_$key", value)
        }

        editor.putBoolean(SENTINEL_KEY, true)
        editor.apply()
    }
}
```

### Pattern 3: Validation-at-Entry-Point
**What:** Each fragment validates its navigation args in `onViewCreated()` before any processing. On failure, navigates back with Snackbar.
**When to use:** Every fragment receiving file path args.
**Example:**
```kotlin
// In PreviewFragment.onViewCreated()
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Validate path BEFORE any processing
    if (!InputValidator.isUriPathWithinAppStorage(args.imageUri, requireContext())) {
        showSnackbar("Document not available")
        findNavController().navigateUp()
        return
    }

    // Continue with existing logic...
    currentImageUri = Uri.parse(args.imageUri)
    // ...
}
```

### Anti-Patterns to Avoid
- **Validating after use:** Never process a file path and then validate -- always validate first, fail fast
- **Using File.exists() as validation:** Existence checks don't prevent path traversal; a file at `../../etc/passwd` may exist but should be rejected
- **Catching exception as flow control in migration:** Don't rely on EncryptedSharedPreferences throwing to detect "needs migration" -- use explicit sentinel key
- **Storing MasterKey reference:** Let the library manage the key lifecycle; don't cache MasterKey objects
- **Separate encrypted prefs per source:** User decided on single merged file; don't create `encrypted_document_history` and `encrypted_pdf_scanner_prefs` separately

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Encryption at rest | Custom AES/GCM encryption wrapper | `EncryptedSharedPreferences` from security-crypto | Handles key generation, rotation, authenticated encryption, key storage in Keystore; rolling your own crypto is the #1 Android security anti-pattern |
| Key management | Manual Keystore key generation + storage | `MasterKey.Builder` with `AES256_GCM` scheme | Handles KeyGenParameterSpec, key alias, key existence checks, hardware backing selection |
| Path canonicalization | Manual `../` string stripping | `File(path).canonicalPath` | Platform handles symlinks, double encoding, null bytes, OS-specific separators |
| MIME type detection | File extension parsing | `contentResolver.getType(uri)` | ContentResolver queries the actual content provider; file extensions can be spoofed |

**Key insight:** Both encryption and path validation have subtle edge cases that platform APIs handle correctly. Tink/security-crypto handles authenticated encryption (AES-GCM prevents tampering, not just reading), and `getCanonicalPath()` handles symlink resolution and OS-level path normalization that regex-based approaches miss.

## Common Pitfalls

### Pitfall 1: EncryptedSharedPreferences Backup Corruption
**What goes wrong:** KeyStore keys don't survive backup/restore. If encrypted prefs file is backed up and restored on a different device (or after factory reset), the app crashes on startup with KeyStoreException because the encryption key no longer exists.
**Why it happens:** Android Auto Backup backs up SharedPreferences files by default. The encrypted prefs file looks like a normal prefs file to the backup system.
**How to avoid:** Exclude the encrypted prefs file from both `backup_rules.xml` (API <31) and `data_extraction_rules.xml` (API 31+). Also exclude the fallback unencrypted file.
**Warning signs:** Crashes in `EncryptedSharedPreferences.create()` or `AndroidKeysetManager` after app restore.

### Pitfall 2: KeyStore Corruption on OEM Devices (API 24-27)
**What goes wrong:** `MasterKey.Builder.build()` or `EncryptedSharedPreferences.create()` throws `KeyStoreException` / `GeneralSecurityException` / `UnrecoverableKeyException`. Once the KeyStore is corrupted, it fails 100% of the time until a system update or factory reset.
**Why it happens:** Certain OEM implementations (Huawei P8/P9/P20, Honor 8, Samsung J3/J6, OPPO devices, Vodafone Smart) have buggy KeyStore implementations. Custom ROMs running Android 7-9 are particularly affected.
**How to avoid:** Wrap `MasterKey.Builder.build()` and `EncryptedSharedPreferences.create()` in try-catch with 3 retries. On persistent failure, fall back to unencrypted SharedPreferences. Retry on each app launch so encryption kicks in if a system update fixes the issue.
**Warning signs:** Crash reports from Huawei/Honor/OPPO/Samsung budget devices on Android 7-9.

### Pitfall 3: Migration Not Idempotent
**What goes wrong:** If the app crashes during migration (between copying data and writing sentinel), migration runs again on next launch, potentially duplicating data.
**Why it happens:** `SharedPreferences.apply()` is asynchronous; if the process dies before the write completes, the sentinel key isn't persisted.
**How to avoid:** Write sentinel key in the SAME `editor.apply()` call as the data. Since it's all one transaction, either everything persists or nothing does. The sentinel check on next launch determines whether to re-run.
**Warning signs:** Duplicate entries in document history after crash.

### Pitfall 4: ContentResolver.getType() Returns Null
**What goes wrong:** `contentResolver.getType(uri)` returns null for `file://` URIs (only works for `content://` URIs). If the app receives a file:// URI from a navigation arg, MIME validation silently fails.
**Why it happens:** `getType()` queries the ContentProvider backing the URI. `file://` URIs have no ContentProvider.
**How to avoid:** For navigation args containing file:// URIs, validate the path instead of the MIME type. MIME validation applies only to content:// URIs from import flows. The InputValidator should handle both schemes differently.
**Warning signs:** All file:// URIs being rejected by MIME validation.

### Pitfall 5: R8 Full Mode + Tink Error-Prone Annotations
**What goes wrong:** R8 in full mode (default since AGP 8.0+) reports missing `com.google.errorprone.annotations.CanIgnoreReturnValue` and `com.google.errorprone.annotations.Immutable` classes from Tink.
**Why it happens:** Tink references error-prone annotations at compile time but they're not runtime dependencies. R8 full mode is stricter about missing references.
**How to avoid:** Add `-dontwarn com.google.errorprone.annotations.**` to proguard-rules.pro alongside the Tink dependency (same commit, per project KEY DECISION).
**Warning signs:** Build fails with "Missing classes detected while running R8" mentioning errorprone.

### Pitfall 6: Prefix Collision in Merged Prefs
**What goes wrong:** If both old prefs files happen to have a key with the same name (e.g., hypothetically both had a "version" key), merging them into one file causes data loss.
**Why it happens:** SharedPreferences keys must be unique within a file.
**How to avoid:** Use distinct prefixes for each source: `history_` for document_history keys, `app_` for pdf_scanner_prefs keys. The sentinel key uses `_migration_` prefix to avoid collision with either.
**Warning signs:** Missing preferences after migration.

## Code Examples

### Example 1: MasterKey + EncryptedSharedPreferences Creation
```kotlin
// Source: Android developer reference
// https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
    context,
    "secure_prefs",                                               // file name
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // deterministic key encryption
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // authenticated value encryption
)

// Use exactly like regular SharedPreferences
val value = encryptedPrefs.getString("key", "default")
encryptedPrefs.edit().putString("key", "value").apply()
```

### Example 2: Path Canonicalization and Boundary Check
```kotlin
// Source: Android path traversal prevention guide
// https://developer.android.com/privacy-and-security/risks/path-traversal
fun isPathWithinAppStorage(path: String, context: Context): Boolean {
    return try {
        val canonical = File(path).canonicalPath
        val boundary = context.filesDir.canonicalPath
        canonical.startsWith(boundary + File.separator) || canonical == boundary
    } catch (e: IOException) {
        false  // If canonicalization fails, reject the path
    }
}
```

### Example 3: MIME Type Validation for Content URIs
```kotlin
// Source: Android ContentResolver docs
// https://developer.android.com/training/secure-file-sharing/retrieve-info
fun isAllowedImportMime(context: Context, uri: Uri): Boolean {
    val mimeType = context.contentResolver.getType(uri) ?: return false
    // Reject application/octet-stream (banking-app security stance)
    if (mimeType == "application/octet-stream") return false
    return mimeType.startsWith("image/") || mimeType == "application/pdf"
}
```

### Example 4: SharedPreferences Value Copying Helper
```kotlin
// Helper for migrating mixed-type SharedPreferences values
private fun copyPrefValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
    when (value) {
        is String -> editor.putString(key, value)
        is Int -> editor.putInt(key, value)
        is Long -> editor.putLong(key, value)
        is Float -> editor.putFloat(key, value)
        is Boolean -> editor.putBoolean(key, value)
        is Set<*> -> {
            @Suppress("UNCHECKED_CAST")
            editor.putStringSet(key, value as Set<String>)
        }
    }
}
```

### Example 5: Backup Rules Exclusion for Encrypted Prefs
```xml
<!-- backup_rules.xml (API < 31) -->
<full-backup-content>
    <exclude domain="file" path="scans/" />
    <exclude domain="file" path="processed/" />
    <exclude domain="file" path="pdfs/" />
    <!-- Encrypted prefs: KeyStore keys don't survive backup/restore -->
    <exclude domain="sharedpref" path="secure_prefs.xml" />
    <exclude domain="sharedpref" path="secure_prefs_fallback.xml" />
</full-backup-content>

<!-- data_extraction_rules.xml (API 31+) -->
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="file" path="scans/" />
        <exclude domain="file" path="processed/" />
        <exclude domain="file" path="pdfs/" />
        <exclude domain="sharedpref" path="secure_prefs.xml" />
        <exclude domain="sharedpref" path="secure_prefs_fallback.xml" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="file" path="scans/" />
        <exclude domain="file" path="processed/" />
        <exclude domain="file" path="pdfs/" />
        <exclude domain="sharedpref" path="secure_prefs.xml" />
        <exclude domain="sharedpref" path="secure_prefs_fallback.xml" />
    </device-transfer>
</data-extraction-rules>
```

### Example 6: ProGuard/R8 Rules for Tink
```
# Tink (via security-crypto:1.1.0) — consumer rules are auto-bundled in tink-android AAR.
# However, R8 full mode (AGP 8.0+) may flag missing error-prone annotations.
-dontwarn com.google.errorprone.annotations.**
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `security-crypto:1.0.0` with `MasterKeys.getOrCreate()` | `security-crypto:1.1.0` with `MasterKey.Builder` | security-crypto 1.1.0 (July 2025) | `MasterKeys` class deprecated; `MasterKey.Builder` is the correct API |
| EncryptedSharedPreferences (security-crypto) | DataStore + Tink AEAD directly | Deprecated June 2025 | security-crypto deprecated in favor of platform APIs + direct Tink; but still functional |
| Custom ProGuard rules for Tink | Auto-bundled consumer rules | Tink 1.5.0+ | No manual Tink keep rules needed; only need `-dontwarn` for error-prone annotations |
| `MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)` | `MasterKey.Builder(ctx).setKeyScheme(AES256_GCM).build()` | 1.1.0-alpha01 | New API is more flexible and properly scoped |

**Deprecated/outdated:**
- `MasterKeys` class: Use `MasterKey.Builder` instead
- `security-crypto:1.0.0`: Has known race conditions in `MasterKeys.getOrCreate()`; use 1.1.0
- `security-crypto:1.1.0-alpha06` and earlier alphas: Stable 1.1.0 is now available

## Open Questions

1. **Kotlin compiler compatibility with security-crypto:1.1.0**
   - What we know: Project uses Kotlin 1.9.21 (pinned). security-crypto:1.1.0 is a Java library with no Kotlin-specific requirements. Tink 1.7.0 (transitive) should be compatible.
   - What's unclear: Whether Tink 1.7.0's transitive dependencies conflict with the project's forced `kotlinx-coroutines:1.7.3` or `kotlin-stdlib:1.9.21` resolution strategy.
   - Recommendation: Add the dependency and run `./gradlew dependencies` to verify no version conflicts. If tink-android:1.7.0 pulls a newer kotlin-stdlib, the existing force resolution in build.gradle.kts will handle it.

2. **EncryptedSharedPreferences thread safety for concurrent reads**
   - What we know: `EncryptedSharedPreferences` implements `SharedPreferences` and is documented as thread-safe for reads. Writes through `edit().apply()` are safe.
   - What's unclear: Whether concurrent `getInstance()` calls during first-launch migration could cause issues before the singleton is assigned.
   - Recommendation: The double-checked locking pattern in SecurePreferences singleton handles this. `synchronized(this)` ensures only one thread runs migration.

## Sources

### Primary (HIGH confidence)
- [Android Developers: EncryptedSharedPreferences API Reference](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences) - API signatures, encryption schemes, factory method
- [Android Developers: Path Traversal Prevention](https://developer.android.com/privacy-and-security/risks/path-traversal) - Canonical path validation pattern, code examples
- [Android Developers: security-crypto Releases](https://developer.android.com/jetpack/androidx/releases/security) - Version 1.1.0 stable (July 2025), deprecation notice, changelog
- [Google Tink Setup for Java/Android](https://developers.google.com/tink/setup/java) - tink-android:1.20.0 (latest standalone), no ProGuard rules needed, API 24+ full support

### Secondary (MEDIUM confidence)
- [Tink GitHub Issue #535: EncryptedSharedPreferences crash](https://github.com/google/tink/issues/535) - Affected devices (Huawei, Samsung, OPPO), KeyStoreException patterns, fallback pattern
- [Google Issue Tracker #176215143: EncryptedSharedPreferences crashes](https://issuetracker.google.com/issues/176215143) - Root cause (KeyStore corruption on OEM devices), device list, try-catch fallback
- [Tink Java Issue #7: R8 full mode missing classes](https://github.com/tink-crypto/tink-java/issues/7) - errorprone annotations dontwarn fix, resolved in Tink 1.9.0 but may recur
- [ProAndroidDev: EncryptedSharedPreferences Migration Guide](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a) - DataStore + Tink as future alternative (not used here, but informed decision)

### Tertiary (LOW confidence)
- Community reports on Medium about migration sentinel patterns - informed the sentinel key approach but not verified against official docs

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - security-crypto:1.1.0 stable, verified on Android developer site, API reference confirmed
- Architecture: HIGH - Path canonicalization pattern directly from Android official security guide; EncryptedSharedPreferences pattern from API reference
- Pitfalls: HIGH - KeyStore crash issue verified across multiple authoritative sources (Google Issue Tracker, Tink GitHub); backup exclusion documented in official Android backup docs

**Research date:** 2026-03-04
**Valid until:** 2026-04-04 (stable APIs, unlikely to change within 30 days; security-crypto 1.1.0 is final stable release)
