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
import android.content.Context
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
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

// Project imports
import com.pdfscanner.app.R
import com.pdfscanner.app.adapter.PagesAdapter
import com.pdfscanner.app.data.DocumentHistoryRepository
import com.pdfscanner.app.databinding.FragmentPagesBinding
import com.pdfscanner.app.ocr.OcrProcessor
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
        // Normal toolbar
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        // Handle menu item clicks
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_home -> {
                    findNavController().navigate(R.id.action_pages_to_home)
                    true
                }
                R.id.action_history -> {
                    // Navigate to document history
                    findNavController().navigate(R.id.action_pages_to_history)
                    true
                }
                R.id.action_ocr -> {
                    performOcrOnAllPages()
                    true
                }
                else -> false
            }
        }
        
        // Selection toolbar - close button exits selection mode
        binding.selectionToolbar.setNavigationOnClickListener {
            pagesAdapter.exitSelectionMode()
        }
        
        // Selection toolbar menu items
        binding.selectionToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_select_all -> {
                    pagesAdapter.selectAll()
                    true
                }
                R.id.action_ocr_selected -> {
                    performOcrOnSelectedPages()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Perform OCR on selected pages only (in selection order)
     */
    private fun performOcrOnSelectedPages() {
        val selectedUris = pagesAdapter.getSelectedUrisInOrder()
        if (selectedUris.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_pages_selected, Toast.LENGTH_SHORT).show()
            return
        }
        
        performOcrOnPages(selectedUris, isSelectedMode = true)
    }

    /**
     * Perform OCR on all scanned pages and show results
     * 
     * Processes each page through ML Kit Text Recognition and
     * combines results into a single text output.
     */
    private fun performOcrOnAllPages() {
        val pages = viewModel.pages.value
        if (pages.isNullOrEmpty()) {
            Toast.makeText(requireContext(), R.string.no_pages, Toast.LENGTH_SHORT).show()
            return
        }
        
        performOcrOnPages(pages, isSelectedMode = false)
    }
    
    /**
     * Perform OCR on a list of page URIs
     * 
     * @param uris List of page URIs to process
     * @param isSelectedMode Whether this is from selection mode (affects exit behavior)
     */
    private fun performOcrOnPages(uris: List<Uri>, isSelectedMode: Boolean) {
        // Show loading with OCR-specific message
        binding.loadingText.text = getString(R.string.ocr_processing)
        binding.loadingOverlay.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val allText = StringBuilder()
                
                // Process each page
                uris.forEachIndexed { index, uri ->
                    val result = OcrProcessor.recognizeText(requireContext(), uri)
                    if (result.success && result.fullText.isNotEmpty()) {
                        if (allText.isNotEmpty()) {
                            allText.append("\n\n--- Page ${index + 2} ---\n\n")
                        } else if (uris.size > 1) {
                            allText.append("--- Page 1 ---\n\n")
                        }
                        allText.append(result.fullText)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    binding.loadingOverlay.visibility = View.GONE
                    
                    // Exit selection mode after OCR if in selection mode
                    if (isSelectedMode) {
                        pagesAdapter.exitSelectionMode()
                    }
                    
                    if (allText.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.ocr_no_text, Toast.LENGTH_SHORT).show()
                    } else {
                        showOcrResultDialog(allText.toString())
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingOverlay.visibility = View.GONE
                    
                    if (isSelectedMode) {
                        pagesAdapter.exitSelectionMode()
                    }
                    
                    Toast.makeText(
                        requireContext(),
                        "${getString(R.string.ocr_error)}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Show dialog with OCR results and copy/share options
     */
    private fun showOcrResultDialog(text: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.recognized_text)
            .setMessage(text)
            .setPositiveButton(R.string.copy_text) { _, _ ->
                copyToClipboard(text)
            }
            .setNeutralButton(R.string.share) { _, _ ->
                shareText(text)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }
    
    /**
     * Copy text to clipboard
     */
    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("OCR Text", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.text_copied, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Share text via Android share sheet
     */
    private fun shareText(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
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
         * We pass lambdas for delete, click, move, drag start, and selection events
         * This is the "callback pattern" - adapter notifies us of events
         */
        pagesAdapter = PagesAdapter(
            onDeleteClick = { position ->
                // Show confirmation dialog before deleting
                showDeleteConfirmation(position)
            },
            onItemClick = { position ->
                // Navigate to preview to edit this page
                editPageAt(position)
            },
            onItemMoved = { fromPosition, toPosition ->
                // Update ViewModel when pages are reordered
                viewModel.movePage(fromPosition, toPosition)
            },
            onDragStarted = { viewHolder ->
                // Start drag when user touches the drag handle
                itemTouchHelper.startDrag(viewHolder)
            },
            onSelectionChanged = { selectedCount, isSelectionMode ->
                // Update UI based on selection mode
                updateSelectionModeUI(selectedCount, isSelectionMode)
            },
            onRotateClick = { position ->
                // Rotate the page 90 degrees clockwise
                rotatePage(position)
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
     * Update UI for selection mode
     */
    private fun updateSelectionModeUI(selectedCount: Int, isSelectionMode: Boolean) {
        if (isSelectionMode) {
            // Show selection toolbar and buttons
            binding.toolbar.visibility = View.GONE
            binding.selectionToolbar.visibility = View.VISIBLE
            binding.selectionToolbar.title = getString(R.string.selected_count, selectedCount)
            
            binding.buttonsLayout.visibility = View.GONE
            binding.selectionButtonsLayout.visibility = View.VISIBLE
        } else {
            // Show normal toolbar and buttons
            binding.toolbar.visibility = View.VISIBLE
            binding.selectionToolbar.visibility = View.GONE
            
            binding.buttonsLayout.visibility = View.VISIBLE
            binding.selectionButtonsLayout.visibility = View.GONE
        }
    }
    
    /**
     * Edit an existing page at the given position
     * 
     * Navigates to PreviewFragment with the page's URI and index,
     * allowing the user to crop/filter the image again.
     */
    private fun editPageAt(position: Int) {
        val pages = viewModel.pages.value ?: return
        if (position in pages.indices) {
            val uri = pages[position]
            val action = PagesFragmentDirections.actionPagesToPreview(
                imageUri = uri.toString(),
                editIndex = position
            )
            findNavController().navigate(action)
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
        
        // ============================================================
        // SELECTION MODE BUTTONS
        // ============================================================
        
        // Delete selected pages
        binding.btnDeleteSelected.setOnClickListener {
            val selectedCount = pagesAdapter.getSelectedCount()
            if (selectedCount > 0) {
                showDeleteSelectedConfirmation(selectedCount)
            }
        }
        
        // Create PDF from selected pages (in selection order)
        binding.btnCreatePdfSelected.setOnClickListener {
            val selectedUris = pagesAdapter.getSelectedUrisInOrder()
            if (selectedUris.isNotEmpty()) {
                showPdfNameDialogForSelected(selectedUris)
            }
        }
    }
    
    /**
     * Show confirmation dialog for deleting selected pages
     */
    private fun showDeleteSelectedConfirmation(count: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_delete)
            .setMessage(getString(R.string.confirm_delete_selected, count))
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteSelectedPages()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Delete all selected pages
     */
    private fun deleteSelectedPages() {
        // Get positions in reverse order to avoid index shifting issues
        val positions = pagesAdapter.getSelectedPositionsInOrder().sortedDescending()
        positions.forEach { position ->
            viewModel.removePage(position)
        }
        pagesAdapter.exitSelectionMode()
        Toast.makeText(requireContext(), 
            getString(R.string.selected_count, positions.size) + " deleted", 
            Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Show PDF name dialog for selected pages
     */
    private fun showPdfNameDialogForSelected(selectedUris: List<Uri>) {
        val inputLayout = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.pdf_name_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(48, 16, 48, 0)
        }
        
        val editText = TextInputEditText(inputLayout.context)
        inputLayout.addView(editText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.name_your_pdf)
            .setMessage("Creating PDF from ${selectedUris.size} selected pages")
            .setView(inputLayout)
            .setPositiveButton(R.string.create_pdf) { _, _ ->
                val pdfName = editText.text?.toString()?.trim()
                createPdfFromSelection(selectedUris, pdfName)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Create PDF from selected pages
     */
    private fun createPdfFromSelection(selectedUris: List<Uri>, baseName: String?) {
        binding.loadingText.text = getString(R.string.creating_pdf)
        binding.loadingOverlay.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    generatePdf(selectedUris, baseName)
                }
                
                generatedPdfFile = pdfFile
                
                withContext(Dispatchers.Main) {
                    binding.loadingOverlay.visibility = View.GONE
                    binding.fabShare.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), R.string.pdf_created, Toast.LENGTH_SHORT).show()
                    
                    // Save to history
                    DocumentHistoryRepository.getInstance(requireContext())
                        .addDocument(pdfFile.name, pdfFile.absolutePath, selectedUris.size)
                    
                    // Exit selection mode
                    pagesAdapter.exitSelectionMode()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(requireContext(), 
                        "${getString(R.string.pdf_error)}: ${e.message}", 
                        Toast.LENGTH_LONG).show()
                }
            }
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
                binding.emptyState.visibility = View.VISIBLE
                binding.recyclerPages.visibility = View.GONE
                binding.btnCreatePdf.isEnabled = false
            } else {
                binding.emptyState.visibility = View.GONE
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

    /**
     * Rotate a page 90 degrees clockwise
     * 
     * This rotates the image file and updates the URI in the ViewModel.
     * The rotation is done in the background to avoid blocking the UI.
     * 
     * @param position Index of page to rotate
     */
    private fun rotatePage(position: Int) {
        val pages = viewModel.pages.value ?: return
        if (position !in pages.indices) return
        
        val uri = pages[position]
        
        // Show loading
        binding.loadingText.text = getString(R.string.rotating_page)
        binding.loadingOverlay.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val rotatedUri = withContext(Dispatchers.IO) {
                    rotateImage(uri)
                }
                
                withContext(Dispatchers.Main) {
                    binding.loadingOverlay.visibility = View.GONE
                    
                    if (rotatedUri != null) {
                        // Update the page with the rotated image
                        viewModel.updatePage(position, rotatedUri)
                        pagesAdapter.notifyItemChanged(position)
                    } else {
                        Toast.makeText(requireContext(), R.string.rotate_error, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(requireContext(), R.string.rotate_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Rotate an image 90 degrees clockwise and save to a new file
     * 
     * @param uri Original image URI
     * @return New URI of rotated image, or null on error
     */
    private fun rotateImage(uri: Uri): Uri? {
        return try {
            // Load the bitmap
            val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            // Create rotation matrix (90 degrees clockwise)
            val matrix = android.graphics.Matrix()
            matrix.postRotate(90f)
            
            // Create rotated bitmap
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, 
                bitmap.width, bitmap.height, 
                matrix, true
            )
            
            // Save to a new file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
            val rotatedFile = File(
                requireContext().filesDir,
                "scans/ROT_${timestamp}.jpg"
            )
            rotatedFile.parentFile?.mkdirs()
            
            FileOutputStream(rotatedFile).use { outputStream ->
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            
            // Clean up
            bitmap.recycle()
            rotatedBitmap.recycle()
            
            Uri.fromFile(rotatedFile)
        } catch (e: Exception) {
            null
        }
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

        // Show loading overlay with PDF-specific message
        binding.loadingText.text = getString(R.string.creating_pdf)
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
     * @param customBaseName Optional custom name for the PDF
     * @return File object pointing to generated PDF
     */
    private fun generatePdf(pageUris: List<Uri>, customBaseName: String? = null): File {
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
        
        // Use custom name if provided, otherwise use ViewModel's name or default
        val baseName = customBaseName?.takeIf { it.isNotEmpty() }
            ?: viewModel.pdfBaseName.value?.takeIf { it.isNotEmpty() }
            ?: "Scan"
        val pdfFileName = "${baseName}_${timestamp}.pdf"
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
                "${requireContext().packageName}.fileprovider",  // Authority from manifest
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
