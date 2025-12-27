/**
 * PagesAdapter.kt - RecyclerView Adapter for Page Thumbnails
 * 
 * FEATURES:
 * - Grid display with thumbnails
 * - Multi-selection mode with long press
 * - Selection order tracking for PDF generation
 * - Drag & drop reordering
 * - Efficient thumbnail caching
 */

package com.pdfscanner.app.adapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.pdfscanner.app.databinding.ItemPageBinding

/**
 * PagesAdapter - Displays page thumbnails with multi-selection & drag-to-reorder
 * 
 * @param onDeleteClick Callback when delete button is clicked
 * @param onItemClick Callback when page is tapped (for editing) - not called in selection mode
 * @param onItemMoved Callback when items are reordered via drag
 * @param onDragStarted Callback to start drag (passes ViewHolder)
 * @param onSelectionChanged Callback when selection changes (count, isInSelectionMode)
 * @param onRotateClick Callback when rotate button is clicked
 */
class PagesAdapter(
    private val onDeleteClick: (Int) -> Unit,
    private val onItemClick: ((Int) -> Unit)? = null,
    private val onItemMoved: (fromPosition: Int, toPosition: Int) -> Unit = { _, _ -> },
    private val onDragStarted: ((RecyclerView.ViewHolder) -> Unit)? = null,
    private val onSelectionChanged: ((selectedCount: Int, isSelectionMode: Boolean) -> Unit)? = null,
    private val onRotateClick: ((Int) -> Unit)? = null
) : ListAdapter<Uri, PagesAdapter.PageViewHolder>(PageDiffCallback()) {

    // ============================================================
    // MULTI-SELECTION STATE
    // ============================================================

    /**
     * Selection mode flag
     * When true, taps toggle selection instead of opening editor
     */
    var isSelectionMode: Boolean = false
        private set

    /**
     * Selected items - maps position to selection order (1-based)
     * Selection order determines PDF page order when creating from selection
     */
    private val selectedItems = mutableMapOf<Int, Int>()

    /**
     * Counter for selection order
     */
    private var selectionCounter = 0

    // ============================================================
    // THUMBNAIL CACHE
    // ============================================================

    private val thumbnailCache: LruCache<String, Bitmap> = run {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ============================================================
    // SELECTION METHODS
    // ============================================================

    /**
     * Start selection mode with initial item
     */
    fun startSelectionMode(position: Int) {
        isSelectionMode = true
        selectionCounter = 1
        selectedItems.clear()
        selectedItems[position] = selectionCounter
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedItems.size, true)
    }

    /**
     * Toggle selection for an item
     */
    fun toggleSelection(position: Int) {
        if (selectedItems.containsKey(position)) {
            // Deselect - remove and update order numbers
            val removedOrder = selectedItems[position]!!
            selectedItems.remove(position)
            
            // Update order numbers for items selected after this one
            selectedItems.entries.forEach { entry ->
                if (entry.value > removedOrder) {
                    selectedItems[entry.key] = entry.value - 1
                }
            }
            selectionCounter--
        } else {
            // Select with next order number
            selectionCounter++
            selectedItems[position] = selectionCounter
        }
        
        notifyItemChanged(position)
        
        // Exit selection mode if nothing selected
        if (selectedItems.isEmpty()) {
            exitSelectionMode()
        } else {
            onSelectionChanged?.invoke(selectedItems.size, true)
        }
    }

    /**
     * Check if an item is selected
     */
    fun isSelected(position: Int): Boolean = selectedItems.containsKey(position)

    /**
     * Get selection order for an item (1-based, or 0 if not selected)
     */
    fun getSelectionOrder(position: Int): Int = selectedItems[position] ?: 0

    /**
     * Get all selected positions in selection order
     */
    fun getSelectedPositionsInOrder(): List<Int> {
        return selectedItems.entries
            .sortedBy { it.value }
            .map { it.key }
    }

    /**
     * Get all selected URIs in selection order
     */
    fun getSelectedUrisInOrder(): List<Uri> {
        return getSelectedPositionsInOrder().mapNotNull { position ->
            if (position < currentList.size) currentList[position] else null
        }
    }

    /**
     * Get count of selected items
     */
    fun getSelectedCount(): Int = selectedItems.size

    /**
     * Exit selection mode and clear selections
     */
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        selectionCounter = 0
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0, false)
    }

    /**
     * Select all items
     */
    fun selectAll() {
        isSelectionMode = true
        selectedItems.clear()
        selectionCounter = 0
        for (i in 0 until itemCount) {
            selectionCounter++
            selectedItems[i] = selectionCounter
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedItems.size, true)
    }

    // ============================================================
    // ADAPTER METHODS
    // ============================================================

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    // ============================================================
    // VIEWHOLDER
    // ============================================================

    inner class PageViewHolder(
        private val binding: ItemPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(uri: Uri, position: Int) {
            // Page number
            binding.textPageNumber.text = (position + 1).toString()

            // Selection state
            val isItemSelected = isSelected(position)
            val selectionOrder = getSelectionOrder(position)

            // Show/hide selection UI elements
            if (isSelectionMode) {
                binding.selectionOverlay.visibility = if (isItemSelected) View.VISIBLE else View.GONE
                binding.textSelectionOrder.visibility = if (isItemSelected) View.VISIBLE else View.GONE
                binding.checkboxSelect.visibility = View.GONE  // We use order badge instead
                
                if (isItemSelected) {
                    binding.textSelectionOrder.text = selectionOrder.toString()
                }
                
                // Hide normal buttons in selection mode
                binding.btnDelete.visibility = View.GONE
                binding.btnRotate.visibility = View.GONE
                binding.dragHandle.visibility = View.GONE
            } else {
                binding.selectionOverlay.visibility = View.GONE
                binding.textSelectionOrder.visibility = View.GONE
                binding.checkboxSelect.visibility = View.GONE
                binding.btnDelete.visibility = View.VISIBLE
                binding.btnRotate.visibility = View.VISIBLE
                binding.dragHandle.visibility = View.VISIBLE
            }

            // Thumbnail loading with caching
            val cacheKey = uri.toString()
            val cachedBitmap = thumbnailCache.get(cacheKey)
            
            if (cachedBitmap != null) {
                binding.imageThumbnail.setImageBitmap(cachedBitmap)
            } else {
                binding.imageThumbnail.setImageDrawable(null)
                binding.imageThumbnail.tag = cacheKey
                
                adapterScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        loadThumbnail(uri, binding.root.context)
                    }
                    
                    if (binding.imageThumbnail.tag == cacheKey && bitmap != null) {
                        thumbnailCache.put(cacheKey, bitmap)
                        binding.imageThumbnail.setImageBitmap(bitmap)
                    }
                }
            }

            // Click handlers
            binding.root.setOnClickListener {
                val currentPosition = bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    if (isSelectionMode) {
                        toggleSelection(currentPosition)
                    } else {
                        onItemClick?.invoke(currentPosition)
                    }
                }
            }

            binding.root.setOnLongClickListener {
                val currentPosition = bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION && !isSelectionMode) {
                    startSelectionMode(currentPosition)
                }
                true
            }

            // Delete button (only in normal mode)
            binding.btnDelete.setOnClickListener {
                val currentPosition = bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onDeleteClick(currentPosition)
                }
            }

            // Rotate button (only in normal mode)
            binding.btnRotate.setOnClickListener {
                val currentPosition = bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    onRotateClick?.invoke(currentPosition)
                }
            }

            // Drag handle (only in normal mode)
            binding.dragHandle.setOnTouchListener { _, event ->
                if (!isSelectionMode && event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onDragStarted?.invoke(this)
                }
                false
            }
        }
        
        private fun loadThumbnail(uri: Uri, context: android.content.Context): Bitmap? {
            return try {
                var width = 0
                var height = 0
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    width = options.outWidth
                    height = options.outHeight
                }

                val targetSize = 200
                val inSampleSize = calculateInSampleSize(width, height, targetSize, targetSize)

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        this.inSampleSize = inSampleSize
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                }
            } catch (e: Exception) {
                null
            }
        }
        
        private fun calculateInSampleSize(
            srcWidth: Int,
            srcHeight: Int,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            var inSampleSize = 1
            
            if (srcHeight > reqHeight || srcWidth > reqWidth) {
                val halfHeight = srcHeight / 2
                val halfWidth = srcWidth / 2
                
                while (halfHeight / inSampleSize >= reqHeight && 
                       halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            
            return inSampleSize
        }
    }

    // ============================================================
    // DIFFUTIL CALLBACK
    // ============================================================

    class PageDiffCallback : DiffUtil.ItemCallback<Uri>() {
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean = oldItem == newItem
    }

    // ============================================================
    // DRAG & DROP SUPPORT
    // ============================================================

    fun moveItem(fromPosition: Int, toPosition: Int) {
        val currentList = currentList.toMutableList()
        val item = currentList.removeAt(fromPosition)
        currentList.add(toPosition, item)
        submitList(currentList)
        onItemMoved(fromPosition, toPosition)
    }

    class DragCallback(
        private val adapter: PagesAdapter
    ) : ItemTouchHelper.Callback() {

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            // Disable drag in selection mode
            if (adapter.isSelectionMode) {
                return makeMovementFlags(0, 0)
            }
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or 
                           ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            return makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun isLongPressDragEnabled(): Boolean = false  // Use drag handle instead

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.alpha = 0.7f
                viewHolder?.itemView?.scaleX = 1.05f
                viewHolder?.itemView?.scaleY = 1.05f
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.alpha = 1.0f
            viewHolder.itemView.scaleX = 1.0f
            viewHolder.itemView.scaleY = 1.0f
        }
    }
}
