# Phase 1: Stability - Research

**Researched:** 2026-02-28
**Domain:** Android Fragment lifecycle safety, resource management, bitmap memory, ViewModel state preservation
**Confidence:** HIGH

## Summary

Phase 1 addresses eight stability bugs (BUG-01 through BUG-08) that cause crashes, resource leaks, data loss, and broken UX in the PDF Scanner app. The codebase has been directly audited and every finding below is verified against specific lines in the source files. The bugs fall into four categories: (1) fragment detachment crashes from 154 `requireContext()`/`requireActivity()` calls across 12 files, many inside coroutine callbacks that execute after fragment detach; (2) resource leaks from manual `close()` calls on PdfRenderer/ParcelFileDescriptor instead of `use {}` blocks, plus temp files never cleaned up; (3) data integrity issues from mutable collections exposed through LiveData and no process death recovery; (4) missing functionality (EXIF rotation, undo/redo placeholder).

The fixes use only existing dependencies -- no new libraries are needed for this phase. The work is primarily refactoring existing code: replacing `requireContext()` with `context ?: return` in coroutine callbacks, wrapping Closeable resources in `use {}` blocks, switching to immutable list patterns in ScannerViewModel, adding SavedStateHandle for process death recovery, capping bitmap decode dimensions, handling EXIF orientation, and either implementing or removing the undo/redo buttons.

**Primary recommendation:** Fix all 8 bugs using standard Android patterns already available in the project's dependency set. No new libraries needed. Audit every coroutine launch block across all 12 fragment files for detachment safety.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| BUG-01 | Replace `requireContext()`/`requireActivity()` in coroutine callbacks with null-safe patterns | Audit of 154 call sites across 12 files; identified coroutine-context calls in PagesFragment (33 calls), HomeFragment (29), HistoryFragment (42), PdfEditorFragment (18), PreviewFragment (8); pattern: `context ?: return` in withContext(Main) blocks |
| BUG-02 | PdfRenderer, ParcelFileDescriptor, and Closeable resources use `use {}` blocks | Verified: PdfUtils.kt mergePdfs/splitPdf/compressPdf/getPageCount use manual close(); NativePdfView.loadPdf creates temp without cleanup; PdfViewerFragment.loadPdf manual close in onDestroyView; PdfAnnotationRenderer.renderAnnotatedPdf manual close |
| BUG-03 | Stale temp files (>1hr) cleaned up on app startup | Verified: NativePdfView creates `pdf_view_temp_*` files, PdfEditorFragment creates `temp_edit_*` files, PdfUtils creates `pdf_compress_temp` directory -- none have cleanup logic |
| BUG-04 | ScannerViewModel page list uses immutable list pattern | Verified: `_pages = MutableLiveData<MutableList<Uri>>` with in-place mutation in addPage/updatePage/removePage/movePage; `_pageFilters = MutableLiveData<MutableMap<Int, FilterType>>` same pattern |
| BUG-05 | ScannerViewModel preserves in-progress scan state via SavedStateHandle | Verified: zero SavedStateHandle usage in codebase; ScannerViewModel extends plain ViewModel; page URIs, filters, PDF name all lost on process death |
| BUG-06 | Bitmap decode dimensions capped to PDF output size, explicit recycle() | Verified: PagesFragment.generatePdf already calls recycle(); decodeSampledBitmap targets 2x page size (1190x1684); BUT PreviewFragment.loadFullResBitmap loads FULL resolution with no cap; ImageProcessor.applyMagicFilter allocates 2 IntArrays (width*height each) + output bitmap |
| BUG-07 | PDF Editor undo/redo implemented or buttons removed | Verified: PdfEditorFragment lines 88-95 show `btnUndo`/`btnRedo` with `Toast("Undo coming soon!")` and `Toast("Redo coming soon!")` -- placeholder only |
| BUG-08 | Imported images display with correct EXIF orientation | Verified: zero ExifInterface usage in codebase; grep for "exif", "ExifInterface", "orientation" returns empty; gallery-imported images will display rotated if camera wrote EXIF rotation metadata |
</phase_requirements>

## Standard Stack

### Core (already in build.gradle.kts -- no additions needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| androidx.lifecycle:lifecycle-viewmodel-ktx | 2.6.2 | ViewModel with SavedStateHandle | Already declared; SavedStateHandle available via `SavedStateViewModelFactory` |
| androidx.fragment:fragment-ktx | 1.6.2 | Fragment lifecycle extensions | Already declared; provides `by activityViewModels()` |
| kotlinx.coroutines-android | 1.7.3 | Coroutine lifecycle scoping | Already declared; `lifecycleScope` and `viewModelScope` |
| androidx.core:core-ktx | 1.12.0 | Kotlin extensions including Uri/File | Already declared |

### Supporting (may need adding)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| androidx.exifinterface:exifinterface | 1.3.7 | Read EXIF orientation from imported images | BUG-08: must add to build.gradle.kts (verify version on Maven Central) |

**Note:** The ExifInterface version 1.3.7 is from training data (May 2025). Verify against Maven Central before adding.

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Manual EXIF handling with ExifInterface | Coil image loader (handles EXIF automatically) | Coil is planned for Phase 2 (DSYS-03); for Phase 1, use ExifInterface directly since it is lightweight and focused |
| SavedStateHandle | onSaveInstanceState in Activity | SavedStateHandle is the modern ViewModel-scoped approach; onSaveInstanceState is fragment/activity-level and harder to maintain |
| Immutable List copies | StateFlow with MutableStateFlow | StateFlow migration is larger scope; immutable List via LiveData is sufficient for Phase 1 |

**Installation:**
```kotlin
// Add to build.gradle.kts dependencies block
implementation("androidx.exifinterface:exifinterface:1.3.7")
```

## Architecture Patterns

### Pattern 1: Null-Safe Context in Coroutine Callbacks

**What:** Replace `requireContext()` with `context ?: return` inside `withContext(Dispatchers.Main)` blocks within `lifecycleScope.launch` coroutines.

**When to use:** Every time a coroutine callback accesses Context after a `withContext(Dispatchers.IO)` block -- the fragment may have detached during the IO work.

**Example:**
```kotlin
// BEFORE (crashes if fragment detached during IO work):
lifecycleScope.launch {
    val result = withContext(Dispatchers.IO) { doWork() }
    withContext(Dispatchers.Main) {
        binding.loadingOverlay.visibility = View.GONE  // NPE if view destroyed
        Toast.makeText(requireContext(), "Done", Toast.LENGTH_SHORT).show()  // IllegalStateException
    }
}

// AFTER (safe):
lifecycleScope.launch {
    val result = withContext(Dispatchers.IO) { doWork() }
    // After IO completes, fragment may be detached. Check before UI work.
    val ctx = context ?: return@launch
    val currentBinding = _binding ?: return@launch
    currentBinding.loadingOverlay.visibility = View.GONE
    Toast.makeText(ctx, "Done", Toast.LENGTH_SHORT).show()
}
```

**Critical insight:** `lifecycleScope` cancels coroutines when the lifecycle is DESTROYED, but `withContext(Dispatchers.IO)` runs its block to completion before the cancellation point is checked. The subsequent `withContext(Dispatchers.Main)` block is where the crash happens.

### Pattern 2: use {} for Closeable Resources

**What:** Wrap PdfRenderer, ParcelFileDescriptor, and all Closeable resources in Kotlin `use {}` blocks.

**When to use:** Every place that opens a PdfRenderer or ParcelFileDescriptor.

**Example:**
```kotlin
// BEFORE (leaks on exception):
val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return
val renderer = PdfRenderer(pfd)
// ... work ...
renderer.close()
pfd.close()

// AFTER (auto-closes even on exception):
context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
    PdfRenderer(pfd).use { renderer ->
        // ... work ...
    }
}
```

**Note:** PdfRenderer implements `Closeable` (via `AutoCloseable` on API 21+), so `use {}` works directly. PdfRenderer.Page also implements Closeable.

### Pattern 3: Immutable LiveData Collections

**What:** Use `List<Uri>` (immutable interface) for LiveData type parameter, create new list instances on mutation.

**When to use:** Any ViewModel LiveData that holds a collection.

**Example:**
```kotlin
// BEFORE (race condition, silent data loss):
private val _pages = MutableLiveData<MutableList<Uri>>(mutableListOf())
val pages: LiveData<MutableList<Uri>> = _pages

fun addPage(uri: Uri) {
    val currentList = _pages.value ?: mutableListOf()
    currentList.add(uri)  // Mutates shared reference
    _pages.value = currentList
}

// AFTER (thread-safe, immutable):
private val _pages = MutableLiveData<List<Uri>>(emptyList())
val pages: LiveData<List<Uri>> = _pages

fun addPage(uri: Uri) {
    _pages.value = _pages.value.orEmpty() + uri  // Creates new list
}

fun removePage(index: Int) {
    val current = _pages.value.orEmpty()
    if (index in current.indices) {
        _pages.value = current.toMutableList().also { it.removeAt(index) }
    }
}

fun movePage(from: Int, to: Int) {
    val current = _pages.value.orEmpty().toMutableList()
    if (from in current.indices && to in current.indices) {
        java.util.Collections.swap(current, from, to)
        _pages.value = current.toList()
    }
}
```

### Pattern 4: SavedStateHandle for Process Death Recovery

**What:** Use `SavedStateHandle` in ScannerViewModel to persist page URIs and filter state across process death.

**When to use:** ViewModel data that the user would expect to survive Android killing the process in the background.

**Example:**
```kotlin
// ScannerViewModel with SavedStateHandle
class ScannerViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    companion object {
        private const val KEY_PAGES = "pages"
        private const val KEY_PDF_BASE_NAME = "pdf_base_name"
        private const val KEY_PAGE_FILTERS = "page_filters"
    }

    // Pages backed by SavedStateHandle
    val pages: LiveData<List<Uri>> = savedStateHandle.getLiveData(KEY_PAGES, emptyList())

    fun addPage(uri: Uri) {
        val current = savedStateHandle.get<List<Uri>>(KEY_PAGES).orEmpty()
        savedStateHandle[KEY_PAGES] = current + uri
    }

    val pdfBaseName: LiveData<String?> = savedStateHandle.getLiveData(KEY_PDF_BASE_NAME)

    fun setPdfBaseName(name: String?) {
        savedStateHandle[KEY_PDF_BASE_NAME] = name?.trim()?.takeIf { it.isNotEmpty() }
    }
}
```

**Note:** `by activityViewModels()` automatically uses `SavedStateViewModelFactory` when the ViewModel constructor accepts `SavedStateHandle`. No factory configuration needed. Uri implements Parcelable, so it can be stored in SavedStateHandle directly. The filter map (Map<Int, FilterType>) needs serialization since FilterType is an enum -- store as `Map<Int, String>` and convert.

### Pattern 5: EXIF Orientation Correction

**What:** Read EXIF orientation metadata from imported images and rotate the bitmap accordingly.

**When to use:** Whenever loading a bitmap from a user-provided URI (gallery import, file picker).

**Example:**
```kotlin
import androidx.exifinterface.media.ExifInterface

fun loadBitmapWithExifCorrection(context: Context, uri: Uri): Bitmap? {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
    val bitmap = BitmapFactory.decodeStream(inputStream)
    inputStream.close()

    // Read EXIF orientation
    val exifStream = context.contentResolver.openInputStream(uri) ?: return bitmap
    val exif = ExifInterface(exifStream)
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    exifStream.close()

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.preScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(-90f)
            matrix.preScale(-1f, 1f)
        }
        else -> return bitmap  // No rotation needed
    }

    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated != bitmap) bitmap.recycle()
    return rotated
}
```

### Anti-Patterns to Avoid

- **Catching generic Exception in coroutines without logging:** Many catch blocks in the codebase swallow exceptions silently. Always log with `Log.e()` before showing user-facing error.
- **Using `requireContext()` anywhere inside a lambda that could execute asynchronously:** This includes activity result callbacks, listener callbacks on asynchronous operations, and coroutine bodies.
- **Storing `Context` in ViewModel:** The PdfAnnotationRenderer takes `requireContext()` in `saveAnnotatedPdf()` inside a coroutine on `Dispatchers.IO`. Pass application context or move to a repository pattern.
- **Calling `bitmap.recycle()` on a bitmap that might still be referenced:** After recycling, any use of the bitmap will crash. Only recycle when you are certain no View or Canvas references it.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| EXIF orientation handling | Custom rotation matrix logic from scratch | `androidx.exifinterface:exifinterface` ExifInterface class | Handles all 8 EXIF orientation values including flips and transposes; tested across device manufacturers |
| Process death state restoration | Manual `onSaveInstanceState` / SharedPreferences hackery | `SavedStateHandle` in ViewModel | Automatically participates in Activity save/restore lifecycle; type-safe; no manual Bundle management |
| Resource cleanup | Manual try/finally with close() | Kotlin `use {}` extension on Closeable | Guarantees close() even on exception; handles double-close; cleaner code |
| Bitmap downsampling | Custom pixel math | `BitmapFactory.Options.inSampleSize` | Android platform API; handles color space, density, and memory alignment correctly |

**Key insight:** Every fix in this phase uses standard Android platform APIs and Kotlin stdlib patterns. No external libraries needed except ExifInterface (a single lightweight AndroidX artifact).

## Common Pitfalls

### Pitfall 1: Coroutine Cancellation Timing

**What goes wrong:** Developer replaces `requireContext()` with `context ?: return` inside `withContext(Dispatchers.Main)` but forgets that `lifecycleScope.launch` itself starts on Main thread. The initial `requireContext()` calls BEFORE the first `withContext(IO)` are safe. Only calls AFTER an IO switch are dangerous.

**Why it happens:** Over-zealous replacement can introduce bugs (early returns where none is needed) or miss the actual danger spots.

**How to avoid:** Only replace `requireContext()` calls that are AFTER a `withContext(Dispatchers.IO)` block within the same coroutine. Calls in `onViewCreated`, `setupButtons`, click listeners (before any coroutine) are safe.

**Warning signs:** App behavior changes unexpectedly (early returns skipping UI updates when fragment IS still attached).

### Pitfall 2: SavedStateHandle Size Limits

**What goes wrong:** Storing large data (bitmaps, large lists of URIs) in SavedStateHandle causes `TransactionTooLargeException` because SavedStateHandle uses the saved instance state Bundle, which has a ~1MB limit.

**Why it happens:** Each page URI is typically a `file://` URI string of ~80 characters. Even 100 pages = ~8KB, well under the limit. But if someone tries to store bitmap thumbnails or the actual image data, it will crash.

**How to avoid:** Store only URI strings and primitive state (filter type, PDF name, loading state). Never store bitmaps or byte arrays in SavedStateHandle.

**Warning signs:** `TransactionTooLargeException` in logcat after pressing Home with many pages.

### Pitfall 3: PdfRenderer Page Close Before Renderer Close

**What goes wrong:** If a PdfRenderer.Page is still open when PdfRenderer.close() is called, it throws `IllegalStateException`. This happens when an exception occurs between `openPage()` and `page.close()`.

**Why it happens:** PdfRenderer enforces that only one page can be open at a time, and all pages must be closed before the renderer is closed.

**How to avoid:** Always close pages in a `use {}` block or `try/finally`. When refactoring PdfUtils to use `use {}` on PdfRenderer, ensure each `page` is closed within the loop iteration, not at the end.

**Warning signs:** `IllegalStateException: Current page not closed` in logcat.

### Pitfall 4: Immutable List Breaking ListAdapter DiffUtil

**What goes wrong:** After switching ScannerViewModel to immutable lists, `PagesAdapter.submitList()` might receive the same list reference if the developer accidentally returns the same list object (e.g., using `_pages.value = currentList` without creating a new list).

**Why it happens:** `ListAdapter.submitList()` compares list references. If the same object is passed, it no-ops (no diff calculated, no UI update).

**How to avoid:** Always create a new list: `_pages.value = currentList + newUri` or `currentList.toMutableList().also { ... }.toList()`. The `observeViewModel` in PagesFragment already calls `pages.toList()` which helps.

**Warning signs:** UI does not update after add/remove/reorder operations.

### Pitfall 5: EXIF Correction Applied Twice

**What goes wrong:** CameraX images already have correct orientation (CameraX handles EXIF internally). If we apply EXIF correction to ALL images (including CameraX captures), we double-rotate.

**Why it happens:** CameraX saves images in the correct orientation and writes EXIF data to match. Gallery imports may have EXIF rotation metadata that the camera app wrote but BitmapFactory does not honor.

**How to avoid:** Only apply EXIF correction to gallery-imported images, not to CameraX captures. In the import flow (HomeFragment.handleImportResult and handleGalleryResult), correct orientation when adding pages. CameraX captures flow through CameraFragment.takePhoto which saves correctly oriented images.

**Warning signs:** CameraX-captured images appear rotated 90/180 degrees after EXIF "correction".

### Pitfall 6: Undo/Redo Scope Creep

**What goes wrong:** Implementing full undo/redo for the PDF editor is a significant feature (annotation stack, state snapshots, memory management). In a stability phase, this could consume disproportionate time.

**Why it happens:** The requirement says "fully implemented OR buttons removed." The simpler path is removal.

**How to avoid:** For Phase 1, remove the undo/redo buttons and their toast handlers. The buttons can be re-added in a future phase when the feature is properly designed with an annotation history stack. This satisfies BUG-07 with minimal risk.

**Warning signs:** Spending more than 1 hour on undo/redo implementation in a stability phase.

## Code Examples

### Specific Files and What Must Change

#### 1. ScannerViewModel.kt -- BUG-04, BUG-05

```kotlin
// Source: Direct code audit of /app/src/main/java/.../viewmodel/ScannerViewModel.kt

// Current: class ScannerViewModel : ViewModel()
// Change to: class ScannerViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel()

// Current line 72:
//   private val _pages = MutableLiveData<MutableList<Uri>>(mutableListOf())
// Change to:
//   val pages: LiveData<List<Uri>> = savedStateHandle.getLiveData(KEY_PAGES, emptyList())

// Current line 142:
//   private val _pageFilters = MutableLiveData<MutableMap<Int, ImageProcessor.FilterType>>(mutableMapOf())
// Change to use immutable map pattern with SavedStateHandle:
//   Store as Map<Int, String> (enum name), convert on read
```

#### 2. PagesFragment.kt -- BUG-01, BUG-06

```kotlin
// Source: Direct code audit of /app/src/main/java/.../ui/PagesFragment.kt

// 33 requireContext() calls. Dangerous ones are in:
// - performOcrOnPages() lines 231-271 (withContext(Main) after IO)
// - createPdfFromSelection() lines 536-564 (withContext(Main) after IO)
// - createPdf() lines 783-823 (withContext(Main) after IO)
// - rotatePage() lines 675-698 (withContext(Main) after IO)

// Also: generatePdf() line 942:
//   val pdfsDir = File(requireContext().filesDir, "pdfs")
// This runs on IO thread! Must capture context before switching.

// Also: decodeSampledBitmap() lines 990-1012:
//   requireContext().contentResolver.openInputStream(uri)
// This runs on IO thread within generatePdf. Capture context first.

// Also: rotateImage() line 711:
//   val inputStream = requireContext().contentResolver.openInputStream(uri)
// This runs on IO thread. Capture context.
```

#### 3. PdfEditorFragment.kt -- BUG-01, BUG-07

```kotlin
// Source: Direct code audit of /app/src/main/java/.../editor/PdfEditorFragment.kt

// Lines 88-95 (undo/redo placeholders):
binding.btnUndo.setOnClickListener {
    // TODO: Implement undo
    Toast.makeText(context, "Undo coming soon!", Toast.LENGTH_SHORT).show()
}
binding.btnRedo.setOnClickListener {
    // TODO: Implement redo
    Toast.makeText(context, "Redo coming soon!", Toast.LENGTH_SHORT).show()
}
// FIX: Remove these buttons from the layout XML and their click listeners.

// Line 470: requireContext() inside lifecycleScope.launch(Dispatchers.IO):
//   val renderer = PdfAnnotationRenderer(requireContext())
// This is on IO thread! Must capture context before launch or use applicationContext.
```

#### 4. NativePdfView.kt -- BUG-02, BUG-03

```kotlin
// Source: Direct code audit of /app/src/main/java/.../editor/NativePdfView.kt

// Line 119: Temp file created but never tracked:
//   val tempFile = File(context.cacheDir, "pdf_view_temp_${System.currentTimeMillis()}.pdf")
// No cleanup in close() or onDetachedFromWindow().
// FIX: Track tempFile in a class field, delete in close().

// Lines 94-96: Manual open without use {}:
//   fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
//   pdfRenderer = PdfRenderer(fileDescriptor!!)
// FIX: These are stored as fields for lifecycle management. The close() method handles them.
// However, close() should be more robust with individual try-catch per resource.
```

#### 5. PdfUtils.kt -- BUG-02

```kotlin
// Source: Direct code audit of /app/src/main/java/.../util/PdfUtils.kt

// mergePdfs lines 108-139: Manual close for pfd and renderer
// splitPdf lines 192-260: Manual close
// compressPdf lines 374-483: Manual close
// getPageCount lines 488-499: Manual close

// All need conversion to use {} blocks. Example for getPageCount:
// BEFORE:
//   val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r") ?: return@withContext 0
//   val renderer = PdfRenderer(pfd)
//   val count = renderer.pageCount
//   renderer.close()
//   pfd.close()
//   count

// AFTER:
//   context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
//       PdfRenderer(pfd).use { renderer ->
//           renderer.pageCount
//       }
//   } ?: 0
```

#### 6. Temp File Cleanup -- BUG-03

```kotlin
// Add to MainActivity.kt or Application.onCreate():
fun cleanupStaleTempFiles(context: Context) {
    val cacheDir = context.cacheDir
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
}
```

#### 7. EXIF Orientation -- BUG-08

```kotlin
// Add utility function, apply in import flows only (not CameraX captures)
// Apply in: HomeFragment.handleGalleryResult, HomeFragment.handleImportResult
// before calling viewModel.addPage(uri)

// For gallery imports, create a corrected copy:
fun correctExifOrientation(context: Context, sourceUri: Uri): Uri {
    val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return sourceUri
    val exif = ExifInterface(inputStream)
    inputStream.close()

    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )
    if (orientation == ExifInterface.ORIENTATION_NORMAL ||
        orientation == ExifInterface.ORIENTATION_UNDEFINED) {
        return sourceUri  // No correction needed
    }

    // Decode, rotate, save corrected copy
    val bitmap = context.contentResolver.openInputStream(sourceUri)?.use {
        BitmapFactory.decodeStream(it)
    } ?: return sourceUri

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        // Handle flip cases too
    }

    val corrected = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (corrected != bitmap) bitmap.recycle()

    val outputFile = File(context.filesDir, "scans/IMPORT_${System.currentTimeMillis()}.jpg")
    outputFile.parentFile?.mkdirs()
    FileOutputStream(outputFile).use { out ->
        corrected.compress(Bitmap.CompressFormat.JPEG, 90, out)
    }
    corrected.recycle()

    return Uri.fromFile(outputFile)
}
```

## Detailed Coroutine Safety Audit

Files ranked by risk (requireContext count in coroutine callbacks):

| File | Total requireContext | In Coroutine Callbacks | Risk Level | Key Danger Spots |
|------|---------------------|----------------------|------------|------------------|
| HistoryFragment.kt | 42 | ~12 (executeMerge, executeSplit, executeCompress) | HIGH | Long-running PDF operations; user likely to navigate away |
| PagesFragment.kt | 33 | ~14 (createPdf, createPdfFromSelection, performOcrOnPages, rotatePage, generatePdf on IO) | HIGH | PDF generation is the longest operation in the app |
| HomeFragment.kt | 29 | ~10 (performMerge, performSplit, executeCompress, handleImportResult) | HIGH | Import can process multiple PDFs sequentially |
| PdfEditorFragment.kt | 18 | ~4 (saveAnnotatedPdf on IO thread, loadPdf) | MEDIUM | Save operation; user may back-navigate |
| CameraFragment.kt | 13 | ~2 (onImageSaved callback, handleScannerResult) | LOW | Callbacks are quick; less likely to have fragment detached |
| PreviewFragment.kt | 8 | ~3 (saveFilteredImageAndAddPage, applyFilterToPreview) | LOW | Filter apply is fast; save is medium-duration |
| SettingsFragment.kt | 5 | 0 (no coroutines) | SAFE | All synchronous |
| PdfViewerFragment.kt | 1 | 1 (renderPage error toast) | LOW | Single call in catch block |
| Dialog fragments | 4 total | 0 | SAFE | All synchronous |

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `requireContext()` in callbacks | `context ?: return` or `viewLifecycleOwner` scope checks | Android Jetpack guidance since ~2020 | Prevents fragment detachment crashes |
| Manual `close()` calls | Kotlin `use {}` / try-with-resources | Kotlin 1.0 (2016) | Prevents resource leaks on exception paths |
| `MutableLiveData<MutableList>` | `MutableLiveData<List>` with copy-on-write | Android architecture best practice since ~2019 | Prevents concurrent modification and silent data loss |
| `onSaveInstanceState` | `SavedStateHandle` in ViewModel | AndroidX Lifecycle 2.2.0 (2020) | Cleaner API, ViewModel-scoped, type-safe |
| Manual EXIF parsing | `androidx.exifinterface:exifinterface` | Available since support library days | Handles all 8 orientation cases, all Android versions |

## Open Questions

1. **Undo/Redo: Remove vs Implement?**
   - What we know: BUG-07 says "fully implemented or buttons removed." Removal is the safe path for a stability phase. Implementation requires annotation history stack (significant scope).
   - What's unclear: Whether the user expects this feature to be implemented in this phase or just the placeholder removed.
   - Recommendation: Remove buttons for Phase 1. This is the lower-risk option. An undo/redo feature can be added as a proper enhancement in a future phase if desired.

2. **PreviewFragment.loadFullResBitmap -- should we cap it?**
   - What we know: BUG-06 specifically targets PDF generation (cap to PDF output size). `loadFullResBitmap` in PreviewFragment loads full resolution for filter application before adding to pages.
   - What's unclear: Whether capping preview filter resolution will noticeably affect PDF output quality.
   - Recommendation: Cap to 2x the PDF page size (1190x1684 for A4 at 144dpi). This is more than sufficient for PDF output quality and prevents OOM on 48MP camera images.

3. **SavedStateHandle -- what exactly to persist?**
   - What we know: Page URIs (List<Uri>), current PDF base name, page filters map. These are the user's in-progress work.
   - What's unclear: Whether `_pdfUri` (generated PDF), `_isLoading`, and `_currentCaptureUri` should also be persisted.
   - Recommendation: Persist pages, pageFilters, pdfBaseName. Do NOT persist isLoading (transient UI state), currentCaptureUri (camera state is session-specific), or pdfUri (must regenerate).

## Sources

### Primary (HIGH confidence)
- Direct codebase analysis: all 31 Kotlin source files in `/app/src/main/java/com/pdfscanner/app/`
- `build.gradle.kts` dependency versions verified
- Android PdfRenderer API behavior (platform documentation)
- Kotlin `use {}` semantics (Kotlin stdlib documentation)
- Fragment lifecycle and coroutine cancellation semantics (AndroidX Fragment 1.6.x, Lifecycle 2.6.x)

### Secondary (MEDIUM confidence)
- SavedStateHandle API usage patterns (AndroidX Lifecycle documentation)
- ExifInterface handling for all 8 orientation cases (AndroidX ExifInterface documentation)
- `inSampleSize` behavior and memory calculations (Android BitmapFactory documentation)

### Tertiary (LOW confidence)
- `androidx.exifinterface:exifinterface:1.3.7` version number (training data, verify on Maven Central)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - all dependencies except ExifInterface are already in the project
- Architecture: HIGH - all patterns are standard Android/Kotlin idioms verified against actual code
- Pitfalls: HIGH - every pitfall is derived from direct code inspection with line numbers
- Code examples: HIGH - every example references actual lines in the codebase

**Research date:** 2026-02-28
**Valid until:** 2026-04-28 (60 days -- stable Android patterns, not fast-moving)
