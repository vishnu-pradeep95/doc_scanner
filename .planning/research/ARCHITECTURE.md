# Architecture Research: Security Hardening Integration

**Domain:** Android document scanner security hardening (Single Activity + MVVM)
**Researched:** 2026-03-03
**Confidence:** HIGH

## System Overview: Security Layer Integration

The security hardening adds a new `security/` package that wraps around existing I/O operations
without restructuring the MVVM architecture. The key principle is: **encryption sits between
the repository/utility layer and the filesystem**, not inside fragments or ViewModel.

```
+-----------------------------------------------------------------------+
|                        UI Layer (Fragments)                           |
|  +----------+ +--------+ +---------+ +------+ +------+ +----------+  |
|  |   Home   | | Camera | | Preview | | Pages| |PdfEd | | Settings |  |
|  +----+-----+ +---+----+ +----+----+ +--+---+ +--+---+ +----+-----+  |
|       |           |           |          |        |          |        |
|  FLAG_SECURE set/cleared per fragment via Activity callback           |
|  BiometricPrompt launched from fragments, gates navigation            |
+-------------------------------+---------------------------------------+
                                |
+-------------------------------v---------------------------------------+
|                     ViewModel Layer                                    |
|  +-------------------+  +-----------------------+                     |
|  | ScannerViewModel  |  | PdfEditorViewModel    |                     |
|  | (activityScope)   |  | (fragment scope)      |                     |
|  +--------+----------+  +----------+------------+                     |
|           |                        |                                  |
|  No encryption awareness. URIs point to encrypted files.              |
|  ViewModel passes URIs; decryption is transparent.                    |
+-----------+------------------------+----------------------------------+
            |                        |
+-----------v------------------------v----------------------------------+
|                Repository / Utility Layer                             |
|  +------------------------------+  +-----------------------------+   |
|  | DocumentHistoryRepository    |  | PdfUtils / ImageProcessor   |   |
|  | (EncryptedSharedPreferences  |  | (read/write via             |   |
|  |  or DataStore+Tink)          |  |  SecureFileManager)         |   |
|  +------------------------------+  +-----------------------------+   |
|                                                                       |
|  +------------------------------+  +-----------------------------+   |
|  | AppPreferences               |  | SecureFileManager (NEW)     |   |
|  | (EncryptedSharedPreferences  |  | Wraps File I/O with Tink   |   |
|  |  or DataStore+Tink)          |  | StreamingAead encryption    |   |
|  +------------------------------+  +-----------------------------+   |
+-------------------------------+---------------------------------------+
                                |
+-------------------------------v---------------------------------------+
|                     Security Layer (NEW)                              |
|  +-----------------------------+  +------------------------------+   |
|  | SecurityManager (NEW)       |  | SecureFileManager (NEW)      |   |
|  | - Tink keyset management    |  | - Encrypt/decrypt streams    |   |
|  | - Android Keystore binding  |  | - Secure deletion            |   |
|  | - Biometric key gating      |  | - Transparent file wrapper   |   |
|  +-----------------------------+  +------------------------------+   |
|                                                                       |
|  +-----------------------------+  +------------------------------+   |
|  | BiometricHelper (NEW)       |  | IntentValidator (NEW)        |   |
|  | - BiometricPrompt wrapper   |  | - Validates incoming intents |   |
|  | - CryptoObject integration  |  | - FileProvider URI checks    |   |
|  +-----------------------------+  +------------------------------+   |
+-------------------------------+---------------------------------------+
                                |
+-------------------------------v---------------------------------------+
|                     Storage Layer                                     |
|  +-------------+  +--------------+  +------------+  +-------------+  |
|  | filesDir/   |  | filesDir/    |  | filesDir/  |  | cacheDir/   |  |
|  | scans/      |  | processed/   |  | pdfs/      |  | (temp)      |  |
|  | (encrypted) |  | (encrypted)  |  | (encrypted)|  | (plaintext) |  |
|  +-------------+  +--------------+  +------------+  +-------------+  |
+-----------------------------------------------------------------------+
```

## Component Responsibilities

| Component | Responsibility | Status |
|-----------|---------------|--------|
| `SecurityManager` | Tink keyset init, Android Keystore key management, master key lifecycle | NEW |
| `SecureFileManager` | Encrypt/decrypt file streams, secure delete, transparent I/O wrapper | NEW |
| `BiometricHelper` | BiometricPrompt setup, CryptoObject creation, auth state tracking | NEW |
| `IntentValidator` | Validate incoming intents, sanitize extras, verify FileProvider URIs | NEW |
| `SecurePreferences` | Migration wrapper: reads old SharedPreferences, writes to encrypted store | NEW |
| `SecureDeletion` | Overwrite-then-delete for sensitive files | NEW |
| `MainActivity` | FLAG_SECURE toggling per active fragment destination, app-lock gate | MODIFIED |
| `DocumentHistoryRepository` | Migrate to encrypted backing store | MODIFIED |
| `AppPreferences` | Migrate to encrypted backing store | MODIFIED |
| `PdfUtils` | Use SecureFileManager for file I/O instead of raw File/FileOutputStream | MODIFIED |
| `ImageProcessor` | Use SecureFileManager for file I/O | MODIFIED |
| `CameraFragment` | Integrate with SecureFileManager for capture output | MODIFIED |
| `PagesFragment` | Use SecureFileManager for PDF generation and sharing | MODIFIED |
| `SettingsFragment` | Add security settings (app lock toggle, biometric toggle) | MODIFIED |

## Recommended Project Structure

```
app/src/main/java/com/pdfscanner/app/
+-- security/                    # NEW PACKAGE
|   +-- SecurityManager.kt      # Tink keyset + Android Keystore management
|   +-- SecureFileManager.kt    # Encrypted file I/O wrapper
|   +-- BiometricHelper.kt      # BiometricPrompt integration
|   +-- IntentValidator.kt      # Intent/URI validation
|   +-- SecurePreferences.kt    # Encrypted key-value storage wrapper
|   +-- SecureDeletion.kt       # Overwrite-then-delete utility
+-- data/
|   +-- DocumentHistory.kt      # MODIFIED: use SecurePreferences
+-- util/
|   +-- AppPreferences.kt       # MODIFIED: use SecurePreferences
|   +-- PdfUtils.kt             # MODIFIED: use SecureFileManager
|   +-- ImageProcessor.kt       # MODIFIED: use SecureFileManager
|   +-- ImageUtils.kt           # MODIFIED: use SecureFileManager
+-- ui/
|   +-- (all fragments)          # MODIFIED: FLAG_SECURE awareness
+-- viewmodel/
|   +-- ScannerViewModel.kt     # UNCHANGED: URIs remain opaque
+-- MainActivity.kt             # MODIFIED: FLAG_SECURE + biometric gate
```

### Structure Rationale

- **security/**: Isolated package makes security code auditable and testable in isolation. All security primitives are co-located, preventing encryption logic from scattering across UI/data layers.
- **Existing packages stay intact**: Fragments, ViewModel, and adapters maintain their current structure. Security integrates via method-call boundaries, not inheritance or restructuring.
- **SecureFileManager as facade**: Single entry point for all file I/O means encryption is enforced consistently. No fragment or utility bypasses encryption by accident.

## Architectural Patterns

### Pattern 1: Transparent Encryption via File I/O Wrapper

**What:** SecureFileManager wraps all file read/write operations. Callers pass a target File and receive an InputStream/OutputStream. Internally, Tink StreamingAead encrypts on write and decrypts on read. Callers never see ciphertext.

**When to use:** Every file operation on scans/, processed/, pdfs/ directories.

**Trade-offs:**
- PRO: Existing code changes are minimal (swap `FileOutputStream(file)` for `secureFileManager.openEncryptedOutput(file)`)
- PRO: Encryption cannot be forgotten because the only File I/O path goes through the wrapper
- CON: Slight performance overhead from Tink streaming (measured at ~5-10% for large files)
- CON: Files are not readable outside the app (intended for security, but affects debugging)

**Example:**
```kotlin
// security/SecureFileManager.kt
class SecureFileManager(private val securityManager: SecurityManager) {

    private val streamingAead: StreamingAead by lazy {
        securityManager.getStreamingAead()
    }

    /**
     * Open an encrypted output stream. Data written to this stream
     * is encrypted with AES256-GCM-HKDF before hitting disk.
     *
     * @param file Target file (will contain ciphertext)
     * @param associatedData Context binding (e.g., "scan" or "pdf") -- authenticated but not encrypted
     */
    fun openEncryptedOutput(file: File, associatedData: ByteArray = ByteArray(0)): OutputStream {
        val fileChannel = FileOutputStream(file).channel
        return Channels.newOutputStream(
            streamingAead.newEncryptingChannel(fileChannel, associatedData)
        )
    }

    /**
     * Open a decrypted input stream. Reads ciphertext from disk
     * and returns plaintext to the caller.
     */
    fun openDecryptedInput(file: File, associatedData: ByteArray = ByteArray(0)): InputStream {
        val fileChannel = FileInputStream(file).channel
        return Channels.newInputStream(
            streamingAead.newDecryptingChannel(fileChannel, associatedData)
        )
    }
}
```

**Integration with existing PdfUtils:**
```kotlin
// BEFORE (PdfUtils.kt line ~144)
FileOutputStream(outputFile).use { fos ->
    pdfDocument.writeTo(fos)
}

// AFTER
secureFileManager.openEncryptedOutput(outputFile, "pdf".toByteArray()).use { os ->
    pdfDocument.writeTo(os)
}
```

### Pattern 2: Activity-Level FLAG_SECURE with Fragment Destination Awareness

**What:** FLAG_SECURE is a Window flag -- it can only be set on the Activity's window, not per-fragment. In a single-activity architecture, the Activity listens to NavController destination changes and toggles FLAG_SECURE based on which fragment is displayed.

**When to use:** Fragments displaying sensitive document content (Preview, Camera preview, Pages, PdfViewer, PdfEditor). HomeFragment and SettingsFragment may NOT need it.

**Trade-offs:**
- PRO: Centralized logic in one place (MainActivity)
- PRO: Works with Navigation Component's OnDestinationChangedListener
- CON: FLAG_SECURE is all-or-nothing per window -- cannot protect one fragment while leaving another visible
- CON: Affects the entire screen including system UI, which may feel heavy-handed

**Example:**
```kotlin
// MainActivity.kt -- in onCreate(), after NavController setup
val navController = navHostFragment.navController

// Fragments that display sensitive document content
val secureDestinations = setOf(
    R.id.cameraFragment,
    R.id.previewFragment,
    R.id.pagesFragment,
    R.id.pdfViewerFragment,
    R.id.pdfEditorFragment
)

navController.addOnDestinationChangedListener { _, destination, _ ->
    if (destination.id in secureDestinations) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
```

**Dialog caveat:** MaterialAlertDialogBuilder dialogs inherit the Activity window's FLAG_SECURE. This is correct behavior for this app -- dialogs over secure fragments should also be protected. No additional work needed for dialogs.

### Pattern 3: Biometric Gating via Navigation Interceptor

**What:** BiometricPrompt authenticates in a Fragment, then conditionally allows navigation. The biometric check gates app entry (on cold start or return from background), not individual fragment transitions.

**When to use:** App launch and return-from-background (configurable in Settings).

**Trade-offs:**
- PRO: BiometricPrompt is lifecycle-aware when hosted in Fragment/FragmentActivity
- PRO: Navigation Component's NavController.navigate() can be called in the success callback
- CON: BiometricPrompt cannot be shown before a Fragment's view is created, so there is a brief flash
- CON: Process death + biometric creates UX complexity (user returns to mid-scan state but must auth first)

**Example:**
```kotlin
// BiometricHelper.kt
class BiometricHelper(private val fragment: Fragment) {

    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(
            fragment,
            ContextCompat.getMainExecutor(fragment.requireContext()),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onFailure()
                    }
                }
            }
        )
        biometricPrompt.authenticate(promptInfo)
    }
}
```

**Navigation Integration:** The HomeFragment's `onViewCreated()` checks biometric state. If the user has not authenticated this session, show BiometricPrompt before revealing content. The ViewModel tracks `isAuthenticated` as transient state (not persisted -- fresh auth on each cold start).

### Pattern 4: SharedPreferences Migration Without Data Loss

**What:** Migrate `AppPreferences` (pdf_scanner_prefs) and `DocumentHistoryRepository` (document_history) from plaintext SharedPreferences to encrypted storage. Use a two-phase migration: read old -> write encrypted -> delete old.

**When to use:** First app launch after the security update.

**Critical decision: EncryptedSharedPreferences vs DataStore+Tink**

The `security-crypto` library (which includes EncryptedSharedPreferences) was deprecated in v1.1.0 (July 2025). The official recommendation is DataStore + Tink. However, for THIS project:

**Use EncryptedSharedPreferences (security-crypto 1.1.0 stable) because:**
1. The app uses synchronous SharedPreferences access patterns throughout (prefs.getInt(), prefs.edit().putInt().apply())
2. DataStore is async-only (Flow/suspend) and would require rewriting every preference read site
3. The stored data is small (a few ints + one JSON string) -- the keyset corruption issues that plagued ESP primarily affected high-frequency writes
4. The community fork `io.github.nickebbitt:security-crypto` provides ongoing maintenance if the stable release becomes unmaintained
5. Migration from SharedPreferences to EncryptedSharedPreferences is trivial because ESP implements the SharedPreferences interface

**Alternative if ESP proves unstable:** Wrap Tink AEAD (not streaming) directly around SharedPreferences values. Encrypt each value before putString(), decrypt after getString(). This avoids the DataStore migration entirely while using Tink directly.

**Migration pattern:**
```kotlin
// SecurePreferences.kt
class SecurePreferences(context: Context) {

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            "pdf_scanner_prefs_encrypted",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * One-time migration: reads all values from old plaintext prefs,
     * writes them to encrypted prefs, then deletes old file.
     */
    fun migrateIfNeeded(context: Context) {
        val migrationKey = "migration_complete_v1"
        if (encryptedPrefs.getBoolean(migrationKey, false)) return

        // Migrate AppPreferences
        val oldAppPrefs = context.getSharedPreferences(
            "pdf_scanner_prefs", Context.MODE_PRIVATE
        )
        if (oldAppPrefs.all.isNotEmpty()) {
            val editor = encryptedPrefs.edit()
            oldAppPrefs.all.forEach { (key, value) ->
                when (value) {
                    is Int -> editor.putInt(key, value)
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                }
            }
            editor.putBoolean(migrationKey, true)
            editor.apply()

            // Delete old plaintext file
            context.deleteSharedPreferences("pdf_scanner_prefs")
        }

        // Migrate DocumentHistory
        val oldHistoryPrefs = context.getSharedPreferences(
            "document_history", Context.MODE_PRIVATE
        )
        if (oldHistoryPrefs.all.isNotEmpty()) {
            val historyEditor = encryptedPrefs.edit()
            oldHistoryPrefs.all.forEach { (key, value) ->
                if (value is String) historyEditor.putString("history_$key", value)
            }
            historyEditor.apply()
            context.deleteSharedPreferences("document_history")
        }
    }
}
```

### Pattern 5: Secure Deletion with Overwrite

**What:** When deleting sensitive files (scans, processed images, PDFs), overwrite file contents with random bytes before calling File.delete(). This prevents recovery from flash storage wear-leveling remnants.

**When to use:** All file deletions in scans/, processed/, pdfs/ directories. Also applies to temp file cleanup in MainActivity.

**Trade-offs:**
- PRO: Significantly harder to recover deleted files
- CON: Flash storage wear-leveling means overwrite is not guaranteed to hit same physical blocks
- CON: Slower than plain delete (must write file-length of random data)
- MITIGATION: Combined with at-rest encryption, secure deletion provides defense-in-depth

**Example:**
```kotlin
// security/SecureDeletion.kt
object SecureDeletion {

    /**
     * Overwrite file contents with random bytes, then delete.
     * Best-effort on flash storage -- combined with encryption for defense-in-depth.
     */
    fun secureDelete(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            val length = file.length()
            if (length > 0) {
                RandomAccessFile(file, "rw").use { raf ->
                    val random = SecureRandom()
                    val buffer = ByteArray(8192)
                    var remaining = length
                    while (remaining > 0) {
                        random.nextBytes(buffer)
                        val toWrite = minOf(remaining, buffer.size.toLong()).toInt()
                        raf.write(buffer, 0, toWrite)
                        remaining -= toWrite
                    }
                    raf.fd.sync()
                }
            }
            file.delete()
        } catch (e: Exception) {
            // Fall back to regular delete
            file.delete()
        }
    }
}
```

### Pattern 6: Intent Validation at Activity Entry Point

**What:** Validate all incoming intents in MainActivity before they reach any fragment. Since this app has a single exported Activity (launcher), the attack surface is limited. The FileProvider is already non-exported. Validation focuses on: (a) sanitizing any extras on the launch intent, (b) validating FileProvider URIs before sharing.

**When to use:** MainActivity.onCreate() for incoming intents; HomeFragment for intent results from document/PDF pickers.

**Trade-offs:**
- PRO: Centralized validation point (single Activity)
- PRO: Existing app only has launcher intent filter -- minimal attack surface
- CON: Over-validation can break legitimate deep links if added later

**Example:**
```kotlin
// security/IntentValidator.kt
object IntentValidator {

    /**
     * Validate that a URI from an intent result is safe to process.
     * Rejects file:// URIs from external sources (must be content://).
     */
    fun isValidContentUri(uri: Uri?): Boolean {
        if (uri == null) return false
        return uri.scheme == "content"
    }

    /**
     * Validate a file path argument from SafeArgs navigation.
     * Ensures path is within app-private storage.
     */
    fun isValidInternalPath(context: Context, path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val resolved = File(path).canonicalPath
        val filesDir = context.filesDir.canonicalPath
        val cacheDir = context.cacheDir.canonicalPath
        return resolved.startsWith(filesDir) || resolved.startsWith(cacheDir)
    }
}
```

## Data Flow

### Scan-to-PDF Flow (with encryption)

```
[Camera Capture]
    |
    v
CameraFragment.takePhoto()
    |
    v
imageCapture.takePicture(outputFile) -- writes plaintext to temp File
    |
    v
SecureFileManager.encryptInPlace(outputFile) -- re-encrypts the file
    |                                           (read plaintext, write ciphertext,
    |                                            delete plaintext)
    v
viewModel.addPage(encryptedFileUri) -- URI stored in SavedStateHandle
    |
    v
[User edits: crop/filter in PreviewFragment]
    |
    v
ImageProcessor reads via SecureFileManager.openDecryptedInput()
    |        writes via SecureFileManager.openEncryptedOutput()
    v
[User taps "Create PDF" in PagesFragment]
    |
    v
PagesFragment reads pages via SecureFileManager.openDecryptedInput()
    |        Decodes bitmaps, renders to PdfDocument
    |        Writes PDF via SecureFileManager.openEncryptedOutput()
    v
[Share via FileProvider]
    |
    v
Temp decrypted copy to cacheDir -> FileProvider URI -> Intent.ACTION_SEND
    |
    v
Temp file cleaned up after share completes (or on next startup)
```

### Biometric App Lock Flow

```
[App Launch / Return from Background]
    |
    v
HomeFragment.onViewCreated() checks SecurityManager.isAuthRequired()
    |
    +-- YES --> BiometricHelper.authenticate()
    |               |
    |               +-- onSuccess() --> show fragment content,
    |               |                   set isAuthenticated = true
    |               |
    |               +-- onError() --> finish() activity (lock user out)
    |
    +-- NO --> show fragment content directly
```

### SharedPreferences Migration Flow

```
[First launch after update]
    |
    v
Application.onCreate() or MainActivity.onCreate()
    |
    v
SecurePreferences.migrateIfNeeded(context)
    |
    +-- Check "migration_complete_v1" flag in encrypted prefs
    |
    +-- NOT migrated:
    |       Read old "pdf_scanner_prefs" (plaintext)
    |       Write all entries to encrypted prefs
    |       Read old "document_history" (plaintext)
    |       Write all entries to encrypted prefs
    |         (namespaced with "history_" prefix)
    |       Set migration flag
    |       Delete old SharedPreferences files
    |
    +-- Already migrated: skip
```

## Integration Points with Existing Components

### Component-by-Component Impact

| Existing Component | Security Integration | Change Size | Risk |
|---|---|---|---|
| **MainActivity** | Add FLAG_SECURE OnDestinationChangedListener; call SecurePreferences.migrateIfNeeded(); update cleanupStaleTempFiles() to use SecureDeletion | Small | LOW -- additive changes |
| **ScannerViewModel** | NONE -- URIs remain opaque. No encryption awareness needed. | Zero | NONE |
| **CameraFragment** | After capture, encrypt file via SecureFileManager.encryptInPlace() | Small | LOW -- one call added after save |
| **PreviewFragment** | Load images via SecureFileManager (decrypt on read) instead of raw URI | Medium | MEDIUM -- bitmap loading path changes |
| **PagesFragment** | PDF generation reads via SecureFileManager; share creates temp decrypted copy; delete uses SecureDeletion | Medium | MEDIUM -- multiple I/O paths |
| **PdfViewerFragment** | Open PDF via decrypted temp copy (PdfRenderer needs seekable FileDescriptor) | Medium | MEDIUM -- PdfRenderer requires raw FD |
| **PdfEditorFragment** | Same as PdfViewer -- decrypted temp copy for editing | Medium | MEDIUM |
| **HomeFragment** | Biometric gate on entry; intent result validation | Small | LOW |
| **SettingsFragment** | New security section (app lock toggle, biometric toggle); uses SecurePreferences | Small | LOW |
| **DocumentHistoryRepository** | Constructor takes SecurePreferences instead of raw SharedPreferences | Small | LOW -- same API (SharedPreferences interface) |
| **AppPreferences** | Constructor takes SecurePreferences instead of raw SharedPreferences | Small | LOW -- same API |
| **PdfUtils** | All FileOutputStream replaced with SecureFileManager.openEncryptedOutput() | Medium | MEDIUM -- 6+ I/O sites |
| **ImageProcessor** | File reads/writes through SecureFileManager | Small | LOW -- utility methods |
| **ImageUtils** | EXIF correction output via SecureFileManager | Small | LOW |
| **PdfPageExtractor** | Read imported PDFs (external, not encrypted); write extracted pages via SecureFileManager | Small | LOW |
| **FileProvider (file_paths.xml)** | No change. FileProvider serves content:// URIs from filesDir paths. Encrypted files are at same paths. | Zero | NONE |
| **nav_graph.xml** | No structural changes. Biometric gating is in fragment code, not navigation. | Zero | NONE |

### PdfRenderer Compatibility Issue

**Problem:** PdfRenderer requires a seekable ParcelFileDescriptor. Tink StreamingAead provides a streaming decryption channel, not a seekable one. You cannot pass an encrypted file directly to PdfRenderer.

**Solution:** For PDF viewing/editing, decrypt to a temp file in cacheDir, open PdfRenderer on the temp file, then securely delete the temp file when done. This is the same pattern already used by PdfViewerFragment (which creates temp copies in cacheDir). The only change is adding encryption/decryption around the copy step.

```kotlin
// In PdfViewerFragment / PdfEditorFragment:
val tempFile = File(
    requireContext().cacheDir,
    "pdf_view_temp_${System.currentTimeMillis()}.pdf"
)
secureFileManager.decryptToFile(encryptedPdfFile, tempFile)
// Use tempFile with PdfRenderer as before
// On fragment destroy: SecureDeletion.secureDelete(tempFile)
```

### Coil Image Loading Compatibility

**Problem:** Coil loads images from URIs. Encrypted files cannot be loaded directly by Coil because the bytes are ciphertext.

**Solution:** Register a custom Coil `Fetcher` that detects encrypted file URIs and decrypts them to a Bitmap before passing to Coil's pipeline. Alternatively, decrypt to a temp file and load that. The custom Fetcher approach is cleaner:

```kotlin
// Custom Coil fetcher for encrypted files
class EncryptedFileFetcher(
    private val secureFileManager: SecureFileManager,
    private val file: File
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val inputStream = secureFileManager.openDecryptedInput(file)
        return SourceResult(
            source = inputStream.source().buffer(),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK
        )
    }
}
```

### CameraX Capture Integration

**Problem:** CameraX ImageCapture writes directly to a File via OutputFileOptions. There is no way to inject an encrypted OutputStream into the CameraX capture pipeline.

**Solution:** Let CameraX write plaintext to the file as normal, then immediately encrypt-in-place in the onImageSaved callback:

```kotlin
// In CameraFragment.takePhoto() onImageSaved callback:
override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
    // CameraX wrote plaintext JPEG to photoFile
    // Encrypt it in place before the URI propagates to ViewModel
    lifecycleScope.launch(Dispatchers.IO) {
        secureFileManager.encryptInPlace(photoFile)
        withContext(Dispatchers.Main) {
            val savedUri = photoFile.toUri()
            viewModel.setCurrentCapture(savedUri)
            // ... navigation as before
        }
    }
}
```

### CanHub Image Cropper Integration

**Problem:** The CanHub Image Cropper library (CropImageActivity) expects to read and write files directly. It cannot work with encrypted files.

**Solution:** Decrypt to a temp file before launching the cropper, let the cropper write its output to a temp file, then encrypt the result and clean up the temp files. This follows the same temp-decrypt pattern used for PdfRenderer.

## Anti-Patterns

### Anti-Pattern 1: Encrypting in the ViewModel

**What people do:** Add encryption/decryption calls inside ScannerViewModel methods.
**Why it's wrong:** ViewModel should be UI-state management only. Mixing I/O concerns makes it untestable with pure JVM tests (existing 22 ViewModel tests would break). Also creates tight coupling between state management and cryptography.
**Do this instead:** Encryption lives in SecureFileManager. ViewModel passes URIs. Fragments call SecureFileManager when doing I/O.

### Anti-Pattern 2: Per-Fragment FLAG_SECURE Calls

**What people do:** Each fragment calls `requireActivity().window.setFlags(FLAG_SECURE, ...)` in its own `onResume()`.
**Why it's wrong:** 8 fragments means 8 places to remember. Fragment transitions can race (new fragment's onResume before old fragment's onPause). Clearing FLAG_SECURE in one fragment while another secure fragment is still visible causes a security gap.
**Do this instead:** Single OnDestinationChangedListener in MainActivity. One source of truth for which destinations are secure.

### Anti-Pattern 3: Encrypting SharedPreferences Values Individually

**What people do:** Manually encrypt each value before `putString()` and decrypt after `getString()`.
**Why it's wrong:** Key names are still plaintext (leaks what settings exist). Error-prone (forget to encrypt one value). Duplicates effort that EncryptedSharedPreferences already handles.
**Do this instead:** Use EncryptedSharedPreferences which encrypts both keys AND values. Or use the Tink AEAD wrapper only as a fallback if ESP is unstable.

### Anti-Pattern 4: Blocking Main Thread for Biometric

**What people do:** Show BiometricPrompt in `onCreate()` before `setContentView()`, hoping to prevent any UI from appearing.
**Why it's wrong:** BiometricPrompt requires a Fragment or FragmentActivity with an active lifecycle. Calling it before the view is ready crashes. Also, there will always be at least one frame rendered before the prompt appears.
**Do this instead:** Show a blank/splash overlay in the layout, show BiometricPrompt in `onViewCreated()`, and remove the overlay on authentication success. Accept the brief visual transition as unavoidable.

### Anti-Pattern 5: Storing Encryption Keys in SharedPreferences

**What people do:** Generate an AES key and store it in SharedPreferences (even encrypted ones).
**Why it's wrong:** Keys should never leave the Android Keystore hardware-backed store. If a key is extractable, a rooted device can read it.
**Do this instead:** Store Tink keysets encrypted with an Android Keystore master key. The Tink keyset contains the actual AES key material, but it is wrapped (encrypted) by the Keystore master key. The AES key never appears in plaintext outside the Keystore.

## Scaling Considerations (Security Context)

| Concern | Current (v1.2) | Future (v2+) |
|---------|----------------|--------------|
| File count | Encrypt all files in scans/processed/pdfs/ | Same -- no change needed |
| Performance | Tink StreamingAead adds ~5-10% overhead | Consider chunk size tuning if >100 page docs |
| Key rotation | Single master keyset, no rotation | Add keyset rotation on major version upgrade |
| Biometric | Simple app-lock gate | Per-document biometric (CryptoObject per PDF) |
| Multi-user | N/A (single user app) | Consider separate keysets per user profile |
| Cloud sync | N/A (out of scope) | End-to-end encryption with user-held key |

## Build Order (Dependency-Aware)

Security features have strict dependencies. Building in the wrong order creates rework.

```
Phase 1: Foundation (must be first -- everything else depends on this)
  1. SecurityManager (Tink init, Keystore master key)
  2. SecureFileManager (encrypt/decrypt streams, using SecurityManager)
  3. SecureDeletion (standalone, no dependencies)

Phase 2: Data Migration (depends on Phase 1)
  4. SecurePreferences (migration wrapper)
  5. AppPreferences migration (swap backing store)
  6. DocumentHistoryRepository migration (swap backing store)

Phase 3: File Encryption Integration (depends on Phase 1)
  7. CameraFragment capture encryption
  8. ImageProcessor/ImageUtils encrypted I/O
  9. PdfUtils encrypted I/O
  10. Coil encrypted file fetcher
  11. PdfViewer/PdfEditor temp-decrypt pattern
  12. CanHub Cropper temp-decrypt pattern

Phase 4: UI Security (independent of Phases 2-3, depends on Phase 1)
  13. FLAG_SECURE in MainActivity
  14. IntentValidator
  15. BiometricHelper
  16. SettingsFragment security section
  17. App-lock biometric gate in HomeFragment

Phase 5: Hardening (depends on all above)
  18. Secure deletion integration in all delete paths
  19. Existing temp cleanup migration to SecureDeletion
  20. ProGuard/R8 keep rules for Tink classes
  21. Security audit pass (verify no plaintext leaks)
```

**Rationale:** SecurityManager/SecureFileManager are the foundation that all other features depend on. SharedPreferences migration and file encryption are independent of each other but both need the foundation. UI security (FLAG_SECURE, biometric) is independent of encryption but should come after the foundation is proven stable. Hardening is last because it cross-cuts everything.

## Key Technology Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| File encryption library | **Tink Android 1.20.0** | Official Google crypto library. StreamingAead handles large files. Fully supported on minSdk 24. NOT deprecated (unlike security-crypto). |
| File encryption scheme | **AES128_GCM_HKDF_4KB** | 4KB segment size keeps memory usage low (important for 10+ page doc processing). GCM provides authenticated encryption. |
| Key storage | **Android Keystore** via Tink AndroidKeysetManager | Hardware-backed key storage. Tink wraps keyset with Keystore master key automatically. |
| SharedPreferences encryption | **EncryptedSharedPreferences** (security-crypto 1.1.0 stable) | Same SharedPreferences interface means minimal code changes. Deprecated but stable release (1.1.0 July 2025). Small data volume avoids known keyset corruption issues. |
| Biometric library | **androidx.biometric:biometric:1.1.0** | Stable release. Fragment-aware. CryptoObject support. minSdk 23 (project minSdk is 24). |
| Biometric authentication mode | **BIOMETRIC_STRONG or DEVICE_CREDENTIAL** | Allows fingerprint/face AND PIN/pattern fallback. Broadest device compatibility. |
| Screenshot prevention | **FLAG_SECURE** via OnDestinationChangedListener | Only reliable mechanism on Android. Activity-level but fragment-aware via nav listener. |

## New Dependencies

```kotlin
// build.gradle.kts additions:

// Tink -- file encryption (StreamingAead)
implementation("com.google.crypto.tink:tink-android:1.20.0")

// Security Crypto -- EncryptedSharedPreferences (deprecated but stable 1.1.0)
implementation("androidx.security:security-crypto:1.1.0")

// Biometric -- BiometricPrompt for app lock
implementation("androidx.biometric:biometric:1.1.0")
```

**Note on dependency conflicts:** Tink 1.20.0 uses Protocol Buffers internally. Check for conflicts with ML Kit's protobuf dependency. If conflicts arise, use `force()` in resolutionStrategy as already done for coroutines/stdlib.

## Sources

- [Android Keystore system](https://developer.android.com/privacy-and-security/keystore) -- Official Keystore documentation (HIGH confidence)
- [Tink Streaming AEAD](https://developers.google.com/tink/streaming-aead) -- Official Tink docs for file encryption (HIGH confidence)
- [Tink setup for Java/Android](https://developers.google.com/tink/setup/java) -- Version 1.20.0, minSdk 24 confirmed (HIGH confidence)
- [Security-crypto releases](https://developer.android.com/jetpack/androidx/releases/security) -- Deprecation status, 1.1.0 stable (HIGH confidence)
- [Biometric library releases](https://developer.android.com/jetpack/androidx/releases/biometric) -- v1.1.0 stable, minSdk 23 (HIGH confidence)
- [BiometricPrompt with CryptoObject](https://medium.com/androiddevelopers/using-biometricprompt-with-cryptoobject-how-and-why-aace500ccdb7) -- Official Android Developers blog (MEDIUM confidence)
- [FLAG_SECURE and window flags](https://issuetracker.google.com/issues/143778149) -- Fragment/Dialog FLAG_SECURE propagation (MEDIUM confidence)
- [Goodbye EncryptedSharedPreferences migration guide](https://www.droidcon.com/2025/12/16/goodbye-encryptedsharedpreferences-a-2026-migration-guide/) -- Community migration patterns (MEDIUM confidence)
- [EncryptedSharedPreferences to Tink and DataStore](https://blog.kinto-technologies.com/posts/2025-06-16-encrypted-shared-preferences-migration/) -- Tink vs Keystore performance comparison (MEDIUM confidence)
- [Intent security best practices](https://securecodingpractices.com/android-intent-security-best-practices-filters-permissions-validation/) -- Intent validation patterns (MEDIUM confidence)
- [Preventing screenshots on Android](https://tomasrepcik.dev/blog/2023/2023-12-09-android-securing-screen/) -- FLAG_SECURE in single-activity apps (MEDIUM confidence)
- [Community fork: encrypted-shared-preferences](https://github.com/ed-george/encrypted-shared-preferences) -- Maintained ESP fork post-deprecation (LOW confidence -- community maintained)

---
*Architecture research for: Security Hardening of Android Document Scanner (v1.2)*
*Researched: 2026-03-03*
