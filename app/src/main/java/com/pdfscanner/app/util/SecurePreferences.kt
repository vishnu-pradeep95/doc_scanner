package com.pdfscanner.app.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecurePreferences — Thread-safe singleton providing EncryptedSharedPreferences
 * with crash-safe migration from unencrypted prefs and KeyStore fallback.
 *
 * Merges both "document_history" and "pdf_scanner_prefs" into a single
 * encrypted file with prefix-namespaced keys (history_, app_).
 *
 * KeyStore fallback: 3 retries with exponential backoff. On persistent
 * failure (OEM KeyStore bugs on API 24-27), silently falls back to
 * unencrypted prefs. Retries on each app launch so encryption activates
 * if a system update fixes the KeyStore.
 */
object SecurePreferences {

    private const val TAG = "SecurePreferences"
    private const val ENCRYPTED_PREFS_NAME = "secure_prefs"
    private const val FALLBACK_PREFS_NAME = "secure_prefs_fallback"
    private const val SENTINEL_KEY = "_migration_complete"
    private const val MAX_RETRIES = 3
    private const val BASE_RETRY_DELAY_MS = 100L

    // Old prefs file names (sources for migration)
    private const val OLD_HISTORY_PREFS = "document_history"
    private const val OLD_APP_PREFS = "pdf_scanner_prefs"

    // Prefix namespacing for merged file
    const val HISTORY_PREFIX = "history_"
    const val APP_PREFIX = "app_"

    @Volatile
    private var instance: SharedPreferences? = null

    /**
     * Get the SharedPreferences instance (encrypted if KeyStore works, fallback otherwise).
     * First call triggers migration from unencrypted prefs if sentinel key absent.
     * Thread-safe via double-checked locking.
     */
    fun getInstance(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: createPreferences(context.applicationContext).also { prefs ->
                migrateIfNeeded(context.applicationContext, prefs)
                instance = prefs
            }
        }
    }

    private fun createPreferences(context: Context): SharedPreferences {
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
                Log.w(TAG, "KeyStore attempt ${attempt + 1}/$MAX_RETRIES failed", e)
                if (attempt < MAX_RETRIES - 1) {
                    Thread.sleep(BASE_RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        // Persistent failure: fall back to unencrypted prefs (silent — per user decision)
        Log.w(TAG, "KeyStore persistently failed after $MAX_RETRIES attempts, using unencrypted fallback")
        return context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Migrate data from both old unencrypted prefs files into the encrypted target.
     * Idempotent: sentinel key written in the SAME apply() as all data, so either
     * all data + sentinel persists or nothing does (crash-safe).
     * Old files are retained through v1.2 cycle (deleted in Phase 10 audit).
     */
    private fun migrateIfNeeded(context: Context, target: SharedPreferences) {
        if (target.getBoolean(SENTINEL_KEY, false)) return // Already migrated

        val oldHistory = context.getSharedPreferences(OLD_HISTORY_PREFS, Context.MODE_PRIVATE)
        val oldAppPrefs = context.getSharedPreferences(OLD_APP_PREFS, Context.MODE_PRIVATE)

        val editor = target.edit()

        // Copy document_history keys with history_ prefix
        oldHistory.all.forEach { (key, value) ->
            copyPrefValue(editor, "$HISTORY_PREFIX$key", value)
        }

        // Copy pdf_scanner_prefs keys with app_ prefix
        oldAppPrefs.all.forEach { (key, value) ->
            copyPrefValue(editor, "$APP_PREFIX$key", value)
        }

        // Sentinel in SAME transaction — crash-safe idempotency
        editor.putBoolean(SENTINEL_KEY, true)
        editor.apply()
    }

    /**
     * Copy a SharedPreferences value of any supported type.
     */
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

    /**
     * Reset singleton for testing. NOT for production use.
     */
    @Suppress("unused")
    internal fun resetForTesting() {
        instance = null
    }
}
