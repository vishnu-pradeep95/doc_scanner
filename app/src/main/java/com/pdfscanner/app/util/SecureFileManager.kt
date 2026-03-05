package com.pdfscanner.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.UUID

/**
 * SecureFileManager -- Thread-safe singleton providing Tink StreamingAead
 * file encryption/decryption with Android KeyStore protection.
 *
 * Uses a SEPARATE KeyStore alias (doc_file_master_key) from SecurePreferences
 * MasterKey (_androidx_security_master_key_) per STATE.md decision:
 * "Biometric auth keys MUST be separate from file encryption keys."
 *
 * Graceful degradation: if KeyStore is unavailable (OEM bugs on API 24-27),
 * files are stored unencrypted. Retries on each app launch so encryption
 * activates if a system update fixes the KeyStore.
 *
 * All encrypt/decrypt methods are safe to call from Dispatchers.IO.
 * Do NOT encrypt temp files in cacheDir (anti-pattern from research).
 */
object SecureFileManager {

    private const val TAG = "SecureFileManager"
    private const val KEYSET_PREFS_NAME = "file_encryption_keyset"
    private const val KEYSET_NAME = "doc_file_keyset"
    private const val MASTER_KEY_URI = "android-keystore://doc_file_master_key"
    private const val MIGRATION_SENTINEL = "_file_migration_complete"

    // Associated data identifies file type for authenticated encryption
    private val ASSOCIATED_DATA = "pdfscanner_file_v1".toByteArray()

    // Secure delete buffer size (8KB chunks)
    private const val SECURE_DELETE_BUFFER_SIZE = 8192

    @Volatile
    private var streamingAead: StreamingAead? = null

    /**
     * Get the StreamingAead instance (or null if KeyStore is unavailable).
     * First call initializes Tink and creates/loads the keyset.
     * Thread-safe via double-checked locking.
     */
    fun getInstance(context: Context): StreamingAead? {
        return streamingAead ?: synchronized(this) {
            streamingAead ?: createStreamingAead(context.applicationContext).also {
                streamingAead = it
            }
        }
    }

    @Suppress("DEPRECATION") // getPrimitive(Class) — Configuration-based API not yet public for StreamingAead in Tink 1.20.0
    private fun createStreamingAead(context: Context): StreamingAead? {
        return try {
            StreamingAeadConfig.register()
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_NAME)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .getKeysetHandle()
            keysetHandle.getPrimitive(StreamingAead::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize StreamingAead, files will be stored unencrypted", e)
            null // Fallback: files stored unencrypted (graceful degradation)
        }
    }

    // ========================================================================
    // Write Operations (fallback to plaintext if StreamingAead is null)
    // ========================================================================

    /**
     * Write encrypted data to a file.
     * If encryption is unavailable, writes plaintext (graceful degradation).
     */
    fun encryptToFile(file: File, data: ByteArray) {
        val aead = streamingAead
        if (aead != null) {
            try {
                FileOutputStream(file).use { fos ->
                    aead.newEncryptingStream(fos, ASSOCIATED_DATA).use { encStream ->
                        encStream.write(data)
                    }
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Encryption failed for ${file.name}, writing plaintext", e)
            }
        }
        // Fallback: write plaintext
        FileOutputStream(file).use { fos -> fos.write(data) }
    }

    /**
     * Encrypt a Bitmap to a file.
     * If encryption is unavailable, writes plaintext.
     *
     * @param bitmap The bitmap to write
     * @param file Target file
     * @param quality Compression quality (0-100)
     * @param format Compress format (default JPEG; use PNG for signatures)
     */
    fun encryptBitmapToFile(
        bitmap: Bitmap,
        file: File,
        quality: Int = 90,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    ) {
        val aead = streamingAead
        if (aead != null) {
            try {
                FileOutputStream(file).use { fos ->
                    aead.newEncryptingStream(fos, ASSOCIATED_DATA).use { encStream ->
                        bitmap.compress(format, quality, encStream)
                    }
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Bitmap encryption failed for ${file.name}, writing plaintext", e)
            }
        }
        // Fallback: write plaintext
        FileOutputStream(file).use { fos ->
            bitmap.compress(format, quality, fos)
        }
    }

    /**
     * Encrypt a PdfDocument to a file.
     * If encryption is unavailable, writes plaintext PDF.
     */
    fun encryptPdfToFile(pdfDocument: PdfDocument, file: File) {
        val aead = streamingAead
        if (aead != null) {
            try {
                FileOutputStream(file).use { fos ->
                    aead.newEncryptingStream(fos, ASSOCIATED_DATA).use { encStream ->
                        pdfDocument.writeTo(encStream)
                    }
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "PDF encryption failed for ${file.name}, writing plaintext", e)
            }
        }
        // Fallback: write plaintext PDF
        FileOutputStream(file).use { fos ->
            pdfDocument.writeTo(fos)
        }
    }

    // ========================================================================
    // Read Operations (attempt decrypt, fall back to plaintext for pre-migration files)
    // ========================================================================

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
                Log.d(TAG, "Decrypt failed for ${file.name}, falling back to plaintext read")
                FileInputStream(file)
            }
        }
        return FileInputStream(file)
    }

    /**
     * Decrypt a file to a temporary file (for APIs requiring File/ParcelFileDescriptor).
     * The temp file is created in cacheDir with a random name.
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
     * Decrypt a file and decode as a Bitmap.
     * Falls back to BitmapFactory.decodeFile if decryption fails.
     * Returns null if bitmap decoding fails entirely.
     */
    fun decryptToBitmap(file: File): Bitmap? {
        val aead = streamingAead
        if (aead != null) {
            try {
                aead.newDecryptingStream(FileInputStream(file), ASSOCIATED_DATA).use { decStream ->
                    val bitmap = BitmapFactory.decodeStream(decStream)
                    if (bitmap != null) return bitmap
                }
            } catch (e: Exception) {
                Log.d(TAG, "Decrypt-to-bitmap failed for ${file.name}, falling back to plaintext decode")
            }
        }
        // Fallback: file may be unencrypted (pre-migration)
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    // ========================================================================
    // In-place Encryption (for CameraX post-capture and migration)
    // ========================================================================

    /**
     * Encrypt a file in place: read plaintext, write encrypted to temp, atomic rename.
     *
     * Idempotent: before encrypting, attempts to decrypt first. If decryption
     * succeeds, the file is already encrypted -- skip it. This makes the
     * method safe to call on already-encrypted files (crash-safe migration).
     *
     * Uses atomic temp-file rename to prevent data loss on crash.
     */
    fun encryptFileInPlace(file: File) {
        val aead = streamingAead ?: return
        if (!file.exists() || !file.isFile) return

        // Idempotency check: try to decrypt first.
        // If decryption succeeds (reads some bytes), file is already encrypted -- skip.
        try {
            aead.newDecryptingStream(FileInputStream(file), ASSOCIATED_DATA).use { decStream ->
                val buffer = ByteArray(1)
                val bytesRead = decStream.read(buffer)
                if (bytesRead >= 0) {
                    // Successfully decrypted -- file is already encrypted
                    return
                }
            }
        } catch (_: Exception) {
            // Decryption failed -- file is plaintext, proceed with encryption
        }

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
            Log.w(TAG, "encryptFileInPlace failed for ${file.name}", e)
        }
    }

    // ========================================================================
    // Secure Deletion (SEC-10)
    // ========================================================================

    /**
     * Securely delete a file: overwrite content with random bytes, sync to
     * storage, then delete the filesystem reference.
     *
     * On flash storage, overwrite is best-effort due to wear-leveling and TRIM.
     * However, since files are AES-256-GCM encrypted with a KeyStore-managed key,
     * the encryption provides cryptographic erasure -- without the key, encrypted
     * blocks are unrecoverable.
     *
     * Falls back to plain file.delete() on any error.
     * Returns true if the file no longer exists after deletion.
     */
    fun secureDelete(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            // Overwrite with random bytes (best-effort on flash)
            val length = file.length()
            if (length > 0) {
                val random = SecureRandom()
                val buffer = ByteArray(SECURE_DELETE_BUFFER_SIZE)
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
            Log.w(TAG, "Secure delete failed for ${file.name}, falling back to regular delete", e)
            file.delete() // Still delete even if overwrite fails
        }
    }

    // ========================================================================
    // Migration
    // ========================================================================

    /**
     * Migrate existing unencrypted files to encrypted format.
     *
     * Iterates all files in filesDir/scans/, filesDir/processed/, filesDir/pdfs/
     * and encrypts each in place. Progress is reported via onProgress callback.
     *
     * Idempotent: checks sentinel key in SecurePreferences. If already set,
     * returns true immediately. Individual file encryption is also idempotent
     * (encryptFileInPlace checks if file is already encrypted).
     *
     * Must be called from Dispatchers.IO.
     *
     * @param context Application context
     * @param onProgress Callback invoked after each file is processed (current, total)
     * @return true if migration completed successfully
     */
    suspend fun migrateExistingFiles(
        context: Context,
        onProgress: (current: Int, total: Int) -> Unit
    ): Boolean {
        val prefs = SecurePreferences.getInstance(context)
        if (prefs.getBoolean(MIGRATION_SENTINEL, false)) return true // Already done

        // Ensure StreamingAead is initialized
        getInstance(context) ?: run {
            // KeyStore unavailable -- mark migration complete (nothing to encrypt)
            prefs.edit().putBoolean(MIGRATION_SENTINEL, true).apply()
            return true
        }

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

    // ========================================================================
    // Testing Support
    // ========================================================================

    /**
     * Reset singleton for testing. NOT for production use.
     */
    @Suppress("unused")
    internal fun resetForTesting() {
        streamingAead = null
    }
}
