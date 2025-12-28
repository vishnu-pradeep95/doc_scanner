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
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.pdfscanner.app.util.PdfPageExtractor
import com.pdfscanner.app.util.PdfUtils
import com.pdfscanner.app.viewmodel.ScannerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            findNavController().navigate(R.id.action_home_to_history)
        }
    }

    /**
     * Setup settings button
     */
    private fun setupSettings() {
        binding.btnSettings.setOnClickListener {
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
            Toast.makeText(
                requireContext(),
                R.string.auto_scan_unavailable,
                Toast.LENGTH_SHORT
            ).show()
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

        // Add imported images to ViewModel
        uris.forEach { uri ->
            viewModel.addPage(uri)
        }

        // Navigate to pages screen to crop/edit
        if (uris.size == 1) {
            // Single image - go to preview for cropping
            viewModel.setCurrentCapture(uris.first())
            val action = HomeFragmentDirections.actionHomeToPreview(uris.first().toString())
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
        
        // Separate images and PDFs
        val imageUris = mutableListOf<Uri>()
        val pdfUris = mutableListOf<Uri>()
        
        uris.forEach { uri ->
            if (PdfPageExtractor.isPdfFile(requireContext(), uri)) {
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
                // First add any images
                imageUris.forEach { uri ->
                    viewModel.addPage(uri)
                }
                
                // Then extract pages from each PDF
                var totalPagesExtracted = 0
                for ((index, pdfUri) in pdfUris.withIndex()) {
                    withContext(Dispatchers.Main) {
                        showImportProgress(true, "Extracting PDF ${index + 1}/${pdfUris.size}...")
                    }
                    
                    val result = PdfPageExtractor.extractPages(requireContext(), pdfUri)
                    
                    if (result.success) {
                        result.pageUris.forEach { pageUri ->
                            viewModel.addPage(pageUri)
                        }
                        totalPagesExtracted += result.pageUris.size
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireContext(),
                                "Error importing PDF: ${result.errorMessage}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    showImportProgress(false)
                    
                    val totalPages = imageUris.size + totalPagesExtracted
                    if (totalPages > 0) {
                        Toast.makeText(
                            requireContext(),
                            "✅ Imported $totalPages pages",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Navigate to pages
                        findNavController().navigate(R.id.action_home_to_pages)
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showImportProgress(false)
                    Toast.makeText(
                        requireContext(),
                        "Error importing: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    /**
     * Show/hide import progress indicator
     */
    private fun showImportProgress(show: Boolean, message: String = "") {
        // Use the loading overlay if available, otherwise just show a toast
        if (show) {
            binding.root.isEnabled = false
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        } else {
            binding.root.isEnabled = true
        }
    }

    /**
     * Open a document from history using built-in PDF viewer
     */
    private fun openDocument(document: DocumentEntry) {
        if (!document.exists()) {
            Toast.makeText(requireContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), R.string.error_sharing_pdf, Toast.LENGTH_SHORT).show()
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
        Toast.makeText(requireContext(), R.string.document_deleted, Toast.LENGTH_SHORT).show()
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
            Toast.makeText(requireContext(), R.string.select_pdfs_to_merge, Toast.LENGTH_SHORT).show()
            return
        }
        
        binding.loadingText.text = getString(R.string.merging_pdfs)
        binding.loadingOverlay.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val result = PdfUtils.mergePdfs(
                context = requireContext(),
                pdfUris = uris,
                outputName = "Merged"
            )
            
            binding.loadingOverlay.visibility = View.GONE
            
            if (result.success && result.outputUri != null) {
                // Add to history
                val file = java.io.File(result.outputUri.path!!)
                historyRepository.addDocument(
                    name = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    pageCount = uris.size  // Approximate
                )
                
                loadRecentDocuments()
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
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
            val result = PdfUtils.splitPdf(
                context = requireContext(),
                pdfUri = uri,
                baseName = "Page"
            )
            
            binding.loadingOverlay.visibility = View.GONE
            
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
                Toast.makeText(
                    requireContext(),
                    getString(R.string.split_success, result.outputUris.size),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
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
            val result = PdfUtils.compressPdf(
                context = requireContext(),
                pdfUri = uri,
                level = level,
                outputName = "Compressed"
            )
            
            binding.loadingOverlay.visibility = View.GONE
            
            if (result.success && result.outputUri != null) {
                val file = java.io.File(result.outputUri.path!!)
                historyRepository.addDocument(
                    name = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    pageCount = 1  // Unknown
                )
                
                loadRecentDocuments()
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
