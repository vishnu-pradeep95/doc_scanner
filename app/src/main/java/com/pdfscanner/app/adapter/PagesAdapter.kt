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
 * ANALOGY:
 * Think of it like a printing press:
 * - The press (RecyclerView) holds paper
 * - The template (ViewHolder) is the layout
 * - The data (your list) is the content
 * - The operator (Adapter) feeds data into templates
 */

package com.pdfscanner.app.adapter

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil  // Computes list differences
import androidx.recyclerview.widget.ListAdapter  // Adapter with DiffUtil built-in
import androidx.recyclerview.widget.RecyclerView

// View Binding for the item layout
import com.pdfscanner.app.databinding.ItemPageBinding

/**
 * PagesAdapter - Displays page thumbnails in a grid
 * 
 * GENERICS:
 * ListAdapter<Uri, PagesAdapter.PageViewHolder>
 * - Uri = type of items in the list
 * - PageViewHolder = type of ViewHolder
 * 
 * @param onDeleteClick Callback when delete button is clicked
 *                     Lambda receives the position of the item
 */
class PagesAdapter(
    private val onDeleteClick: (Int) -> Unit
) : ListAdapter<Uri, PagesAdapter.PageViewHolder>(PageDiffCallback()) {

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
            // THUMBNAIL IMAGE
            // ============================================
            
            /**
             * Load thumbnail image
             * 
             * We use inSampleSize = 4 to load 1/16 of the pixels
             * This is much faster and uses less memory for thumbnails
             */
            try {
                val context = binding.root.context
                
                // First pass: get dimensions (not loading pixels)
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(inputStream, null, options)
                }

                // Second pass: load downsampled image
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 4  // Load 1/4 width, 1/4 height = 1/16 pixels
                    }
                    val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                    binding.imageThumbnail.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                // If loading fails, clear the image
                // This handles deleted files, permission issues, etc.
                binding.imageThumbnail.setImageDrawable(null)
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
}
