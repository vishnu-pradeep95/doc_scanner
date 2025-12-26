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
 */

package com.pdfscanner.app.viewmodel

import android.net.Uri  // URI = Uniform Resource Identifier, points to files/resources
import androidx.lifecycle.LiveData  // Read-only observable data
import androidx.lifecycle.MutableLiveData  // Read-write observable data
import androidx.lifecycle.ViewModel  // Base class for ViewModels

/**
 * ScannerViewModel - Manages scanned pages across all Fragments
 * 
 * This ViewModel is "shared" - multiple Fragments access the same instance
 * using 'by activityViewModels()' in each Fragment.
 * 
 * DATA STORED:
 * - List of scanned page URIs (file paths)
 * - Current capture URI (before adding to list)
 * - Generated PDF URI
 * - Loading state
 */
class ScannerViewModel : ViewModel() {

    // ============================================================
    // SCANNED PAGES LIST
    // ============================================================
    
    /**
     * Private mutable list of page URIs
     * 
     * NAMING CONVENTION:
     * _underscore prefix = private backing property (internal, mutable)
     * 
     * MutableLiveData = can be changed (setValue, postValue)
     * Initial value = empty mutableListOf()
     * 
     * 'Uri' represents a file path like: file:///data/data/com.pdfscanner.app/files/scans/SCAN_123.jpg
     */
    private val _pages = MutableLiveData<MutableList<Uri>>(mutableListOf())
    
    /**
     * Public read-only access to pages
     * 
     * LiveData (not MutableLiveData) = read-only to external code
     * This is ENCAPSULATION - Fragments can observe but not directly modify
     * 
     * They must use addPage(), removePage() etc. methods instead.
     */
    val pages: LiveData<MutableList<Uri>> = _pages

    // ============================================================
    // CURRENT CAPTURE (temporary, before adding to pages)
    // ============================================================
    
    /**
     * Holds the URI of the image just captured by camera
     * 
     * FLOW:
     * 1. CameraFragment captures image → sets currentCaptureUri
     * 2. PreviewFragment shows it for editing
     * 3. User clicks "Add Page" → moves to pages list
     * 4. currentCaptureUri is cleared
     * 
     * Nullable (Uri?) because there might be no current capture
     */
    private val _currentCaptureUri = MutableLiveData<Uri?>()
    val currentCaptureUri: LiveData<Uri?> = _currentCaptureUri

    // ============================================================
    // GENERATED PDF
    // ============================================================
    
    /**
     * URI of the generated PDF file (after "Create PDF" is clicked)
     * 
     * Null until PDF is generated, then points to:
     * file:///data/data/com.pdfscanner.app/files/pdfs/Scan_20251226_123456.pdf
     */
    private val _pdfUri = MutableLiveData<Uri?>()
    val pdfUri: LiveData<Uri?> = _pdfUri

    // ============================================================
    // LOADING STATE
    // ============================================================
    
    /**
     * Boolean flag for showing/hiding loading indicators
     * 
     * UI observes this and shows ProgressBar when true
     * Set to true before long operations, false when done
     */
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // ============================================================
    // PUBLIC METHODS (how Fragments modify data)
    // ============================================================

    /**
     * Store the URI of a newly captured image
     * 
     * Called by CameraFragment after successful capture
     * 
     * @param uri File URI of the captured JPEG image
     */
    fun setCurrentCapture(uri: Uri) {
        // .value = synchronous update (must be on main thread)
        // .postValue() = asynchronous update (safe from any thread)
        _currentCaptureUri.value = uri
    }

    /**
     * Add a page to the scanned pages list
     * 
     * Called by PreviewFragment when user clicks "Add Page"
     * 
     * @param uri File URI of the (possibly edited) page image
     */
    fun addPage(uri: Uri) {
        // Get current list, or empty list if null
        // Elvis operator (?:) = if left is null, use right
        val currentList = _pages.value ?: mutableListOf()
        
        // Add the new page
        currentList.add(uri)
        
        // IMPORTANT: Must reassign to trigger LiveData observers!
        // Just modifying the list contents doesn't trigger updates
        _pages.value = currentList
    }

    /**
     * Update a page at a specific position (after cropping)
     * 
     * When user edits an existing page, we replace it in-place
     * 
     * @param index Position in the list (0-based)
     * @param newUri New file URI after editing
     */
    fun updatePage(index: Int, newUri: Uri) {
        val currentList = _pages.value ?: mutableListOf()
        
        // Safety check: ensure index is valid
        // 'in' checks if index is within list.indices (0 until size)
        if (index in currentList.indices) {
            currentList[index] = newUri
            _pages.value = currentList  // Trigger observers
        }
    }

    /**
     * Remove a page from the list
     * 
     * Called when user clicks delete button on a page thumbnail
     * 
     * @param index Position to remove (0-based)
     */
    fun removePage(index: Int) {
        val currentList = _pages.value ?: mutableListOf()
        
        if (index in currentList.indices) {
            currentList.removeAt(index)
            _pages.value = currentList  // Trigger observers
        }
    }

    /**
     * Clear the current capture reference
     * 
     * Called after the capture is added to pages list
     * or when user decides to retake
     */
    fun clearCurrentCapture() {
        _currentCaptureUri.value = null
    }

    /**
     * Store the generated PDF URI
     * 
     * @param uri File URI of the PDF, or null to clear
     */
    fun setPdfUri(uri: Uri?) {
        _pdfUri.value = uri
    }

    /**
     * Set loading state
     * 
     * @param loading True to show loading indicator, false to hide
     */
    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    /**
     * Get the current number of scanned pages
     * 
     * @return Number of pages, or 0 if none
     */
    fun getPageCount(): Int = _pages.value?.size ?: 0

    /**
     * Clear all data (start fresh)
     * 
     * Useful for "New Document" functionality
     */
    fun clearAllPages() {
        _pages.value = mutableListOf()
        _pdfUri.value = null
    }
}
