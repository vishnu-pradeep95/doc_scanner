/**
 * HistoryFragment.kt - Document History Screen
 * 
 * Displays a list of previously created PDFs with options to:
 * - Open/view a PDF
 * - Share a PDF
 * - Delete a PDF
 * 
 * Uses DocumentHistoryRepository for data persistence.
 */

package com.pdfscanner.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pdfscanner.app.R
import com.pdfscanner.app.adapter.HistoryAdapter
import com.pdfscanner.app.data.DocumentEntry
import com.pdfscanner.app.data.DocumentHistoryRepository
import com.pdfscanner.app.databinding.FragmentHistoryBinding
import java.io.File

/**
 * Fragment displaying list of previously created PDFs
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
        
        // Setup RecyclerView
        setupRecyclerView()
        
        // Load documents
        loadDocuments()
    }
    
    /**
     * Initialize RecyclerView with adapter
     */
    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            onItemClick = { document -> openDocument(document) },
            onShareClick = { document -> shareDocument(document) },
            onDeleteClick = { document -> confirmDelete(document) }
        )
        
        binding.recyclerHistory.adapter = historyAdapter
    }
    
    /**
     * Load documents from repository and update UI
     */
    private fun loadDocuments() {
        val documents = repository.getAllDocuments()
        
        if (documents.isEmpty()) {
            binding.recyclerHistory.visibility = View.GONE
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.recyclerHistory.visibility = View.VISIBLE
            binding.emptyState.visibility = View.GONE
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
