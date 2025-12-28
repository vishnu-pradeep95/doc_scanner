/**
 * PdfEditorFragment.kt - Main PDF Editor Fragment
 * 
 * Provides the full PDF editing experience with:
 * - PDF viewing using native Android PdfRenderer
 * - Signature drawing and insertion
 * - Text box addition
 * - Stamp placement
 * - Shape drawing
 * - Free-hand drawing
 * - Save annotated PDF
 */

package com.pdfscanner.app.editor

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.pdfscanner.app.R
import com.pdfscanner.app.databinding.FragmentPdfEditorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fragment for editing PDFs with annotations
 */
class PdfEditorFragment : Fragment() {
    
    private var _binding: FragmentPdfEditorBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: PdfEditorViewModel by activityViewModels()
    private val args: PdfEditorFragmentArgs by navArgs()
    
    // Tool icons for highlighting selection
    private lateinit var toolIcons: Map<EditorTool, ImageView>
    
    // Pending annotation position (for dialogs)
    private var pendingAnnotationX: Float = 0f
    private var pendingAnnotationY: Float = 0f
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfEditorBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupToolbar()
        setupToolButtons()
        setupAnnotationCanvas()
        setupPageNavigation()
        setupColorPreview()
        
        loadPdf()
        observeViewModel()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            handleBackPress()
        }
        
        // Setup action bar buttons (new layout)
        binding.btnUndo.setOnClickListener {
            // TODO: Implement undo
            Toast.makeText(context, "Undo coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnRedo.setOnClickListener {
            // TODO: Implement redo
            Toast.makeText(context, "Redo coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnZoomIn.setOnClickListener {
            binding.pdfView.zoomIn()
        }
        
        binding.btnZoomOut.setOnClickListener {
            binding.pdfView.zoomOut()
        }
        
        binding.btnSave.setOnClickListener {
            saveAnnotatedPdf()
        }
    }
    
    private fun setupToolButtons() {
        toolIcons = mapOf(
            EditorTool.SELECT to binding.iconSelect,
            EditorTool.SIGNATURE to binding.iconSignature,
            EditorTool.TEXT to binding.iconText,
            EditorTool.HIGHLIGHT to binding.iconHighlight,
            EditorTool.STAMP to binding.iconStamp,
            EditorTool.DRAW to binding.iconDraw,
            EditorTool.RECTANGLE to binding.iconShapes,
            EditorTool.CHECKMARK to binding.iconCheckmark,
            EditorTool.ERASER to binding.iconEraser
        )
        
        binding.toolSelect.setOnClickListener { selectTool(EditorTool.SELECT) }
        binding.toolSignature.setOnClickListener { selectTool(EditorTool.SIGNATURE) }
        binding.toolText.setOnClickListener { selectTool(EditorTool.TEXT) }
        binding.toolHighlight.setOnClickListener { selectTool(EditorTool.HIGHLIGHT) }
        binding.toolStamp.setOnClickListener { selectTool(EditorTool.STAMP) }
        binding.toolDraw.setOnClickListener { selectTool(EditorTool.DRAW) }
        binding.toolShapes.setOnClickListener { showShapesMenu() }
        binding.toolCheckmark.setOnClickListener { selectTool(EditorTool.CHECKMARK) }
        binding.toolEraser.setOnClickListener { selectTool(EditorTool.ERASER) }
        binding.toolColor.setOnClickListener { showColorPicker() }
        binding.toolFontSize.setOnClickListener { showFontSizePicker() }
        binding.toolStrokeWidth.setOnClickListener { showStrokeWidthPicker() }
        
        // Setup selection toolbar (resize/delete)
        setupSelectionToolbar()
        
        // Default to select tool
        selectTool(EditorTool.SELECT)
    }
    
    private fun setupSelectionToolbar() {
        binding.btnResizeSmaller.setOnClickListener {
            binding.annotationCanvas.resizeSelected(0.85f)
        }
        
        binding.btnResizeLarger.setOnClickListener {
            binding.annotationCanvas.resizeSelected(1.15f)
        }
        
        binding.btnDeleteSelected.setOnClickListener {
            if (binding.annotationCanvas.deleteSelected()) {
                saveCurrentPageAnnotations()
                binding.selectionToolbar.visibility = View.GONE
                Snackbar.make(binding.root, "Annotation deleted", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupAnnotationCanvas() {
        binding.annotationCanvas.onAnnotationSelected = { annotation ->
            // Show/hide selection toolbar based on selection
            binding.selectionToolbar.visibility = if (annotation != null) View.VISIBLE else View.GONE
        }
        
        binding.annotationCanvas.onAnnotationsChanged = {
            saveCurrentPageAnnotations()
        }
        
        binding.annotationCanvas.onTextAnnotationRequested = { x, y ->
            pendingAnnotationX = x
            pendingAnnotationY = y
            showTextInputDialog()
        }
        
        binding.annotationCanvas.onSignatureRequested = { x, y ->
            pendingAnnotationX = x
            pendingAnnotationY = y
            showSignatureDialog()
        }
        
        binding.annotationCanvas.onStampRequested = { x, y ->
            pendingAnnotationX = x
            pendingAnnotationY = y
            showStampPicker()
        }
    }
    
    private fun setupPageNavigation() {
        binding.btnPreviousPage.setOnClickListener {
            val current = viewModel.currentPage.value ?: 0
            if (current > 0) {
                goToPage(current - 1)
            }
        }
        
        binding.btnNextPage.setOnClickListener {
            val current = viewModel.currentPage.value ?: 0
            val total = viewModel.totalPages.value ?: 1
            if (current < total - 1) {
                goToPage(current + 1)
            }
        }
    }
    
    private fun setupColorPreview() {
        updateColorPreview(viewModel.drawColor.value ?: Color.BLACK)
    }
    
    private fun loadPdf() {
        val pdfUriString = args.pdfUri
        android.util.Log.d("PdfEditor", "Loading PDF from URI string: $pdfUriString")
        
        try {
            val pdfUri = Uri.parse(pdfUriString)
            viewModel.loadPdf(pdfUri)
            
            // Check if it's a file:// URI (direct file path)
            if (pdfUri.scheme == "file") {
                val file = File(pdfUri.path ?: "")
                if (file.exists()) {
                    android.util.Log.d("PdfEditor", "Loading from file: ${file.absolutePath}")
                    loadPdfFromFile(file)
                    return
                }
            }
            
            // For content:// URIs (FileProvider), copy to temp file for reliable loading
            android.util.Log.d("PdfEditor", "Loading from content URI...")
            requireContext().contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                // Create temp file
                val tempFile = File(requireContext().cacheDir, "temp_edit_${System.currentTimeMillis()}.pdf")
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                
                android.util.Log.d("PdfEditor", "Copied to temp file: ${tempFile.absolutePath}, size: ${tempFile.length()}")
                
                if (tempFile.exists() && tempFile.length() > 0) {
                    loadPdfFromFile(tempFile)
                } else {
                    Toast.makeText(context, "Failed to load PDF file", Toast.LENGTH_LONG).show()
                }
            } ?: run {
                android.util.Log.e("PdfEditor", "Could not open input stream for URI")
                Toast.makeText(context, "Could not access PDF file", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("PdfEditor", "Exception loading PDF", e)
            Toast.makeText(context, "Error loading PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun loadPdfFromFile(file: File) {
        android.util.Log.d("PdfEditor", "Loading PDF from file: ${file.absolutePath}")
        
        // Set up NativePdfView callbacks
        binding.pdfView.onLoadCompleteListener = { pageCount ->
            viewModel.setTotalPages(pageCount)
            binding.tvPageInfo.text = "1 / $pageCount"
            android.util.Log.d("PdfEditor", "PDF loaded successfully with $pageCount pages")
        }
        
        binding.pdfView.onPageChangedListener = { page, pageCount ->
            // Save annotations from previous page
            saveCurrentPageAnnotations()
            viewModel.setCurrentPage(page)
        }
        
        binding.pdfView.onErrorListener = { exception ->
            android.util.Log.e("PdfEditor", "PDF load error", exception)
            Toast.makeText(context, "Error loading PDF: ${exception.message}", Toast.LENGTH_LONG).show()
        }
        
        // Load the PDF
        binding.pdfView.loadPdf(file)
    }
    
    private fun observeViewModel() {
        viewModel.currentTool.observe(viewLifecycleOwner) { tool ->
            binding.annotationCanvas.currentTool = tool
            updateToolSelection(tool)
        }
        
        viewModel.drawColor.observe(viewLifecycleOwner) { color ->
            binding.annotationCanvas.drawColor = color
            updateColorPreview(color)
        }
        
        viewModel.strokeWidth.observe(viewLifecycleOwner) { width ->
            binding.annotationCanvas.drawStrokeWidth = width
        }
        
        viewModel.textSize.observe(viewLifecycleOwner) { size ->
            binding.annotationCanvas.textSize = size
        }
        
        viewModel.currentPage.observe(viewLifecycleOwner) { page ->
            val total = viewModel.totalPages.value ?: 1
            binding.tvPageInfo.text = "${page + 1} / $total"
            
            // Load annotations for this page
            val annotations = viewModel.getAnnotationsForPage(page)
            binding.annotationCanvas.setAnnotations(annotations)
        }
    }
    
    private fun goToPage(page: Int) {
        saveCurrentPageAnnotations()
        binding.pdfView.showPage(page)
        viewModel.setCurrentPage(page)
    }
    
    private fun saveCurrentPageAnnotations() {
        val page = viewModel.currentPage.value ?: 0
        val annotations = binding.annotationCanvas.getAnnotations()
        viewModel.setAnnotationsForPage(page, annotations)
    }
    
    // Tool selection
    
    private var lastSelectedTool: EditorTool? = null
    
    private fun selectTool(tool: EditorTool) {
        viewModel.setCurrentTool(tool)
    }
    
    private fun updateToolSelection(tool: EditorTool) {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val grayColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        
        toolIcons.forEach { (iconTool, imageView) ->
            val isSelected = iconTool == tool || 
                (tool in listOf(EditorTool.RECTANGLE, EditorTool.CIRCLE, EditorTool.LINE, EditorTool.ARROW) && iconTool == EditorTool.RECTANGLE)
            
            val color = if (isSelected) primaryColor else grayColor
            imageView.setColorFilter(color)
            
            // Animate the selected tool with a bounce effect
            if (isSelected && iconTool != lastSelectedTool) {
                val bounceAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.tool_select_bounce)
                imageView.startAnimation(bounceAnim)
            }
        }
        
        lastSelectedTool = tool
    }
    
    private fun showShapesMenu() {
        val shapes = arrayOf("Rectangle", "Circle", "Line", "Arrow", "Cross")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Shape")
            .setItems(shapes) { _, which ->
                val tool = when (which) {
                    0 -> EditorTool.RECTANGLE
                    1 -> EditorTool.CIRCLE
                    2 -> EditorTool.LINE
                    3 -> EditorTool.ARROW
                    4 -> EditorTool.CROSS
                    else -> EditorTool.RECTANGLE
                }
                selectTool(tool)
            }
            .show()
    }
    
    private fun showColorPicker() {
        val colors = intArrayOf(
            Color.BLACK,
            Color.parseColor("#E74C3C"),
            Color.parseColor("#3498DB"),
            Color.parseColor("#27AE60"),
            Color.parseColor("#8E44AD"),
            Color.parseColor("#F39C12"),
            Color.parseColor("#FFE66D"),
            Color.parseColor("#4ECDC4")
        )
        val colorNames = arrayOf("Black", "Red", "Blue", "Green", "Purple", "Orange", "Yellow", "Teal")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🎨 Select Color")
            .setItems(colorNames) { _, which ->
                viewModel.setDrawColor(colors[which])
            }
            .show()
    }
    
    private fun showFontSizePicker() {
        val sizes = arrayOf("Small (24pt)", "Medium (36pt)", "Large (48pt)", "X-Large (64pt)", "Huge (96pt)")
        val sizeValues = floatArrayOf(24f, 36f, 48f, 64f, 96f)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📏 Select Font Size")
            .setItems(sizes) { _, which ->
                viewModel.setTextSize(sizeValues[which])
                binding.annotationCanvas.textSize = sizeValues[which]
                Snackbar.make(binding.root, "Font size: ${sizes[which]}", Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun showStrokeWidthPicker() {
        val widths = arrayOf("Thin (2pt)", "Normal (4pt)", "Medium (6pt)", "Thick (8pt)", "Extra Thick (12pt)")
        val widthValues = floatArrayOf(2f, 4f, 6f, 8f, 12f)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("✏️ Select Stroke Width")
            .setItems(widths) { _, which ->
                viewModel.setStrokeWidth(widthValues[which])
                binding.annotationCanvas.drawStrokeWidth = widthValues[which]
                Snackbar.make(binding.root, "Stroke: ${widths[which]}", Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun updateColorPreview(color: Int) {
        val drawable = binding.colorPreview.background as? GradientDrawable
        drawable?.setColor(color)
    }
    
    // Dialogs
    
    private fun showSignatureDialog() {
        val dialog = SignatureDialogFragment.newInstance()
        dialog.setOnSignatureSelectedListener { bitmap ->
            binding.annotationCanvas.addSignature(bitmap, pendingAnnotationX, pendingAnnotationY)
            saveCurrentPageAnnotations()
        }
        dialog.show(childFragmentManager, SignatureDialogFragment.TAG)
    }
    
    private fun showTextInputDialog() {
        val dialog = TextInputDialogFragment.newInstance()
        dialog.setOnTextConfirmedListener { text, color, size ->
            binding.annotationCanvas.addText(text, pendingAnnotationX, pendingAnnotationY, color, size)
            saveCurrentPageAnnotations()
        }
        dialog.show(childFragmentManager, TextInputDialogFragment.TAG)
    }
    
    private fun showStampPicker() {
        val dialog = StampPickerDialogFragment.newInstance()
        dialog.setOnStampSelectedListener { stampType ->
            binding.annotationCanvas.addStamp(stampType, pendingAnnotationX, pendingAnnotationY)
            saveCurrentPageAnnotations()
        }
        dialog.show(childFragmentManager, StampPickerDialogFragment.TAG)
    }
    
    // Save PDF
    
    private var savingDialog: androidx.appcompat.app.AlertDialog? = null
    
    private fun saveAnnotatedPdf() {
        if (!viewModel.hasAnnotations()) {
            Snackbar.make(binding.root, "No annotations to save", Snackbar.LENGTH_SHORT).show()
            return
        }
        
        saveCurrentPageAnnotations()
        
        // Show mascot saving dialog
        showSavingDialog()
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val renderer = PdfAnnotationRenderer(requireContext())
                val inputUri = Uri.parse(args.pdfUri)
                val annotations = viewModel.getAllAnnotations()
                
                val outputFile = renderer.renderAnnotatedPdf(inputUri, annotations)
                
                withContext(Dispatchers.Main) {
                    dismissSavingDialog()
                    
                    if (outputFile != null) {
                        viewModel.markChangesSaved()
                        showSuccessDialog(outputFile)
                    } else {
                        showErrorSnackbar("Failed to save PDF")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    dismissSavingDialog()
                    showErrorSnackbar("Error saving PDF: ${e.message}")
                }
            }
        }
    }
    
    private fun showSavingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_saving_progress, null)
        
        // Animate the saving mascot
        val mascotImage = dialogView.findViewById<ImageView>(R.id.imgSavingMascot)
        val bounceAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.mascot_bounce)
        mascotImage.startAnimation(bounceAnim)
        
        // Set random tip
        val tips = listOf(
            "💡 Tip: You can add signatures to any page!",
            "💡 Tip: Tap and hold to move annotations!",
            "💡 Tip: Use stamps for quick approvals!",
            "💡 Tip: Double-tap to zoom the PDF!",
            "💡 Tip: Your signatures are saved for reuse!"
        )
        dialogView.findViewById<android.widget.TextView>(R.id.tvTip).text = tips.random()
        
        savingDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        savingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        savingDialog?.show()
    }
    
    private fun dismissSavingDialog() {
        savingDialog?.dismiss()
        savingDialog = null
    }
    
    private fun showSuccessDialog(outputFile: File) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_save_success, null)
        
        // Animate the success mascot
        val mascotImage = dialogView.findViewById<ImageView>(R.id.imgSuccessMascot)
        val celebrationAnim = AnimationUtils.loadAnimation(requireContext(), R.anim.success_celebration)
        mascotImage.startAnimation(celebrationAnim)
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Setup buttons
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShare).setOnClickListener {
            dialog.dismiss()
            shareFile(outputFile)
        }
        
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDone).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun shareFile(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            
            val shareIntent = android.content.Intent().apply {
                action = android.content.Intent.ACTION_SEND
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                type = "application/pdf"
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(android.content.Intent.createChooser(shareIntent, "Share PDF"))
        } catch (e: Exception) {
            showErrorSnackbar("Error sharing file: ${e.message}")
        }
    }
    
    private fun showErrorSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.error))
            .show()
    }
    
    private fun handleBackPress() {
        if (viewModel.hasChanges.value == true) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Unsaved Changes")
                .setMessage("You have unsaved annotations. Do you want to save before leaving?")
                .setPositiveButton("Save") { _, _ ->
                    saveAnnotatedPdf()
                }
                .setNegativeButton("Discard") { _, _ ->
                    findNavController().navigateUp()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            findNavController().navigateUp()
        }
    }
    
    override fun onDestroyView() {
        // Close the PDF renderer to free resources
        binding.pdfView.close()
        super.onDestroyView()
        _binding = null
    }
}
