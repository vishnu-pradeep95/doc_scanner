/**
 * PreviewFragment.kt - Image Preview and Edit Screen
 * 
 * PURPOSE:
 * After capturing an image, users see it here and can:
 * 1. Retake (go back to camera)
 * 2. Crop/Rotate (edit the image)
 * 3. Add Page (save and continue)
 * 
 * IMAGE CROPPING:
 * We use CanHub/Android-Image-Cropper library (maintained fork of uCrop)
 * It provides a full-screen crop activity with rotation, aspect ratio, etc.
 * 
 * NAVIGATION ARGUMENTS:
 * This Fragment receives the image URI as a navigation argument.
 * The argument is defined in nav_graph.xml and accessed via 'navArgs()'
 */

package com.pdfscanner.app.ui

// Android core imports
import android.graphics.BitmapFactory  // Decode image files
import android.net.Uri  // Universal Resource Identifier for files
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast

// Activity Result API for handling crop result
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider  // Secure file sharing
import androidx.core.net.toUri  // Extension to convert File to Uri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels  // Shared ViewModel
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs  // Safe Args navigation arguments

// CanHub Image Cropper library
import com.canhub.cropper.CropImageContract  // Activity result contract for cropping
import com.canhub.cropper.CropImageContractOptions  // Options to pass to cropper
import com.canhub.cropper.CropImageOptions  // Crop configuration

// Project imports
import com.pdfscanner.app.R
import com.pdfscanner.app.databinding.FragmentPreviewBinding
import com.pdfscanner.app.viewmodel.ScannerViewModel

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
                // Update our current image reference
                currentImageUri = croppedUri
                // Display the cropped image
                loadImage(croppedUri)
            }
        } else {
            // Crop failed - show error
            val error = result.error
            Toast.makeText(
                requireContext(),
                "Crop failed: ${error?.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ============================================================
    // LIFECYCLE
    // ============================================================

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

        /**
         * Get image URI from navigation arguments
         * 
         * args.imageUri is a String (URIs are passed as strings in navigation)
         * Uri.parse() converts string back to Uri object
         */
        currentImageUri = Uri.parse(args.imageUri)

        setupToolbar()
        setupButtons()
        
        // Display the captured image
        loadImage(currentImageUri!!)
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
    }

    /**
     * Setup action button click listeners
     */
    private fun setupButtons() {
        /**
         * RETAKE BUTTON
         * User wants to discard this image and take a new one
         */
        binding.btnRetake.setOnClickListener {
            // Delete the captured file to free storage
            // We don't want to keep images the user doesn't want
            currentImageUri?.path?.let { path ->
                File(path).delete()
            }
            
            // Navigate back to camera
            // action_preview_to_camera pops back to camera (defined in nav_graph.xml)
            findNavController().navigate(R.id.action_preview_to_camera)
        }

        /**
         * CROP/EDIT BUTTON
         * Launch the image cropper for rotation and cropping
         */
        binding.btnCrop.setOnClickListener {
            launchCrop()
        }

        /**
         * ADD PAGE BUTTON
         * Save this image to the pages list and continue
         */
        binding.btnAddPage.setOnClickListener {
            currentImageUri?.let { uri ->
                // Add to ViewModel's page list
                viewModel.addPage(uri)
                
                // Clear the "current capture" since it's now in the list
                viewModel.clearCurrentCapture()

                // Navigate to pages list to see all scanned pages
                findNavController().navigate(R.id.action_preview_to_pages)
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
            /**
             * Step 1: Get image dimensions without loading pixels
             * 
             * inJustDecodeBounds = true means:
             * - Only read image metadata (width, height)
             * - Don't allocate memory for pixels
             * - Much faster than full decode
             */
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true  // Don't load pixels yet
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                // Now options.outWidth and options.outHeight contain dimensions
            }

            /**
             * Step 2: Load image with sampling
             * 
             * inSampleSize = 2 means load 1/4 of pixels (1/2 width × 1/2 height)
             * inSampleSize = 4 means load 1/16 of pixels
             * 
             * This dramatically reduces memory usage for display
             */
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                val options = BitmapFactory.Options().apply {
                    // Calculate appropriate sample size based on target dimensions
                    inSampleSize = calculateInSampleSize(1080, 1920)
                }
                val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                
                // Set the decoded bitmap as the ImageView source
                binding.imagePreview.setImageBitmap(bitmap)
            }
        } catch (e: Exception) {
            // Handle file not found, permission denied, etc.
            Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Calculate inSampleSize for bitmap decoding
     * 
     * This is a simplified version - production apps would calculate
     * based on actual image dimensions vs requested dimensions
     * 
     * @param reqWidth Target width
     * @param reqHeight Target height
     * @return Sample size (1 = full resolution, 2 = half, etc.)
     */
    private fun calculateInSampleSize(reqWidth: Int, reqHeight: Int): Int {
        // For simplicity, return 1 (full resolution)
        // A more sophisticated version would compare actual vs target dimensions
        return 1
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
        // Prevent memory leak by clearing binding reference
        _binding = null
    }
}
