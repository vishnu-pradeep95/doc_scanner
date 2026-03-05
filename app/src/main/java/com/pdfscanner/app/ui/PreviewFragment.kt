/**
 * PreviewFragment.kt - Image Preview and Edit Screen
 * 
 * PURPOSE:
 * After capturing an image, users see it here and can:
 * 1. Retake (go back to camera)
 * 2. Crop/Rotate (edit the image)
 * 3. Apply filters (Original, Enhanced, B&W)
 * 4. Add Page (save and continue)
 * 
 * IMAGE CROPPING:
 * We use CanHub/Android-Image-Cropper library (maintained fork of uCrop)
 * It provides a full-screen crop activity with rotation, aspect ratio, etc.
 * 
 * IMAGE FILTERS:
 * We apply document-style filters (contrast/brightness adjustments) to
 * improve text legibility. Filters are applied on a background thread
 * and the processed image is saved to disk before adding to the PDF.
 * 
 * NAVIGATION ARGUMENTS:
 * This Fragment receives the image URI as a navigation argument.
 * The argument is defined in nav_graph.xml and accessed via 'navArgs()'
 */

package com.pdfscanner.app.ui

// Android core imports
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory  // Decode image files
import android.net.Uri  // Universal Resource Identifier for files
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.snackbar.Snackbar

// Activity Result API for handling crop result
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider  // Secure file sharing
import androidx.core.net.toUri  // Extension to convert File to Uri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels  // Shared ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs  // Safe Args navigation arguments

// CanHub Image Cropper library
import com.canhub.cropper.CropImageContract  // Activity result contract for cropping
import com.canhub.cropper.CropImageContractOptions  // Options to pass to cropper
import com.canhub.cropper.CropImageOptions  // Crop configuration

// Project imports
import com.pdfscanner.app.R
import com.pdfscanner.app.databinding.FragmentPreviewBinding
import com.pdfscanner.app.util.ImageProcessor
import com.pdfscanner.app.util.InputValidator
import com.pdfscanner.app.util.SecureFileManager
import com.pdfscanner.app.viewmodel.ScannerViewModel

// Coroutines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Java utilities
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * PreviewFragment - Shows captured image with edit options
 */
class PreviewFragment : Fragment() {

    // ============================================================
    // VIEW BINDING
    // ============================================================
    
    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    // ============================================================
    // VIEWMODEL & NAVIGATION ARGS
    // ============================================================
    
    /**
     * Shared ViewModel - same instance as CameraFragment
     */
    private val viewModel: ScannerViewModel by activityViewModels()
    
    /**
     * Navigation arguments
     * 
     * 'navArgs()' is a delegate that lazily parses arguments from the Bundle
     * PreviewFragmentArgs is auto-generated from nav_graph.xml
     * 
     * The argument 'imageUri' was passed when navigating here from CameraFragment
     */
    private val args: PreviewFragmentArgs by navArgs()

    // ============================================================
    // STATE
    // ============================================================
    
    /**
     * Current image URI being displayed
     * 
     * This may change if user crops/edits the image
     * Starts with the captured image URI from navigation args
     */
    private var currentImageUri: Uri? = null
    
    /**
     * Edit index - position of page being edited (from PagesFragment)
     * 
     * -1 means this is a NEW page (from camera capture)
     * >= 0 means we're EDITING an existing page at this index
     */
    private var editIndex: Int = -1

    // ============================================================
    // FILTER STATE
    // ============================================================

    /**
     * Currently selected filter type
     * 
     * Defaults to ORIGINAL (no processing).
     * When user taps a filter button, we:
     * 1. Update this variable
     * 2. Apply the filter in background
     * 3. Update the preview ImageView
     */
    private var currentFilterType: ImageProcessor.FilterType = ImageProcessor.FilterType.ORIGINAL

    /**
     * Original image URI (before any filter is applied)
     * 
     * We keep this reference so we can re-apply filters from the original.
     * When user switches filters, we load from originalImageUri, not the
     * previously filtered image (to avoid quality loss from repeated processing).
     */
    private var originalImageUri: Uri? = null

    /**
     * Cached downsampled bitmap for preview
     * 
     * We load the image once at a reduced resolution for preview.
     * This bitmap is used when applying filters for display.
     * Full-resolution filtering happens only when saving.
     * 
     * MEMORY TRADE-OFF:
     * - Keeping a preview bitmap (~1-2MB) in memory
     * - Avoids re-reading from disk on each filter change
     * - Released in onDestroyView()
     */
    private var previewBitmap: Bitmap? = null

    // ============================================================
    // IMAGE CROPPER
    // ============================================================
    
    /**
     * Crop activity result launcher
     * 
     * CropImageContract is provided by CanHub library
     * It launches the crop activity and returns the result
     * 
     * The lambda handles the result:
     * - isSuccessful: crop completed without error
     * - uriContent: URI of the cropped image
     * - error: exception if something went wrong
     */
    private val cropLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            // Get the URI of the cropped image
            result.uriContent?.let { croppedUri ->
                // CanHub wrote plaintext cropped file; encrypt in-place (SEC-09)
                croppedUri.path?.let { path ->
                    val croppedFile = File(path)
                    if (croppedFile.exists()) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            SecureFileManager.encryptFileInPlace(croppedFile)
                        }
                    }
                }

                // Update our current image reference
                currentImageUri = croppedUri
                originalImageUri = croppedUri  // Cropped image becomes new "original"

                // Reset filter to Original when image changes
                currentFilterType = ImageProcessor.FilterType.ORIGINAL
                binding.filterToggleGroup.check(R.id.btnFilterOriginal)

                // Display the cropped image
                loadImage(croppedUri)
            }
        } else {
            // Crop failed - show error
            val error = result.error
            showSnackbar(getString(R.string.error_crop_failed, error?.message ?: ""))
        }
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val duration = resources.getInteger(R.integer.motion_duration_large).toLong()
        enterTransition = com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, true).apply { this.duration = duration }
        returnTransition = com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, false).apply { this.duration = duration }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Validate path BEFORE any processing (SEC-07)
        if (!InputValidator.isUriPathWithinAppStorage(args.imageUri, requireContext())) {
            showSnackbar("Document not available")
            findNavController().navigateUp()
            return
        }

        /**
         * Get image URI from navigation arguments
         *
         * args.imageUri is a String (URIs are passed as strings in navigation)
         * Uri.parse() converts string back to Uri object
         */
        currentImageUri = Uri.parse(args.imageUri)
        originalImageUri = currentImageUri  // Keep reference to original
        
        /**
         * Get edit index from navigation arguments
         * 
         * -1 = new page (from camera)
         * >= 0 = editing existing page at this index
         */
        editIndex = args.editIndex

        setupToolbar()
        setupButtons()
        setupFilterButtons()

        // Display the captured image
        loadImage(currentImageUri!!)

        // Edge-to-edge inset handling
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = insets.top)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.buttonsLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = insets.bottom)
            windowInsets
        }
    }

    // ============================================================
    // UI SETUP
    // ============================================================

    /**
     * Setup toolbar navigation (back button)
     */
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            // Navigate back to previous screen (camera)
            findNavController().navigateUp()
        }
        
        // Setup toolbar menu (home button)
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_home -> {
                    findNavController().navigate(R.id.action_preview_to_home)
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Setup action button click listeners
     */
    private fun setupButtons() {
        /**
         * RETAKE BUTTON
         * User wants to discard this image and take a new one
         * (Only available for new captures, not when editing existing pages)
         */
        if (editIndex >= 0) {
            // Editing existing page - hide retake, show "Save" instead of "Add Page"
            binding.btnRetake.visibility = View.GONE
            binding.btnAddPage.text = getString(R.string.save_changes)
        } else {
            // New capture - show retake button
            binding.btnRetake.visibility = View.VISIBLE
            binding.btnRetake.setOnClickListener {
                // Securely delete the captured file (SEC-10)
                // We don't want to keep images the user doesn't want
                currentImageUri?.path?.let { path ->
                    SecureFileManager.secureDelete(File(path))
                }
                
                // Navigate back to camera
                // action_preview_to_camera pops back to camera (defined in nav_graph.xml)
                findNavController().navigate(R.id.action_preview_to_camera)
            }
        }

        /**
         * CROP/EDIT BUTTON
         * Launch the image cropper for rotation and cropping
         */
        binding.btnCrop.setOnClickListener {
            launchCrop()
        }

        /**
         * ADD PAGE / SAVE BUTTON
         * Save this image to the pages list and continue
         */
        binding.btnAddPage.setOnClickListener {
            // If a filter is applied, save the processed image before adding
            if (currentFilterType != ImageProcessor.FilterType.ORIGINAL) {
                saveFilteredImageAndAddPage()
            } else {
                // No filter - use original image directly
                addPageAndNavigate(currentImageUri!!)
            }
        }
    }

    /**
     * Add or update page in ViewModel and navigate back
     * 
     * @param uri The URI of the image to add/update (original or processed)
     */
    private fun addPageAndNavigate(uri: Uri) {
        if (editIndex >= 0) {
            // EDITING existing page - update it in place
            viewModel.updatePage(editIndex, uri)
            viewModel.setPageFilter(editIndex, currentFilterType)
            
            // Navigate back to pages list
            findNavController().navigateUp()
        } else {
            // NEW page - add to list
            viewModel.addPage(uri)
            
            // Track which filter was applied to this page
            val pageIndex = viewModel.getPageCount() - 1
            viewModel.setPageFilter(pageIndex, currentFilterType)
            
            // Clear the "current capture" since it's now in the list
            viewModel.clearCurrentCapture()

            // Navigate to pages list to see all scanned pages
            findNavController().navigate(R.id.action_preview_to_pages)
        }
    }

    /**
     * Save the filtered image to disk and then add page
     * 
     * When a filter is applied, we need to persist the processed image
     * before adding it to the pages list. This ensures the PDF uses the
     * filtered version.
     * 
     * PROCESS:
     * 1. Show loading overlay
     * 2. Load full-resolution original image
     * 3. Apply filter
     * 4. Save to processed directory
     * 5. Add the processed URI to pages
     */
    private fun saveFilteredImageAndAddPage() {
        val originalUri = originalImageUri ?: return
        val appContext = context?.applicationContext ?: return  // Capture before launch

        // Show loading
        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val processedUri = withContext(Dispatchers.IO) {
                    // Load full resolution bitmap for final output (uses captured appContext)
                    val fullResBitmap = loadFullResBitmap(appContext, originalUri)
                        ?: throw Exception("Failed to load image")

                    // Apply the selected filter
                    val processedBitmap = ImageProcessor.applyFilter(fullResBitmap, currentFilterType)

                    // Save to processed directory (uses captured appContext)
                    val processedFile = createProcessedFile(appContext)
                    val success = ImageProcessor.saveBitmapToFile(processedBitmap, processedFile)

                    // Clean up bitmaps
                    if (processedBitmap != fullResBitmap) {
                        processedBitmap.recycle()
                    }
                    fullResBitmap.recycle()

                    if (!success) {
                        throw Exception("Failed to save processed image")
                    }

                    processedFile.toUri()
                }

                // Hide loading and add page
                withContext(Dispatchers.Main) {
                    _binding?.loadingOverlay?.visibility = View.GONE
                    if (context != null) {
                        addPageAndNavigate(processedUri)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _binding?.loadingOverlay?.visibility = View.GONE
                    if (_binding == null) return@withContext
                    showSnackbar(getString(R.string.error_processing_image, e.message ?: ""))
                }
            }
        }
    }

    /**
     * Load full resolution bitmap from URI, capped to 2x A4 PDF size to prevent OOM
     * on high-megapixel cameras (e.g., 48MP = 8000x6000 = 192MB uncapped).
     *
     * Max 2380x3368 = 2x A4 at 144dpi — sufficient for PDF output quality.
     *
     * @param ctx Application context (safe to use on IO thread)
     * @param uri URI of the image to decode
     */
    private fun loadFullResBitmap(ctx: Context, uri: Uri): Bitmap? {
        return try {
            decodeCappedBitmap(ctx, uri)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decode a bitmap from URI, capping dimensions to maxWidth x maxHeight
     * using BitmapFactory inSampleSize to avoid OOM on high-resolution cameras.
     *
     * For file:// URIs (app-private encrypted files), uses SecureFileManager
     * to decrypt before decoding. For content:// URIs, uses contentResolver.
     */
    private fun decodeCappedBitmap(
        ctx: Context,
        uri: Uri,
        maxWidth: Int = 2380,
        maxHeight: Int = 3368
    ): Bitmap? {
        // First pass: decode bounds only (no pixel allocation)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openInputStreamForUri(ctx, uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        // Calculate inSampleSize
        options.inSampleSize = calculateDecodeSampleSize(
            options.outWidth, options.outHeight, maxWidth, maxHeight
        )
        options.inJustDecodeBounds = false

        // Second pass: decode with downsampling applied
        return openInputStreamForUri(ctx, uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    /**
     * Open an InputStream for a URI, decrypting if it is a file:// URI
     * pointing to app-private storage (encrypted files).
     * For content:// URIs, delegates to contentResolver.
     */
    private fun openInputStreamForUri(ctx: Context, uri: Uri): java.io.InputStream? {
        return if (uri.scheme == "file") {
            val file = File(uri.path!!)
            if (file.exists()) SecureFileManager.decryptFromFile(file) else null
        } else {
            ctx.contentResolver.openInputStream(uri)
        }
    }

    /**
     * Calculate the largest power-of-2 inSampleSize such that the decoded image
     * remains at or above the target dimensions.
     */
    private fun calculateDecodeSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawWidth > maxWidth || rawHeight > maxHeight) {
            val halfWidth = rawWidth / 2
            val halfHeight = rawHeight / 2
            while ((halfWidth / inSampleSize) >= maxWidth && (halfHeight / inSampleSize) >= maxHeight) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Create a file for the processed/filtered image
     *
     * Saves to filesDir/processed/ directory to keep organized.
     *
     * @param ctx Application context (safe to use on IO thread)
     */
    private fun createProcessedFile(ctx: Context): File {
        val processedDir = File(ctx.filesDir, "processed").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(System.currentTimeMillis())
        return File(processedDir, "PROC_$timestamp.jpg")
    }

    // ============================================================
    // FILTER SETUP
    // ============================================================

    /**
     * Setup filter toggle button listeners
     * 
     * MaterialButtonToggleGroup provides a segmented control look.
     * When a button is selected, we apply the corresponding filter.
     */
    private fun setupFilterButtons() {
        binding.filterToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val filterType = when (checkedId) {
                    R.id.btnFilterOriginal -> ImageProcessor.FilterType.ORIGINAL
                    R.id.btnFilterMagic -> ImageProcessor.FilterType.MAGIC
                    R.id.btnFilterEnhanced -> ImageProcessor.FilterType.ENHANCED
                    R.id.btnFilterSharpen -> ImageProcessor.FilterType.SHARPEN
                    R.id.btnFilterBw -> ImageProcessor.FilterType.DOCUMENT_BW
                    else -> ImageProcessor.FilterType.ORIGINAL
                }
                applyFilterToPreview(filterType)
            }
        }
    }

    /**
     * Apply a filter to the preview image
     * 
     * This applies the filter to the cached preview bitmap (downsampled)
     * for fast UI response. The full-resolution filtering happens when
     * the user clicks "Add Page".
     * 
     * @param filterType The filter to apply
     */
    private fun applyFilterToPreview(filterType: ImageProcessor.FilterType) {
        currentFilterType = filterType

        // For ORIGINAL, just show the cached preview
        if (filterType == ImageProcessor.FilterType.ORIGINAL) {
            previewBitmap?.let { binding.imagePreview.setImageBitmap(it) }
            return
        }

        // For other filters, apply in background
        val bitmap = previewBitmap ?: return

        // Show brief loading indicator
        binding.loadingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            val filteredBitmap = withContext(Dispatchers.IO) {
                // Apply filter to preview bitmap
                // NOTE: We create a copy to avoid modifying the cached original
                ImageProcessor.applyFilter(bitmap, filterType)
            }

            withContext(Dispatchers.Main) {
                _binding?.loadingOverlay?.visibility = View.GONE
                _binding?.imagePreview?.setImageBitmap(filteredBitmap)
            }
        }
    }

    // ============================================================
    // IMAGE LOADING
    // ============================================================

    /**
     * Load and display an image from URI
     * 
     * MEMORY MANAGEMENT:
     * Large images can cause OutOfMemoryError (OOM)
     * We use BitmapFactory.Options to:
     * 1. First get dimensions without loading pixels (inJustDecodeBounds)
     * 2. Calculate appropriate sample size to reduce memory
     * 3. Load downsampled bitmap for display
     * 
     * For display purposes, we don't need full resolution
     * The original file keeps full quality for PDF generation
     * 
     * @param uri File URI to load
     */
    private fun loadImage(uri: Uri) {
        try {
            val ctx = requireContext()
            /**
             * Step 1: Get image dimensions without loading pixels
             *
             * inJustDecodeBounds = true means:
             * - Only read image metadata (width, height)
             * - Don't allocate memory for pixels
             * - Much faster than full decode
             *
             * Uses openInputStreamForUri to decrypt file:// URIs (encrypted files)
             */
            var imageWidth = 0
            var imageHeight = 0
            openInputStreamForUri(ctx, uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true  // Don't load pixels yet
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                imageWidth = options.outWidth
                imageHeight = options.outHeight
            }

            /**
             * Step 2: Load image with sampling
             *
             * inSampleSize = 2 means load 1/4 of pixels (1/2 width × 1/2 height)
             * inSampleSize = 4 means load 1/16 of pixels
             *
             * This dramatically reduces memory usage for display
             */
            openInputStreamForUri(ctx, uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    // Calculate appropriate sample size based on target dimensions
                    inSampleSize = calculateInSampleSize(imageWidth, imageHeight, 1080, 1920)
                }
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)

                // Cache the preview bitmap for filter application
                previewBitmap = bitmap

                // Set the decoded bitmap as the ImageView source
                binding.imagePreview.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            // Handle file not found, permission denied, etc.
            showSnackbar(R.string.error_failed_to_load_image)
        }
    }

    /**
     * Calculate inSampleSize for bitmap decoding
     * 
     * Finds the largest power of 2 that keeps dimensions >= target.
     * This provides a good balance of quality vs memory usage.
     * 
     * @param srcWidth Source image width
     * @param srcHeight Source image height
     * @param reqWidth Target width
     * @param reqHeight Target height
     * @return Sample size (1 = full resolution, 2 = half, etc.)
     */
    private fun calculateInSampleSize(
        srcWidth: Int, 
        srcHeight: Int,
        reqWidth: Int, 
        reqHeight: Int
    ): Int {
        var inSampleSize = 1

        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2

            // Calculate the largest inSampleSize that keeps dimensions >= required
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    // ============================================================
    // IMAGE CROPPING
    // ============================================================

    /**
     * Launch the image cropper activity
     * 
     * PROCESS:
     * 1. Create output file for cropped result
     * 2. Configure crop options (rotation, format, etc.)
     * 3. Launch crop activity
     * 4. Result handled by cropLauncher callback
     */
    private fun launchCrop() {
        currentImageUri?.let { sourceUri ->
            /**
             * Create output file for the cropped image
             * 
             * We save to the same scans directory with a different prefix
             * CROP_ distinguishes edited images from original captures
             */
            val scansDir = File(requireContext().filesDir, "scans").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
                .format(System.currentTimeMillis())
            val outputFile = File(scansDir, "CROP_$timestamp.jpg")

            /**
             * Configure crop options
             * 
             * CropImageContractOptions packages:
             * 1. Source URI - image to crop
             * 2. CropImageOptions - configuration settings
             */
            val cropOptions = CropImageContractOptions(
                uri = sourceUri,  // Image to crop
                cropImageOptions = CropImageOptions().apply {
                    // Enable rotation controls in crop UI
                    allowRotation = true
                    allowFlipping = true
                    
                    // Output format: JPEG with 90% quality
                    // Good balance of quality vs file size for documents
                    outputCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG
                    outputCompressQuality = 90
                    
                    // Save cropped image to our output file
                    customOutputUri = outputFile.toUri()
                    
                    // Show crop guidelines (grid overlay)
                    guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                    
                    // Don't lock aspect ratio - documents come in various sizes
                    fixAspectRatio = false
                    
                    // Toolbar configuration - CRITICAL for showing accept/cancel buttons
                    activityTitle = "Crop & Rotate"
                    
                    // Colors for the crop activity toolbar
                    toolbarColor = android.graphics.Color.parseColor("#1976D2")
                    toolbarTitleColor = android.graphics.Color.WHITE
                    toolbarBackButtonColor = android.graphics.Color.WHITE
                    
                    // Activity menu icons
                    activityMenuIconColor = android.graphics.Color.WHITE
                    
                    // Background color of crop area
                    backgroundColor = android.graphics.Color.parseColor("#212121")
                    
                    // Border and guideline colors
                    borderCornerColor = android.graphics.Color.WHITE
                    borderLineColor = android.graphics.Color.WHITE
                    guidelinesColor = android.graphics.Color.parseColor("#80FFFFFF")
                }
            )

            // Launch the crop activity
            // Result will be handled by cropLauncher callback defined above
            cropLauncher.launch(cropOptions)
        }
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up cached bitmap to prevent memory leak
        previewBitmap?.recycle()
        previewBitmap = null
        // Prevent memory leak by clearing binding reference
        _binding = null
    }
}
