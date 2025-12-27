/**
 * DocumentHistory.kt - Data Model and Repository for PDF History
 * 
 * ANDROID CONCEPT: Data Layer
 * ============================
 * The data layer handles persistence - saving data that survives app restarts.
 * 
 * OPTIONS FOR PERSISTENCE:
 * 1. SharedPreferences - Simple key-value pairs (good for settings, small data)
 * 2. Room Database - SQLite wrapper (good for complex/large data)
 * 3. DataStore - Modern replacement for SharedPreferences
 * 4. Files - Raw file storage
 * 
 * We use SharedPreferences + JSON for simplicity since our history
 * is just a list of document entries with basic info.
 */

package com.pdfscanner.app.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Data class representing a saved PDF document
 * 
 * DATA CLASS in Kotlin:
 * - Auto-generates equals(), hashCode(), toString(), copy()
 * - Perfect for holding data without behavior
 * - Similar to a struct in C/C++
 */
data class DocumentEntry(
    val id: String,           // Unique identifier (timestamp-based)
    val name: String,         // User-given name or auto-generated
    val filePath: String,     // Absolute path to PDF file
    val pageCount: Int,       // Number of pages in the PDF
    val createdAt: Long,      // Unix timestamp when created
    val fileSize: Long        // Size in bytes
) {
    /**
     * Get the URI for this document
     */
    fun toUri(): Uri = Uri.fromFile(File(filePath))
    
    /**
     * Check if the PDF file still exists
     */
    fun exists(): Boolean = File(filePath).exists()
    
    /**
     * Get human-readable file size
     */
    fun formattedSize(): String {
        return when {
            fileSize < 1024 -> "$fileSize B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
            else -> String.format("%.1f MB", fileSize / (1024.0 * 1024.0))
        }
    }
    
    /**
     * Convert to JSON for storage
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("filePath", filePath)
            put("pageCount", pageCount)
            put("createdAt", createdAt)
            put("fileSize", fileSize)
        }
    }
    
    companion object {
        /**
         * Create from JSON
         */
        fun fromJson(json: JSONObject): DocumentEntry {
            return DocumentEntry(
                id = json.getString("id"),
                name = json.getString("name"),
                filePath = json.getString("filePath"),
                pageCount = json.getInt("pageCount"),
                createdAt = json.getLong("createdAt"),
                fileSize = json.getLong("fileSize")
            )
        }
    }
}

/**
 * Repository for managing document history
 * 
 * REPOSITORY PATTERN:
 * - Single source of truth for data
 * - Abstracts the data source (SharedPreferences in this case)
 * - Makes it easy to swap implementations (e.g., to Room database)
 */
class DocumentHistoryRepository(context: Context) {
    
    /**
     * SharedPreferences for persistent storage
     * 
     * MODE_PRIVATE = Only this app can access
     */
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, 
        Context.MODE_PRIVATE
    )
    
    /**
     * Get all documents from history, most recent first
     * 
     * @return List of DocumentEntry, filtered to only existing files
     */
    fun getAllDocuments(): List<DocumentEntry> {
        val jsonString = prefs.getString(KEY_DOCUMENTS, "[]") ?: "[]"
        val jsonArray = JSONArray(jsonString)
        
        val documents = mutableListOf<DocumentEntry>()
        for (i in 0 until jsonArray.length()) {
            try {
                val entry = DocumentEntry.fromJson(jsonArray.getJSONObject(i))
                // Only include if file still exists
                if (entry.exists()) {
                    documents.add(entry)
                }
            } catch (e: Exception) {
                // Skip malformed entries
            }
        }
        
        // Sort by creation date, newest first
        return documents.sortedByDescending { it.createdAt }
    }
    
    /**
     * Add a new document to history
     * 
     * @param name User-provided name
     * @param filePath Path to the PDF file
     * @param pageCount Number of pages
     */
    fun addDocument(name: String, filePath: String, pageCount: Int) {
        val file = File(filePath)
        if (!file.exists()) return
        
        val entry = DocumentEntry(
            id = System.currentTimeMillis().toString(),
            name = name,
            filePath = filePath,
            pageCount = pageCount,
            createdAt = System.currentTimeMillis(),
            fileSize = file.length()
        )
        
        // Get existing documents
        val documents = getAllDocuments().toMutableList()
        
        // Add new entry at the beginning
        documents.add(0, entry)
        
        // Limit history size to prevent unbounded growth
        val trimmed = documents.take(MAX_HISTORY_SIZE)
        
        // Save back to preferences
        saveDocuments(trimmed)
    }
    
    /**
     * Remove a document from history
     * 
     * @param id Document ID to remove
     * @param deleteFile If true, also delete the PDF file
     */
    fun removeDocument(id: String, deleteFile: Boolean = false) {
        val documents = getAllDocuments().toMutableList()
        val toRemove = documents.find { it.id == id }
        
        if (toRemove != null) {
            if (deleteFile) {
                File(toRemove.filePath).delete()
            }
            documents.remove(toRemove)
            saveDocuments(documents)
        }
    }
    
    /**
     * Clear all history (optionally delete files too)
     */
    fun clearHistory(deleteFiles: Boolean = false) {
        if (deleteFiles) {
            getAllDocuments().forEach { doc ->
                File(doc.filePath).delete()
            }
        }
        prefs.edit().remove(KEY_DOCUMENTS).apply()
    }
    
    /**
     * Get document by ID
     */
    fun getDocument(id: String): DocumentEntry? {
        return getAllDocuments().find { it.id == id }
    }
    
    /**
     * Save documents list to SharedPreferences
     */
    private fun saveDocuments(documents: List<DocumentEntry>) {
        val jsonArray = JSONArray()
        documents.forEach { doc ->
            jsonArray.put(doc.toJson())
        }
        prefs.edit().putString(KEY_DOCUMENTS, jsonArray.toString()).apply()
    }
    
    companion object {
        private const val PREFS_NAME = "document_history"
        private const val KEY_DOCUMENTS = "documents"
        private const val MAX_HISTORY_SIZE = 50  // Keep last 50 documents
        
        // Singleton instance
        @Volatile
        private var instance: DocumentHistoryRepository? = null
        
        /**
         * Get singleton instance
         * 
         * SINGLETON PATTERN:
         * - Ensures only one instance exists
         * - synchronized block prevents race conditions
         */
        fun getInstance(context: Context): DocumentHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: DocumentHistoryRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
