/**
 * HistoryFragment.kt - Document History Screen
 * 
 * Displays a list of previously created PDFs with options to:
 * - Open/view a PDF
 * - Share a PDF
 * - Delete a PDF
 * - Merge multiple PDFs (long-press to select)
 * - Compress PDFs to reduce file size
 * 
 * Uses DocumentHistoryRepository for data persistence.
 */

package com.pdfscanner.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pdfscanner.app.R
import com.pdfscanner.app.adapter.HistoryAdapter
import com.pdfscanner.app.data.DocumentEntry
import com.pdfscanner.app.data.DocumentHistoryRepository
import com.pdfscanner.app.databinding.FragmentHistoryBinding
import com.pdfscanner.app.util.PdfUtils
import kotlinx.coroutines.launch
import java.io.File

/**
 * Fragment displaying list of previously created PDFs
 * with support for merge and compress operations
 */
class HistoryFragment : Fragment() {

    // View Binding
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    // Repository for document history
    private lateinit var repository: DocumentHistoryRepository
    
    // RecyclerView adapter
    private lateinit var historyAdapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize repository
        repository = DocumentHistoryRepository.getInstance(requireContext())
        
        // Setup toolbar navigation
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        
        // Setup selection toolbar
        setupSelectionToolbar()
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Load documents
        loadDocuments()
    }
    
    /**
     * Setup selection toolbar with merge/compress actions
     */
    private fun setupSelectionToolbar() {
        binding.selectionToolbar.setNavigationOnClickListener {
            historyAdapter.exitSelectionMode()
        }
        
        binding.selectionToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_merge -> {
                    performMerge()
                    true
                }
                R.id.action_split -> {
                    performSplit()
                    true
                }
                R.id.action_compress -> {
                    performCompress()
                    true
                }
                R.id.action_select_all -> {
                    historyAdapter.selectAll()
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * Initialize RecyclerView with adapter
     */
    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            onItemClick = { document -> openDocument(document) },
            onShareClick = { document -> shareDocument(document) },
            onDeleteClick = { document -> confirmDelete(document) },
            onLongClick = { _ -> /* Selection mode started */ }
        )
        
        // Handle selection changes
        historyAdapter.onSelectionChanged = { count, isSelectionMode ->
            updateSelectionUI(count, isSelectionMode)
        }
        
        binding.recyclerHistory.adapter = historyAdapter
    }
    
    /**
     * Update UI based on selection mode
     */
    private fun updateSelectionUI(count: Int, isSelectionMode: Boolean) {
        if (isSelectionMode) {
            binding.toolbar.visibility = View.GONE
            binding.selectionToolbar.visibility = View.VISIBLE
            binding.selectionToolbar.title = getString(R.string.selected_count, count)
            binding.helpText.visibility = View.GONE
        } else {
            binding.toolbar.visibility = View.VISIBLE
            binding.selectionToolbar.visibility = View.GONE
            binding.helpText.visibility = View.VISIBLE
        }
    }
    
    /**
     * Merge selected PDFs
     */
    private fun performMerge() {
        val selected = historyAdapter.getSelectedDocuments()
        if (selected.size < 2) {
            Toast.makeText(requireContext(), R.string.select_pdfs_to_merge, Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show merge confirmation dialog
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.merge_pdfs)
            .setMessage("Merge ${selected.size} PDFs into one document?")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.merge_pdfs) { _, _ ->
                executeMerge(selected)
            }
            .show()
    }
    
    /**
     * Execute the merge operation
     */
    private fun executeMerge(documents: List<DocumentEntry>) {
        binding.loadingText.text = getString(R.string.merging_pdfs)
        binding.loadingOverlay.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val uris = documents.mapNotNull { doc ->
                val file = File(doc.filePath)
                if (file.exists()) Uri.fromFile(file) else null
            }
            
            val result = PdfUtils.mergePdfs(
                context = requireContext(),
                pdfUris = uris,
                outputName = "Merged"
            )
            
            binding.loadingOverlay.visibility = View.GONE
            
            if (result.success && result.outputUri != null) {
                // Add to history
                val file = File(result.outputUri.path!!)
                repository.addDocument(
                    name = file.nameWithoutExtension,
                    filePath = file.absolutePath,
                    pageCount = documents.sumOf { it.pageCount }
                )
                
                historyAdapter.exitSelectionMode()
                loadDocuments()
                
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Split selected PDF into individual pages
     */
    private fun performSplit() {
        val selected = historyAdapter.getSelectedDocuments()
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.select_pdf_to_split, Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selected.size > 1) {
            Toast.makeText(requireContext(), "Select only one PDF to split", Toast.LENGTH_SHORT).show()
            return
        }
        
        val doc = selected.first()
        if (doc.pageCount <= 1) {
            Toast.makeText(requireContext(), "PDF has only one page", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Confirm split
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.split_pdf)
            .setMessage("Split \"${doc.name}\" into ${doc.pageCount} separate PDFs?")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.split_pdf) { _, _ ->
                executeSplit(doc)
            }
            .show()
    }
    
    /**
     * Execute the split operation
     */
    private fun executeSplit(document: DocumentEntry) {
        binding.loadingText.text = getString(R.string.splitting_pdf)
        binding.loadingOverlay.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            val file = File(document.filePath)
            if (!file.exists()) {
                binding.loadingOverlay.visibility = View.GONE
                Toast.makeText(requireContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val result = PdfUtils.splitPdf(
                context = requireContext(),
                pdfUri = Uri.fromFile(file),
                baseName = document.name
            )
            
            binding.loadingOverlay.visibility = View.GONE
            
            if (result.success && !result.outputUris.isNullOrEmpty()) {
                // Add each split page to history
                result.outputUris.forEachIndexed { index, uri ->
                    val splitFile = File(uri.path!!)
                    repository.addDocument(
                        name = splitFile.nameWithoutExtension,
                        filePath = splitFile.absolutePath,
                        pageCount = 1
                    )
                }
                
                historyAdapter.exitSelectionMode()
                loadDocuments()
                
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
     * Compress selected PDF(s)
     */
    private fun performCompress() {
        val selected = historyAdapter.getSelectedDocuments()
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), R.string.select_pdf_to_compress, Toast.LENGTH_SHORT).show()
            return
        }
        
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
                executeCompress(selected, level)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    /**
     * Execute compression on selected documents
     */
    private fun executeCompress(documents: List<DocumentEntry>, level: PdfUtils.CompressionLevel) {
        binding.loadingText.text = getString(R.string.compressing_pdf)
        binding.loadingOverlay.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            var successCount = 0
            var lastMessage = ""
            
            for (doc in documents) {
                val file = File(doc.filePath)
                if (!file.exists()) continue
                
                val result = PdfUtils.compressPdf(
                    context = requireContext(),
                    pdfUri = Uri.fromFile(file),
                    level = level,
                    outputName = "${doc.name}_compressed"
                )
                
                if (result.success && result.outputUri != null) {
                    successCount++
                    lastMessage = result.message
                    
                    // Add compressed version to history
                    val compressedFile = File(result.outputUri.path!!)
                    repository.addDocument(
                        name = compressedFile.nameWithoutExtension,
                        filePath = compressedFile.absolutePath,
                        pageCount = doc.pageCount
                    )
                }
            }
            
            binding.loadingOverlay.visibility = View.GONE
            historyAdapter.exitSelectionMode()
            loadDocuments()
            
            if (successCount > 0) {
                val message = if (documents.size == 1) lastMessage 
                    else "Compressed $successCount files"
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Compression failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Load documents from repository and update UI
     */
    private fun loadDocuments() {
        val documents = repository.getAllDocuments()
        
        if (documents.isEmpty()) {
            binding.recyclerHistory.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
            binding.helpText.visibility = View.GONE
        } else {
            binding.recyclerHistory.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
            binding.helpText.visibility = View.VISIBLE
            historyAdapter.submitList(documents)
        }
    }
    
    /**
     * Open a PDF document using system viewer
     */
    private fun openDocument(document: DocumentEntry) {
        try {
            val file = File(document.filePath)
            if (!file.exists()) {
                Toast.makeText(requireContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show()
                loadDocuments()  // Refresh list to remove missing files
                return
            }
            
            // Get content URI via FileProvider
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            
            // Create intent to view PDF
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Check if there's an app to handle PDFs
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), R.string.no_pdf_viewer, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.error_opening_pdf, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Share a PDF document
     */
    private fun shareDocument(document: DocumentEntry) {
        try {
            val file = File(document.filePath)
            if (!file.exists()) {
                Toast.makeText(requireContext(), R.string.file_not_found, Toast.LENGTH_SHORT).show()
                loadDocuments()
                return
            }
            
            // Get content URI via FileProvider
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                file
            )
            
            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, document.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_pdf)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.error_sharing_pdf, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Show confirmation dialog before deleting
     */
    private fun confirmDelete(document: DocumentEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_document)
            .setMessage(getString(R.string.confirm_delete_document, document.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteDocument(document)
            }
            .show()
    }
    
    /**
     * Delete a document from history and storage
     */
    private fun deleteDocument(document: DocumentEntry) {
        repository.removeDocument(document.id, deleteFile = true)
        loadDocuments()  // Refresh list
        Toast.makeText(requireContext(), R.string.document_deleted, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
