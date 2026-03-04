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
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity  // Base class for Activities
import androidx.appcompat.app.AppCompatDelegate  // For theme switching
import androidx.core.view.WindowCompat  // For edge-to-edge display
import androidx.navigation.fragment.NavHostFragment  // Handles Fragment navigation
import java.io.File

// View Binding - auto-generated class from activity_main.xml
// Naming convention: ActivityMainBinding comes from activity_main.xml
import com.pdfscanner.app.databinding.ActivityMainBinding
import com.pdfscanner.app.util.AppPreferences

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

        // Clean up stale temp files from previous sessions
        cleanupStaleTempFiles()

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
    
    /**
     * Clean up stale temp files created by PDF viewing and editing operations.
     * Removes files older than 1 hour that match known temp file prefixes.
     * Runs on app startup as a best-effort cleanup (never crashes the app).
     */
    private fun cleanupStaleTempFiles() {
        try {
            val cacheDir = cacheDir
            val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000)

            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < oneHourAgo) {
                    val name = file.name
                    if (name.startsWith("pdf_view_temp_") ||
                        name.startsWith("temp_edit_") ||
                        name.startsWith("pdf_compress_temp")) {
                        file.delete()
                    }
                }
            }

            // Also clean pdf_compress_temp directory
            val compressTemp = File(cacheDir, "pdf_compress_temp")
            if (compressTemp.exists() && compressTemp.isDirectory) {
                compressTemp.listFiles()?.forEach { file ->
                    if (file.lastModified() < oneHourAgo) {
                        file.delete()
                    }
                }
                if (compressTemp.listFiles()?.isEmpty() == true) {
                    compressTemp.delete()
                }
            }
        } catch (e: Exception) {
            // Cleanup is best-effort, don't crash the app
            android.util.Log.w("MainActivity", "Temp cleanup failed", e)
        }
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
