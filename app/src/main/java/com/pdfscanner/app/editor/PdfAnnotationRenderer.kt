/**
 * PdfAnnotationRenderer.kt - Renders annotations onto PDF pages
 * 
 * Takes the original PDF and annotation data, then produces a new PDF
 * with all annotations flattened onto the pages.
 */

package com.pdfscanner.app.editor

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Renders PDF annotations to produce a new annotated PDF file
 */
class PdfAnnotationRenderer(private val context: Context) {
    
    private val pdfsDir: File by lazy {
        File(context.filesDir, "pdfs").apply { mkdirs() }
    }
    
    /**
     * Render annotations onto the PDF and save as a new file
     * 
     * @param inputUri URI of the original PDF
     * @param pageAnnotations Map of page index to list of annotations
     * @return The output file, or null if rendering failed
     */
    fun renderAnnotatedPdf(
        inputUri: Uri,
        pageAnnotations: Map<Int, List<AnnotationCanvasView.AnnotationItem>>
    ): File? {
        return try {
            // Open the input PDF
            val inputPfd = context.contentResolver.openFileDescriptor(inputUri, "r")
                ?: return null
            
            val renderer = PdfRenderer(inputPfd)
            val pageCount = renderer.pageCount
            
            // Create output document
            val outputDoc = PdfDocument()
            
            for (pageIndex in 0 until pageCount) {
                // Render each page
                val page = renderer.openPage(pageIndex)
                
                val pageWidth = page.width
                val pageHeight = page.height
                
                // Create bitmap for the page
                val bitmap = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                
                // Render original PDF page
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()
                
                // Draw annotations on top
                val canvas = Canvas(bitmap)
                val annotations = pageAnnotations[pageIndex] ?: emptyList()
                
                for (annotation in annotations) {
                    drawAnnotation(canvas, annotation, pageWidth.toFloat(), pageHeight.toFloat())
                }
                
                // Create page in output document
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val outputPage = outputDoc.startPage(pageInfo)
                
                // Draw the annotated bitmap to the page
                outputPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                outputDoc.finishPage(outputPage)
                
                // Clean up bitmap
                bitmap.recycle()
            }
            
            renderer.close()
            inputPfd.close()
            
            // Generate output filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val outputFile = File(pdfsDir, "Edited_$timestamp.pdf")
            
            // Write output document
            FileOutputStream(outputFile).use { out ->
                outputDoc.writeTo(out)
            }
            
            outputDoc.close()
            
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Draw a single annotation onto the canvas
     */
    private fun drawAnnotation(
        canvas: Canvas,
        item: AnnotationCanvasView.AnnotationItem,
        pageWidth: Float,
        pageHeight: Float
    ) {
        val x = item.x * pageWidth
        val y = item.y * pageHeight
        val w = item.width * pageWidth
        val h = item.height * pageHeight
        
        when (item.type) {
            AnnotationCanvasView.AnnotationType.SIGNATURE -> {
                item.bitmap?.let { bitmap ->
                    val destRect = RectF(x, y, x + w, y + h)
                    canvas.drawBitmap(bitmap, null, destRect, null)
                }
            }
            
            AnnotationCanvasView.AnnotationType.TEXT -> {
                val paint = Paint().apply {
                    color = item.color
                    textSize = item.textSize * (pageWidth / 1000f)
                    isAntiAlias = true
                }
                canvas.drawText(item.text, x, y + paint.textSize, paint)
            }
            
            AnnotationCanvasView.AnnotationType.SHAPE -> {
                val paint = Paint().apply {
                    color = item.color
                    strokeWidth = item.strokeWidth
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                
                when (item.shapeType) {
                    ShapeType.RECTANGLE -> {
                        canvas.drawRect(x, y, x + w, y + h, paint)
                    }
                    ShapeType.CIRCLE -> {
                        canvas.drawOval(RectF(x, y, x + w, y + h), paint)
                    }
                    ShapeType.LINE -> {
                        canvas.drawLine(x, y, x + w, y + h, paint)
                    }
                    ShapeType.ARROW -> {
                        drawArrow(canvas, x, y, x + w, y + h, paint)
                    }
                    ShapeType.CHECKMARK -> {
                        drawCheckmark(canvas, x, y, w, h, item.color)
                    }
                    ShapeType.CROSS -> {
                        drawCross(canvas, x, y, w, h, paint)
                    }
                }
            }
            
            AnnotationCanvasView.AnnotationType.STAMP -> {
                item.stampType?.let { stamp ->
                    drawStamp(canvas, stamp, x, y, w, h)
                }
            }
            
            AnnotationCanvasView.AnnotationType.DRAWING -> {
                if (item.points.isNotEmpty()) {
                    val paint = Paint().apply {
                        color = item.color
                        strokeWidth = item.strokeWidth
                        style = Paint.Style.STROKE
                        strokeJoin = Paint.Join.ROUND
                        strokeCap = Paint.Cap.ROUND
                        isAntiAlias = true
                    }
                    
                    val path = Path()
                    val firstPoint = item.points.first()
                    path.moveTo(firstPoint.x * pageWidth, firstPoint.y * pageHeight)
                    
                    for (i in 1 until item.points.size) {
                        val prev = item.points[i - 1]
                        val curr = item.points[i]
                        path.quadTo(
                            prev.x * pageWidth, prev.y * pageHeight,
                            (curr.x + prev.x) / 2 * pageWidth, (curr.y + prev.y) / 2 * pageHeight
                        )
                    }
                    canvas.drawPath(path, paint)
                }
            }
            
            AnnotationCanvasView.AnnotationType.HIGHLIGHT -> {
                // Highlight is drawn as a semi-transparent rectangle
                val paint = Paint().apply {
                    color = item.color
                    alpha = 80 // Semi-transparent for highlight effect
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawRect(x, y, x + w, y + h, paint)
            }
        }
    }
    
    private fun drawArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        canvas.drawLine(x1, y1, x2, y2, paint)
        
        val arrowSize = 20f
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
        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        
        val path = Path()
        path.moveTo(x + w * 0.15f, y + h * 0.5f)
        path.lineTo(x + w * 0.4f, y + h * 0.75f)
        path.lineTo(x + w * 0.85f, y + h * 0.25f)
        canvas.drawPath(path, paint)
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
        val borderPaint = Paint().apply {
            color = bgColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRect(x, y, x + w, y + h, borderPaint)
        
        // Draw stamp text
        val textPaint = Paint().apply {
            color = bgColor
            textSize = h * 0.5f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        
        val textY = y + h / 2 + textPaint.textSize / 3
        canvas.drawText(text, x + w / 2, textY, textPaint)
    }
}
