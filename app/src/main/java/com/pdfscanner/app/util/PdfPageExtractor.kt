/**
 * PdfPageExtractor.kt - Extract pages from PDF as images
 * 
 * PURPOSE:
 * When importing PDFs, we need to extract each page as an image
 * so users can edit, crop, and re-combine them with other scans.
 * 
 * USES:
 * - PdfRenderer (Android native API, API 21+)
 * - Renders PDF pages to Bitmap
 * - Saves each page as JPEG for editing
 */

package com.pdfscanner.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Utility class for extracting pages from PDF files as images
 */
object PdfPageExtractor {
    
    private const val TAG = "PdfPageExtractor"
    
    /**
     * Result of PDF extraction
     */
    data class ExtractionResult(
        val pageUris: List<Uri>,
        val pageCount: Int,
        val success: Boolean,
        val errorMessage: String? = null
    )
    
    /**
     * Extract all pages from a PDF as JPEG images
     * 
     * @param context Application context
     * @param pdfUri URI of the PDF file to extract
     * @param targetDpi DPI for rendering (higher = better quality, larger files)
     * @return ExtractionResult with list of page URIs
     */
    suspend fun extractPages(
        context: Context,
        pdfUri: Uri,
        targetDpi: Int = 200
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val pageUris = mutableListOf<Uri>()
        
        try {
            // Open the PDF file
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext ExtractionResult(
                    pageUris = emptyList(),
                    pageCount = 0,
                    success = false,
                    errorMessage = "Could not open PDF file"
                )
            
            // Create PDF renderer
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)
            val pageCount = pdfRenderer.pageCount
            
            Log.d(TAG, "PDF has $pageCount pages")
            
            // Create output directory
            val outputDir = File(context.filesDir, "scans").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(System.currentTimeMillis())
            
            // Extract each page
            for (pageIndex in 0 until pageCount) {
                try {
                    val pageUri = extractPage(
                        pdfRenderer = pdfRenderer,
                        pageIndex = pageIndex,
                        outputDir = outputDir,
                        timestamp = timestamp,
                        targetDpi = targetDpi
                    )
                    pageUri?.let { pageUris.add(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting page $pageIndex", e)
                    // Continue with other pages
                }
            }
            
            // Cleanup
            pdfRenderer.close()
            parcelFileDescriptor.close()
            
            ExtractionResult(
                pageUris = pageUris,
                pageCount = pageCount,
                success = pageUris.isNotEmpty()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDF", e)
            ExtractionResult(
                pageUris = emptyList(),
                pageCount = 0,
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Extract a single page from the PDF
     */
    private fun extractPage(
        pdfRenderer: PdfRenderer,
        pageIndex: Int,
        outputDir: File,
        timestamp: String,
        targetDpi: Int
    ): Uri? {
        // Open the page
        val page = pdfRenderer.openPage(pageIndex)
        
        try {
            // Calculate dimensions based on target DPI
            // PDF points are 1/72 inch, so we scale accordingly
            val scale = targetDpi / 72f
            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()
            
            Log.d(TAG, "Page $pageIndex: ${page.width}x${page.height} -> ${width}x${height}")
            
            // Create bitmap with white background
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            
            // Render the page
            page.render(
                bitmap,
                null,  // No clip rect - render full page
                null,  // No transform matrix - use default
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )
            
            // Save as JPEG
            val outputFile = File(outputDir, "PDF_${timestamp}_page${pageIndex + 1}.jpg")
            FileOutputStream(outputFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            
            // Recycle bitmap to free memory
            bitmap.recycle()
            
            Log.d(TAG, "Saved page ${pageIndex + 1} to ${outputFile.absolutePath}")
            
            return Uri.fromFile(outputFile)
            
        } finally {
            // Always close the page
            page.close()
        }
    }
    
    /**
     * Check if a URI points to a PDF file
     */
    fun isPdfFile(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType == "application/pdf"
    }
    
    /**
     * Get the page count of a PDF without extracting
     */
    suspend fun getPageCount(context: Context, pdfUri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext 0
            
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)
            val count = pdfRenderer.pageCount
            
            pdfRenderer.close()
            parcelFileDescriptor.close()
            
            count
        } catch (e: Exception) {
            Log.e(TAG, "Error getting page count", e)
            0
        }
    }
}
