/**
 * InputValidator.kt - Centralized Input Validation Utility
 *
 * PURPOSE:
 * Validates external input (navigation arg file paths and imported content URIs)
 * before use, preventing path traversal attacks and MIME type attacks.
 *
 * SEC-07: All external input must be validated before processing.
 * - Path traversal via crafted `../` nav args could access files outside the app sandbox.
 * - Unvalidated MIME types could lead to processing unexpected content.
 *
 * PATTERN: Object singleton, consistent with ImageUtils, PdfUtils.
 */

package com.pdfscanner.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

object InputValidator {

    /**
     * Check whether an absolute file path resolves to within the app's filesDir.
     *
     * Uses File.canonicalPath to resolve `../` traversals and symlinks, then
     * verifies the canonical path starts with (or equals) filesDir.
     *
     * @param path Raw file path to validate
     * @param context Android context (used to obtain filesDir)
     * @return true if the path is within app-private storage, false otherwise
     */
    fun isPathWithinAppStorage(path: String, context: Context): Boolean {
        if (path.isBlank()) return false

        return try {
            val canonicalPath = File(path).canonicalPath
            val filesDirPath = context.filesDir.canonicalPath

            // Path must either equal filesDir or be a child (start with filesDir + separator)
            canonicalPath == filesDirPath ||
                canonicalPath.startsWith(filesDirPath + File.separator)
        } catch (_: IOException) {
            // canonicalization failed -- treat as invalid
            false
        }
    }

    /**
     * Check whether a URI string points to a safe location.
     *
     * - `file://` URIs: delegates to [isPathWithinAppStorage] after extracting the path.
     * - `content://` URIs: always pass (access is mediated by ContentResolver).
     * - All other schemes (ftp, http, null, empty): rejected.
     *
     * @param uriString The raw URI string from navigation arguments
     * @param context Android context
     * @return true if the URI is safe to use, false otherwise
     */
    fun isUriPathWithinAppStorage(uriString: String, context: Context): Boolean {
        if (uriString.isBlank()) return false

        val uri = Uri.parse(uriString)

        return when (uri.scheme) {
            "file" -> {
                val path = uri.path
                if (path.isNullOrBlank()) false
                else isPathWithinAppStorage(path, context)
            }
            "content" -> true // ContentResolver mediates access
            else -> false     // Unknown or null scheme -- reject
        }
    }

    /**
     * Check whether a content URI has an allowed MIME type for import.
     *
     * Allowed types:
     * - image/[star] (any image subtype)
     * - application/pdf
     *
     * Explicitly rejected:
     * - `application/octet-stream` (banking-app security stance per user decision)
     * - null MIME type (content resolver could not determine type)
     * - All other types
     *
     * @param context Android context (used for ContentResolver)
     * @param uri Content URI to check
     * @return true if the MIME type is in the allowlist, false otherwise
     */
    fun isAllowedMimeType(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri) ?: return false

        // Explicit rejection of octet-stream before the image wildcard check
        if (mimeType == "application/octet-stream") return false

        return mimeType.startsWith("image/") || mimeType == "application/pdf"
    }
}
