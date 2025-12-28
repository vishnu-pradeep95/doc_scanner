/**
 * PdfEditorViewModel.kt - ViewModel for PDF Editor
 * 
 * Manages editor state, annotations, and saved signatures.
 */

package com.pdfscanner.app.editor

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * ViewModel for managing PDF editor state and operations
 */
class PdfEditorViewModel(application: Application) : AndroidViewModel(application) {
    
    // PDF file being edited
    private val _pdfUri = MutableLiveData<Uri>()
    val pdfUri: LiveData<Uri> = _pdfUri
    
    // Current page index
    private val _currentPage = MutableLiveData(0)
    val currentPage: LiveData<Int> = _currentPage
    
    // Total pages
    private val _totalPages = MutableLiveData(1)
    val totalPages: LiveData<Int> = _totalPages
    
    // Current tool
    private val _currentTool = MutableLiveData(EditorTool.SELECT)
    val currentTool: LiveData<EditorTool> = _currentTool
    
    // Annotations per page (page index -> list of annotations)
    private val pageAnnotations = mutableMapOf<Int, MutableList<AnnotationCanvasView.AnnotationItem>>()
    
    // Saved signatures
    private val _savedSignatures = MutableLiveData<List<SavedSignature>>(emptyList())
    val savedSignatures: LiveData<List<SavedSignature>> = _savedSignatures
    
    // Drawing properties
    private val _drawColor = MutableLiveData(android.graphics.Color.BLACK)
    val drawColor: LiveData<Int> = _drawColor
    
    private val _strokeWidth = MutableLiveData(4f)
    val strokeWidth: LiveData<Float> = _strokeWidth
    
    private val _textSize = MutableLiveData(48f)
    val textSize: LiveData<Float> = _textSize
    
    // Has unsaved changes
    private val _hasChanges = MutableLiveData(false)
    val hasChanges: LiveData<Boolean> = _hasChanges
    
    // Loading state
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    // Directories
    private val signaturesDir: File by lazy {
        File(getApplication<Application>().filesDir, "signatures").apply { mkdirs() }
    }
    
    init {
        loadSavedSignatures()
    }
    
    /**
     * Data class for saved signatures
     */
    data class SavedSignature(
        val id: String,
        val name: String,
        val filePath: String,
        val createdAt: Long
    )
    
    fun loadPdf(uri: Uri) {
        _pdfUri.value = uri
        _currentPage.value = 0
    }
    
    fun setTotalPages(count: Int) {
        _totalPages.value = count
    }
    
    fun setCurrentPage(page: Int) {
        _currentPage.value = page
    }
    
    fun setCurrentTool(tool: EditorTool) {
        _currentTool.value = tool
    }
    
    fun setDrawColor(color: Int) {
        _drawColor.value = color
    }
    
    fun setStrokeWidth(width: Float) {
        _strokeWidth.value = width
    }
    
    fun setTextSize(size: Float) {
        _textSize.value = size
    }
    
    fun getAnnotationsForPage(page: Int): List<AnnotationCanvasView.AnnotationItem> {
        return pageAnnotations[page]?.toList() ?: emptyList()
    }
    
    fun setAnnotationsForPage(page: Int, annotations: List<AnnotationCanvasView.AnnotationItem>) {
        pageAnnotations[page] = annotations.toMutableList()
        _hasChanges.value = true
    }
    
    fun clearAnnotationsForPage(page: Int) {
        pageAnnotations.remove(page)
        _hasChanges.value = pageAnnotations.isNotEmpty()
    }
    
    fun getAllAnnotations(): Map<Int, List<AnnotationCanvasView.AnnotationItem>> {
        return pageAnnotations.mapValues { it.value.toList() }
    }
    
    fun hasAnnotations(): Boolean = pageAnnotations.values.any { it.isNotEmpty() }
    
    /**
     * Save a signature for reuse
     */
    fun saveSignature(bitmap: Bitmap, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val id = "sig_${System.currentTimeMillis()}"
                val file = File(signaturesDir, "$id.png")
                
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                
                // Save metadata
                val savedSignature = SavedSignature(
                    id = id,
                    name = name,
                    filePath = file.absolutePath,
                    createdAt = System.currentTimeMillis()
                )
                
                saveSignatureMetadata(savedSignature)
                loadSavedSignatures()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Delete a saved signature
     */
    fun deleteSignature(signature: SavedSignature) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(signature.filePath).delete()
                removeSignatureMetadata(signature.id)
                loadSavedSignatures()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Load a saved signature as bitmap
     */
    suspend fun loadSignatureBitmap(signature: SavedSignature): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeFile(signature.filePath)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private fun loadSavedSignatures() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val metadataFile = File(signaturesDir, "signatures.json")
                if (metadataFile.exists()) {
                    val json = metadataFile.readText()
                    val jsonArray = JSONArray(json)
                    
                    val signatures = mutableListOf<SavedSignature>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val filePath = obj.getString("filePath")
                        
                        // Only include if file exists
                        if (File(filePath).exists()) {
                            signatures.add(SavedSignature(
                                id = obj.getString("id"),
                                name = obj.getString("name"),
                                filePath = filePath,
                                createdAt = obj.getLong("createdAt")
                            ))
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        _savedSignatures.value = signatures.sortedByDescending { it.createdAt }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun saveSignatureMetadata(signature: SavedSignature) {
        try {
            val metadataFile = File(signaturesDir, "signatures.json")
            val existingArray = if (metadataFile.exists()) {
                JSONArray(metadataFile.readText())
            } else {
                JSONArray()
            }
            
            val newObj = JSONObject().apply {
                put("id", signature.id)
                put("name", signature.name)
                put("filePath", signature.filePath)
                put("createdAt", signature.createdAt)
            }
            
            existingArray.put(newObj)
            metadataFile.writeText(existingArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun removeSignatureMetadata(signatureId: String) {
        try {
            val metadataFile = File(signaturesDir, "signatures.json")
            if (metadataFile.exists()) {
                val jsonArray = JSONArray(metadataFile.readText())
                val newArray = JSONArray()
                
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    if (obj.getString("id") != signatureId) {
                        newArray.put(obj)
                    }
                }
                
                metadataFile.writeText(newArray.toString())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun markChangesSaved() {
        _hasChanges.value = false
    }
}
