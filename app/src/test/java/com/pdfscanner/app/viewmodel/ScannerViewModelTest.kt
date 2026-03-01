package com.pdfscanner.app.viewmodel

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.pdfscanner.app.util.ImageProcessor
import io.mockk.clearAllMocks
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Unit tests for ScannerViewModel.
 *
 * Runs on the JVM — no device, no emulator required.
 *
 * SavedStateHandle is constructed directly (no mocking needed for JVM).
 * InstantTaskExecutorRule makes LiveData updates synchronous.
 * MainDispatcherRule replaces Dispatchers.Main with UnconfinedTestDispatcher.
 */
@RunWith(JUnit4::class)
class ScannerViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: ScannerViewModel

    // Uri.parse is an Android API, not available on plain JVM — use mockk stubs instead.
    // The ViewModel stores URIs by reference/equality, so distinct mock instances are sufficient.
    private fun uri(name: String): Uri = mockk(name = name)

    // pageFilters is a derived (transformed) LiveData — it must be observed to activate.
    // Without an active observer, Transformations.map chains remain inactive and value is null.
    private val pageFiltersObserver = Observer<Map<Int, ImageProcessor.FilterType>> { }

    @Before
    fun setup() {
        viewModel = ScannerViewModel(SavedStateHandle())
        // Activate the derived pageFilters LiveData so .value is populated synchronously
        viewModel.pageFilters.observeForever(pageFiltersObserver)
    }

    @After
    fun teardown() {
        viewModel.pageFilters.removeObserver(pageFiltersObserver)
        clearAllMocks()
    }

    // ============================================================
    // PAGE CRUD TESTS
    // ============================================================

    @Test
    fun `addPage increases page count by one`() = runTest {
        val page = uri("page1")
        viewModel.addPage(page)
        assertThat(viewModel.pages.value).hasSize(1)
    }

    @Test
    fun `addPage stores the added URI as the last element`() = runTest {
        val page = uri("page1")
        viewModel.addPage(page)
        assertThat(viewModel.pages.value?.last()).isEqualTo(page)
    }

    @Test
    fun `addPage twice results in size 2 with correct order`() = runTest {
        val first = uri("first")
        val second = uri("second")
        viewModel.addPage(first)
        viewModel.addPage(second)
        val pages = viewModel.pages.value!!
        assertThat(pages).hasSize(2)
        assertThat(pages[0]).isEqualTo(first)
        assertThat(pages[1]).isEqualTo(second)
    }

    @Test
    fun `removePage at index 0 decreases size by one`() = runTest {
        viewModel.addPage(uri("page1"))
        viewModel.addPage(uri("page2"))
        viewModel.removePage(0)
        assertThat(viewModel.pages.value).hasSize(1)
    }

    @Test
    fun `removePage at index 0 leaves the remaining element correct`() = runTest {
        val remaining = uri("remaining")
        viewModel.addPage(uri("to-remove"))
        viewModel.addPage(remaining)
        viewModel.removePage(0)
        assertThat(viewModel.pages.value?.get(0)).isEqualTo(remaining)
    }

    @Test
    fun `removePage on out-of-range index leaves pages unchanged`() = runTest {
        viewModel.addPage(uri("page1"))
        viewModel.removePage(5)
        assertThat(viewModel.pages.value).hasSize(1)
    }

    @Test
    fun `movePage swaps elements at positions 0 and 1`() = runTest {
        val first = uri("first")
        val second = uri("second")
        viewModel.addPage(first)
        viewModel.addPage(second)
        viewModel.movePage(0, 1)
        val pages = viewModel.pages.value!!
        assertThat(pages[0]).isEqualTo(second)
        assertThat(pages[1]).isEqualTo(first)
    }

    @Test
    fun `movePage with out-of-range index leaves pages unchanged`() = runTest {
        viewModel.addPage(uri("page1"))
        viewModel.movePage(0, 10)
        assertThat(viewModel.pages.value).hasSize(1)
    }

    @Test
    fun `insertPage at position 0 inserts URI at front and shifts existing URIs right`() = runTest {
        val existing = uri("existing")
        val inserted = uri("inserted")
        viewModel.addPage(existing)
        viewModel.insertPage(0, inserted)
        val pages = viewModel.pages.value!!
        assertThat(pages[0]).isEqualTo(inserted)
        assertThat(pages[1]).isEqualTo(existing)
    }

    @Test
    fun `insertPages with two entries inserts both URIs at correct positions`() = runTest {
        // Starting with one page; inserting two pages at positions 0 and 1
        val base = uri("base")
        val ins0 = uri("ins0")
        val ins1 = uri("ins1")
        viewModel.addPage(base)
        viewModel.insertPages(listOf(0 to ins0, 1 to ins1))
        val pages = viewModel.pages.value!!
        assertThat(pages).hasSize(3)
        // ins0 goes to 0, ins1 goes to 1, base shifts to 2
        assertThat(pages[0]).isEqualTo(ins0)
        assertThat(pages[1]).isEqualTo(ins1)
    }

    @Test
    fun `updatePage at index 0 replaces URI and keeps size unchanged`() = runTest {
        val original = uri("original")
        val updated = uri("updated")
        viewModel.addPage(original)
        viewModel.updatePage(0, updated)
        val pages = viewModel.pages.value!!
        assertThat(pages).hasSize(1)
        assertThat(pages[0]).isEqualTo(updated)
    }

    @Test
    fun `clearAllPages empties pages list`() = runTest {
        viewModel.addPage(uri("page1"))
        viewModel.addPage(uri("page2"))
        viewModel.clearAllPages()
        assertThat(viewModel.pages.value).isEmpty()
    }

    @Test
    fun `clearAllPages empties pageFilters`() = runTest {
        viewModel.addPage(uri("page1"))
        viewModel.setPageFilter(0, ImageProcessor.FilterType.ENHANCED)
        viewModel.clearAllPages()
        assertThat(viewModel.pageFilters.value).isEmpty()
    }

    @Test
    fun `clearAllPages sets pdfUri to null`() = runTest {
        viewModel.setPdfUri(uri("some-pdf"))
        viewModel.clearAllPages()
        assertThat(viewModel.pdfUri.value).isNull()
    }

    // ============================================================
    // FILTER STATE TESTS
    // ============================================================

    @Test
    fun `setPageFilter stores ENHANCED for page index 0`() = runTest {
        viewModel.addPage(uri("page1"))
        viewModel.setPageFilter(0, ImageProcessor.FilterType.ENHANCED)
        assertThat(viewModel.pageFilters.value?.get(0)).isEqualTo(ImageProcessor.FilterType.ENHANCED)
    }

    @Test
    fun `setPageFilter overwrites previous filter for same index`() = runTest {
        viewModel.addPage(uri("page1"))
        viewModel.setPageFilter(0, ImageProcessor.FilterType.ENHANCED)
        viewModel.setPageFilter(0, ImageProcessor.FilterType.MAGIC)
        assertThat(viewModel.pageFilters.value?.get(0)).isEqualTo(ImageProcessor.FilterType.MAGIC)
    }

    @Test
    fun `getPageFilter for unset index returns ORIGINAL as default`() = runTest {
        assertThat(viewModel.getPageFilter(99)).isEqualTo(ImageProcessor.FilterType.ORIGINAL)
    }

    @Test
    fun `setPageFilter with DOCUMENT_BW round-trips correctly through SavedStateHandle`() = runTest {
        viewModel.addPage(uri("page1"))
        viewModel.setPageFilter(0, ImageProcessor.FilterType.DOCUMENT_BW)
        val retrieved = viewModel.getPageFilter(0)
        assertThat(retrieved).isEqualTo(ImageProcessor.FilterType.DOCUMENT_BW)
    }

    // ============================================================
    // PDF NAMING TESTS
    // ============================================================

    @Test
    fun `getPdfFileName with baseName set returns baseName followed by timestamp`() = runTest {
        viewModel.setPdfBaseName("Meeting")
        val fileName = viewModel.getPdfFileName("20251226_123456")
        assertThat(fileName).isEqualTo("Meeting_20251226_123456.pdf")
    }

    @Test
    fun `getPdfFileName with baseName null returns Scan prefix with timestamp`() = runTest {
        viewModel.setPdfBaseName(null)
        val fileName = viewModel.getPdfFileName("20251226_123456")
        assertThat(fileName).isEqualTo("Scan_20251226_123456.pdf")
    }

    @Test
    fun `getPdfFileName with baseName empty string returns Scan prefix with timestamp`() = runTest {
        viewModel.setPdfBaseName("")
        val fileName = viewModel.getPdfFileName("20251226_123456")
        assertThat(fileName).isEqualTo("Scan_20251226_123456.pdf")
    }

    @Test
    fun `setPdfBaseName with whitespace-only string stores null`() = runTest {
        viewModel.setPdfBaseName("   ")
        // pdfBaseName LiveData backed by SavedStateHandle — after whitespace trim it is empty -> stored as null
        // getPdfFileName should fall back to "Scan" prefix
        val fileName = viewModel.getPdfFileName("ts")
        assertThat(fileName).isEqualTo("Scan_ts.pdf")
    }
}
