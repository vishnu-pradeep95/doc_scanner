package com.pdfscanner.app.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager

/**
 * AppLockManager — Centralized lock state management for biometric/PIN app lock.
 *
 * Encapsulates all lock-related logic: enable/disable state, timeout configuration,
 * background timestamp tracking, authentication requirement checks, and API-tiered
 * authenticator selection.
 *
 * All state is persisted in SecurePreferences with APP_PREFIX keys to survive
 * process death and prevent tampering.
 *
 * API-level split for authenticators:
 * - API 30+ (R): BIOMETRIC_STRONG | DEVICE_CREDENTIAL
 * - API < 30:    BIOMETRIC_WEAK | DEVICE_CREDENTIAL
 * This prevents IllegalArgumentException on API 28-29 where BIOMETRIC_STRONG|DEVICE_CREDENTIAL
 * is unsupported.
 */
object AppLockManager {

    private const val KEY_LOCK_ENABLED = "${SecurePreferences.APP_PREFIX}lock_enabled"
    private const val KEY_LOCK_TIMEOUT = "${SecurePreferences.APP_PREFIX}lock_timeout"
    private const val KEY_LAST_BACKGROUND_TIME = "${SecurePreferences.APP_PREFIX}last_background_time"

    /** Re-lock immediately when app goes to background. */
    const val TIMEOUT_IMMEDIATE = 0L

    /** Re-lock after 30 seconds in background. */
    const val TIMEOUT_30_SECONDS = 30_000L

    /** Re-lock after 1 minute in background. */
    const val TIMEOUT_1_MINUTE = 60_000L

    /** Re-lock after 5 minutes in background. */
    const val TIMEOUT_5_MINUTES = 300_000L

    /**
     * Whether the user has enabled app lock.
     */
    fun isLockEnabled(context: Context): Boolean {
        return SecurePreferences.getInstance(context)
            .getBoolean(KEY_LOCK_ENABLED, false)
    }

    /**
     * Enable or disable app lock.
     */
    fun setLockEnabled(context: Context, enabled: Boolean) {
        SecurePreferences.getInstance(context).edit()
            .putBoolean(KEY_LOCK_ENABLED, enabled)
            .apply()
    }

    /**
     * Get the configured auto-lock timeout in milliseconds.
     * Default: TIMEOUT_IMMEDIATE (re-lock immediately on background).
     */
    fun getTimeout(context: Context): Long {
        return SecurePreferences.getInstance(context)
            .getLong(KEY_LOCK_TIMEOUT, TIMEOUT_IMMEDIATE)
    }

    /**
     * Set the auto-lock timeout in milliseconds.
     */
    fun setTimeout(context: Context, timeoutMs: Long) {
        SecurePreferences.getInstance(context).edit()
            .putLong(KEY_LOCK_TIMEOUT, timeoutMs)
            .apply()
    }

    /**
     * Record the current time as the moment the app went to background.
     * Called from ProcessLifecycleOwner.onStop().
     */
    fun recordBackgroundTime(context: Context) {
        SecurePreferences.getInstance(context).edit()
            .putLong(KEY_LAST_BACKGROUND_TIME, System.currentTimeMillis())
            .apply()
    }

    /**
     * Determine whether authentication should be required on resume.
     *
     * Returns false if lock is not enabled.
     * Returns true on first launch with lock enabled (no recorded background time).
     * Otherwise, returns true if elapsed time since background exceeds the configured timeout.
     */
    fun shouldRequireAuth(context: Context): Boolean {
        if (!isLockEnabled(context)) return false
        val lastBackground = SecurePreferences.getInstance(context)
            .getLong(KEY_LAST_BACKGROUND_TIME, 0L)
        if (lastBackground == 0L) return true // First launch with lock enabled
        val timeout = getTimeout(context)
        return System.currentTimeMillis() - lastBackground >= timeout
    }

    /**
     * Check if the device supports any form of authentication (biometric or device credential).
     *
     * Returns true if BiometricManager reports BIOMETRIC_SUCCESS (hardware + enrollment present)
     * or BIOMETRIC_ERROR_NONE_ENROLLED (hardware present but no enrollment — user can set up).
     */
    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = getAllowedAuthenticators()
        val result = biometricManager.canAuthenticate(authenticators)
        return result == BiometricManager.BIOMETRIC_SUCCESS ||
            result == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    /**
     * Returns the correct authenticator flags for the current API level.
     *
     * API 30+ (R): BIOMETRIC_STRONG | DEVICE_CREDENTIAL — full support for Class 3 biometric + PIN fallback.
     * API < 30:    BIOMETRIC_WEAK | DEVICE_CREDENTIAL — avoids IllegalArgumentException on API 28-29
     *              where BIOMETRIC_STRONG | DEVICE_CREDENTIAL is unsupported. On API 24-27, the library
     *              internally delegates to KeyguardManager.
     */
    fun getAllowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    /**
     * Clear the last-background timestamp. Called when disabling lock to reset state.
     */
    fun clearAuthState(context: Context) {
        SecurePreferences.getInstance(context).edit()
            .remove(KEY_LAST_BACKGROUND_TIME)
            .apply()
    }
}
