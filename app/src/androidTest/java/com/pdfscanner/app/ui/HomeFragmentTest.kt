package com.pdfscanner.app.ui

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.pdfscanner.app.R
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for HomeFragment.
 *
 * Verifies that HomeFragment inflates its layout without crashing when launched
 * via FragmentScenario. Does NOT test camera functionality.
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 * (Requires a connected device or emulator.)
 */
@RunWith(AndroidJUnit4::class)
class HomeFragmentTest {

    @Test
    fun homeFragment_launchesWithoutCrash() {
        // Theme_PDFScanner.Cartoon is the app theme declared in AndroidManifest.xml.
        // Passing it here prevents Material3 views from throwing InflateException.
        launchFragmentInContainer<HomeFragment>(
            themeResId = R.style.Theme_PDFScanner_Cartoon
        ).use { scenario ->
            scenario.onFragment { fragment ->
                // If layout inflation failed the fragment would not reach RESUMED state.
                assertThat(fragment.isResumed).isTrue()
            }
        }
    }
}
