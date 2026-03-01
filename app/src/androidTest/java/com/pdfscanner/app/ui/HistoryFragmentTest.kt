package com.pdfscanner.app.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.pdfscanner.app.R
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for HistoryFragment.
 *
 * Verifies that HistoryFragment inflates its layout without crashing when launched
 * with an empty document history (expected state on a fresh install).
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 * (Requires a connected device or emulator.)
 */
@RunWith(AndroidJUnit4::class)
class HistoryFragmentTest {

    @Test
    fun historyFragment_launchesWithoutCrash() {
        launchFragmentInContainer<HistoryFragment>(
            themeResId = R.style.Theme_PDFScanner_Cartoon
        ).use { scenario ->
            scenario.onFragment { fragment ->
                assertThat(fragment.isResumed).isTrue()
            }
        }
    }
}
