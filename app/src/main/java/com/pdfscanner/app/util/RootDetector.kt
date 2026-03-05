package com.pdfscanner.app.util

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

/**
 * RootDetector -- Lightweight heuristic checks for rooted or debuggable devices.
 *
 * SEC-14: Warn-only. NEVER call finish() or disable functionality based on
 * these results. The FLAG_DEBUGGABLE check always returns true in debug builds;
 * the caller (MainActivity) guards with !BuildConfig.DEBUG to prevent false
 * positives during development.
 */
object RootDetector {

    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/vendor/bin/su"
    )

    /**
     * Returns true if the device appears rooted or is running a debuggable build.
     * Any single positive heuristic is enough to trigger a warning.
     */
    fun isDeviceCompromised(context: Context): Boolean {
        return checkTestKeys() || checkSuBinary() || checkDebuggable(context)
    }

    /**
     * Check if the system image was signed with test keys (common on custom ROMs).
     */
    private fun checkTestKeys(): Boolean {
        return android.os.Build.TAGS?.contains("test-keys") == true
    }

    /**
     * Check for the existence of the su binary in common filesystem paths.
     */
    private fun checkSuBinary(): Boolean {
        return SU_PATHS.any { File(it).exists() }
    }

    /**
     * Check if the running application has the debuggable flag set.
     */
    private fun checkDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
