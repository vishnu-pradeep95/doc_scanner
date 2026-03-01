# Architecture Research: Test Suite Structure for Android Document Scanner

**Domain:** Android app testing -- adding test coverage to existing MVVM app
**Researched:** 2026-02-28
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
| ScannerViewModel | Shared state: scanned pages list, current capture URI, PDF URI, loading state, filter state | Plain ViewModel with MutableLiveData, no Android framework deps beyond Uri |
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

```
app/src/
  test/                                          # JVM unit tests
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
      adapter/
        PagesAdapterTest.kt                      # Data binding verification

  androidTest/                                   # Instrumented tests
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
```

## Architectural Patterns for Testing

### Pattern 1: ViewModel Unit Testing with InstantTaskExecutorRule

**What:** Test ScannerViewModel on JVM using AndroidX test utilities that make LiveData synchronous.
**When to use:** All ViewModel tests.
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
        viewModel = ScannerViewModel()
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
**Trade-offs:** Slower than pure JVM tests (~3-5s startup) but much faster than device tests. Not 100% faithful to real Android behavior for Canvas operations.

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
}
```

### Pattern 3: Fragment Testing with FragmentScenario

**What:** Launch fragments in isolation with mock or real NavController to test UI behavior.
**When to use:** All Fragment integration tests.
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

1. **ScannerViewModel** -- highest value, easiest to test, core of the app
2. **DocumentEntry** -- JSON round-trip, data integrity
3. **PdfUtils.formatFileSize** -- pure utility
4. **ScannerViewModel.getPdfFileName** -- naming logic
5. **PdfEditorViewModel** -- tool/page state management (with Robolectric)

**Why first:** Zero infrastructure needed beyond JUnit + `androidx.arch.core:core-testing`. Establishes test patterns and CI baseline immediately.

### Phase 2: Robolectric Tests (JVM, Android stubs)

6. **ImageProcessor** -- filter functions with Bitmap operations
7. **DocumentHistoryRepository** -- SharedPreferences CRUD
8. **AppPreferences** -- settings persistence

**Why second:** Needs Robolectric dependency added but still runs on JVM. Covers the data/utility layer.

### Phase 3: Instrumented Integration Tests (device/emulator)

9. **PdfUtils** -- merge, split, compress with actual PDF files
10. **OcrProcessor** -- text recognition with test images
11. **Navigation flows** -- TestNavHostController for critical paths
12. **Fragment smoke tests** -- HomeFragment renders, buttons are clickable

**Why third:** Requires device/emulator, slower feedback loop. But covers the critical file I/O paths that are most likely to have bugs.

### Phase 4: UI and E2E Tests

13. **Fragment interaction tests** -- PreviewFragment filter buttons, PagesFragment drag
14. **PdfEditorFragment** -- tool selection, annotation drawing
15. **End-to-end flows** -- manual test scripts for camera + full pipeline

**Why last:** Most fragile, slowest, most maintenance. Build after the foundation is solid.

## Anti-Patterns to Avoid

### Anti-Pattern 1: Testing CameraX Capture in Automated Tests

**What people do:** Try to automate camera capture with Espresso/UI Automator.
**Why it's wrong:** CameraX requires real camera hardware, emulator cameras are unreliable, tests become flaky. ML Kit Document Scanner launches its own Activity which cannot be controlled by Espresso.
**Do this instead:** Test CameraFragment UI state (permission handling, button visibility) but NOT actual capture. Use test fixture images for everything downstream of capture.

### Anti-Pattern 2: Testing LiveData Without InstantTaskExecutorRule

**What people do:** Observe LiveData in tests without making the executor synchronous.
**Why it's wrong:** LiveData posts to main thread; without the rule, assertions run before values are set.
**Do this instead:** Always add `@get:Rule val instantExecutorRule = InstantTaskExecutorRule()` in ViewModel tests. Add `lifecycle-runtime-testing` for lifecycle-aware testing.

### Anti-Pattern 3: Heavyweight Integration Tests for Pure Logic

**What people do:** Run ImageProcessor filter tests as androidTest on device because Bitmap is an Android class.
**Why it's wrong:** Device tests are 10-50x slower than JVM tests. Robolectric shadows Bitmap adequately for these tests.
**Do this instead:** Use Robolectric for Bitmap/Canvas operations. Only use device tests for PdfRenderer/PdfDocument which Robolectric does NOT shadow well.

### Anti-Pattern 4: Trying to Unit Test Singleton Objects Directly

**What people do:** Test OcrProcessor or DocumentScanner objects that hold static state.
**Why it's wrong:** Singletons leak state between tests. The lazy `recognizer` in OcrProcessor persists across test methods.
**Do this instead:** For OcrProcessor, test on device where ML Kit runtime is available. Accept that singleton objects with hardware dependencies are integration-test territory. For DocumentHistoryRepository, use `getInstance()` with fresh context per test and clear SharedPreferences in @Before.

### Anti-Pattern 5: Testing Navigation by Checking Fragment Transactions

**What people do:** Assert that FragmentManager contains specific fragments after navigation.
**Why it's wrong:** Navigation Component manages the back stack internally. Checking fragment transactions is brittle.
**Do this instead:** Use `TestNavHostController` and assert `navController.currentDestination?.id` equals expected destination ID.

## Integration Points

### External Services

| Service | Integration Pattern | Test Strategy |
|---------|---------------------|---------------|
| CameraX | Camera lifecycle bound to Fragment | Do NOT automate capture. Test Fragment UI state only. Use fixture images. |
| ML Kit Text Recognition | Singleton TextRecognizer, suspend + await | Integration test on device with known test image containing text. Assert recognized text contains expected substrings. |
| ML Kit Document Scanner | GMS Intent-based API | NOT automatically testable. Launches separate Activity. Manual testing only. |
| CanHub Image Cropper | Activity result contract | NOT automatically testable in isolation. Test that crop result URIs are correctly handled in PreviewFragment. |
| Android PdfRenderer | ParcelFileDescriptor from ContentResolver | Integration test with test PDF in androidTest assets. Verify page count, rendered bitmap dimensions. |
| SharedPreferences | Context.getSharedPreferences | Robolectric for unit tests, real device for integration. Clear in @Before. |
| FileProvider | Manifest-configured, shares files via content:// URI | Integration test: verify share Intent contains correct URI and MIME type. |

### Internal Boundaries

| Boundary | Communication | Test Implications |
|----------|---------------|-------------------|
| Fragment <-> ScannerViewModel | LiveData observation, method calls | Test ViewModel in isolation (unit). Test Fragment observes correctly (integration). |
| Fragment <-> PdfEditorViewModel | LiveData observation, method calls | Same as above. PdfEditorViewModel needs Application context. |
| Fragment <-> Navigation | NavController.navigate() with Safe Args | Test with TestNavHostController, assert destination IDs. |
| PagesFragment <-> PdfUtils | Direct suspend call from Fragment coroutine scope | Test PdfUtils in isolation (integration). Fragment tests can mock ViewModel state. |
| HistoryFragment <-> DocumentHistoryRepository | Direct instantiation via getInstance() | Test Repository in isolation. Fragment tests verify list rendering. |
| PdfEditorFragment <-> AnnotationCanvasView | Direct view reference, method calls | Tightly coupled. Test as unit at Fragment level. |

## Required Dependencies for Testing

```groovy
// build.gradle.kts additions needed

// Unit test dependencies
testImplementation("junit:junit:4.13.2")                          // Already present
testImplementation("androidx.arch.core:core-testing:2.2.0")       // InstantTaskExecutorRule
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3") // TestCoroutineDispatcher
testImplementation("org.robolectric:robolectric:4.11.1")          // Android stubs on JVM
testImplementation("androidx.test:core:1.5.0")                    // ApplicationProvider for Robolectric
testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")     // Mocking (minimal use)
testImplementation("com.google.truth:truth:1.1.5")                // Fluent assertions

// Instrumented test dependencies
androidTestImplementation("androidx.test.ext:junit:1.1.5")        // Already present
androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")  // Already present
androidTestImplementation("androidx.test.espresso:espresso-contrib:3.5.1") // RecyclerView actions
androidTestImplementation("androidx.test.espresso:espresso-intents:3.5.1") // Intent verification
androidTestImplementation("androidx.fragment:fragment-testing:1.6.2")     // FragmentScenario
androidTestImplementation("androidx.navigation:navigation-testing:2.7.6") // TestNavHostController
androidTestImplementation("androidx.test:runner:1.5.2")           // AndroidJUnitRunner
androidTestImplementation("androidx.test:rules:1.5.0")            // Test rules
androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
androidTestImplementation("com.google.truth:truth:1.1.5")
```

## Scalability Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Current (0 tests) | Add test infrastructure, write foundational tests for ViewModels and utilities |
| 30-50 tests | Run unit tests in CI on every commit. Run instrumented tests nightly or on PR. |
| 50-100 tests | Consider Gradle test sharding for instrumented tests. Add screenshot testing for UI regression. |

### Scaling Priorities

1. **First bottleneck:** Instrumented test execution time. PdfUtils tests create real files and are slow. Mitigation: Use `@SmallTest`/`@MediumTest`/`@LargeTest` annotations to run subsets.
2. **Second bottleneck:** Flaky emulator tests. CameraX and ML Kit tests may fail intermittently on CI emulators. Mitigation: Tag these as `@FlakyTest` and run separately with retry logic.

## Sources

- Android developer documentation on testing (developer.android.com/training/testing) -- HIGH confidence
- AndroidX Test library APIs (InstantTaskExecutorRule, FragmentScenario, TestNavHostController) -- HIGH confidence, based on established APIs present in the project's existing dependencies
- Robolectric project documentation -- HIGH confidence for Bitmap/SharedPreferences shadowing; MEDIUM confidence for exact version compatibility claims
- Codebase analysis of all 31 Kotlin source files in this project -- HIGH confidence

---
*Architecture research for: Android Document Scanner Test Suite*
*Researched: 2026-02-28*
