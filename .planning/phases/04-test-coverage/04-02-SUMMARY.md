---
phase: 04-test-coverage
plan: 02
subsystem: testing
tags: [scannerviewmodel, livedata, savedstatehandle, mockk, coroutines-test, truth, unit-tests, junit4]

# Dependency graph
requires:
  - phase: 04-test-coverage/04-01
    provides: "Test infrastructure scaffold: InstantTaskExecutorRule, MainDispatcherRule, mockk, coroutines-test, truth"
provides:
  - "22 JVM unit tests for ScannerViewModel covering page CRUD, filter state transitions, and PDF naming"
  - "Pattern: use mockk() for Uri instances (Uri.parse not available on plain JVM without Robolectric)"
  - "Pattern: observeForever to activate derived/transformed LiveData in tests before asserting .value"
affects: [04-03, 04-04, 04-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Mock Uri with mockk() on plain JVM — Uri.parse is an Android API, mockk() stubs are sufficient for reference equality"
    - "observeForever pattern for derived LiveData: Transformations.map chains are inactive until observed; activate in @Before with observeForever, remove in @After"
    - "runTest wrapper on ViewModel methods even when not suspend — ensures UnconfinedTestDispatcher processes pending coroutine work before assertions"

key-files:
  created:
    - "app/src/test/java/com/pdfscanner/app/viewmodel/ScannerViewModelTest.kt"
  modified: []

key-decisions:
  - "Mock Uri with mockk() instead of Uri.parse() — plain JVM (JUnit4 runner, not Robolectric) does not stub Android SDK methods; mockk objects compare by identity which is sufficient for all ViewModel page list operations"
  - "observeForever required on pageFilters derived LiveData — pageFilters is a Transformations.map chain that remains inactive (value = null) until an active observer is attached; pages is a direct SavedStateHandle LiveData and does not need this"

patterns-established:
  - "Uri in JVM unit tests: use mockk(name = 'label') for distinct testable instances"
  - "Derived LiveData activation: attach observeForever in @Before, removeObserver in @After"

requirements-completed: [TEST-02]

# Metrics
duration: 5min
completed: 2026-03-01
---

# Phase 4 Plan 02: ScannerViewModel Unit Tests Summary

**22 JVM unit tests for ScannerViewModel covering all page CRUD mutations, filter state persistence via SavedStateHandle, and PDF naming logic — zero failures on `./gradlew testDebugUnitTest`**

## Performance

- **Duration:** 5 min
- **Started:** 2026-03-01T22:27:54Z
- **Completed:** 2026-03-01T22:32:30Z
- **Tasks:** 1
- **Files modified:** 1 (created)

## Accomplishments

- 22 named @Test functions with clear backtick names covering all 3 domains
- Page CRUD domain (15 tests): addPage, removePage, movePage, insertPage, insertPages, updatePage, clearAllPages — all edge cases (out-of-range, empty state, order preservation) covered
- Filter state domain (4 tests): ENHANCED filter, filter overwrite, default ORIGINAL, DOCUMENT_BW round-trip through SavedStateHandle name serialization
- PDF naming domain (3 tests): custom baseName, null baseName, empty string baseName — all return correct filename format
- All tests pass on JVM with no device or emulator

## Test Names by Domain

**Page CRUD (15 tests):**
1. `addPage increases page count by one`
2. `addPage stores the added URI as the last element`
3. `addPage twice results in size 2 with correct order`
4. `removePage at index 0 decreases size by one`
5. `removePage at index 0 leaves the remaining element correct`
6. `removePage on out-of-range index leaves pages unchanged`
7. `movePage swaps elements at positions 0 and 1`
8. `movePage with out-of-range index leaves pages unchanged`
9. `insertPage at position 0 inserts URI at front and shifts existing URIs right`
10. `insertPages with two entries inserts both URIs at correct positions`
11. `updatePage at index 0 replaces URI and keeps size unchanged`
12. `clearAllPages empties pages list`
13. `clearAllPages empties pageFilters`
14. `clearAllPages sets pdfUri to null`
15. `setPdfBaseName with whitespace-only string stores null`

**Filter State (4 tests):**
16. `setPageFilter stores ENHANCED for page index 0`
17. `setPageFilter overwrites previous filter for same index`
18. `getPageFilter for unset index returns ORIGINAL as default`
19. `setPageFilter with DOCUMENT_BW round-trips correctly through SavedStateHandle`

**PDF Naming (3 tests):**
20. `getPdfFileName with baseName set returns baseName followed by timestamp`
21. `getPdfFileName with baseName null returns Scan prefix with timestamp`
22. `getPdfFileName with baseName empty string returns Scan prefix with timestamp`

## Task Commits

Each task was committed atomically:

1. **Task 1: Write ScannerViewModelTest with 22 tests** - `8cb10d4` (feat)

## Files Created/Modified

- `app/src/test/java/com/pdfscanner/app/viewmodel/ScannerViewModelTest.kt` — 22 JVM unit tests for ScannerViewModel; 258 lines

## Decisions Made

- **Mock Uri with mockk()**: The plan specified `@RunWith(JUnit4::class)` (not Robolectric). Plain JVM stubs do not implement Android SDK methods like `Uri.parse()`. The ViewModel stores URIs by reference identity (list additions, removals, swaps), so `mockk(name = "label")` instances are fully sufficient — each call returns a distinct object. No behavior mocking needed.
- **observeForever for derived LiveData**: `pageFilters` is a `Transformations.map` chain derived from `_rawPageFilters`. LiveData transformations are lazy — the chain is only active when observed. Without `observeForever`, `pageFilters.value` is always `null` even with `InstantTaskExecutorRule`. Direct `SavedStateHandle`-backed LiveData (like `pages`) does not have this issue.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Switched from Uri.parse() to mockk() for Uri creation**
- **Found during:** Task 1 (TDD RED phase — first test run)
- **Issue:** `Uri.parse()` throws `RuntimeException: Method parse in android.net.Uri not mocked` on plain JVM (JUnit4 runner, no Robolectric). All 17 URI-using tests failed on first run.
- **Fix:** Changed `uri()` helper from `Uri.parse("file:///test/$name.jpg")` to `mockk(name = name)`. Added `io.mockk.mockk` import.
- **Files modified:** `ScannerViewModelTest.kt`
- **Verification:** Tests that use URI instances (addPage, removePage, movePage, insertPage, etc.) all pass.
- **Committed in:** `8cb10d4` (Task 1 commit)

**2. [Rule 1 - Bug] Added observeForever to activate derived pageFilters LiveData**
- **Found during:** Task 1 (TDD GREEN phase — filter tests still failing after Uri fix)
- **Issue:** `pageFilters.value` returned `null` because `Transformations.map` chains are inactive until observed. The 2 filter tests asserting on `pageFilters.value` returned `null` instead of the expected FilterType.
- **Fix:** Added `pageFiltersObserver` field, `viewModel.pageFilters.observeForever(pageFiltersObserver)` in `@Before setup()`, and `viewModel.pageFilters.removeObserver(pageFiltersObserver)` in `@After teardown()`. Added `androidx.lifecycle.Observer` import.
- **Files modified:** `ScannerViewModelTest.kt`
- **Verification:** All 4 filter tests pass.
- **Committed in:** `8cb10d4` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking Android API, 1 LiveData activation bug)
**Impact on plan:** Both auto-fixes were necessary for tests to work correctly on JVM. No scope creep — both are standard patterns for JVM-based Android ViewModel testing.

## ScannerViewModel Behaviors Discovered

- `pageFilters` is a derived LiveData (Transformations.map from `_rawPageFilters`). Unlike `pages` which is a direct SavedStateHandle LiveData, `pageFilters` requires an active observer to evaluate.
- `pages` LiveData backed directly by SavedStateHandle — value populates synchronously with InstantTaskExecutorRule active; no observeForever needed.
- `insertPages` sorts entries ascending by position before inserting — the method's docstring says "must be sorted ASCENDING" but the implementation also sorts internally, so the sort is defensive/redundant.

## Issues Encountered

- `Uri.parse` is not stubbed by the Android JUnit4 runner (only Robolectric stubs Android SDK methods). Resolved by using `mockk()` stubs — standard practice for non-Android-behavior-dependent URI usage.
- `Transformations.map` lazy evaluation required `observeForever` — standard gotcha for derived LiveData in JVM tests. Direct `SavedStateHandle.getLiveData()` LiveData does not have this issue.

## Verification Results

```
./gradlew :app:testDebugUnitTest --tests "com.pdfscanner.app.viewmodel.ScannerViewModelTest"
BUILD SUCCESSFUL — 22 tests completed, 0 failed, 0 skipped
```

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- ScannerViewModel is fully covered with 22 tests. JaCoCo coverage for the ViewModel package will increase substantially when `./gradlew testDebugUnitTest jacocoTestReport` runs.
- Patterns established here (mockk for Uri, observeForever for derived LiveData) apply to 04-03 if any other ViewModel-like classes need testing.
- 04-03 (data layer tests) can proceed immediately.

---
*Phase: 04-test-coverage*
*Completed: 2026-03-01*
