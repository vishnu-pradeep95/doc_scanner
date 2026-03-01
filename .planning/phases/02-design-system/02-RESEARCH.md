# Phase 2: Design System - Research

**Researched:** 2026-02-28
**Domain:** Android Material 3 design tokens, Coil image loading, Toast-to-Snackbar migration, strings.xml i18n hygiene, dark mode validation
**Confidence:** HIGH

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| DSYS-01 | Material type scale in `styles.xml` using Nunito, applied consistently across all screens | Nunito TTF files are already bundled (4 weights). Cartoon theme in `themes_cartoon.xml` already has full TextAppearance.Cartoon.* hierarchy. Work is applying these to layouts that still use hardcoded `textSize`. |
| DSYS-02 | Spacing constants based on 8dp grid in `dimens.xml`, applied consistently | `dimens.xml` does not exist. All margins/paddings are currently hardcoded in layouts. Must create file and migrate. |
| DSYS-03 | Coil integrated for all image/thumbnail loading | Coil is NOT in `build.gradle.kts`. PagesAdapter uses manual BitmapFactory + LruCache. HistoryAdapter shows no thumbnail at all. Must add dependency and replace both. |
| DSYS-04 | All 85 Toast calls replaced with Snackbar or inline messages | 85 actual `Toast.makeText()` call sites across 7 fragment files. HomeFragment already uses CoordinatorLayout (Snackbar anchor available). |
| DSYS-05 | All hardcoded strings moved to `strings.xml` | Multiple layouts have hardcoded English text. PdfEditorFragment has hardcoded Toast strings. Need systematic grep + migrate. |
| DSYS-06 | Emoji removed from programmatic strings | Emoji in layouts: `dialog_save_success.xml` ("Woohoo! 🎉"), `dialog_signature_pro.xml` ("Sign here ✍️"), `layout_example_cartoon_home.xml`. Need to move emoji to drawable or remove. |
| DSYS-07 | Dark mode visually verified on all 7 screens | Dark cartoon theme exists in `values-night/themes.xml`. The non-cartoon `Theme.PDFScanner` dark also exists. Manifest uses `Theme.PDFScanner` (not cartoon). Need to verify all screens render correctly with system dark mode. |
</phase_requirements>

---

## Summary

Phase 2 is a systematic design-system enforcement pass on a feature-complete app. The cartoon theme infrastructure is already in place: `themes_cartoon.xml` defines the full Material 3 type scale with Nunito fonts, `colors_cartoon.xml` has all color tokens, and dark variants exist in `values-night/`. The problem is that the app's manifest uses `Theme.PDFScanner` (the non-cartoon Ghibli theme) rather than `Theme.PDFScanner.Cartoon`, and many layouts ignore the defined text appearances by hardcoding `textSize` values directly. Additionally, Coil has never been added as a dependency — both adapters still load bitmaps manually using BitmapFactory coroutines.

The most impactful work is the Toast-to-Snackbar migration (85 call sites across 7 fragments) and the Coil integration replacing manual bitmap loading. The typography and spacing work is lighter: the token definitions exist and just need to be referenced. The strings/emoji cleanup requires systematic search-and-replace across layouts and Kotlin files.

**Primary recommendation:** Work in four waves — (1) add Coil + dimens.xml + enforce typography tokens, (2) migrate all Toasts to Snackbar, (3) move hardcoded strings/emoji to resources, (4) manually verify dark mode on all 7 screens.

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Coil (`io.coil-kt.coil3:coil`) | 3.4.0 | Image loading, caching, placeholders | Kotlin-first, coroutine-backed, replaces manual BitmapFactory + LruCache with automatic memory/disk caching |
| Material Components | 1.11.0 (already in project) | Snackbar, type scale, CoordinatorLayout behavior | Already on 1.11.0; Snackbar requires no new dependency |
| AndroidX Core KTX | 1.12.0 (already in project) | `ViewCompat.setOnApplyWindowInsetsListener` and string resource access | Already present |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Coil SVG (optional) | 3.4.0 | SVG image support | Only needed if loading SVG files; not required here — app loads JPG/PNG from file URIs |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Coil 3.4.0 | Coil 2.7.0 (`io.coil-kt:coil:2.7.0`) | 2.x has identical `ImageView.load()` API, simpler Maven coords, more Stack Overflow coverage. Both are production-ready. Use 3.4.0 since it's current stable. |
| Coil 3.4.0 | Glide | Glide requires annotation processor, larger method count; Coil is the Kotlin-first standard for this stack |
| Manual Snackbar calls | Extension function wrapper | Reduce 85 call sites to 1-line pattern; recommended for maintainability |

**Gradle dependency to add:**
```kotlin
// app/build.gradle.kts
implementation("io.coil-kt.coil3:coil:3.4.0")
```

No network fetcher needed — all images are loaded from local file URIs (`content://` or `file://`), not HTTP URLs. The base `coil` artifact handles this.

---

## Architecture Patterns

### Recommended Project Structure Changes

```
app/src/main/res/
├── values/
│   ├── dimens.xml          # CREATE: 8dp grid spacing constants
│   ├── strings.xml         # MODIFY: add missing strings
│   ├── themes.xml          # VERIFY: textAppearance references applied
│   └── themes_cartoon.xml  # EXISTING: full type scale already defined
├── values-night/
│   └── themes.xml          # EXISTING: dark cartoon theme
└── font/
    ├── nunito_regular.ttf  # EXISTING
    ├── nunito_semibold.ttf # EXISTING
    ├── nunito_bold.ttf     # EXISTING
    └── nunito_extrabold.ttf # EXISTING
```

### Pattern 1: Coil ImageView Loading (replaces PagesAdapter manual bitmap loading)

**What:** Replace the manual `BitmapFactory` coroutine + LruCache pattern in `PagesAdapter` with Coil's `load()` extension.
**When to use:** Any `ImageView` that currently uses `BitmapFactory.decodeStream()` or loads from a URI on a background thread.

```kotlin
// Source: https://coil-kt.github.io/coil/api/coil/coil3/load.html
import coil3.load
import coil3.request.crossfade

// In PagesAdapter.PageViewHolder.bind():
binding.imageThumbnail.load(uri) {
    crossfade(true)
    placeholder(R.drawable.ic_cartoon_document)  // existing drawable
    error(R.drawable.ic_cartoon_document)
}

// To cancel a request when view is recycled (optional but clean):
// Coil automatically cancels requests when the ImageView is detached
```

**What to remove from PagesAdapter:**
- `thumbnailCache: LruCache<String, Bitmap>` — Coil has built-in memory + disk cache
- `adapterScope: CoroutineScope(Dispatchers.Main + SupervisorJob())` — no longer needed
- `loadThumbnail()` private function — replaced by Coil
- `calculateInSampleSize()` — replaced by Coil

### Pattern 2: Snackbar as Toast Replacement

**What:** Replace `Toast.makeText(context, message, LENGTH).show()` with `Snackbar.make(view, message, LENGTH).show()`.
**When to use:** All feedback that was previously a Toast. Use `LENGTH_SHORT` for success/info, `LENGTH_LONG` for errors.

```kotlin
// Source: https://github.com/material-components/material-components-android/blob/master/docs/components/Snackbar.md

// Instead of:
Toast.makeText(requireContext(), R.string.pdf_created, Toast.LENGTH_SHORT).show()

// Use:
Snackbar.make(binding.root, R.string.pdf_created, Snackbar.LENGTH_SHORT).show()

// With undo action (for destructive operations, but that's Phase 3 PERF-05):
Snackbar.make(binding.root, R.string.document_deleted, Snackbar.LENGTH_LONG)
    .setAction(R.string.undo) { /* undo logic */ }
    .show()
```

**Critical:** `binding.root` must be a `CoordinatorLayout` or child of one for swipe-to-dismiss and proper positioning. `HomeFragment` already has `CoordinatorLayout` as root. For fragments where root is not a `CoordinatorLayout` (e.g., `ConstraintLayout`), pass `requireActivity().findViewById(android.R.id.content)` instead — this anchors the Snackbar in the Activity window.

**Extension function to reduce 85 call-site repetition:**
```kotlin
// Place in a Kotlin extensions file, e.g., ui/Extensions.kt
fun Fragment.showSnackbar(message: String, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.let { Snackbar.make(it, message, duration).show() }
}

fun Fragment.showSnackbar(@StringRes messageRes: Int, duration: Int = Snackbar.LENGTH_SHORT) {
    view?.let { Snackbar.make(it, messageRes, duration).show() }
}
```

### Pattern 3: dimens.xml 8dp Grid

**What:** Define spacing constants that enforce an 8dp baseline grid.
**When to use:** All `layout_margin`, `padding`, and dimension values in layouts.

```xml
<!-- res/values/dimens.xml — CREATE THIS FILE -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 8dp base grid -->
    <dimen name="spacing_xxs">4dp</dimen>
    <dimen name="spacing_xs">8dp</dimen>
    <dimen name="spacing_sm">12dp</dimen>
    <dimen name="spacing_md">16dp</dimen>
    <dimen name="spacing_lg">24dp</dimen>
    <dimen name="spacing_xl">32dp</dimen>
    <dimen name="spacing_xxl">40dp</dimen>

    <!-- Semantic spacing -->
    <dimen name="screen_horizontal_padding">24dp</dimen>
    <dimen name="card_padding">16dp</dimen>
    <dimen name="card_margin">8dp</dimen>
    <dimen name="item_vertical_spacing">8dp</dimen>
    <dimen name="bottom_nav_height">100dp</dimen>

    <!-- Corner radii (from cartoon theme) -->
    <dimen name="corner_radius_small">16dp</dimen>
    <dimen name="corner_radius_medium">20dp</dimen>
    <dimen name="corner_radius_large">28dp</dimen>
    <dimen name="corner_radius_pill">50dp</dimen>
</resources>
```

### Pattern 4: Applying textAppearance to Views

**What:** Replace hardcoded `android:textSize="16sp"` with `android:textAppearance="@style/TextAppearance.Cartoon.BodyLarge"`.
**When to use:** Every TextView in layouts that currently has a hardcoded `textSize` attribute.

```xml
<!-- Before (found in fragment_settings.xml and dialog files): -->
<TextView
    android:textSize="16sp"
    android:fontFamily="@font/nunito_bold" />

<!-- After: -->
<TextView
    android:textAppearance="@style/TextAppearance.Cartoon.BodyLarge" />
```

**Mapping (from existing `themes_cartoon.xml`):**

| Size | Weight | Use `@style/TextAppearance.Cartoon.*` |
|------|--------|--------------------------------------|
| 32sp | ExtraBold | HeadlineLarge |
| 26sp | Bold | HeadlineMedium |
| 22sp | Bold | HeadlineSmall |
| 20sp | Bold | TitleLarge |
| 16sp | SemiBold | TitleMedium |
| 14sp | SemiBold | TitleSmall |
| 16sp | Regular | BodyLarge |
| 14sp | Regular | BodyMedium |
| 12sp | Regular | BodySmall |
| 14sp | Bold | LabelLarge |
| 12sp | SemiBold | LabelMedium |
| 11sp | SemiBold | LabelSmall |

### Pattern 5: Theme Consistency Check

**Observation:** The manifest uses `android:theme="@style/Theme.PDFScanner"` — the non-cartoon Ghibli theme. The cartoon theme (`Theme.PDFScanner.Cartoon`) has the proper Nunito type scale. The planner needs to decide: switch the manifest to use `Theme.PDFScanner.Cartoon`, or retrofit the `Theme.PDFScanner` styles with Nunito references.

**Recommendation:** Switch manifest to `Theme.PDFScanner.Cartoon` — it already has the complete type scale, Nunito font references, dark mode variant in `values-night/`, and corner shapes. The Ghibli `Theme.PDFScanner` uses `sans-serif` system fonts and does not reference Nunito at all. The `Theme.PDFScanner.Crop` (CropActivity) may need separate handling.

### Anti-Patterns to Avoid

- **Using `requireContext()` for Snackbar anchor:** Use `binding.root` or `requireActivity().findViewById(android.R.id.content)` — never pass a Context to Snackbar.
- **Calling `Snackbar.make()` when fragment view is detached:** Guard with `view?.let { }` or check `isAdded`.
- **Loading bitmaps synchronously in `onBindViewHolder`:** Coil handles async loading correctly; do NOT call `BitmapFactory.decodeStream()` on the main thread.
- **Keeping `adapterScope` in PagesAdapter after Coil migration:** Coil manages its own coroutine lifecycle tied to the view; the custom scope becomes dead code.
- **Using `notifyDataSetChanged()` after Coil integration:** Existing `ListAdapter` with `DiffUtil` is correct; Coil placeholders handle the loading state per-item without full rebind.
- **Applying `textAppearance` AND explicit `textSize`:** When both are set, `textSize` wins. Remove explicit `textSize` after setting `textAppearance`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Image loading from URI with cache | Manual BitmapFactory + LruCache (current) | Coil `imageView.load(uri)` | Coil handles memory pressure, disk cache, downsampling, placeholder, error, and lifecycle cancellation automatically. Manual LruCache doesn't handle disk cache or OOM gracefully. |
| Thumbnail downsampling | Custom `calculateInSampleSize()` | Coil's built-in sampling | Coil targets the view's measured size automatically |
| Feedback UI | Custom dialog or Toast | Material Snackbar | Snackbar integrates with CoordinatorLayout for FAB dodge, swipe-dismiss, and accessibility |
| Font registration | Manual `Typeface.createFromAsset()` | `@font/` resource XML + `android:fontFamily` | Font resources are cached by the framework and work in XML |

**Key insight:** All four of these are "solved problems" in Android. The existing manual implementations exist because Coil was never added; they are now technical debt, not features.

---

## Common Pitfalls

### Pitfall 1: Snackbar in Fragments Without CoordinatorLayout Root

**What goes wrong:** Snackbar appears behind the bottom navigation bar, clipping the message text.
**Why it happens:** Snackbar uses the provided view's coordinator behavior to position itself. If the root is a `ConstraintLayout`, it cannot dodge the nav bar.
**How to avoid:** For fragments without `CoordinatorLayout` as root, use `requireActivity().findViewById(android.R.id.content)` as the Snackbar view parameter.
**Warning signs:** Snackbar appears but is partially hidden by navigation bar on test device.

### Pitfall 2: Coil Request Leaks in RecyclerView on Fast Scroll

**What goes wrong:** Stale images appear in wrong cells when scrolling fast.
**Why it happens:** Without proper tag management, a recycled ViewHolder gets its image overwritten by a previous slow-loading request.
**How to avoid:** Coil handles this correctly out-of-the-box using the `ImageView`'s tag internally. Do NOT set `binding.imageThumbnail.tag` manually (the old code does this — remove it when migrating).
**Warning signs:** Wrong thumbnail appears briefly on fast scroll.

### Pitfall 3: Hardcoded String in Toast → Snackbar Migration

**What goes wrong:** Some Toast calls pass hardcoded string literals (not `R.string.*`): e.g., `Toast.makeText(context, "Failed to load PDF file", Toast.LENGTH_LONG)` in `PdfEditorFragment`.
**Why it happens:** These were written before strings.xml discipline. The migration must extract the string first, then convert the call.
**How to avoid:** Before replacing with Snackbar, extract the string literal to `strings.xml`, then use `R.string.xxx`.
**Warning signs:** grep for `Toast.makeText` calls where the second argument is a quoted string literal rather than `R.string.*`.

### Pitfall 4: `textAppearance` and `textColor` Interaction in Dark Mode

**What goes wrong:** Dark mode text appears invisible (white on white or black on black).
**Why it happens:** `TextAppearance.Cartoon.BodyMedium` hardcodes `android:textColor="@color/cartoon_text_secondary"` — a light-mode-only color. In dark mode, this color is not overridden.
**How to avoid:** Do NOT set `android:textColor` inside `TextAppearance` styles unless you point to a color state list that includes a night variant. Instead, rely on `colorOnSurface`/`colorOnSurfaceVariant` from the Material 3 theme, or create a `color/` resource with a night qualifier.
**Warning signs:** After switching to dark mode, some text is invisible; particularly body/secondary text.

**Specifically:** `themes_cartoon.xml` sets `android:textColor="@color/cartoon_text_secondary"` (#636E72 dark gray) in several `TextAppearance` styles. In dark mode (`values-night/themes.xml`), this color token is NOT overridden — dark mode only changes surface/background colors. Result: dark gray text on dark background. This needs audit.

### Pitfall 5: Manifest Theme Not Changed

**What goes wrong:** All the typography work is done but screens still render with Ghibli theme (`sans-serif`, no Nunito).
**Why it happens:** Manifest declares `android:theme="@style/Theme.PDFScanner"` (Ghibli theme). No layouts reference `textAppearance` from the cartoon theme by default.
**How to avoid:** Change manifest to `android:theme="@style/Theme.PDFScanner.Cartoon"` as first task. Verify `Theme.PDFScanner.Crop` still works for `CropActivity` (it has its own explicit theme).
**Warning signs:** Fonts look like system sans-serif after all changes.

### Pitfall 6: dimens.xml Migration Scope Creep

**What goes wrong:** Attempting to migrate ALL hardcoded dp values delays delivery.
**Why it happens:** There are hundreds of hardcoded dp values across 24 layout files.
**How to avoid:** Focus on `padding`, `layout_margin`, and `textSize` in the 7 primary fragment layouts and 3 key item layouts (`item_page.xml`, `item_document.xml`, `item_recent_document.xml`). Dialog layouts are lower priority — migrate if time permits.
**Warning signs:** Spending more than 2 hours on dimens.xml migration.

---

## Code Examples

### Coil Integration: PagesAdapter Before/After

```kotlin
// BEFORE (current PagesAdapter — remove all of this):
private val thumbnailCache: LruCache<String, Bitmap> = run { ... }
private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

fun bind(uri: Uri, position: Int) {
    val cacheKey = uri.toString()
    val cachedBitmap = thumbnailCache.get(cacheKey)
    if (cachedBitmap != null) {
        binding.imageThumbnail.setImageBitmap(cachedBitmap)
    } else {
        binding.imageThumbnail.setImageDrawable(null)
        binding.imageThumbnail.tag = cacheKey  // <-- REMOVE (breaks Coil)
        adapterScope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadThumbnail(uri, binding.root.context) }
            if (binding.imageThumbnail.tag == cacheKey && bitmap != null) {
                thumbnailCache.put(cacheKey, bitmap)
                binding.imageThumbnail.setImageBitmap(bitmap)
            }
        }
    }
}

// AFTER (Coil):
import coil3.load
import coil3.request.crossfade

fun bind(uri: Uri, position: Int) {
    binding.imageThumbnail.load(uri) {
        crossfade(true)
        placeholder(R.drawable.ic_cartoon_document)
        error(R.drawable.ic_cartoon_document)
    }
    // ... rest of bind stays the same
}
```

### Toast-to-Snackbar: Standard Pattern

```kotlin
// BEFORE:
Toast.makeText(requireContext(), R.string.pdf_created, Toast.LENGTH_SHORT).show()

// AFTER option A — simple direct call:
Snackbar.make(binding.root, R.string.pdf_created, Snackbar.LENGTH_SHORT).show()

// AFTER option B — using extension (preferred for 85 call sites):
showSnackbar(R.string.pdf_created)

// Extension in ui/Extensions.kt:
fun Fragment.showSnackbar(
    @StringRes messageRes: Int,
    duration: Int = Snackbar.LENGTH_SHORT
) = view?.let { Snackbar.make(it, messageRes, duration).show() }

fun Fragment.showSnackbar(
    message: String,
    duration: Int = Snackbar.LENGTH_SHORT
) = view?.let { Snackbar.make(it, message, duration).show() }
```

### Strings to Extract (high priority — PdfEditorFragment hardcoded)

```xml
<!-- Add to strings.xml: -->
<string name="error_failed_to_load_pdf">Failed to load PDF file</string>
<string name="error_could_not_access_pdf">Could not access PDF file</string>
<string name="error_loading_pdf">Error loading PDF: %s</string>
<string name="no_annotations_to_save">No annotations to save</string>
<string name="error_saving_pdf">Failed to save PDF</string>
<string name="error_sharing_file">Error sharing file: %s</string>
<string name="annotation_deleted">Annotation deleted</string>
<!-- dialog_save_success.xml: -->
<string name="save_success_title">Woohoo!</string>
<string name="save_success_body">Your PDF has been saved!</string>
<string name="success_content_desc">Success</string>
```

### Emoji Removal Strategy

```xml
<!-- dialog_save_success.xml BEFORE: -->
<TextView android:text="Woohoo! 🎉" />

<!-- AFTER: -->
<TextView android:text="@string/save_success_title" />
<!-- where save_success_title = "Woohoo!" — emoji removed from string, can be expressed via drawable next to text -->

<!-- dialog_signature_pro.xml BEFORE: -->
<TextView android:text="Sign here ✍️" />
<!-- AFTER: -->
<TextView android:text="@string/sign_here_hint" />
<!-- where sign_here_hint = "Sign here" -->
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual BitmapFactory + LruCache in RecyclerView | Coil `imageView.load()` | 2019 (Coil 1.0) | Eliminates memory leak risk, automatic lifecycle cancellation, placeholder/error UI |
| `Toast.makeText()` for all feedback | Material Snackbar | Material Design 2 (2014), Material 3 (2021) | Anchored, swipeable, supports action buttons |
| Hardcoded font via `android:fontFamily` string | `@font/` resources with `android:fontFamily="@font/nunito_bold"` | API 26 + Support Library | Font bundled as resource, IDE-checked references |
| Hardcoded `textSize="16sp"` in XML | `textAppearance` from Material 3 type scale | Material 3 (2021) | Single source of truth, consistent scaling, dark mode aware |

**Deprecated/outdated in this project:**
- `android.widget.Toast` for app feedback: Material Design spec says "Prefer Snackbar"
- `BitmapFactory.decodeStream()` in adapters: No caching, no lifecycle awareness, OOM risk
- Hardcoded English strings in layouts: Android Lint error level when `lint.xml` is added in Phase 5

---

## Open Questions

1. **Should manifest switch to `Theme.PDFScanner.Cartoon`?**
   - What we know: Manifest currently uses `Theme.PDFScanner` (Ghibli, sans-serif). Cartoon theme has complete Nunito type scale. DSYS-01 requires Nunito on all screens.
   - What's unclear: Whether switching the theme will break any screen that relies on Ghibli-specific colors, or whether the `CropActivity` (using `Theme.PDFScanner.Crop`) is affected.
   - Recommendation: Switch the main theme to `Theme.PDFScanner.Cartoon` as Wave 0 task. `CropActivity` has its own explicit theme and is unaffected. Audit screens for unexpected color changes after switch.

2. **TextAppearance.Cartoon.* hardcodes textColor — does this break dark mode?**
   - What we know: `TextAppearance.Cartoon.BodyMedium` sets `android:textColor="@color/cartoon_text_secondary"` (#636E72). This color is not in `values-night/colors.xml`. Dark mode changes `colorOnSurfaceVariant` but not this direct color reference.
   - What's unclear: How many views use this text appearance and are they affected.
   - Recommendation: For DSYS-07, audit the dark mode rendering. If text is invisible, replace the hardcoded `android:textColor` in text appearance styles with `?attr/colorOnSurfaceVariant` (theme attribute) rather than a direct color reference.

3. **Coil 2.x vs 3.x for this project?**
   - What we know: Coil 3.4.0 is current stable (released 2026-02-24). Coil 2.7.0 has simpler Maven coords (`io.coil-kt:coil`) and more community resources. Both support `ImageView.load()` with identical API for View-based apps. Coil 3.x changed group ID to `io.coil-kt.coil3`.
   - What's unclear: Whether any project constraint prefers 2.x. No Compose in this project.
   - Recommendation: Use Coil 3.4.0 (`io.coil-kt.coil3:coil:3.4.0`) — it's the current stable and actively maintained. No Compose dependency needed; the base `coil` artifact includes `ImageView.load()`.

---

## Sources

### Primary (HIGH confidence)
- Coil official changelog (https://coil-kt.github.io/coil/changelog/) — confirmed version 3.4.0 released 2026-02-24
- Coil official docs (https://coil-kt.github.io/coil/getting_started/) — API and dependency structure
- Material 3 Snackbar guidelines (https://m3.material.io/components/snackbar/guidelines) — when to use vs dialog
- Material Components Android source (https://github.com/material-components/material-components-android) — Snackbar API
- Project source files: `themes_cartoon.xml`, `colors_cartoon.xml`, `values-night/themes.xml`, `build.gradle.kts`, `PagesAdapter.kt`, `HistoryAdapter.kt`, `HomeFragment.kt`, all 7 fragment files — direct code audit

### Secondary (MEDIUM confidence)
- Maven Repository: `io.coil-kt.coil3:coil-android` — confirmed artifact group/name structure
- WebSearch 2026 Coil 3.x Views usage — confirmed `ImageView.load()` API unchanged from 2.x

### Tertiary (LOW confidence)
- TextAppearance dark mode textColor pitfall — derived from code inspection of `themes_cartoon.xml` + `values-night/colors.xml`; not verified against runtime behavior

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Coil version confirmed from official changelog, Snackbar from official Material docs, font resources from existing project code
- Architecture patterns: HIGH — based on direct audit of existing code; replacement patterns follow official APIs
- Pitfalls: MEDIUM-HIGH — most derived from direct code inspection, with runtime dark mode pitfall being MEDIUM (requires device verification)

**Research date:** 2026-02-28
**Valid until:** 2026-03-28 (Coil is actively developed but 3.4.0 is stable; Material Components 1.11.0 is stable)
