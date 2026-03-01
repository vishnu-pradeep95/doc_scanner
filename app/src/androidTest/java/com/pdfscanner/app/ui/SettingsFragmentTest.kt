package com.pdfscanner.app.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.pdfscanner.app.R
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for SettingsFragment.
 *
 * Verifies that SettingsFragment inflates its layout and reaches RESUMED state
 * without crashing.
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 * (Requires a connected device or emulator.)
 */
@RunWith(AndroidJUnit4::class)
class SettingsFragmentTest {

    @Test
    fun settingsFragment_launchesWithoutCrash() {
        launchFragmentInContainer<SettingsFragment>(
            themeResId = R.style.Theme_PDFScanner_Cartoon
        ).use { scenario ->
            scenario.onFragment { fragment ->
                assertThat(fragment.isResumed).isTrue()
            }
        }
    }
}
