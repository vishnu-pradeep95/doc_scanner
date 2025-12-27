/**
 * PagesFragment.kt - Page Management and PDF Generation Screen
 * 
 * PURPOSE:
 * This is the "hub" where users:
 * 1. See all scanned pages as thumbnails
 * 2. Delete unwanted pages
 * 3. Add more pages (go back to camera)
 * 4. Generate a PDF from all pages
 * 5. Share the generated PDF
 * 
 * KEY CONCEPTS DEMONSTRATED:
 * - RecyclerView for displaying lists
 * - Coroutines for background work
 * - PdfDocument API for PDF generation
 * - FileProvider for secure file sharing
 * - Intent system for sharing
 */

package com.pdfscanner.app.ui

// Android core imports
import android.content.Intent  // For launching other apps/activities
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument  // Android's built-in PDF API
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast

// AlertDialog for confirmation prompts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider  // Secure file URI provider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

// Coroutines for async/background work
import androidx.lifecycle.lifecycleScope  // Coroutine scope tied to lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager  // Grid layout for RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper  // Drag & drop support

// Material design components
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

// Project imports
import com.pdfscanner.app.R
import com.pdfscanner.app.adapter.PagesAdapter
import com.pdfscanner.app.data.DocumentHistoryRepository
import com.pdfscanner.app.databinding.FragmentPagesBinding
import com.pdfscanner.app.viewmodel.ScannerViewModel

// Coroutine imports
import kotlinx.coroutines.Dispatchers  // Thread dispatchers
import kotlinx.coroutines.launch  // Start a coroutine
import kotlinx.coroutines.withContext  // Switch threads

// Java utilities
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * PagesFragment - Manages scanned pages and generates PDFs
 */
class PagesFragment : Fragment() {

    // ============================================================
    // VIEW BINDING & VIEWMODEL
    // ============================================================
    
    private var _binding: FragmentPagesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScannerViewModel by activityViewModels()
    
    /**
     * RecyclerView adapter for displaying page thumbnails
     * 
     * Adapter is the "bridge" between data and RecyclerView
     * It creates views for each item and binds data to them
     */
    private lateinit var pagesAdapter: PagesAdapter

    /**
     * ItemTouchHelper for drag & drop reordering
     */
    private lateinit var itemTouchHelper: ItemTouchHelper

    // ============================================================
    // STATE
    // ============================================================
    
    /**
     * Reference to the generated PDF file
     * 
     * Null until "Create PDF" is clicked
     * Stored here so we can share it later
     */
    private var generatedPdfFile: File? = null

    // ============================================================
    // LIFECYCLE
    // ============================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerView()
        setupButtons()
        observeViewModel()
    }

    // ============================================================
    // UI SETUP
    // ============================================================

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        // Handle menu item clicks
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_history -> {
                    // Navigate to document history
                    findNavController().navigate(R.id.action_pages_to_history)
                    true
                }
                R.id.action_ocr -> {
                    showOcrComingSoonMessage()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Show OCR coming soon snackbar
     * 
     * OCR (Optical Character Recognition) will be implemented in a future phase.
     * This placeholder informs users the feature is planned.
     */
    private fun showOcrComingSoonMessage() {
        Snackbar.make(
            binding.root,
            R.string.ocr_coming_soon,
            Snackbar.LENGTH_LONG
        ).show()
    }

    /**
     * Setup RecyclerView for displaying page thumbnails
     * 
     * RECYCLERVIEW PATTERN:
     * 1. LayoutManager - how items are arranged (grid, list, etc.)
     * 2. Adapter - creates and binds views for each item
     * 3. Data - the list of items to display
     * 
     * RecyclerView "recycles" views - instead of creating a new view
     * for each item, it reuses views that scroll off screen.
     * This is crucial for performance with long lists.
     * 
     * DRAG & DROP:
     * ItemTouchHelper enables drag-to-reorder functionality.
     * Users can long-press or use the drag handle to reorder pages.
     */
    private fun setupRecyclerView() {
        /**
         * Create adapter with callbacks
         * 
         * We pass lambdas for delete, move, and drag start events
         * This is the "callback pattern" - adapter notifies us of events
         */
        pagesAdapter = PagesAdapter(
            onDeleteClick = { position ->
                // Show confirmation dialog before deleting
                showDeleteConfirmation(position)
            },
            onItemMoved = { fromPosition, toPosition ->
                // Update ViewModel when pages are reordered
                viewModel.movePage(fromPosition, toPosition)
            },
            onDragStarted = { viewHolder ->
                // Start drag when user touches the drag handle
                itemTouchHelper.startDrag(viewHolder)
            }
        )

        // Setup ItemTouchHelper for drag & drop
        val callback = PagesAdapter.DragCallback(pagesAdapter)
        itemTouchHelper = ItemTouchHelper(callback)

        /**
         * Configure RecyclerView
         * 
         * GridLayoutManager(context, spanCount) creates a grid layout
         * spanCount = 2 means 2 columns
         */
        binding.recyclerPages.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = pagesAdapter
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    /**
     * Setup action button click listeners
     */
    private fun setupButtons() {
        // Add more pages - go back to camera
        binding.btnAddMore.setOnClickListener {
            findNavController().navigate(R.id.action_pages_to_camera)
        }

        // Create PDF from all pages - show rename dialog first
        binding.btnCreatePdf.setOnClickListener {
            showPdfNameDialog()
        }

        // Share the generated PDF
        binding.fabShare.setOnClickListener {
            sharePdf()
        }
    }

    /**
     * Show dialog to let user name the PDF before creation
     * 
     * UX IMPROVEMENT:
     * Instead of auto-generating a name, let users provide a meaningful name.
     * Example: "Meeting Notes" → "Meeting Notes_20251226_123456.pdf"
     * 
     * If left empty, defaults to "Scan".
     */
    private fun showPdfNameDialog() {
        // Create the input layout programmatically for Material Design styling
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.pdf_name_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(48, 16, 48, 0)
        }
        
        val editText = TextInputEditText(inputLayout.context).apply {
            // Pre-fill with existing name if set
            setText(viewModel.pdfBaseName.value ?: "")
        }
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.name_your_pdf)
            .setView(inputLayout)
            .setPositiveButton(R.string.create_pdf) { _, _ ->
                val pdfName = editText.text?.toString()?.trim()
                viewModel.setPdfBaseName(pdfName)
                createPdf()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Observe ViewModel changes and update UI
     */
    private fun observeViewModel() {
        viewModel.pages.observe(viewLifecycleOwner) { pages ->
            /**
             * submitList() is a ListAdapter method
             * 
             * .toList() creates a new list instance
             * This is important because ListAdapter uses reference equality
             * to detect changes. If we pass the same list object, it won't update.
             */
            pagesAdapter.submitList(pages.toList())

            // Show/hide empty state
            if (pages.isEmpty()) {
                binding.emptyText.visibility = View.VISIBLE
                binding.recyclerPages.visibility = View.GONE
                binding.btnCreatePdf.isEnabled = false
            } else {
                binding.emptyText.visibility = View.GONE
                binding.recyclerPages.visibility = View.VISIBLE
                binding.btnCreatePdf.isEnabled = true
            }

            // Reset PDF state when pages change
            // (old PDF is no longer valid if pages changed)
            generatedPdfFile = null
            binding.fabShare.visibility = View.GONE
        }
    }

    // ============================================================
    // DELETE CONFIRMATION
    // ============================================================

    /**
     * Show confirmation dialog before deleting a page
     * 
     * AlertDialog is Android's standard dialog component
     * Builder pattern is used to configure it
     * 
     * @param position Index of page to delete
     */
    private fun showDeleteConfirmation(position: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete)  // "Delete this page?"
            .setPositiveButton(R.string.yes) { _, _ ->
                // User confirmed - delete the page
                viewModel.removePage(position)
            }
            .setNegativeButton(R.string.no, null)  // null = just dismiss
            .show()
    }

    // ============================================================
    // PDF GENERATION
    // ============================================================

    /**
     * Create PDF from all scanned pages
     * 
     * COROUTINES:
     * PDF generation is CPU-intensive and should not run on main thread
     * (it would freeze the UI). We use Kotlin Coroutines to run it
     * on a background thread.
     * 
     * COROUTINE BASICS:
     * - launch {} starts a new coroutine (like starting a thread)
     * - Dispatchers.IO = background thread pool for I/O operations
     * - Dispatchers.Main = main/UI thread
     * - withContext() switches threads within a coroutine
     */
    private fun createPdf() {
        val pages = viewModel.pages.value ?: return
        
        if (pages.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_pages, Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading overlay
        binding.loadingOverlay.visibility = View.VISIBLE

        /**
         * lifecycleScope.launch - start coroutine tied to Fragment lifecycle
         * 
         * If Fragment is destroyed, coroutine is automatically cancelled
         * This prevents crashes from updating destroyed UI
         */
        lifecycleScope.launch {
            try {
                /**
                 * withContext(Dispatchers.IO) - run this block on background thread
                 * 
                 * generatePdf() is CPU-intensive, so we run it off main thread
                 * The result is returned to the outer coroutine
                 */
                val pdfFile = withContext(Dispatchers.IO) {
                    generatePdf(pages)
                }

                // Store reference for sharing
                generatedPdfFile = pdfFile

                /**
                 * withContext(Dispatchers.Main) - update UI on main thread
                 * 
                 * UI operations MUST run on main thread in Android
                 * Doing them on background thread crashes the app
                 */
                withContext(Dispatchers.Main) {
                    binding.loadingOverlay.visibility = View.GONE
                    binding.fabShare.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), R.string.pdf_created, Toast.LENGTH_SHORT).show()
                    
                    // Save to document history
                    saveToHistory(pdfFile, pages.size)
                }
            } catch (e: Exception) {
                // Handle errors (file I/O, out of memory, etc.)
                withContext(Dispatchers.Main) {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(
                        requireContext(),
                        "${getString(R.string.pdf_error)}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Generate a multi-page PDF from image URIs
     * 
     * PDFDOCUMENT API:
     * Android provides PdfDocument class for creating PDFs programmatically
     * 
     * PROCESS:
     * 1. Create PdfDocument
     * 2. For each image:
     *    a. Decode bitmap
     *    b. Create PDF page
     *    c. Draw bitmap on page's canvas
     *    d. Finish page
     * 3. Write document to file
     * 4. Close document
     * 
     * @param pageUris List of image URIs to include
     * @return File object pointing to generated PDF
     */
    private fun generatePdf(pageUris: List<Uri>): File {
        // Create new PDF document
        val pdfDocument = PdfDocument()

        /**
         * A4 page dimensions at 72 DPI
         * 
         * A4 is 210mm × 297mm
         * At 72 DPI: 595 × 842 points
         * 
         * These are PDF points, not pixels
         */
        val pageWidth = 595
        val pageHeight = 842

        /**
         * Process each page
         * 
         * forEachIndexed gives us both the index and the item
         * Like enumerate() in Python
         */
        pageUris.forEachIndexed { index, uri ->
            // Decode the image bitmap
            val bitmap = decodeSampledBitmap(uri, pageWidth, pageHeight)
                ?: throw Exception("Failed to decode page ${index + 1}")

            /**
             * Create PDF page
             * 
             * PageInfo defines page dimensions and page number (1-based)
             */
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)

            /**
             * Draw bitmap onto page canvas
             * 
             * Canvas is Android's 2D drawing API
             * We need to scale and center the image to fit the page
             */
            val canvas = page.canvas
            
            /**
             * Calculate scaling to fit page while maintaining aspect ratio
             * 
             * "Fit" means the entire image is visible, with possible empty space
             * "Fill" would crop to fill the entire page
             */
            val scale: Float
            val dx: Float  // X offset (horizontal centering)
            val dy: Float  // Y offset (vertical centering)

            val bitmapAspect = bitmap.width.toFloat() / bitmap.height
            val pageAspect = pageWidth.toFloat() / pageHeight

            if (bitmapAspect > pageAspect) {
                // Bitmap is wider than page - fit to width
                scale = pageWidth.toFloat() / bitmap.width
                dx = 0f
                dy = (pageHeight - bitmap.height * scale) / 2  // Center vertically
            } else {
                // Bitmap is taller than page - fit to height
                scale = pageHeight.toFloat() / bitmap.height
                dx = (pageWidth - bitmap.width * scale) / 2  // Center horizontally
                dy = 0f
            }

            /**
             * Canvas transformations
             * 
             * save() - save current transformation state
             * translate() - move origin
             * scale() - scale subsequent drawings
             * drawBitmap() - draw the image
             * restore() - restore transformation state
             */
            canvas.save()
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            canvas.restore()

            // Finish this page (commit to document)
            pdfDocument.finishPage(page)

            /**
             * MEMORY MANAGEMENT: Recycle bitmap
             * 
             * Bitmaps use significant memory (width × height × 4 bytes)
             * recycle() immediately releases the memory
             * Without this, processing many pages could cause OOM
             */
            bitmap.recycle()
        }

        // Create output directory and file
        val pdfsDir = File(requireContext().filesDir, "pdfs").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            .format(System.currentTimeMillis())
        
        // Use custom name from ViewModel if set, otherwise default to "Scan"
        val pdfFileName = viewModel.getPdfFileName(timestamp)
        val pdfFile = File(pdfsDir, pdfFileName)

        /**
         * Write PDF to file
         * 
         * .use {} is Kotlin's try-with-resources equivalent
         * Automatically closes the stream when done (even if exception occurs)
         */
        FileOutputStream(pdfFile).use { outputStream ->
            pdfDocument.writeTo(outputStream)
        }

        // Release PDF document resources
        pdfDocument.close()

        return pdfFile
    }

    /**
     * Decode bitmap with downsampling to reduce memory usage
     * 
     * MEMORY OPTIMIZATION:
     * A 12MP camera image is ~4000×3000 pixels
     * At 4 bytes/pixel = 48MB per image!
     * 
     * For PDF generation, we don't need full resolution
     * Downsampling to ~2× the PDF page size is sufficient
     * 
     * @param uri Image file URI
     * @param reqWidth Target width
     * @param reqHeight Target height
     * @return Decoded bitmap, or null if failed
     */
    private fun decodeSampledBitmap(uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            // Step 1: Get dimensions without loading pixels
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            /**
             * Step 2: Calculate appropriate inSampleSize
             * 
             * inSampleSize = 2 loads every 2nd pixel (1/4 total pixels)
             * inSampleSize = 4 loads every 4th pixel (1/16 total pixels)
             * 
             * We target 2× the required size for better quality
             */
            options.inSampleSize = calculateInSampleSize(options, reqWidth * 2, reqHeight * 2)
            options.inJustDecodeBounds = false  // Actually load pixels this time

            // Step 3: Decode with calculated sample size
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            null  // Return null on any error
        }
    }

    /**
     * Calculate optimal inSampleSize
     * 
     * ALGORITHM:
     * Keep doubling inSampleSize until the resulting dimensions
     * are smaller than the required dimensions
     * 
     * @param options BitmapFactory.Options with outWidth/outHeight set
     * @param reqWidth Required width
     * @param reqHeight Required height
     * @return Power of 2 sample size
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            // Calculate the largest inSampleSize that keeps dimensions >= required
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    // ============================================================
    // PDF SHARING
    // ============================================================

    /**
     * Share the generated PDF using Android's share sheet
     * 
     * FILEPROVIDER:
     * App-private files can't be directly accessed by other apps
     * FileProvider creates a content:// URI that grants temporary access
     * This is secure - access is revoked after sharing
     * 
     * INTENT SYSTEM:
     * Android apps communicate via Intents
     * ACTION_SEND = "I want to send something"
     * Other apps register to handle ACTION_SEND for various MIME types
     */
    private fun sharePdf() {
        val pdfFile = generatedPdfFile ?: return

        try {
            /**
             * Get content URI from FileProvider
             * 
             * Parameters:
             * 1. Context
             * 2. Authority (must match AndroidManifest.xml)
             * 3. File to share
             * 
             * Returns: content://com.pdfscanner.app.fileprovider/pdfs/Scan_xxx.pdf
             */
            val pdfUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageId}.fileprovider",  // Authority from manifest
                pdfFile
            )

            /**
             * Create share Intent
             * 
             * ACTION_SEND = implicit intent for sharing
             * type = MIME type helps Android find appropriate apps
             * EXTRA_STREAM = the URI to share
             * FLAG_GRANT_READ_URI_PERMISSION = grant temporary read access
             */
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            /**
             * Show share sheet (app chooser)
             * 
             * createChooser() wraps the intent in a chooser dialog
             * This lets user pick which app to share with
             */
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_pdf)))
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Failed to share: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================================
    // DOCUMENT HISTORY
    // ============================================================

    /**
     * Save newly created PDF to document history
     * 
     * @param pdfFile The generated PDF file
     * @param pageCount Number of pages in the PDF
     */
    private fun saveToHistory(pdfFile: File, pageCount: Int) {
        val repository = DocumentHistoryRepository.getInstance(requireContext())
        
        // Get the display name (without extension and timestamp)
        val baseName = viewModel.pdfBaseName.value ?: "Scan"
        
        repository.addDocument(
            name = baseName,
            filePath = pdfFile.absolutePath,
            pageCount = pageCount
        )
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Extension property to get package ID
 * 
 * Kotlin extension properties add properties to existing classes
 * This adds 'packageId' to Context class for cleaner code
 */
private val android.content.Context.packageId: String
    get() = packageName
