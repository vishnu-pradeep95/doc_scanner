# Architecture Research: Test Suite Structure for Android Document Scanner

**Domain:** Android app testing and release readiness — adding test coverage and release tooling to existing MVVM app
**Researched:** 2026-03-01
**Confidence:** HIGH (based on established Android testing patterns and actual codebase analysis)

## Existing Architecture Summary

The app follows Single Activity + Navigation Component + MVVM with these concrete components:

```
app/src/main/java/com/pdfscanner/app/
  MainActivity.kt                       # Single Activity host
  viewmodel/ScannerViewModel.kt         # Shared ViewModel (activityViewModels)
  editor/PdfEditorViewModel.kt          # Editor ViewModel (AndroidViewModel)
  editor/PdfAnnotation.kt               # Data models for annotations
  editor/AnnotationCanvasView.kt        # Custom View for drawing
  editor/PdfEditorFragment.kt           # Editor UI
  editor/SignaturePadView.kt            # Custom View for signatures
  editor/NativePdfView.kt              # Custom View for PDF rendering
  editor/SignatureDialogFragment.kt     # Dialog
  editor/StampPickerDialogFragment.kt   # Dialog
  editor/TextInputDialogFragment.kt     # Dialog
  ui/HomeFragment.kt                    # Home screen
  ui/CameraFragment.kt                  # CameraX capture
  ui/PreviewFragment.kt                 # Image preview + filters
  ui/PagesFragment.kt                   # Multi-page management
  ui/PdfViewerFragment.kt              # PDF viewing
  ui/HistoryFragment.kt                # Document history
  ui/SettingsFragment.kt               # App settings
  util/ImageProcessor.kt               # Bitmap filter logic (pure functions)
  util/PdfUtils.kt                     # PDF merge/split/compress (suspend, needs Context)
  util/DocumentScanner.kt              # ML Kit Document Scanner wrapper
  util/SoundManager.kt                 # Sound effects
  util/AnimationHelper.kt              # UI animations
  util/AppPreferences.kt               # SharedPreferences wrapper
  util/PdfPageExtractor.kt             # PDF page extraction
  ocr/OcrProcessor.kt                  # ML Kit text recognition
  data/DocumentHistory.kt              # DocumentEntry + DocumentHistoryRepository
  adapter/PagesAdapter.kt              # RecyclerView adapter
  adapter/HistoryAdapter.kt            # RecyclerView adapter
  adapter/RecentDocumentsAdapter.kt    # RecyclerView adapter
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| ScannerViewModel | Shared state: scanned pages list, current capture URI, PDF URI, loading state, filter state | Plain ViewModel with MutableLiveData, SavedStateHandle for process-death safety |
| PdfEditorViewModel | Editor state: annotations per page, saved signatures, drawing properties, tool selection | AndroidViewModel (needs Application for filesDir), JSON-based signature storage |
| ImageProcessor | Bitmap filter application (Enhanced, B&W, Magic, Sharpen) | Pure object with static functions, takes Bitmap returns Bitmap |
| PdfUtils | PDF merge, split, compress, extract pages | Object with suspend functions, needs Context for content resolver and filesDir |
| DocumentHistoryRepository | CRUD for document history entries | SharedPreferences + JSON, singleton pattern |
| OcrProcessor | Text recognition from images | ML Kit TextRecognizer wrapper, suspend functions |
| DocumentScanner | ML Kit Document Scanner integration | Thin wrapper around GMS Document Scanner API |
| Fragments (7 total) | UI layer: camera, preview, pages, home, history, settings, PDF viewer | View Binding, observe LiveData, delegate to ViewModels |
| PdfEditorFragment | Complex UI: annotation canvas, tool switching, PDF rendering | Combines NativePdfView + AnnotationCanvasView |
| Custom Views (3) | AnnotationCanvasView, SignaturePadView, NativePdfView | Canvas-based drawing, touch handling |
| Adapters (3) | RecyclerView list rendering | ViewHolder pattern, click listeners |

## Recommended Test Architecture

### Test Pyramid for This App

```
                    /\
                   /  \
                  / E2E \          ~5 tests
                 / (Manual \       Smoke tests on real device
                /  + limited  \    CameraX, ML Kit, full flows
               /   automated)  \
              /________________\
             /                  \
            /   Integration      \    ~15-20 tests
           /  (androidTest)       \   Fragment + ViewModel, Navigation,
          /   Robolectric where    \  File I/O with real filesystem
         /    possible              \
        /__________________________\
       /                            \
      /       Unit Tests             \   ~40-60 tests
     /   (test - local JVM)           \  ViewModels, ImageProcessor,
    /    Pure logic, data models,      \ PdfUtils logic, DocumentEntry,
   /     PdfOperationResult, filters    \ DocumentHistoryRepository
  /____________________________________\
```

**Ratio target: ~60% unit, ~30% integration, ~10% manual/E2E**

This ratio is skewed toward unit tests because the app has significant testable logic in its utility classes and ViewModels that requires zero refactoring.

### What Is Testable Without Restructuring

| Component | Test Type | Testable As-Is | Notes |
|-----------|-----------|----------------|-------|
| ScannerViewModel | Unit (JVM) | YES | Pure ViewModel, only depends on Uri and LiveData |
| PdfEditorViewModel | Unit (JVM) | PARTIAL | Extends AndroidViewModel, needs Application mock or Robolectric |
| ImageProcessor | Unit (JVM) | YES | Pure functions on Bitmap. Bitmap needs Robolectric or Android test |
| PdfUtils | Integration (device) | YES | Needs real ContentResolver and filesystem |
| DocumentEntry | Unit (JVM) | YES | Data class with JSON serialization |
| DocumentHistoryRepository | Integration | YES | Needs SharedPreferences (Robolectric or device) |
| OcrProcessor | Integration (device) | PARTIAL | Needs ML Kit runtime, only testable on device with real model |
| DocumentScanner | NOT TESTABLE | NO | Wraps GMS Intent-based API, test at E2E level only |
| Fragments | Integration (device) | YES | FragmentScenario for isolated testing |
| Navigation flows | Integration (device) | YES | TestNavHostController |
| Custom Views | Integration (device) | PARTIAL | Needs real Canvas, test rendering on device |
| Adapters | Unit/Integration | YES | Test data binding, click callbacks |

### What Requires Mocking vs Real Dependencies

**No mocking needed (test directly):**
- ScannerViewModel -- all LiveData operations, page management, filter tracking
- DocumentEntry -- JSON round-trip, exists() with temp files, formattedSize()
- PdfOperationResult -- data class validation
- ImageProcessor.FilterType enum usage

**Needs Robolectric (JVM with Android stubs):**
- ImageProcessor -- Bitmap, Canvas, ColorMatrix are Android classes
- DocumentHistoryRepository -- SharedPreferences
- PdfEditorViewModel -- AndroidViewModel requires Application

**Needs real device/emulator (androidTest):**
- PdfUtils -- PdfRenderer, PdfDocument, ContentResolver
- OcrProcessor -- ML Kit model loading
- CameraFragment -- CameraX requires camera hardware
- Navigation flows -- Fragment lifecycle + NavController
- Custom Views -- Canvas rendering, touch events

## Recommended Test Directory Structure

New directories to create (none exist today):

```
app/src/
  test/                                          # JVM unit tests  [NEW]
    java/com/pdfscanner/app/
      viewmodel/
        ScannerViewModelTest.kt                  # Page management, filter state, loading
      editor/
        PdfEditorViewModelTest.kt                # Tool selection, page nav, annotation state
      data/
        DocumentEntryTest.kt                     # JSON serialization, formattedSize
        DocumentHistoryRepositoryTest.kt         # CRUD ops (Robolectric)
      util/
        ImageProcessorTest.kt                    # Filter application (Robolectric for Bitmap)
        PdfUtilsFormatTest.kt                    # formatFileSize, pure utility methods

  androidTest/                                   # Instrumented tests  [NEW]
    java/com/pdfscanner/app/
      util/
        PdfUtilsInstrumentedTest.kt              # Merge, split, compress with real PDFs
        PdfPageExtractorTest.kt                  # PDF page extraction
      ocr/
        OcrProcessorTest.kt                      # Text recognition with test images
      ui/
        HomeFragmentTest.kt                      # Navigation from home
        CameraFragmentTest.kt                    # Camera permission, basic UI (no capture)
        PreviewFragmentTest.kt                   # Filter application, add page flow
        PagesFragmentTest.kt                     # Drag reorder, multi-select, PDF generation
        HistoryFragmentTest.kt                   # List display, delete, open
        PdfViewerFragmentTest.kt                 # PDF rendering, share
      editor/
        PdfEditorFragmentTest.kt                 # Tool switching, annotation, save
      navigation/
        NavigationFlowTest.kt                    # End-to-end navigation paths
      data/
        DocumentHistoryInstrumentedTest.kt       # Real SharedPreferences on device

  androidTest/assets/                            # Test fixture PDFs  [NEW]
    test_single_page.pdf                         # Used by PdfUtils merge/split tests
    test_multi_page.pdf                          # 3+ page PDF for split/extract tests
    test_ocr_image.jpg                           # Known-text image for OCR assertion
```

**New files at root level:**

```
app/
  proguard-rules.pro          [MODIFY — add ML Kit + Safe Args + Navigation rules]
  detekt.yml                  [NEW — Detekt rule configuration]
  detekt-baseline.xml         [NEW — generated by detektBaseline task, suppresses existing violations]
  lint.xml                    [NEW — Android Lint configuration, accessibility as errors]

config/
  (no separate config dir needed for single-module app)
```

## Architectural Patterns for Testing

### Pattern 1: ViewModel Unit Testing with InstantTaskExecutorRule

**What:** Test ScannerViewModel on JVM using AndroidX test utilities that make LiveData synchronous.
**When to use:** All ViewModel tests. ScannerViewModel takes SavedStateHandle — pass `SavedStateHandle()` directly in tests (no mocking needed, it works on JVM).
**Trade-offs:** Fast execution, no device needed. Cannot test coroutine-dependent operations without TestCoroutineDispatcher.

**Example:**
```kotlin
@RunWith(JUnit4::class)
class ScannerViewModelTest {
    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: ScannerViewModel

    @Before
    fun setup() {
        // SavedStateHandle() works on JVM without mocking
        viewModel = ScannerViewModel(SavedStateHandle())
    }

    @Test
    fun addPage_addsToList() {
        val uri = Uri.parse("file:///test/scan1.jpg")
        viewModel.addPage(uri)
        assertEquals(1, viewModel.getPageCount())
        assertEquals(uri, viewModel.pages.value?.first())
    }

    @Test
    fun movePage_swapsPositions() {
        val uri1 = Uri.parse("file:///test/scan1.jpg")
        val uri2 = Uri.parse("file:///test/scan2.jpg")
        viewModel.addPage(uri1)
        viewModel.addPage(uri2)
        viewModel.movePage(0, 1)
        assertEquals(uri2, viewModel.pages.value?.get(0))
        assertEquals(uri1, viewModel.pages.value?.get(1))
    }

    @Test
    fun getPdfFileName_usesBaseName_whenSet() {
        viewModel.setPdfBaseName("Meeting Notes")
        val result = viewModel.getPdfFileName("20260301_120000")
        assertEquals("Meeting Notes_20260301_120000.pdf", result)
    }

    @Test
    fun clearAllPages_resetsEverything() {
        viewModel.addPage(Uri.parse("file:///test.jpg"))
        viewModel.setPdfBaseName("test")
        viewModel.clearAllPages()
        assertEquals(0, viewModel.getPageCount())
        assertNull(viewModel.pdfBaseName.value)
    }
}
```

### Pattern 2: Robolectric for Android-Dependent Unit Tests

**What:** Run tests that need Android framework classes (Bitmap, SharedPreferences, Application) on JVM using Robolectric shadows.
**When to use:** ImageProcessor tests, DocumentHistoryRepository tests, PdfEditorViewModel tests.
**Trade-offs:** Slower than pure JVM tests (~3-5s startup) but much faster than device tests. Not 100% faithful to real Android behavior for Canvas operations. Robolectric does NOT adequately shadow PdfRenderer — do not use Robolectric for PdfUtils tests.

**Example:**
```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImageProcessorTest {
    @Test
    fun applyFilter_original_returnsSameBitmap() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val result = ImageProcessor.applyFilter(bitmap, ImageProcessor.FilterType.ORIGINAL)
        assertSame(bitmap, result)
    }

    @Test
    fun applyFilter_enhanced_returnsDifferentBitmap() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val result = ImageProcessor.applyFilter(bitmap, ImageProcessor.FilterType.ENHANCED)
        assertNotSame(bitmap, result)
        assertEquals(bitmap.width, result.width)
    }

    @Test
    fun applyFilter_magic_capsToMaxDimension() {
        // 4000x4000 should be scaled down to ~3368
        val bitmap = Bitmap.createBitmap(4000, 4000, Bitmap.Config.ARGB_8888)
        val result = ImageProcessor.applyFilter(bitmap, ImageProcessor.FilterType.MAGIC)
        assertTrue(result.width <= 3368 && result.height <= 3368)
    }
}
```

### Pattern 3: Fragment Testing with FragmentScenario

**What:** Launch fragments in isolation with mock or real NavController to test UI behavior.
**When to use:** All Fragment integration tests. Use `launchFragmentInContainer` for UI tests, `launchFragment` (no container) for logic-only tests.
**Trade-offs:** Requires device/emulator, slower. Provides real lifecycle and view rendering.

**Example:**
```kotlin
@RunWith(AndroidJUnit4::class)
class HomeFragmentTest {
    @Test
    fun clickScanButton_navigatesToCamera() {
        val navController = TestNavHostController(ApplicationProvider.getApplicationContext())

        launchFragmentInContainer<HomeFragment>(themeResId = R.style.Theme_PdfScanner).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.btnScan)).perform(click())
        assertEquals(R.id.cameraFragment, navController.currentDestination?.id)
    }
}
```

### Pattern 4: File I/O Testing with Temporary Directories

**What:** Test PdfUtils and file-based operations using InstrumentationRegistry's targetContext for real filesystem access.
**When to use:** PdfUtils merge/split/compress tests, signature save/load tests.
**Trade-offs:** Needs device, creates real files. Must clean up in @After.

**Example:**
```kotlin
@RunWith(AndroidJUnit4::class)
class PdfUtilsInstrumentedTest {
    private lateinit var context: Context
    private lateinit var testDir: File

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.cacheDir, "test_pdfs").apply { mkdirs() }
    }

    @After
    fun cleanup() {
        testDir.deleteRecursively()
    }

    @Test
    fun mergePdfs_withEmptyList_returnsFailure() = runBlocking {
        val result = PdfUtils.mergePdfs(context, emptyList())
        assertFalse(result.success)
    }

    @Test
    fun formatFileSize_formatsCorrectly() {
        assertEquals("500 B", PdfUtils.formatFileSize(500))
        assertEquals("1 KB", PdfUtils.formatFileSize(1024))
        assertEquals("1.5 MB", PdfUtils.formatFileSize(1572864))
    }
}
```

## Build Configuration: New Files and Modifications

### JaCoCo Integration in build.gradle.kts

**What changes:** Two modifications to `app/build.gradle.kts`:
1. Enable unit test coverage in the debug build type.
2. Register a custom `jacocoTestReport` task.

**Why a custom task is needed:** The Android Gradle Plugin's built-in JaCoCo integration produces `.exec` files but does not generate the HTML/XML report automatically. A custom task assembles the report from execution data and source directories.

**Modified section of `app/build.gradle.kts`:**
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
    id("jacoco")  // Add JaCoCo plugin
}

android {
    // ... existing config unchanged ...

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            enableUnitTestCoverage = true        // New: replaces deprecated isTestCoverageEnabled
            enableAndroidTestCoverage = true     // New: for instrumented test coverage
        }
        release {
            // ... unchanged ...
        }
    }
}

// JaCoCo report task registered AFTER the android {} block
// so Android Gradle Plugin task outputs are available
tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
    }

    // Exclusions: generated code that should not count toward coverage targets
    val excludes = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*_Impl*",               // Room DAO implementations (none currently, future-proof)
        "**/databinding/**",
        "**/*Args*",                // Navigation Safe Args generated classes
        "**/*Directions*",          // Navigation Safe Args generated classes
    )

    val javaClasses = fileTree(layout.buildDirectory.dir("intermediates/javac/debug")) {
        exclude(excludes)
    }
    val kotlinClasses = fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude(excludes)
    }

    classDirectories.setFrom(files(javaClasses, kotlinClasses))
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include("jacoco/testDebugUnitTest.exec")
        }
    )
}
```

**Coverage targets from requirements:**
- `util/` package: 70% line coverage (ImageProcessor, PdfUtils format methods, AppPreferences)
- `viewmodel/` package: 50% line coverage (ScannerViewModel)

**How to run:**
```bash
./gradlew testDebugUnitTest jacocoTestReport
# Report: app/build/reports/jacoco/html/index.html
```

### Detekt Integration

**What:** Two new files + one modification to root `build.gradle.kts`.

**New file: `app/detekt.yml`** — Detekt rule configuration for this project.

The key principle: rules ship enabled by default in Detekt. The config file selectively disables rules that conflict with the codebase's established patterns.

```yaml
# app/detekt.yml
# Rules not listed here use Detekt defaults (active: true where applicable)

style:
  MagicNumber:
    active: true
    ignoreNumbers: ['-1', '0', '1', '2', '100', '255', '1024']  # Common in image math
    ignoreConstantDeclaration: true
    ignoreEnums: true
  MaxLineLength:
    active: true
    maxLineLength: 120         # Matches Android Studio default
  WildcardImport:
    active: true
    excludeImports: []

complexity:
  LongMethod:
    active: true
    threshold: 60              # Some filter functions are inherently long
  CyclomaticComplexMethod:
    active: true
    threshold: 15

naming:
  FunctionNaming:
    active: true
    functionPattern: '[a-z][a-zA-Z0-9]*'   # Allows existing camelCase names

formatting:
  # detekt-formatting delegates to ktlint internally
  # Enable the rules you care about:
  Indentation:
    active: true
    indentSize: 4
  NoWildcardImports:
    active: true
  MaximumLineLength:
    active: true
    maxLineLength: 120
```

**Baseline approach: generate first, enforce new violations only.**

The baseline mechanism works as follows:
1. Run `./gradlew detektBaseline` — Detekt analyzes all existing code and writes every current violation into `app/detekt-baseline.xml`.
2. On every subsequent `./gradlew detekt` run, violations present in the baseline are silently ignored. Only violations NOT in the baseline (i.e., new code violations) cause build failure.
3. The baseline file must be committed to version control. It is the "debt snapshot" of existing code.
4. When a baseline violation is fixed, re-running `./gradlew detektBaseline` regenerates the file without that entry.

**Modified `build.gradle.kts` (root level):**
```kotlin
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("androidx.navigation.safeargs.kotlin") version "2.7.6" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false  // Add Detekt
}
```

**Modified `app/build.gradle.kts` (add plugin and configure):**
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("androidx.navigation.safeargs.kotlin")
    id("jacoco")
    id("io.gitlab.arturbosch.detekt")          // Add Detekt
}

// After the android {} block:
detekt {
    config.setFrom("$projectDir/detekt.yml")
    baseline = file("$projectDir/detekt-baseline.xml")
    buildUponDefaultConfig = true              // Layer on top of Detekt defaults

    // Include detekt-formatting (ktlint rules)
    dependencies {
        detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
    }
}
```

**Workflow for Phase 5 (RELEASE-01):**
```bash
# Step 1: Generate baseline for all existing code violations
./gradlew detektBaseline
# -> creates app/detekt-baseline.xml, commit this file

# Step 2: From now on, detekt enforces zero NEW violations
./gradlew detekt
# -> passes (existing violations suppressed by baseline)
# -> fails if new code introduces violations not in baseline
```

**Important:** Do NOT run `detektBaseline` on a CI system that blocks merges. Run it once locally, commit the result, and thereafter only run `detekt`.

### ProGuard / R8 Rules

**What changes:** `app/proguard-rules.pro` is modified (it currently has minimal rules for CameraX and CanHub).

**Existing file (for reference):**
```
-keep class androidx.camera.** { *; }
-keep class com.canhub.cropper.** { *; }
```

**Complete updated `app/proguard-rules.pro`:**

```proguard
# =============================================================
# EXISTING RULES (kept)
# =============================================================

# Keep CameraX classes
-keep class androidx.camera.** { *; }

# Keep CanHub Image Cropper
-keep class com.canhub.cropper.** { *; }

# =============================================================
# ML KIT TEXT RECOGNITION (com.google.mlkit:text-recognition)
# =============================================================
# ML Kit text recognition bundles its model in the APK.
# R8 must not strip ML Kit runtime classes.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# =============================================================
# ML KIT DOCUMENT SCANNER (play-services-mlkit-document-scanner)
# =============================================================
# GMS-based scanner — the AAR includes consumer ProGuard rules,
# but explicit keep prevents issues with dynamic feature loading.
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# =============================================================
# NAVIGATION COMPONENT + SAFE ARGS
# =============================================================
# Safe Args generates *Directions and *Args classes at build time.
# R8 must not rename these because they are referenced by the
# navigation graph XML at runtime via reflection.
#
# The generated classes follow the pattern:
#   com.pdfscanner.app.ui.*Directions
#   com.pdfscanner.app.ui.*FragmentArgs
#   com.pdfscanner.app.editor.*Directions
#
# Keep all generated Args and Directions classes by name.
-keepnames class com.pdfscanner.app.** implements androidx.navigation.NavArgs
-keep class com.pdfscanner.app.**Args { *; }
-keep class com.pdfscanner.app.**Directions { *; }
-keep class com.pdfscanner.app.**Directions$* { *; }

# Navigation Component core — safe to keep entire package
-keep class androidx.navigation.** { *; }
-dontwarn androidx.navigation.**

# =============================================================
# KOTLIN / COROUTINES
# =============================================================
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# =============================================================
# SERIALIZATION / REFLECTION USED BY DocumentEntry
# =============================================================
# DocumentEntry uses org.json.JSONObject for serialization.
# org.json is part of Android SDK — no keep rules needed.
# If Gson or kotlinx.serialization is added later, add rules here.

# =============================================================
# DEBUG TOOLING -- STRIP FROM RELEASE
# =============================================================
# LeakCanary is debugImplementation only; no rules needed.
# The following removes any accidental Timber/Log.d calls.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# =============================================================
# USEFUL DIAGNOSTICS (uncomment when diagnosing R8 issues)
# =============================================================
# -printconfiguration build/outputs/logs/r8-configuration.txt
# -printseeds build/outputs/logs/r8-seeds.txt
# -printusage build/outputs/logs/r8-usage.txt
```

**Why Safe Args needs explicit rules:** The Navigation Safe Args plugin generates classes at compile time that are referenced by the navigation graph XML at runtime. R8 does not see these XML-to-class references during analysis and will strip or rename the classes unless explicitly kept. The `NavArgs` interface keep covers future Safe Args classes if new destinations are added.

**Why ML Kit GMS scanner needs explicit rules:** The `play-services-mlkit-document-scanner` library downloads its scanning model at runtime via Play Services. The AAR's consumer rules are bundled, but the `-keep class com.google.android.gms.**` guard prevents edge cases where GMS service class names are obfuscated, which breaks the dynamic feature download.

### Android Lint Configuration

**New file: `app/lint.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <!-- Treat accessibility issues as errors (blocks release build) -->
    <issue id="ContentDescription" severity="error" />
    <issue id="LabelFor" severity="error" />
    <issue id="TouchTargetSizeCheck" severity="error" />

    <!-- Treat hardcoded text as errors (all text must be in strings.xml) -->
    <issue id="HardcodedText" severity="error" />

    <!-- Network security config warnings are errors -->
    <issue id="NetworkSecurityConfig" severity="error" />

    <!-- Baseline lint for known existing issues -->
    <!-- Run: ./gradlew lint and check lint-baseline.xml -->

    <!-- Suppress false positives in generated binding classes -->
    <issue id="UnusedResources">
        <ignore path="**/databinding/**" />
        <ignore path="**/R.java" />
    </issue>
</lint>
```

**Lint baseline** (separate from Detekt baseline): Run `./gradlew lintDebug` with `android.lintOptions { baseline = file("lint-baseline.xml") }` to generate a suppression file for existing lint warnings before enforcing new ones.

**Modified `app/build.gradle.kts` — android block addition:**
```kotlin
android {
    // ... existing config ...

    lint {
        lintConfig = file("lint.xml")
        abortOnError = true          // Build fails on lint errors
        checkReleaseBuilds = true    // Run lint on release variant
        baseline = file("lint-baseline.xml")  // Suppress pre-existing issues
    }
}
```

### LeakCanary Integration

**What:** One line addition to `app/build.gradle.kts` dependencies. No code changes needed — LeakCanary auto-installs via ContentProvider.

```kotlin
dependencies {
    // ... existing dependencies ...

    // LeakCanary -- debug builds only, no code changes needed
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}
```

**Verification approach:** After adding, run debug build on device, exercise all 8 fragment flows, then navigate away from app and return. LeakCanary will show a notification if retained leaks are detected. Zero leaks required before RELEASE-08 is marked complete.

## Data Flow: How Tests Map to App Flows

### Scan Flow (highest priority for testing)

```
[Camera Capture]     [ML Kit Document Scanner]     [Gallery Import]
       |                      |                          |
       v                      v                          v
  CameraFragment -----> ScannerViewModel.setCurrentCapture(uri)
                              |
                              v
                    PreviewFragment (filter selection)
                              |
                    ScannerViewModel.addPage(uri)
                    ScannerViewModel.setPageFilter(idx, type)
                              |
                              v
                    PagesFragment (reorder, multi-select)
                              |
                    ScannerViewModel.movePage(from, to)
                    ScannerViewModel.removePage(idx)
                              |
                              v
                    PDF Generation (in PagesFragment)
                              |
                    ScannerViewModel.setPdfUri(uri)
                              |
                              v
                    DocumentHistoryRepository.addDocument()
```

**Test coverage strategy for this flow:**
- Unit: ScannerViewModel page operations (add, remove, move, filter, clear)
- Unit: ScannerViewModel PDF naming (getPdfFileName)
- Integration: PreviewFragment filter UI selection triggers ViewModel state change
- Integration: PagesFragment RecyclerView displays correct page count
- Integration: DocumentHistoryRepository persists and retrieves entries
- E2E (manual): Full camera -> preview -> pages -> PDF -> history

### PDF Tools Flow

```
[Select PDF from History]
         |
         v
   PdfViewerFragment
         |
    [User selects tool]
         |
    +----+----+----+
    |    |    |    |
    v    v    v    v
  Merge Split Compress Edit
    |    |    |    |
    v    v    v    v
  PdfUtils.*()     PdfEditorFragment
    |                    |
    v                    v
  PdfOperationResult   PdfAnnotationRenderer.render()
    |                    |
    v                    v
  DocumentHistoryRepository.addDocument()
```

**Test coverage strategy for this flow:**
- Unit: PdfOperationResult construction and field access
- Unit: PdfUtils.formatFileSize
- Integration: PdfUtils.mergePdfs, splitPdf, compressPdf with real test PDF files
- Integration: PdfEditorViewModel annotation state management
- E2E (manual): Open PDF -> Edit -> Save -> Verify annotations rendered

## Build Order: What to Test First

Priority is based on: (1) risk of bugs, (2) ease of testing, (3) user-facing impact.

### Phase 1: Foundation -- Pure Unit Tests (no device needed)

1. **ScannerViewModel** -- highest value, easiest to test, core of the app. Uses `SavedStateHandle()` directly (no mocking).
2. **DocumentEntry** -- JSON round-trip, data integrity
3. **PdfUtils.formatFileSize** -- pure utility
4. **ScannerViewModel.getPdfFileName** -- naming logic

**Why first:** Zero infrastructure needed beyond JUnit + `androidx.arch.core:core-testing`. Establishes test patterns and CI baseline immediately.

### Phase 2: Robolectric Tests (JVM, Android stubs)

5. **ImageProcessor** -- filter functions with Bitmap operations
6. **DocumentHistoryRepository** -- SharedPreferences CRUD (use fresh context + clear prefs in @Before)
7. **PdfEditorViewModel** -- tool/page state management (Robolectric needed for AndroidViewModel)

**Why second:** Needs Robolectric dependency added but still runs on JVM. Covers the data/utility layer.

### Phase 3: Instrumented Integration Tests (device/emulator)

8. **PdfUtils** -- merge, split, compress with actual PDF files. Use test assets in `androidTest/assets/`.
9. **OcrProcessor** -- text recognition with test images (expect partial match on recognized text)
10. **Navigation flows** -- TestNavHostController for critical paths (Camera -> Preview -> Pages -> PDF)
11. **Fragment smoke tests** -- HomeFragment renders, buttons are clickable

**Why third:** Requires device/emulator, slower feedback loop. But covers the critical file I/O paths that are most likely to have bugs.

### Phase 4: Release Tooling (after all tests pass)

12. **Detekt baseline**: Run `./gradlew detektBaseline`, commit `detekt-baseline.xml`
13. **Detekt enforcement**: Run `./gradlew detekt` — zero new violations
14. **Lint baseline**: Run `./gradlew lintDebug` with baseline configured
15. **ProGuard**: Build release APK, smoke test all 8 flows on real device
16. **LeakCanary**: Exercise all flows in debug build, verify zero retained leaks

**Why last:** Release tooling depends on test suite being stable. ProGuard rules should only be validated against a working, well-tested app.

## Integration Points: New vs Modified Files

### New Files

| File | Purpose | Created By |
|------|---------|------------|
| `app/src/test/java/com/pdfscanner/app/viewmodel/ScannerViewModelTest.kt` | ViewModel unit tests | Phase 4 |
| `app/src/test/java/com/pdfscanner/app/data/DocumentEntryTest.kt` | JSON round-trip tests | Phase 4 |
| `app/src/test/java/com/pdfscanner/app/util/ImageProcessorTest.kt` | Filter tests (Robolectric) | Phase 4 |
| `app/src/test/java/com/pdfscanner/app/data/DocumentHistoryRepositoryTest.kt` | Repository CRUD (Robolectric) | Phase 4 |
| `app/src/androidTest/java/com/pdfscanner/app/util/PdfUtilsInstrumentedTest.kt` | PDF ops on device | Phase 4 |
| `app/src/androidTest/java/com/pdfscanner/app/navigation/NavigationFlowTest.kt` | Navigation flow tests | Phase 4 |
| `app/src/androidTest/java/com/pdfscanner/app/ui/HomeFragmentTest.kt` + 4 more | Fragment smoke tests | Phase 4 |
| `app/src/androidTest/assets/test_single_page.pdf` | Test fixture | Phase 4 |
| `app/src/androidTest/assets/test_multi_page.pdf` | Test fixture | Phase 4 |
| `app/detekt.yml` | Detekt rule config | Phase 5 |
| `app/detekt-baseline.xml` | Generated by detektBaseline task | Phase 5 |
| `app/lint.xml` | Lint rule config | Phase 5 |
| `app/lint-baseline.xml` | Generated by lintDebug task | Phase 5 |

### Modified Files

| File | What Changes | Phase |
|------|-------------|-------|
| `app/build.gradle.kts` | Add: JaCoCo plugin + enableUnitTestCoverage, Detekt plugin + config, LeakCanary debugImplementation, lint config, MockK + Robolectric + fragment-testing + navigation-testing dependencies | Phase 4-5 |
| `build.gradle.kts` (root) | Add: Detekt plugin declaration | Phase 5 |
| `app/proguard-rules.pro` | Add: ML Kit rules, Safe Args rules, Navigation rules, Log stripping | Phase 5 |

## Anti-Patterns to Avoid

### Anti-Pattern 1: Testing CameraX Capture in Automated Tests

**What people do:** Try to automate camera capture with Espresso/UI Automator.
**Why it's wrong:** CameraX requires real camera hardware, emulator cameras are unreliable, tests become flaky. ML Kit Document Scanner launches its own Activity which cannot be controlled by Espresso.
**Do this instead:** Test CameraFragment UI state (permission handling, button visibility) but NOT actual capture. Use test fixture images for everything downstream of capture.

### Anti-Pattern 2: Testing LiveData Without InstantTaskExecutorRule

**What people do:** Observe LiveData in tests without making the executor synchronous.
**Why it's wrong:** LiveData posts to main thread; without the rule, assertions run before values are set.
**Do this instead:** Always add `@get:Rule val instantExecutorRule = InstantTaskExecutorRule()` in ViewModel tests. This is satisfied by `androidx.arch.core:core-testing`.

### Anti-Pattern 3: Heavyweight Integration Tests for Pure Logic

**What people do:** Run ImageProcessor filter tests as androidTest on device because Bitmap is an Android class.
**Why it's wrong:** Device tests are 10-50x slower than JVM tests. Robolectric shadows Bitmap adequately for these tests.
**Do this instead:** Use Robolectric for Bitmap/Canvas operations. Only use device tests for PdfRenderer/PdfDocument which Robolectric does NOT shadow well.

### Anti-Pattern 4: Trying to Unit Test Singleton Objects Directly

**What people do:** Test OcrProcessor or DocumentScanner objects that hold static state.
**Why it's wrong:** Singletons leak state between tests. The lazy `recognizer` in OcrProcessor persists across test methods.
**Do this instead:** For OcrProcessor, test on device where ML Kit runtime is available. For DocumentHistoryRepository, use `getInstance()` with fresh context per test and clear SharedPreferences in @Before.

### Anti-Pattern 5: Testing Navigation by Checking Fragment Transactions

**What people do:** Assert that FragmentManager contains specific fragments after navigation.
**Why it's wrong:** Navigation Component manages the back stack internally. Checking fragment transactions is brittle.
**Do this instead:** Use `TestNavHostController` and assert `navController.currentDestination?.id` equals expected destination ID.

### Anti-Pattern 6: Running detektBaseline in CI

**What people do:** Add `detektBaseline` to the CI pipeline.
**Why it's wrong:** CI regenerating the baseline would silently suppress new violations that were introduced in the branch.
**Do this instead:** Run `detektBaseline` once locally (Phase 5 setup), commit `detekt-baseline.xml`, and run only `detekt` in CI. The baseline file is immutable until a developer intentionally regenerates it.

### Anti-Pattern 7: Applying JaCoCo to Release Builds

**What people do:** Enable `enableUnitTestCoverage = true` in the release build type.
**Why it's wrong:** Coverage instrumentation adds overhead to the build and can interfere with R8 optimization.
**Do this instead:** Only enable coverage on the debug build type as shown above.

### Anti-Pattern 8: Keeping ALL Safe Args Classes With Wildcard

**What people do:** `-keep class com.pdfscanner.app.** { *; }` to avoid thinking about Safe Args.
**Why it's wrong:** This defeats R8's dead code elimination entirely for your own package, bloating the APK and negating obfuscation.
**Do this instead:** Keep only the Safe Args interfaces and generated class name patterns (as shown in the proguard-rules.pro above). Let R8 optimize all other classes.

## Required Dependencies for Testing

Add to `app/build.gradle.kts` dependencies block:

```kotlin
dependencies {
    // === EXISTING (already present) ===
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // === NEW: Unit test dependencies ===
    testImplementation("androidx.arch.core:core-testing:2.2.0")             // InstantTaskExecutorRule
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3") // TestCoroutineDispatcher
    testImplementation("org.robolectric:robolectric:4.11.1")               // Android stubs on JVM
    testImplementation("androidx.test:core:1.5.0")                         // ApplicationProvider for Robolectric
    testImplementation("io.mockk:mockk:1.13.10")                           // Mocking for Kotlin (prefer MockK over Mockito in Kotlin codebases)

    // === NEW: Instrumented test dependencies ===
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1")  // RecyclerView actions
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1")  // Intent verification
    androidTestImplementation("androidx.fragment:fragment-testing:1.6.2")        // FragmentScenario
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.6")    // TestNavHostController
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("io.mockk:mockk-android:1.13.10")

    // === NEW: Release quality tooling ===
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // Detekt formatting plugin is configured via detekt { } block, not dependencies { }
}
```

**Dependency version notes:**
- Robolectric 4.11.1: Compatible with SDK 34 and Kotlin 1.9. Does NOT shadow PdfRenderer — use androidTest for PdfUtils tests.
- MockK 1.13.10: Kotlin-native mock library. Preferred over Mockito-Kotlin in pure Kotlin codebases.
- fragment-testing 1.6.2: Must match the `androidx.fragment:fragment-ktx:1.6.2` version already in the project.
- navigation-testing 2.7.6: Must match `navigation-fragment-ktx:2.7.6` already in the project.
- LeakCanary 2.14: Current stable as of early 2026. Requires no code changes — auto-installs via ContentProvider.

## Scalability Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Current (0 tests) | Add test infrastructure, write foundational tests for ViewModels and utilities |
| 30-50 tests | Run unit tests in CI on every commit. Run instrumented tests nightly or on PR. |
| 50-100 tests | Consider Gradle test sharding for instrumented tests. Add screenshot testing for UI regression. |

### Scaling Priorities

1. **First bottleneck:** Instrumented test execution time. PdfUtils tests create real files and are slow. Mitigation: Use `@SmallTest`/`@MediumTest`/`@LargeTest` annotations to run subsets.
2. **Second bottleneck:** Flaky emulator tests. CameraX and ML Kit tests may fail intermittently on CI emulators. Mitigation: Tag these as `@FlakyTest` and run separately with retry logic.

## External Services Integration

| Service | Integration Pattern | Test Strategy |
|---------|---------------------|---------------|
| CameraX | Camera lifecycle bound to Fragment | Do NOT automate capture. Test Fragment UI state only. Use fixture images. |
| ML Kit Text Recognition | Singleton TextRecognizer, suspend + await | Integration test on device with known test image containing text. Assert recognized text contains expected substrings. |
| ML Kit Document Scanner | GMS Intent-based API | NOT automatically testable. Launches separate Activity. Manual testing only. |
| CanHub Image Cropper | Activity result contract | NOT automatically testable in isolation. Test that crop result URIs are correctly handled in PreviewFragment. |
| Android PdfRenderer | ParcelFileDescriptor from ContentResolver | Integration test with test PDF in androidTest assets. Verify page count, rendered bitmap dimensions. |
| SharedPreferences | Context.getSharedPreferences | Robolectric for unit tests, real device for integration. Clear in @Before. |
| FileProvider | Manifest-configured, shares files via content:// URI | Integration test: verify share Intent contains correct URI and MIME type. |

## Internal Boundaries

| Boundary | Communication | Test Implications |
|----------|---------------|-------------------|
| Fragment <-> ScannerViewModel | LiveData observation, method calls | Test ViewModel in isolation (unit). Test Fragment observes correctly (integration). |
| Fragment <-> PdfEditorViewModel | LiveData observation, method calls | Same as above. PdfEditorViewModel needs Application context — use Robolectric or androidTest. |
| Fragment <-> Navigation | NavController.navigate() with Safe Args | Test with TestNavHostController, assert destination IDs. |
| PagesFragment <-> PdfUtils | Direct suspend call from Fragment coroutine scope | Test PdfUtils in isolation (integration). Fragment tests can mock ViewModel state. |
| HistoryFragment <-> DocumentHistoryRepository | Direct instantiation via getInstance() | Test Repository in isolation. Fragment tests verify list rendering. |
| PdfEditorFragment <-> AnnotationCanvasView | Direct view reference, method calls | Tightly coupled. Test as unit at Fragment level. |

## Sources

- Android developer documentation on testing (developer.android.com/training/testing) -- HIGH confidence
- [Detekt baseline documentation](https://detekt.dev/docs/introduction/baseline/) -- HIGH confidence (established API, stable across 1.x)
- [Detekt Gradle plugin docs](https://detekt.dev/docs/gettingstarted/gradle/) -- HIGH confidence
- [JaCoCo Gradle plugin documentation](https://docs.gradle.org/current/userguide/jacoco_plugin.html) -- HIGH confidence
- [JaCoCo with Kotlin DSL (Medium)](https://medium.com/@ranjeetsinha/jacoco-with-kotlin-dsl-f1f067e42cd0) -- MEDIUM confidence (community article, verified against AGP docs)
- [enableUnitTestCoverage AGP API reference](https://developer.android.com/reference/tools/gradle-api/8.1/com/android/build/api/dsl/BuildType) -- HIGH confidence (official)
- [LeakCanary Getting Started](https://square.github.io/leakcanary/getting_started/) -- HIGH confidence (official Square docs)
- [Navigation Safe Args ProGuard (Droid on Roids)](https://www.thedroidsonroids.com/blog/how-to-generate-proguard-r8-rules-for-navigation-component-arguments) -- MEDIUM confidence (established pattern, widely referenced)
- [ProGuard/R8 Rules 2025 (Android Developers Blog)](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html) -- HIGH confidence (official Google blog)
- Codebase analysis of all 31 Kotlin source files in this project -- HIGH confidence

---
*Architecture research for: Android Document Scanner Test Suite + Release Tooling*
*Researched: 2026-03-01*
