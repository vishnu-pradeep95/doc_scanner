package com.pdfscanner.app.ui

import android.os.Bundle
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.pdfscanner.app.R
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for PreviewFragment.
 *
 * PreviewFragment is a navigation destination that requires an `imageUri` String
 * argument (defined in nav_graph.xml). We supply a dummy file URI so that the
 * fragment can reach RESUMED state without crashing on a null argument.
 *
 * The fragment may show an error state or empty preview for a non-existent file —
 * that is acceptable. This test only verifies that layout inflation succeeds.
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 * (Requires a connected device or emulator.)
 */
@RunWith(AndroidJUnit4::class)
class PreviewFragmentTest {

    @Test
    fun previewFragment_launchesWithoutCrash() {
        // PreviewFragmentArgs requires "imageUri" (String) and "editIndex" (Int, default -1).
        // Supply a dummy URI string so navArgs() does not throw a missing-argument exception.
        val args = Bundle().apply {
            putString("imageUri", "file:///dev/null")
            putInt("editIndex", -1)
        }

        launchFragmentInContainer<PreviewFragment>(
            fragmentArgs = args,
            themeResId = R.style.Theme_PDFScanner_Cartoon
        ).use { scenario ->
            scenario.onFragment { fragment ->
                // Fragment reached RESUMED state — layout inflation succeeded.
                assertThat(fragment.isResumed).isTrue()
            }
        }
    }
}
