# Phase 8: File Encryption at Rest - Research

**Researched:** 2026-03-04
**Domain:** Android file encryption -- Tink StreamingAead, transparent encrypt/decrypt across all document I/O paths, migration of existing unencrypted files, secure file deletion
**Confidence:** HIGH

## Summary

Phase 8 encrypts all document files (scans/, processed/, pdfs/) at rest using Tink's `StreamingAead` primitive with AES-256-GCM-HKDF, managed by Android KeyStore. This requires intercepting every file write path (CameraX capture, image filter processing, PDF generation, PDF editing/annotation, image import, PDF page extraction) and every file read path (Coil image loading, PdfRenderer viewing, PdfAnnotationRenderer, FileProvider sharing, BitmapFactory decoding). Existing unencrypted files must be migrated on first launch with progress UI.

The codebase has clear file I/O chokepoints: files are written via `FileOutputStream` in ~12 locations across `CameraFragment`, `PreviewFragment`, `PagesFragment`, `PdfAnnotationRenderer`, `PdfUtils`, `PdfPageExtractor`, `ImageUtils`, and `ImageProcessor`. Files are read via `ParcelFileDescriptor.open()` / `PdfRenderer`, `contentResolver.openInputStream()`, `BitmapFactory.decodeFile/Stream()`, Coil `load()`, and `File.inputStream()`. The architecture recommendation is to create a centralized `SecureFileManager` utility that wraps all encrypt/decrypt operations, then modify each I/O site to route through it. For PdfRenderer (which requires a seekable `ParcelFileDescriptor`), the pattern is decrypt-to-temp-file with cleanup -- the same pattern already used by `NativePdfView.loadPdf(context, uri)`.

**Primary recommendation:** Add `com.google.crypto.tink:tink-android:1.20.0` as a direct dependency (Gradle resolves the transitive 1.7.0 from security-crypto upward). Create `SecureFileManager` singleton using `AndroidKeysetManager` with `android-keystore://` master key URI to store the StreamingAead keyset in SharedPreferences. File encryption key MUST be separate from the existing biometric/prefs MasterKey (per STATE.md key decision). Wrap all file writes through `SecureFileManager.encryptToFile()` and all reads through `SecureFileManager.decryptFromFile()` / `SecureFileManager.decryptToTempFile()`.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SEC-09 | All document images and PDFs encrypted at rest using Tink StreamingAead; existing files migrated on first launch | tink-android:1.20.0 provides StreamingAead with AES256_GCM_HKDF_4KB key template; AndroidKeysetManager stores keyset in SharedPreferences encrypted by Android KeyStore master key; SecureFileManager centralized utility handles all encrypt/decrypt; migration iterates filesDir subdirectories with progress callback |
| SEC-10 | File deletion overwrites content with random bytes before removing filesystem reference | On flash storage, overwrite is best-effort (wear-leveling, TRIM); however, encrypted content with KeyStore-managed key provides cryptographic erasure -- deleting the key renders all encrypted data irrecoverable. Overwrite pattern still adds defense-in-depth for the migration window |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| com.google.crypto.tink:tink-android | 1.20.0 | StreamingAead file encryption + AndroidKeysetManager + Android KeyStore integration | Google-maintained crypto library; StreamingAead handles chunked encrypt/decrypt of arbitrarily large files without loading entire file into memory; AndroidKeysetManager wraps keyset in Android KeyStore-protected key |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| androidx.security:security-crypto | 1.1.0 (existing) | EncryptedSharedPreferences (Phase 7) | Already in project; tink-android:1.20.0 replaces its transitive tink 1.7.0 via Gradle resolution |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| tink-android StreamingAead | EncryptedFile (security-crypto) | EncryptedFile uses Tink internally but is deprecated, limited API, no streaming control, no random-access decrypt |
| tink-android StreamingAead | javax.crypto AES/GCM directly | Hand-rolling crypto: must handle IV generation, key management, chunking, authenticated encryption, nonce reuse prevention -- exactly what Tink automates |
| AndroidKeysetManager (SharedPrefs + KeyStore) | MasterKey.Builder (security-crypto) | MasterKey.Builder is for EncryptedSharedPreferences only; AndroidKeysetManager is the proper Tink API for managing keyset lifecycle with KeyStore wrapping |

**Installation:**
```kotlin
// In app/build.gradle.kts dependencies block
// tink-android:1.20.0 upgrades the transitive tink 1.7.0 from security-crypto
implementation("com.google.crypto.tink:tink-android:1.20.0")
```

**Dependency resolution:** Gradle resolves `com.google.crypto.tink:tink-android` to the highest version (1.20.0) across all dependency paths. The existing `security-crypto:1.1.0` (which declares tink-android:1.7.0) will use 1.20.0 at runtime. Tink maintains backward compatibility across these versions.

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/java/com/pdfscanner/app/
  util/
    SecureFileManager.kt      # NEW: centralized file encrypt/decrypt/migration
    SecurePreferences.kt      # EXISTING (Phase 7)
    InputValidator.kt         # EXISTING (Phase 7)
    ImageUtils.kt             # MODIFIED: encrypt after EXIF correction write
    ImageProcessor.kt         # MODIFIED: encrypt after saveBitmapToFile
    PdfUtils.kt               # MODIFIED: encrypt after PDF write
    PdfPageExtractor.kt       # MODIFIED: encrypt after page extraction
  ui/
    CameraFragment.kt         # MODIFIED: encrypt after CameraX capture
    PreviewFragment.kt        # MODIFIED: decrypt for display, encrypt after filter save
    PagesFragment.kt          # MODIFIED: decrypt for PDF generation, encrypt output
    PdfViewerFragment.kt      # MODIFIED: decrypt-to-temp for PdfRenderer
    HomeFragment.kt           # MODIFIED: trigger migration on first launch
    HistoryFragment.kt        # MODIFIED: secure delete
  adapter/
    PagesAdapter.kt           # MODIFIED: Coil loads via decrypting fetcher
    HistoryAdapter.kt         # MODIFIED: Coil loads decrypted thumbnails
    RecentDocumentsAdapter.kt # MODIFIED: decrypt PDF for thumbnail rendering
  editor/
    NativePdfView.kt          # MODIFIED: decrypt-to-temp for PdfRenderer
    PdfAnnotationRenderer.kt  # MODIFIED: decrypt input, encrypt output
    PdfEditorViewModel.kt     # MODIFIED: decrypt for signature loading
  data/
    DocumentHistory.kt        # MODIFIED: secure delete in removeDocument
```

### Pattern 1: SecureFileManager Singleton
**What:** Centralized singleton managing a Tink StreamingAead keyset with Android KeyStore protection. Provides encrypt/decrypt stream wrappers and migration logic.
**When to use:** Every file I/O operation on document data (scans/, processed/, pdfs/).
**Example:**
```kotlin
// Source: Tink official docs + AndroidKeysetManager API
object SecureFileManager {
    private const val TAG = "SecureFileManager"
    private const val KEYSET_PREFS_NAME = "file_encryption_keyset"
    private const val KEYSET_NAME = "doc_file_keyset"
    private const val MASTER_KEY_URI = "android-keystore://doc_file_master_key"
    private const val MIGRATION_SENTINEL = "_file_migration_complete"

    // Associated data identifies file type for authenticated encryption
    private val ASSOCIATED_DATA = "pdfscanner_file_v1".toByteArray()

    @Volatile
    private var streamingAead: StreamingAead? = null

    @Volatile
    private var isAvailable: Boolean? = null

    fun getInstance(context: Context): StreamingAead? {
        return streamingAead ?: synchronized(this) {
            streamingAead ?: createStreamingAead(context.applicationContext).also {
                streamingAead = it
                isAvailable = (it != null)
            }
        }
    }

    private fun createStreamingAead(context: Context): StreamingAead? {
        return try {
            StreamingAeadConfig.register()
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_NAME)
                .withKeyTemplate(
                    com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters
                        .AES256_GCM_HKDF_4KB
                )
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
            keysetHandle.getPrimitive(StreamingAead::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize StreamingAead", e)
            null  // Fallback: files stored unencrypted
        }
    }

    /**
     * Write encrypted data to a file.
     * If encryption is unavailable, writes plaintext (graceful degradation).
     */
    fun encryptToFile(file: File, data: ByteArray) {
        val aead = streamingAead
        if (aead != null) {
            FileOutputStream(file).use { fos ->
                aead.newEncryptingStream(fos, ASSOCIATED_DATA).use { encStream ->
                    encStream.write(data)
                }
            }
        } else {
            FileOutputStream(file).use { fos -> fos.write(data) }
        }
    }

    /**
     * Get a decrypting InputStream for an encrypted file.
     * Falls back to plain FileInputStream if decryption fails
     * (handles pre-migration unencrypted files).
     */
    fun decryptFromFile(file: File): InputStream {
        val aead = streamingAead
        if (aead != null) {
            return try {
                aead.newDecryptingStream(FileInputStream(file), ASSOCIATED_DATA)
            } catch (e: Exception) {
                // File may be unencrypted (pre-migration) -- fall back
                FileInputStream(file)
            }
        }
        return FileInputStream(file)
    }

    /**
     * Decrypt a file to a temporary file (for APIs requiring File/PFD).
     * Caller MUST delete the temp file when done.
     */
    fun decryptToTempFile(context: Context, encryptedFile: File): File {
        val tempFile = File(context.cacheDir, "dec_${UUID.randomUUID()}.tmp")
        decryptFromFile(encryptedFile).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    /**
     * Encrypt a Bitmap to a JPEG file.
     */
    fun encryptBitmapToFile(bitmap: Bitmap, file: File, quality: Int = 90) {
        val aead = streamingAead
        if (aead != null) {
            FileOutputStream(file).use { fos ->
                aead.newEncryptingStream(fos, ASSOCIATED_DATA).use { encStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, encStream)
                }
            }
        } else {
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            }
        }
    }

    /**
     * Encrypt a PdfDocument to a file.
     */
    fun encryptPdfToFile(pdfDocument: PdfDocument, file: File) {
        val aead = streamingAead
        if (aead != null) {
            FileOutputStream(file).use { fos ->
                aead.newEncryptingStream(fos, ASSOCIATED_DATA).use { encStream ->
                    pdfDocument.writeTo(encStream)
                }
            }
        } else {
            FileOutputStream(file).use { fos ->
                pdfDocument.writeTo(fos)
            }
        }
    }
}
```

### Pattern 2: Decrypt-to-Temp for PdfRenderer
**What:** PdfRenderer requires a seekable `ParcelFileDescriptor`. Encrypted files cannot be opened directly. Decrypt to a temp file in cacheDir, open with PdfRenderer, delete temp file on close.
**When to use:** PdfViewerFragment, NativePdfView, RecentDocumentsAdapter, PdfUtils (any PdfRenderer usage).
**Example:**
```kotlin
// In PdfViewerFragment.loadPdf()
withContext(Dispatchers.IO) {
    val file = File(args.pdfPath)
    // Decrypt to temp file for PdfRenderer (which needs seekable PFD)
    val tempFile = SecureFileManager.decryptToTempFile(ctx, file)
    try {
        fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(fileDescriptor!!)
        pageCount = pdfRenderer?.pageCount ?: 0
    } finally {
        // Track tempFile for cleanup in onDestroyView
        this@PdfViewerFragment.tempDecryptedFile = tempFile
    }
}
```

### Pattern 3: Encrypt-After-Write for CameraX
**What:** CameraX `ImageCapture.takePicture()` writes directly to a file via `OutputFileOptions`. Cannot intercept the write stream. Solution: let CameraX write plaintext, then encrypt in-place in the `OnImageSavedCallback`.
**When to use:** CameraFragment capture flow.
**Example:**
```kotlin
// In CameraFragment.takePhoto() callback
object : ImageCapture.OnImageSavedCallback {
    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
        // CameraX wrote plaintext; now encrypt in-place
        lifecycleScope.launch(Dispatchers.IO) {
            SecureFileManager.encryptFileInPlace(photoFile)
        }
        // Continue with encrypted file URI
        val uri = Uri.fromFile(photoFile)
        // ... navigate to preview
    }
}
```

### Pattern 4: Transparent Coil Decryption
**What:** Coil `load(uri)` cannot read encrypted files. Two options: (A) custom Fetcher that decrypts on-the-fly, or (B) decrypt to a Bitmap and use `load(bitmap)`. Option B is simpler and avoids Coil pipeline complexity.
**When to use:** PagesAdapter, HistoryAdapter image thumbnail loading.
**Example:**
```kotlin
// Option B: Decode bitmap from encrypted file, pass to Coil
// In adapter binding
lifecycleScope.launch(Dispatchers.IO) {
    val bitmap = SecureFileManager.decryptToBitmap(file)
    withContext(Dispatchers.Main) {
        binding.imageThumbnail.load(bitmap) {
            crossfade(true)
            placeholder(R.drawable.ic_cartoon_document)
        }
    }
}
```

### Pattern 5: Encrypt-in-Place Helper
**What:** Read plaintext file, write encrypted to temp, atomically rename temp over original. Used for CameraX post-capture and migration.
**When to use:** CameraX callback, migration of existing files.
**Example:**
```kotlin
fun encryptFileInPlace(file: File) {
    val aead = streamingAead ?: return
    val tempFile = File(file.parent, "${file.name}.enc.tmp")
    try {
        FileInputStream(file).use { input ->
            FileOutputStream(tempFile).use { fos ->
                aead.newEncryptingStream(fos, ASSOCIATED_DATA).use { encStream ->
                    input.copyTo(encStream)
                }
            }
        }
        // Atomic rename
        if (!tempFile.renameTo(file)) {
            // Fallback: copy + delete
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    } catch (e: Exception) {
        tempFile.delete() // Cleanup on failure
        Log.e(TAG, "encryptFileInPlace failed for ${file.name}", e)
    }
}
```

### Pattern 6: Migration with Progress UI
**What:** On first launch after update, iterate all files in scans/, processed/, pdfs/ and encrypt each in-place. Show a non-cancelable progress dialog.
**When to use:** MainActivity or HomeFragment on app start, gated by migration sentinel in SecurePreferences.
**Example:**
```kotlin
// Migration flow
suspend fun migrateExistingFiles(
    context: Context,
    onProgress: (current: Int, total: Int) -> Unit
): Boolean {
    val prefs = SecurePreferences.getInstance(context)
    if (prefs.getBoolean(MIGRATION_SENTINEL, false)) return true // Already done

    val dirs = listOf("scans", "processed", "pdfs")
    val allFiles = dirs.flatMap { dir ->
        File(context.filesDir, dir).listFiles()?.toList() ?: emptyList()
    }.filter { it.isFile }

    if (allFiles.isEmpty()) {
        prefs.edit().putBoolean(MIGRATION_SENTINEL, true).apply()
        return true
    }

    allFiles.forEachIndexed { index, file ->
        encryptFileInPlace(file)
        onProgress(index + 1, allFiles.size)
    }

    prefs.edit().putBoolean(MIGRATION_SENTINEL, true).apply()
    return true
}
```

### Pattern 7: Secure File Deletion
**What:** For SEC-10, overwrite file content with random bytes before deletion. On flash storage, this is best-effort (wear-leveling may preserve old blocks). However, since files are AES-256-GCM encrypted with a KeyStore-managed key, the encryption itself provides cryptographic erasure -- without the key, encrypted blocks are unrecoverable.
**When to use:** DocumentHistoryRepository.removeDocument(), HistoryFragment delete, HomeFragment delete.
**Example:**
```kotlin
fun secureDelete(file: File): Boolean {
    if (!file.exists()) return true
    return try {
        // Overwrite with random bytes (best-effort on flash)
        val length = file.length()
        if (length > 0) {
            val random = java.security.SecureRandom()
            val buffer = ByteArray(8192)
            RandomAccessFile(file, "rw").use { raf ->
                var remaining = length
                while (remaining > 0) {
                    val toWrite = minOf(remaining, buffer.size.toLong()).toInt()
                    random.nextBytes(buffer)
                    raf.write(buffer, 0, toWrite)
                    remaining -= toWrite
                }
                raf.fd.sync() // Force write to storage
            }
        }
        file.delete()
    } catch (e: Exception) {
        Log.w("SecureFileManager", "Secure delete failed, falling back to regular delete", e)
        file.delete() // Still delete even if overwrite fails
    }
}
```

### Anti-Patterns to Avoid
- **Mixing encryption keys with biometric keys:** STATE.md explicitly states "Biometric auth keys MUST be separate from file encryption keys." The file encryption keyset uses its own KeyStore alias (`doc_file_master_key`), completely separate from the `MasterKey` used by `SecurePreferences`
- **Encrypting temp files in cacheDir:** Temp files are ephemeral and cleaned up immediately. Encrypting them adds latency with no security benefit since cacheDir is already excluded from backup and auto-cleaned by the OS
- **Blocking the UI thread for encryption:** All encrypt/decrypt operations MUST run on `Dispatchers.IO`. Migration runs in a coroutine with progress callback
- **Failing silently on encryption unavailability:** If KeyStore is broken (OEM bugs), log warning and operate unencrypted (same fallback pattern as SecurePreferences). User's documents remain accessible
- **Using AEAD instead of StreamingAead:** AEAD loads the entire file into memory for encryption. Document images can be 10+ MB. StreamingAead processes in chunks (4KB segments with AES256_GCM_HKDF_4KB)
- **Re-encrypting files on every read:** Decrypt returns a stream/temp file. Do not re-encrypt after reading
- **Sharing encrypted files via FileProvider:** FileProvider serves the encrypted file directly, which external apps cannot read. Must decrypt to temp file in cacheDir, share the temp file via FileProvider, clean up after share completes

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Stream encryption | Custom AES/GCM with chunking | Tink StreamingAead | Handles segment encryption, nonce management, authentication tags, key derivation per-segment; hand-rolling has catastrophic nonce-reuse risk |
| Key management | Manual KeyStore key generation + storage | AndroidKeysetManager | Handles keyset creation, KeyStore wrapping, SharedPreferences persistence, key rotation, self-test for buggy KeyStore implementations |
| Encrypt/decrypt streams | Custom InputStream/OutputStream wrappers | `streamingAead.newEncryptingStream()` / `newDecryptingStream()` | Returns standard Java streams; works with `Bitmap.compress()`, `PdfDocument.writeTo()`, `input.copyTo()` |
| Secure random bytes | `java.util.Random` | `java.security.SecureRandom` | Cryptographically secure PRNG; `Random` is predictable |
| Atomic file replacement | Delete + write | Write to temp + rename | Prevents data loss if app crashes during encryption |

**Key insight:** The entire value proposition of this phase is that Tink provides battle-tested streaming encryption. The complexity is NOT in the encryption itself -- it is in identifying and modifying every file I/O path in the codebase to route through SecureFileManager.

## Common Pitfalls

### Pitfall 1: PdfRenderer Cannot Read Encrypted Files
**What goes wrong:** `PdfRenderer` requires a seekable `ParcelFileDescriptor`. Encrypted files contain Tink ciphertext headers and segment tags, not valid PDF data. `PdfRenderer` throws `IOException`.
**Why it happens:** PdfRenderer is an Android platform API that expects raw PDF bytes at specific offsets. There is no way to provide a decrypting `ParcelFileDescriptor`.
**How to avoid:** Always decrypt to a temp file in cacheDir before opening with PdfRenderer. Track the temp file reference and delete in `onDestroyView()` / `close()`.
**Warning signs:** `IOException` or blank pages in PdfViewerFragment, NativePdfView, or RecentDocumentsAdapter thumbnail loading.

### Pitfall 2: CameraX OutputFileOptions Cannot Encrypt
**What goes wrong:** `ImageCapture.takePicture(OutputFileOptions.Builder(file).build(), ...)` writes directly to the file. There is no API to provide an encrypting OutputStream.
**Why it happens:** CameraX handles the file write internally via the Camera2 pipeline.
**How to avoid:** Let CameraX write plaintext, then encrypt-in-place in the `OnImageSavedCallback`. The window of plaintext exposure is milliseconds and only in app-private storage.
**Warning signs:** Encrypted file is unreadable -- check that encryption runs AFTER CameraX callback, not concurrently.

### Pitfall 3: Coil Cannot Load Encrypted Images
**What goes wrong:** `imageView.load(Uri.fromFile(encryptedFile))` fails because Coil reads raw bytes and attempts JPEG/PNG decoding on ciphertext.
**Why it happens:** Coil's default `FileFetcher` reads files directly without any decryption layer.
**How to avoid:** Either (A) implement a custom Coil `Fetcher` that decrypts before decoding, or (B) decrypt to Bitmap on IO thread and pass the Bitmap to `load()`. Option B is simpler and sufficient since thumbnails are small and cached in memory by Coil.
**Warning signs:** Coil error callbacks triggering, placeholder images showing permanently.

### Pitfall 4: FileProvider Shares Encrypted Content
**What goes wrong:** When sharing a PDF via `FileProvider.getUriForFile()`, the receiving app gets encrypted bytes instead of a valid PDF.
**Why it happens:** FileProvider serves raw file content. It has no knowledge of encryption.
**How to avoid:** Before sharing, decrypt the file to a temp file in cacheDir, then create a FileProvider URI for the temp file. Clean up the temp file after the share intent resolves (use `onActivityResult` or a cleanup timer).
**Warning signs:** External apps showing "Invalid PDF" or corrupted file errors when user shares.

### Pitfall 5: Migration Crash Leaves Files Half-Encrypted
**What goes wrong:** If the app crashes during migration (e.g., mid-encrypt on file 15 of 30), some files are encrypted and some are not. On next launch, the encrypted files fail to read as plaintext and the migration sentinel is not set, so migration retries but re-encrypting already-encrypted files corrupts them.
**Why it happens:** `encryptFileInPlace` uses atomic temp-file rename, but the overall migration batch is not atomic.
**How to avoid:** (1) `encryptFileInPlace` is idempotent -- before encrypting, attempt to decrypt first; if decryption succeeds, the file is already encrypted, skip it. (2) The migration sentinel is only written AFTER all files are processed. (3) Individual file encryption is atomic (temp + rename), so a crash during one file's encryption leaves either the original plaintext OR the fully encrypted version.
**Warning signs:** `GeneralSecurityException` during migration on already-encrypted files.

### Pitfall 6: Tink Version Conflict with security-crypto
**What goes wrong:** Adding `tink-android:1.20.0` alongside `security-crypto:1.1.0` (which transitively depends on `tink-android:1.7.0`) could cause API incompatibilities.
**Why it happens:** Gradle resolves to the highest version (1.20.0), but if 1.20.0 removed or changed APIs that security-crypto 1.1.0 uses internally, runtime crashes occur.
**How to avoid:** Tink maintains backward compatibility. The APIs used by security-crypto (AEAD, MasterKey internals) are stable across 1.7.0 to 1.20.0. Run `./gradlew dependencies` after adding the dependency to verify resolution. Run the existing SecurePreferences tests to confirm no regression.
**Warning signs:** `NoSuchMethodError` or `ClassNotFoundException` in Tink classes at runtime.

### Pitfall 7: KeyStore Alias Collision
**What goes wrong:** Using the same KeyStore alias for file encryption and preferences encryption causes key-type mismatch errors.
**Why it happens:** security-crypto's MasterKey uses `_androidx_security_master_key_` alias for AES256_GCM (AEAD). If SecureFileManager accidentally used the same alias, the key type would not match StreamingAead requirements.
**How to avoid:** Use a distinct alias: `doc_file_master_key` for file encryption, completely separate from the prefs MasterKey.
**Warning signs:** `GeneralSecurityException: wrong key type` during AndroidKeysetManager initialization.

### Pitfall 8: Overwrite Ineffective on Flash Storage
**What goes wrong:** Writing random bytes over a file on flash storage (eMMC/UFS) may not actually overwrite the same physical blocks due to wear-leveling and TRIM.
**Why it happens:** Flash storage controllers remap logical blocks to physical blocks transparently. The OS writes to a new physical block while the old block is queued for TRIM/garbage collection.
**How to avoid:** Accept that overwrite is best-effort on flash. The true security guarantee comes from encryption: with the AES-256-GCM key in KeyStore, the encrypted file content on disk is cryptographically unrecoverable without the key. The overwrite pattern adds defense-in-depth during the migration window when unencrypted files exist.
**Warning signs:** None visible -- this is a known limitation of flash storage. Document it as a security note.

## Code Examples

### Example 1: StreamingAead Initialization with AndroidKeysetManager
```kotlin
// Source: Tink AndroidKeysetManager docs
// https://github.com/tink-crypto/tink/blob/master/java_src/src/main/java/com/google/crypto/tink/integration/android/AndroidKeysetManager.java
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import com.google.crypto.tink.streamingaead.PredefinedStreamingAeadParameters

// One-time initialization (Application.onCreate or lazy singleton)
StreamingAeadConfig.register()

val keysetHandle = AndroidKeysetManager.Builder()
    .withSharedPref(context, "doc_file_keyset", "file_encryption_keyset")
    .withKeyTemplate(PredefinedStreamingAeadParameters.AES256_GCM_HKDF_4KB)
    .withMasterKeyUri("android-keystore://doc_file_master_key")
    .build()
    .keysetHandle

val streamingAead = keysetHandle.getPrimitive(StreamingAead::class.java)
```

### Example 2: Encrypt a Bitmap to File via StreamingAead
```kotlin
// Source: Tink StreamingAead API
fun encryptBitmapToFile(aead: StreamingAead, bitmap: Bitmap, file: File, quality: Int = 90) {
    FileOutputStream(file).use { fos ->
        aead.newEncryptingStream(fos, ASSOCIATED_DATA).use { encStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, encStream)
        }
    }
}
```

### Example 3: Decrypt File to Bitmap
```kotlin
// Source: Tink StreamingAead API + BitmapFactory
fun decryptToBitmap(aead: StreamingAead, file: File): Bitmap? {
    return try {
        aead.newDecryptingStream(FileInputStream(file), ASSOCIATED_DATA).use { decStream ->
            BitmapFactory.decodeStream(decStream)
        }
    } catch (e: Exception) {
        // Fallback: file may be unencrypted (pre-migration)
        BitmapFactory.decodeFile(file.absolutePath)
    }
}
```

### Example 4: Encrypt PdfDocument Output
```kotlin
// Source: Tink StreamingAead API + Android PdfDocument
fun encryptPdfToFile(aead: StreamingAead, pdfDocument: PdfDocument, file: File) {
    FileOutputStream(file).use { fos ->
        aead.newEncryptingStream(fos, ASSOCIATED_DATA).use { encStream ->
            pdfDocument.writeTo(encStream)
        }
    }
}
```

### Example 5: Decrypt to Temp File for PdfRenderer
```kotlin
// Source: Existing NativePdfView pattern + Tink StreamingAead
fun decryptToTempFile(context: Context, aead: StreamingAead, encryptedFile: File): File {
    val tempFile = File(context.cacheDir, "dec_${UUID.randomUUID()}.tmp")
    try {
        aead.newDecryptingStream(FileInputStream(encryptedFile), ASSOCIATED_DATA).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        // Fallback: file may be unencrypted
        encryptedFile.inputStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
    }
    return tempFile
}
```

### Example 6: ProGuard Rules for Tink 1.20.0
```
# Tink 1.20.0 — consumer rules are bundled in the AAR, but R8 full mode
# may flag missing error-prone and protobuf annotations.
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.protobuf.**

# Keep Tink's KeyManager and StreamingAead classes loaded via reflection
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
```

### Example 7: Migration Progress UI
```kotlin
// Non-cancelable MaterialAlertDialog with ProgressBar
private fun showMigrationProgress() {
    val progressView = layoutInflater.inflate(R.layout.dialog_migration_progress, null)
    val progressBar = progressView.findViewById<ProgressBar>(R.id.progressBar)
    val progressText = progressView.findViewById<TextView>(R.id.textProgress)

    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle("Securing your documents")
        .setView(progressView)
        .setCancelable(false)
        .create()
    dialog.show()

    lifecycleScope.launch {
        val success = withContext(Dispatchers.IO) {
            SecureFileManager.migrateExistingFiles(applicationContext) { current, total ->
                launch(Dispatchers.Main) {
                    progressBar.max = total
                    progressBar.progress = current
                    progressText.text = "Encrypting $current of $total files..."
                }
            }
        }
        dialog.dismiss()
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| EncryptedFile (security-crypto) | Tink StreamingAead directly | security-crypto deprecated June 2025 | Direct Tink gives full control over key management, streaming, and error handling |
| `StreamingAeadFactory.getPrimitive()` | `keysetHandle.getPrimitive(StreamingAead::class.java)` | Tink 1.13.0+ | Factory classes deprecated; use KeysetHandle directly |
| `StreamingAeadKeyTemplates.AES256_GCM_HKDF_4KB` | `PredefinedStreamingAeadParameters.AES256_GCM_HKDF_4KB` | Tink 1.14.0+ | KeyTemplates deprecated in favor of PredefinedParameters |
| `TinkConfig.register()` (global) | `StreamingAeadConfig.register()` (specific) | Best practice | Register only the primitives you need; reduces attack surface |
| Manual File.delete() | Overwrite + delete (best-effort) + encryption (cryptographic guarantee) | Phase 8 | Flash storage makes overwrite unreliable; encryption provides true security |

**Deprecated/outdated:**
- `StreamingAeadFactory`: Use `keysetHandle.getPrimitive()` directly
- `StreamingAeadKeyTemplates`: Use `PredefinedStreamingAeadParameters`
- `TinkConfig.register()`: Use specific config registrations (`StreamingAeadConfig.register()`)
- `EncryptedFile` (security-crypto): Deprecated; use Tink StreamingAead directly for file encryption

## File I/O Inventory

Complete list of file I/O sites that must be modified:

### Write Paths (must encrypt after write)
| File | Method | What it writes | Current API |
|------|--------|---------------|-------------|
| CameraFragment.kt | takePhoto() | Camera capture to scans/ | ImageCapture.takePicture(OutputFileOptions) |
| PreviewFragment.kt | saveFilteredImageAndAddPage() | Filtered image to processed/ | ImageProcessor.saveBitmapToFile() -> FileOutputStream |
| PreviewFragment.kt | onCropResult() | Cropped image to scans/ | CanHub returns File, may need re-encrypt |
| PagesFragment.kt | generatePdf() | PDF document to pdfs/ | PdfDocument.writeTo(FileOutputStream) |
| PagesFragment.kt | rotateImage() | Rotated scan to scans/ | Bitmap.compress(FileOutputStream) |
| PdfUtils.kt | mergePdfs/splitPdf/compressPdf/extractPages | PDFs to pdfs/ | PdfDocument.writeTo(FileOutputStream) |
| PdfPageExtractor.kt | extractPage() | PDF pages as JPEG to scans/ | Bitmap.compress(FileOutputStream) |
| ImageUtils.kt | correctExifOrientation() | EXIF-corrected image to scans/ | Bitmap.compress(FileOutputStream) |
| PdfAnnotationRenderer.kt | renderAnnotatedPdf() | Annotated PDF to pdfs/ | PdfDocument.writeTo(FileOutputStream) |
| PdfEditorViewModel.kt | saveSignature() | Signature bitmap to signatures/ | Bitmap.compress(FileOutputStream) |

### Read Paths (must decrypt before read)
| File | Method | What it reads | Current API |
|------|--------|--------------|-------------|
| PdfViewerFragment.kt | loadPdf() | PDF from pdfs/ | ParcelFileDescriptor.open() -> PdfRenderer |
| NativePdfView.kt | loadPdf(file) | PDF from pdfs/ | ParcelFileDescriptor.open() -> PdfRenderer |
| PagesAdapter.kt | bind() | Scan thumbnails from scans/ | Coil load(uri) |
| HistoryAdapter.kt | bind() | PDF thumbnails from pdfs/ | Coil load(Uri.fromFile) |
| RecentDocumentsAdapter.kt | loadPdfThumbnail() | PDFs from pdfs/ | ParcelFileDescriptor.open() -> PdfRenderer |
| PreviewFragment.kt | loadFullResBitmap() | Images from scans/ | contentResolver.openInputStream() |
| PagesFragment.kt | generatePdf() | Scan images for PDF | contentResolver.openInputStream() |
| PdfAnnotationRenderer.kt | renderAnnotatedPdf() | Source PDF | contentResolver.openFileDescriptor() |
| PdfUtils.kt | all methods | PDFs for merge/split/compress | contentResolver.openFileDescriptor() |

### Delete Paths (must secure delete)
| File | Method | What it deletes |
|------|--------|----------------|
| DocumentHistory.kt | removeDocument() | PDF files |
| DocumentHistory.kt | clearHistory() | All PDF files |
| HistoryFragment.kt | delete actions | PDF files |
| HomeFragment.kt | delete actions | PDF files |
| PreviewFragment.kt | retake button | Captured scan image |
| MainActivity.kt | cleanupStaleTempFiles() | Temp files (no encryption needed) |

## Open Questions

1. **CanHub Image Cropper output encryption**
   - What we know: CanHub writes the cropped result to a file URI that the app provides. The app creates the output file in scans/.
   - What's unclear: Whether CanHub's internal write pipeline supports an encrypting OutputStream, or whether we must encrypt-in-place after the crop result returns.
   - Recommendation: Encrypt-in-place after crop result (same pattern as CameraX). CanHub returns the result file path in its callback.

2. **Tink 1.20.0 ProGuard rules on R8 full mode**
   - What we know: tink-android bundles consumer ProGuard rules. Phase 7 already added `-dontwarn com.google.errorprone.annotations.**`.
   - What's unclear: Whether tink-android:1.20.0 adds new classes that R8 full mode might strip (e.g., StreamingAead implementation classes loaded via reflection).
   - Recommendation: Add defensive `-keep class com.google.crypto.tink.** { *; }` rule. Build a release APK and test the full encrypt/decrypt flow.

3. **PredefinedStreamingAeadParameters availability in tink-android 1.20.0**
   - What we know: The `PredefinedStreamingAeadParameters` class replaced `StreamingAeadKeyTemplates` in Tink 1.14.0+.
   - What's unclear: Whether tink-android:1.20.0 packages this class or if it is only in tink-java.
   - Recommendation: If `PredefinedStreamingAeadParameters` is not available, fall back to `KeyTemplates.get("AES256_GCM_HKDF_4KB")` which is the legacy but still-supported API.

4. **Keyset SharedPreferences backup exclusion**
   - What we know: AndroidKeysetManager stores the wrapped keyset in a SharedPreferences file (`file_encryption_keyset`). This file MUST be excluded from backup (same issue as secure_prefs).
   - What's unclear: Automatic -- just add the exclusion rule.
   - Recommendation: Add `<exclude domain="sharedpref" path="file_encryption_keyset.xml" />` to both backup rule files.

## Sources

### Primary (HIGH confidence)
- [Tink StreamingAead Overview](https://developers.google.com/tink/streaming-aead) - Recommended key types, security properties, segment encryption model
- [Tink File Encryption Guide](https://developers.google.com/tink/encrypt-large-files-or-data-streams) - Java StreamingAead example with FileChannel, registry initialization
- [Tink AES-GCM-HKDF Streaming Key Specification](https://developers.google.com/tink/streaming-aead/aes_gcm_hkdf_streaming) - Key parameters, segment sizes, limitations
- [Tink Java Setup](https://developers.google.com/tink/setup/java) - tink-android:1.20.0, API 24+ full support, no ProGuard config needed
- [AndroidKeysetManager Source](https://github.com/tink-crypto/tink/blob/master/java_src/src/main/java/com/google/crypto/tink/integration/android/AndroidKeysetManager.java) - SharedPref + KeyStore integration, builder API, thread safety notes

### Secondary (MEDIUM confidence)
- [Tink GitHub Issue #229: API 23 AES256_GCM_HKDF_4KB bug](https://github.com/tink-crypto/tink/issues/229) - Confirmed fix; API 24+ (our min SDK) is unaffected
- [Maven Central: tink-android versions](https://mvnrepository.com/artifact/com.google.crypto.tink/tink-android) - Version history, 1.20.0 is latest
- [Coil Image Pipeline Extension](https://coil-kt.github.io/coil/image_pipeline/) - Custom Fetcher API for alternative decryption approach

### Tertiary (LOW confidence)
- Community blog posts on file encryption patterns - informed architectural decisions but code patterns verified against official Tink docs
- Flash storage secure deletion discussions - confirmed that overwrite is best-effort; encryption is the real guarantee

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - tink-android:1.20.0 verified on Maven Central and Google Tink docs; StreamingAead API confirmed in official documentation
- Architecture: HIGH - File I/O paths exhaustively inventoried from codebase; decrypt-to-temp pattern already exists in NativePdfView; encrypt-in-place pattern follows standard atomic file operations
- Pitfalls: HIGH - PdfRenderer limitation verified from Android API docs; CameraX OutputFileOptions limitation verified from CameraX docs; KeyStore issues documented in Phase 7 research and Tink GitHub issues

**Research date:** 2026-03-04
**Valid until:** 2026-04-04 (Tink is stable; StreamingAead API has not changed since Tink 1.14.0)
