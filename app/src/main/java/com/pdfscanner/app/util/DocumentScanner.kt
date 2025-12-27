/**
 * DocumentScanner.kt - ML Kit Document Scanner Integration
 * 
 * PURPOSE:
 * Provides automatic document detection with edge detection using
 * Google ML Kit's Document Scanner API. This gives users a high-quality
 * scanning experience with automatic crop suggestions.
 * 
 * FEATURES:
 * - Automatic document edge detection
 * - Built-in scanning UI from ML Kit
 * - Manual adjustment of crop corners
 * - Multiple page scanning support
 * - Automatic perspective correction
 * 
 * NOTE:
 * This uses ML Kit's built-in Document Scanner which provides its own
 * camera UI and processing. It's an alternative to our custom CameraX flow.
 */

package com.pdfscanner.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Helper class for ML Kit Document Scanner
 * 
 * USAGE:
 * 1. Create instance in Fragment/Activity
 * 2. Register activity result launcher
 * 3. Call startScanning() to launch scanner
 * 4. Handle results in the callback
 */
object DocumentScanner {

    /**
     * Scanner options configuration
     * 
     * SCANNER_MODE_FULL: Shows all UI including gallery import
     * SCANNER_MODE_BASE: Basic scanning only
     * SCANNER_MODE_BASE_WITH_FILTER: Basic + image filters
     * 
     * PAGE_LIMIT: Max pages user can scan in one session
     */
    private fun createScannerOptions(
        pageLimit: Int = 10,
        enableGalleryImport: Boolean = true
    ): GmsDocumentScannerOptions {
        return GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(enableGalleryImport)
            .setPageLimit(pageLimit)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    /**
     * Create a document scanner instance
     */
    fun createScanner(
        pageLimit: Int = 10,
        enableGalleryImport: Boolean = true
    ): GmsDocumentScanner {
        val options = createScannerOptions(pageLimit, enableGalleryImport)
        return GmsDocumentScanning.getClient(options)
    }

    /**
     * Start the document scanning process
     * 
     * @param activity The activity context
     * @param scanner The scanner instance
     * @param launcher The activity result launcher
     */
    fun startScanning(
        activity: Activity,
        scanner: GmsDocumentScanner,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                launcher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                // Handle failure - scanner not available
                throw e
            }
    }

    /**
     * Parse scanning results from the intent
     * 
     * @param resultCode Activity result code
     * @param data Result intent
     * @return ScanResult containing page URIs and optional PDF
     */
    fun parseResult(resultCode: Int, data: Intent?): ScanResult? {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return null
        }

        val result = GmsDocumentScanningResult.fromActivityResultIntent(data)
        if (result == null) {
            return null
        }

        return ScanResult(
            pageUris = result.pages?.mapNotNull { page ->
                page.imageUri
            } ?: emptyList(),
            pdfUri = result.pdf?.uri
        )
    }

    /**
     * Result from document scanning
     * 
     * @param pageUris List of scanned page image URIs
     * @param pdfUri Optional PDF URI if scanner generated one
     */
    data class ScanResult(
        val pageUris: List<Uri>,
        val pdfUri: Uri?
    )
}
