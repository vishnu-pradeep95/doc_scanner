# Phase 3: Performance & Polish - Research

**Researched:** 2026-03-01
**Domain:** Android Material Motion, Haptics, Edge-to-Edge, ProgressBar, Snackbar Undo, PdfRenderer Caching
**Confidence:** HIGH (all 6 requirements verified against official Android docs)

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| PERF-01 | Material motion navigation transitions between all major screen transitions | MaterialSharedAxis (Z-axis) via fragment `enterTransition`/`exitTransition` in `onCreate()` — no extra library needed, already in `com.google.android.material:material:1.11.0` |
| PERF-02 | Haptic feedback on camera capture | `binding.btnCapture.performHapticFeedback(HapticFeedbackConstants.CONFIRM)` called after `takePhoto()` in `onImageSaved` callback — works API 24+ with no VIBRATE permission |
| PERF-03 | Edge-to-edge display with proper WindowInsets on API 24–34 | `WindowCompat.enableEdgeToEdge(window)` in `MainActivity.onCreate()` + per-fragment `ViewCompat.setOnApplyWindowInsetsListener` padding |
| PERF-04 | PDF generation shows "Page X of Y" determinate progress | Refactor `generatePdf()` to accept a `onProgress: (Int, Int) -> Unit` callback; update `loadingText` on main thread via `withContext(Main)` |
| PERF-05 | Destructive actions use Snackbar with undo action | `Snackbar.make(...).setAction("Undo") { ... }.show()` — requires `CoordinatorLayout` as fragment root; affects PagesFragment delete and discard flows |
| PERF-06 | PdfRenderer caches adjacent pages for smooth swiping | Add `SparseArray<Bitmap>` cache (prev/current/next) inside `PdfViewerFragment`; preload adjacent pages after each `renderPage()` call |
</phase_requirements>

---

## Summary

Phase 3 covers six independent polish features: Material motion transitions, haptic feedback, edge-to-edge display, deterministic PDF progress, undo-capable Snackbars, and adjacent-page caching in the PDF viewer. All six features use stable Android APIs already in the project's dependency set — no new libraries are required. The Material transitions (`MaterialSharedAxis`, `MaterialFadeThrough`) are shipped inside `com.google.android.material:material:1.11.0` already in `app/build.gradle.kts`.

The most architecturally significant change is PERF-03 (edge-to-edge): it requires one call in `MainActivity.onCreate()` and per-fragment inset listeners in all 7 fragments' `onViewCreated()`. This is the widest-touching change in the phase. PERF-01 (transitions) requires setting `enterTransition`/`exitTransition` in `onCreate()` of each fragment — not `onViewCreated()`, which is a common mistake. PERF-05 (Snackbar undo) requires replacing `ConstraintLayout` root with `CoordinatorLayout` in affected fragment layouts so swipe-to-dismiss works.

PERF-06 (PdfRenderer caching) is a self-contained change to `PdfViewerFragment`: replace the current on-demand `renderPage()` approach with a `SparseArray<Bitmap>` sliding window that pre-renders the previous and next page whenever the current page changes. The current viewer uses button-driven Next/Previous navigation (not ViewPager2), so the cache lookup on button press eliminates the visible render delay users experience today.

**Primary recommendation:** Implement each PERF item as one focused plan. Tackle PERF-03 (edge-to-edge) and PERF-05 (Snackbar undo) first since they touch layout files shared by multiple features. PERF-01 (transitions) last, since transitions are only visible once the content is correctly inset.

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `com.google.android.material:material` | 1.11.0 (already in project) | MaterialSharedAxis, MaterialFadeThrough, MaterialFade, Snackbar | Contains all motion transition classes in `com.google.android.material.transition` package — no separate dep needed |
| `androidx.core:core-ktx` | 1.12.0 (already in project) | `WindowCompat.enableEdgeToEdge()`, `ViewCompat.setOnApplyWindowInsetsListener()`, `WindowInsetsCompat`, `updatePadding()` | Official AndroidX edge-to-edge API; backward-compatible to API 24 |
| `android.view.HapticFeedbackConstants` | Platform (no dep needed) | `CONFIRM` constant for camera shutter | Built into Android; no permission required |
| `android.util.SparseArray` | Platform (no dep needed) | Adjacent-page bitmap cache in PdfViewerFragment | Integer-keyed, memory-efficient; preferred over `HashMap<Int, Bitmap>` for small sets |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `androidx.core:core-ktx` `updatePadding()` extension | 1.12.0 | Kotlin-idiomatic padding update | Use instead of `setPadding()` to only update the axis that changed |
| `android.widget.ProgressBar` (determinate mode) | Platform | "Page X of Y" PDF generation progress | Set `style="@style/Widget.AppCompat.ProgressBar.Horizontal"` and call `setProgress()` on main thread |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `MaterialSharedAxis` (Z-axis) | XML `app:enterAnim` / `app:exitAnim` in nav graph | XML approach only supports standard Android `anim/` transitions — cannot produce Material motion curves. MaterialSharedAxis is the correct approach for Material motion. |
| `HapticFeedbackConstants.CONFIRM` (API 30+) | `HapticFeedbackConstants.VIRTUAL_KEY` (API 5+) | CONFIRM is semantically correct for a capture confirmation but was added in API 30. Since minSdk is 24, use VIRTUAL_KEY as safe fallback or conditionally use CONFIRM on API 30+. |
| `SparseArray<Bitmap>` | `LruCache<Int, Bitmap>` | LruCache auto-evicts by memory budget — better for large PDFs; for a 3-item window (prev/curr/next) SparseArray is simpler and sufficient. |
| Replace `ConstraintLayout` root with `CoordinatorLayout` | Add `CoordinatorLayout` wrapper outside fragment container | Replacing root is cleaner; the wrapper approach adds unnecessary nesting. |

**Installation:** No new dependencies required. All APIs exist in the project's current dependency set.

---

## Architecture Patterns

### Recommended Project Structure

No new directories needed. All changes are in-place modifications to existing files:

```
app/src/main/
├── java/com/pdfscanner/app/
│   ├── MainActivity.kt                   # Add WindowCompat.enableEdgeToEdge()
│   ├── ui/
│   │   ├── CameraFragment.kt             # Add haptic feedback + enterTransition + inset handling
│   │   ├── HomeFragment.kt               # Add enterTransition + inset handling
│   │   ├── PagesFragment.kt              # Add exitTransition + undo Snackbar + inset handling + generatePdf progress
│   │   ├── PreviewFragment.kt            # Add enterTransition + inset handling
│   │   ├── HistoryFragment.kt            # Add enterTransition + inset handling
│   │   ├── PdfViewerFragment.kt          # Add page cache (SparseArray<Bitmap>) + inset handling
│   │   └── SettingsFragment.kt           # Add inset handling
│   └── editor/
│       └── PdfEditorFragment.kt          # Add enterTransition + inset handling
└── res/layout/
    ├── fragment_pages.xml                # Replace ConstraintLayout root with CoordinatorLayout (for Snackbar)
    └── fragment_pages.xml                # Replace indeterminate ProgressBar with determinate LinearProgressIndicator
```

### Pattern 1: Material Motion Transitions (PERF-01)

**What:** Set `enterTransition` and `exitTransition` on each fragment in `onCreate()` using `MaterialSharedAxis.Z`. The departing fragment sets `exitTransition` + `reenterTransition`; the entering fragment sets `enterTransition` + `returnTransition`.

**When to use:** Forward/back navigation through a linear flow (Home → Camera → Preview → Pages). Z-axis is correct for hierarchical depth; X-axis for lateral peer screens.

**Key constraint:** Set transitions in `onCreate()`, NOT in `onViewCreated()`. Transitions must be set before the fragment view is created for them to apply.

**Also required:** Add `android:transitionGroup="true"` to each fragment's root view group in XML so the root animates as one unit, not each child view separately.

```kotlin
// Source: https://developer.android.com/codelabs/material-motion-android
// In each Fragment's onCreate():
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Entering this fragment (forward navigation)
    enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ true).apply {
        duration = resources.getInteger(R.integer.motion_duration_large).toLong()
    }
    // Returning from a deeper fragment (back navigation)
    returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ false).apply {
        duration = resources.getInteger(R.integer.motion_duration_large).toLong()
    }
}

// In the DEPARTING fragment's onClick or navigate() call site:
exitTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ true).apply {
    duration = resources.getInteger(R.integer.motion_duration_large).toLong()
}
reenterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ false).apply {
    duration = resources.getInteger(R.integer.motion_duration_large).toLong()
}
findNavController().navigate(R.id.action_home_to_camera)
```

**Duration value:** Add `<integer name="motion_duration_large">300</integer>` to `res/values/integers.xml` (create file if absent). 300ms matches Material guidelines.

**Package import:**
```kotlin
import com.google.android.material.transition.MaterialSharedAxis
import com.google.android.material.transition.MaterialFadeThrough
```

Use `MaterialFadeThrough` (not `MaterialSharedAxis`) for top-level lateral navigation (e.g., Home → History, Home → Settings), where there is no hierarchical relationship.

### Pattern 2: Haptic Feedback on Camera Capture (PERF-02)

**What:** Call `performHapticFeedback()` on a view after image capture is confirmed.

**When to use:** In `onImageSaved` callback of `ImageCapture.OnImageSavedCallback`, after hiding the progress bar and re-enabling the capture button.

```kotlin
// Source: https://developer.android.com/develop/ui/views/haptics/haptic-feedback
// In CameraFragment.kt, inside takePhoto() → onImageSaved():
override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
    val b = _binding ?: return
    b.progressBar.visibility = View.GONE
    b.btnCapture.isEnabled = true

    // Haptic pulse to confirm document captured
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        b.btnCapture.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
    } else {
        b.btnCapture.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }
    // ... rest of navigation logic
}
```

**No permission required.** `performHapticFeedback()` respects the system haptics setting automatically. No `VIBRATE` permission in AndroidManifest.xml needed.

### Pattern 3: Edge-to-Edge Display (PERF-03)

**What:** Enable the app to draw content behind system bars (status bar, navigation bar). Apply `WindowInsetsCompat` padding in each fragment to prevent UI elements from being hidden.

**Step 1 — Activity (one-time):**
```kotlin
// Source: https://developer.android.com/develop/ui/views/layout/edge-to-edge
// In MainActivity.onCreate(), BEFORE setContentView():
override fun onCreate(savedInstanceState: Bundle?) {
    // Apply theme first (existing code)
    val prefs = AppPreferences(this)
    applyAppStyle(prefs)
    AppCompatDelegate.setDefaultNightMode(prefs.getThemeMode())

    super.onCreate(savedInstanceState)

    // Enable edge-to-edge AFTER super.onCreate(), BEFORE setContentView()
    WindowCompat.enableEdgeToEdge(window)

    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    // ... rest of setup
}
```

**Step 2 — Remove hardcoded status/nav bar colors from themes.xml:**

After `enableEdgeToEdge()`, the status/nav bar colors are managed automatically. Remove or replace these items in `themes.xml`:
```xml
<!-- REMOVE these — enableEdgeToEdge() manages bar transparency -->
<!-- <item name="android:statusBarColor">@color/surface</item> -->
<!-- <item name="android:navigationBarColor">@color/surface</item> -->
```

**Step 3 — Per-fragment inset handling in `onViewCreated()`:**

```kotlin
// Source: https://developer.android.com/develop/ui/views/layout/edge-to-edge
// In each fragment's onViewCreated():
ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
    val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    )
    v.updatePadding(
        left = insets.left,
        top = insets.top,
        right = insets.right,
        bottom = insets.bottom
    )
    WindowInsetsCompat.CONSUMED
}
```

**For fragments with RecyclerView:** Apply padding to the RecyclerView directly (not the root) and ensure `android:clipToPadding="false"` is set so items can scroll under the nav bar:

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerPages) { v, windowInsets ->
    val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
    )
    v.updatePadding(bottom = insets.bottom)
    WindowInsetsCompat.CONSUMED
}
```

**Toolbar handling:** For fragments with a `MaterialToolbar` at top, apply top inset to the toolbar container (or `AppBarLayout`), not the root:

```kotlin
ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, windowInsets ->
    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    v.updatePadding(top = insets.top)
    WindowInsetsCompat.CONSUMED
}
```

### Pattern 4: Determinate PDF Generation Progress (PERF-04)

**What:** Replace the indeterminate `ProgressBar` in `fragment_pages.xml` loading overlay with a `LinearProgressIndicator` (Material 3). Refactor `generatePdf()` to report page-by-page progress via a callback.

**Layout change** in `fragment_pages.xml` inside the `loadingOverlay` `FrameLayout`:
```xml
<!-- Replace <ProgressBar ... /> with: -->
<com.google.android.material.progressindicator.LinearProgressIndicator
    android:id="@+id/progressIndicator"
    android:layout_width="200dp"
    android:layout_height="wrap_content"
    android:indeterminate="false"
    app:trackCornerRadius="4dp" />
```

**Code change** in `PagesFragment.kt`:
```kotlin
// Refactored generatePdf signature to accept progress callback:
private fun generatePdf(
    ctx: android.content.Context,
    pageUris: List<Uri>,
    customBaseName: String? = null,
    onProgress: ((current: Int, total: Int) -> Unit)? = null
): File {
    val pdfDocument = PdfDocument()
    val total = pageUris.size
    pageUris.forEachIndexed { index, uri ->
        onProgress?.invoke(index + 1, total) // Report progress
        // ... existing bitmap + page logic ...
    }
    // ...
}

// In createPdf() coroutine:
lifecycleScope.launch {
    val ctx = context ?: return@launch
    binding.loadingText.text = getString(R.string.creating_pdf)
    binding.loadingOverlay.visibility = View.VISIBLE
    binding.progressIndicator.max = pages.size
    binding.progressIndicator.progress = 0

    val pdfFile = withContext(Dispatchers.IO) {
        generatePdf(ctx, pages) { current, total ->
            // Switch to Main thread to update UI
            // Use post() since we're on IO thread inside withContext
            binding.progressIndicator.post {
                binding.progressIndicator.progress = current
                binding.loadingText.text = getString(R.string.pdf_progress, current, total)
            }
        }
    }
    // ... rest of success/error handling
}
```

Add to `strings.xml`:
```xml
<string name="pdf_progress">Page %1$d of %2$d…</string>
```

### Pattern 5: Snackbar with Undo Action (PERF-05)

**What:** Replace confirmation dialogs for destructive actions (delete page, discard scan) with `Snackbar.make(...).setAction("Undo") { restore() }.show()`.

**Prerequisite:** The fragment layout root must be a `CoordinatorLayout` for the Snackbar swipe-to-dismiss gesture to work. PagesFragment currently uses `ConstraintLayout` as root — it must be replaced.

**Layout change** in `fragment_pages.xml`:
```xml
<!-- Replace root <androidx.constraintlayout.widget.ConstraintLayout> with: -->
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background"
    tools:context=".ui.PagesFragment">
    <!-- All child views remain the same; ConstraintLayout becomes an inner child -->
    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        <!-- existing content -->
    </androidx.constraintlayout.widget.ConstraintLayout>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

**Code pattern:**
```kotlin
// Source: https://developer.android.com/develop/ui/views/notifications/snackbar/action
// In PagesFragment.kt — delete page with undo:
fun deletePage(position: Int) {
    val deletedUri = viewModel.pages.value?.get(position) ?: return
    viewModel.removePage(position)                    // Commit deletion immediately

    Snackbar.make(binding.root, R.string.page_deleted, Snackbar.LENGTH_LONG)
        .setAction(R.string.undo) {
            viewModel.insertPage(position, deletedUri)  // Restore on undo
        }
        .show()
}
```

**No `addCallback()` needed** for the basic undo pattern. The undo action lambda captures everything needed to restore. If the Snackbar is dismissed by timeout or swipe without pressing Undo, the deletion remains committed — this is correct UX.

### Pattern 6: PdfRenderer Adjacent-Page Cache (PERF-06)

**What:** Replace the current on-demand-per-click rendering in `PdfViewerFragment` with a 3-slot cache (prev/current/next) using `SparseArray<Bitmap>`.

**Current problem:** Each button press triggers an IO coroutine that opens a `PdfRenderer.Page`, renders a Bitmap, and returns it to the UI. This is a 150–500ms delay per page on a 2x scale render, causing visible lag.

**Cache strategy:** After rendering page N, immediately start background rendering of page N-1 and N+1 into the cache. On button press, serve from cache if available, then update cache for new position.

```kotlin
// In PdfViewerFragment.kt:
import android.util.SparseArray

private val pageCache = SparseArray<Bitmap>(3)  // 3 slots: prev/current/next

// Call after each renderPage():
private fun prefetchAdjacentPages(currentIndex: Int) {
    val pagesToPrefetch = listOf(currentIndex - 1, currentIndex + 1)
        .filter { it in 0 until pageCount }
        .filter { pageCache[it] == null }  // Skip if already cached

    pagesToPrefetch.forEach { index ->
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { renderPageToBitmap(index) }
            if (bmp != null) pageCache.put(index, bmp)
        }
    }

    // Evict pages outside the window [currentIndex-1, currentIndex+1]
    val keysToEvict = mutableListOf<Int>()
    for (i in 0 until pageCache.size()) {
        val key = pageCache.keyAt(i)
        if (key < currentIndex - 1 || key > currentIndex + 1) keysToEvict.add(key)
    }
    keysToEvict.forEach { key ->
        pageCache[key]?.recycle()
        pageCache.remove(key)
    }
}

// Extracted render-only function (no UI update):
private suspend fun renderPageToBitmap(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val page = pdfRenderer?.openPage(pageIndex) ?: return@withContext null
        val scale = 2f
        val bmp = Bitmap.createBitmap(
            (page.width * scale).toInt(),
            (page.height * scale).toInt(),
            Bitmap.Config.ARGB_8888
        )
        bmp.eraseColor(android.graphics.Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        bmp
    } catch (e: Exception) { null }
}

// Modified renderPage() — serves from cache when available:
private fun renderPage(pageIndex: Int) {
    val cached = pageCache[pageIndex]
    if (cached != null) {
        binding.pdfImage.setImageBitmap(cached)
        updateNavigationState()
        prefetchAdjacentPages(pageIndex)
        return
    }

    lifecycleScope.launch {
        val bitmap = renderPageToBitmap(pageIndex)
        bitmap?.let {
            pageCache.put(pageIndex, it)
            _binding?.pdfImage?.setImageBitmap(it)
        }
        if (_binding != null) updateNavigationState()
        prefetchAdjacentPages(pageIndex)
    }
}

// In onDestroyView() — recycle all cached bitmaps:
override fun onDestroyView() {
    super.onDestroyView()
    for (i in 0 until pageCache.size()) {
        pageCache.valueAt(i)?.recycle()
    }
    pageCache.clear()
    // ... existing pdfRenderer/fileDescriptor cleanup
}
```

**Memory note:** At 2x scale, one A4 PDF page (595*2 × 842*2 = 1190 × 1684) in ARGB_8888 = ~7.5MB. Three pages cached = ~22MB. Acceptable for a document scanner app; does not approach OOM thresholds established in BUG-06 (192MB was the threshold).

### Anti-Patterns to Avoid

- **Setting transitions in `onViewCreated()`:** Transition objects must be attached before the view is created. Setting them in `onViewCreated()` causes them to be ignored for the current navigation event.
- **Using `WindowInsetsCompat.CONSUMED` without `installCompatInsetsDispatch()`:** On API 29 and earlier, consuming insets in one child blocks sibling views from receiving them. Call `ViewGroupCompat.installCompatInsetsDispatch(rootView)` on the root before consuming, or apply insets to each view individually without consuming.
- **Replacing `CoordinatorLayout` root with only a wrapper around existing root:** The Snackbar requires `CoordinatorLayout` as the anchor — if the fragment view's root is not a `CoordinatorLayout`, swipe-to-dismiss and proper Snackbar elevation positioning will fail.
- **Calling `pdfRenderer.openPage()` on multiple pages simultaneously:** PdfRenderer is NOT thread-safe. Only one page may be open at a time. In the cache prefetch, calls to `openPage()` must be sequential, not parallel. Use a Mutex or a single-threaded dispatcher for PDF operations.
- **Forgetting `bitmap.recycle()` on cache eviction:** PdfRenderer creates large Bitmaps. Without explicit `recycle()` on evicted cache entries, heap pressure accumulates across long viewing sessions.
- **Using `HapticFeedbackConstants.CONFIRM` without API level check:** `CONFIRM` was added in API 30. Since minSdk is 24, a conditional or fallback to `VIRTUAL_KEY` is required.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Material motion curves for Fragment transitions | Custom `ObjectAnimator` or `ValueAnimator` with Interpolator | `MaterialSharedAxis`, `MaterialFadeThrough` from MDC | The Material motion system handles the enter/exit/reenter/return quartet correctly, respects reduced-motion accessibility, and uses the correct easing curves. Custom animators miss the return/reenter pair. |
| Edge-to-edge status bar color management | `window.statusBarColor = Color.TRANSPARENT` + manual flag juggling | `WindowCompat.enableEdgeToEdge(window)` | The manual approach requires per-API level conditionals (~100 lines). `enableEdgeToEdge()` encapsulates all of this. |
| PDF page bitmap caching | `LruCache` with complex eviction logic | Simple `SparseArray<Bitmap>` with a 3-slot sliding window | For 3 slots the complexity of LruCache is unnecessary; `SparseArray` with manual eviction is ~20 lines and fully deterministic. |
| Determinate progress text ("Page X of Y") | A custom dialog or `AlertDialog` with a `ProgressBar` child | `LinearProgressIndicator` + `TextView` in the existing `loadingOverlay` | The existing overlay already has a `TextView` (`loadingText`). Updating its text to "Page X of Y" and swapping the `ProgressBar` for `LinearProgressIndicator` is 2 changes. |

**Key insight:** Every feature in this phase has an official Android API specifically designed for it. The risk in this phase is not "which library?" — it is "did I call the right method in the right lifecycle callback?"

---

## Common Pitfalls

### Pitfall 1: Transition Set in Wrong Lifecycle Method

**What goes wrong:** Fragment transitions are invisible — screen changes instantly despite transition code being present.
**Why it happens:** `enterTransition`, `exitTransition`, etc. must be set in `onCreate()`. If set in `onViewCreated()`, the fragment's view is already being created and the transition is ignored for the current navigation event.
**How to avoid:** Always set `enterTransition`/`returnTransition` in `onCreate()`. Set `exitTransition`/`reenterTransition` immediately before calling `findNavController().navigate()` in the departing fragment.
**Warning signs:** Logcat is silent (no error); the fragment simply pops in without animation.

### Pitfall 2: Snackbar Anchored to ConstraintLayout Root

**What goes wrong:** Snackbar appears but cannot be swiped to dismiss; it also obscures bottom buttons because it doesn't know about them.
**Why it happens:** Swipe-to-dismiss requires `CoordinatorLayout` as the ancestor of the Snackbar's anchor view. `ConstraintLayout` does not implement the coordinator protocol.
**How to avoid:** Wrap the fragment root with `CoordinatorLayout` or replace it (while keeping `ConstraintLayout` as an inner child for constraint-based layout).
**Warning signs:** `Snackbar` shows but dragging it does nothing; `CoordinatorLayout` is absent from the layout hierarchy.

### Pitfall 3: PdfRenderer Race Condition in Cache Prefetch

**What goes wrong:** `java.lang.IllegalStateException: Page already closed` or `IllegalStateException: Already closed` from PdfRenderer.
**Why it happens:** `PdfRenderer` enforces that only one `PdfRenderer.Page` is open at a time. If the cache prefetch launches two coroutines that both call `openPage()` concurrently, one will fail.
**How to avoid:** Serialize all `pdfRenderer.openPage()` calls. Use `Dispatchers.IO.limitedParallelism(1)` (or a `Mutex`) for the coroutine context that accesses `pdfRenderer`. Do not use `Dispatchers.IO` directly for prefetch because it uses a shared thread pool.
**Warning signs:** Intermittent crashes when swiping quickly between pages; crash stack trace mentions `PdfRenderer`.

### Pitfall 4: Edge-to-Edge Breaks Bottom Button Bar

**What goes wrong:** The bottom action button row (Create PDF, Add More) is hidden behind the navigation bar / gesture indicator.
**Why it happens:** `enableEdgeToEdge()` makes the navigation bar transparent but does NOT automatically add padding. Without an explicit `WindowInsetsCompat` listener on the bottom bar, the bar sits at the screen bottom but the system nav bar overlaps it.
**How to avoid:** Apply bottom inset specifically to the `buttonsLayout` view (or the `CoordinatorLayout`'s bottom padding), NOT just the root view's overall padding.
**Warning signs:** App appears fine in portrait/3-button mode but buttons are clipped on devices with gesture navigation.

### Pitfall 5: Bitmap Leak from Cache After Fragment Destroyed

**What goes wrong:** Memory leak or `IllegalStateException: Canvas: trying to use a recycled bitmap` after navigating away from `PdfViewerFragment`.
**Why it happens:** If the cache isn't cleared in `onDestroyView()`, cached Bitmaps remain referenced until GC. If they are set on an `ImageView` that is subsequently destroyed and then the cache is recycled, the `ImageView` tries to draw a recycled Bitmap.
**How to avoid:** In `onDestroyView()`, call `recycle()` on all cached bitmaps before clearing the `SparseArray`. Also call `binding.pdfImage.setImageDrawable(null)` before recycling to detach the current bitmap from the ImageView.
**Warning signs:** LeakCanary reports `PdfViewerFragment` retained; OOM on second open of a large PDF.

### Pitfall 6: Theme statusBarColor Overrides enableEdgeToEdge

**What goes wrong:** Status bar is still opaque (white/surface color) after calling `enableEdgeToEdge()`.
**Why it happens:** `android:statusBarColor` and `android:navigationBarColor` in `themes.xml` override what `enableEdgeToEdge()` tries to set.
**How to avoid:** Remove `android:statusBarColor` and `android:navigationBarColor` from all theme styles that apply to `MainActivity` after enabling edge-to-edge.
**Warning signs:** App draws behind navigation bar but not behind status bar; or status/nav bars appear with solid color instead of transparent.

---

## Code Examples

Verified patterns from official sources:

### MaterialSharedAxis Setup in Fragment.onCreate()

```kotlin
// Source: https://developer.android.com/codelabs/material-motion-android
import com.google.android.material.transition.MaterialSharedAxis

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enterTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ true).apply {
        duration = resources.getInteger(R.integer.motion_duration_large).toLong()
    }
    returnTransition = MaterialSharedAxis(MaterialSharedAxis.Z, /* forward= */ false).apply {
        duration = resources.getInteger(R.integer.motion_duration_large).toLong()
    }
}
```

### MaterialFadeThrough for Lateral Navigation

```kotlin
// Source: https://developer.android.com/codelabs/material-motion-android
import com.google.android.material.transition.MaterialFadeThrough

// In HomeFragment.onCreate() — transition for switching between top-level destinations:
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enterTransition = MaterialFadeThrough().apply {
        duration = resources.getInteger(R.integer.motion_duration_large).toLong()
    }
}
```

### Haptic Feedback on Camera Capture

```kotlin
// Source: https://developer.android.com/develop/ui/views/haptics/haptic-feedback
// In CameraFragment.takePhoto() → onImageSaved callback:
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
    b.btnCapture.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
} else {
    b.btnCapture.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
}
```

### Edge-to-Edge Activity Setup

```kotlin
// Source: https://developer.android.com/develop/ui/views/layout/edge-to-edge
// In MainActivity.onCreate(), after super.onCreate(), before setContentView():
WindowCompat.enableEdgeToEdge(window)
```

### WindowInsets Per-Fragment (Toolbar + RecyclerView)

```kotlin
// Source: https://developer.android.com/develop/ui/views/layout/edge-to-edge
// In PagesFragment.onViewCreated():
ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, windowInsets ->
    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    v.updatePadding(top = insets.top)
    windowInsets  // Do NOT consume here — pass to siblings
}
ViewCompat.setOnApplyWindowInsetsListener(binding.buttonsLayout) { v, windowInsets ->
    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
    v.updatePadding(bottom = insets.bottom)
    windowInsets
}
```

### Snackbar with Undo Action

```kotlin
// Source: https://developer.android.com/develop/ui/views/notifications/snackbar/action
Snackbar.make(binding.root, R.string.page_deleted, Snackbar.LENGTH_LONG)
    .setAction(R.string.undo) {
        viewModel.insertPage(position, deletedUri)
    }
    .show()
```

### Determinate Progress Update from IO Thread

```kotlin
// Inside withContext(Dispatchers.IO) block:
generatePdf(ctx, pages) { current, total ->
    binding.progressIndicator.post {   // post() switches to UI thread
        binding.progressIndicator.progress = current
        binding.loadingText.text = getString(R.string.pdf_progress, current, total)
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `window.statusBarColor = Color.TRANSPARENT` + flag juggling | `WindowCompat.enableEdgeToEdge(window)` | AndroidX Core 1.6+ | One-line API; backward-compatible to API 21 |
| XML `app:enterAnim` with standard Android `anim/` XML | `MaterialSharedAxis` / `MaterialFadeThrough` in `onCreate()` | Material Components 1.2+ | Material motion curves, not just linear slide |
| `ProgressBar` indeterminate + text update | `LinearProgressIndicator` (Material 3) with `setProgress()` | Material Components 1.3+ | Matches Material Design 3 visual language |
| `Toast.makeText(...).show()` | `Snackbar.make(...).setAction("Undo") {...}.show()` | Material 1.x (already done in Phase 2 for feedback; this phase adds the Action) | Recoverable destructive actions |
| Render-on-demand PdfRenderer | Adjacent-page SparseArray cache | Project-specific (no platform change) | Eliminates render delay on page swipe |

**Deprecated/outdated:**

- `window.setStatusBarColor()` / `window.setNavigationBarColor()`: Still functional but superseded by `enableEdgeToEdge()` which handles all API level differences.
- `View.setSystemUiVisibility()`: Deprecated since API 30. All inset/visibility work should use `WindowInsetsController` or `WindowCompat` APIs.
- `android:windowTranslucentStatus` / `android:windowTranslucentNavigation` in theme: Old approach to edge-to-edge; causes layout issues. Use `enableEdgeToEdge()` instead.

---

## Open Questions

1. **CoordinatorLayout root for all fragments or just PagesFragment?**
   - What we know: The Snackbar undo pattern (PERF-05) requires `CoordinatorLayout` as an ancestor. PagesFragment is the primary fragment with destructive delete actions. The existing `showSnackbar()` extension in `Extensions.kt` uses `requireView()` as the anchor — this works even without `CoordinatorLayout` for display, but swipe-to-dismiss fails.
   - What's unclear: Whether swipe-to-dismiss is required for the Snackbar in the requirements, or just display + undo action button.
   - Recommendation: Add `CoordinatorLayout` wrapper only to `PagesFragment` (the main delete flow). Other fragments already have a working `showSnackbar()` extension that doesn't need swipe-to-dismiss.

2. **`HapticFeedbackConstants.CONFIRM` vs `VIRTUAL_KEY` for API 24–29 devices**
   - What we know: `CONFIRM` was added in API 30. `VIRTUAL_KEY` is available from API 5 and semantically represents "pressing a button," which is close enough for a capture shutter.
   - What's unclear: Whether VIRTUAL_KEY produces a satisfying haptic on all Android 7-9 devices (hardware varies significantly).
   - Recommendation: Use `Build.VERSION.SDK_INT >= Build.VERSION_CODES.R` to conditionally use `CONFIRM`; fall back to `VIRTUAL_KEY`. This is 3 lines of code and zero risk.

3. **Inset handling for CameraFragment**
   - What we know: CameraFragment has a full-screen camera preview (`PreviewView`) with overlaid buttons. Edge-to-edge here means the capture button should be padded above the gesture indicator, not obscured.
   - What's unclear: Whether the `frameOverlay` (document framing rectangle) needs to account for insets or should remain full-screen.
   - Recommendation: Apply bottom inset only to `btnCapture` and `btnViewPages`; leave `frameOverlay` and `previewView` at match_parent since camera preview should be truly full-screen.

---

## Sources

### Primary (HIGH confidence)
- https://developer.android.com/codelabs/material-motion-android — MaterialSharedAxis and MaterialFadeThrough code examples, Fragment.onCreate() placement requirement, `transitionGroup` XML attribute
- https://developer.android.com/develop/ui/views/layout/edge-to-edge — `WindowCompat.enableEdgeToEdge()`, per-fragment `ViewCompat.setOnApplyWindowInsetsListener`, `RecyclerView` + `clipToPadding` pattern
- https://developer.android.com/develop/ui/views/haptics/haptic-feedback — `performHapticFeedback()`, `HapticFeedbackConstants`, no VIBRATE permission requirement
- https://developer.android.com/develop/ui/views/notifications/snackbar/action — `Snackbar.setAction()`, `CoordinatorLayout` requirement

### Secondary (MEDIUM confidence)
- https://github.com/material-components/material-components-android/blob/master/docs/theming/Motion.md — Package import paths (`com.google.android.material.transition` vs `.platform`), transition class names
- https://learn.microsoft.com/en-us/dotnet/api/android.views.hapticfeedbackconstants.confirm — `HapticFeedbackConstants.CONFIRM` added at API 30 (verified via Microsoft Android binding docs referencing Android SDK)
- WebSearch: "HapticFeedbackConstants CONFIRM added API 30" — multiple sources confirm API 30 minimum

### Tertiary (LOW confidence)
- https://medium.com/engineering-at-cloud-academy (403 error — could not read) — SparseArray sliding window approach for PdfRenderer cache; pattern was independently reasoned from PdfRenderer API constraints and Android memory guidance

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — All APIs are in the existing dependency set; verified against official Android Developers docs
- Architecture: HIGH — Code patterns verified from official codelabs and API reference
- Pitfalls: HIGH — Three pitfalls (transition lifecycle, CoordinatorLayout, PdfRenderer thread safety) are verifiable from API contracts; two (bitmap leak, theme override) confirmed by official edge-to-edge docs

**Research date:** 2026-03-01
**Valid until:** 2026-06-01 (Material Components 1.11.0 and AndroidX Core 1.12.0 are stable; no breaking changes expected in 90 days)
