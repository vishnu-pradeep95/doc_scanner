/**
 * PagesAdapter.kt - RecyclerView Adapter for Page Thumbnails
 * 
 * RECYCLERVIEW PATTERN:
 * RecyclerView is Android's efficient way to display lists/grids.
 * 
 * THE PROBLEM IT SOLVES:
 * If you have 1000 items and create 1000 views, you waste memory.
 * RecyclerView creates only enough views to fill the screen (~10-15),
 * then RECYCLES them as user scrolls.
 * 
 * COMPONENTS:
 * 1. RecyclerView - the container view
 * 2. LayoutManager - arranges items (linear, grid, staggered)
 * 3. Adapter - creates views and binds data
 * 4. ViewHolder - holds references to views (avoids repeated findViewById)
 * 
 * LISTADAPTER:
 * ListAdapter is a RecyclerView.Adapter subclass that:
 * - Uses DiffUtil to efficiently compute list changes
 * - Automatically animates insertions/deletions
 * - Handles the "notify" calls for you
 * 
 * DRAG & DROP:
 * ItemTouchHelper is used to enable drag-to-reorder functionality.
 * When user long-presses and drags, we swap items in the list.
 * 
 * THUMBNAIL OPTIMIZATION:
 * - Use inSampleSize to load downsampled images (1/16 pixels)
 * - Cache bitmaps in a LruCache keyed by URI
 * - Load thumbnails asynchronously to avoid blocking UI
 * 
 * ANALOGY:
 * Think of it like a printing press:
 * - The press (RecyclerView) holds paper
 * - The template (ViewHolder) is the layout
 * - The data (your list) is the content
 * - The operator (Adapter) feeds data into templates
 */

package com.pdfscanner.app.adapter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil  // Computes list differences
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter  // Adapter with DiffUtil built-in
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// View Binding for the item layout
import com.pdfscanner.app.databinding.ItemPageBinding

/**
 * PagesAdapter - Displays page thumbnails in a grid with drag-to-reorder
 * 
 * GENERICS:
 * ListAdapter<Uri, PagesAdapter.PageViewHolder>
 * - Uri = type of items in the list
 * - PageViewHolder = type of ViewHolder
 * 
 * @param onDeleteClick Callback when delete button is clicked
 * @param onItemMoved Callback when items are reordered via drag
 * @param onDragStarted Callback to start drag (passes ViewHolder)
 */
class PagesAdapter(
    private val onDeleteClick: (Int) -> Unit,
    private val onItemMoved: (fromPosition: Int, toPosition: Int) -> Unit = { _, _ -> },
    private val onDragStarted: ((RecyclerView.ViewHolder) -> Unit)? = null
) : ListAdapter<Uri, PagesAdapter.PageViewHolder>(PageDiffCallback()) {

    // ============================================================
    // THUMBNAIL CACHE
    // ============================================================

    /**
     * LruCache for thumbnail bitmaps
     * 
     * LRU = Least Recently Used
     * When cache is full, oldest (least recently accessed) items are removed.
     * 
     * MEMORY SIZING:
     * We allocate 1/8 of available memory for thumbnail cache.
     * Each thumbnail is ~200KB (assuming 200x280 @ 4 bytes/pixel = 224KB)
     * So with 32MB cache, we can hold ~140 thumbnails.
     * 
     * KEY: URI string
     * VALUE: Bitmap
     */
    private val thumbnailCache: LruCache<String, Bitmap> = run {
        // Get max available memory (in KB)
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        // Use 1/8th of available memory for cache
        val cacheSize = maxMemory / 8
        
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                // Return size in KB
                return bitmap.byteCount / 1024
            }
        }
    }

    /**
     * Coroutine scope for async thumbnail loading
     * 
     * SupervisorJob ensures one failed load doesn't cancel others
     */
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ============================================================
    // ADAPTER METHODS
    // ============================================================

    /**
     * Called when RecyclerView needs a NEW ViewHolder
     * 
     * This only happens when there aren't enough recycled views.
     * Typically called ~10-15 times total (enough to fill screen + buffer)
     * 
     * @param parent The RecyclerView itself
     * @param viewType Type of view (for multiple view types, not used here)
     * @return New ViewHolder instance
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        /**
         * Inflate the item layout using View Binding
         * 
         * LayoutInflater converts XML to View objects
         * parent = the RecyclerView
         * attachToParent = false (RecyclerView handles attachment)
         */
        val binding = ItemPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false  // Don't attach yet - RecyclerView will do it
        )
        return PageViewHolder(binding)
    }

    /**
     * Called to display data at a specific position
     * 
     * This is called MANY times - every time a view is recycled
     * Keep this method FAST - no heavy computation or I/O
     * 
     * @param holder The ViewHolder to bind data to
     * @param position Index in the list
     */
    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        // getItem() is from ListAdapter - gets item at position
        holder.bind(getItem(position), position)
    }

    // ============================================================
    // VIEWHOLDER
    // ============================================================

    /**
     * ViewHolder - Holds references to views in each item
     * 
     * WHY VIEWHOLDER?
     * findViewById() is expensive (searches view tree).
     * ViewHolder does it once and caches the references.
     * 
     * 'inner class' means it can access outer class members (onDeleteClick)
     * 
     * @param binding View Binding for item_page.xml
     */
    inner class PageViewHolder(
        private val binding: ItemPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {  // root = the CardView

        /**
         * Bind data to this ViewHolder's views
         * 
         * Called every time this ViewHolder is used for a (potentially different) item
         * 
         * @param uri Image URI for this page
         * @param position Position in the list (for display and callbacks)
         */
        fun bind(uri: Uri, position: Int) {
            // ============================================
            // PAGE NUMBER
            // ============================================
            
            // Display 1-based page number (position is 0-based)
            binding.textPageNumber.text = (position + 1).toString()

            // ============================================
            // THUMBNAIL IMAGE (with caching)
            // ============================================
            
            /**
             * Load thumbnail image with LRU caching
             * 
             * PROCESS:
             * 1. Check cache for existing thumbnail
             * 2. If cached, use immediately
             * 3. If not cached, load asynchronously and cache result
             * 
             * This avoids re-decoding images on every bind (scroll)
             */
            val cacheKey = uri.toString()
            val cachedBitmap = thumbnailCache.get(cacheKey)
            
            if (cachedBitmap != null) {
                // Cache hit - use immediately
                binding.imageThumbnail.setImageBitmap(cachedBitmap)
            } else {
                // Cache miss - load asynchronously
                // Clear previous image while loading
                binding.imageThumbnail.setImageDrawable(null)
                
                // Store the URI we're loading for - to handle recycled views
                binding.imageThumbnail.tag = cacheKey
                
                adapterScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        loadThumbnail(uri, binding.root.context)
                    }
                    
                    // Only set if this view is still showing the same URI
                    // (handles recycled views during scroll)
                    if (binding.imageThumbnail.tag == cacheKey && bitmap != null) {
                        thumbnailCache.put(cacheKey, bitmap)
                        binding.imageThumbnail.setImageBitmap(bitmap)
                    }
                }
            }

            // ============================================
            // DELETE BUTTON
            // ============================================
            
            binding.btnDelete.setOnClickListener {
                /**
                 * Get position from bindingAdapterPosition
                 * 
                 * WHY NOT USE 'position' PARAMETER?
                 * The position parameter is only valid when bind() is called.
                 * If items are inserted/removed, the position might be stale.
                 * bindingAdapterPosition gives the CURRENT position.
                 * 
                 * NO_POSITION (-1) means the item is being removed - ignore clicks
                 */
                val currentPosition = bindingAdapterPosition
                if (currentPosition != RecyclerView.NO_POSITION) {
                    // Call the callback passed to adapter constructor
                    onDeleteClick(currentPosition)
                }
            }

            // ============================================
            // DRAG HANDLE
            // ============================================
            
            /**
             * Setup drag handle for reordering
             * 
             * When user touches the drag handle, we start the drag operation.
             * This provides better UX than long-press on entire item.
             */
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onDragStarted?.invoke(this)
                }
                false
            }
        }
        
        /**
         * Load a thumbnail bitmap from URI
         * 
         * Uses inSampleSize = 4 for efficient memory usage.
         * Thumbnails are 1/16 the pixels of the original.
         * 
         * @param uri Image file URI
         * @param context Context for ContentResolver
         * @return Decoded bitmap, or null on error
         */
        private fun loadThumbnail(uri: Uri, context: android.content.Context): Bitmap? {
            return try {
                // First pass: get dimensions
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

                // Calculate appropriate sample size for ~200px thumbnail
                val targetSize = 200
                val inSampleSize = calculateInSampleSize(width, height, targetSize, targetSize)

                // Second pass: load downsampled image
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
        
        /**
         * Calculate optimal inSampleSize for thumbnails
         */
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

    /**
     * DiffUtil.ItemCallback - Helps ListAdapter compute list differences
     * 
     * DIFFUTIL:
     * When you call submitList(newList), DiffUtil:
     * 1. Compares old and new lists
     * 2. Finds minimum number of changes
     * 3. Triggers appropriate animations
     * 
     * TWO METHODS:
     * - areItemsTheSame(): Are these the same item? (identity)
     * - areContentsTheSame(): Has the item's content changed? (equality)
     */
    class PageDiffCallback : DiffUtil.ItemCallback<Uri>() {
        
        /**
         * Are these the same item?
         * 
         * Checks IDENTITY - is this the same page (same file)?
         * Used to match items between old and new lists.
         * 
         * For our case, two URIs pointing to the same file are the same item.
         */
        override fun areItemsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            // URIs are the same if they point to the same file
            return oldItem == newItem
        }

        /**
         * Has the item's content changed?
         * 
         * Only called if areItemsTheSame() returns true.
         * Checks if the item needs to be re-bound.
         * 
         * For immutable items like file URIs, same URI = same content.
         */
        override fun areContentsTheSame(oldItem: Uri, newItem: Uri): Boolean {
            // For file URIs, if the path is the same, content is the same
            // (we're not tracking if the file contents changed)
            return oldItem == newItem
        }
    }

    // ============================================================
    // DRAG & DROP SUPPORT
    // ============================================================

    /**
     * Move an item from one position to another
     * 
     * Called by ItemTouchHelper during drag operation.
     * Updates internal list and notifies RecyclerView of the move.
     * 
     * @param fromPosition Original position
     * @param toPosition New position
     */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        val currentList = currentList.toMutableList()
        val item = currentList.removeAt(fromPosition)
        currentList.add(toPosition, item)
        submitList(currentList)
        
        // Notify the callback so ViewModel can be updated
        onItemMoved(fromPosition, toPosition)
    }

    /**
     * ItemTouchHelper.Callback for drag & drop reordering
     * 
     * ITEMTOUCHHELPER:
     * Android's built-in helper for swipe and drag gestures on RecyclerView.
     * 
     * @param adapter The PagesAdapter instance
     */
    class DragCallback(
        private val adapter: PagesAdapter
    ) : ItemTouchHelper.Callback() {

        /**
         * Define which movement directions are allowed
         * 
         * UP/DOWN/LEFT/RIGHT for grid layout reordering
         */
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or 
                           ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            return makeMovementFlags(dragFlags, 0)  // 0 = no swipe
        }

        /**
         * Called when item is moved during drag
         * 
         * @return true if items were swapped
         */
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            adapter.moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        /**
         * Called when item is swiped (not used here)
         */
        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // Not used - we only support drag, not swipe
        }

        /**
         * Enable long press to start drag (as fallback)
         */
        override fun isLongPressDragEnabled(): Boolean = true

        /**
         * Visual feedback when dragging
         */
        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.alpha = 0.7f
            }
        }

        /**
         * Reset visual state when drag ends
         */
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.alpha = 1.0f
        }
    }
}
