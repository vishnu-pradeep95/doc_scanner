package com.pdfscanner.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Image utility functions for the scanner app.
 */
object ImageUtils {

    private const val TAG = "ImageUtils"

    /**
     * Correct EXIF orientation for an image URI.
     *
     * Reads EXIF metadata, and if the image needs rotation/flip,
     * creates a corrected copy and returns its URI. If no correction
     * is needed, returns the original URI unchanged.
     *
     * IMPORTANT: Only call this for gallery/file-picker imports.
     * Do NOT call for CameraX captures (CameraX handles orientation internally).
     *
     * @param context Application or activity context
     * @param sourceUri URI of the image to check/correct
     * @return URI of the corrected image, or sourceUri if no correction needed
     */
    fun correctExifOrientation(context: Context, sourceUri: Uri): Uri {
        return try {
            // Read EXIF orientation
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return sourceUri
            val exif = ExifInterface(inputStream)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            // No correction needed for normal/undefined orientation
            if (orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED) {
                return sourceUri
            }

            Log.d(TAG, "EXIF orientation $orientation detected, correcting...")

            // Build rotation/flip matrix
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.preScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(-90f)
                    matrix.preScale(-1f, 1f)
                }
                else -> return sourceUri
            }

            // Decode the bitmap
            val bitmapStream = context.contentResolver.openInputStream(sourceUri) ?: return sourceUri
            val bitmap = BitmapFactory.decodeStream(bitmapStream)
            bitmapStream.close()

            if (bitmap == null) {
                Log.w(TAG, "Failed to decode bitmap for EXIF correction")
                return sourceUri
            }

            // Apply rotation
            val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (corrected != bitmap) {
                bitmap.recycle()
            }

            // Save corrected image to scans directory
            val scansDir = File(context.filesDir, "scans")
            if (!scansDir.exists()) scansDir.mkdirs()
            val outputFile = File(scansDir, "IMPORT_${System.currentTimeMillis()}.jpg")

            SecureFileManager.encryptBitmapToFile(corrected, outputFile, 90)
            corrected.recycle()

            Log.d(TAG, "EXIF corrected image saved: ${outputFile.absolutePath}")
            Uri.fromFile(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "EXIF correction failed, using original", e)
            sourceUri
        }
    }
}
