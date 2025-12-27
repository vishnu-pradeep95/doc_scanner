/**
 * RecentDocumentsAdapter.kt - Adapter for Recent Documents on Home Screen
 * 
 * Displays horizontal list of recent PDF documents with previews
 */

package com.pdfscanner.app.adapter

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pdfscanner.app.R
import com.pdfscanner.app.data.DocumentEntry
import com.pdfscanner.app.databinding.ItemRecentDocumentBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adapter for displaying recent documents in horizontal scrolling list
 */
class RecentDocumentsAdapter(
    private val onDocumentClick: (DocumentEntry) -> Unit,
    private val onShareClick: ((DocumentEntry) -> Unit)? = null,
    private val onDeleteClick: ((DocumentEntry) -> Unit)? = null
) : ListAdapter<DocumentEntry, RecentDocumentsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentDocumentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemRecentDocumentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(document: DocumentEntry) {
            // Set document name
            binding.textDocName.text = document.name

            // Format and set date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.textDocDate.text = dateFormat.format(Date(document.createdAt))

            // Set page count
            binding.textDocPages.text = "${document.pageCount} page${if (document.pageCount > 1) "s" else ""}"

            // Load PDF thumbnail
            loadPdfThumbnail(document)

            // Click listener
            binding.root.setOnClickListener {
                onDocumentClick(document)
            }
            
            // Long-press listener - show popup menu
            binding.root.setOnLongClickListener { view ->
                val popup = PopupMenu(view.context, view)
                popup.menuInflater.inflate(R.menu.menu_recent_document, popup.menu)
                
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_share -> {
                            onShareClick?.invoke(document)
                            true
                        }
                        R.id.action_delete -> {
                            onDeleteClick?.invoke(document)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
                true
            }
        }

        private fun loadPdfThumbnail(document: DocumentEntry) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val file = File(document.filePath)
                    if (!file.exists()) return@launch

                    val fileDescriptor = ParcelFileDescriptor.open(
                        file,
                        ParcelFileDescriptor.MODE_READ_ONLY
                    )
                    val pdfRenderer = PdfRenderer(fileDescriptor)
                    
                    if (pdfRenderer.pageCount > 0) {
                        val page = pdfRenderer.openPage(0)
                        
                        // Create bitmap for thumbnail
                        val bitmap = Bitmap.createBitmap(
                            page.width,
                            page.height,
                            Bitmap.Config.ARGB_8888
                        )
                        
                        // Render page to bitmap
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        
                        page.close()
                        pdfRenderer.close()
                        fileDescriptor.close()

                        // Update UI on main thread
                        withContext(Dispatchers.Main) {
                            binding.imagePreview.setImageBitmap(bitmap)
                        }
                    } else {
                        pdfRenderer.close()
                        fileDescriptor.close()
                    }
                } catch (e: Exception) {
                    // Silently fail - will show default icon
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DocumentEntry>() {
        override fun areItemsTheSame(oldItem: DocumentEntry, newItem: DocumentEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DocumentEntry, newItem: DocumentEntry): Boolean {
            return oldItem == newItem
        }
    }
}
