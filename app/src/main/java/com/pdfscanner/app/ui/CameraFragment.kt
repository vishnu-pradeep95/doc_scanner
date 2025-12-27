/**
 * CameraFragment.kt - Camera Capture Screen
 * 
 * ANDROID CONCEPT: Fragment
 * =========================
 * A Fragment is a reusable UI component - think of it as a "sub-Activity"
 * or a modular screen that can be swapped in/out of an Activity.
 * 
 * WHY FRAGMENTS?
 * - Modular: Each screen is self-contained
 * - Reusable: Same Fragment can appear in different Activities
 * - Lifecycle-aware: Has its own lifecycle tied to the parent Activity
 * 
 * FRAGMENT LIFECYCLE (simplified):
 * onCreateView()   → Create/inflate the UI layout
 * onViewCreated()  → Setup listeners, initialize components
 * onDestroyView()  → Cleanup to prevent memory leaks
 * 
 * CAMERAX LIBRARY
 * ===============
 * CameraX is Google's modern camera library that:
 * - Handles device-specific quirks automatically
 * - Provides simple "use case" API (Preview, ImageCapture, etc.)
 * - Is lifecycle-aware (automatically starts/stops with Fragment)
 */

package com.pdfscanner.app.ui

// Android core imports
import android.app.Activity
import android.Manifest  // Permission constants
import android.content.pm.PackageManager  // Permission check results
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater  // Converts XML to View objects
import android.view.View
import android.view.ViewGroup  // Base class for UI containers
import android.widget.Toast  // Simple popup messages

// Activity Result API - modern way to handle permission requests and activity results
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

// CameraX imports - Google's camera library
import androidx.camera.core.CameraSelector  // Choose front/back camera
import androidx.camera.core.ImageCapture  // Capture still images
import androidx.camera.core.ImageCaptureException  // Capture error handling
import androidx.camera.core.Preview  // Live camera preview
import androidx.camera.lifecycle.ProcessCameraProvider  // Manages camera lifecycle

// AndroidX utilities
import androidx.core.content.ContextCompat  // Permission checking helper
import androidx.core.net.toUri  // Extension function: File.toUri()
import androidx.fragment.app.Fragment  // Base Fragment class
import androidx.fragment.app.activityViewModels  // Shared ViewModel injection
import androidx.navigation.fragment.findNavController  // Navigation helper

// ML Kit Document Scanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner

// Project-specific imports
import com.pdfscanner.app.R  // Generated resource references (R.id.xxx, R.string.xxx)
import com.pdfscanner.app.databinding.FragmentCameraBinding  // View Binding for fragment_camera.xml
import com.pdfscanner.app.util.DocumentScanner
import com.pdfscanner.app.viewmodel.ScannerViewModel

// Java utilities
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService  // Thread pool for background work
import java.util.concurrent.Executors

/**
 * CameraFragment - Handles camera preview and image capture
 * 
 * RESPONSIBILITIES:
 * 1. Request camera permission
 * 2. Display live camera preview
 * 3. Capture image when button pressed
 * 4. Save image to app storage
 * 5. Navigate to preview screen (normal mode) or continue capturing (batch mode)
 */
class CameraFragment : Fragment() {

    // ============================================================
    // VIEW BINDING
    // ============================================================
    
    /**
     * Nullable binding reference
     * 
     * WHY NULLABLE?
     * The binding is only valid between onCreateView and onDestroyView.
     * Before and after, it should be null to prevent memory leaks.
     * 
     * '_' prefix = private backing field convention in Kotlin
     */
    private var _binding: FragmentCameraBinding? = null
    
    /**
     * Non-null binding accessor
     * 
     * 'get()' makes this a computed property (like a getter in C++)
     * '!!' asserts the value is not null (throws NPE if it is)
     * 
     * USE: binding.btnCapture instead of _binding!!.btnCapture
     */
    private val binding get() = _binding!!

    // ============================================================
    // VIEWMODEL
    // ============================================================
    
    /**
     * Shared ViewModel instance
     * 
     * 'by activityViewModels()' means:
     * - Lazy initialization (created on first access)
     * - Scoped to the Activity (shared across all Fragments)
     * 
     * Compare to 'by viewModels()' which would be Fragment-scoped
     */
    private val viewModel: ScannerViewModel by activityViewModels()

    // ============================================================
    // CAMERAX COMPONENTS
    // ============================================================
    
    /**
     * ImageCapture use case - handles still image capture
     * 
     * Nullable because it's initialized after camera starts
     */
    private var imageCapture: ImageCapture? = null
    
    /**
     * Background executor for camera operations
     * 
     * Camera operations should not block the main thread (UI would freeze)
     * This executor provides a background thread pool
     */
    private lateinit var cameraExecutor: ExecutorService

    // ============================================================
    // BATCH MODE STATE
    // ============================================================
    
    /**
     * Whether batch scanning mode is active
     * 
     * In batch mode:
     * - Captured images are added directly to pages list
     * - No navigation to preview after each capture
     * - Shows counter of captured pages
     * - User can tap "Done" when finished
     */
    private var isBatchMode = false
    
    /**
     * Counter for pages captured in current batch session
     * Resets when batch mode is disabled
     */
    private var batchCaptureCount = 0

    // ============================================================
    // ML KIT DOCUMENT SCANNER
    // ============================================================
    
    /**
     * Document scanner instance from ML Kit
     * Provides automatic edge detection and perspective correction
     */
    private lateinit var documentScanner: GmsDocumentScanner
    
    /**
     * Activity result launcher for document scanner
     * 
     * The ML Kit Document Scanner launches its own activity with camera UI,
     * so we need to handle the result when scanning is complete.
     */
    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result: ActivityResult ->
        handleScannerResult(result)
    }

    // ============================================================
    // PERMISSION HANDLING
    // ============================================================
    
    /**
     * Modern permission request handler
     * 
     * OLD WAY (deprecated):
     * requestPermissions(arrayOf(CAMERA), REQUEST_CODE)
     * override fun onRequestPermissionsResult(...)
     * 
     * NEW WAY (Activity Result API):
     * - Register a callback when Fragment is created
     * - Launch it when permission is needed
     * - Callback receives boolean result
     * 
     * registerForActivityResult() is called during Fragment initialization,
     * not inside a method, because Android requires it before STARTED state.
     */
    private val requestPermissionLauncher = registerForActivityResult(
        // Contract defines what we're requesting (a permission)
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // This lambda runs when user responds to permission dialog
        if (isGranted) {
            // User granted permission - start camera
            startCamera()
        } else {
            // User denied - show explanation
            showPermissionDenied()
        }
    }

    // ============================================================
    // LIFECYCLE METHODS
    // ============================================================

    /**
     * onCreateView - Inflate the UI layout
     * 
     * "Inflation" = Converting XML layout to actual View objects in memory
     * 
     * @param inflater System service that does the XML parsing
     * @param container Parent ViewGroup this Fragment will be added to
     * @param savedInstanceState Previous state (null if fresh)
     * @return The root View of this Fragment's layout
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout using View Binding
        // FragmentCameraBinding.inflate() is auto-generated from fragment_camera.xml
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        
        // Return the root view of the binding (the ConstraintLayout)
        return binding.root
    }

    /**
     * onViewCreated - Setup UI after inflation
     * 
     * This is where you:
     * - Set click listeners
     * - Initialize components
     * - Start observing LiveData
     * 
     * @param view The View returned from onCreateView
     * @param savedInstanceState Previous state
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Create a single-thread executor for camera operations
        // newSingleThreadExecutor() = sequential execution, no race conditions
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize ML Kit Document Scanner
        // pageLimit=10 allows scanning up to 10 pages per session
        documentScanner = DocumentScanner.createScanner(
            pageLimit = 10,
            enableGalleryImport = true
        )

        // Setup UI components
        setupUI()
        
        // Check/request camera permission
        checkCameraPermission()
        
        // Start observing ViewModel data
        observeViewModel()
    }

    /**
     * Setup button click listeners
    */
    private fun setupUI() {
        // Capture button - takes a photo
        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        // View pages button - navigate to pages list
        binding.btnViewPages.setOnClickListener {
            // Exit batch mode when navigating to pages
            if (isBatchMode) {
                exitBatchMode()
            }
            // findNavController() gets the NavController from the NavHostFragment
            // navigate() changes to another Fragment using the action ID from nav_graph.xml
            findNavController().navigate(R.id.action_camera_to_pages)
        }

        // Grant permission button (shown when permission denied)
        binding.btnGrantPermission.setOnClickListener {
            // Re-request the camera permission
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        
        // Batch mode toggle button - initially dimmed to indicate inactive
        binding.btnBatchMode.alpha = 0.6f
        binding.btnBatchMode.setOnClickListener {
            toggleBatchMode()
        }
        
        // Auto-scan button - launches ML Kit Document Scanner
        binding.btnAutoScan.setOnClickListener {
            startAutoScan()
        }
    }
    
    /**
     * Start auto-scan using ML Kit Document Scanner
     * 
     * This launches ML Kit's built-in scanning UI which provides:
     * - Automatic document edge detection
     * - Real-time guidance for best capture angle
     * - Automatic perspective correction
     * - Multi-page scanning support
     */
    private fun startAutoScan() {
        try {
            // Exit batch mode if active (auto-scan handles multi-page itself)
            if (isBatchMode) {
                exitBatchMode()
            }
            
            // Launch the ML Kit Document Scanner
            DocumentScanner.startScanning(
                activity = requireActivity(),
                scanner = documentScanner,
                launcher = scannerLauncher
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start auto-scan", e)
            Toast.makeText(
                requireContext(),
                R.string.auto_scan_unavailable,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * Handle results from ML Kit Document Scanner
     * 
     * @param result The activity result containing scanned pages
     */
    private fun handleScannerResult(result: ActivityResult) {
        val scanResult = DocumentScanner.parseResult(result.resultCode, result.data)
        
        if (scanResult == null) {
            // User cancelled or scan failed
            Log.d(TAG, "Document scanning cancelled or failed")
            return
        }
        
        val pageUris = scanResult.pageUris
        if (pageUris.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "No pages scanned",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // Add all scanned pages to the ViewModel
        pageUris.forEach { uri ->
            viewModel.addPage(uri)
        }
        
        // Show success message
        val message = if (pageUris.size == 1) {
            "1 page scanned"
        } else {
            "${pageUris.size} pages scanned"
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        
        // Navigate to pages view to show the scanned pages
        findNavController().navigate(R.id.action_camera_to_pages)
    }
    
    companion object {
        private const val TAG = "CameraFragment"
    }
    
    /**
     * Toggle batch scanning mode on/off
     * 
     * Batch mode allows capturing multiple pages quickly without
     * navigating to preview screen after each capture.
     */
    private fun toggleBatchMode() {
        isBatchMode = !isBatchMode
        
        if (isBatchMode) {
            // Entering batch mode
            batchCaptureCount = 0
            updateBatchUI()
            binding.batchModeIndicator.visibility = View.VISIBLE
            // Tint the button to indicate active state
            binding.btnBatchMode.alpha = 1.0f
            Toast.makeText(requireContext(), R.string.batch_mode_on, Toast.LENGTH_SHORT).show()
        } else {
            exitBatchMode()
        }
    }
    
    /**
     * Exit batch mode and reset UI
     */
    private fun exitBatchMode() {
        isBatchMode = false
        batchCaptureCount = 0
        binding.batchModeIndicator.visibility = View.GONE
        binding.btnBatchMode.alpha = 0.6f
        Toast.makeText(requireContext(), R.string.batch_mode_off, Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Update the batch mode indicator with current count
     */
    private fun updateBatchUI() {
        binding.textBatchCount.text = getString(R.string.batch_count, batchCaptureCount)
    }

    /**
     * Observe LiveData changes from ViewModel
     * 
     * When pages list changes, update the "View Pages" button
     */
    private fun observeViewModel() {
        /**
         * observe() takes two parameters:
         * 1. LifecycleOwner - viewLifecycleOwner (tied to Fragment's view lifecycle)
         * 2. Observer lambda - called whenever data changes
         * 
         * viewLifecycleOwner (not 'this') ensures observer is removed when view is destroyed
         */
        viewModel.pages.observe(viewLifecycleOwner) { pages ->
            val count = pages.size
            if (count > 0) {
                // Show button with page count
                binding.btnViewPages.visibility = View.VISIBLE
                // getString(R.string.view_pages, count) = "View Pages (3)"
                // %d in strings.xml is replaced with count
                binding.btnViewPages.text = getString(R.string.view_pages, count)
            } else {
                binding.btnViewPages.visibility = View.GONE
            }
        }
    }

    // ============================================================
    // PERMISSION HANDLING
    // ============================================================

    /**
     * Check camera permission and act accordingly
     * 
     * ANDROID PERMISSION SYSTEM:
     * - Dangerous permissions (camera, location, etc.) need runtime request
     * - User can grant, deny, or deny permanently
     * - shouldShowRequestPermissionRationale() tells if we should explain why
     */
    private fun checkCameraPermission() {
        when {
            // Case 1: Permission already granted
            ContextCompat.checkSelfPermission(
                requireContext(),  // Fragment's context
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission granted - start camera immediately
                startCamera()
            }
            
            // Case 2: Should show rationale (user denied once but didn't check "never ask again")
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Show UI explaining why we need the permission
                showPermissionRationale()
            }
            
            // Case 3: First time asking or user chose "never ask again"
            else -> {
                // Launch the permission request dialog
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // ============================================================
    // CAMERAX SETUP
    // ============================================================

    /**
     * Initialize and start CameraX
     * 
     * CameraX uses "use cases" - you define what you want to do:
     * - Preview: Live viewfinder
     * - ImageCapture: Take photos
     * - ImageAnalysis: Process frames (for ML, etc.)
     * - VideoCapture: Record video
     */
    private fun startCamera() {
        // Hide permission UI, show camera UI
        binding.permissionLayout.visibility = View.GONE
        binding.previewView.visibility = View.VISIBLE
        binding.frameOverlay.visibility = View.VISIBLE

        /**
         * ProcessCameraProvider.getInstance() returns a ListenableFuture
         * 
         * A Future is like a "promise" in JavaScript - represents a value
         * that will be available in the future (asynchronous)
         */
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        /**
         * addListener() is called when the Future completes
         * 
         * Parameters:
         * 1. Runnable (lambda) - code to execute
         * 2. Executor - which thread to run on (main thread for UI updates)
         */
        cameraProviderFuture.addListener({
            // Get the camera provider (manages camera hardware)
            val cameraProvider = cameraProviderFuture.get()

            /**
             * Preview use case - shows live camera feed
             * 
             * Builder pattern is common in Android:
             * Builder().setOption().build()
             */
            val preview = Preview.Builder()
                .build()
                .also {
                    // Connect preview to our PreviewView in the layout
                    // setSurfaceProvider handles the rendering
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            /**
             * ImageCapture use case - handles still photos
             * 
             * CAPTURE_MODE_MAXIMIZE_QUALITY = better image, slower capture
             * CAPTURE_MODE_MINIMIZE_LATENCY = faster capture, slightly lower quality
             */
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            /**
             * Camera selector - choose which camera to use
             * 
             * DEFAULT_BACK_CAMERA = Main rear camera
             * DEFAULT_FRONT_CAMERA = Selfie camera
             */
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Remove any existing camera bindings
                // (important if this is called multiple times)
                cameraProvider.unbindAll()

                /**
                 * Bind use cases to camera lifecycle
                 * 
                 * bindToLifecycle() connects camera to Fragment's lifecycle:
                 * - Camera starts when Fragment starts
                 * - Camera stops when Fragment stops
                 * - Automatic resource cleanup
                 * 
                 * This is what makes CameraX "lifecycle-aware"
                 */
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,  // Lifecycle owner
                    cameraSelector,       // Which camera
                    preview,              // Use case 1: Preview
                    imageCapture          // Use case 2: Capture
                )
            } catch (e: Exception) {
                // Show error if camera setup fails
                // Common causes: No camera, camera in use by another app
                Toast.makeText(
                    requireContext(),
                    "Failed to start camera: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
        // ^ getMainExecutor returns the main/UI thread executor
    }

    // ============================================================
    // IMAGE CAPTURE
    // ============================================================

    /**
     * Capture a photo and save to storage
     */
    private fun takePhoto() {
        // Safety check - imageCapture might not be initialized if camera failed
        val imageCapture = imageCapture ?: return

        // Show loading indicator, disable button (prevent double-tap)
        binding.progressBar.visibility = View.VISIBLE
        binding.btnCapture.isEnabled = false

        /**
         * Create output file in app-private storage
         * 
         * filesDir = /data/data/com.pdfscanner.app/files/
         * This is PRIVATE to the app - no other app can access it
         * 
         * apply { mkdirs() } = create directory if it doesn't exist
         */
        val scansDir = File(requireContext().filesDir, "scans").apply { mkdirs() }
        
        /**
         * Generate unique filename with timestamp
         * 
         * SimpleDateFormat converts Date to String
         * Locale.US ensures consistent format regardless of device locale
         * 
         * Result: SCAN_20251226_143025_123.jpg
         */
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(System.currentTimeMillis())
        val photoFile = File(scansDir, "SCAN_$timestamp.jpg")

        // Configure where to save the captured image
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        /**
         * Take the picture asynchronously
         * 
         * Parameters:
         * 1. OutputFileOptions - where to save
         * 2. Executor - thread to run callback on
         * 3. Callback - called when capture completes or fails
         */
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                /**
                 * Called when image is saved successfully
                 */
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Hide loading, re-enable button
                    binding.progressBar.visibility = View.GONE
                    binding.btnCapture.isEnabled = true

                    // Convert File to Uri using extension function
                    // Uri is Android's standard way to reference files/resources
                    val savedUri = photoFile.toUri()
                    
                    if (isBatchMode) {
                        // BATCH MODE: Add directly to pages, stay on camera
                        viewModel.addPage(savedUri)
                        batchCaptureCount++
                        updateBatchUI()
                        
                        // Brief visual feedback - flash effect would go here
                        // For now, we just update the counter
                    } else {
                        // NORMAL MODE: Navigate to preview for crop/edit
                        // Store in ViewModel for PreviewFragment to access
                        viewModel.setCurrentCapture(savedUri)

                        /**
                         * Navigate to preview screen
                         * 
                         * CameraFragmentDirections is auto-generated from nav_graph.xml
                         * actionCameraToPreview() creates an action with the required argument
                         * 
                         * Safe Args plugin ensures type-safe navigation arguments
                         */
                        val action = CameraFragmentDirections
                            .actionCameraToPreview(savedUri.toString())
                        findNavController().navigate(action)
                    }
                }

                /**
                 * Called when capture fails
                 */
                override fun onError(exception: ImageCaptureException) {
                    binding.progressBar.visibility = View.GONE
                    binding.btnCapture.isEnabled = true

                    // Show error message to user
                    // ${} is Kotlin's string interpolation (like f-strings in Python)
                    Toast.makeText(
                        requireContext(),
                        "Failed to capture: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // ============================================================
    // PERMISSION UI
    // ============================================================

    /**
     * Show UI for permission denied state
     */
    private fun showPermissionDenied() {
        binding.previewView.visibility = View.GONE
        binding.frameOverlay.visibility = View.GONE
        binding.permissionLayout.visibility = View.VISIBLE
    }

    /**
     * Show UI explaining why permission is needed
     */
    private fun showPermissionRationale() {
        binding.previewView.visibility = View.GONE
        binding.frameOverlay.visibility = View.GONE
        binding.permissionLayout.visibility = View.VISIBLE
    }

    // ============================================================
    // CLEANUP
    // ============================================================

    /**
     * onDestroyView - Called when Fragment's view is destroyed
     * 
     * CRITICAL: Clean up resources to prevent memory leaks!
     * 
     * The binding holds references to Views. If we keep it after
     * the view is destroyed, we have a memory leak.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Shutdown the camera executor (releases threads)
        cameraExecutor.shutdown()
        
        // IMPORTANT: Set binding to null to prevent memory leak
        // This allows garbage collection of the Views
        _binding = null
    }
}
