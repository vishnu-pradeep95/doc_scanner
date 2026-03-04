package com.pdfscanner.app.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import com.pdfscanner.app.util.AppPreferences
import com.pdfscanner.app.util.ImageProcessor
import com.pdfscanner.app.util.SecurePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppPreferencesTest {

    private lateinit var context: Context
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Reset SecurePreferences singleton so each test gets a fresh instance
        SecurePreferences.resetForTesting()
        // Clear the fallback prefs (Robolectric has no real KeyStore, so fallback is used)
        context.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE)
            .edit().clear().commit()
        // Construct fresh instance — not a singleton, safe to create per test
        appPreferences = AppPreferences(context)
    }

    // ============================================================
    // Theme Mode
    // ============================================================

    @Test
    fun `getThemeMode returns MODE_NIGHT_FOLLOW_SYSTEM by default`() {
        val result = appPreferences.getThemeMode()
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, result)
    }

    @Test
    fun `setThemeMode then getThemeMode returns the set value`() {
        appPreferences.setThemeMode(AppCompatDelegate.MODE_NIGHT_YES)
        val result = appPreferences.getThemeMode()
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, result)
    }

    // ============================================================
    // App Style
    // ============================================================

    @Test
    fun `getAppStyle returns STYLE_GHIBLI by default`() {
        val result = appPreferences.getAppStyle()
        assertEquals(AppPreferences.STYLE_GHIBLI, result)
    }

    @Test
    fun `setAppStyle STYLE_CARTOON then getAppStyle returns STYLE_CARTOON`() {
        appPreferences.setAppStyle(AppPreferences.STYLE_CARTOON)
        val result = appPreferences.getAppStyle()
        assertEquals(AppPreferences.STYLE_CARTOON, result)
    }

    @Test
    fun `getAppStyleName returns Classic when style is STYLE_GHIBLI`() {
        // Default is STYLE_GHIBLI, no need to set
        val result = appPreferences.getAppStyleName()
        assertEquals("Classic", result)
    }

    @Test
    fun `getAppStyleName returns Cartoon when style is STYLE_CARTOON`() {
        appPreferences.setAppStyle(AppPreferences.STYLE_CARTOON)
        val result = appPreferences.getAppStyleName()
        assertEquals("Cartoon", result)
    }

    @Test
    fun `isCartoonStyle returns false by default and true after setAppStyle STYLE_CARTOON`() {
        assertFalse(appPreferences.isCartoonStyle())
        appPreferences.setAppStyle(AppPreferences.STYLE_CARTOON)
        assertTrue(appPreferences.isCartoonStyle())
    }

    // ============================================================
    // Default Filter
    // ============================================================

    @Test
    fun `getDefaultFilterIndex returns 3 by default`() {
        val result = appPreferences.getDefaultFilterIndex()
        assertEquals(3, result)
    }

    @Test
    fun `setDefaultFilter 0 then getDefaultFilterIndex returns 0 and getDefaultFilterName returns Original`() {
        appPreferences.setDefaultFilter(0)
        assertEquals(0, appPreferences.getDefaultFilterIndex())
        assertEquals("Original", appPreferences.getDefaultFilterName())
    }

    @Test
    fun `getDefaultFilterType returns FilterType MAGIC by default and FilterType ORIGINAL after setDefaultFilter 0`() {
        assertEquals(ImageProcessor.FilterType.MAGIC, appPreferences.getDefaultFilterType())
        appPreferences.setDefaultFilter(0)
        assertEquals(ImageProcessor.FilterType.ORIGINAL, appPreferences.getDefaultFilterType())
    }
}
