/**
 * HomeFragment.kt - Landing Page / Home Screen
 * 
 * PURPOSE:
 * Serves as the main entry point for the app, providing:
 * - Quick access to scanning features (New Scan, Auto Scan, Import)
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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.pdfscanner.app.R
import com.pdfscanner.app.adapter.RecentDocumentsAdapter
import com.pdfscanner.app.data.DocumentEntry
import com.pdfscanner.app.data.DocumentHistoryRepository
import com.pdfscanner.app.databinding.FragmentHomeBinding
import com.pdfscanner.app.util.DocumentScanner
import com.pdfscanner.app.viewmodel.ScannerViewModel

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
     * Setup quick action card click listeners
     */
    private fun setupQuickActions() {
        // New Scan - Navigate to camera
        binding.cardNewScan.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_camera)
        }

        // Auto Scan - Launch ML Kit Document Scanner
        binding.cardAutoScan.setOnClickListener {
            startAutoScan()
        }

        // Import - Open gallery picker
        binding.cardImport.setOnClickListener {
            galleryLauncher.launch("image/*")
        }
    }

    /**
     * Setup recent documents RecyclerView
     */
    private fun setupRecentDocuments() {
        recentDocsAdapter = RecentDocumentsAdapter(
            onDocumentClick = { document ->
                openDocument(document)
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
     * Open a document from history
     */
    private fun openDocument(document: DocumentEntry) {
        if (!document.exists()) {
            Toast.makeText(requireContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show()
            loadRecentDocuments()  // Refresh list
            return
        }

        // Open PDF with external viewer or share
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(
                    androidx.core.content.FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.provider",
                        java.io.File(document.filePath)
                    ),
                    "application/pdf"
                )
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.no_pdf_viewer, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
