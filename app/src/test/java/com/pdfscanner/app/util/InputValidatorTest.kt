package com.pdfscanner.app.util

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InputValidatorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ============================================================
    // isPathWithinAppStorage
    // ============================================================

    @Test
    fun `isPathWithinAppStorage returns true for path within filesDir`() {
        val filesDir = context.filesDir.canonicalPath
        val path = "$filesDir/pdfs/doc.pdf"
        java.io.File("$filesDir/pdfs").mkdirs()
        java.io.File(path).createNewFile()
        assertThat(InputValidator.isPathWithinAppStorage(path, context)).isTrue()
    }

    @Test
    fun `isPathWithinAppStorage returns false for path traversal attack`() {
        val filesDir = context.filesDir.canonicalPath
        val path = "$filesDir/../../../etc/passwd"
        assertThat(InputValidator.isPathWithinAppStorage(path, context)).isFalse()
    }

    @Test
    fun `isPathWithinAppStorage returns false for path outside app storage`() {
        val path = "/sdcard/Downloads/evil.pdf"
        assertThat(InputValidator.isPathWithinAppStorage(path, context)).isFalse()
    }

    @Test
    fun `isPathWithinAppStorage returns false for empty path`() {
        assertThat(InputValidator.isPathWithinAppStorage("", context)).isFalse()
    }

    @Test
    fun `isPathWithinAppStorage returns false for blank path`() {
        assertThat(InputValidator.isPathWithinAppStorage("   ", context)).isFalse()
    }

    @Test
    fun `isPathWithinAppStorage returns true for filesDir itself`() {
        val filesDir = context.filesDir.canonicalPath
        assertThat(InputValidator.isPathWithinAppStorage(filesDir, context)).isTrue()
    }

    // ============================================================
    // isUriPathWithinAppStorage
    // ============================================================

    @Test
    fun `isUriPathWithinAppStorage returns true for file URI within filesDir`() {
        val filesDir = context.filesDir.canonicalPath
        java.io.File("$filesDir/pdfs").mkdirs()
        java.io.File("$filesDir/pdfs/doc.pdf").createNewFile()
        val uriString = "file://$filesDir/pdfs/doc.pdf"
        assertThat(InputValidator.isUriPathWithinAppStorage(uriString, context)).isTrue()
    }

    @Test
    fun `isUriPathWithinAppStorage returns false for file URI outside filesDir`() {
        val uriString = "file:///etc/passwd"
        assertThat(InputValidator.isUriPathWithinAppStorage(uriString, context)).isFalse()
    }

    @Test
    fun `isUriPathWithinAppStorage returns true for content URI`() {
        val uriString = "content://com.pdfscanner.app.fileprovider/pdfs/doc.pdf"
        assertThat(InputValidator.isUriPathWithinAppStorage(uriString, context)).isTrue()
    }

    @Test
    fun `isUriPathWithinAppStorage returns false for empty URI string`() {
        assertThat(InputValidator.isUriPathWithinAppStorage("", context)).isFalse()
    }

    @Test
    fun `isUriPathWithinAppStorage returns false for unknown scheme`() {
        val uriString = "ftp://example.com/file.pdf"
        assertThat(InputValidator.isUriPathWithinAppStorage(uriString, context)).isFalse()
    }

    // ============================================================
    // isAllowedMimeType
    // ============================================================

    @Test
    fun `isAllowedMimeType returns true for image jpeg`() {
        val uri = Uri.parse("content://mime_jpeg/image.jpg")
        registerMimeProvider("mime_jpeg", "image/jpeg")
        assertThat(InputValidator.isAllowedMimeType(context, uri)).isTrue()
    }

    @Test
    fun `isAllowedMimeType returns true for image png`() {
        val uri = Uri.parse("content://mime_png/image.png")
        registerMimeProvider("mime_png", "image/png")
        assertThat(InputValidator.isAllowedMimeType(context, uri)).isTrue()
    }

    @Test
    fun `isAllowedMimeType returns true for application pdf`() {
        val uri = Uri.parse("content://mime_pdf/doc.pdf")
        registerMimeProvider("mime_pdf", "application/pdf")
        assertThat(InputValidator.isAllowedMimeType(context, uri)).isTrue()
    }

    @Test
    fun `isAllowedMimeType returns false for application octet-stream`() {
        val uri = Uri.parse("content://mime_octet/binary.bin")
        registerMimeProvider("mime_octet", "application/octet-stream")
        assertThat(InputValidator.isAllowedMimeType(context, uri)).isFalse()
    }

    @Test
    fun `isAllowedMimeType returns false for null MIME type`() {
        // Use a URI with no registered provider -- getType() returns null
        val uri = Uri.parse("content://unregistered_authority/unknown")
        assertThat(InputValidator.isAllowedMimeType(context, uri)).isFalse()
    }

    @Test
    fun `isAllowedMimeType returns false for text plain`() {
        val uri = Uri.parse("content://mime_text/doc.txt")
        registerMimeProvider("mime_text", "text/plain")
        assertThat(InputValidator.isAllowedMimeType(context, uri)).isFalse()
    }

    // ============================================================
    // Helper
    // ============================================================

    /**
     * Register a ContentProvider that returns the given MIME type for all URIs.
     * Uses Robolectric's buildContentProvider API to wire it into the test context.
     */
    private fun registerMimeProvider(authority: String, mimeType: String) {
        val providerInfo = ProviderInfo().apply {
            this.authority = authority
        }
        Robolectric.buildContentProvider(MimeTypeProvider::class.java)
            .create(providerInfo)
            .get()
            .setMimeType(mimeType)
    }

    /**
     * Minimal ContentProvider that returns a configurable MIME type.
     */
    class MimeTypeProvider : ContentProvider() {
        private var mimeType: String = ""

        fun setMimeType(type: String) {
            mimeType = type
        }

        override fun onCreate() = true
        override fun query(uri: Uri, p: Array<out String>?, s: String?, sa: Array<out String>?, so: String?): Cursor? = null
        override fun getType(uri: Uri): String = mimeType
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, s: String?, sa: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, s: String?, sa: Array<out String>?) = 0
    }
}
