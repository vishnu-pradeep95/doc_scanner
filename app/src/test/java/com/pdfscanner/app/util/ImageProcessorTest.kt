package com.pdfscanner.app.util

import android.graphics.Bitmap
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageProcessorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun bitmap(w: Int = 100, h: Int = 100): Bitmap =
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

    // ============================================================
    // Filter output correctness (100x100 ARGB_8888 input)
    // ============================================================

    @Test
    fun `applyFilter ORIGINAL returns same bitmap reference`() {
        val input = bitmap()
        val result = ImageProcessor.applyFilter(input, ImageProcessor.FilterType.ORIGINAL)
        assertThat(result).isSameInstanceAs(input)
    }

    @Test
    fun `applyFilter ENHANCED returns new bitmap with same dimensions`() {
        val input = bitmap()
        val result = ImageProcessor.applyFilter(input, ImageProcessor.FilterType.ENHANCED)
        assertThat(result).isNotSameInstanceAs(input)
        assertThat(result.width).isEqualTo(100)
        assertThat(result.height).isEqualTo(100)
    }

    @Test
    fun `applyFilter DOCUMENT_BW returns non-null bitmap`() {
        val input = bitmap()
        val result = ImageProcessor.applyFilter(input, ImageProcessor.FilterType.DOCUMENT_BW)
        assertThat(result).isNotNull()
        assertThat(result).isNotSameInstanceAs(input)
    }

    @Test
    fun `applyFilter MAGIC returns non-null bitmap`() {
        val input = bitmap()
        val result = ImageProcessor.applyFilter(input, ImageProcessor.FilterType.MAGIC)
        assertThat(result).isNotNull()
    }

    @Test
    fun `applyFilter SHARPEN preserves input dimensions`() {
        val input = bitmap(200, 150)
        val result = ImageProcessor.applyFilter(input, ImageProcessor.FilterType.SHARPEN)
        assertThat(result.width).isEqualTo(200)
        assertThat(result.height).isEqualTo(150)
    }

    // ============================================================
    // Dimension capping (maxDim = 3368)
    // ============================================================

    @Test
    fun `applyMagicFilter caps oversized bitmap width and height to 3368`() {
        val oversized = bitmap(4000, 4000)
        val result = ImageProcessor.applyMagicFilter(oversized)
        assertThat(result.width).isAtMost(3368)
        assertThat(result.height).isAtMost(3368)
    }

    @Test
    fun `applySharpen caps oversized bitmap width and height to 3368`() {
        val oversized = bitmap(4000, 4000)
        val result = ImageProcessor.applySharpen(oversized)
        assertThat(result.width).isAtMost(3368)
        assertThat(result.height).isAtMost(3368)
    }

    // ============================================================
    // Edge case: 1x1 bitmap
    // ============================================================

    @Test
    fun `applyFilter on 1x1 bitmap does not throw for any filter type`() {
        val tiny = bitmap(1, 1)
        for (filter in ImageProcessor.FilterType.values()) {
            // Must not throw
            val result = ImageProcessor.applyFilter(tiny, filter)
            assertThat(result).isNotNull()
        }
    }

    // ============================================================
    // saveBitmapToFile
    // ============================================================

    @Test
    fun `saveBitmapToFile writes non-empty file and returns true`() {
        val input = bitmap()
        val outputFile = tempFolder.newFile("output.jpg")
        val success = ImageProcessor.saveBitmapToFile(input, outputFile)
        assertThat(success).isTrue()
        assertThat(outputFile.exists()).isTrue()
        assertThat(outputFile.length()).isGreaterThan(0L)
    }
}
