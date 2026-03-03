# Phase 4: Test Coverage - Research

**Researched:** 2026-03-01
**Domain:** Android testing infrastructure — retrofitting a test suite onto a feature-complete MVVM app
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| TEST-01 | Add all test dependencies to build.gradle.kts; configure JaCoCo with LINE counter and generated-class exclusions; establish MainDispatcherRule | Complete build.gradle.kts diff in Standard Stack section; JaCoCo task template in Architecture section |
| TEST-02 | 15+ ScannerViewModel unit tests covering page CRUD, filter state, PDF naming (InstantTaskExecutorRule + UnconfinedTestDispatcher + runTest) | ViewModel test pattern confirmed; SavedStateHandle works on JVM directly without mocking |
| TEST-03 | DocumentEntry JSON round-trip — all fields preserved (pure JVM test, no Android dependencies) | DocumentEntry uses org.json.JSONObject; pure JVM, no Robolectric needed; toJson/fromJson already implemented |
| TEST-04 | 8+ ImageProcessor filter tests via Robolectric with FakeOcrProcessor interface for ML Kit boundary | OcrProcessor is a concrete `object` (not interface) — refactor step required; Bitmap tests via Robolectric confirmed |
| TEST-05 | 8+ DocumentHistoryRepository CRUD tests via Robolectric covering all operations | Repository takes Context; SharedPreferences fully supported by Robolectric; getAllDocuments() filters by file.exists() which needs temp file setup |
| TEST-07 | 5+ fragment smoke tests via FragmentScenario across non-camera fragments (stretch) | Fragment-testing 1.8.9 confirmed; theme must be passed to launchFragmentInContainer; CameraFragment must stay in androidTest |
| TEST-08 | Navigation flow test using TestNavHostController (stretch) | TestNavHostController pattern documented; scope should be limited to nav action firing, not full camera capture |
| RELEASE-09 | JaCoCo coverage report with LINE coverage 70% for util/, 50% for viewmodel/ | JaCoCo task template complete; exclusion list identified; command is ./gradlew testDebugUnitTest jacocoTestReport |
</phase_requirements>

---

## Summary

Phase 4 adds a test suite to a zero-test codebase. The app is architecturally sound for testing: `ScannerViewModel` takes only `SavedStateHandle` (works on JVM), `DocumentEntry` uses `org.json` (pure JVM), and `ImageProcessor` is a pure-functions object (needs Robolectric for Bitmap, not ML Kit). No structural refactoring is needed except for one critical boundary: `OcrProcessor` is currently a concrete `object`, not an interface. TEST-04 requires wrapping ML Kit behind a testable interface before any `ImageProcessor` Robolectric tests can be written.

The test execution environment splits cleanly into three tiers. Pure JVM tests (TEST-02, TEST-03) run on any machine including WSL2 with no setup beyond Gradle. Robolectric tests (TEST-04, TEST-05) also run on JVM but require `isIncludeAndroidResources = true` in `testOptions` and the `@RunWith(RobolectricTestRunner::class)` annotation. Fragment and navigation tests (TEST-07, TEST-08) require a device or emulator and live in `androidTest/` — they cannot be Robolectric. JaCoCo (RELEASE-09) is built into AGP and is activated by `enableUnitTestCoverage = true`; no third-party plugin is needed.

The five failure modes most likely to waste time: missing `InstantTaskExecutorRule` (all LiveData assertions return null silently), using the deprecated `TestCoroutineDispatcher` API (passes locally, fails under CI ordering), missing JaCoCo generated-class exclusions (coverage numbers are 10–20% lower than reality), attempting CameraX or ML Kit in Robolectric (crashes with `UnsatisfiedLinkError`), and forgetting to resolve the OcrProcessor interface gap before writing TEST-04 (makes the entire test class impossible to execute on JVM).

**Primary recommendation:** Execute plans in strict order — TEST-01 (scaffold) then TEST-02/TEST-03 (JVM) then TEST-04/TEST-05 (Robolectric) then RELEASE-09 (coverage check) then TEST-07/TEST-08 (instrumented, stretch). Wire JaCoCo configuration into TEST-01 so coverage is measurable from the first test run.

---

## Standard Stack

### Core (testImplementation)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| JUnit 4 | 4.13.2 | Test framework | Already present. JUnit 5 adds `android-junit5` plugin friction with no benefit. Keep JUnit 4. |
| MockK | 1.14.7 | Kotlin-first mocking | Handles `suspend fun`, final classes (Kotlin default), sealed classes natively. Mockito cannot mock Kotlin final classes without `mockito-inline` workaround. |
| kotlinx-coroutines-test | 1.7.3 | Coroutine test dispatchers | MUST match app's `kotlinx-coroutines-android` version (both 1.7.3). Provides `runTest`, `UnconfinedTestDispatcher`. |
| androidx.arch.core:core-testing | 2.2.0 | LiveData synchronization | Provides `InstantTaskExecutorRule` — makes LiveData post synchronously. Required for every ViewModel test class. |
| Robolectric | 4.16 | Android APIs on JVM | Enables Bitmap, SharedPreferences, Context in unit tests. Supports compileSdk 35. |
| androidx.test:core-ktx | 1.6.1 | Robolectric support utilities | Provides `ApplicationProvider` for Robolectric tests. |
| com.google.truth:truth | 1.4.4 | Fluent assertions | Clearer failure messages than JUnit asserts. Standard in Android/Google ecosystem. |

### Instrumented (androidTestImplementation — stretch goals TEST-07, TEST-08)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| androidx.test.ext:junit-ktx | 1.3.0 | JUnit AndroidX runner | All instrumented tests; supersedes junit:1.1.5 |
| androidx.test:runner | 1.6.2 | Test runner | Required for AndroidJUnitRunner |
| androidx.test:core-ktx | 1.6.1 | Test utilities | ApplicationProvider, ActivityScenario |
| androidx.test:rules | 1.6.1 | JUnit rules | GrantPermissionRule for camera permission |
| espresso-core | 3.7.0 | UI interactions | Fragment smoke tests; auto-synchronizes with UI thread |
| espresso-intents | 3.7.0 | Intent verification | PDF share intent tests |
| espresso-contrib | 3.7.0 | RecyclerView actions | scrollToPosition, etc. |
| io.mockk:mockk-android | 1.14.7 | Mocking on device | Two artifacts required for Android instrumented tests |
| io.mockk:mockk-agent | 1.14.7 | MockK bytecode agent | Companion to mockk-android — both required |

### Fragment Testing (debugImplementation — for TEST-07)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| androidx.fragment:fragment-testing | 1.8.9 | Isolated fragment launching | `launchFragmentInContainer<T>()` — must be `debugImplementation` for manifest merge |

### What NOT to Use

| Avoid | Reason | Use Instead |
|-------|--------|-------------|
| JUnit 5 | Requires `android-junit5` plugin; instrumentation friction | JUnit 4 |
| Mockito | Cannot mock Kotlin final classes without `mockito-inline` | MockK 1.14.7 |
| `io.mockk:mockk` in androidTest | JVM-only artifact fails silently on device | `mockk-android` + `mockk-agent` |
| Third-party JaCoCo plugins (vanniktech, arturdm) | AGP 8.x bundles JaCoCo natively | AGP `enableUnitTestCoverage = true` |
| Robolectric for CameraX/ML Kit tests | Crashes with `UnsatisfiedLinkError` | Instrumented tests on device |
| Detekt 2.x | Alpha; incompatible with Kotlin 1.9.21 | Detekt 1.23.8 (Phase 5 only) |
| TestCoroutineDispatcher + runBlockingTest | Deprecated since coroutines-test 1.6; ordering bugs | `UnconfinedTestDispatcher` + `runTest` |
| Turbine | App uses LiveData, not Flow | `InstantTaskExecutorRule` + `observeForever` |
| Roborazzi / Paparazzi | Out of scope per PROJECT.md | Not applicable for v1.1 |

**Installation (full dependency diff for app/build.gradle.kts):**

```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            // ADD:
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
    }

    testOptions {
        unitTests {
            // ADD: Required for Robolectric to access Android resources
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // ===== UNIT TESTS (src/test/) =====
    testImplementation("junit:junit:4.13.2")                              // already present
    testImplementation("io.mockk:mockk:1.14.7")                          // ADD
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")  // ADD
    testImplementation("androidx.arch.core:core-testing:2.2.0")          // ADD
    testImplementation("org.robolectric:robolectric:4.16")               // ADD
    testImplementation("androidx.test:core-ktx:1.6.1")                  // ADD
    testImplementation("com.google.truth:truth:1.4.4")                   // ADD

    // ===== INSTRUMENTED TESTS (src/androidTest/) — stretch =====
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0")      // UPDATE from 1.1.5
    androidTestImplementation("androidx.test:runner:1.6.2")             // ADD
    androidTestImplementation("androidx.test:core-ktx:1.6.1")           // ADD
    androidTestImplementation("androidx.test:rules:1.6.1")              // ADD
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")     // UPDATE from 3.5.1
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")  // ADD
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.7.0")  // ADD
    androidTestImplementation("io.mockk:mockk-android:1.14.7")          // ADD
    androidTestImplementation("io.mockk:mockk-agent:1.14.7")            // ADD
    androidTestImplementation("com.google.truth:truth:1.4.4")           // ADD

    // ===== FRAGMENT TESTING — stretch =====
    debugImplementation("androidx.fragment:fragment-testing:1.8.9")     // ADD
}
```

---

## Architecture Patterns

### Recommended Project Structure

```
app/src/
  test/                                         # JVM unit tests  [NONE EXIST TODAY]
    java/com/pdfscanner/app/
      viewmodel/
        ScannerViewModelTest.kt                 # TEST-02 (15+ tests)
        MainDispatcherRule.kt                   # Shared rule for coroutine tests
      data/
        DocumentEntryTest.kt                    # TEST-03 (JSON round-trip, formattedSize)
        DocumentHistoryRepositoryTest.kt        # TEST-05 (Robolectric CRUD)
      util/
        ImageProcessorTest.kt                   # TEST-04 (Robolectric filter tests)

  androidTest/                                  # Instrumented tests  [NONE EXIST TODAY]
    java/com/pdfscanner/app/
      ui/
        HomeFragmentTest.kt                     # TEST-07 smoke test
        PreviewFragmentTest.kt                  # TEST-07 smoke test
        PagesFragmentTest.kt                    # TEST-07 smoke test
        HistoryFragmentTest.kt                  # TEST-07 smoke test
        SettingsFragmentTest.kt                 # TEST-07 smoke test
      navigation/
        NavigationFlowTest.kt                   # TEST-08 flow test

  main/java/com/pdfscanner/app/
    ocr/
      OcrProcessor.kt    [REFACTOR: convert object to interface — see Pattern 4 below]
      OcrProcessorImpl.kt  [NEW: production ML Kit implementation]
```

### Pattern 1: ViewModel Unit Test with InstantTaskExecutorRule + MainDispatcherRule

**What:** Test ScannerViewModel on JVM. `SavedStateHandle()` works directly on JVM — no mocking required.
**When to use:** Every ScannerViewModel test (TEST-02).

```kotlin
// src/test/.../viewmodel/MainDispatcherRule.kt
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description?) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description?) = Dispatchers.resetMain()
}

// src/test/.../viewmodel/ScannerViewModelTest.kt
@RunWith(JUnit4::class)
class ScannerViewModelTest {

    @get:Rule val instantExecutorRule = InstantTaskExecutorRule()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: ScannerViewModel

    @Before
    fun setup() {
        viewModel = ScannerViewModel(SavedStateHandle())
    }

    @After
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `addPage increases page count by one`() = runTest {
        val uri = Uri.parse("file:///test/scan1.jpg")
        viewModel.addPage(uri)
        assertEquals(1, viewModel.pages.value?.size)
    }

    @Test
    fun `movePage swaps positions`() = runTest {
        val uri1 = Uri.parse("file:///test/scan1.jpg")
        val uri2 = Uri.parse("file:///test/scan2.jpg")
        viewModel.addPage(uri1)
        viewModel.addPage(uri2)
        viewModel.movePage(0, 1)
        assertEquals(uri2, viewModel.pages.value?.get(0))
        assertEquals(uri1, viewModel.pages.value?.get(1))
    }

    @Test
    fun `removePage removes at correct index`() = runTest {
        val uri1 = Uri.parse("file:///test/scan1.jpg")
        val uri2 = Uri.parse("file:///test/scan2.jpg")
        viewModel.addPage(uri1)
        viewModel.addPage(uri2)
        viewModel.removePage(0)
        assertEquals(1, viewModel.pages.value?.size)
        assertEquals(uri2, viewModel.pages.value?.first())
    }

    @Test
    fun `setPageFilter records filter type for index`() = runTest {
        val uri = Uri.parse("file:///test/scan1.jpg")
        viewModel.addPage(uri)
        viewModel.setPageFilter(0, ImageProcessor.FilterType.ENHANCED)
        assertEquals(
            ImageProcessor.FilterType.ENHANCED,
            viewModel.pageFilters.value?.get(0)
        )
    }

    @Test
    fun `clearAllPages resets pages and filters`() = runTest {
        viewModel.addPage(Uri.parse("file:///test.jpg"))
        viewModel.setPageFilter(0, ImageProcessor.FilterType.BW)
        viewModel.clearAllPages()
        assertEquals(0, viewModel.pages.value?.size)
        assertTrue(viewModel.pageFilters.value?.isEmpty() ?: true)
    }

    @Test
    fun `getPdfFileName uses baseName when set`() {
        viewModel.setPdfBaseName("Meeting Notes")
        val result = viewModel.getPdfFileName("20260301_120000")
        assertEquals("Meeting Notes_20260301_120000.pdf", result)
    }

    @Test
    fun `getPdfFileName uses default when baseName is null`() {
        val result = viewModel.getPdfFileName("20260301_120000")
        assertThat(result).endsWith(".pdf")
        assertThat(result).contains("20260301_120000")
    }
}
```

### Pattern 2: DocumentEntry Pure JVM Test (TEST-03)

**What:** JSON serialization round-trip with no Android dependencies. `org.json.JSONObject` is part of the Android SDK but is available on the JVM test classpath via Robolectric's shadows — however, this test is pure enough to run without Robolectric at all since `org.json` is included in the JVM test classpath by AGP.
**When to use:** TEST-03.

```kotlin
// src/test/.../data/DocumentEntryTest.kt
@RunWith(JUnit4::class)
class DocumentEntryTest {

    private fun makeEntry(
        id: String = "1234",
        name: String = "My Scan",
        filePath: String = "/data/pdfs/my_scan.pdf",
        pageCount: Int = 3,
        createdAt: Long = 1740000000L,
        fileSize: Long = 512000L
    ) = DocumentEntry(id, name, filePath, pageCount, createdAt, fileSize)

    @Test
    fun `toJson and fromJson round-trips all fields`() {
        val original = makeEntry()
        val restored = DocumentEntry.fromJson(original.toJson())
        assertEquals(original, restored)
    }

    @Test
    fun `formattedSize returns bytes for values under 1024`() {
        val entry = makeEntry(fileSize = 500)
        assertEquals("500 B", entry.formattedSize())
    }

    @Test
    fun `formattedSize returns KB for values 1024 to 1048575`() {
        val entry = makeEntry(fileSize = 2048)
        assertEquals("2 KB", entry.formattedSize())
    }

    @Test
    fun `formattedSize returns MB for values over 1MB`() {
        val entry = makeEntry(fileSize = 1_572_864)
        assertEquals("1.5 MB", entry.formattedSize())
    }

    @Test
    fun `toJson preserves special characters in name`() {
        val entry = makeEntry(name = "Scan & Report \"2026\"")
        val restored = DocumentEntry.fromJson(entry.toJson())
        assertEquals("Scan & Report \"2026\"", restored.name)
    }

    @Test
    fun `fromJson with missing optional fields throws`() {
        val json = JSONObject().apply { put("id", "1") }
        assertThrows(JSONException::class.java) { DocumentEntry.fromJson(json) }
    }
}
```

### Pattern 3: Robolectric for ImageProcessor (TEST-04)

**What:** Test Bitmap filter operations on JVM using Robolectric for Android's `Bitmap`, `Canvas`, `ColorMatrix` classes. ML Kit is NOT invoked — tests target only the bitmap manipulation logic.
**When to use:** TEST-04 ImageProcessor tests.

```kotlin
// src/test/.../util/ImageProcessorTest.kt
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])  // Pin SDK to avoid downloading multiple SDK JARs
class ImageProcessorTest {

    @Test
    fun `applyFilter ORIGINAL returns same bitmap reference`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val result = ImageProcessor.applyFilter(bitmap, ImageProcessor.FilterType.ORIGINAL)
        assertSame(bitmap, result)
    }

    @Test
    fun `applyFilter ENHANCED returns new bitmap with same dimensions`() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val result = ImageProcessor.applyFilter(bitmap, ImageProcessor.FilterType.ENHANCED)
        assertNotSame(bitmap, result)
        assertEquals(bitmap.width, result.width)
        assertEquals(bitmap.height, result.height)
    }

    @Test
    fun `applyFilter BW returns non-null bitmap`() {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val result = ImageProcessor.applyFilter(bitmap, ImageProcessor.FilterType.BW)
        assertNotNull(result)
    }

    @Test
    fun `applyFilter MAGIC caps oversized bitmap dimensions`() {
        val bitmap = Bitmap.createBitmap(4000, 4000, Bitmap.Config.ARGB_8888)
        val result = ImageProcessor.applyFilter(bitmap, ImageProcessor.FilterType.MAGIC)
        assertTrue("Width should be capped", result.width <= 3368)
        assertTrue("Height should be capped", result.height <= 3368)
    }

    @Test
    fun `applyFilter SHARPEN produces bitmap with same dimensions`() {
        val bitmap = Bitmap.createBitmap(200, 150, Bitmap.Config.ARGB_8888)
        val result = ImageProcessor.applyFilter(bitmap, ImageProcessor.FilterType.SHARPEN)
        assertEquals(200, result.width)
        assertEquals(150, result.height)
    }

    @Test
    fun `applyFilter on 1x1 bitmap does not throw`() {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        for (filterType in ImageProcessor.FilterType.values()) {
            assertDoesNotThrow { ImageProcessor.applyFilter(bitmap, filterType) }
        }
    }
}
```

### Pattern 4: OcrProcessor Interface Refactor (PREREQUISITE for TEST-04)

**What:** `OcrProcessor` is currently a Kotlin `object` that calls ML Kit directly. This makes it impossible to fake in unit tests. The object must be refactored into an interface + concrete implementation. This is a production code change required before any test can be written for image filter logic that might involve OCR.

**CRITICAL DISCOVERY:** `OcrProcessor.kt` is a concrete singleton `object` using `TextRecognition.getClient()` directly. If `ImageProcessor.kt` calls `OcrProcessor` internally, then TEST-04 requires this refactor first. Verify whether `ImageProcessor.kt` calls `OcrProcessor` — if not, the refactor can be deferred.

**When to use:** Start of TEST-04 plan, before writing any Robolectric test.

```kotlin
// REFACTORED: ocr/OcrProcessor.kt — convert to interface
interface OcrProcessor {
    suspend fun recognizeText(context: Context, uri: Uri): OcrResult
    fun isAvailable(): Boolean
    fun close()
}

// NEW: ocr/MlKitOcrProcessor.kt — production implementation (move existing logic here)
class MlKitOcrProcessor : OcrProcessor {
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    override suspend fun recognizeText(context: Context, uri: Uri): OcrResult { /* existing logic */ }
    override fun isAvailable(): Boolean = true
    override fun close() { recognizer.close() }
}

// NEW: src/test/.../util/FakeOcrProcessor.kt — test double
class FakeOcrProcessor(
    private val result: OcrResult = OcrResult("FAKE_TEXT", emptyList(), true)
) : OcrProcessor {
    var callCount = 0
    override suspend fun recognizeText(context: Context, uri: Uri): OcrResult {
        callCount++
        return result
    }
    override fun isAvailable(): Boolean = true
    override fun close() {}
}
```

### Pattern 5: Robolectric for DocumentHistoryRepository (TEST-05)

**What:** Test SharedPreferences-backed CRUD repository on JVM. Robolectric provides a working `Context` and in-memory `SharedPreferences`. `getAllDocuments()` filters by `entry.exists()` — tests must create real temp files for entries that should appear in results.
**When to use:** TEST-05.

```kotlin
// src/test/.../data/DocumentHistoryRepositoryTest.kt
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentHistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: DocumentHistoryRepository
    private lateinit var tempDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear SharedPreferences between tests
        context.getSharedPreferences("document_history", Context.MODE_PRIVATE)
            .edit().clear().commit()
        repository = DocumentHistoryRepository(context)
        tempDir = createTempDir()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    private fun makeTempPdf(name: String = "test.pdf"): File {
        return File(tempDir, name).apply { writeText("fake pdf content") }
    }

    @Test
    fun `getAllDocuments returns empty list when no entries exist`() {
        assertTrue(repository.getAllDocuments().isEmpty())
    }

    @Test
    fun `addDocument and getAllDocuments retrieves entry`() {
        val file = makeTempPdf()
        repository.addDocument("My Doc", file.absolutePath, 2)
        val docs = repository.getAllDocuments()
        assertEquals(1, docs.size)
        assertEquals("My Doc", docs.first().name)
        assertEquals(2, docs.first().pageCount)
    }

    @Test
    fun `getAllDocuments filters out entries whose files no longer exist`() {
        repository.addDocument("Ghost Doc", "/nonexistent/path.pdf", 1)
        assertTrue(repository.getAllDocuments().isEmpty())
    }

    @Test
    fun `removeDocument removes entry by id`() {
        val file = makeTempPdf()
        repository.addDocument("Doc to Remove", file.absolutePath, 1)
        val id = repository.getAllDocuments().first().id
        repository.removeDocument(id)
        assertTrue(repository.getAllDocuments().isEmpty())
    }

    @Test
    fun `removeDocument with deleteFile=true deletes the PDF file`() {
        val file = makeTempPdf()
        repository.addDocument("Delete Me", file.absolutePath, 1)
        val id = repository.getAllDocuments().first().id
        repository.removeDocument(id, deleteFile = true)
        assertFalse(file.exists())
    }

    @Test
    fun `clearHistory removes all entries`() {
        val file1 = makeTempPdf("doc1.pdf")
        val file2 = makeTempPdf("doc2.pdf")
        repository.addDocument("Doc 1", file1.absolutePath, 1)
        repository.addDocument("Doc 2", file2.absolutePath, 1)
        repository.clearHistory()
        assertTrue(repository.getAllDocuments().isEmpty())
    }

    @Test
    fun `getAllDocuments returns entries sorted newest first`() {
        val file1 = makeTempPdf("old.pdf")
        repository.addDocument("Old", file1.absolutePath, 1)
        Thread.sleep(10)  // Ensure different timestamps
        val file2 = makeTempPdf("new.pdf")
        repository.addDocument("New", file2.absolutePath, 1)
        val docs = repository.getAllDocuments()
        assertEquals("New", docs[0].name)
        assertEquals("Old", docs[1].name)
    }

    @Test
    fun `multiple add operations maintain MAX_HISTORY_SIZE limit`() {
        repeat(110) { i ->
            val file = makeTempPdf("doc$i.pdf")
            repository.addDocument("Doc $i", file.absolutePath, 1)
        }
        assertTrue(repository.getAllDocuments().size <= 100)
    }
}
```

### Pattern 6: JaCoCo Task in build.gradle.kts

**What:** AGP built-in JaCoCo activated via `enableUnitTestCoverage = true` plus a custom report task with exclusions.
**When to use:** TEST-01 scaffold — configure before the first test is written.

```kotlin
// In app/build.gradle.kts — AFTER the android {} block
tasks.register("jacocoTestReport", JacocoReport::class) {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.dir("reports/jacoco/html"))
    }

    // Exclusions: generated code that must not count toward coverage targets
    val excludes = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Args*",           // Navigation Safe Args generated
        "**/*Directions*",     // Navigation Safe Args generated
        "**/*Binding.*",       // ViewBinding generated
        "**/*_Impl*",          // Future-proof: Room DAO impls
        "**/databinding/**"
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

// Coverage verification task (run manually to check thresholds)
tasks.register("jacocoTestCoverageVerification", JacocoCoverageVerification::class) {
    dependsOn("testDebugUnitTest")
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("com/pdfscanner/app/util/*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = 0.70.toBigDecimal()
            }
        }
        rule {
            element = "PACKAGE"
            includes = listOf("com/pdfscanner/app/viewmodel/*")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = 0.50.toBigDecimal()
            }
        }
    }
    // Re-use exclusions from report task
    classDirectories.setFrom(tasks.named("jacocoTestReport", JacocoReport::class).get().classDirectories)
    executionData.setFrom(tasks.named("jacocoTestReport", JacocoReport::class).get().executionData)
}
```

**Coverage commands:**
```bash
# Run tests and generate HTML report
./gradlew testDebugUnitTest jacocoTestReport
# Open: app/build/reports/jacoco/html/index.html

# Check thresholds pass
./gradlew jacocoTestCoverageVerification
```

### Pattern 7: Fragment Smoke Test via FragmentScenario (TEST-07, stretch)

**What:** Launch each non-camera fragment in isolation and verify layout inflation completes without crash. These are instrumented tests — they run on device/emulator only.
**When to use:** TEST-07 (after JVM and Robolectric tests are complete).

```kotlin
// src/androidTest/.../ui/HomeFragmentTest.kt
@RunWith(AndroidJUnit4::class)
class HomeFragmentTest {

    @Test
    fun homeFragment_inflatesWithoutCrash() {
        launchFragmentInContainer<HomeFragment>(
            themeResId = R.style.Theme_PdfScanner  // REQUIRED: fragments crash without app theme
        )
        // If we reach here, layout inflation and View Binding init succeeded
        onView(withId(R.id.root)).check(matches(isDisplayed()))
    }

    @Test
    fun homeFragment_scanButtonIsVisible() {
        launchFragmentInContainer<HomeFragment>(themeResId = R.style.Theme_PdfScanner)
        onView(withId(R.id.btnScan)).check(matches(isDisplayed()))
    }
}
```

### Pattern 8: Navigation Flow Test (TEST-08, stretch)

**What:** Use `TestNavHostController` to verify navigation actions fire correctly without exercising actual camera hardware. Scope is limited to confirming the nav action fires — not verifying full camera pipeline.
**When to use:** TEST-08 (last task, after all other tests pass).

```kotlin
// src/androidTest/.../navigation/NavigationFlowTest.kt
@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {

    @Test
    fun homeFragment_scanClick_navigatesToCamera() {
        val navController = TestNavHostController(ApplicationProvider.getApplicationContext())

        launchFragmentInContainer<HomeFragment>(
            themeResId = R.style.Theme_PdfScanner
        ).onFragment { fragment ->
            navController.setGraph(R.navigation.nav_graph)
            navController.setCurrentDestination(R.id.homeFragment)
            Navigation.setViewNavController(fragment.requireView(), navController)
        }

        onView(withId(R.id.btnScan)).perform(click())
        assertEquals(R.id.cameraFragment, navController.currentDestination?.id)
    }
}
```

### Anti-Patterns to Avoid

- **`@RunWith(RobolectricTestRunner::class)` without `@Config(sdk = [34])`:** Robolectric downloads multiple SDK JARs on first run. Pin to a single SDK to avoid a 5+ minute first-run download.
- **`observeForever` without removing the observer:** Causes test-order-dependent memory retention. Always remove observers in `@After` or use `liveData.value` with `InstantTaskExecutorRule` active.
- **`clearAllMocks()` missing in `@After`:** MockK state leaks between tests in the same JVM instance, causing ordering-dependent failures.
- **Constructing `ScannerViewModel` with `mockk<SavedStateHandle>()`:** Unnecessary — `SavedStateHandle()` works directly on JVM.
- **Writing tests that only assert mock interactions:** Tests that only verify `verify { mock.method() }` without asserting ViewModel state provide zero regression protection.
- **`launchFragmentInContainer<T>()` without `themeResId`:** Fragments render against a bare theme and Material components fail with `IllegalArgumentException: The style on this component requires your app theme to be Theme.AppCompat`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Kotlin final class mocking | Reflection-based manual fakes | MockK 1.14.7 | Handles all Kotlin types natively; manual fakes don't catch contract changes |
| Coroutine test synchronization | `Thread.sleep()` / `CountDownLatch` | `runTest` + `UnconfinedTestDispatcher` | Thread.sleep is flaky on CI; `runTest` advances virtual time correctly |
| LiveData synchronization | `Semaphore` or blocking loops | `InstantTaskExecutorRule` | Rule is one line; custom solutions miss edge cases like `postValue` vs `setValue` |
| SharedPreferences test isolation | Separate prefs file names per test | Clear prefs in `@Before` via `.edit().clear().commit()` | File names change per test cause file accumulation; clear is simpler and reliable |
| Coverage counter interpretation | Reading HTML numbers without checking type | `jacocoTestCoverageVerification` with `counter = "LINE"` | HTML shows multiple counters; without enforcement the "correct" one is ambiguous |
| Fragment test theme injection | Copying theme XML into test resources | Pass `themeResId = R.style.Theme_PdfScanner` to `launchFragmentInContainer` | App theme is already defined; duplicating it in tests causes drift |

**Key insight:** In Android testing, the framework boilerplate (rules, runners, dispatchers) is not accidental complexity — each piece addresses a specific JVM vs Android lifecycle gap. Skipping any piece does not simplify the test; it creates a silent failure that takes hours to debug.

---

## Common Pitfalls

### Pitfall 1: InstantTaskExecutorRule Missing

**What goes wrong:** `LiveData.setValue()` either throws `java.lang.RuntimeException: Method getMainLooper in android.os.Looper not mocked` or silently posts to a background executor that never delivers. Every assertion on `viewModel.pages.value` returns `null`.
**Why it happens:** LiveData enforces main-thread observation; the JVM test environment has no Looper.
**How to avoid:** Add `@get:Rule val instantExecutorRule = InstantTaskExecutorRule()` to every ViewModel test class. This rule is in `androidx.arch.core:core-testing:2.2.0`.
**Warning signs:** `viewModel.pages.value` is null immediately after `viewModel.addPage()`.

### Pitfall 2: Deprecated Coroutine Test API

**What goes wrong:** Tests using `TestCoroutineDispatcher` and `runBlockingTest` (deprecated since coroutines-test 1.6) compile but produce ordering-dependent failures. They may pass locally but fail on CI.
**Why it happens:** Most tutorials online are pre-1.6 vintage. The deprecated API has subtly different flush semantics.
**How to avoid:** Use only `UnconfinedTestDispatcher` + `runTest` from the start. Create a shared `MainDispatcherRule` (not `MainCoroutineRule`). Never copy older patterns.
**Warning signs:** Compiler warning `'TestCoroutineDispatcher' is deprecated`.

### Pitfall 3: JaCoCo Counter Type Ambiguity

**What goes wrong:** BRANCH coverage for ViewModel tests reports 45% because Kotlin coroutines generate synthetic state-machine branches. LINE coverage for the same tests is 75%. The 50% threshold passes if enforced on LINE, fails on BRANCH — both numbers are in the HTML report.
**Why it happens:** JaCoCo tracks six counters. Coroutines inflate BRANCH by 15–25 percentage points. The requirement says "50% coverage" without specifying the counter.
**How to avoid:** Configure `jacocoTestCoverageVerification` with `counter = "LINE"` explicitly. Never read coverage from the HTML headline without checking which counter column you are reading.
**Warning signs:** BRANCH coverage is 15+ percentage points lower than LINE coverage.

### Pitfall 4: JaCoCo Exclusions Missing

**What goes wrong:** Generated classes (`R`, `BuildConfig`, `*Directions`, `*Args`, `*Binding`) are included in coverage calculations, dragging total coverage down by 10–20 percentage points. The 70% util/ threshold fails even when all ImageProcessor logic is covered.
**Why it happens:** AGP does not auto-exclude generated classes from JaCoCo.
**How to avoid:** Apply the exclusion list in the `jacocoTestReport` task `classDirectories` configuration (see Pattern 6 above). Run report once without exclusions to baseline, then apply.
**Warning signs:** HTML report tree shows `R`, `BuildConfig`, or `PreviewFragmentDirections` entries.

### Pitfall 5: OcrProcessor is an Object, Not an Interface

**What goes wrong:** If `ImageProcessor.kt` calls `OcrProcessor` directly, there is no way to substitute a fake implementation in Robolectric tests. Tests that import `OcrProcessor` will attempt to instantiate `TextRecognition.getClient()`, which loads native TFLite libraries and crashes with `UnsatisfiedLinkError`.
**Why it happens:** `OcrProcessor.kt` is implemented as a Kotlin `object` (singleton) with a concrete ML Kit dependency. Objects cannot be mocked without PowerMock-style bytecode manipulation, which is not supported in Robolectric contexts.
**How to avoid:** Before writing TEST-04, verify whether `ImageProcessor.applyFilter()` calls `OcrProcessor`. If it does, refactor `OcrProcessor` from `object` to `interface` + `MlKitOcrProcessor : OcrProcessor` implementation. Create `FakeOcrProcessor` in `src/test/`. This is the only production code change required in Phase 4.
**Warning signs:** `UnsatisfiedLinkError: libtensorflowlite_jni.so` during any Robolectric test run.

### Pitfall 6: CameraFragment in Robolectric

**What goes wrong:** Any test class that imports `ProcessCameraProvider` or exercises `CameraFragment.onViewCreated()` in a Robolectric context crashes with `UnsatisfiedLinkError: libcamera2ndk.so`.
**Why it happens:** CameraX uses native camera2 bindings. Robolectric cannot load `.so` libraries.
**How to avoid:** CameraFragment tests MUST be in `src/androidTest/` as instrumented tests. Never include `CameraFragment` in Robolectric test classes. TEST-07 fragment smoke tests cover non-camera fragments: Home, Preview, Pages, History, Settings.
**Warning signs:** `UnsatisfiedLinkError` mentioning `camera2ndk` in a test in `src/test/`.

### Pitfall 7: DocumentHistoryRepository getAllDocuments() Filters by File Existence

**What goes wrong:** Tests that add entries without creating real temp files get zero results back from `getAllDocuments()`. The repository silently filters out all entries whose file does not exist on disk.
**Why it happens:** `getAllDocuments()` calls `entry.exists()` which checks `File(filePath).exists()`. In Robolectric, the filesystem is real (not stubbed). A path like `/nonexistent/path.pdf` does not exist.
**How to avoid:** Every positive-case repository test must create a real temp file in a temp directory and pass its absolute path to `addDocument()`. Clean up in `@After` with `tempDir.deleteRecursively()`. See Pattern 5 above.
**Warning signs:** `getAllDocuments()` always returns an empty list even after calling `addDocument()`.

### Pitfall 8: MockK clearAllMocks() Not Called in @After

**What goes wrong:** MockK stores mock state in a static registry per JVM instance. Without `clearAllMocks()` in `@After`, stubs and verifications from test N leak into test N+1. This causes ordering-dependent flakiness.
**Why it happens:** JUnit runs all tests in a class in the same JVM process, sharing MockK's registry.
**How to avoid:** Add `@After fun teardown() { clearAllMocks() }` to every test class that uses MockK. For test classes that use `unmockkAll()`, that is also acceptable.

### Pitfall 9: Robolectric First-Run SDK Download Timeout

**What goes wrong:** On first run, Robolectric downloads the Android SDK JAR for every SDK version it needs. Without `@Config(sdk = [34])`, it may attempt to download SDK 26 through 35. This takes 3–10 minutes and appears hung.
**Why it happens:** Robolectric defaults to testing against multiple SDK versions for compatibility.
**How to avoid:** Always annotate Robolectric test classes with `@Config(sdk = [34])` to pin to a single SDK. The download happens once and is cached.

---

## Code Examples

### ScannerViewModel — Complete API Surface to Test

```kotlin
// Confirmed from reading ScannerViewModel.kt directly

// DATA (observable via LiveData)
viewModel.pages: LiveData<List<Uri>>          // TEST-02: empty list, add, remove, reorder
viewModel.pageFilters: LiveData<Map<Int, FilterType>>  // TEST-02: set, clear, persist
viewModel.pdfBaseName: LiveData<String?>      // TEST-02: set and get
viewModel.currentCaptureUri: LiveData<Uri?>   // TEST-02: set and clear
viewModel.pdfUri: LiveData<Uri?>              // TEST-02: set
viewModel.isLoading: LiveData<Boolean>        // TEST-02: loading state transitions

// METHODS (to test through state assertions)
viewModel.addPage(uri)                        // TEST-02
viewModel.removePage(index)                   // TEST-02
viewModel.movePage(from, to)                  // TEST-02
viewModel.clearAllPages()                     // TEST-02
viewModel.setPageFilter(index, filterType)    // TEST-02
viewModel.setPdfBaseName(name)                // TEST-02
viewModel.getPdfFileName(timestamp)           // TEST-02 — pure function, most testable
viewModel.setCurrentCaptureUri(uri)           // TEST-02
viewModel.setPdfUri(uri)                      // TEST-02
```

### DocumentHistoryRepository — Complete CRUD Surface to Test

```kotlin
// Confirmed from reading DocumentHistory.kt directly

repository.getAllDocuments(): List<DocumentEntry>      // TEST-05: empty, filtered, sorted
repository.addDocument(name, filePath, pageCount)     // TEST-05: create
repository.removeDocument(id, deleteFile=false)       // TEST-05: delete without file removal
repository.removeDocument(id, deleteFile=true)        // TEST-05: delete with file removal
repository.clearHistory(deleteFiles=false)            // TEST-05: clear all
repository.clearHistory(deleteFiles=true)             // TEST-05: clear all + delete files
// Note: updateDocument() — verify if this method exists; ARCHITECTURE.md mentions update tests
```

### ImageProcessor — FilterType Enum Values to Test

```kotlin
// All values based on reading ImageProcessor.kt header comments
// Confirm exact enum names from FilterType declaration in source

ImageProcessor.FilterType.ORIGINAL    // Should return same bitmap reference
ImageProcessor.FilterType.ENHANCED    // New bitmap, same dimensions
ImageProcessor.FilterType.BW          // Document black and white
ImageProcessor.FilterType.MAGIC       // Adaptive, caps dimensions for large bitmaps
ImageProcessor.FilterType.SHARPEN     // Edge enhancement

// Edge cases to test:
// - 1x1 bitmap (minimum size)
// - 4000x4000 bitmap (MAGIC should scale down)
// - ARGB_8888 vs RGB_565 config
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `TestCoroutineDispatcher` + `runBlockingTest` | `UnconfinedTestDispatcher` + `runTest` | coroutines-test 1.6 (2022) | Old API has ordering bugs; new API is deterministic |
| Third-party JaCoCo plugins (arturdm) | AGP built-in `enableUnitTestCoverage` | AGP 7.0+ | No extra plugin needed; single-module projects get this for free |
| `mockk-android` version separate from `mockk` | All MockK artifacts now 1.14.7 | MockK 1.12+ | Versions must still match; both artifacts still required for Android tests |
| Robolectric `@Config(constants = BuildConfig::class)` | `@Config(sdk = [34])` | Robolectric 4.x | Old `constants` attribute removed; SDK pinning is the correct approach |
| `launchFragmentInContainer` (no theme) | `launchFragmentInContainer(themeResId = ...)` | fragment-testing 1.3+ | Material components require app theme; bare launch crashes |
| `isTestCoverageEnabled` (deprecated) | `enableUnitTestCoverage` | AGP 8.x | Old property is no longer available in AGP 8 Kotlin DSL |

**Deprecated/outdated:**
- `TestCoroutineDispatcher`: deprecated, do not use
- `runBlockingTest`: deprecated, use `runTest`
- `MainCoroutineRule` (old pattern): replace with `MainDispatcherRule` using `TestWatcher`
- `isTestCoverageEnabled`: replaced by `enableUnitTestCoverage` in AGP 8+

---

## Open Questions

1. **Does ImageProcessor.kt call OcrProcessor?**
   - What we know: `ImageProcessor.kt` header lists only `Bitmap`, `Canvas`, `Color`, `ColorMatrix`, `ColorMatrixColorFilter`, `Paint` imports. No OCR import visible in the first 60 lines.
   - What's unclear: Whether OCR is invoked inside specific filter methods lower in the file (e.g., MAGIC filter).
   - Recommendation: Read the full `ImageProcessor.kt` at the start of TEST-04 plan. If no ML Kit calls exist, the `OcrProcessor` interface refactor is not required for Phase 4 at all. If ML Kit calls do exist, the refactor is the first task of TEST-04.

2. **What are the exact method signatures for ScannerViewModel?**
   - What we know: Public properties confirmed (`pages`, `pageFilters`, `pdfBaseName`, `currentCaptureUri`, `pdfUri`, `isLoading`). High-level method list visible in source.
   - What's unclear: Exact method names (e.g., is it `setPageFilter` or `updatePageFilter`? Is it `removePage` or `deletePage`?).
   - Recommendation: Read the full ScannerViewModel.kt during TEST-02 plan creation to confirm all method names. Do not assume names from research — use the actual source.

3. **Does DocumentHistoryRepository have an update method?**
   - What we know: `ARCHITECTURE.md` mentions "create, read, update, delete" for TEST-05, but only `addDocument`, `removeDocument`, and `clearHistory` are confirmed from reading the source.
   - What's unclear: Whether an `updateDocument()` method exists or needs to be created.
   - Recommendation: Read the full `DocumentHistory.kt` during TEST-05 plan. If no update method exists, TEST-05 covers create, read, delete, and filter (existence check) — 8 tests are achievable without update.

4. **What is the actual nav graph destination ID for CameraFragment?**
   - What we know: Navigation pattern documented with `R.id.cameraFragment` as the destination ID.
   - What's unclear: The actual resource ID name used in `nav_graph.xml`.
   - Recommendation: Read `res/navigation/nav_graph.xml` during TEST-08 plan creation to confirm destination IDs before writing any navigation test.

---

## Sources

### Primary (HIGH confidence)

- Direct source code inspection: `OcrProcessor.kt`, `ScannerViewModel.kt`, `DocumentHistory.kt`, `ImageProcessor.kt` — read directly from project during research
- `.planning/research/STACK.md` — all dependency versions and build.gradle.kts diff (researched 2026-03-01, versions web-verified)
- `.planning/research/ARCHITECTURE.md` — test directory structure, JaCoCo task configuration, code patterns (researched 2026-03-01)
- `.planning/research/PITFALLS.md` — 10 pitfalls with code samples and recovery cost analysis (researched 2026-03-01)
- `.planning/REQUIREMENTS.md` — exact requirement IDs, acceptance criteria, and traceability matrix
- `.planning/STATE.md` — locked technical decisions (LINE counter, UnconfinedTestDispatcher, InstantTaskExecutorRule)

### Secondary (MEDIUM confidence)

- JaCoCo coroutine branch inflation — JaCoCo GitHub issues #1045 and #1353 (coroutine synthetic branches confirmed as phantom coverage loss)
- Robolectric `@Config(sdk = [34])` pattern — robolectric.org official configuration docs

### Tertiary (LOW confidence — needs validation)

- OcrProcessor usage within ImageProcessor — not confirmed; requires reading full ImageProcessor.kt source (the first 60 lines showed no ML Kit import, but the full file was not read)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versions web-verified March 2026; compatibility matrix confirmed in prior milestone research
- Architecture patterns: HIGH — patterns derived from direct source code inspection + official AndroidX test docs
- Pitfalls: HIGH for JaCoCo/MockK/Robolectric/LiveData (multi-source verified); MEDIUM for OcrProcessor usage boundary (not fully confirmed)
- Open questions: 4 items requiring source file reads at plan time — all are low-risk clarifications, not blockers

**Research date:** 2026-03-01
**Valid until:** 2026-04-01 (stable library ecosystem; recheck MockK and fragment-testing if more than 30 days pass before implementation)
