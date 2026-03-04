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

import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    // Android Application Plugin - required for Android apps
    // Provides tasks like assembleDebug, installDebug, etc.
    id("com.android.application")

    // Kotlin Android Plugin - enables Kotlin for Android
    id("org.jetbrains.kotlin.android")

    // Safe Args Plugin - generates type-safe classes for navigation arguments
    // After adding this, rebuild to generate *Directions and *Args classes
    id("androidx.navigation.safeargs.kotlin")

    // Detekt - static analysis for Kotlin
    id("io.gitlab.arturbosch.detekt")
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
    compileSdk = 35

    /**
     * Signing Configs - for release builds
     * 
     * IMPORTANT: For production, store these in local.properties or environment variables!
     * Never commit your keystore password to version control.
     * 
     * To create a keystore, run:
     * keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pdf-scanner
     */
    signingConfigs {
        create("release") {
            // These will be loaded from local.properties or command line
            // See PUBLISHING_GUIDE.md for setup instructions
            val keystoreFile = file("../release-key.jks")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String? ?: ""
                keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String? ?: "pdf-scanner"
                keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String? ?: ""
            }
        }
    }

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
            isMinifyEnabled = true
            isShrinkResources = true
            
            // ProGuard rules files
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Use release signing config if keystore exists
            val keystoreFile = file("../release-key.jks")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        
        // Debug build type (default)
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }
    
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
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
        buildConfig = true  // SEC-01: Required for BuildConfig.DEBUG (AGP 8.x default is false)
    }

    // ===== LINT CONFIGURATION (RELEASE-02) =====
    // abortOnError = true — treat ContentDescription and TouchTargetSizeCheck as build errors
    // lintConfig points to app/lint.xml which sets those issue severities
    lint {
        abortOnError = true
        lintConfig = file("lint.xml")
        htmlReport = true
        htmlOutput = file("${project.buildDir}/reports/lint/lint-results.html")
    }
}

// ===== DETEKT STATIC ANALYSIS (RELEASE-01) =====
// Detekt 1.23.8 — MUST stay at 1.23.x (2.x requires Kotlin 2.x; project uses Kotlin 1.9.21)
// autoCorrect is NOT set — detekt-formatting runs as check/report only (not auto-formatter)
// to avoid conflicts with baseline generation.
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/detekt-baseline.xml")
    source.setFrom(
        "src/main/java",
        "src/test/java",
        "src/androidTest/java"
    )
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        html.required.set(true)
        xml.required.set(false)
    }
    exclude("**/build/**")
    exclude("**/generated/**")
}

// ===== ROBOLECTRIC JACOCO COVERAGE FIX =====
// Problem: Robolectric's InstrumentingClassLoader re-instruments classes with its own
// ASM pass, which strips the JaCoCo instrumentation that AGP added at compile time.
// The AGP exec file therefore shows 0% for Robolectric-backed test classes.
//
// Fix: Use the JaCoCo Gradle plugin's built-in task extension to write a SEPARATE
// exec file specifically for the testDebugUnitTest JVM process. JaCoCo's agent runs
// in the host JVM (not Robolectric's sandboxed classloader), so it captures coverage
// for the instrument call chains that Robolectric exposes back to the host JVM.
//
// The jacocoTestReport task is updated below to include BOTH exec files.
apply(plugin = "jacoco")

// Configure the JaCoCo agent for the unit test task to write a separate exec file
afterEvaluate {
    tasks.named("testDebugUnitTest") {
        extensions.configure<JacocoTaskExtension> {
            isEnabled = true
            destinationFile = layout.buildDirectory.file(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTestRobolectric.exec"
            ).get().asFile
            isIncludeNoLocationClasses = true
            // Exclude synthetic Kotlin lambdas/coroutine continuations from probe insertion
            excludes = listOf("jdk.internal.*", "sun.reflect.*")
        }
    }
}

// ===== DEPENDENCY RESOLUTION STRATEGY =====
// Force coroutines to 1.7.3 across ALL configurations.
// Without this, mockk / Robolectric transitive dependencies pull in a
// kotlinx-coroutines BOM that upgrades coroutines to 1.10.1 (Kotlin 2.1.0 binary),
// which is incompatible with this project's Kotlin 1.9.21 compiler.
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.7.3")
        force("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
        // Keep Kotlin stdlib at 1.9.x — prevents transitive Kotlin 2.x stdlib from being pulled in
        force("org.jetbrains.kotlin:kotlin-stdlib:1.9.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.21")
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
    // IMAGE PROCESSING
    // ===========================================

    // ExifInterface - read EXIF orientation from imported images
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // ===========================================
    // UI COMPONENTS
    // ===========================================

    // RecyclerView - efficient scrolling lists and grids
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    
    // ===========================================
    // IMAGE LOADING
    // ===========================================

    // Coil - Kotlin-first image loading with automatic caching, lifecycle handling, and placeholders
    // Replaces manual BitmapFactory + LruCache in PagesAdapter and HistoryAdapter
    // 3.4.0 is current stable (released 2026-02-24). No network fetcher needed — loads local file URIs.
    implementation("io.coil-kt:coil:2.7.0")

    // ===========================================
    // TESTING
    // ===========================================
    
    // ===== UNIT TESTS (src/test/) =====
    testImplementation("junit:junit:4.13.2")                                         // already present
    testImplementation("io.mockk:mockk:1.14.7")                                      // ADD
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")        // ADD — matches coroutines-android:1.7.3
    testImplementation("androidx.arch.core:core-testing:2.2.0")                      // ADD — InstantTaskExecutorRule
    testImplementation("org.robolectric:robolectric:4.16")                           // ADD
    testImplementation("androidx.test:core-ktx:1.6.1")                              // ADD
    testImplementation("com.google.truth:truth:1.4.4")                               // ADD

    // ===== INSTRUMENTED TESTS (src/androidTest/) =====
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0")                  // UPDATE from junit:1.1.5
    androidTestImplementation("androidx.test:runner:1.6.2")                         // ADD
    androidTestImplementation("androidx.test:core-ktx:1.6.1")                       // ADD
    androidTestImplementation("androidx.test:rules:1.6.1")                          // ADD
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")         // UPDATE from 3.5.1
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")      // ADD
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.7.0")      // ADD
    // mockk-android:1.14.7 and mockk-agent:1.14.7 are compiled with Kotlin 2.1.0 binary
    // and are incompatible with this project's Kotlin 1.9.21. They are removed because
    // no instrumented test files use MockK — fragment smoke tests use only Truth assertions.
    // androidTestImplementation("io.mockk:mockk-android:1.14.7")
    // androidTestImplementation("io.mockk:mockk-agent:1.14.7")
    androidTestImplementation("com.google.truth:truth:1.4.4")                       // ADD

    // ===== FRAGMENT TESTING (stretch — TEST-07) =====
    debugImplementation("androidx.fragment:fragment-testing:1.8.9")                 // ADD

    // ===== NAVIGATION TESTING (TEST-07/TEST-08) =====
    // Provides TestNavHostController for navigation flow tests
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.6")       // ADD

    // ===== STATIC ANALYSIS (RELEASE-01) =====
    // Detekt formatting plugin — runs as check/report only (no autoCorrect) to avoid baseline conflicts
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")

    // ===== MEMORY LEAK DETECTION (RELEASE-08) =====
    // LeakCanary auto-installs via ContentProvider — no Application subclass changes needed
    // Navigation 2.7.x AbstractAppBarOnDestinationChangedListener leak is documented in KNOWN_LEAKS.md
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}

// ===== JACOCO COVERAGE REPORT (RELEASE-09) =====
// Requires: ./gradlew testDebugUnitTest jacocoTestReport
// Output:   app/build/reports/jacoco/jacocoTestReport/html/index.html
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    // COUNTER TYPE: LINE (not BRANCH — Kotlin coroutines inflate BRANCH by 15-25%)
    // EXCLUSIONS: generated classes that inflate coverage numbers negatively
    val fileFilter = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        // Navigation SafeArgs generated classes
        "**/*Args.*",
        "**/*Directions.*",
        // View Binding generated classes
        "**/*Binding.*",
        "**/*Binding\$*.*",
        // Databinding (not used, but defensive)
        "**/databinding/**",
        "**/android/databinding/**",
    )

    val debugTree = fileTree("${layout.buildDirectory.get()}/intermediates/javac/debug") {
        exclude(fileFilter)
    }
    val kotlinDebugTree = fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
        exclude(fileFilter)
    }

    classDirectories.setFrom(files(debugTree, kotlinDebugTree))
    sourceDirectories.setFrom(files("${projectDir}/src/main/java"))
    // Include both exec files:
    // 1. AGP-generated exec (covers JUnit4/MockK tests — ScannerViewModelTest)
    // 2. Gradle JaCoCo plugin exec (covers Robolectric tests — ImageProcessorTest,
    //    DocumentEntryTest, DocumentHistoryRepositoryTest)
    executionData.setFrom(fileTree(layout.buildDirectory.get()) {
        include(
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTestRobolectric.exec",
        )
    })
}
