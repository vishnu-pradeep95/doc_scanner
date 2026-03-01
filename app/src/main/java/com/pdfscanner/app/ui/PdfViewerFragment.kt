/**
 * PdfViewerFragment.kt - Built-in PDF Viewer
 * 
 * Displays PDFs within the app when no external viewer is available.
 * Uses PdfRenderer to render PDF pages as bitmaps.
 */

package com.pdfscanner.app.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pdfscanner.app.ui.showSnackbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.pdfscanner.app.R
import com.pdfscanner.app.databinding.FragmentPdfViewerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fragment for viewing PDF documents within the app
 */
class PdfViewerFragment : Fragment() {
    
    private var _binding: FragmentPdfViewerBinding? = null
    private val binding get() = _binding!!
    
    private val args: PdfViewerFragmentArgs by navArgs()
    
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentPage = 0
    private var pageCount = 0

    // Single-threaded dispatcher — serializes all pdfRenderer.openPage() calls
    // PdfRenderer is NOT thread-safe; only one page may be open at a time
    private val pdfIoDispatcher = Dispatchers.IO.limitedParallelism(1)

    // Bitmap cache: up to 3 slots (prev/current/next)
    // Key = page index, Value = rendered Bitmap
    private val pageCache = SparseArray<Bitmap>(3)
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfViewerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupNavigation()
        loadPdf()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.toolbar.title = args.pdfName
        
        // Setup toolbar menu (home button)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_home -> {
                    findNavController().navigate(R.id.action_pdfViewer_to_home)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupNavigation() {
        binding.btnPrevious.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderPage(currentPage)
            }
        }
        
        binding.btnNext.setOnClickListener {
            if (currentPage < pageCount - 1) {
                currentPage++
                renderPage(currentPage)
            }
        }
    }
    
    private fun loadPdf() {
        binding.progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val file = File(args.pdfPath)
                if (!file.exists()) {
                    showError(getString(R.string.file_not_found))
                    return@launch
                }
                
                withContext(Dispatchers.IO) {
                    fileDescriptor = ParcelFileDescriptor.open(
                        file,
                        ParcelFileDescriptor.MODE_READ_ONLY
                    )
                    pdfRenderer = PdfRenderer(fileDescriptor!!)
                    pageCount = pdfRenderer?.pageCount ?: 0
                }
                
                // Guard against fragment detachment during IO
                if (_binding == null) return@launch

                if (pageCount > 0) {
                    renderPage(0)
                    updateNavigationState()
                } else {
                    showError(getString(R.string.pdf_empty))
                }

            } catch (e: Exception) {
                if (_binding == null) return@launch
                showError(getString(R.string.error_opening_pdf))
            }

            _binding?.progressBar?.visibility = View.GONE
        }
    }
    
    // Render a single page to a Bitmap. Uses pdfIoDispatcher to serialize openPage() calls.
    // Returns null if renderer is null or an exception occurs.
    private suspend fun renderPageToBitmap(pageIndex: Int): Bitmap? =
        withContext(pdfIoDispatcher) {
            try {
                val page = pdfRenderer?.openPage(pageIndex) ?: return@withContext null
                val scale = 2f
                val bmp = Bitmap.createBitmap(
                    (page.width * scale).toInt(),
                    (page.height * scale).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bmp
            } catch (e: Exception) {
                null
            }
        }

    // Pre-renders prev and next pages into the cache. Evicts pages outside the 3-slot window.
    // Must be called after renderPage() updates currentPage.
    private fun prefetchAdjacentPages(currentIndex: Int) {
        val pagesToPrefetch = listOf(currentIndex - 1, currentIndex + 1)
            .filter { it in 0 until pageCount }
            .filter { pageCache[it] == null }   // Skip already-cached pages

        pagesToPrefetch.forEach { index ->
            lifecycleScope.launch {
                val bmp = renderPageToBitmap(index)
                if (bmp != null) pageCache.put(index, bmp)
            }
        }

        // Evict pages outside the [currentIndex-1, currentIndex+1] window
        val keysToEvict = mutableListOf<Int>()
        for (i in 0 until pageCache.size()) {
            val key = pageCache.keyAt(i)
            if (key < currentIndex - 1 || key > currentIndex + 1) keysToEvict.add(key)
        }
        keysToEvict.forEach { key ->
            pageCache[key]?.recycle()
            pageCache.remove(key)
        }
    }

    private fun renderPage(pageIndex: Int) {
        // Cache hit: serve immediately, update nav state, prefetch neighbors
        val cached = pageCache[pageIndex]
        if (cached != null) {
            binding.pdfImage.setImageBitmap(cached)
            updateNavigationState()
            prefetchAdjacentPages(pageIndex)
            return
        }

        // Cache miss: render on background thread, store in cache, update UI
        lifecycleScope.launch {
            val bitmap = renderPageToBitmap(pageIndex)
            if (bitmap != null) {
                pageCache.put(pageIndex, bitmap)
                _binding?.pdfImage?.setImageBitmap(bitmap)
            }
            if (_binding != null) updateNavigationState()
            prefetchAdjacentPages(pageIndex)
        }
    }
    
    private fun updateNavigationState() {
        binding.btnPrevious.isEnabled = currentPage > 0
        binding.btnPrevious.alpha = if (currentPage > 0) 1f else 0.4f
        
        binding.btnNext.isEnabled = currentPage < pageCount - 1
        binding.btnNext.alpha = if (currentPage < pageCount - 1) 1f else 0.4f
        
        binding.textPageNumber.text = getString(R.string.page_of, currentPage + 1, pageCount)
    }
    
    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
    }
    
    override fun onDestroyView() {
        super.onDestroyView()

        // Detach current bitmap from ImageView before recycling to prevent
        // "Canvas: trying to use a recycled bitmap" if ImageView is still drawing
        _binding?.pdfImage?.setImageDrawable(null)

        // Recycle all cached bitmaps to release heap immediately
        for (i in 0 until pageCache.size()) {
            pageCache.valueAt(i)?.recycle()
        }
        pageCache.clear()

        try { pdfRenderer?.close() } catch (e: Exception) { android.util.Log.e("PdfViewerFragment", "Error closing renderer", e) }
        pdfRenderer = null
        try { fileDescriptor?.close() } catch (e: Exception) { android.util.Log.e("PdfViewerFragment", "Error closing fd", e) }
        fileDescriptor = null
        _binding = null
    }
}
