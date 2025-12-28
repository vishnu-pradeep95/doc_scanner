/**
 * AnnotationCanvasView.kt - Overlay Canvas for PDF Annotations
 * 
 * Displays and allows manipulation of annotations on top of a PDF page.
 * Handles selection, dragging, resizing of annotation objects.
 */

package com.pdfscanner.app.editor

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Overlay canvas for rendering and editing PDF annotations
 */
class AnnotationCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // Current page annotations
    private var annotations = mutableListOf<AnnotationItem>()
    
    // Currently selected annotation
    private var selectedAnnotation: AnnotationItem? = null
    
    // Current tool
    var currentTool: EditorTool = EditorTool.SELECT
        set(value) {
            field = value
            if (value != EditorTool.SELECT) {
                selectedAnnotation = null
                onAnnotationSelected?.invoke(null)
            }
            invalidate()
        }
    
    // Drawing properties
    var drawColor: Int = Color.BLACK
    var drawStrokeWidth: Float = 4f
    var textSize: Float = 48f
    var highlightAlpha: Int = 77 // ~30% opacity for highlights
    
    // Free-hand drawing state
    private var isDrawing = false
    private var currentDrawPath = Path()
    private var currentDrawPoints = mutableListOf<PointF>()
    
    // Shape drawing state
    private var shapeStartPoint: PointF? = null
    private var shapeEndPoint: PointF? = null
    
    // Dragging state
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var annotationStartX = 0f
    private var annotationStartY = 0f
    
    // Resizing state
    private var isResizing = false
    private var resizeCorner: Int = -1 // 0=TL, 1=TR, 2=BL, 3=BR
    private var annotationStartWidth = 0f
    private var annotationStartHeight = 0f
    
    // Listeners
    var onAnnotationSelected: ((AnnotationItem?) -> Unit)? = null
    var onAnnotationsChanged: (() -> Unit)? = null
    var onTextAnnotationRequested: ((Float, Float) -> Unit)? = null
    var onStampRequested: ((Float, Float) -> Unit)? = null
    var onSignatureRequested: ((Float, Float) -> Unit)? = null
    
    // Paints
    private val shapePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    
    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val highlightPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 48f
    }
    
    private val selectionPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#4ECDC4")
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }
    
    private val handlePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#4ECDC4")
    }
    
    /**
     * Wrapper class for annotation items
     */
    data class AnnotationItem(
        val id: String,
        val type: AnnotationType,
        var x: Float,       // Position as percentage (0.0-1.0)
        var y: Float,
        var width: Float,   // Size as percentage
        var height: Float,
        var color: Int = Color.BLACK,
        var text: String = "",
        var bitmap: Bitmap? = null,
        var points: List<PointF> = emptyList(),
        var shapeType: ShapeType = ShapeType.RECTANGLE,
        var stampType: StampType? = null,
        var strokeWidth: Float = 4f,
        var textSize: Float = 48f
    )
    
    enum class AnnotationType {
        SIGNATURE, TEXT, SHAPE, STAMP, DRAWING, HIGHLIGHT
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        // Draw all annotations
        for (annotation in annotations) {
            drawAnnotation(canvas, annotation, viewWidth, viewHeight)
        }
        
        // Draw selection handles
        selectedAnnotation?.let { selected ->
            drawSelectionHandles(canvas, selected, viewWidth, viewHeight)
        }
        
        // Draw current drawing path
        if (isDrawing && currentTool == EditorTool.DRAW) {
            shapePaint.color = drawColor
            shapePaint.strokeWidth = drawStrokeWidth
            canvas.drawPath(currentDrawPath, shapePaint)
        }
        
        // Draw shape preview
        if (shapeStartPoint != null && shapeEndPoint != null) {
            val start = shapeStartPoint!!
            val end = shapeEndPoint!!
            drawShapePreview(canvas, start, end)
        }
    }
    
    private fun drawAnnotation(canvas: Canvas, item: AnnotationItem, viewWidth: Float, viewHeight: Float) {
        val x = item.x * viewWidth
        val y = item.y * viewHeight
        val w = item.width * viewWidth
        val h = item.height * viewHeight
        
        when (item.type) {
            AnnotationType.SIGNATURE -> {
                item.bitmap?.let { bitmap ->
                    val destRect = RectF(x, y, x + w, y + h)
                    canvas.drawBitmap(bitmap, null, destRect, null)
                }
            }
            
            AnnotationType.TEXT -> {
                textPaint.color = item.color
                textPaint.textSize = item.textSize * (viewWidth / 1000f) // Scale text
                canvas.drawText(item.text, x, y + textPaint.textSize, textPaint)
            }
            
            AnnotationType.SHAPE -> {
                shapePaint.color = item.color
                shapePaint.strokeWidth = item.strokeWidth
                
                when (item.shapeType) {
                    ShapeType.RECTANGLE -> {
                        canvas.drawRect(x, y, x + w, y + h, shapePaint)
                    }
                    ShapeType.CIRCLE -> {
                        canvas.drawOval(RectF(x, y, x + w, y + h), shapePaint)
                    }
                    ShapeType.LINE -> {
                        canvas.drawLine(x, y, x + w, y + h, shapePaint)
                    }
                    ShapeType.ARROW -> {
                        drawArrow(canvas, x, y, x + w, y + h, shapePaint)
                    }
                    ShapeType.CHECKMARK -> {
                        drawCheckmark(canvas, x, y, w, h, item.color)
                    }
                    ShapeType.CROSS -> {
                        drawCross(canvas, x, y, w, h, shapePaint)
                    }
                }
            }
            
            AnnotationType.STAMP -> {
                item.stampType?.let { stamp ->
                    drawStamp(canvas, stamp, x, y, w, h)
                }
            }
            
            AnnotationType.DRAWING -> {
                if (item.points.isNotEmpty()) {
                    shapePaint.color = item.color
                    shapePaint.strokeWidth = item.strokeWidth
                    
                    val path = Path()
                    val firstPoint = item.points.first()
                    path.moveTo(firstPoint.x * viewWidth, firstPoint.y * viewHeight)
                    
                    for (i in 1 until item.points.size) {
                        val prev = item.points[i - 1]
                        val curr = item.points[i]
                        path.quadTo(
                            prev.x * viewWidth, prev.y * viewHeight,
                            (curr.x + prev.x) / 2 * viewWidth, (curr.y + prev.y) / 2 * viewHeight
                        )
                    }
                    canvas.drawPath(path, shapePaint)
                }
            }
            
            AnnotationType.HIGHLIGHT -> {
                highlightPaint.color = item.color
                highlightPaint.alpha = highlightAlpha
                canvas.drawRect(x, y, x + w, y + h, highlightPaint)
            }
        }
    }
    
    private fun drawArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        // Draw line
        canvas.drawLine(x1, y1, x2, y2, paint)
        
        // Draw arrowhead
        val arrowSize = 30f
        val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        
        val arrowAngle1 = angle - Math.PI / 6
        val arrowAngle2 = angle + Math.PI / 6
        
        canvas.drawLine(
            x2, y2,
            (x2 - arrowSize * Math.cos(arrowAngle1)).toFloat(),
            (y2 - arrowSize * Math.sin(arrowAngle1)).toFloat(),
            paint
        )
        canvas.drawLine(
            x2, y2,
            (x2 - arrowSize * Math.cos(arrowAngle2)).toFloat(),
            (y2 - arrowSize * Math.sin(arrowAngle2)).toFloat(),
            paint
        )
    }
    
    private fun drawCheckmark(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        val checkPaint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        
        val path = Path()
        path.moveTo(x + w * 0.15f, y + h * 0.5f)
        path.lineTo(x + w * 0.4f, y + h * 0.75f)
        path.lineTo(x + w * 0.85f, y + h * 0.25f)
        canvas.drawPath(path, checkPaint)
    }
    
    private fun drawCross(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, paint: Paint) {
        canvas.drawLine(x + w * 0.2f, y + h * 0.2f, x + w * 0.8f, y + h * 0.8f, paint)
        canvas.drawLine(x + w * 0.8f, y + h * 0.2f, x + w * 0.2f, y + h * 0.8f, paint)
    }
    
    private fun drawStamp(canvas: Canvas, stamp: StampType, x: Float, y: Float, w: Float, h: Float) {
        val (bgColor, text) = when (stamp) {
            StampType.APPROVED -> Color.parseColor("#27AE60") to "APPROVED"
            StampType.REJECTED -> Color.parseColor("#E74C3C") to "REJECTED"
            StampType.DRAFT -> Color.parseColor("#F39C12") to "DRAFT"
            StampType.CONFIDENTIAL -> Color.parseColor("#8E44AD") to "CONFIDENTIAL"
            StampType.COPY -> Color.parseColor("#3498DB") to "COPY"
            StampType.FINAL -> Color.parseColor("#1ABC9C") to "FINAL"
            StampType.VOID -> Color.parseColor("#7F8C8D") to "VOID"
            StampType.PAID -> Color.parseColor("#27AE60") to "PAID"
            StampType.RECEIVED -> Color.parseColor("#2980B9") to "RECEIVED"
            StampType.SIGN_HERE -> Color.parseColor("#FF5722") to "SIGN HERE"
            StampType.CUSTOM -> Color.parseColor("#34495E") to "CUSTOM"
        }
        
        // Draw stamp border
        shapePaint.color = bgColor
        shapePaint.strokeWidth = 4f
        canvas.drawRect(x, y, x + w, y + h, shapePaint)
        
        // Draw stamp text
        textPaint.color = bgColor
        textPaint.textSize = h * 0.5f
        textPaint.isFakeBoldText = true
        textPaint.textAlign = Paint.Align.CENTER
        
        val textY = y + h / 2 + textPaint.textSize / 3
        canvas.drawText(text, x + w / 2, textY, textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }
    
    private fun drawShapePreview(canvas: Canvas, start: PointF, end: PointF) {
        val left = min(start.x, end.x)
        val top = min(start.y, end.y)
        val right = max(start.x, end.x)
        val bottom = max(start.y, end.y)
        
        when (currentTool) {
            EditorTool.HIGHLIGHT -> {
                // Draw highlight preview with semi-transparency
                highlightPaint.color = drawColor
                highlightPaint.alpha = highlightAlpha
                canvas.drawRect(left, top, right, bottom, highlightPaint)
            }
            EditorTool.RECTANGLE -> {
                shapePaint.color = drawColor
                shapePaint.strokeWidth = drawStrokeWidth
                canvas.drawRect(left, top, right, bottom, shapePaint)
            }
            EditorTool.CIRCLE -> {
                shapePaint.color = drawColor
                shapePaint.strokeWidth = drawStrokeWidth
                canvas.drawOval(RectF(left, top, right, bottom), shapePaint)
            }
            EditorTool.LINE -> {
                shapePaint.color = drawColor
                shapePaint.strokeWidth = drawStrokeWidth
                canvas.drawLine(start.x, start.y, end.x, end.y, shapePaint)
            }
            EditorTool.ARROW -> {
                shapePaint.color = drawColor
                shapePaint.strokeWidth = drawStrokeWidth
                drawArrow(canvas, start.x, start.y, end.x, end.y, shapePaint)
            }
            else -> {}
        }
    }
    
    private fun drawSelectionHandles(canvas: Canvas, item: AnnotationItem, viewWidth: Float, viewHeight: Float) {
        val x = item.x * viewWidth
        val y = item.y * viewHeight
        val w = item.width * viewWidth
        val h = item.height * viewHeight
        
        // Draw selection rectangle
        canvas.drawRect(x - 5, y - 5, x + w + 5, y + h + 5, selectionPaint)
        
        // Draw corner handles
        val handleSize = 16f
        listOf(
            PointF(x, y),
            PointF(x + w, y),
            PointF(x, y + h),
            PointF(x + w, y + h)
        ).forEachIndexed { index, corner ->
            canvas.drawCircle(corner.x, corner.y, handleSize, handlePaint)
        }
        
        // Draw resize indicator in center
        val centerX = x + w / 2
        val centerY = y + h / 2
        handlePaint.alpha = 100
        canvas.drawCircle(centerX, centerY, handleSize * 0.6f, handlePaint)
        handlePaint.alpha = 255
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                when (currentTool) {
                    EditorTool.SELECT -> {
                        handleSelectTouch(x, y, viewWidth, viewHeight)
                    }
                    EditorTool.DRAW -> {
                        startDrawing(x, y)
                    }
                    EditorTool.HIGHLIGHT -> {
                        shapeStartPoint = PointF(x, y)
                        shapeEndPoint = PointF(x, y) // Initialize end point for immediate feedback
                    }
                    EditorTool.TEXT -> {
                        onTextAnnotationRequested?.invoke(x / viewWidth, y / viewHeight)
                    }
                    EditorTool.SIGNATURE -> {
                        onSignatureRequested?.invoke(x / viewWidth, y / viewHeight)
                    }
                    EditorTool.STAMP -> {
                        onStampRequested?.invoke(x / viewWidth, y / viewHeight)
                    }
                    EditorTool.RECTANGLE, EditorTool.CIRCLE, EditorTool.LINE, EditorTool.ARROW -> {
                        shapeStartPoint = PointF(x, y)
                    }
                    EditorTool.CHECKMARK -> {
                        addCheckmark(x / viewWidth, y / viewHeight)
                    }
                    EditorTool.CROSS -> {
                        addCross(x / viewWidth, y / viewHeight)
                    }
                    EditorTool.ERASER -> {
                        // Eraser mode - delete annotation at touch point
                        val touched = findAnnotationAt(x / viewWidth, y / viewHeight)
                        if (touched != null) {
                            annotations.remove(touched)
                            invalidate()
                            onAnnotationsChanged?.invoke()
                        }
                    }
                }
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                when {
                    isDragging && selectedAnnotation != null -> {
                        handleDrag(x, y, viewWidth, viewHeight)
                    }
                    isDrawing && currentTool == EditorTool.DRAW -> {
                        continueDrawing(x, y)
                    }
                    shapeStartPoint != null -> {
                        shapeEndPoint = PointF(x, y)
                    }
                }
                invalidate()
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                when {
                    isDragging -> {
                        isDragging = false
                        onAnnotationsChanged?.invoke()
                    }
                    isDrawing -> {
                        finishDrawing()
                    }
                    shapeStartPoint != null && shapeEndPoint != null -> {
                        finishShape()
                    }
                }
                invalidate()
                return true
            }
        }
        return false
    }
    
    private fun handleSelectTouch(x: Float, y: Float, viewWidth: Float, viewHeight: Float) {
        // Check if touching an annotation
        val touched = findAnnotationAt(x / viewWidth, y / viewHeight)
        
        if (touched != null) {
            selectedAnnotation = touched
            isDragging = true
            dragStartX = x
            dragStartY = y
            annotationStartX = touched.x
            annotationStartY = touched.y
        } else {
            selectedAnnotation = null
        }
        
        onAnnotationSelected?.invoke(selectedAnnotation)
        invalidate()
    }
    
    private fun handleDrag(x: Float, y: Float, viewWidth: Float, viewHeight: Float) {
        selectedAnnotation?.let { annotation ->
            val dx = (x - dragStartX) / viewWidth
            val dy = (y - dragStartY) / viewHeight
            
            annotation.x = (annotationStartX + dx).coerceIn(0f, 1f - annotation.width)
            annotation.y = (annotationStartY + dy).coerceIn(0f, 1f - annotation.height)
        }
    }
    
    private fun findAnnotationAt(x: Float, y: Float): AnnotationItem? {
        // Search from top (last added) to bottom
        for (i in annotations.indices.reversed()) {
            val item = annotations[i]
            if (x >= item.x && x <= item.x + item.width &&
                y >= item.y && y <= item.y + item.height) {
                return item
            }
        }
        return null
    }
    
    private fun startDrawing(x: Float, y: Float) {
        isDrawing = true
        currentDrawPath.reset()
        currentDrawPath.moveTo(x, y)
        currentDrawPoints.clear()
        currentDrawPoints.add(PointF(x / width, y / height))
    }
    
    private fun continueDrawing(x: Float, y: Float) {
        val lastX = currentDrawPoints.lastOrNull()?.x ?: return
        val lastY = currentDrawPoints.lastOrNull()?.y ?: return
        
        currentDrawPath.quadTo(
            lastX * width, lastY * height,
            (x + lastX * width) / 2, (y + lastY * height) / 2
        )
        currentDrawPoints.add(PointF(x / width, y / height))
    }
    
    private fun finishDrawing() {
        isDrawing = false
        
        if (currentDrawPoints.size > 1) {
            val bounds = calculateBounds(currentDrawPoints)
            
            addAnnotation(AnnotationItem(
                id = generateId(),
                type = AnnotationType.DRAWING,
                x = bounds.left,
                y = bounds.top,
                width = bounds.width(),
                height = bounds.height(),
                color = drawColor,
                strokeWidth = drawStrokeWidth,
                points = currentDrawPoints.toList()
            ))
        }
        
        currentDrawPath.reset()
        currentDrawPoints.clear()
    }
    
    private fun finishShape() {
        val start = shapeStartPoint ?: return
        val end = shapeEndPoint ?: return
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        
        val x1 = start.x / viewWidth
        val y1 = start.y / viewHeight
        val x2 = end.x / viewWidth
        val y2 = end.y / viewHeight
        
        val left = min(x1, x2)
        val top = min(y1, y2)
        val w = abs(x2 - x1)
        val h = abs(y2 - y1)
        
        if (w > 0.01f || h > 0.01f) {
            // Check if it's a highlight
            if (currentTool == EditorTool.HIGHLIGHT) {
                addAnnotation(AnnotationItem(
                    id = generateId(),
                    type = AnnotationType.HIGHLIGHT,
                    x = left,
                    y = top,
                    width = w,
                    height = h,
                    color = drawColor
                ))
            } else {
                val shapeType = when (currentTool) {
                    EditorTool.RECTANGLE -> ShapeType.RECTANGLE
                    EditorTool.CIRCLE -> ShapeType.CIRCLE
                    EditorTool.LINE -> ShapeType.LINE
                    EditorTool.ARROW -> ShapeType.ARROW
                    else -> ShapeType.RECTANGLE
                }
                
                addAnnotation(AnnotationItem(
                    id = generateId(),
                    type = AnnotationType.SHAPE,
                    x = left,
                    y = top,
                    width = w,
                    height = h,
                    color = drawColor,
                    strokeWidth = drawStrokeWidth,
                    shapeType = shapeType
                ))
            }
        }
        
        shapeStartPoint = null
        shapeEndPoint = null
    }
    
    private fun addCheckmark(x: Float, y: Float) {
        val size = 0.05f
        addAnnotation(AnnotationItem(
            id = generateId(),
            type = AnnotationType.SHAPE,
            x = x - size / 2,
            y = y - size / 2,
            width = size,
            height = size,
            color = Color.parseColor("#27AE60"),
            shapeType = ShapeType.CHECKMARK
        ))
    }
    
    private fun addCross(x: Float, y: Float) {
        val size = 0.05f
        addAnnotation(AnnotationItem(
            id = generateId(),
            type = AnnotationType.SHAPE,
            x = x - size / 2,
            y = y - size / 2,
            width = size,
            height = size,
            color = Color.parseColor("#E74C3C"),
            strokeWidth = 4f,
            shapeType = ShapeType.CROSS
        ))
    }
    
    private fun calculateBounds(points: List<PointF>): RectF {
        var minX = 1f
        var minY = 1f
        var maxX = 0f
        var maxY = 0f
        
        for (point in points) {
            minX = min(minX, point.x)
            minY = min(minY, point.y)
            maxX = max(maxX, point.x)
            maxY = max(maxY, point.y)
        }
        
        return RectF(minX, minY, maxX, maxY)
    }
    
    // Public methods
    
    fun addAnnotation(item: AnnotationItem) {
        annotations.add(item)
        invalidate()
        onAnnotationsChanged?.invoke()
    }
    
    fun addSignature(bitmap: Bitmap, x: Float, y: Float) {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height
        val height = 0.1f
        val width = height * aspectRatio
        
        addAnnotation(AnnotationItem(
            id = generateId(),
            type = AnnotationType.SIGNATURE,
            x = x,
            y = y,
            width = width,
            height = height,
            bitmap = bitmap
        ))
    }
    
    fun addText(text: String, x: Float, y: Float, color: Int, size: Float) {
        // Estimate text width
        textPaint.textSize = size
        val textWidth = textPaint.measureText(text) / width
        val textHeight = size / height
        
        addAnnotation(AnnotationItem(
            id = generateId(),
            type = AnnotationType.TEXT,
            x = x,
            y = y,
            width = textWidth,
            height = textHeight,
            color = color,
            text = text,
            textSize = size
        ))
    }
    
    fun addStamp(stamp: StampType, x: Float, y: Float) {
        val width = 0.2f
        val height = 0.06f
        
        addAnnotation(AnnotationItem(
            id = generateId(),
            type = AnnotationType.STAMP,
            x = x - width / 2,
            y = y - height / 2,
            width = width,
            height = height,
            stampType = stamp
        ))
    }
    
    fun deleteSelected(): Boolean {
        selectedAnnotation?.let { selected ->
            annotations.remove(selected)
            selectedAnnotation = null
            invalidate()
            onAnnotationsChanged?.invoke()
            return true
        }
        return false
    }
    
    fun clearAnnotations() {
        annotations.clear()
        selectedAnnotation = null
        invalidate()
        onAnnotationsChanged?.invoke()
    }
    
    fun getAnnotations(): List<AnnotationItem> = annotations.toList()
    
    fun setAnnotations(items: List<AnnotationItem>) {
        annotations.clear()
        annotations.addAll(items)
        invalidate()
    }
    
    /**
     * Resize the currently selected annotation
     * @param scaleFactor Factor to scale by (> 1 = larger, < 1 = smaller)
     */
    fun resizeSelected(scaleFactor: Float): Boolean {
        selectedAnnotation?.let { annotation ->
            val newWidth = (annotation.width * scaleFactor).coerceIn(0.02f, 0.8f)
            val newHeight = (annotation.height * scaleFactor).coerceIn(0.02f, 0.8f)
            
            // Keep centered
            val widthDiff = newWidth - annotation.width
            val heightDiff = newHeight - annotation.height
            
            annotation.x = (annotation.x - widthDiff / 2).coerceIn(0f, 1f - newWidth)
            annotation.y = (annotation.y - heightDiff / 2).coerceIn(0f, 1f - newHeight)
            annotation.width = newWidth
            annotation.height = newHeight
            
            // Also scale text size if it's a text annotation
            if (annotation.type == AnnotationType.TEXT) {
                annotation.textSize = (annotation.textSize * scaleFactor).coerceIn(12f, 200f)
            }
            
            invalidate()
            onAnnotationsChanged?.invoke()
            return true
        }
        return false
    }
    
    /**
     * Get the currently selected annotation
     */
    fun getSelectedAnnotation(): AnnotationItem? = selectedAnnotation
    
    /**
     * Check if any annotation is selected
     */
    fun hasSelection(): Boolean = selectedAnnotation != null
    
    /**
     * Clear the current selection
     */
    fun clearSelection() {
        selectedAnnotation = null
        onAnnotationSelected?.invoke(null)
        invalidate()
    }
    
    private fun generateId(): String = "ann_${System.currentTimeMillis()}_${(0..999).random()}"
}
