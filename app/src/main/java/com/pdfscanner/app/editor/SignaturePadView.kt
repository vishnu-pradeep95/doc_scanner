/**
 * SignaturePadView.kt - Custom View for Drawing Signatures
 * 
 * A touch-enabled canvas for capturing handwritten signatures.
 * Features:
 * - Smooth bezier curve drawing
 * - Undo/redo support
 * - Save signature as bitmap or path points
 * - Customizable stroke width and color
 */

package com.pdfscanner.app.editor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Custom view for capturing signatures via touch input
 * 
 * Uses quadratic bezier curves for smooth lines between touch points.
 */
class SignaturePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // Paint for drawing the signature
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 6f
        color = Color.BLACK
    }
    
    // Current path being drawn
    private var currentPath = Path()
    
    // All completed paths (for undo support)
    private val paths = mutableListOf<PathData>()
    
    // Undo stack (paths removed via undo)
    private val undoStack = mutableListOf<PathData>()
    
    // Points for each stroke (for serialization)
    private val allStrokes = mutableListOf<MutableList<PointF>>()
    private var currentStroke = mutableListOf<PointF>()
    
    // Previous touch point (for bezier calculations)
    private var lastX = 0f
    private var lastY = 0f
    
    // Threshold for drawing (to avoid jitter)
    private val touchTolerance = 4f
    
    // Callback when signature changes
    var onSignatureChanged: ((Boolean) -> Unit)? = null
    
    // Stroke color and width
    var strokeColor: Int
        get() = paint.color
        set(value) {
            paint.color = value
            invalidate()
        }
    
    var strokeWidth: Float
        get() = paint.strokeWidth
        set(value) {
            paint.strokeWidth = value
            invalidate()
        }
    
    /**
     * Data class to store path with its paint properties
     */
    private data class PathData(
        val path: Path,
        val color: Int,
        val strokeWidth: Float,
        val points: List<PointF>
    )
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw all completed paths
        for (pathData in paths) {
            paint.color = pathData.color
            paint.strokeWidth = pathData.strokeWidth
            canvas.drawPath(pathData.path, paint)
        }
        
        // Draw current path being drawn
        paint.color = strokeColor
        paint.strokeWidth = strokeWidth
        canvas.drawPath(currentPath, paint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStart(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                touchMove(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                touchUp()
                invalidate()
                return true
            }
        }
        return false
    }
    
    private fun touchStart(x: Float, y: Float) {
        // Clear redo stack when new drawing starts
        undoStack.clear()
        
        // Start new path
        currentPath.reset()
        currentPath.moveTo(x, y)
        
        // Start new stroke
        currentStroke = mutableListOf()
        currentStroke.add(PointF(x, y))
        
        lastX = x
        lastY = y
    }
    
    private fun touchMove(x: Float, y: Float) {
        val dx = abs(x - lastX)
        val dy = abs(y - lastY)
        
        // Only draw if moved beyond tolerance
        if (dx >= touchTolerance || dy >= touchTolerance) {
            // Use quadratic bezier for smooth curves
            currentPath.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
            currentStroke.add(PointF(x, y))
            lastX = x
            lastY = y
        }
    }
    
    private fun touchUp() {
        // Finish the current path
        currentPath.lineTo(lastX, lastY)
        
        // Save completed path
        if (!currentPath.isEmpty) {
            paths.add(PathData(
                path = Path(currentPath),
                color = paint.color,
                strokeWidth = paint.strokeWidth,
                points = currentStroke.toList()
            ))
            allStrokes.add(currentStroke)
        }
        
        // Reset for next stroke
        currentPath.reset()
        currentStroke = mutableListOf()
        
        // Notify listener
        onSignatureChanged?.invoke(hasSignature())
    }
    
    /**
     * Clear the signature pad
     */
    fun clear() {
        paths.clear()
        allStrokes.clear()
        undoStack.clear()
        currentPath.reset()
        invalidate()
        onSignatureChanged?.invoke(false)
    }
    
    /**
     * Undo the last stroke
     */
    fun undo(): Boolean {
        if (paths.isNotEmpty()) {
            undoStack.add(paths.removeLast())
            if (allStrokes.isNotEmpty()) {
                allStrokes.removeLast()
            }
            invalidate()
            onSignatureChanged?.invoke(hasSignature())
            return true
        }
        return false
    }
    
    /**
     * Redo the last undone stroke
     */
    fun redo(): Boolean {
        if (undoStack.isNotEmpty()) {
            val pathData = undoStack.removeLast()
            paths.add(pathData)
            allStrokes.add(pathData.points.toMutableList())
            invalidate()
            onSignatureChanged?.invoke(hasSignature())
            return true
        }
        return false
    }
    
    /**
     * Check if there's any signature drawn
     */
    fun hasSignature(): Boolean = paths.isNotEmpty()
    
    /**
     * Check if undo is available
     */
    fun canUndo(): Boolean = paths.isNotEmpty()
    
    /**
     * Check if redo is available
     */
    fun canRedo(): Boolean = undoStack.isNotEmpty()
    
    /**
     * Get signature as bitmap
     */
    fun getSignatureBitmap(): Bitmap? {
        if (!hasSignature()) return null
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)
        
        for (pathData in paths) {
            paint.color = pathData.color
            paint.strokeWidth = pathData.strokeWidth
            canvas.drawPath(pathData.path, paint)
        }
        
        return bitmap
    }
    
    /**
     * Get signature as cropped bitmap (removes whitespace)
     */
    fun getCroppedSignatureBitmap(): Bitmap? {
        val fullBitmap = getSignatureBitmap() ?: return null
        
        // Find bounds of actual signature
        var minX = fullBitmap.width
        var minY = fullBitmap.height
        var maxX = 0
        var maxY = 0
        
        for (y in 0 until fullBitmap.height) {
            for (x in 0 until fullBitmap.width) {
                if (fullBitmap.getPixel(x, y) != Color.TRANSPARENT) {
                    minX = minOf(minX, x)
                    minY = minOf(minY, y)
                    maxX = maxOf(maxX, x)
                    maxY = maxOf(maxY, y)
                }
            }
        }
        
        // Add padding
        val padding = 20
        minX = maxOf(0, minX - padding)
        minY = maxOf(0, minY - padding)
        maxX = minOf(fullBitmap.width - 1, maxX + padding)
        maxY = minOf(fullBitmap.height - 1, maxY + padding)
        
        val width = maxX - minX
        val height = maxY - minY
        
        return if (width > 0 && height > 0) {
            Bitmap.createBitmap(fullBitmap, minX, minY, width, height)
        } else {
            null
        }
    }
    
    /**
     * Get all strokes as list of point lists (for serialization)
     */
    fun getStrokes(): List<List<PointF>> = allStrokes.map { it.toList() }
    
    /**
     * Load strokes from saved data
     */
    fun loadStrokes(strokes: List<List<PointF>>) {
        clear()
        
        for (stroke in strokes) {
            if (stroke.isEmpty()) continue
            
            val path = Path()
            val firstPoint = stroke.first()
            path.moveTo(firstPoint.x, firstPoint.y)
            
            for (i in 1 until stroke.size) {
                val prev = stroke[i - 1]
                val curr = stroke[i]
                path.quadTo(prev.x, prev.y, (curr.x + prev.x) / 2, (curr.y + prev.y) / 2)
            }
            
            paths.add(PathData(path, paint.color, paint.strokeWidth, stroke))
            allStrokes.add(stroke.toMutableList())
        }
        
        invalidate()
        onSignatureChanged?.invoke(hasSignature())
    }
}
