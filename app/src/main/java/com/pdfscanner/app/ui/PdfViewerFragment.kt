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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
                
                if (pageCount > 0) {
                    renderPage(0)
                    updateNavigationState()
                } else {
                    showError(getString(R.string.pdf_empty))
                }
                
            } catch (e: Exception) {
                showError(getString(R.string.error_opening_pdf))
            }
            
            binding.progressBar.visibility = View.GONE
        }
    }
    
    private fun renderPage(pageIndex: Int) {
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val page = pdfRenderer?.openPage(pageIndex)
                    page?.let {
                        // Calculate dimensions for high quality rendering
                        val scale = 2f // 2x for sharper text
                        val width = (it.width * scale).toInt()
                        val height = (it.height * scale).toInt()
                        
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // White background
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        it.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        it.close()
                        bmp
                    }
                }
                
                bitmap?.let {
                    binding.pdfImage.setImageBitmap(it)
                }
                
                updateNavigationState()
                
            } catch (e: Exception) {
                Toast.makeText(requireContext(), R.string.error_rendering_page, Toast.LENGTH_SHORT).show()
            }
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
        pdfRenderer?.close()
        fileDescriptor?.close()
        _binding = null
    }
}
