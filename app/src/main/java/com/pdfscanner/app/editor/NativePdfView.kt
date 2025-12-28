/**
 * NativePdfView.kt - Custom PDF viewer using Android's native PdfRenderer
 * 
 * This view renders PDF pages using the built-in Android PdfRenderer API
 * which is available on API 21+ and has no external dependencies.
 * 
 * Features:
 * - Native PDF rendering (no external libraries)
 * - Page navigation
 * - Zoom and pan support
 * - Memory-efficient page rendering
 */

package com.pdfscanner.app.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.io.File
import java.io.FileOutputStream

/**
 * Custom View that renders PDF pages using Android's native PdfRenderer
 */
class NativePdfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TAG = "NativePdfView"
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 5.0f
    }
    
    // PDF rendering
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentPage: PdfRenderer.Page? = null
    private var pageBitmap: Bitmap? = null
    
    // View state
    private var currentPageIndex = 0
    private var totalPages = 0
    
    // Transform
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f
    private val matrix = Matrix()
    
    // Rendering
    private val paint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }
    
    // Listeners
    var onPageChangedListener: ((page: Int, total: Int) -> Unit)? = null
    var onLoadCompleteListener: ((totalPages: Int) -> Unit)? = null
    var onErrorListener: ((error: Throwable) -> Unit)? = null
    
    // Gesture detectors
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())
    
    /**
     * Load PDF from a File
     */
    fun loadPdf(file: File) {
        try {
            Log.d(TAG, "Loading PDF from file: ${file.absolutePath}")
            
            close() // Close any existing PDF
            
            if (!file.exists()) {
                onErrorListener?.invoke(Exception("File does not exist: ${file.absolutePath}"))
                return
            }
            
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)
            totalPages = pdfRenderer!!.pageCount
            
            Log.d(TAG, "PDF loaded successfully, total pages: $totalPages")
            
            // Load first page
            showPage(0)
            
            onLoadCompleteListener?.invoke(totalPages)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading PDF", e)
            onErrorListener?.invoke(e)
        }
    }
    
    /**
     * Load PDF from a content URI by copying to temp file
     */
    fun loadPdf(context: Context, uri: Uri) {
        try {
            Log.d(TAG, "Loading PDF from URI: $uri")
            
            // Copy to temp file for reliable access
            val tempFile = File(context.cacheDir, "pdf_view_temp_${System.currentTimeMillis()}.pdf")
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            if (tempFile.exists() && tempFile.length() > 0) {
                loadPdf(tempFile)
            } else {
                onErrorListener?.invoke(Exception("Failed to copy PDF from URI"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading PDF from URI", e)
            onErrorListener?.invoke(e)
        }
    }
    
    /**
     * Show a specific page
     */
    fun showPage(pageIndex: Int) {
        if (pdfRenderer == null) {
            Log.w(TAG, "PDF not loaded")
            return
        }
        
        if (pageIndex < 0 || pageIndex >= totalPages) {
            Log.w(TAG, "Invalid page index: $pageIndex (total: $totalPages)")
            return
        }
        
        try {
            // Close current page if open
            currentPage?.close()
            
            // Open new page
            currentPage = pdfRenderer!!.openPage(pageIndex)
            currentPageIndex = pageIndex
            
            // Render the page
            renderCurrentPage()
            
            // Reset zoom/pan for new page
            resetTransform()
            
            onPageChangedListener?.invoke(currentPageIndex, totalPages)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing page $pageIndex", e)
            onErrorListener?.invoke(e)
        }
    }
    
    /**
     * Render the current page to bitmap
     */
    private fun renderCurrentPage() {
        val page = currentPage ?: return
        
        // Calculate bitmap size based on view size and page aspect ratio
        val viewWidth = if (width > 0) width else 1080
        val viewHeight = if (height > 0) height else 1920
        
        val pageWidth = page.width
        val pageHeight = page.height
        val pageAspect = pageWidth.toFloat() / pageHeight.toFloat()
        val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
        
        val bitmapWidth: Int
        val bitmapHeight: Int
        
        if (pageAspect > viewAspect) {
            // Page is wider than view
            bitmapWidth = viewWidth
            bitmapHeight = (viewWidth / pageAspect).toInt()
        } else {
            // Page is taller than view
            bitmapHeight = viewHeight
            bitmapWidth = (viewHeight * pageAspect).toInt()
        }
        
        // Recycle old bitmap
        pageBitmap?.recycle()
        
        // Create new bitmap
        pageBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        pageBitmap?.eraseColor(Color.WHITE)
        
        // Render page to bitmap
        page.render(pageBitmap!!, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        
        Log.d(TAG, "Rendered page ${currentPageIndex + 1}/$totalPages, bitmap: ${bitmapWidth}x${bitmapHeight}")
        
        invalidate()
    }
    
    /**
     * Reset zoom and pan
     */
    private fun resetTransform() {
        scaleFactor = 1.0f
        translateX = 0f
        translateY = 0f
        invalidate()
    }
    
    /**
     * Zoom in by 25%
     */
    fun zoomIn() {
        scaleFactor = (scaleFactor * 1.25f).coerceIn(MIN_SCALE, MAX_SCALE)
        invalidate()
    }
    
    /**
     * Zoom out by 25%
     */
    fun zoomOut() {
        scaleFactor = (scaleFactor / 1.25f).coerceIn(MIN_SCALE, MAX_SCALE)
        invalidate()
    }

    /**
     * Navigate to next page
     */
    fun nextPage(): Boolean {
        if (currentPageIndex < totalPages - 1) {
            showPage(currentPageIndex + 1)
            return true
        }
        return false
    }
    
    /**
     * Navigate to previous page
     */
    fun previousPage(): Boolean {
        if (currentPageIndex > 0) {
            showPage(currentPageIndex - 1)
            return true
        }
        return false
    }
    
    /**
     * Get current page index (0-based)
     */
    fun getCurrentPage(): Int = currentPageIndex
    
    /**
     * Get total page count
     */
    fun getPageCount(): Int = totalPages
    
    /**
     * Get current page bitmap (for annotation overlay alignment)
     */
    fun getPageBitmap(): Bitmap? = pageBitmap
    
    /**
     * Get the display rect of the PDF page in view coordinates
     */
    fun getPageDisplayRect(): android.graphics.RectF {
        val bitmap = pageBitmap ?: return android.graphics.RectF()
        
        val left = (width - bitmap.width * scaleFactor) / 2f + translateX
        val top = (height - bitmap.height * scaleFactor) / 2f + translateY
        val right = left + bitmap.width * scaleFactor
        val bottom = top + bitmap.height * scaleFactor
        
        return android.graphics.RectF(left, top, right, bottom)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw white background
        canvas.drawColor(Color.parseColor("#F5F5F5"))
        
        val bitmap = pageBitmap ?: return
        
        // Calculate position to center the bitmap
        val left = (width - bitmap.width * scaleFactor) / 2f + translateX
        val top = (height - bitmap.height * scaleFactor) / 2f + translateY
        
        // Apply transform
        matrix.reset()
        matrix.postScale(scaleFactor, scaleFactor)
        matrix.postTranslate(left, top)
        
        // Draw bitmap
        canvas.drawBitmap(bitmap, matrix, paint)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Re-render at new size
        if (currentPage != null) {
            renderCurrentPage()
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleGestureDetector.onTouchEvent(event)
        handled = gestureDetector.onTouchEvent(event) || handled
        return handled || super.onTouchEvent(event)
    }
    
    /**
     * Scale gesture listener for pinch zoom
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(MIN_SCALE, MAX_SCALE)
            invalidate()
            return true
        }
    }
    
    /**
     * Gesture listener for pan and double-tap zoom
     */
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            translateX -= distanceX
            translateY -= distanceY
            invalidate()
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Toggle between 1x and 2x zoom
            scaleFactor = if (scaleFactor > 1.5f) 1.0f else 2.0f
            translateX = 0f
            translateY = 0f
            invalidate()
            return true
        }
    }
    
    /**
     * Close and clean up resources
     */
    fun close() {
        try {
            currentPage?.close()
            currentPage = null
            
            pdfRenderer?.close()
            pdfRenderer = null
            
            fileDescriptor?.close()
            fileDescriptor = null
            
            pageBitmap?.recycle()
            pageBitmap = null
            
            totalPages = 0
            currentPageIndex = 0
            
        } catch (e: Exception) {
            Log.e(TAG, "Error closing PDF", e)
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        close()
    }
}
