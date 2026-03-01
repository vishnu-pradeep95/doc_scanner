# Feature Research: Polish & Quality Attributes

**Domain:** Android Document Scanner App -- Polish Pass (not new features)
**Researched:** 2026-02-28
**Confidence:** HIGH (based on Google's official Core App Quality guidelines, code audit of existing codebase, and established Android UX patterns)

## Context

This is NOT a "what features to build" research. The app is feature-complete (Phase 10). This research answers: **what quality attributes separate a polished, Play Store-worthy document scanner from an unpolished indie project?**

Every item below is framed as a quality attribute to audit and fix, not a feature to add.

---

## Table Stakes Quality (Must Fix or Users Uninstall)

Quality attributes users expect. Missing these means 1-star reviews and immediate uninstalls.

### Stability & Crash Freedom

| Quality Attribute | Why Expected | Complexity | Notes |
|-------------------|--------------|------------|-------|
| Zero crashes on all core flows | Google Core App Quality requirement; crashes are the #1 reason for 1-star reviews | HIGH | No test suite exists. PagesFragment alone has 7 catch blocks suggesting fragile code paths. 70 Toast.makeText calls across 7 files suggest errors are swallowed with toasts rather than handled gracefully |
| No ANR (Application Not Responding) | Google flags ANRs in Play Console vitals; >0.47% ANR rate triggers warnings | MEDIUM | PDF operations (merge, split, compress) and PDF generation happen in coroutines but should be verified they never block main thread. Bitmap operations in ImageProcessor need audit |
| Graceful handling of large files | Users will scan 50+ page documents, import multi-MB PDFs | MEDIUM | PagesFragment loads all page bitmaps; no evidence of memory-aware thumbnail loading. BitmapFactory without inSampleSize will OOM on high-res photos |
| Configuration change survival | Screen rotation must not lose state or crash | LOW | ViewModel pattern is used correctly. Verify all fragments properly save/restore UI state |
| Process death recovery | Android can kill background app; returning must not crash | MEDIUM | No SavedStateHandle usage in ScannerViewModel. LiveData with mutableListOf is volatile -- if process dies, all in-progress scan pages are lost |

### Permission & Error Handling

| Quality Attribute | Why Expected | Complexity | Notes |
|-------------------|--------------|------------|-------|
| Camera permission denial handled gracefully | Google quality requirement: app must degrade gracefully | LOW | CameraFragment has permission UI but verify it works on all API levels (24-34). Check "Don't ask again" scenario |
| File not found handling | Documents in history may be deleted externally | LOW | HomeFragment checks `document.exists()` which is good. Verify this pattern is consistent everywhere |
| Storage full handling | Users with full storage will see cryptic errors | LOW | PDF generation and image saving likely crash or show raw exception messages. Need user-friendly error for disk full |
| Network-free operation | Document scanner must work fully offline | LOW | ML Kit Text Recognition is bundled (16.0.0). ML Kit Document Scanner (play-services-mlkit-document-scanner) requires Google Play Services -- verify it fails gracefully without GMS |

### Performance

| Quality Attribute | Why Expected | Complexity | Notes |
|-------------------|--------------|------------|-------|
| Camera preview starts in under 2 seconds | Adobe Scan and Google Drive scanner show preview almost instantly | MEDIUM | CameraX initialization is async but no cold-start optimization. No splash screen using Android 12+ SplashScreen API |
| Smooth 60fps scrolling in document list | Jank in lists is immediately noticeable | MEDIUM | HistoryFragment and RecentDocumentsAdapter load thumbnails -- verify they use proper thumbnail caching and not full-resolution bitmap loading |
| PDF generation shows real progress | Users scan 20 pages, generation can take 10+ seconds | LOW | PagesFragment has loading overlay but uses indeterminate progress. Should show "Page 3/20" determinate progress |
| Filter preview applies in under 500ms | Top scanner apps show filter previews nearly instantly | MEDIUM | ImageProcessor filter operations need to work on downscaled preview, not full-resolution image |
| App startup under 1 second | Google quality: show content or progress within 2 seconds | MEDIUM | No evidence of startup optimization. Consider Android 12+ SplashScreen API |

### UI Consistency & Visual Quality

| Quality Attribute | Why Expected | Complexity | Notes |
|-------------------|--------------|------------|-------|
| Consistent spacing and padding | Inconsistent spacing looks amateurish | MEDIUM | fragment_home.xml mixes 20dp, 24dp, 16dp, 28dp padding/margin values. Section headers use different text styles (some `textStyle="bold"`, some `fontFamily="@font/nunito_bold"`) |
| Consistent typography scale | Professional apps have a clear type hierarchy | MEDIUM | Text sizes range from 13sp to 28sp with no apparent Material type scale. Some use `fontFamily` attributes, some do not. Needs a defined type scale enforced everywhere |
| Touch target minimum 48dp | Google accessibility requirement. Small targets cause mis-taps | LOW | Settings button has 52dp (good). But PDF tool icons are in LinearLayouts with only 12dp padding -- total touch area may be under 48dp. "View All" button uses only 8dp padding |
| Content descriptions on all interactive elements | Accessibility requirement for TalkBack users | LOW | 13 elements use `contentDescription="@null"` including camera preview decorations (acceptable for decorative) but also some in fragment_camera.xml that may be interactive. Hardcoded strings like "Share", "Undo", "Redo" not using string resources |
| Dark mode visual correctness | Users who enable dark mode expect all screens to look correct | MEDIUM | Dark mode is supported but hardcoded colors like `#88000000` (loading overlay), `@color/white` (card text), `@color/primary` need verification in dark theme. `android:background="?android:colorBackground"` is correct pattern but check all screens |
| Proper empty states | Every list screen needs a meaningful empty state | LOW | HomeFragment has empty state for recent documents. Verify all list screens (History, Pages) have proper empty states with illustration + helpful text |

### Edge Cases & Error States

| Quality Attribute | Why Expected | Complexity | Notes |
|-------------------|--------------|------------|-------|
| Back navigation works correctly everywhere | Google Core App Quality: standard back must work | LOW | Navigation Component handles this but verify no fragments have broken back stack. CameraFragment has custom home button logic that may conflict |
| Interruption handling (phone call during scan) | Users get interrupted; app must not lose work | MEDIUM | CameraX is lifecycle-aware and will pause correctly. But verify in-progress PDF operations handle lifecycle interruption without corruption |
| Orientation lock or proper landscape support | Rotating phone during scanning must not break UI | LOW | No `android:screenOrientation` set in manifest, meaning rotation is allowed. Verify all layouts work in landscape or explicitly lock to portrait |
| Import of corrupt/malformed PDFs | Users will try to import any PDF | LOW | PdfPageExtractor has try-catch but verify error messages are user-friendly, not raw exceptions |

---

## Polish Differentiators (What Separates Good from Great)

Quality attributes that top scanner apps (Adobe Scan, Microsoft Lens, Google Drive) exhibit. Users notice these and they drive higher ratings.

| Quality Attribute | Value Proposition | Complexity | Notes |
|-------------------|-------------------|------------|-------|
| Haptic feedback on capture | Adobe Scan and Lens provide a satisfying tactile click when capturing. Creates a "premium" feel | LOW | Simple `HapticFeedbackConstants.CONFIRM` on capture button. SoundManager exists but haptics are missing |
| Animated transitions between screens | Smooth slide/fade transitions instead of instant cuts look professional | LOW | Navigation Component supports shared element transitions and custom animations. Current app likely uses default instant transitions |
| Loading skeleton screens | Show content placeholders while loading instead of spinner-then-content jump | MEDIUM | Recent documents and history could show shimmer/skeleton placeholders during load |
| Undo for destructive actions (delete) | Snackbar with "Undo" instead of confirmation dialog is faster and more forgiving | LOW | Currently uses MaterialAlertDialogBuilder for delete confirmation. Snackbar with undo is the Material Design recommended pattern |
| Proper Material 3 motion | Material motion principles (container transform, shared axis) make navigation feel connected | MEDIUM | Current bounce animations are playful but navigation transitions should follow Material motion guidelines |
| Thumbnail caching with Glide or Coil | Fast image loading with memory/disk cache prevents re-loading on scroll | MEDIUM | No image loading library detected in dependencies. Bitmaps are likely loaded manually which means no caching, no smooth loading |
| Onboarding / first-launch experience | 2-3 screen walkthrough showing key features helps first-time users and looks professional | MEDIUM | No onboarding detected. Top scanner apps show a quick intro on first launch |
| Predictive back gesture animation (Android 14+) | Shows a peek of the previous screen during back gesture. Modern Android quality signal | MEDIUM | Requires `android:enableOnBackInvokedCallback="true"` in manifest and proper back handling |
| Edge-to-edge display | Content renders behind system bars with proper insets. Standard on Android 15+, expected on modern apps | MEDIUM | No evidence of `WindowCompat.setDecorFitsSystemWindows(false)` or inset handling. Status bar area is likely wasted space |
| Proper Material 3 dynamic color theming | Support device wallpaper-based dynamic colors while maintaining brand identity | LOW | App uses custom coral/turquoise palette. Could support dynamic color as an option while keeping mascot theme |

---

## Anti-Patterns to Eliminate

Quality gaps commonly found in indie document scanner apps that this app should specifically avoid.

| Anti-Pattern | Why Problematic | What to Do Instead |
|--------------|-----------------|-------------------|
| Toast spam for every action | App shows 70 Toast calls across 7 files. Toasts stack up, are not accessible, and feel cheap | Use Snackbar for actionable messages (with undo), inline UI feedback for status, and Toast only for confirmations that need no action |
| Hardcoded content descriptions | Strings like `"Share"`, `"Undo"`, `"Document preview"` are not in string resources. Breaks i18n and accessibility tooling | Move ALL contentDescription strings to `strings.xml` |
| Emoji in user-facing strings | Code uses `"Imported $totalPages pages"` with emoji in Toast. Looks unprofessional in production | Remove emoji from programmatic strings. Use proper icons/illustrations instead |
| Loading indicator via Toast | `showImportProgress()` in HomeFragment shows a Toast as loading indicator and disables root view. This is a broken UX pattern | Use a proper loading overlay (which exists in the layout!) or a progress dialog |
| Catching generic Exception everywhere | 52 catch blocks across 19 files. Many catch `Exception` broadly, which masks bugs and makes debugging impossible | Catch specific exceptions. Log unexpected ones to a crash reporter. Show appropriate user messages per exception type |
| No image loading library | Manual bitmap loading without caching causes: repeated disk reads, OOM on large images, no smooth loading transitions, no placeholder/error images | Add Coil (Kotlin-first, lightweight) for all image/thumbnail loading. It provides caching, transitions, and proper lifecycle handling |
| Inconsistent section header styling | Some headers use `textStyle="bold"`, others `fontFamily="@font/nunito_bold"`, text sizes vary (18sp, 16sp, 14sp for similar-level elements) | Define text appearance styles in `styles.xml` and apply consistently |
| No ProGuard rules for libraries | R8/ProGuard is enabled for release but no custom rules file content verified. ML Kit, CanHub cropper, and Navigation SafeArgs may break in release builds | Add and test ProGuard keep rules for all third-party libraries |
| `uses-feature android:required="true"` for camera | Prevents installation on devices without cameras (tablets). Users may want to use import-only features on tablets | Set `android:required="false"` and check camera availability at runtime |

---

## Feature Dependencies (Polish Order)

```
[Crash fixes & error handling]
    |
    v
[UI consistency audit] ----requires----> [Typography/spacing system defined]
    |
    v
[Performance optimization] ----requires----> [Image loading library added]
    |
    v
[Accessibility audit] ----requires----> [Content descriptions fixed]
    |                                         |
    v                                         v
[Animation & transitions] ----enhances----> [Edge-to-edge + Material motion]
    |
    v
[Test coverage] ----requires----> [All above fixes stable]
```

### Dependency Notes

- **Crash fixes before UI polish:** No point polishing UI if app crashes on core flows. Fix stability first.
- **Typography system before UI audit:** Define the type scale and spacing constants, then apply everywhere. Doing it screen-by-screen without a system creates new inconsistencies.
- **Image loading library before performance:** Adding Coil/Glide solves thumbnail caching, memory management, and smooth loading in one step. Many "performance" issues disappear when proper image loading is in place.
- **Content descriptions before accessibility audit:** Fix the string resource issues first, then do a comprehensive TalkBack walkthrough.
- **All fixes before test coverage:** Write tests against the fixed behavior, not the broken behavior. Tests written first would all need rewriting.

---

## Priority Definition (Polish-Only Context)

Since the app is feature-complete, "MVP" means "minimum viable polish" -- the least work to reach Play Store quality.

### P1: Must Fix for Play Store (Launch Blockers)

- [ ] Crash freedom on all core flows (scan, import, PDF generation, history, share) -- stability is non-negotiable
- [ ] Memory management for bitmap operations (OOM = 1-star review) -- add image loading library
- [ ] Fix Toast-as-loading-indicator pattern in HomeFragment -- broken UX
- [ ] Remove emoji from programmatic strings -- unprofessional
- [ ] Hardcoded contentDescription strings moved to resources -- Play Store accessibility flagging
- [ ] ProGuard/R8 rules verified for release builds -- release builds must not crash
- [ ] Process death recovery for in-progress scans -- lost work = uninstall

### P2: Should Fix for Portfolio Quality

- [ ] Consistent typography scale defined and applied across all screens
- [ ] Consistent spacing system (8dp grid) applied across all layouts
- [ ] Replace Toast spam with Snackbar/inline feedback pattern
- [ ] Add proper loading overlays with determinate progress where applicable
- [ ] Navigation transitions (Material motion patterns)
- [ ] Haptic feedback on capture and key interactions
- [ ] Edge-to-edge display with proper inset handling
- [ ] Snackbar with undo for destructive actions (delete)
- [ ] Camera `uses-feature required="false"` for tablet compatibility
- [ ] Dark mode visual verification on all screens

### P3: Nice to Have for Wow Factor

- [ ] First-launch onboarding (2-3 screens)
- [ ] Skeleton/shimmer loading for document lists
- [ ] Predictive back gesture support (Android 14+)
- [ ] Dynamic color theming option
- [ ] Proper splash screen using Android 12+ SplashScreen API

### Defer: Not in Scope

- [ ] Searchable PDFs, cloud sync, new export formats -- Phase 11+
- [ ] Internationalization / multiple languages -- future milestone
- [ ] Tablet-optimized layouts -- future milestone (but camera required="false" is P2)
- [ ] In-app review prompt -- add after Play Store launch validation

---

## Competitor Quality Comparison

| Quality Attribute | Adobe Scan | Microsoft Lens | Google Drive Scanner | This App (Current) |
|-------------------|------------|----------------|---------------------|-------------------|
| Crash-free rate | 99.9%+ | 99.9%+ | 99.9%+ | Unknown (no testing) |
| Camera startup | <1s | <1s | <1.5s | Unknown (no benchmarks) |
| Haptic on capture | Yes | Yes | Yes | No |
| Navigation animation | Smooth shared element | Material motion | Standard Material | None (instant cuts) |
| Loading indicators | Determinate progress | Progress with cancel | Determinate | Mix of Toast + indeterminate |
| Empty states | Illustrated + CTA | Illustrated + CTA | Minimal | Basic text only |
| Accessibility | Full TalkBack | Full TalkBack | Full TalkBack | Partial (many @null descriptions) |
| Back gesture | Predictive back | Predictive back | Predictive back | Standard (no animation) |
| Edge-to-edge | Yes | Yes | Yes | No |
| Offline capability | Partial (OCR needs download) | Full | Partial | Full (ML Kit bundled) |
| Onboarding | Yes (quick) | Yes (3 screens) | None (integrated) | None |

---

## Sources

- **Google Core App Quality Guidelines** (https://developer.android.com/docs/quality-guidelines/core-app-quality) -- HIGH confidence. Defines Play Store quality bar including performance, stability, accessibility, and visual quality requirements. Verified via WebFetch.
- **Codebase audit** -- HIGH confidence. Direct inspection of all source files, layouts, manifest, and build configuration.
- **Material Design 3 Guidelines** -- HIGH confidence. Industry-standard Android design system from Google (training data, well-established).
- **Competitor analysis (Adobe Scan, Microsoft Lens, Google Drive)** -- MEDIUM confidence. Based on widely known UX patterns of market-leading scanner apps (training data, but these apps are well-documented).
- **Android accessibility guidelines** -- HIGH confidence. Google-mandated requirements for 48dp touch targets, contrast ratios, content descriptions.

---

## Key Observations from Code Audit

1. **No test suite exists.** Zero test files found. Testing infrastructure is in build.gradle (JUnit, Espresso) but no tests written.

2. **Toast is the primary feedback mechanism.** 70 Toast calls across 7 files is excessive. Some are used as loading indicators (HomeFragment `showImportProgress`), which is a broken pattern.

3. **Accessibility is partially implemented.** Many elements have `contentDescription="@null"` (acceptable for decorative), but interactive elements in the PDF editor use hardcoded English strings instead of string resources.

4. **No image loading library.** All bitmap loading is manual. This means no memory caching, no disk caching, no smooth loading transitions, and potential OOM on high-resolution images.

5. **The cartoon/mascot theme is a strong differentiator** but needs to be consistently applied. Mixed use of Nunito font weights and inconsistent spacing undermine the playful brand.

6. **ViewModel usage is correct** but lacks SavedStateHandle for process death survival. A user who switches to another app during a scan session may lose all pages.

---
*Feature quality research for: Android Document Scanner Polish Pass*
*Researched: 2026-02-28*
