# Pitfalls Research

**Domain:** Android Document Scanner App -- Polish & Quality Pass
**Researched:** 2026-02-28
**Confidence:** HIGH (based on codebase analysis and well-known Android platform behaviors)

## Critical Pitfalls

### Pitfall 1: OutOfMemoryError During PDF Generation with Many Pages

**What goes wrong:**
When generating a PDF from 10+ high-resolution scanned images, the app loads full-resolution bitmaps into memory sequentially. Each uncompressed ARGB_8888 bitmap from a 12MP camera consumes ~48MB. Even with `decodeSampledBitmap`, the sampling calculation may still produce bitmaps larger than necessary for 595x842 PDF points. With multiple pages, garbage collection cannot keep up, and the process OOMs -- especially on devices with smaller heap limits (128-256MB).

**Why it happens:**
`PagesFragment.generatePdf()` iterates over all page URIs and decodes bitmaps in a loop. While each bitmap is used and then falls out of scope, the GC is not guaranteed to recycle promptly. The `decodeSampledBitmap` method uses `inSampleSize` (power-of-2 downsampling) but does not explicitly call `bitmap.recycle()` after drawing to the PDF page canvas. Additionally, `ImageProcessor.applyMagicFilter()` allocates TWO full IntArray pixel buffers (`width * height * 4 bytes each`) PLUS the output bitmap, tripling memory for that operation.

**How to avoid:**
1. Explicitly call `bitmap.recycle()` immediately after `canvas.drawBitmap()` in the PDF generation loop.
2. Add `System.gc()` hints between pages for large documents (not reliable alone, but helps).
3. Cap decoded bitmap dimensions to match PDF output size (595x842 at 72dpi, or 1190x1684 at 144dpi maximum).
4. For the magic filter: process in tiles or reduce bitmap size before pixel-level operations.
5. Use `BitmapFactory.Options.inBitmap` to reuse bitmap memory across loop iterations.

**Warning signs:**
- App crashes with `java.lang.OutOfMemoryError` when creating PDFs with 8+ pages.
- `ActivityManager.getMemoryClass()` returns < 256 on test devices.
- Logcat shows frequent GC messages (`GC_FOR_ALLOC`, `Background GC`) during PDF generation.

**Phase to address:**
Performance audit phase -- add memory profiling and stress test with 20+ page documents.

---

### Pitfall 2: Leaked PdfRenderer and ParcelFileDescriptor in Error Paths

**What goes wrong:**
In `PdfUtils.kt`, the `mergePdfs()` function opens `ParcelFileDescriptor` and `PdfRenderer` for each input PDF. If an exception occurs mid-loop (e.g., a corrupt PDF), the `catch` block at the outer level runs, but any already-opened `PdfRenderer`/`ParcelFileDescriptor` from earlier successful iterations are already closed. However, the CURRENT iteration's resources may leak. More critically, in `NativePdfView.loadPdf(context, uri)`, a temp file is created but never tracked for cleanup -- repeated PDF loads accumulate temp files in `cacheDir` indefinitely.

**Why it happens:**
Resources are managed manually with `close()` calls rather than `use {}` blocks. The happy path works, but exception paths skip cleanup. The temp file pattern `pdf_view_temp_${timestamp}.pdf` in both `NativePdfView` and `PdfEditorFragment` creates unique files each time but never deletes previous ones.

**How to avoid:**
1. Wrap all `PdfRenderer` and `ParcelFileDescriptor` usage in `use {}` blocks or try-with-resources equivalent.
2. Track temp files and clean them up in `onDestroyView()` or `close()`.
3. Add a cache cleanup routine on app startup that deletes stale temp files older than 1 hour.
4. In `PdfUtils`, restructure the merge loop so each PDF's resources are in their own `use {}` scope.

**Warning signs:**
- `cacheDir` grows unbounded over multiple editing sessions (check with `adb shell du`).
- File descriptor exhaustion on heavy use (rare but possible with rapid PDF switching).
- `Too many open files` errors in logcat.

**Phase to address:**
Bug fix / stability phase -- convert all resource handling to `use {}` blocks.

---

### Pitfall 3: requireContext() / requireActivity() Crashes After Fragment Detachment

**What goes wrong:**
The codebase has 154 calls to `requireContext()` and `requireActivity()` across 12 files. When coroutines complete after a fragment is detached (user navigates away during PDF generation, OCR, or rotation), `requireContext()` throws `IllegalStateException: Fragment not attached to a context`. This is the single most common crash pattern in production Android apps using coroutines with fragments.

**Why it happens:**
`lifecycleScope.launch` cancels coroutines when the lifecycle is destroyed, BUT the `withContext(Dispatchers.IO)` block runs to completion before cancellation is checked. The subsequent `withContext(Dispatchers.Main)` block then calls `requireContext()` on a detached fragment. Specifically vulnerable locations:
- `PagesFragment.createPdf()` -- long-running PDF generation, user navigates away.
- `PagesFragment.rotatePage()` -- rotation during back navigation.
- `PdfEditorFragment.saveAnnotatedPdf()` -- save during back press.
- `PagesFragment.performOcrOnPages()` -- OCR on multiple pages.

**How to avoid:**
1. Replace `requireContext()` with `context ?: return` in all coroutine callbacks.
2. Use `viewLifecycleOwner.lifecycleScope` (already done) but add `isAdded` checks before UI updates.
3. For critical operations (save PDF), use `viewModel` scope instead of `lifecycleScope` so work completes even if fragment dies, then observe result.
4. Add a null-safe `safeContext` extension: `val Fragment.safeContext get() = context ?: return`.

**Warning signs:**
- Crash reports with `IllegalStateException: Fragment X not attached to a context`.
- Crashes that are hard to reproduce because they require specific timing (navigate during background work).
- ANRs or freezes where users cannot navigate away from a loading screen.

**Phase to address:**
Bug fix phase -- systematic audit of all coroutine launch blocks for detachment safety.

---

### Pitfall 4: Camera Hardware Required Blocks Chromebook and Tablet Installation

**What goes wrong:**
The manifest declares `<uses-feature android:name="android.hardware.camera" android:required="true" />`. This prevents installation on Chromebooks (many lack rear cameras), tablets with only front cameras, and some foldable devices. For a portfolio/Play Store app, this unnecessarily limits the addressable device base. The Play Store will filter out the app from these devices entirely.

**Why it happens:**
The declaration was likely added as a best practice for a camera-centric app, but ML Kit Document Scanner and gallery import can function without a rear camera.

**How to avoid:**
1. Change to `android:required="false"` and handle the missing camera gracefully at runtime.
2. Keep the CAMERA permission (which auto-implies the feature) but use `uses-feature required=false` to override.
3. When no camera is detected, hide the camera button and surface the "Import from Gallery" and "Auto-scan" options prominently.

**Warning signs:**
- App not visible on Play Store for certain device categories.
- Play Console shows surprisingly low "supported devices" count.

**Phase to address:**
Play Store readiness phase -- manifest and device compatibility review.

---

### Pitfall 5: ProGuard Rules Are Incomplete for Release Builds

**What goes wrong:**
The current `proguard-rules.pro` only keeps CameraX and CanHub classes. Missing rules for: ML Kit (`com.google.mlkit.**`), Google Play Services ML Kit Document Scanner (`com.google.android.gms.**`), Navigation Safe Args generated classes, and any reflection-based code. A release build with R8/ProGuard enabled (`isMinifyEnabled = true`) will crash at runtime with `ClassNotFoundException` or `NoSuchMethodException` for stripped/obfuscated classes.

**Why it happens:**
ProGuard rules are typically only discovered to be missing when testing release builds, which developers often skip during development. Debug builds do not use ProGuard.

**How to avoid:**
1. Add ML Kit rules: `-keep class com.google.mlkit.** { *; }`.
2. Add Play Services rules: `-keep class com.google.android.gms.** { *; }`.
3. Add Navigation component rules (usually auto-included but verify).
4. Test the release build (not just debug) on a real device before Play Store submission.
5. Run `./gradlew assembleRelease` and install the APK to verify no ProGuard crashes.

**Warning signs:**
- App works perfectly in debug but crashes on launch or specific feature use in release.
- `ClassNotFoundException` or `NoSuchMethodError` in release logcat.
- ML Kit document scanner silently fails in release builds.

**Phase to address:**
Play Store readiness phase -- MUST test release build thoroughly.

---

### Pitfall 6: MutableLiveData Exposing Mutable Collections Causes Silent Data Corruption

**What goes wrong:**
`ScannerViewModel` exposes `LiveData<MutableList<Uri>>`. Any observer can mutate the list directly (e.g., `viewModel.pages.value?.add(uri)`) without triggering LiveData notification. The `addPage()` method works by getting the current list, modifying it in place, and reassigning -- but if two coroutines call `addPage()` concurrently (e.g., batch capture while auto-scan completes), the shared mutable list can lose entries due to race conditions.

**Why it happens:**
LiveData's `value` returns the actual mutable list reference, not a copy. Multiple callers can hold references to the same list. The ViewModel uses `.value =` (main thread only) which provides some serialization, but `postValue()` from background threads can interleave.

**How to avoid:**
1. Change `_pages` to `MutableLiveData<List<Uri>>` (immutable interface) and always create new lists: `_pages.value = currentList + newUri`.
2. Use `_pages.value = _pages.value.orEmpty().toMutableList().also { it.add(uri) }` pattern.
3. Alternatively, migrate to `StateFlow` which has better thread-safety characteristics.
4. For page filters map: same issue -- use immutable maps.

**Warning signs:**
- Pages occasionally disappear from the list after batch scanning.
- Filter assignments lost after rapid operations.
- Inconsistent page counts between what the user sees and what the PDF contains.

**Phase to address:**
Bug fix phase -- refactor ViewModel to use immutable collections.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Render-to-image PDF operations (merge/split/compress) | Works without external PDF library | Lossy quality degradation on every operation, text becomes non-searchable raster images | Acceptable for v1 but document in-app that operations are lossy |
| Temp files with timestamps instead of managed cache | Simple implementation, unique names | Unbounded cache growth, wasted storage | Never -- must add cleanup |
| `requireContext()` in coroutine callbacks | Less boilerplate than null checks | Crash on fragment detachment | Never in coroutine callbacks |
| Manual bitmap memory management | Direct control | Easy to miss recycle() calls, OOM potential | Acceptable if systematic -- add lint checks |
| No database (SharedPreferences for history) | Quick to implement | Poor query performance, no relational data | Acceptable for current feature set, migrate to Room if features grow |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| ML Kit Document Scanner | Assuming scanner is always available on all devices | Check `GmsDocumentScanner` availability with `ModuleInstallClient`, handle `MlKitException` with code `UNAVAILABLE`, show fallback UI |
| CameraX on foldables/tablets | Hardcoding `DEFAULT_BACK_CAMERA` | Query available cameras, handle `CameraSelector` failure, support front camera fallback |
| CanHub Image Cropper | Declaring `CropImageActivity` without proper theme | Already handled (Theme.PDFScanner.Crop), but verify it inherits ActionBar theme for toolbar |
| FileProvider sharing | Using `Uri.fromFile()` for sharing (fails on API 24+) | Use `FileProvider.getUriForFile()` with FLAG_GRANT_READ_URI_PERMISSION -- already correctly done in codebase |
| ML Kit Text Recognition (OCR) | Not handling model download failure on first use | The bundled model (`text-recognition:16.0.0`) is included in APK, but large images may cause OOM during recognition |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Loading full-res bitmaps for RecyclerView thumbnails | Scroll jank, memory spikes | `PagesAdapter` already uses `inSampleSize` but verify target size matches actual view dimensions | 15+ pages in grid view |
| Magic filter pixel-by-pixel processing on main thread | UI freeze during filter preview | Already uses coroutines for PDF gen but verify filter preview uses background thread | Images > 3000px wide |
| PdfRenderer rendering at view resolution on every page change | Lag when swiping between pages | Cache rendered page bitmaps (at least previous/current/next) | PDFs with 20+ pages, rapid swiping |
| No bitmap pool/reuse across operations | Excessive GC pressure, jank | Use `inBitmap` option in `BitmapFactory.Options` for same-dimension bitmaps | Batch processing of 10+ pages |
| Synchronous file I/O in adapter bind | RecyclerView scroll stutters | `PagesAdapter` loads thumbnails -- verify this is async (should use a loading placeholder) | 20+ pages in the grid |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Debug applicationIdSuffix (`.debug`) exposes debug variant on device alongside release | Confusing for users who have both installed, debug data accessible | Ensure debug builds are uninstalled before release testing |
| `android:allowBackup="true"` in manifest | User's scanned documents (potentially sensitive: IDs, contracts) are included in Android backup, extractable via ADB | Set `allowBackup="false"` or use `android:fullBackupContent` to exclude `filesDir/pdfs` and `filesDir/scans` |
| Scanned images stored in plaintext in app-private storage | If device is rooted or backup is extracted, all scanned documents are readable | For sensitive apps, encrypt files at rest; for portfolio app, at minimum document the privacy story clearly |
| FileProvider paths may expose more than intended | Other apps could potentially request files from shared directories | Review `file_paths.xml` to ensure only the `pdfs/` directory is shared, not the entire `filesDir` |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No progress indication during multi-page PDF generation | User thinks app froze, force-quits, losing the scan | Show per-page progress ("Creating page 3 of 12...") not just a spinner |
| Undo/Redo in PDF editor shows "coming soon!" toast | Feature looks broken, unprofessional for portfolio | Either implement it or remove the buttons entirely |
| Lossy PDF operations without warning | User merges PDFs expecting lossless, gets degraded image quality | Show a warning: "This operation re-renders pages as images. Text will no longer be selectable." |
| No confirmation before clearing all pages | Accidental tap on "New Document" loses all work | Add confirmation dialog for destructive actions |
| Sound effects with no mute option visible | Annoying in quiet environments, user scrambles to mute device | Provide an obvious toggle in the camera/scan screen, not buried in settings |
| Toast messages for errors instead of inline UI | Toasts disappear quickly, user misses error info | Use Snackbar with action button or inline error states for persistent errors |

## "Looks Done But Isn't" Checklist

- [ ] **PDF Generation:** Does not handle EXIF rotation -- photos taken in portrait may appear rotated in PDF. Verify `BitmapFactory` respects EXIF orientation or apply correction.
- [ ] **PDF Editor Save:** The undo/redo buttons exist but show "coming soon" toast -- either implement or remove before Play Store submission.
- [ ] **Release Build:** ProGuard rules are incomplete -- ML Kit and Play Services classes will be stripped. Must test release APK, not just debug.
- [ ] **Temp File Cleanup:** `cacheDir` accumulates `pdf_view_temp_*` and `temp_edit_*` files permanently. Must add cleanup logic.
- [ ] **Back Navigation During Operations:** Pressing back during PDF creation, OCR, or rotation will crash due to `requireContext()` on detached fragment.
- [ ] **Large File Handling:** No size validation before PDF operations. A 100MB PDF will OOM during merge/compress because the entire file is rendered to bitmaps.
- [ ] **Dark Mode:** Verify PDF editor annotations are visible in dark mode -- color picker includes black text which would be invisible on dark backgrounds.
- [ ] **Screen Rotation:** During PDF generation the coroutine continues but fragment is destroyed and recreated. ViewModel survives but UI references are stale. Verify loading state is restored after rotation.
- [ ] **Empty Document History:** Verify history screen handles the case where all referenced PDF files have been deleted (stale entries).
- [ ] **Play Store Content Policy:** Scanner apps frequently get flagged for "impersonation" if they claim to be official scanners, or "deceptive behavior" if camera permissions are not clearly justified. Ensure description is honest and permissions are well-explained.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| OOM during PDF generation | LOW | Add try-catch with bitmap recycle in catch block, show "reduce page count" message, offer to generate in smaller batches |
| Leaked file descriptors | LOW | Add cleanup in `onDestroy`, implement cache-clearing utility accessible from settings |
| Fragment detachment crashes | MEDIUM | Systematic audit of all `requireContext()` in coroutine blocks (154 occurrences across 12 files) |
| ProGuard stripping | LOW | Add keep rules and test release build -- straightforward once discovered |
| Mutable LiveData races | MEDIUM | Refactor ViewModel to immutable collections -- requires updating all observers |
| Lossy PDF operations | HIGH | Would require integrating a real PDF library (iTextPdf, Apache PDFBox) for lossless operations -- significant effort |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| OOM during PDF generation | Performance audit | Stress test: 20-page PDF with 12MP images on 2GB RAM device |
| Leaked resources (PdfRenderer, temp files) | Bug fix / stability | Monitor `cacheDir` size and file descriptor count during extended use |
| requireContext() crashes | Bug fix / stability | Navigate away during every long-running operation, verify no crashes |
| Camera hardware requirement | Play Store readiness | Check "Supported devices" count in Play Console after manifest change |
| ProGuard rules | Play Store readiness | Install `assembleRelease` APK on device, test every feature path |
| Mutable LiveData races | Bug fix / stability | Rapid batch capture (10+ pages in quick succession), verify all pages appear |
| Undo/redo placeholder | UX audit | Remove or implement -- no "coming soon" in portfolio app |
| EXIF rotation | Bug fix / stability | Test with photos from different camera orientations |
| Temp file accumulation | Bug fix / stability | Use app for 30 minutes of PDF editing, check cacheDir size |
| Play Store policy compliance | Play Store readiness | Review against Play Store policy checklist for scanner/utility apps |

## Sources

- Direct codebase analysis of `/home/vishnu/projects/doc_scanner/app/src/main/java/` (all source files reviewed)
- Android PdfRenderer API documentation (platform behavior for API 24-34)
- CameraX lifecycle binding behavior (documented in CameraX 1.3.x release notes)
- Android Fragment lifecycle and coroutine cancellation semantics (Kotlin coroutines documentation)
- Play Store developer policy center -- scanner/utility app requirements
- Confidence: HIGH for code-specific pitfalls (directly observed in source), MEDIUM for Play Store policy items (based on training data, recommend verifying current policy)

---
*Pitfalls research for: Android Document Scanner App -- Polish & Quality Pass*
*Researched: 2026-02-28*
