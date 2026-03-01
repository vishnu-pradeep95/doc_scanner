# Requirements: PDF Scanner — Polish & Quality Pass

**Defined:** 2026-02-28
**Core Value:** Every feature that exists must work flawlessly, feel delightful, and be verified — no rough edges, no untested flows.

## v1 Requirements

### Stability (BUG)

- [x] **BUG-01**: App does not crash when fragments detach during long-running operations — all `requireContext()`/`requireActivity()` calls in coroutine callbacks replaced with null-safe patterns (`context ?: return`)
- [x] **BUG-02**: PdfRenderer, ParcelFileDescriptor, and all Closeable resources released using `use {}` blocks — no file descriptor leaks
- [x] **BUG-03**: Stale temp files (>1hr) in cacheDir cleaned up on app startup — storage does not grow unboundedly
- [x] **BUG-04**: ScannerViewModel page list uses immutable list pattern — no concurrent modification or silent data loss during batch scan
- [x] **BUG-05**: ScannerViewModel preserves in-progress scan state across process death using SavedStateHandle
- [x] **BUG-06**: Bitmap decode dimensions capped to PDF output size during generation, explicit `recycle()` called after use — no OOM on 10+ page documents
- [x] **BUG-07**: PDF Editor undo/redo is either fully implemented or buttons are removed — no "coming soon" placeholder in a portfolio app
- [x] **BUG-08**: Imported images display with correct rotation (EXIF orientation respected)

### Design System & UI Consistency (DSYS)

- [x] **DSYS-01**: Material type scale defined in `styles.xml` (heading, title, body, caption sizes using Nunito) and applied consistently across all screens
- [x] **DSYS-02**: Spacing constants based on 8dp grid defined in `dimens.xml` and applied consistently across all layouts
- [ ] **DSYS-03**: Coil library integrated for all image/thumbnail loading — replaces manual bitmap loading in RecyclerViews and history screens
- [ ] **DSYS-04**: All 70 Toast calls replaced with Snackbar (transient feedback) or inline messages (persistent feedback) — no Toast used as a loading indicator
- [x] **DSYS-05**: All hardcoded `contentDescription` strings and UI text moved to `strings.xml` — no hardcoded English strings in code or layouts
- [x] **DSYS-06**: Emoji removed from programmatic strings — emoji only in drawables/resources, not in code strings
- [ ] **DSYS-07**: Dark mode visually verified on all 7 screens — no unreadable text, invisible icons, or wrong backgrounds

### Performance & Polish (PERF)

- [ ] **PERF-01**: Material motion navigation transitions implemented between all major screen transitions
- [ ] **PERF-02**: Haptic feedback triggered on camera capture (matching Adobe Scan / Microsoft Lens UX)
- [ ] **PERF-03**: Edge-to-edge display enabled with proper WindowInsets handling across API 24–34
- [ ] **PERF-04**: PDF generation progress shown as determinate indicator ("Page X of Y") — not spinner
- [ ] **PERF-05**: Destructive actions (delete page, discard scan) use Snackbar with undo action instead of confirmation dialogs
- [ ] **PERF-06**: PdfRenderer caches adjacent pages (previous/current/next) for smooth page swiping in viewer

### Test Coverage (TEST)

- [ ] **TEST-01**: All test dependencies added to `build.gradle.kts` — JUnit 4, MockK, Robolectric, coroutines-test, arch-core-testing, Espresso, FragmentScenario
- [ ] **TEST-02**: ScannerViewModel unit tests covering page list CRUD (add, remove, reorder), filter state, and PDF naming — minimum 15 tests
- [ ] **TEST-03**: DocumentEntry JSON serialization/deserialization round-trip tests — all fields preserved
- [ ] **TEST-04**: ImageProcessor filter tests via Robolectric — Enhanced, B&W, Sharpen, Magic filters produce non-null output — minimum 8 tests
- [ ] **TEST-05**: DocumentHistoryRepository CRUD tests via Robolectric — add, retrieve, delete, list operations — minimum 8 tests
- [ ] **TEST-06**: PdfUtils instrumented tests — merge, split, compress operations with real PDF files — minimum 8 tests
- [ ] **TEST-07**: Fragment smoke tests — HomeFragment loads without crash, PagesFragment renders page grid, HistoryFragment shows document list — minimum 5 tests
- [ ] **TEST-08**: Navigation flow test — scan flow (Camera → Preview → Pages → PDF created) completes without crash using TestNavHostController

### Release Readiness (RELEASE)

- [ ] **RELEASE-01**: Detekt configured with `detekt-formatting` plugin — runs on `check` task, baseline established, no blocking errors
- [ ] **RELEASE-02**: Android Lint configured with `lint.xml` — accessibility errors and hardcoded text treated as errors
- [ ] **RELEASE-03**: Complete ProGuard/R8 rules added for ML Kit, Google Play Services, Navigation Safe Args — release build does not strip required classes
- [ ] **RELEASE-04**: Release APK built and manually tested end-to-end on real device — all core feature paths (scan, filter, crop, PDF, edit, OCR, merge, split, compress, share) verified
- [ ] **RELEASE-05**: Camera `uses-feature` changed to `required="false"` — app installable on tablets and Chromebooks
- [ ] **RELEASE-06**: `android:allowBackup` set to `false` or `fullBackupContent` configured to exclude private scans and PDFs
- [ ] **RELEASE-07**: FileProvider `file_paths.xml` verified to scope sharing to only the `pdfs/` directory
- [ ] **RELEASE-08**: LeakCanary added to debug builds — no retained Activity/Fragment leaks detected
- [ ] **RELEASE-09**: JaCoCo coverage report configured with thresholds — 70% line coverage for `util/` package, 50% for `viewmodel/`

## v2 Requirements

### Future Enhancements

- **V2-01**: First-launch onboarding flow for new users
- **V2-02**: Skeleton/shimmer loading placeholders for document history
- **V2-03**: Predictive back gesture support (Android 14+)
- **V2-04**: Dynamic color theming (Material You)
- **V2-05**: PDF merge/split/compress using a real PDF library (not render-to-image) — lossy operation warning for v1
- **V2-06**: Searchable PDFs with embedded OCR text layer
- **V2-07**: Cloud sync (Google Drive, Dropbox)
- **V2-08**: App internationalization / multi-language support

## Out of Scope

| Feature | Reason |
|---------|--------|
| Phase 11+ new features (cloud sync, searchable PDFs) | Next milestone — polish existing first |
| New export formats (DOCX, TIFF) | Feature expansion, not polish |
| WearOS / home screen widgets | Future milestone |
| In-app purchases / Pro tier | Not in scope for this pass |
| Real PDF library (iTextPDF, PDFBox) | High effort, v2+ — add lossy warning instead |
| Internationalization | v2+ — English-only for v1 |
| Tablet-optimized layouts | v2+ — phone-first for v1 |
| Screenshot regression tests (Roborazzi/Paparazzi) | Low confidence tooling — pursue in v2 if regressions occur |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| BUG-01 | Phase 1 | Complete (01-03) |
| BUG-02 | Phase 1 | Complete (01-02) |
| BUG-03 | Phase 1 | Complete (01-02) |
| BUG-04 | Phase 1 | Complete (01-01) |
| BUG-05 | Phase 1 | Complete (01-01) |
| BUG-06 | Phase 1 | Complete (01-04) |
| BUG-07 | Phase 1 | Complete (01-02) |
| BUG-08 | Phase 1 | Complete (01-03) |
| DSYS-01 | Phase 2 | Complete (02-01) |
| DSYS-02 | Phase 2 | Complete (02-01) |
| DSYS-03 | Phase 2 | Pending |
| DSYS-04 | Phase 2 | Pending |
| DSYS-05 | Phase 2 | Complete (02-04) |
| DSYS-06 | Phase 2 | Complete (02-04) |
| DSYS-07 | Phase 2 | Pending |
| PERF-01 | Phase 3 | Pending |
| PERF-02 | Phase 3 | Pending |
| PERF-03 | Phase 3 | Pending |
| PERF-04 | Phase 3 | Pending |
| PERF-05 | Phase 3 | Pending |
| PERF-06 | Phase 3 | Pending |
| TEST-01 | Phase 4 | Pending |
| TEST-02 | Phase 4 | Pending |
| TEST-03 | Phase 4 | Pending |
| TEST-04 | Phase 4 | Pending |
| TEST-05 | Phase 4 | Pending |
| TEST-06 | Phase 4 | Pending |
| TEST-07 | Phase 4 | Pending |
| TEST-08 | Phase 4 | Pending |
| RELEASE-01 | Phase 5 | Pending |
| RELEASE-02 | Phase 5 | Pending |
| RELEASE-03 | Phase 5 | Pending |
| RELEASE-04 | Phase 5 | Pending |
| RELEASE-05 | Phase 5 | Pending |
| RELEASE-06 | Phase 5 | Pending |
| RELEASE-07 | Phase 5 | Pending |
| RELEASE-08 | Phase 5 | Pending |
| RELEASE-09 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 38 total
- Mapped to phases: 38
- Unmapped: 0 ✓

---
*Requirements defined: 2026-02-28*
*Last updated: 2026-03-01 after 02-04 plan completion (DSYS-05, DSYS-06 complete — string externalization and emoji cleanup done)*
