/**
 * MainActivity.kt - The Entry Point of the Android Application
 * 
 * ANDROID CONCEPT: Activity
 * =========================
 * An Activity is like a "window" in your app. Think of it as a container
 * that holds your UI. In modern Android, we use a "Single Activity Architecture"
 * where one Activity hosts multiple Fragments (individual screens).
 * 
 * This is similar to having one main() function in C++ that manages
 * different modules/screens.
 * 
 * LIFECYCLE:
 * When app starts → onCreate() is called (like a constructor)
 * User leaves app → onPause(), onStop()
 * User returns → onResume()
 * App closed → onDestroy()
 */

package com.pdfscanner.app

// Android core imports
import android.os.Bundle  // Bundle is like a dictionary/map for passing data
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity  // Base class for Activities
import androidx.appcompat.app.AppCompatDelegate  // For theme switching
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat  // For edge-to-edge display
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.fragment.NavHostFragment  // Handles Fragment navigation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.io.File

// View Binding - auto-generated class from activity_main.xml
// Naming convention: ActivityMainBinding comes from activity_main.xml
import com.pdfscanner.app.databinding.ActivityMainBinding
import com.pdfscanner.app.util.AppLockManager
import com.pdfscanner.app.util.AppPreferences
import com.pdfscanner.app.util.RootDetector
import com.pdfscanner.app.util.SecurePreferences

/**
 * MainActivity extends AppCompatActivity
 * 
 * AppCompatActivity provides backward compatibility for older Android versions
 * (like using a compatibility library in C++)
 */
class MainActivity : AppCompatActivity() {

    /**
     * View Binding variable
     *
     * 'lateinit' means "I promise to initialize this before using it"
     * This is Kotlin's way of handling non-null variables that can't be
     * initialized in the declaration.
     *
     * Similar to declaring a pointer in C++ and initializing it later.
     */
    private lateinit var binding: ActivityMainBinding

    // SEC-02: Biometric app lock state
    private var isAuthenticating = false
    private var isAuthenticated = false

    /**
     * onCreate() - Called when Activity is first created
     * 
     * This is like a constructor + initialization combined.
     * 'savedInstanceState' contains data from previous instance if the
     * Activity was destroyed and recreated (e.g., screen rotation).
     * 
     * @param savedInstanceState Previously saved state, or null if fresh start
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme BEFORE super.onCreate() for smooth theme transition
        val prefs = AppPreferences(this)
        
        // Apply the selected app style (Ghibli or Cartoon)
        applyAppStyle(prefs)
        
        // Apply dark/light mode preference
        AppCompatDelegate.setDefaultNightMode(prefs.getThemeMode())

        // SEC-01: Prevent screenshots and Recents thumbnails in release builds
        // Conditional on BuildConfig.DEBUG to preserve screenshot test capability (locked decision)
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        // ALWAYS call super first - this runs the parent class's onCreate
        // Failing to do this will crash your app!
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display: content draws behind system bars
        // Must be called AFTER super.onCreate() and BEFORE setContentView()
        // WindowCompat.enableEdgeToEdge() does not exist; use setDecorFitsSystemWindows(false)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        /**
         * View Binding inflation
         * 
         * 'inflate' parses the XML layout and creates actual View objects
         * This is like parsing an HTML file into a DOM tree
         * 
         * layoutInflater is a system service that does the XML → Object conversion
         */
        binding = ActivityMainBinding.inflate(layoutInflater)
        
        /**
         * setContentView() tells Android "this is what to display"
         * 
         * binding.root is the top-level View (ConstraintLayout in our XML)
         * 
         * OLD way: setContentView(R.layout.activity_main)
         * NEW way (with binding): setContentView(binding.root)
         */
        setContentView(binding.root)

        // SEC-13: Track app background transitions for auto-lock timeout
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // App went to background -- record timestamp for timeout check
                AppLockManager.recordBackgroundTime(applicationContext)
                // Reset authenticated state so onResume will re-check
                isAuthenticated = false
                // Show overlay immediately to prevent content glimpse on task switcher
                if (AppLockManager.isLockEnabled(applicationContext)) {
                    binding.lockOverlay.visibility = View.VISIBLE
                }
            }
        })

        // SEC-02: Show overlay on cold start if lock is enabled
        if (AppLockManager.shouldRequireAuth(this)) {
            binding.lockOverlay.visibility = View.VISIBLE
        }

        // Clean up stale temp files from previous sessions
        cleanupStaleTempFiles()

        // SEC-14: Root/debuggable detection -- release builds only
        if (!BuildConfig.DEBUG) {
            checkRootedDevice()
        }

        /**
         * Navigation Setup
         * 
         * NavHostFragment is a special Fragment that:
         * 1. Hosts other Fragments (our screens)
         * 2. Handles navigation between them
         * 3. Manages the back stack (pressing back goes to previous screen)
         * 
         * findFragmentById finds the NavHostFragment we defined in activity_main.xml
         * The 'as' keyword is Kotlin's type cast (like static_cast in C++)
         */
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        
        /**
         * NavController is the object that actually performs navigation
         * We get it from the NavHostFragment
         * 
         * We could use this to navigate programmatically from the Activity,
         * but in this app, Fragments handle their own navigation.
         */
        val navController = navHostFragment.navController
        
        // NavController is available but not used here since Fragments navigate themselves
        // You could add: setupActionBarWithNavController(navController) for toolbar integration
    }

    override fun onResume() {
        super.onResume()
        // SEC-02: Check if biometric auth is needed
        if (AppLockManager.isLockEnabled(this)) {
            // Graceful degradation: if user removed device security between sessions, auto-disable lock
            if (!AppLockManager.canAuthenticate(this)) {
                AppLockManager.setLockEnabled(this, false)
                AppLockManager.clearAuthState(this)
                binding.lockOverlay.visibility = View.GONE
                Snackbar.make(binding.root, R.string.app_lock_disabled_no_security, Snackbar.LENGTH_LONG).show()
                return
            }
            if (AppLockManager.shouldRequireAuth(this) && !isAuthenticated && !isAuthenticating) {
                showBiometricPrompt()
            } else if (isAuthenticated) {
                binding.lockOverlay.visibility = View.GONE
            }
        } else {
            binding.lockOverlay.visibility = View.GONE
        }
    }

    /**
     * SEC-02: Show BiometricPrompt with API-tiered authenticator flags.
     * Uses AppLockManager.getAllowedAuthenticators() which returns:
     *   API 30+: BIOMETRIC_STRONG | DEVICE_CREDENTIAL
     *   API < 30: BIOMETRIC_WEAK | DEVICE_CREDENTIAL
     *
     * IMPORTANT: Do NOT call setNegativeButtonText when DEVICE_CREDENTIAL is included
     * (BiometricPrompt throws IllegalArgumentException).
     */
    private fun showBiometricPrompt() {
        isAuthenticating = true
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isAuthenticated = true
                    isAuthenticating = false
                    binding.lockOverlay.visibility = View.GONE
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    isAuthenticating = false
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        // User refused authentication -- close app to prevent bypass
                        finishAffinity()
                    }
                    // Other errors (e.g., ERROR_LOCKOUT): prompt dismissed, overlay stays,
                    // next onResume will re-trigger prompt
                }
                override fun onAuthenticationFailed() {
                    // Biometric not recognized -- prompt stays open, user can retry
                    // No action needed; BiometricPrompt handles retry UI internally
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_lock_title))
            .setSubtitle(getString(R.string.app_lock_subtitle))
            .setAllowedAuthenticators(AppLockManager.getAllowedAuthenticators())
            .build()
        prompt.authenticate(promptInfo)
    }

    /**
     * Clean up stale temp files created by PDF viewing and editing operations.
     * SEC-05: Deletes ALL matching temp files on startup regardless of age,
     * ensuring no stale temp files survive an app restart.
     * Runs on app startup as a best-effort cleanup (never crashes the app).
     */
    private fun cleanupStaleTempFiles() {
        try {
            val cacheDir = cacheDir
            // SEC-05: Delete ALL matching temp files on startup (no age check)
            // Ensures no stale temp files survive app restart
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val name = file.name
                    if (name.startsWith("pdf_view_") ||
                        name.startsWith("temp_edit_") ||
                        name.startsWith("pdf_compress_temp")) {
                        file.delete()
                    }
                }
            }
            // Also clean pdf_compress_temp directory completely
            val compressTemp = File(cacheDir, "pdf_compress_temp")
            if (compressTemp.exists() && compressTemp.isDirectory) {
                compressTemp.listFiles()?.forEach { it.delete() }
                compressTemp.delete()
            }
        } catch (e: Exception) {
            // Cleanup is best-effort, don't crash the app
            android.util.Log.w("MainActivity", "Temp cleanup failed", e)
        }
    }

    /**
     * SEC-14: Show a one-time dismissible warning dialog if the device is
     * rooted or running a debuggable system image. Warn-only -- never
     * blocks functionality or calls finish().
     *
     * Persists dismissal in SecurePreferences so the dialog appears at most once.
     */
    private fun checkRootedDevice() {
        if (!RootDetector.isDeviceCompromised(this)) return

        val securePrefs = SecurePreferences.getInstance(this)
        val dismissedKey = "${SecurePreferences.APP_PREFIX}root_warning_dismissed"
        if (securePrefs.getBoolean(dismissedKey, false)) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.security_warning_title)
            .setMessage(R.string.security_warning_rooted_message)
            .setPositiveButton(R.string.i_understand) { _, _ ->
                securePrefs.edit().putBoolean(dismissedKey, true).apply()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Apply the selected app style theme
     *
     * This must be called BEFORE super.onCreate() to properly apply the theme.
     * The theme is set using setTheme() which changes the base theme for the Activity.
     */
    private fun applyAppStyle(prefs: AppPreferences) {
        // Always use Cartoon theme
        setTheme(R.style.Theme_PDFScanner_Cartoon)
    }
}
