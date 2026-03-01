package com.pdfscanner.app.data

import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentEntryTest {

    private fun makeEntry(
        id: String = "entry-123",
        name: String = "My Scan",
        filePath: String = "/data/pdfs/my_scan.pdf",
        pageCount: Int = 3,
        createdAt: Long = 1740000000L,
        fileSize: Long = 512000L
    ) = DocumentEntry(id, name, filePath, pageCount, createdAt, fileSize)

    // ============================================================
    // JSON round-trip tests
    // ============================================================

    @Test
    fun `toJson and fromJson round-trips all 6 fields`() {
        val original = makeEntry()
        val restored = DocumentEntry.fromJson(original.toJson())

        assertThat(restored.id).isEqualTo(original.id)
        assertThat(restored.name).isEqualTo(original.name)
        assertThat(restored.filePath).isEqualTo(original.filePath)
        assertThat(restored.pageCount).isEqualTo(original.pageCount)
        assertThat(restored.createdAt).isEqualTo(original.createdAt)
        assertThat(restored.fileSize).isEqualTo(original.fileSize)
    }

    @Test
    fun `toJson and fromJson preserves special characters in name`() {
        val original = makeEntry(name = "Invoice & Receipt \"Q1\" 2024 O'Brien")
        val restored = DocumentEntry.fromJson(original.toJson())

        assertThat(restored.name).isEqualTo(original.name)
    }

    @Test
    fun `fromJson with missing id field throws JSONException`() {
        val incomplete = JSONObject().apply {
            put("name", "Test")
            // intentionally missing id, filePath, pageCount, createdAt, fileSize
        }
        try {
            DocumentEntry.fromJson(incomplete)
            assert(false) { "Expected JSONException was not thrown" }
        } catch (e: JSONException) {
            // expected — test passes
        }
    }

    // ============================================================
    // formattedSize tests
    // ============================================================

    @Test
    fun `formattedSize returns bytes for values under 1024`() {
        val entry = makeEntry(fileSize = 500L)
        assertThat(entry.formattedSize()).isEqualTo("500 B")
    }

    @Test
    fun `formattedSize returns 1023 B at upper byte boundary`() {
        val entry = makeEntry(fileSize = 1023L)
        assertThat(entry.formattedSize()).isEqualTo("1023 B")
    }

    @Test
    fun `formattedSize returns 1 KB at exactly 1024 bytes`() {
        val entry = makeEntry(fileSize = 1024L)
        assertThat(entry.formattedSize()).isEqualTo("1 KB")
    }

    @Test
    fun `formattedSize returns KB for values in kilobyte range`() {
        val entry = makeEntry(fileSize = 2048L)
        assertThat(entry.formattedSize()).isEqualTo("2 KB")
    }

    @Test
    fun `formattedSize returns MB for values over 1 megabyte`() {
        val entry = makeEntry(fileSize = 1_048_576L)
        assertThat(entry.formattedSize()).isEqualTo("1.0 MB")
    }

    @Test
    fun `formattedSize returns 1-decimal MB for fractional megabytes`() {
        val entry = makeEntry(fileSize = 1_572_864L)
        assertThat(entry.formattedSize()).isEqualTo("1.5 MB")
    }
}
