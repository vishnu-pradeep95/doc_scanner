/**
 * app/build.gradle.kts - Module-level Build Configuration
 * 
 * This file configures:
 * 1. Plugins to apply (Android, Kotlin, etc.)
 * 2. Android-specific settings (SDK versions, build types)
 * 3. Dependencies (libraries your app uses)
 * 
 * KOTLIN DSL:
 * This uses Kotlin syntax (build.gradle.kts) instead of Groovy (build.gradle)
 * Kotlin DSL provides better IDE support and type checking
 */

plugins {
    // Android Application Plugin - required for Android apps
    // Provides tasks like assembleDebug, installDebug, etc.
    id("com.android.application")
    
    // Kotlin Android Plugin - enables Kotlin for Android
    id("org.jetbrains.kotlin.android")
    
    // Safe Args Plugin - generates type-safe classes for navigation arguments
    // After adding this, rebuild to generate *Directions and *Args classes
    id("androidx.navigation.safeargs.kotlin")
}

/**
 * android {} block - Android-specific configuration
 */
android {
    // Namespace for generated R class and BuildConfig
    // Must match applicationId or be a parent package
    namespace = "com.pdfscanner.app"
    
    // SDK version to compile against
    // Use latest stable SDK for newest APIs and best tooling
    compileSdk = 34

    defaultConfig {
        // Unique identifier for your app on Play Store
        // Cannot be changed after publishing!
        applicationId = "com.pdfscanner.app"
        
        // Minimum Android version supported
        // 24 = Android 7.0 (Nougat) - good balance of features vs reach
        minSdk = 24
        
        // Target SDK - Android version you've tested against
        // Should match compileSdk for best behavior
        targetSdk = 34
        
        // Version code - integer, must increase with each release
        // Play Store uses this to determine updates
        versionCode = 1
        
        // Version name - human-readable version string
        versionName = "1.0"

        // Test runner for instrumented tests (run on device/emulator)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * Build Types - different configurations for different purposes
     * 
     * debug - default for development, includes debugging symbols
     * release - for distribution, optimized and minified
     */
    buildTypes {
        release {
            // Minification (ProGuard/R8) - shrinks and obfuscates code
            // Set to true for release builds to reduce APK size
            isMinifyEnabled = false
            
            // ProGuard rules files
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    /**
     * Java compatibility options
     * 
     * Android uses a subset of Java. Java 17 is current standard.
     */
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    /**
     * Kotlin-specific options
     */
    kotlinOptions {
        jvmTarget = "17"  // Must match Java version
    }
    
    /**
     * Build Features - enable optional Android features
     */
    buildFeatures {
        // View Binding generates a binding class for each XML layout
        // Provides type-safe access to views (no more findViewById)
        viewBinding = true
    }
}

/**
 * dependencies {} block - External Libraries
 * 
 * implementation() - library is used in your code, not exposed to dependents
 * api() - library is used and exposed to dependents (for library modules)
 * testImplementation() - only for unit tests
 * androidTestImplementation() - only for instrumented tests
 * 
 * Dependency format: "group:artifact:version"
 * Example: "androidx.core:core-ktx:1.12.0"
 */
dependencies {
    // ===========================================
    // CORE ANDROID LIBRARIES
    // ===========================================
    
    // Core KTX - Kotlin extensions for Android APIs
    // Provides idiomatic Kotlin syntax for common operations
    implementation("androidx.core:core-ktx:1.12.0")
    
    // AppCompat - backward compatibility for UI components
    // Allows using modern UI features on older Android versions
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // Material Design Components - Google's design system
    // Provides buttons, cards, dialogs, and more with Material styling
    implementation("com.google.android.material:material:1.11.0")
    
    // ConstraintLayout - flexible layout system
    // Allows complex layouts with flat view hierarchy (better performance)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // ===========================================
    // NAVIGATION
    // ===========================================
    
    // Navigation Fragment - handles Fragment transactions
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    
    // Navigation UI - connects navigation to UI components (toolbar, etc.)
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    
    // ===========================================
    // LIFECYCLE & ARCHITECTURE
    // ===========================================
    
    // ViewModel - survives configuration changes
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    
    // Lifecycle Runtime - lifecycle-aware coroutine scopes
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    
    // Fragment KTX - Kotlin extensions for Fragments
    // Provides 'by viewModels()' and 'by activityViewModels()' delegates
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    
    // ===========================================
    // CAMERAX - Modern Camera API
    // ===========================================
    
    // Version variable - ensures all CameraX libs use same version
    val cameraxVersion = "1.3.1"
    
    // Core - basic CameraX functionality
    implementation("androidx.camera:camera-core:$cameraxVersion")
    
    // Camera2 - implementation using Camera2 API
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    
    // Lifecycle - ties camera to lifecycle (auto start/stop)
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    
    // View - PreviewView widget for camera preview
    implementation("androidx.camera:camera-view:$cameraxVersion")
    
    // ===========================================
    // IMAGE CROPPING
    // ===========================================
    
    // CanHub Image Cropper - maintained fork of uCrop
    // Provides crop activity with rotation, aspect ratio, etc.
    // From JitPack repository (configured in settings.gradle.kts)
    // Note: Using 4.4.0 as newer versions have JitPack build errors
    implementation("com.github.CanHub:Android-Image-Cropper:4.4.0")
    
    // ===========================================
    // ML KIT - Text Recognition (OCR)
    // ===========================================
    
    // ML Kit Text Recognition - on-device OCR
    // Recognizes text in Latin-based languages (English, Spanish, etc.)
    // Model is bundled with app (~3.5MB increase)
    implementation("com.google.mlkit:text-recognition:16.0.0")
    
    // ===========================================
    // PDF VIEWER & EDITOR
    // ===========================================
    
    // Using custom NativePdfView with Android's built-in PdfRenderer API
    // No external library needed - see NativePdfView.kt in the editor package
    // This avoids AndroidX compatibility issues with third-party PDF libraries
    
    // ===========================================
    // ML KIT - Document Scanner (Auto Edge Detection)
    // ===========================================
    
    // ML Kit Document Scanner - auto document detection with edge detection
    // Provides built-in UI for scanning documents
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    
    // ===========================================
    // KOTLIN COROUTINES
    // ===========================================
    
    // Coroutines Android - async programming with Android dispatchers
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Coroutines Play Services - await() for Google Play Tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // ===========================================
    // UI COMPONENTS
    // ===========================================
    
    // RecyclerView - efficient scrolling lists and grids
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // ===========================================
    // TESTING
    // ===========================================
    
    // JUnit - unit testing framework (runs on JVM, not device)
    testImplementation("junit:junit:4.13.2")
    
    // AndroidX Test - unit testing with Android components
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    
    // Espresso - UI testing framework (runs on device/emulator)
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
