package com.pdfscanner.app.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.pdfscanner.app.R
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for PagesFragment.
 *
 * Verifies that PagesFragment inflates its layout without crashing when launched
 * with an empty page list in the ViewModel (the expected empty-state path).
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 * (Requires a connected device or emulator.)
 */
@RunWith(AndroidJUnit4::class)
class PagesFragmentTest {

    @Test
    fun pagesFragment_launchesWithoutCrash() {
        // No pages are in the ViewModel — fragment must handle the empty state gracefully.
        launchFragmentInContainer<PagesFragment>(
            themeResId = R.style.Theme_PDFScanner_Cartoon
        ).use { scenario ->
            scenario.onFragment { fragment ->
                assertThat(fragment.isResumed).isTrue()
            }
        }
    }
}
