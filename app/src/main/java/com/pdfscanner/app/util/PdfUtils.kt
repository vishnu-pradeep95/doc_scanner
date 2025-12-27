/**
 * PdfUtils.kt - PDF Manipulation Utilities
 * 
 * PURPOSE:
 * Provides utilities for merging, splitting, and compressing PDF files.
 * Uses Android's native PdfRenderer and PdfDocument APIs.
 * 
 * FEATURES:
 * - Merge multiple PDFs into one
 * - Split PDF into individual page PDFs
 * - Compress PDF by reducing image quality
 * - Extract specific page ranges
 * 
 * NOTE: Android's native PDF APIs have limitations:
 * - PdfRenderer can only READ PDFs (not modify)
 * - PdfDocument can only CREATE new PDFs
 * - So we render pages as images and recreate PDFs
 */

package com.pdfscanner.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result from PDF operations
 */
data class PdfOperationResult(
    val success: Boolean,
    val outputUri: Uri? = null,
    val outputUris: List<Uri>? = null,
    val message: String,
    val originalSize: Long = 0,
    val newSize: Long = 0
)

object PdfUtils {
    
    private const val TAG = "PdfUtils"
    
    // Default DPI for rendering (higher = better quality, larger file)
    private const val DEFAULT_DPI = 150
    private const val HIGH_QUALITY_DPI = 200
    private const val COMPRESSED_DPI = 72  // Much lower for aggressive compression
    
    // Compression scale factors (multiply page dimensions)
    private const val HIGH_QUALITY_SCALE = 1.5f
    private const val MEDIUM_SCALE = 1.0f
    private const val LOW_SCALE = 0.6f
    
    // JPEG quality for compression (0-100)
    private const val HIGH_QUALITY = 90
    private const val MEDIUM_QUALITY = 70
    private const val LOW_QUALITY = 50
    
    /**
     * Merge multiple PDF files into a single PDF
     * 
     * @param context Application context
     * @param pdfUris List of PDF URIs to merge (in order)
     * @param outputName Name for the merged PDF (without extension)
     * @return Result containing the merged PDF URI
     */
    suspend fun mergePdfs(
        context: Context,
        pdfUris: List<Uri>,
        outputName: String = "Merged"
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        
        if (pdfUris.isEmpty()) {
            return@withContext PdfOperationResult(
                success = false,
                message = "No PDFs to merge"
            )
        }
        
        if (pdfUris.size == 1) {
            return@withContext PdfOperationResult(
                success = false,
                message = "Need at least 2 PDFs to merge"
            )
        }
        
        try {
            val pdfDocument = PdfDocument()
            var pageNumber = 0
            var totalOriginalSize = 0L
            
            // Process each PDF
            for (pdfUri in pdfUris) {
                val fileSize = getFileSize(context, pdfUri)
                totalOriginalSize += fileSize
                
                val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                    ?: continue
                
                val renderer = PdfRenderer(pfd)
                
                // Render each page and add to new document
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    
                    // Calculate dimensions at default DPI
                    val width = (page.width * DEFAULT_DPI / 72f).toInt()
                    val height = (page.height * DEFAULT_DPI / 72f).toInt()
                    
                    // Render page to bitmap
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    
                    // Add to new PDF
                    pageNumber++
                    val pageInfo = PdfDocument.PageInfo.Builder(width, height, pageNumber).create()
                    val newPage = pdfDocument.startPage(pageInfo)
                    val canvas = newPage.canvas
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(newPage)
                    
                    bitmap.recycle()
                }
                
                renderer.close()
                pfd.close()
            }
            
            // Save merged PDF
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputFile = File(getPdfsDir(context), "${outputName}_$timestamp.pdf")
            
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            
            Log.d(TAG, "Merged ${pdfUris.size} PDFs into ${outputFile.name}")
            
            PdfOperationResult(
                success = true,
                outputUri = Uri.fromFile(outputFile),
                message = "Merged ${pdfUris.size} PDFs ($pageNumber pages)",
                originalSize = totalOriginalSize,
                newSize = outputFile.length()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge PDFs", e)
            PdfOperationResult(
                success = false,
                message = "Merge failed: ${e.message}"
            )
        }
    }
    
    /**
     * Split a PDF into individual page PDFs
     * 
     * @param context Application context
     * @param pdfUri URI of the PDF to split
     * @param baseName Base name for output files
     * @return Result containing list of split PDF URIs
     */
    suspend fun splitPdf(
        context: Context,
        pdfUri: Uri,
        baseName: String = "Page"
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        
        try {
            val originalSize = getFileSize(context, pdfUri)
            val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext PdfOperationResult(
                    success = false,
                    message = "Could not open PDF"
                )
            
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            
            if (pageCount <= 1) {
                renderer.close()
                pfd.close()
                return@withContext PdfOperationResult(
                    success = false,
                    message = "PDF has only one page, nothing to split"
                )
            }
            
            val outputUris = mutableListOf<Uri>()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            var totalNewSize = 0L
            
            // Create a separate PDF for each page
            for (i in 0 until pageCount) {
                val page = renderer.openPage(i)
                
                // Calculate dimensions
                val width = (page.width * DEFAULT_DPI / 72f).toInt()
                val height = (page.height * DEFAULT_DPI / 72f).toInt()
                
                // Render page to bitmap
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                // Create single-page PDF
                val singlePagePdf = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(width, height, 1).create()
                val newPage = singlePagePdf.startPage(pageInfo)
                newPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                singlePagePdf.finishPage(newPage)
                
                // Save
                val outputFile = File(getPdfsDir(context), "${baseName}_${i + 1}_$timestamp.pdf")
                FileOutputStream(outputFile).use { fos ->
                    singlePagePdf.writeTo(fos)
                }
                singlePagePdf.close()
                bitmap.recycle()
                
                outputUris.add(Uri.fromFile(outputFile))
                totalNewSize += outputFile.length()
            }
            
            renderer.close()
            pfd.close()
            
            Log.d(TAG, "Split PDF into ${outputUris.size} files")
            
            PdfOperationResult(
                success = true,
                outputUris = outputUris,
                message = "Split into $pageCount separate PDFs",
                originalSize = originalSize,
                newSize = totalNewSize
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to split PDF", e)
            PdfOperationResult(
                success = false,
                message = "Split failed: ${e.message}"
            )
        }
    }
    
    /**
     * Extract specific pages from a PDF
     * 
     * @param context Application context
     * @param pdfUri URI of the source PDF
     * @param pageNumbers List of page numbers to extract (1-indexed)
     * @param outputName Name for output file
     * @return Result containing the extracted PDF URI
     */
    suspend fun extractPages(
        context: Context,
        pdfUri: Uri,
        pageNumbers: List<Int>,
        outputName: String = "Extracted"
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        
        if (pageNumbers.isEmpty()) {
            return@withContext PdfOperationResult(
                success = false,
                message = "No pages specified"
            )
        }
        
        try {
            val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext PdfOperationResult(
                    success = false,
                    message = "Could not open PDF"
                )
            
            val renderer = PdfRenderer(pfd)
            val pdfDocument = PdfDocument()
            var newPageNumber = 0
            
            // Extract specified pages (convert to 0-indexed)
            for (pageNum in pageNumbers) {
                val pageIndex = pageNum - 1
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) continue
                
                val page = renderer.openPage(pageIndex)
                
                val width = (page.width * DEFAULT_DPI / 72f).toInt()
                val height = (page.height * DEFAULT_DPI / 72f).toInt()
                
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                newPageNumber++
                val pageInfo = PdfDocument.PageInfo.Builder(width, height, newPageNumber).create()
                val newPage = pdfDocument.startPage(pageInfo)
                newPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(newPage)
                
                bitmap.recycle()
            }
            
            // Save extracted PDF
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputFile = File(getPdfsDir(context), "${outputName}_$timestamp.pdf")
            
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            renderer.close()
            pfd.close()
            
            PdfOperationResult(
                success = true,
                outputUri = Uri.fromFile(outputFile),
                message = "Extracted $newPageNumber pages",
                newSize = outputFile.length()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract pages", e)
            PdfOperationResult(
                success = false,
                message = "Extract failed: ${e.message}"
            )
        }
    }
    
    /**
     * Compression quality levels
     */
    enum class CompressionLevel {
        LOW,      // Highest compression, lowest quality
        MEDIUM,   // Balanced
        HIGH      // Lowest compression, highest quality
    }
    
    /**
     * Compress a PDF by reducing image quality using JPEG compression
     * 
     * @param context Application context
     * @param pdfUri URI of the PDF to compress
     * @param level Compression level
     * @param outputName Name for output file
     * @return Result containing the compressed PDF URI
     */
    suspend fun compressPdf(
        context: Context,
        pdfUri: Uri,
        level: CompressionLevel = CompressionLevel.MEDIUM,
        outputName: String? = null
    ): PdfOperationResult = withContext(Dispatchers.IO) {
        
        try {
            val originalSize = getFileSize(context, pdfUri)
            
            val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: return@withContext PdfOperationResult(
                    success = false,
                    message = "Could not open PDF"
                )
            
            val renderer = PdfRenderer(pfd)
            val pdfDocument = PdfDocument()
            
            // Select parameters based on compression level
            val (scale, jpegQuality) = when (level) {
                CompressionLevel.LOW -> Pair(0.5f, 40)    // Aggressive: 50% size, 40% quality
                CompressionLevel.MEDIUM -> Pair(0.7f, 60) // Balanced: 70% size, 60% quality
                CompressionLevel.HIGH -> Pair(0.9f, 80)   // Light: 90% size, 80% quality
            }
            
            // Create temp directory for compressed images
            val tempDir = File(context.cacheDir, "pdf_compress_temp")
            if (!tempDir.exists()) tempDir.mkdirs()
            
            // Process each page: render -> compress as JPEG -> add to PDF
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                
                // Scale dimensions based on compression level
                val width = (page.width * scale).toInt().coerceAtLeast(100)
                val height = (page.height * scale).toInt().coerceAtLeast(100)
                
                // Render page to bitmap
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                // Compress to JPEG and reload (this actually reduces quality/size)
                val tempJpeg = File(tempDir, "page_$i.jpg")
                FileOutputStream(tempJpeg).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, fos)
                }
                bitmap.recycle()
                
                // Load the compressed JPEG back
                val compressedBitmap = android.graphics.BitmapFactory.decodeFile(tempJpeg.absolutePath)
                
                // Add compressed bitmap to PDF
                val pageInfo = PdfDocument.PageInfo.Builder(
                    compressedBitmap.width, 
                    compressedBitmap.height, 
                    i + 1
                ).create()
                val newPage = pdfDocument.startPage(pageInfo)
                newPage.canvas.drawBitmap(compressedBitmap, 0f, 0f, null)
                pdfDocument.finishPage(newPage)
                
                compressedBitmap.recycle()
                tempJpeg.delete()
            }
            
            // Cleanup temp directory
            tempDir.delete()
            
            // Save compressed PDF
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val baseName = outputName ?: "Compressed"
            val outputFile = File(getPdfsDir(context), "${baseName}_$timestamp.pdf")
            
            FileOutputStream(outputFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            renderer.close()
            pfd.close()
            
            val newSize = outputFile.length()
            val savingsPercent = if (originalSize > 0) {
                ((originalSize - newSize) * 100 / originalSize).toInt()
            } else 0
            
            Log.d(TAG, "Compressed PDF: $originalSize -> $newSize bytes ($savingsPercent% reduction)")
            
            // Check if we actually saved space
            if (newSize >= originalSize) {
                // Delete the larger file and report failure
                outputFile.delete()
                return@withContext PdfOperationResult(
                    success = false,
                    message = "PDF is already optimized (no reduction possible)",
                    originalSize = originalSize,
                    newSize = newSize
                )
            }
            
            PdfOperationResult(
                success = true,
                outputUri = Uri.fromFile(outputFile),
                message = "Reduced by $savingsPercent% (${formatFileSize(originalSize)} → ${formatFileSize(newSize)})",
                originalSize = originalSize,
                newSize = newSize
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress PDF", e)
            PdfOperationResult(
                success = false,
                message = "Compression failed: ${e.message}"
            )
        }
    }
    
    /**
     * Get page count of a PDF
     */
    suspend fun getPageCount(context: Context, pdfUri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return@withContext 0
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            count
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get page count", e)
            0
        }
    }
    
    /**
     * Get file size from URI
     */
    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Get PDFs directory
     */
    private fun getPdfsDir(context: Context): File {
        val dir = File(context.filesDir, "pdfs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    
    /**
     * Format file size for display
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
