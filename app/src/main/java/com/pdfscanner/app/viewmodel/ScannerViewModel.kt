/**
 * ScannerViewModel.kt - Shared Data Holder for the App
 *
 * ANDROID CONCEPT: ViewModel
 * ==========================
 * A ViewModel survives configuration changes like screen rotation.
 *
 * THE PROBLEM IT SOLVES:
 * When you rotate your phone, Android destroys and recreates the Activity/Fragment.
 * Without ViewModel, all your variables would be reset!
 *
 * ANALOGY:
 * Think of ViewModel as a "safe" where you store important data.
 * Even if your room (Fragment) gets demolished and rebuilt,
 * the safe (ViewModel) stays intact.
 *
 * LIFECYCLE:
 * - Created when first Fragment requests it
 * - Survives Fragment destruction/recreation
 * - Only destroyed when the Activity is truly finished (not just rotated)
 *
 * ANDROID CONCEPT: LiveData
 * =========================
 * LiveData is an "observable" data holder - it notifies observers when data changes.
 *
 * PATTERN:
 * 1. ViewModel holds LiveData
 * 2. Fragment observes LiveData
 * 3. When data changes, Fragment's observer callback runs automatically
 *
 * This is the "Observer Pattern" - common in reactive programming.
 * Similar to signals/slots in Qt, or pub/sub messaging.
 *
 * ANDROID CONCEPT: SavedStateHandle
 * ==================================
 * SavedStateHandle persists ViewModel state across process death.
 * When Android kills the app to reclaim memory (while it is in the background),
 * any plain ViewModel fields are lost. SavedStateHandle stores data in the
 * Activity's saved instance state Bundle, so it survives process death and
 * is restored when the user returns to the app.
 *
 * Only Bundle-safe types can be stored: primitives, Strings, Parcelables,
 * and lists/maps of those types. Uri implements Parcelable, so List<Uri>
 * works. Map<Int, String> contains only primitives, so it is Bundle-safe.
 */

package com.pdfscanner.app.viewmodel

import android.net.Uri  // URI = Uniform Resource Identifier, points to files/resources
import androidx.lifecycle.LiveData  // Read-only observable data
import androidx.lifecycle.MutableLiveData  // Read-write observable data
import androidx.lifecycle.SavedStateHandle  // Process-death-safe state store
import androidx.lifecycle.ViewModel  // Base class for ViewModels
import androidx.lifecycle.map  // LiveData transformation extension
import com.pdfscanner.app.util.ImageProcessor  // Filter utilities

/**
 * ScannerViewModel - Manages scanned pages across all Fragments
 *
 * This ViewModel is "shared" - multiple Fragments access the same instance
 * using 'by activityViewModels()' in each Fragment.
 *
 * CONSTRUCTOR:
 * SavedStateHandle is injected automatically by the AndroidX ViewModel factory
 * when the constructor takes it as a parameter. No custom factory is needed.
 * 'by activityViewModels()' handles this via SavedStateViewModelFactory.
 *
 * DATA STORED:
 * - List of scanned page URIs (file paths) -- saved to SavedStateHandle
 * - Page filter map -- saved to SavedStateHandle
 * - PDF base name -- saved to SavedStateHandle
 * - Current capture URI -- transient session state (NOT persisted)
 * - Generated PDF URI -- transient session state (NOT persisted)
 * - Loading state -- transient session state (NOT persisted)
 */
class ScannerViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    companion object {
        private const val KEY_PAGES = "pages"
        private const val KEY_PAGE_FILTERS = "page_filters"
        private const val KEY_PDF_BASE_NAME = "pdf_base_name"
    }

    // ============================================================
    // SCANNED PAGES LIST (persisted via SavedStateHandle)
    // ============================================================

    /**
     * Immutable list of page URIs, backed by SavedStateHandle.
     *
     * TYPE CHANGE: was LiveData<MutableList<Uri>>, now LiveData<List<Uri>>.
     * This is intentional -- the immutable type prevents callers from mutating
     * the list in-place (which would bypass LiveData observers). All mutations
     * must go through addPage(), removePage(), etc., which always create a new
     * List instance before storing it back into savedStateHandle.
     *
     * PROCESS DEATH: Uri implements Parcelable, so List<Uri> is Bundle-safe and
     * survives process death. The list is restored automatically when the user
     * returns to the app after Android has killed it.
     *
     * DIFFUTIL: Because every mutation creates a NEW list reference, ListAdapter's
     * DiffUtil will always receive a new object and perform its diff correctly.
     */
    val pages: LiveData<List<Uri>> = savedStateHandle.getLiveData(KEY_PAGES, emptyList())

    // ============================================================
    // CURRENT CAPTURE (transient -- NOT persisted)
    // ============================================================

    /**
     * Holds the URI of the image just captured by camera.
     *
     * NOT persisted: Camera captures are session-specific. After process death
     * the camera capture flow must restart from scratch anyway, so there is
     * no value in restoring this state.
     *
     * FLOW:
     * 1. CameraFragment captures image -> sets currentCaptureUri
     * 2. PreviewFragment shows it for editing
     * 3. User clicks "Add Page" -> moves to pages list
     * 4. currentCaptureUri is cleared
     *
     * Nullable (Uri?) because there might be no current capture.
     */
    private val _currentCaptureUri = MutableLiveData<Uri?>()
    val currentCaptureUri: LiveData<Uri?> = _currentCaptureUri

    // ============================================================
    // GENERATED PDF (transient -- NOT persisted)
    // ============================================================

    /**
     * URI of the generated PDF file (after "Create PDF" is clicked).
     *
     * NOT persisted: The PDF URI must be regenerated each session because
     * the underlying file may be in a cache location that is cleared between
     * sessions.
     *
     * Null until PDF is generated.
     */
    private val _pdfUri = MutableLiveData<Uri?>()
    val pdfUri: LiveData<Uri?> = _pdfUri

    // ============================================================
    // LOADING STATE (transient -- NOT persisted)
    // ============================================================

    /**
     * Boolean flag for showing/hiding loading indicators.
     *
     * NOT persisted: Loading state is always false at the start of a new session.
     * If the process was killed during a load operation, that operation is gone
     * and the UI should start in a non-loading state.
     */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // ============================================================
    // FILTER STATE (persisted via SavedStateHandle)
    // ============================================================

    /**
     * Raw filter map stored in SavedStateHandle as Map<Int, String>.
     *
     * We store the enum NAME (String) rather than the enum value because
     * FilterType is not Parcelable and cannot be stored in a Bundle directly.
     * The name string is human-readable and stable across app versions as long
     * as enum names are not renamed.
     *
     * Key   = page index in the pages list
     * Value = FilterType.name (e.g. "ORIGINAL", "ENHANCED")
     */
    private val _rawPageFilters: LiveData<Map<Int, String>> =
        savedStateHandle.getLiveData(KEY_PAGE_FILTERS, emptyMap())

    /**
     * Public page filter map, exposed as Map<Int, FilterType>.
     *
     * Transforms the raw Map<Int, String> from SavedStateHandle into the
     * typed Map<Int, FilterType> that the rest of the app uses.
     * This is a derived LiveData that updates automatically whenever the
     * underlying raw map changes.
     */
    val pageFilters: LiveData<Map<Int, ImageProcessor.FilterType>> = _rawPageFilters.map { raw ->
        raw.mapValues { (_, name) -> ImageProcessor.FilterType.valueOf(name) }
    }

    /**
     * Custom PDF base name, persisted via SavedStateHandle.
     *
     * If null or empty, a default timestamp-based name will be used.
     * Example: "Meeting Notes" -> "Meeting Notes_20251226_123456.pdf"
     */
    val pdfBaseName: LiveData<String?> = savedStateHandle.getLiveData(KEY_PDF_BASE_NAME)

    // ============================================================
    // PUBLIC METHODS (how Fragments modify data)
    // ============================================================

    /**
     * Store the URI of a newly captured image.
     *
     * Called by CameraFragment after successful capture.
     *
     * @param uri File URI of the captured JPEG image
     */
    fun setCurrentCapture(uri: Uri) {
        // .value = synchronous update (must be on main thread)
        // .postValue() = asynchronous update (safe from any thread)
        _currentCaptureUri.value = uri
    }

    /**
     * Add a page to the scanned pages list.
     *
     * Creates a NEW list by appending the URI to the existing list.
     * The new list reference triggers LiveData observers and DiffUtil correctly.
     *
     * Called by PreviewFragment when user clicks "Add Page".
     *
     * @param uri File URI of the (possibly edited) page image
     */
    fun addPage(uri: Uri) {
        savedStateHandle[KEY_PAGES] = savedStateHandle.get<List<Uri>>(KEY_PAGES).orEmpty() + uri
    }

    /**
     * Update a page at a specific position (after cropping).
     *
     * Creates a NEW list with the element at [index] replaced by [newUri].
     *
     * @param index Position in the list (0-based)
     * @param newUri New file URI after editing
     */
    fun updatePage(index: Int, newUri: Uri) {
        val current = savedStateHandle.get<List<Uri>>(KEY_PAGES).orEmpty()
        if (index in current.indices) {
            savedStateHandle[KEY_PAGES] = current.toMutableList().also { it[index] = newUri }.toList()
        }
    }

    /**
     * Remove a page from the list.
     *
     * Creates a NEW list without the element at [index].
     *
     * Called when user clicks delete button on a page thumbnail.
     *
     * @param index Position to remove (0-based)
     */
    fun removePage(index: Int) {
        val current = savedStateHandle.get<List<Uri>>(KEY_PAGES).orEmpty()
        if (index in current.indices) {
            savedStateHandle[KEY_PAGES] = current.toMutableList().also { it.removeAt(index) }.toList()
        }
    }

    /**
     * Insert a page at a specific position (used by Snackbar Undo after single-page delete).
     *
     * Creates a NEW list with the URI inserted at [position].
     * If [position] is out of range, it is clamped to [0, size].
     *
     * @param position Index to insert at (0-based)
     * @param uri File URI of the page to restore
     */
    fun insertPage(position: Int, uri: Uri) {
        val current = savedStateHandle.get<List<Uri>>(KEY_PAGES).orEmpty().toMutableList()
        val clampedPosition = position.coerceIn(0, current.size)
        current.add(clampedPosition, uri)
        savedStateHandle[KEY_PAGES] = current.toList()
    }

    /**
     * Insert multiple pages at their original positions (used by Snackbar Undo after bulk delete).
     *
     * Entries must be sorted ASCENDING by position before calling this function —
     * we insert from lowest to highest so each prior insertion does not shift later indices.
     *
     * @param entries List of (originalPosition, uri) pairs, sorted ascending by position
     */
    fun insertPages(entries: List<Pair<Int, Uri>>) {
        val current = savedStateHandle.get<List<Uri>>(KEY_PAGES).orEmpty().toMutableList()
        entries.sortedBy { it.first }.forEach { (position, uri) ->
            val clampedPosition = position.coerceIn(0, current.size)
            current.add(clampedPosition, uri)
        }
        savedStateHandle[KEY_PAGES] = current.toList()
    }

    /**
     * Move a page from one position to another (for drag & drop reordering).
     *
     * Creates a NEW list with the elements at [fromPosition] and [toPosition] swapped.
     * Uses Collections.swap() for simple adjacent moves, which is what ItemTouchHelper
     * typically does (one position at a time during drag).
     *
     * @param fromPosition Original position of the page
     * @param toPosition Target position to move to
     */
    fun movePage(fromPosition: Int, toPosition: Int) {
        val current = savedStateHandle.get<List<Uri>>(KEY_PAGES).orEmpty().toMutableList()
        if (fromPosition in current.indices && toPosition in current.indices) {
            java.util.Collections.swap(current, fromPosition, toPosition)
            savedStateHandle[KEY_PAGES] = current.toList()
        }
    }

    /**
     * Clear the current capture reference.
     *
     * Called after the capture is added to pages list or when user decides to retake.
     */
    fun clearCurrentCapture() {
        _currentCaptureUri.value = null
    }

    /**
     * Store the generated PDF URI.
     *
     * @param uri File URI of the PDF, or null to clear
     */
    fun setPdfUri(uri: Uri?) {
        _pdfUri.value = uri
    }

    /**
     * Set loading state.
     *
     * @param loading True to show loading indicator, false to hide
     */
    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    /**
     * Get the current number of scanned pages.
     *
     * @return Number of pages, or 0 if none
     */
    fun getPageCount(): Int = savedStateHandle.get<List<Uri>>(KEY_PAGES).orEmpty().size

    /**
     * Clear all data (start fresh).
     *
     * Resets pages, page filters, and PDF base name to empty/null.
     * Also clears the transient PDF URI.
     *
     * Useful for "New Document" functionality.
     */
    fun clearAllPages() {
        savedStateHandle[KEY_PAGES] = emptyList<Uri>()
        _pdfUri.value = null
        savedStateHandle[KEY_PAGE_FILTERS] = emptyMap<Int, String>()
        savedStateHandle[KEY_PDF_BASE_NAME] = null as String?
    }

    // ============================================================
    // FILTER METHODS
    // ============================================================

    /**
     * Set the filter type for a specific page.
     *
     * Creates a NEW map with the entry for [pageIndex] updated to [filterType].
     * Stores the filter as its enum name (String) for Bundle compatibility.
     *
     * Called when user selects a filter in PreviewFragment.
     * The actual processed image URI is stored in the pages list.
     *
     * @param pageIndex Index of the page (0-based)
     * @param filterType The filter that was applied
     */
    fun setPageFilter(pageIndex: Int, filterType: ImageProcessor.FilterType) {
        val current = savedStateHandle.get<Map<Int, String>>(KEY_PAGE_FILTERS).orEmpty()
        savedStateHandle[KEY_PAGE_FILTERS] = current + (pageIndex to filterType.name)
    }

    /**
     * Get the filter type for a specific page.
     *
     * @param pageIndex Index of the page
     * @return FilterType, defaults to ORIGINAL if not set
     */
    fun getPageFilter(pageIndex: Int): ImageProcessor.FilterType {
        val name = savedStateHandle.get<Map<Int, String>>(KEY_PAGE_FILTERS)?.get(pageIndex)
        return name?.let { ImageProcessor.FilterType.valueOf(it) } ?: ImageProcessor.FilterType.ORIGINAL
    }

    /**
     * Set custom PDF base name.
     *
     * Trims whitespace and stores null if the result is empty, so callers
     * can always treat a non-null value as a meaningful name.
     *
     * @param name The base name for the PDF (without extension)
     */
    fun setPdfBaseName(name: String?) {
        savedStateHandle[KEY_PDF_BASE_NAME] = name?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Get the PDF file name to use.
     *
     * @param timestamp Timestamp string to append
     * @return Complete filename (with .pdf extension)
     */
    fun getPdfFileName(timestamp: String): String {
        val baseName = savedStateHandle.get<String>(KEY_PDF_BASE_NAME)?.takeIf { it.isNotEmpty() } ?: "Scan"
        return "${baseName}_$timestamp.pdf"
    }
}
