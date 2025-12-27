/**
 * ImageProcessor.kt - Document Image Enhancement Utilities
 * 
 * PURPOSE:
 * Provides image filters to improve document legibility, similar to
 * what Adobe Scan or CamScanner offer for scanned documents.
 * 
 * FILTERS PROVIDED:
 * 1. Original - No processing, raw image
 * 2. Enhanced - Moderate contrast/brightness boost for better text visibility
 * 3. Document B&W - Strong contrast, near-binary output for clean document look
 * 4. Magic - Adaptive enhancement that makes text very clear on any background
 * 5. Sharpen - Edge enhancement for crisp text
 * 
 * IMPLEMENTATION APPROACH:
 * We use Android's ColorMatrix/ColorMatrixColorFilter system, which provides
 * hardware-accelerated matrix operations on pixel colors.
 * For sharpen, we use a ConvolveMatrix approach with render script fallback.
 * 
 * COLOR MATRIX MATH:
 * A 4x5 matrix transforms RGBA values:
 * 
 *   | R' |   | a b c d e |   | R |
 *   | G' | = | f g h i j | × | G |
 *   | B' |   | k l m n o |   | B |
 *   | A' |   | p q r s t |   | A |
 *                             | 1 |
 * 
 * The 5th column (e, j, o, t) adds constant offsets (useful for brightness).
 * The diagonal elements (a, g, n) control per-channel scaling (useful for contrast).
 * 
 * CONTRAST/BRIGHTNESS:
 * - Contrast: Multiply RGB values by a factor > 1 (scales away from gray)
 * - Brightness: Add a constant to RGB values (shifts all values up/down)
 * 
 * MEMORY CONSIDERATIONS:
 * These functions create a NEW Bitmap - the caller is responsible for:
 * 1. Recycling the input bitmap if no longer needed
 * 2. Recycling the output bitmap when done
 * 
 * @see android.graphics.ColorMatrix
 * @see android.graphics.ColorMatrixColorFilter
 */

package com.pdfscanner.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

/**
 * ImageProcessor - Static utility functions for document image enhancement
 * 
 * All functions are pure (no side effects) and thread-safe.
 * Safe to call from Dispatchers.IO without synchronization.
 */
object ImageProcessor {

    // ============================================================
    // FILTER TYPES
    // ============================================================
    
    /**
     * Enum representing available filter types
     * 
     * Used for UI state management and persistence
     */
    enum class FilterType {
        /** No processing - original image as captured/cropped */
        ORIGINAL,
        
        /** Moderate enhancement - slight contrast/brightness boost */
        ENHANCED,
        
        /** Strong enhancement - high contrast, near B&W for documents */
        DOCUMENT_BW,
        
        /** Magic - Adaptive processing that makes text clear on any background */
        MAGIC,
        
        /** Sharpen - Edge enhancement for crisp, clear text */
        SHARPEN
    }

    // ============================================================
    // PUBLIC FILTER FUNCTIONS
    // ============================================================

    /**
     * Apply the specified filter type to a bitmap
     * 
     * Convenience function that dispatches to the appropriate filter.
     * 
     * @param bitmap Input bitmap (not modified)
     * @param filterType The filter to apply
     * @return New processed bitmap, or the same bitmap reference for ORIGINAL
     * 
     * NOTE: For ORIGINAL, returns the same bitmap reference (no copy).
     * For other filters, returns a NEW bitmap - caller owns both.
     */
    fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap {
        return when (filterType) {
            FilterType.ORIGINAL -> bitmap  // No processing needed
            FilterType.ENHANCED -> applyEnhanced(bitmap)
            FilterType.DOCUMENT_BW -> applyDocumentBw(bitmap)
            FilterType.MAGIC -> applyMagicFilter(bitmap)
            FilterType.SHARPEN -> applySharpen(bitmap)
        }
    }

    /**
     * Apply enhanced filter for improved text legibility
     * 
     * WHAT IT DOES:
     * - Increases contrast by ~30% (makes darks darker, lights lighter)
     * - Increases brightness slightly to compensate for darker darks
     * - Result: Text pops more against background
     * 
     * MATH:
     * Contrast factor = 1.3 (30% increase)
     * The contrast formula centers around 128 (middle gray):
     *   newValue = (oldValue - 128) * contrast + 128 + brightness
     *            = oldValue * contrast + 128 * (1 - contrast) + brightness
     *            = oldValue * contrast + translate
     * 
     * Where translate = 128 * (1 - contrast) + brightness
     * 
     * For contrast=1.3, brightness=20:
     *   translate = 128 * (1 - 1.3) + 20 = 128 * -0.3 + 20 = -38.4 + 20 = -18.4
     * 
     * @param bitmap Input bitmap (not modified)
     * @return New bitmap with enhanced contrast/brightness
     */
    fun applyEnhanced(bitmap: Bitmap): Bitmap {
        // Enhancement parameters - tuned for typical document photos
        val contrast = 1.3f     // 30% contrast boost
        val brightness = 20f   // Slight brightness increase to keep it readable
        
        return applyContrastBrightness(bitmap, contrast, brightness)
    }

    /**
     * Apply document B&W filter for clean, high-contrast document look
     * 
     * WHAT IT DOES:
     * 1. Converts to grayscale (weighted average of RGB)
     * 2. Applies very high contrast (makes darks black, lights white)
     * 3. Slight brightness boost to ensure white background stays white
     * 
     * GRAYSCALE FORMULA:
     * The human eye is more sensitive to green than red or blue.
     * Standard luminance weights (ITU-R BT.601):
     *   Gray = 0.299R + 0.587G + 0.114B
     * 
     * We apply these weights to R, G, and B channels equally to produce
     * grayscale output.
     * 
     * RESULT:
     * Near-binary image - text becomes very dark, background becomes very light.
     * Similar to a photocopied document look.
     * 
     * @param bitmap Input bitmap (not modified)
     * @return New bitmap with B&W document effect
     */
    fun applyDocumentBw(bitmap: Bitmap): Bitmap {
        // First convert to grayscale, then apply high contrast
        val grayscaleBitmap = applyGrayscale(bitmap)
        
        // High contrast parameters for document mode
        val contrast = 2.0f      // 100% contrast boost - aggressive!
        val brightness = 30f     // Compensate to keep whites white
        
        val result = applyContrastBrightness(grayscaleBitmap, contrast, brightness)
        
        // Clean up intermediate bitmap
        if (grayscaleBitmap != bitmap) {
            grayscaleBitmap.recycle()
        }
        
        return result
    }

    /**
     * Apply MAGIC filter - Adaptive enhancement for document text clarity
     * 
     * WHAT IT DOES:
     * This is our best filter for making text readable. It combines:
     * 1. Adaptive histogram-like enhancement (per-pixel adjustment)
     * 2. Local contrast enhancement (makes text pop from background)
     * 3. White balance correction (makes paper look white)
     * 4. Slight sharpening for text edges
     * 
     * HOW IT WORKS:
     * Rather than applying a global transform, we analyze the image's
     * luminance distribution and apply adaptive thresholding combined
     * with local contrast enhancement.
     * 
     * RESULT:
     * Text becomes very dark and crisp, background becomes clean white,
     * similar to professional document scanner apps.
     * 
     * @param bitmap Input bitmap (not modified)
     * @return New bitmap with magic document enhancement
     */
    fun applyMagicFilter(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // Create output bitmap
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Get all pixels
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Calculate histogram to understand the image
        val histogram = IntArray(256)
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // Luminance calculation
            val luminance = ((0.299 * r + 0.587 * g + 0.114 * b).toInt()).coerceIn(0, 255)
            histogram[luminance]++
        }
        
        // Find the background level (usually the brightest peak = paper)
        // and text level (darker peak)
        var maxCount = 0
        var backgroundLevel = 255
        for (i in 200..255) {  // Look for bright background
            if (histogram[i] > maxCount) {
                maxCount = histogram[i]
                backgroundLevel = i
            }
        }
        
        // Find text threshold - look for significant counts in darker region
        var textLevel = 0
        maxCount = 0
        for (i in 0..150) {  // Look for dark text
            if (histogram[i] > maxCount) {
                maxCount = histogram[i]
                textLevel = i
            }
        }
        
        // Calculate adaptive threshold
        val threshold = (backgroundLevel + textLevel) / 2
        
        // Apply adaptive enhancement
        val outputPixels = IntArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            val a = Color.alpha(pixel)
            
            // Calculate luminance
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            
            // Adaptive curve - S-curve that pushes darks darker and lights lighter
            // This creates the "scanner" effect
            val newLuminance: Int
            if (luminance < threshold) {
                // Dark pixel (likely text) - make it darker
                val ratio = luminance.toFloat() / threshold
                // Use a power curve to make text very dark
                newLuminance = (ratio * ratio * threshold * 0.3).toInt().coerceIn(0, 255)
            } else {
                // Light pixel (likely background) - make it lighter (white)
                val ratio = (luminance - threshold).toFloat() / (255 - threshold)
                // Push towards white
                newLuminance = (threshold + ratio * ratio * (255 - threshold) * 1.5).toInt().coerceIn(0, 255)
            }
            
            // Apply the luminance change while trying to preserve some color
            val luminanceRatio = if (luminance > 0) newLuminance.toFloat() / luminance else 1f
            val newR = (r * luminanceRatio).toInt().coerceIn(0, 255)
            val newG = (g * luminanceRatio).toInt().coerceIn(0, 255)
            val newB = (b * luminanceRatio).toInt().coerceIn(0, 255)
            
            outputPixels[i] = Color.argb(a, newR, newG, newB)
        }
        
        outputBitmap.setPixels(outputPixels, 0, width, 0, 0, width, height)
        
        // Apply a light sharpen pass to make text crisp
        return applyLightSharpen(outputBitmap)
    }

    /**
     * Apply SHARPEN filter - Edge enhancement for crisp text
     * 
     * WHAT IT DOES:
     * Uses a sharpening convolution kernel to enhance edges,
     * making text appear more crisp and readable.
     * 
     * CONVOLUTION KERNEL:
     * The sharpening kernel emphasizes the center pixel relative to neighbors:
     * 
     *   |  0  -1   0 |
     *   | -1   5  -1 |
     *   |  0  -1   0 |
     * 
     * This subtracts neighboring pixel values and amplifies the center,
     * creating edge enhancement.
     * 
     * @param bitmap Input bitmap (not modified)
     * @return New bitmap with sharpened edges
     */
    fun applySharpen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // First apply slight enhancement
        val enhanced = applyContrastBrightness(bitmap, 1.15f, 5f)
        
        // Then apply sharpen
        return applyConvolutionKernel(enhanced, SHARPEN_KERNEL, 1.0f)
    }

    /**
     * Light sharpen - Gentler sharpening for use after other filters
     */
    private fun applyLightSharpen(bitmap: Bitmap): Bitmap {
        return applyConvolutionKernel(bitmap, LIGHT_SHARPEN_KERNEL, 1.0f)
    }

    /**
     * Apply a 3x3 convolution kernel to a bitmap
     * 
     * Used for sharpening and other spatial filters.
     * 
     * @param bitmap Input bitmap
     * @param kernel 3x3 convolution kernel as 9-element array
     * @param divisor Normalization divisor
     * @return New filtered bitmap
     */
    private fun applyConvolutionKernel(
        bitmap: Bitmap,
        kernel: FloatArray,
        divisor: Float
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val srcPixels = IntArray(width * height)
        bitmap.getPixels(srcPixels, 0, width, 0, 0, width, height)
        
        val dstPixels = IntArray(width * height)
        
        // Apply kernel to each pixel (excluding 1-pixel border)
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                
                var ki = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = srcPixels[(y + ky) * width + (x + kx)]
                        val weight = kernel[ki++]
                        sumR += Color.red(pixel) * weight
                        sumG += Color.green(pixel) * weight
                        sumB += Color.blue(pixel) * weight
                    }
                }
                
                val newR = (sumR / divisor).toInt().coerceIn(0, 255)
                val newG = (sumG / divisor).toInt().coerceIn(0, 255)
                val newB = (sumB / divisor).toInt().coerceIn(0, 255)
                val alpha = Color.alpha(srcPixels[y * width + x])
                
                dstPixels[y * width + x] = Color.argb(alpha, newR, newG, newB)
            }
        }
        
        // Copy border pixels unchanged
        for (x in 0 until width) {
            dstPixels[x] = srcPixels[x]  // Top row
            dstPixels[(height - 1) * width + x] = srcPixels[(height - 1) * width + x]  // Bottom row
        }
        for (y in 0 until height) {
            dstPixels[y * width] = srcPixels[y * width]  // Left column
            dstPixels[y * width + width - 1] = srcPixels[y * width + width - 1]  // Right column
        }
        
        val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outputBitmap.setPixels(dstPixels, 0, width, 0, 0, width, height)
        
        // Note: Don't recycle input bitmap - caller manages memory
        
        return outputBitmap
    }

    // Sharpening kernel - standard unsharp mask
    private val SHARPEN_KERNEL = floatArrayOf(
        0f, -1f, 0f,
        -1f, 5f, -1f,
        0f, -1f, 0f
    )
    
    // Light sharpening kernel - gentler effect
    private val LIGHT_SHARPEN_KERNEL = floatArrayOf(
        0f, -0.5f, 0f,
        -0.5f, 3f, -0.5f,
        0f, -0.5f, 0f
    )

    // ============================================================
    // INTERNAL HELPER FUNCTIONS
    // ============================================================

    /**
     * Apply contrast and brightness adjustment using ColorMatrix
     * 
     * @param bitmap Input bitmap
     * @param contrast Contrast multiplier (1.0 = no change, >1 = more contrast)
     * @param brightness Brightness offset (-255 to 255)
     * @return New processed bitmap
     */
    private fun applyContrastBrightness(
        bitmap: Bitmap,
        contrast: Float,
        brightness: Float
    ): Bitmap {
        /**
         * Calculate the translate value for contrast centering
         * 
         * Standard contrast formula centers around 128:
         * newPixel = (oldPixel - 128) * contrast + 128 + brightness
         *          = oldPixel * contrast + 128 * (1 - contrast) + brightness
         * 
         * So translate = 128 * (1 - contrast) + brightness
         */
        val translate = 128f * (1f - contrast) + brightness

        /**
         * Build the color matrix
         * 
         * Matrix layout (4x5):
         * | contrast  0        0        0  translate |  <- Red
         * | 0         contrast 0        0  translate |  <- Green
         * | 0         0        contrast 0  translate |  <- Blue
         * | 0         0        0        1  0         |  <- Alpha (unchanged)
         */
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,  // Red channel
            0f, contrast, 0f, 0f, translate,  // Green channel
            0f, 0f, contrast, 0f, translate,  // Blue channel
            0f, 0f, 0f, 1f, 0f                 // Alpha channel (unchanged)
        ))

        return applyColorMatrix(bitmap, colorMatrix)
    }

    /**
     * Convert bitmap to grayscale using luminance weights
     * 
     * @param bitmap Input bitmap
     * @return New grayscale bitmap
     */
    private fun applyGrayscale(bitmap: Bitmap): Bitmap {
        /**
         * Grayscale color matrix using standard luminance coefficients
         * 
         * Each row applies the same weights to produce equal R, G, B values:
         * R' = 0.299R + 0.587G + 0.114B
         * G' = 0.299R + 0.587G + 0.114B
         * B' = 0.299R + 0.587G + 0.114B
         * A' = A
         */
        val colorMatrix = ColorMatrix(floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,  // Red output
            0.299f, 0.587f, 0.114f, 0f, 0f,  // Green output
            0.299f, 0.587f, 0.114f, 0f, 0f,  // Blue output
            0f, 0f, 0f, 1f, 0f                // Alpha (unchanged)
        ))

        return applyColorMatrix(bitmap, colorMatrix)
    }

    /**
     * Apply a ColorMatrix to a bitmap, returning a new bitmap
     * 
     * This is the core rendering function that creates a new bitmap
     * and draws the source through a color-matrix-filtered Paint.
     * 
     * PROCESS:
     * 1. Create a new mutable bitmap of the same size
     * 2. Create a Canvas targeting the new bitmap
     * 3. Create a Paint with the ColorMatrixColorFilter
     * 4. Draw the source bitmap through the Paint
     * 
     * The GPU/hardware may accelerate the color matrix multiplication,
     * making this relatively fast even for large images.
     * 
     * @param bitmap Source bitmap
     * @param colorMatrix The color transformation to apply
     * @return New bitmap with the transformation applied
     */
    private fun applyColorMatrix(bitmap: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        // Create output bitmap with same dimensions and config
        // ARGB_8888 is the standard config for display/manipulation
        val outputBitmap = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ARGB_8888
        )

        // Canvas draws onto the output bitmap
        val canvas = Canvas(outputBitmap)

        // Paint with color filter applies the matrix during drawing
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        // Draw source bitmap through the filter to output
        // (0f, 0f) = draw at origin
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return outputBitmap
    }

    // ============================================================
    // UTILITY FUNCTIONS
    // ============================================================

    /**
     * Save a bitmap to file in JPEG format
     * 
     * Used to persist the filtered image before adding to PDF.
     * 
     * QUALITY TRADE-OFF:
     * - 90 is good balance of quality vs file size for documents
     * - Lower values show JPEG artifacts on text
     * - Higher values give diminishing returns
     * 
     * @param bitmap The bitmap to save
     * @param outputFile Target file
     * @param quality JPEG quality (0-100)
     * @return true if successful, false otherwise
     */
    fun saveBitmapToFile(
        bitmap: Bitmap,
        outputFile: java.io.File,
        quality: Int = 90
    ): Boolean {
        return try {
            java.io.FileOutputStream(outputFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
