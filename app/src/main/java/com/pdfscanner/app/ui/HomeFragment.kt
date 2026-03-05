/**
 * HomeFragment.kt - Landing Page / Home Screen
 * 
 * PURPOSE:
 * Serves as the main entry point for the app, providing:
 * - Quick access to scanning features (New Scan, Auto Scan, Import)
 * - PDF Tools (Merge, Split, Compress)
 * - Recent documents list
 * - Current session status (if pages are being edited)
 * - Access to settings
 * 
 * DESIGN DECISIONS:
 * - Replaces camera as the start destination for better UX
 * - Shows recent docs for quick access to previous work
 * - Maintains session awareness so users don't lose work
 */

package com.pdfscanner.app.ui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.pdfscanner.app.R
import com.pdfscanner.app.adapter.RecentDocumentsAdapter
import com.pdfscanner.app.data.DocumentEntry
import com.pdfscanner.app.data.DocumentHistoryRepository
import com.pdfscanner.app.databinding.FragmentHomeBinding
import com.pdfscanner.app.util.DocumentScanner
import com.pdfscanner.app.util.InputValidator
import com.pdfscanner.app.util.PdfPageExtractor
import com.pdfscanner.app.util.PdfUtils
import com.pdfscanner.app.util.SecureFileManager
import com.pdfscanner.app.util.SecurePreferences
import com.pdfscanner.app.viewmodel.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// PDF operation modes
private enum class PdfOperation {
    MERGE, SPLIT, COMPRESS
}

/**
 * HomeFragment - The main landing screen of the app
 */
class HomeFragment : Fragment() {

    // View Binding
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Shared ViewModel
    private val viewModel: ScannerViewModel by activityViewModels()

    // Document history repository
    private lateinit var historyRepository: DocumentHistoryRepository

    // Recent documents adapter
    private lateinit var recentDocsAdapter: RecentDocumentsAdapter

    // ML Kit Document Scanner
    private lateinit var documentScanner: GmsDocumentScanner
    
    // Current PDF operation mode
    private var currentPdfOperation: PdfOperation? = null

    // Activity result launcher for document scanner
    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        handleScannerResult(result)
    }

    // Activity result launcher for gallery import
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        handleGalleryResult(uris)
    }
    
    // Activity result launcher for import (images + PDFs)
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        handleImportResult(uris)
    }
    
    // Activity result launcher for PDF picker (multiple)
    private val pdfPickerMultiple = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        handlePdfPickerResult(uris)
    }
    
    // Activity result launcher for PDF picker (single)
    private val pdfPickerSingle = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handlePdfPickerResult(listOf(it)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val duration = resources.getInteger(R.integer.motion_duration_large).toLong()
        enterTransition = com.google.android.material.transition.MaterialFadeThrough().apply {
            this.duration = duration
        }
        returnTransition = com.google.android.material.transition.MaterialFadeThrough().apply {
            this.duration = duration
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize repository
        historyRepository = DocumentHistoryRepository(requireContext())

        // Initialize document scanner
        documentScanner = DocumentScanner.createScanner(
            pageLimit = 10,
            enableGalleryImport = true
        )

        // Setup UI
        setupQuickActions()
        setupRecentDocuments()
        setupSettings()
        observeViewModel()

        // Edge-to-edge inset handling
        // HomeFragment has no toolbar — apply top inset to root, bottom inset to recycler
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = insets.top)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerRecentDocs) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        // SEC-09: Migrate existing unencrypted files on first launch after update
        checkAndRunMigration()
    }

    override fun onResume() {
        super.onResume()
        // Refresh recent documents when returning to this screen
        loadRecentDocuments()
        updateCurrentSessionUI()
    }

    /**
     * Setup quick action card click listeners with bounce animations
     */
    private fun setupQuickActions() {
        // New Scan - Navigate to camera with bounce animation
        binding.cardNewScan.setOnClickListener {
            animateCardClick(it)
            val dur = resources.getInteger(R.integer.motion_duration_large).toLong()
            exitTransition = com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, true).apply { duration = dur }
            reenterTransition = com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, false).apply { duration = dur }
            findNavController().navigate(R.id.action_home_to_camera)
        }

        // Auto Scan - Launch ML Kit Document Scanner with bounce animation
        binding.cardAutoScan.setOnClickListener {
            animateCardClick(it)
            startAutoScan()
        }

        // Import - Open file picker for images and PDFs with bounce animation
        binding.cardImport.setOnClickListener {
            animateCardClick(it)
            // Allow picking images and PDFs
            importLauncher.launch(arrayOf("image/*", "application/pdf"))
        }
        
        // PDF Tools with bounce animations
        binding.cardMerge.setOnClickListener {
            animateCardClick(it)
            currentPdfOperation = PdfOperation.MERGE
            pdfPickerMultiple.launch(arrayOf("application/pdf"))
        }
        
        binding.cardSplit.setOnClickListener {
            animateCardClick(it)
            currentPdfOperation = PdfOperation.SPLIT
            pdfPickerSingle.launch(arrayOf("application/pdf"))
        }
        
        binding.cardCompress.setOnClickListener {
            animateCardClick(it)
            currentPdfOperation = PdfOperation.COMPRESS
            pdfPickerSingle.launch(arrayOf("application/pdf"))
        }
    }
    
    /**
     * Animate card click with bounce effect
     */
    private fun animateCardClick(view: View) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    /**
     * Setup recent documents RecyclerView
     */
    private fun setupRecentDocuments() {
        recentDocsAdapter = RecentDocumentsAdapter(
            onDocumentClick = { document ->
                openDocument(document)
            },
            onShareClick = { document ->
                shareDocument(document)
            },
            onDeleteClick = { document ->
                confirmDeleteDocument(document)
            }
        )

        binding.recyclerRecentDocs.apply {
            layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL,
                false
            )
            adapter = recentDocsAdapter
        }

        // View all button
        binding.btnViewAll.setOnClickListener {
            val dur = resources.getInteger(R.integer.motion_duration_large).toLong()
            exitTransition = com.google.android.material.transition.MaterialFadeThrough().apply { duration = dur }
            reenterTransition = com.google.android.material.transition.MaterialFadeThrough().apply { duration = dur }
            findNavController().navigate(R.id.action_home_to_history)
        }
    }

    /**
     * Setup settings button
     */
    private fun setupSettings() {
        binding.btnSettings.setOnClickListener {
            val dur = resources.getInteger(R.integer.motion_duration_large).toLong()
            exitTransition = com.google.android.material.transition.MaterialFadeThrough().apply { duration = dur }
            reenterTransition = com.google.android.material.transition.MaterialFadeThrough().apply { duration = dur }
            findNavController().navigate(R.id.action_home_to_settings)
        }
    }

    /**
     * Observe ViewModel for current session pages
     */
    private fun observeViewModel() {
        viewModel.pages.observe(viewLifecycleOwner) { pages ->
            updateCurrentSessionUI()
        }
    }

    /**
     * Load recent documents from history
     */
    private fun loadRecentDocuments() {
        val documents = historyRepository.getAllDocuments()
            .filter { it.exists() }  // Only show documents that still exist
            .take(10)  // Show up to 10 recent documents

        if (documents.isEmpty()) {
            binding.recyclerRecentDocs.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.recyclerRecentDocs.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            recentDocsAdapter.submitList(documents)
        }
    }

    /**
     * Update the current session section based on ViewModel pages
     */
    private fun updateCurrentSessionUI() {
        val pageCount = viewModel.pages.value?.size ?: 0
        
        if (pageCount > 0) {
            binding.currentSessionSection.visibility = View.VISIBLE
            binding.textSessionPages.text = getString(R.string.pages_scanned, pageCount)
            
            // Click handlers for current session
            binding.cardCurrentSession.setOnClickListener {
                findNavController().navigate(R.id.action_home_to_pages)
            }
            binding.btnContinueSession.setOnClickListener {
                findNavController().navigate(R.id.action_home_to_pages)
            }
        } else {
            binding.currentSessionSection.visibility = View.GONE
        }
    }

    /**
     * Start auto-scan using ML Kit Document Scanner
     */
    private fun startAutoScan() {
        try {
            DocumentScanner.startScanning(
                activity = requireActivity(),
                scanner = documentScanner,
                launcher = scannerLauncher
            )
        } catch (e: Exception) {
            showSnackbar(R.string.auto_scan_unavailable)
        }
    }

    /**
     * Handle results from ML Kit Document Scanner
     */
    private fun handleScannerResult(result: ActivityResult) {
        val scanResult = DocumentScanner.parseResult(result.resultCode, result.data)
        
        if (scanResult == null || scanResult.pageUris.isEmpty()) {
            return
        }

        // Add all scanned pages to the ViewModel
        scanResult.pageUris.forEach { uri ->
            viewModel.addPage(uri)
        }

        // Navigate to pages screen
        findNavController().navigate(R.id.action_home_to_pages)
    }

    /**
     * Handle results from gallery import
     */
    private fun handleGalleryResult(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val ctx = context ?: return

        // Validate MIME types (SEC-07)
        val validUris = uris.filter { uri ->
            InputValidator.isAllowedMimeType(ctx, uri)
        }
        if (validUris.isEmpty()) {
            showSnackbar("Unsupported file type")
            return
        }

        // Add imported images to ViewModel with EXIF correction
        validUris.forEach { uri ->
            val correctedUri = com.pdfscanner.app.util.ImageUtils.correctExifOrientation(ctx, uri)
            viewModel.addPage(correctedUri)
        }

        // Navigate to pages screen to crop/edit
        if (validUris.size == 1) {
            // Single image - go to preview for cropping
            val correctedFirst = viewModel.pages.value?.lastOrNull() ?: validUris.first()
            viewModel.setCurrentCapture(correctedFirst)
            val action = HomeFragmentDirections.actionHomeToPreview(correctedFirst.toString())
            findNavController().navigate(action)
        } else {
            // Multiple images - go to pages
            findNavController().navigate(R.id.action_home_to_pages)
        }
    }

    /**
     * Handle results from import (images + PDFs)
     * Separates images and PDFs, extracts PDF pages as images
     */
    private fun handleImportResult(uris: List<Uri>) {
        if (uris.isEmpty()) return

        val ctx = context ?: return

        // Validate MIME types -- reject unsupported files (SEC-07)
        val validUris = uris.filter { uri ->
            InputValidator.isAllowedMimeType(ctx, uri)
        }
        if (validUris.isEmpty()) {
            showSnackbar("Unsupported file type")
            return
        }

        // Separate images and PDFs
        val imageUris = mutableListOf<Uri>()
        val pdfUris = mutableListOf<Uri>()

        validUris.forEach { uri ->
            if (PdfPageExtractor.isPdfFile(ctx, uri)) {
                pdfUris.add(uri)
            } else {
                imageUris.add(uri)
            }
        }

        // If no PDFs, handle as normal image import
        if (pdfUris.isEmpty()) {
            handleGalleryResult(imageUris)
            return
        }

        // Show loading and process PDFs
        showImportProgress(true, "Importing files...")

        lifecycleScope.launch {
            try {
                // First add any images (with EXIF correction)
                imageUris.forEach { uri ->
                    val correctedUri = com.pdfscanner.app.util.ImageUtils.correctExifOrientation(ctx, uri)
                    viewModel.addPage(correctedUri)
                }

                // Then extract pages from each PDF
                var totalPagesExtracted = 0
                for ((index, pdfUri) in pdfUris.withIndex()) {
                    showImportProgress(true, "Extracting PDF ${index + 1}/${pdfUris.size}...")

                    val result = PdfPageExtractor.extractPages(ctx, pdfUri)

                    if (result.success) {
                        result.pageUris.forEach { pageUri ->
                            viewModel.addPage(pageUri)
                        }
                        totalPagesExtracted += result.pageUris.size
                    } else {
                        showSnackbar(getString(R.string.error_import_pdf, result.errorMessage ?: ""))
                    }
                }

                _binding ?: return@launch
                val currentCtx = context ?: return@launch
                showImportProgress(false)

                val totalPages = imageUris.size + totalPagesExtracted
                if (totalPages > 0) {
                    showSnackbar(getString(R.string.imported_pages_count, totalPages))

                    // Navigate to pages
                    findNavController().navigate(R.id.action_home_to_pages)
                }

            } catch (e: Exception) {
                _binding ?: return@launch
                showImportProgress(false)
                showSnackbar(getString(R.string.error_importing, e.message ?: ""))
            }
        }
    }
    
    /**
     * Show/hide import progress indicator
     */
    private fun showImportProgress(show: Boolean, message: String = "") {
        // Use the loading overlay if available, otherwise just show a snackbar
        val currentBinding = _binding ?: return
        if (show) {
            currentBinding.root.isEnabled = false
            if (message.isNotEmpty()) showSnackbar(message)
        } else {
            currentBinding.root.isEnabled = true
        }
    }

    /**
     * Open a document from history using built-in PDF viewer
     */
    private fun openDocument(document: DocumentEntry) {
        if (!document.exists()) {
            showSnackbar(R.string.file_not_found)
            loadRecentDocuments()  // Refresh list
            return
        }

        // Open with built-in PDF viewer
        val action = HomeFragmentDirections.actionHomeToPdfViewer(
            pdfPath = document.filePath,
            pdfName = document.name
        )
        findNavController().navigate(action)
    }
    
    /**
     * Share a document
     */
    private fun shareDocument(document: DocumentEntry) {
        val file = java.io.File(document.filePath)
        if (!file.exists()) {
            showSnackbar(R.string.file_not_found)
            loadRecentDocuments()
            return
        }

        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )

            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, document.name)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.share_pdf)))
        } catch (e: Exception) {
            showSnackbar(R.string.error_sharing_pdf)
        }
    }
    
    /**
     * Confirm before deleting a document
     */
    private fun confirmDeleteDocument(document: DocumentEntry) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_document)
            .setMessage(getString(R.string.confirm_delete_document, document.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteDocument(document)
            }
            .show()
    }
    
    /**
     * Delete a document
     */
    private fun deleteDocument(document: DocumentEntry) {
        historyRepository.removeDocument(document.id, deleteFile = true)
        loadRecentDocuments()
        showSnackbar(R.string.document_deleted)
    }
    
    /**
     * Handle PDF picker results for merge/split/compress
     */
    private fun handlePdfPickerResult(uris: List<Uri>) {
        if (uris.isEmpty()) {
            currentPdfOperation = null
            return
        }
        
        // Take persistent permission for the URIs
        uris.forEach { uri ->
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Permission may not be available, continue anyway
            }
        }
        
        when (currentPdfOperation) {
            PdfOperation.MERGE -> performMerge(uris)
            PdfOperation.SPLIT -> performSplit(uris.first())
            PdfOperation.COMPRESS -> performCompress(uris.first())
            null -> {}
        }
        
        currentPdfOperation = null
    }
    
    /**
     * Merge multiple PDFs
     */
    private fun performMerge(uris: List<Uri>) {
        if (uris.size < 2) {
            showSnackbar(R.string.select_pdfs_to_merge)
            return
        }

        binding.loadingText.text = getString(R.string.merging_pdfs)
        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            val ctx = context ?: run {
                _binding?.loadingOverlay?.visibility = View.GONE
                return@launch
            }

            val result = PdfUtils.mergePdfs(
                context = ctx,
                pdfUris = uris,
                outputName = "Merged"
            )

            val currentBinding = _binding ?: return@launch
            val currentCtx = context ?: return@launch
            currentBinding.loadingOverlay.visibility = View.GONE

            if (result.success && result.outputUri != null) {
                // Add to history
                val file = java.io.File(result.outputUri.path!!)
                historyRepository.addDocument(
                    name = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    pageCount = uris.size  // Approximate
                )

                loadRecentDocuments()
                showSnackbar(result.message, Snackbar.LENGTH_LONG)
            } else {
                showSnackbar(result.message)
            }
        }
    }

    /**
     * Split a PDF into pages
     */
    private fun performSplit(uri: Uri) {
        binding.loadingText.text = getString(R.string.splitting_pdf)
        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            val ctx = context ?: run {
                _binding?.loadingOverlay?.visibility = View.GONE
                return@launch
            }

            val result = PdfUtils.splitPdf(
                context = ctx,
                pdfUri = uri,
                baseName = "Page"
            )

            val currentBinding = _binding ?: return@launch
            val currentCtx = context ?: return@launch
            currentBinding.loadingOverlay.visibility = View.GONE

            if (result.success && !result.outputUris.isNullOrEmpty()) {
                // Add each split page to history
                result.outputUris.forEach { pageUri ->
                    val file = java.io.File(pageUri.path!!)
                    historyRepository.addDocument(
                        name = file.nameWithoutExtension,
                        filePath = file.absolutePath,
                        pageCount = 1
                    )
                }

                loadRecentDocuments()
                showSnackbar(getString(R.string.split_success, result.outputUris.size), Snackbar.LENGTH_LONG)
            } else {
                showSnackbar(result.message)
            }
        }
    }
    
    /**
     * Compress a PDF
     */
    private fun performCompress(uri: Uri) {
        // Show compression level dialog
        val levels = arrayOf(
            getString(R.string.compression_low),
            getString(R.string.compression_medium),
            getString(R.string.compression_high)
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.compression_level)
            .setItems(levels) { _, which ->
                val level = when (which) {
                    0 -> PdfUtils.CompressionLevel.HIGH   // Low compression = high quality
                    1 -> PdfUtils.CompressionLevel.MEDIUM
                    else -> PdfUtils.CompressionLevel.LOW // High compression = low quality
                }
                executeCompress(uri, level)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Execute PDF compression
     */
    private fun executeCompress(uri: Uri, level: PdfUtils.CompressionLevel) {
        binding.loadingText.text = getString(R.string.compressing_pdf)
        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            val ctx = context ?: run {
                _binding?.loadingOverlay?.visibility = View.GONE
                return@launch
            }

            val result = PdfUtils.compressPdf(
                context = ctx,
                pdfUri = uri,
                level = level,
                outputName = "Compressed"
            )

            val currentBinding = _binding ?: return@launch
            val currentCtx = context ?: return@launch
            currentBinding.loadingOverlay.visibility = View.GONE

            if (result.success && result.outputUri != null) {
                val file = java.io.File(result.outputUri.path!!)
                historyRepository.addDocument(
                    name = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    pageCount = 1  // Unknown
                )

                loadRecentDocuments()
                showSnackbar(result.message, Snackbar.LENGTH_LONG)
            } else {
                showSnackbar(result.message)
            }
        }
    }

    /**
     * SEC-09: Check if unencrypted files need migration and run with progress dialog.
     *
     * Quick sentinel check avoids dialog flash on subsequent launches.
     * Zero-file case sets sentinel immediately without showing dialog.
     * Non-cancelable dialog prevents user from interrupting encryption mid-file.
     * Crash-safe: SecureFileManager.migrateExistingFiles is idempotent.
     */
    private fun checkAndRunMigration() {
        val ctx = context ?: return

        // Quick sentinel check to avoid dialog flash on subsequent launches
        val prefs = SecurePreferences.getInstance(ctx)
        if (prefs.getBoolean("_file_migration_complete", false)) return

        // Check if there are any files to migrate
        val dirs = listOf("scans", "processed", "pdfs")
        val fileCount = dirs.sumOf { dir ->
            File(ctx.filesDir, dir).listFiles()?.count { it.isFile } ?: 0
        }
        if (fileCount == 0) {
            // No files to migrate -- set sentinel and return
            prefs.edit().putBoolean("_file_migration_complete", true).apply()
            return
        }

        // Show non-cancelable progress dialog
        val progressView = layoutInflater.inflate(R.layout.dialog_migration_progress, null)
        val progressBar = progressView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progressMigration)
        val statusText = progressView.findViewById<android.widget.TextView>(R.id.textMigrationStatus)
        val countText = progressView.findViewById<android.widget.TextView>(R.id.textMigrationCount)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.migration_title)
            .setView(progressView)
            .setCancelable(false)
            .create()
        dialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                SecureFileManager.migrateExistingFiles(ctx.applicationContext) { current, total ->
                    launch(Dispatchers.Main) {
                        progressBar.max = total
                        progressBar.progress = current
                        statusText.text = getString(R.string.migration_status_encrypting, current, total)
                        countText.text = "$current / $total"
                    }
                }
            }
            dialog.dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
