/**
 * PdfAnnotation.kt - Data Models for PDF Annotations
 * 
 * Defines all annotation types that can be added to PDFs:
 * - Signatures (drawn paths)
 * - Text boxes (positioned text)
 * - Shapes (rectangles, lines, circles)
 * - Stamps (predefined text overlays)
 * 
 * All positions are stored as percentages (0.0-1.0) of page dimensions
 * to support different screen sizes and PDF scales.
 */

package com.pdfscanner.app.editor

import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import java.io.Serializable
import java.util.UUID

/**
 * Base class for all PDF annotations
 */
sealed class PdfAnnotation : Serializable {
    abstract val id: String
    abstract val pageNumber: Int
    abstract var x: Float  // Position as percentage of page width (0.0-1.0)
    abstract var y: Float  // Position as percentage of page height (0.0-1.0)
    
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Signature annotation - stores drawing path points
 * 
 * @property points List of points making up the signature path
 * @property strokeWidth Width of the signature stroke
 * @property color Signature color (usually black or blue)
 * @property width Width as percentage of page (for scaling)
 * @property height Height as percentage of page (for scaling)
 */
data class SignatureAnnotation(
    override val id: String = UUID.randomUUID().toString(),
    override val pageNumber: Int,
    override var x: Float,
    override var y: Float,
    val points: List<List<PointF>>,  // List of strokes, each stroke is list of points
    val strokeWidth: Float = 4f,
    val color: Int = Color.BLACK,
    val width: Float = 0.3f,   // 30% of page width by default
    val height: Float = 0.1f  // 10% of page height by default
) : PdfAnnotation() {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Text box annotation - positioned text on PDF
 * 
 * @property text The text content
 * @property fontSize Font size in sp
 * @property color Text color
 * @property backgroundColor Background color (transparent by default)
 * @property isBold Whether text is bold
 * @property isItalic Whether text is italic
 */
data class TextAnnotation(
    override val id: String = UUID.randomUUID().toString(),
    override val pageNumber: Int,
    override var x: Float,
    override var y: Float,
    var text: String,
    val fontSize: Float = 14f,
    val color: Int = Color.BLACK,
    val backgroundColor: Int = Color.TRANSPARENT,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
) : PdfAnnotation() {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Shape annotation - rectangles, circles, lines, arrows
 */
data class ShapeAnnotation(
    override val id: String = UUID.randomUUID().toString(),
    override val pageNumber: Int,
    override var x: Float,
    override var y: Float,
    val shapeType: ShapeType,
    var width: Float,   // Width as percentage of page
    var height: Float,  // Height as percentage of page
    val strokeWidth: Float = 3f,
    val strokeColor: Int = Color.RED,
    val fillColor: Int = Color.TRANSPARENT
) : PdfAnnotation() {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Types of shapes that can be drawn
 */
enum class ShapeType {
    RECTANGLE,
    CIRCLE,
    LINE,
    ARROW,
    CHECKMARK,
    CROSS
}

/**
 * Stamp annotation - predefined text/image overlays
 * 
 * @property stampType Type of stamp (APPROVED, DRAFT, etc.)
 * @property scale Scale factor for the stamp (1.0 = 100%)
 * @property rotation Rotation in degrees
 */
data class StampAnnotation(
    override val id: String = UUID.randomUUID().toString(),
    override val pageNumber: Int,
    override var x: Float,
    override var y: Float,
    val stampType: StampType,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val color: Int = Color.RED
) : PdfAnnotation() {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Types of stamps available
 */
enum class StampType(val displayText: String, val defaultColor: Int) {
    APPROVED("APPROVED", Color.parseColor("#4CAF50")),     // Green
    REJECTED("REJECTED", Color.parseColor("#F44336")),     // Red
    DRAFT("DRAFT", Color.parseColor("#FF9800")),           // Orange
    CONFIDENTIAL("CONFIDENTIAL", Color.parseColor("#9C27B0")),  // Purple
    COPY("COPY", Color.parseColor("#2196F3")),             // Blue
    FINAL("FINAL", Color.parseColor("#4CAF50")),           // Green
    VOID("VOID", Color.parseColor("#F44336")),             // Red
    PAID("PAID", Color.parseColor("#4CAF50")),             // Green
    RECEIVED("RECEIVED", Color.parseColor("#2196F3")),     // Blue
    SIGN_HERE("SIGN HERE", Color.parseColor("#FF5722")),   // Deep Orange
    CUSTOM("CUSTOM", Color.parseColor("#607D8B"))          // Blue Grey
}

/**
 * Freehand drawing annotation - for highlighting, underlining, etc.
 */
data class DrawingAnnotation(
    override val id: String = UUID.randomUUID().toString(),
    override val pageNumber: Int,
    override var x: Float = 0f,
    override var y: Float = 0f,
    val points: List<List<PointF>>,  // Multiple strokes
    val strokeWidth: Float = 8f,
    val color: Int = Color.YELLOW,
    val alpha: Int = 128  // Semi-transparent for highlighting
) : PdfAnnotation() {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Container for all annotations on a PDF document
 */
data class PdfAnnotations(
    val pdfPath: String,
    val annotations: MutableList<PdfAnnotation> = mutableListOf(),
    val savedSignatures: MutableList<SignatureAnnotation> = mutableListOf()
) : Serializable {
    
    fun getAnnotationsForPage(pageNumber: Int): List<PdfAnnotation> {
        return annotations.filter { it.pageNumber == pageNumber }
    }
    
    fun addAnnotation(annotation: PdfAnnotation) {
        annotations.add(annotation)
    }
    
    fun removeAnnotation(id: String) {
        annotations.removeAll { it.id == id }
    }
    
    fun clearPage(pageNumber: Int) {
        annotations.removeAll { it.pageNumber == pageNumber }
    }
    
    fun clearAll() {
        annotations.clear()
    }
    
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Editing tool types for the toolbar
 */
enum class EditorTool {
    SELECT,      // Select and move annotations
    SIGNATURE,   // Add signature
    TEXT,        // Add text box
    HIGHLIGHT,   // Highlight text/areas
    STAMP,       // Add stamps
    DRAW,        // Freehand drawing
    ERASER,      // Remove annotations
    // Shape tools
    RECTANGLE,   // Draw rectangle
    CIRCLE,      // Draw circle/oval
    LINE,        // Draw line
    ARROW,       // Draw arrow
    CHECKMARK,   // Quick checkmark
    CROSS        // Quick X mark
}
