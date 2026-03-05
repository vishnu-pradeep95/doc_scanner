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

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.pdfscanner.app.R
import com.pdfscanner.app.data.DocumentEntry
import com.pdfscanner.app.databinding.ItemDocumentBinding
import com.pdfscanner.app.util.SecureFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
 * @param onEditClick Called when user taps edit button
 * @param onShareClick Called when user taps share button
 * @param onDeleteClick Called when user taps delete button
 * @param onLongClick Called when user long-presses for selection
 */
class HistoryAdapter(
    private val onItemClick: (DocumentEntry) -> Unit,
    private val onEditClick: ((DocumentEntry) -> Unit)? = null,
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
            
            // Document name - fallback to filename if name is empty
            val displayName = if (document.name.isNotBlank()) {
                document.name
            } else {
                // Extract filename without extension as fallback
                File(document.filePath).nameWithoutExtension
            }
            binding.textDocumentName.text = displayName

            // Load document thumbnail -- decrypt encrypted PDF to render first page (SEC-09)
            val file = File(document.filePath)
            if (file.exists()) {
                binding.root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                    val bitmap = renderPdfThumbnail(file)
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            binding.imageDocumentThumbnail.load(bitmap) {
                                crossfade(true)
                                placeholder(R.drawable.ic_cartoon_document)
                            }
                        } else {
                            binding.imageDocumentThumbnail.setImageResource(R.drawable.ic_cartoon_document)
                        }
                    }
                } ?: run {
                    binding.imageDocumentThumbnail.setImageResource(R.drawable.ic_cartoon_document)
                }
            } else {
                binding.imageDocumentThumbnail.setImageResource(R.drawable.ic_cartoon_document)
            }

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
                binding.btnEdit.visibility = View.GONE
                binding.btnShare.visibility = View.GONE
                binding.btnDelete.visibility = View.GONE
            } else {
                binding.root.isActivated = false
                binding.root.alpha = 1.0f
                binding.btnEdit.visibility = View.VISIBLE
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
            
            binding.btnEdit.setOnClickListener { onEditClick?.invoke(document) }
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
        // Notify fragment that selection mode started
        onSelectionChanged?.invoke(0, true)
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
    
    /**
     * Render first page of an encrypted PDF as a thumbnail bitmap.
     * Decrypts to temp, opens with PdfRenderer, renders page 0, cleans up.
     * Must be called from Dispatchers.IO.
     */
    private fun renderPdfThumbnail(file: File): Bitmap? {
        var tempFile: File? = null
        return try {
            // Decrypt to temp file for PdfRenderer (no Context needed)
            tempFile = File.createTempFile("hist_thumb_", ".tmp", file.parentFile)
            SecureFileManager.decryptFromFile(file).use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                renderer.close()
                pfd.close()
                bitmap
            } else {
                renderer.close()
                pfd.close()
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            tempFile?.delete()
        }
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
