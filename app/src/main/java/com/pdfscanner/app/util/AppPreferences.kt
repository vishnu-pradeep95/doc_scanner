/**
 * AppPreferences.kt - SharedPreferences wrapper for app settings
 * 
 * Stores user preferences like theme mode and default filter
 */

package com.pdfscanner.app.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

/**
 * AppPreferences - Utility class for managing app preferences
 */
class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "pdf_scanner_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DEFAULT_FILTER = "default_filter"
    }

    // ============================================================
    // THEME MODE
    // ============================================================

    /**
     * Get the current theme mode
     */
    fun getThemeMode(): Int {
        return prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    /**
     * Set the theme mode
     */
    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    // ============================================================
    // DEFAULT FILTER
    // ============================================================

    /**
     * Get the default filter index
     * 0 = Original, 1 = Enhanced, 2 = B&W, 3 = Magic, 4 = Sharpen
     */
    fun getDefaultFilterIndex(): Int {
        return prefs.getInt(KEY_DEFAULT_FILTER, 3)  // Default to Magic
    }

    /**
     * Set the default filter
     */
    fun setDefaultFilter(index: Int) {
        prefs.edit().putInt(KEY_DEFAULT_FILTER, index).apply()
    }

    /**
     * Get filter name for display
     */
    fun getDefaultFilterName(): String {
        return when (getDefaultFilterIndex()) {
            0 -> "Original"
            1 -> "Enhanced"
            2 -> "B&W"
            3 -> "Magic"
            4 -> "Sharpen"
            else -> "Magic"
        }
    }

    /**
     * Get the filter type enum value
     */
    fun getDefaultFilterType(): ImageProcessor.FilterType {
        return when (getDefaultFilterIndex()) {
            0 -> ImageProcessor.FilterType.ORIGINAL
            1 -> ImageProcessor.FilterType.ENHANCED
            2 -> ImageProcessor.FilterType.DOCUMENT_BW
            3 -> ImageProcessor.FilterType.MAGIC
            4 -> ImageProcessor.FilterType.SHARPEN
            else -> ImageProcessor.FilterType.MAGIC
        }
    }
}
