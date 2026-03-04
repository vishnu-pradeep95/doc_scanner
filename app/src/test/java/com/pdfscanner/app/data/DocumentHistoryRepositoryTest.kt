package com.pdfscanner.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.pdfscanner.app.util.SecurePreferences
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentHistoryRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var repository: DocumentHistoryRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Reset SecurePreferences singleton so each test gets a fresh instance
        SecurePreferences.resetForTesting()
        // Clear the fallback prefs (Robolectric has no real KeyStore, so fallback is used)
        context.getSharedPreferences("secure_prefs_fallback", Context.MODE_PRIVATE)
            .edit().clear().commit()
        // Construct directly — do not use getInstance() singleton (it caches old context)
        repository = DocumentHistoryRepository(context)
    }

    @After
    fun teardown() {
        // tempFolder @Rule handles temp file cleanup automatically
    }

    /** Helper: creates a real temp file that satisfies file.exists() checks */
    private fun makeTempPdf(name: String = "test.pdf"): File =
        tempFolder.newFile(name).also { it.writeText("fake pdf content") }

    // ─────────────────────────────────────────────────────────────────────────
    // Empty state
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getAllDocuments with no entries returns empty list`() {
        val documents = repository.getAllDocuments()
        assertThat(documents).isEmpty()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Add and retrieve
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `addDocument with existing file - getAllDocuments returns 1 entry with correct fields`() {
        val pdf = makeTempPdf("doc1.pdf")
        repository.addDocument("My Doc", pdf.absolutePath, 5)

        val documents = repository.getAllDocuments()

        assertThat(documents).hasSize(1)
        assertThat(documents[0].name).isEqualTo("My Doc")
        assertThat(documents[0].pageCount).isEqualTo(5)
        assertThat(documents[0].filePath).isEqualTo(pdf.absolutePath)
    }

    @Test
    fun `addDocument twice - getAllDocuments returns 2 entries newest first`() {
        val pdf1 = makeTempPdf("first.pdf")
        repository.addDocument("First", pdf1.absolutePath, 1)

        // Small sleep to ensure different createdAt timestamps
        Thread.sleep(5)

        val pdf2 = makeTempPdf("second.pdf")
        repository.addDocument("Second", pdf2.absolutePath, 2)

        val documents = repository.getAllDocuments()

        assertThat(documents).hasSize(2)
        // Newest first — "Second" was added last so has a larger createdAt
        assertThat(documents[0].name).isEqualTo("Second")
        assertThat(documents[1].name).isEqualTo("First")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File existence filtering
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `addDocument with non-existent file path - silently not added, getAllDocuments returns empty`() {
        repository.addDocument("Ghost", "/tmp/does_not_exist_ever.pdf", 3)

        val documents = repository.getAllDocuments()
        assertThat(documents).isEmpty()
    }

    @Test
    fun `add valid entry then delete backing file - getAllDocuments returns empty`() {
        val pdf = makeTempPdf("temp.pdf")
        repository.addDocument("Will Delete", pdf.absolutePath, 2)

        // Verify it was added
        assertThat(repository.getAllDocuments()).hasSize(1)

        // Delete the backing file
        pdf.delete()
        assertThat(pdf.exists()).isFalse()

        // getAllDocuments should filter it out
        val documents = repository.getAllDocuments()
        assertThat(documents).isEmpty()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Remove
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `removeDocument by id - entry no longer appears in getAllDocuments`() {
        val pdf = makeTempPdf("to_remove.pdf")
        repository.addDocument("Remove Me", pdf.absolutePath, 4)

        val id = repository.getAllDocuments()[0].id
        repository.removeDocument(id)

        val documents = repository.getAllDocuments()
        assertThat(documents).isEmpty()
    }

    @Test
    fun `removeDocument with deleteFile=true - backing PDF file is deleted from disk`() {
        val pdf = makeTempPdf("delete_me.pdf")
        assertThat(pdf.exists()).isTrue()

        repository.addDocument("Delete File", pdf.absolutePath, 1)
        val id = repository.getAllDocuments()[0].id

        repository.removeDocument(id, deleteFile = true)

        // File should be deleted from disk
        assertThat(pdf.exists()).isFalse()
        // Entry should also be gone from history
        assertThat(repository.getAllDocuments()).isEmpty()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clear
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `clearHistory - getAllDocuments returns empty list after clearing`() {
        val pdf1 = makeTempPdf("clear1.pdf")
        val pdf2 = makeTempPdf("clear2.pdf")
        repository.addDocument("Doc A", pdf1.absolutePath, 1)
        repository.addDocument("Doc B", pdf2.absolutePath, 2)

        assertThat(repository.getAllDocuments()).hasSize(2)

        repository.clearHistory()

        assertThat(repository.getAllDocuments()).isEmpty()
    }

    @Test
    fun `clearHistory with deleteFiles=true - all PDF files are deleted from disk`() {
        val pdf1 = makeTempPdf("del_all1.pdf")
        val pdf2 = makeTempPdf("del_all2.pdf")
        repository.addDocument("Doc X", pdf1.absolutePath, 1)
        repository.addDocument("Doc Y", pdf2.absolutePath, 1)

        assertThat(repository.getAllDocuments()).hasSize(2)

        repository.clearHistory(deleteFiles = true)

        // All backing files should be gone
        assertThat(pdf1.exists()).isFalse()
        assertThat(pdf2.exists()).isFalse()
        assertThat(repository.getAllDocuments()).isEmpty()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GetById
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `getDocument with valid id - returns the correct entry`() {
        val pdf = makeTempPdf("getbyid.pdf")
        repository.addDocument("Find Me", pdf.absolutePath, 7)

        val id = repository.getAllDocuments()[0].id
        val entry = repository.getDocument(id)

        assertThat(entry).isNotNull()
        assertThat(entry!!.name).isEqualTo("Find Me")
        assertThat(entry.pageCount).isEqualTo(7)
    }

    @Test
    fun `getDocument with unknown id - returns null`() {
        val pdf = makeTempPdf("exists.pdf")
        repository.addDocument("Real Doc", pdf.absolutePath, 3)

        val entry = repository.getDocument("non-existent-id-12345")
        assertThat(entry).isNull()
    }
}
