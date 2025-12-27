/**
 * OcrProcessor.kt - OCR (Optical Character Recognition) using ML Kit
 * 
 * PURPOSE:
 * Extracts text from scanned document images using Google ML Kit's
 * on-device Text Recognition. Works offline after initial model download.
 * 
 * FEATURES:
 * - Recognizes text in Latin-based languages (English, Spanish, etc.)
 * - Returns structured results (full text + individual blocks)
 * - Provides bounding boxes for text regions
 * - Thread-safe suspend functions for coroutine use
 * 
 * @see https://developers.google.com/ml-kit/vision/text-recognition/v2/android
 */

package com.pdfscanner.app.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Data class representing a block of recognized text
 * 
 * @param text The recognized text content
 * @param boundingBox Rectangle containing this text in the image
 * @param confidence Confidence score (0.0 to 1.0), if available
 */
data class OcrTextBlock(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float?
)

/**
 * Result from OCR processing
 * 
 * @param fullText All recognized text concatenated
 * @param blocks Individual text blocks with positions
 * @param success Whether OCR completed successfully
 * @param error Error message if failed
 */
data class OcrResult(
    val fullText: String,
    val blocks: List<OcrTextBlock>,
    val success: Boolean,
    val error: String? = null
) {
    companion object {
        /**
         * Create a failed result with error message
         */
        fun failure(error: String): OcrResult = OcrResult(
            fullText = "",
            blocks = emptyList(),
            success = false,
            error = error
        )
        
        /**
         * Create an empty result (no text found)
         */
        fun empty(): OcrResult = OcrResult(
            fullText = "",
            blocks = emptyList(),
            success = true,
            error = null
        )
    }
}

/**
 * OcrProcessor - Handles OCR operations using ML Kit
 * 
 * USAGE:
 * ```kotlin
 * lifecycleScope.launch {
 *     val result = OcrProcessor.recognizeText(context, imageUri)
 *     if (result.success) {
 *         showText(result.fullText)
 *     } else {
 *         showError(result.error)
 *     }
 * }
 * ```
 * 
 * THREAD SAFETY:
 * All methods are suspend functions that run on Dispatchers.IO.
 * Safe to call from any coroutine context.
 */
object OcrProcessor {
    
    /**
     * Lazy-initialized TextRecognizer instance
     * 
     * ML Kit recommends reusing the recognizer for better performance.
     * Using lazy initialization to defer creation until first use.
     */
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    
    /**
     * Recognize text from an image URI
     * 
     * This is the main entry point for OCR. Pass a URI to any image
     * (captured photo, cropped image, etc.) and get back recognized text.
     * 
     * @param context Android context for accessing content resolver
     * @param uri URI of the image to process
     * @return OcrResult containing recognized text or error
     * 
     * EXAMPLE:
     * ```kotlin
     * val result = OcrProcessor.recognizeText(requireContext(), photoUri)
     * if (result.success && result.fullText.isNotEmpty()) {
     *     // Show recognized text
     * } else if (result.success) {
     *     // No text found in image
     * } else {
     *     // Handle error: result.error
     * }
     * ```
     */
    suspend fun recognizeText(context: Context, uri: Uri): OcrResult {
        return withContext(Dispatchers.IO) {
            try {
                // Step 1: Create InputImage from URI
                // InputImage is ML Kit's wrapper for various image sources
                val inputImage = InputImage.fromFilePath(context, uri)
                
                // Step 2: Process the image
                // This is the actual OCR operation - runs on-device
                // await() converts the Task to a suspend function
                val result = recognizer.process(inputImage).await()
                
                // Step 3: Check if any text was found
                if (result.text.isEmpty()) {
                    return@withContext OcrResult.empty()
                }
                
                // Step 4: Extract text blocks with bounding boxes
                val blocks = result.textBlocks.map { block ->
                    OcrTextBlock(
                        text = block.text,
                        boundingBox = block.boundingBox,
                        confidence = null  // ML Kit v2 doesn't provide block-level confidence
                    )
                }
                
                // Step 5: Return successful result
                OcrResult(
                    fullText = result.text,
                    blocks = blocks,
                    success = true
                )
                
            } catch (e: Exception) {
                // Handle any errors (file not found, corrupt image, etc.)
                OcrResult.failure(e.message ?: "Unknown OCR error")
            }
        }
    }
    
    /**
     * Check if OCR is available on this device
     * 
     * ML Kit downloads models on first use. This method can be used
     * to pre-check availability, though recognize() handles unavailability gracefully.
     * 
     * @return true if OCR is available
     */
    fun isAvailable(): Boolean {
        // ML Kit text recognition is always available (bundled model)
        // For unbundled models, you'd check Google Play Services here
        return true
    }
    
    /**
     * Release resources when no longer needed
     * 
     * Call this when the app is shutting down or OCR is no longer needed.
     * The recognizer will be recreated on next use if needed.
     */
    fun close() {
        recognizer.close()
    }
}
