package com.pdfscanner.app.navigation

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.ViewModelStore
import androidx.navigation.Navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.pdfscanner.app.R
import com.pdfscanner.app.ui.HomeFragment
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation flow test: HomeFragment -> CameraFragment
 *
 * Uses TestNavHostController to intercept navigation calls so that
 * CameraFragment is never actually launched (it requires real camera hardware).
 * This test only verifies that clicking the "New Scan" card fires the correct
 * navigation action and moves to the cameraFragment destination.
 *
 * View ID confirmed from fragment_home.xml: R.id.cardNewScan (MaterialCardView)
 * Destination ID confirmed from nav_graph.xml:  R.id.cameraFragment
 *
 * Run with: ./gradlew connectedDebugAndroidTest
 * (Requires a connected device or emulator.)
 */
@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {

    @Test
    fun homeFragment_newScanCard_navigatesToCamera() {
        val navController = TestNavHostController(
            ApplicationProvider.getApplicationContext()
        )

        launchFragmentInContainer<HomeFragment>(
            themeResId = R.style.Theme_PDFScanner_Cartoon
        ).use { scenario ->
            scenario.onFragment { fragment ->
                // Wire TestNavHostController BEFORE any click so the fragment's
                // findNavController() returns our test controller.
                navController.setViewModelStore(ViewModelStore())
                navController.setGraph(R.navigation.nav_graph)
                Navigation.setViewNavController(fragment.requireView(), navController)
            }

            // Click the "New Scan" card (R.id.cardNewScan in fragment_home.xml).
            // HomeFragment.setupQuickActions() sets cardNewScan.setOnClickListener {
            //     findNavController().navigate(R.id.action_home_to_camera)
            // }
            onView(withId(R.id.cardNewScan)).perform(click())

            // Assert that navigation moved to cameraFragment destination.
            assertThat(navController.currentDestination?.id).isEqualTo(R.id.cameraFragment)
        }
    }
}
