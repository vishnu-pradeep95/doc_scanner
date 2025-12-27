/**
 * HistoryAdapter.kt - RecyclerView Adapter for Document History
 * 
 * Displays list of previously created PDFs with:
 * - Document name
 * - Page count, file size, and creation date
 * - Share and delete actions
 * - Multi-selection support for merge/compress operations
 */

package com.pdfscanner.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pdfscanner.app.R
import com.pdfscanner.app.data.DocumentEntry
import com.pdfscanner.app.databinding.ItemDocumentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter for displaying document history items
 * 
 * LISTADAPTER vs RECYCLERVIEW.ADAPTER:
 * ListAdapter uses DiffUtil internally for efficient updates.
 * When you submit a new list, it automatically calculates
 * which items changed and animates only those.
 * 
 * @param onItemClick Called when user taps on a document (to open/view)
 * @param onShareClick Called when user taps share button
 * @param onDeleteClick Called when user taps delete button
 * @param onLongClick Called when user long-presses for selection
 */
class HistoryAdapter(
    private val onItemClick: (DocumentEntry) -> Unit,
    private val onShareClick: (DocumentEntry) -> Unit,
    private val onDeleteClick: (DocumentEntry) -> Unit,
    private val onLongClick: ((DocumentEntry) -> Unit)? = null
) : ListAdapter<DocumentEntry, HistoryAdapter.DocumentViewHolder>(DocumentDiffCallback()) {

    // Selection mode state
    var isSelectionMode = false
        private set
    
    private val selectedItems = mutableSetOf<String>() // Document IDs
    
    // Callback when selection changes
    var onSelectionChanged: ((count: Int, isSelectionMode: Boolean) -> Unit)? = null

    /**
     * ViewHolder for document items
     * 
     * Using View Binding for type-safe view access
     */
    inner class DocumentViewHolder(
        private val binding: ItemDocumentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        /**
         * Bind document data to views
         */
        fun bind(document: DocumentEntry) {
            val isSelected = selectedItems.contains(document.id)
            
            // Document name
            binding.textDocumentName.text = document.name
            
            // Format details: "5 pages • 1.2 MB • Dec 26, 2025"
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(document.createdAt))
            val pagesText = if (document.pageCount == 1) "1 page" else "${document.pageCount} pages"
            
            binding.textDocumentDetails.text = buildString {
                append(pagesText)
                append(" • ")
                append(document.formattedSize())
                append(" • ")
                append(formattedDate)
            }
            
            // Selection state visual feedback
            if (isSelectionMode) {
                binding.root.isActivated = isSelected
                binding.root.alpha = if (isSelected) 1.0f else 0.7f
                // Hide action buttons in selection mode
                binding.btnShare.visibility = View.GONE
                binding.btnDelete.visibility = View.GONE
            } else {
                binding.root.isActivated = false
                binding.root.alpha = 1.0f
                binding.btnShare.visibility = View.VISIBLE
                binding.btnDelete.visibility = View.VISIBLE
            }
            
            // Click listeners
            binding.root.setOnClickListener { 
                if (isSelectionMode) {
                    toggleSelection(document)
                } else {
                    onItemClick(document) 
                }
            }
            
            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    enterSelectionMode()
                    toggleSelection(document)
                    onLongClick?.invoke(document)
                }
                true
            }
            
            binding.btnShare.setOnClickListener { onShareClick(document) }
            binding.btnDelete.setOnClickListener { onDeleteClick(document) }
        }
    }
    
    /**
     * Enter selection mode
     */
    private fun enterSelectionMode() {
        isSelectionMode = true
        selectedItems.clear()
        notifyDataSetChanged()
    }
    
    /**
     * Toggle selection of a document
     */
    private fun toggleSelection(document: DocumentEntry) {
        if (selectedItems.contains(document.id)) {
            selectedItems.remove(document.id)
        } else {
            selectedItems.add(document.id)
        }
        
        // Exit selection mode if nothing selected
        if (selectedItems.isEmpty()) {
            exitSelectionMode()
        } else {
            notifyDataSetChanged()
            onSelectionChanged?.invoke(selectedItems.size, true)
        }
    }
    
    /**
     * Exit selection mode
     */
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0, false)
    }
    
    /**
     * Get selected document entries
     */
    fun getSelectedDocuments(): List<DocumentEntry> {
        return currentList.filter { selectedItems.contains(it.id) }
    }
    
    /**
     * Get count of selected items
     */
    fun getSelectedCount(): Int = selectedItems.size
    
    /**
     * Select all items
     */
    fun selectAll() {
        if (!isSelectionMode) {
            isSelectionMode = true
        }
        selectedItems.clear()
        currentList.forEach { selectedItems.add(it.id) }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedItems.size, true)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val binding = ItemDocumentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DocumentViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    /**
     * DiffUtil callback for efficient list updates
     * 
     * DIFFUTIL:
     * Compares old and new lists to find minimal set of changes.
     * Much more efficient than notifyDataSetChanged() for partial updates.
     */
    class DocumentDiffCallback : DiffUtil.ItemCallback<DocumentEntry>() {
        override fun areItemsTheSame(oldItem: DocumentEntry, newItem: DocumentEntry): Boolean {
            // Same ID = same item (even if contents changed)
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: DocumentEntry, newItem: DocumentEntry): Boolean {
            // Full equality check for content changes
            return oldItem == newItem
        }
    }
}
