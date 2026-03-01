---
phase: 04-test-coverage
plan: 03
subsystem: testing
tags: [robolectric, truth, org.json, image-processing, data-model, unit-tests]

# Dependency graph
requires:
  - "04-01 — test infrastructure (Robolectric 4.16, Truth 1.4.4, coroutines 1.7.3 pinning)"
provides:
  - "DocumentEntryTest: 9 Robolectric tests for JSON round-trip and formattedSize boundaries"
  - "ImageProcessorTest: 9 Robolectric tests for all 5 FilterType values, dimension capping, and file write"
affects: [04-04, 04-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Robolectric runner required for org.json.JSONObject in unit tests — plain JUnit4 Android SDK stubs throw RuntimeException on put/getString"
    - "@Config(sdk = [34]) on both test classes pins SDK to avoid downloading multiple SDK JARs"
    - "assertThat(result).isSameInstanceAs(input) for ORIGINAL filter reference equality"
    - "assertThat(x).isAtMost(3368) for dimension cap assertions"

key-files:
  created:
    - "app/src/test/java/com/pdfscanner/app/data/DocumentEntryTest.kt"
    - "app/src/test/java/com/pdfscanner/app/util/ImageProcessorTest.kt"
  modified: []

key-decisions:
  - "DocumentEntryTest uses RobolectricTestRunner (not JUnit4): org.json.JSONObject.put() and getString() are Android SDK methods stubbed to throw RuntimeException in plain JVM unit tests — Robolectric provides the real implementation"
  - "@Config(sdk = [34]) pins SDK on both test classes: consistent with 04-02 ScannerViewModelTest pattern, avoids multi-SDK JAR downloads"

# Metrics
duration: 2min
completed: 2026-03-01
---

# Phase 4 Plan 03: DocumentEntry and ImageProcessor Unit Tests Summary

**9 Robolectric tests for DocumentEntry JSON round-trip + formattedSize boundaries; 9 Robolectric tests for ImageProcessor confirming all 5 FilterType outputs, dimension capping at 3368px, and JPEG file write — both running on JVM without a device.**

## Performance

- **Duration:** 2 min
- **Started:** 2026-03-01T22:33:34Z
- **Completed:** 2026-03-01T22:35:16Z
- **Tasks:** 2
- **Files modified:** 2 (2 created, 0 modified)

## Accomplishments

- Created `DocumentEntryTest.kt` with 9 tests covering JSON round-trip for all 6 fields, special character preservation, JSONException on missing field, and all 6 formattedSize boundary conditions (500B, 1023B, 1024B=1KB, 2048B=2KB, 1.0MB, 1.5MB)
- Created `ImageProcessorTest.kt` with 9 tests covering all 5 FilterType values (ORIGINAL reference equality, ENHANCED/DOCUMENT_BW new bitmap, MAGIC/SHARPEN non-null), 4000x4000 oversized bitmap dimension capping to <=3368px for both MAGIC and SHARPEN, 1x1 edge case for all filters, and saveBitmapToFile returning true with non-empty JPEG output
- Confirmed ImageProcessor does NOT call OcrProcessor — no ML Kit interface refactor needed. No `UnsatisfiedLinkError` in any test run.
- Combined run with 04-02 ScannerViewModelTest: 40 total tests, 0 failures, BUILD SUCCESSFUL

## Test Results

| Class | Tests | Passed | Failed |
|-------|-------|--------|--------|
| DocumentEntryTest | 9 | 9 | 0 |
| ImageProcessorTest | 9 | 9 | 0 |
| ScannerViewModelTest (04-02) | 22 | 22 | 0 |
| **Total** | **40** | **40** | **0** |

## Task Commits

Each task was committed atomically:

1. **Task 1: DocumentEntry JSON round-trip and formattedSize tests** - `021bf32` (test)
2. **Task 2: ImageProcessor Robolectric filter tests** - `08bef27` (test)

## Files Created/Modified

- `app/src/test/java/com/pdfscanner/app/data/DocumentEntryTest.kt` — 9 Robolectric tests for DocumentEntry data model; JSON serialization round-trip (all 6 fields, special chars, missing field exception) and formattedSize boundary cases
- `app/src/test/java/com/pdfscanner/app/util/ImageProcessorTest.kt` — 9 Robolectric tests for ImageProcessor; all 5 FilterType values, MAGIC/SHARPEN dimension capping, 1x1 edge case, saveBitmapToFile

## Decisions Made

- **RobolectricTestRunner for DocumentEntryTest:** `org.json.JSONObject` is an Android SDK class. In plain Android unit tests (JUnit4 runner without Robolectric), all Android SDK methods are stubs that throw `RuntimeException("Method X not mocked")`. Switching to `@RunWith(RobolectricTestRunner::class)` provides the real `org.json` implementation. The plan said "org.json is on the test classpath via AGP" — this is true only for Robolectric-enabled tests where the shadow framework loads real Android implementations.
- **@Config(sdk = [34]) on both classes:** Consistent with the rest of Phase 4. Pins execution to a single SDK JAR, avoiding multi-download during test run.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] DocumentEntryTest switched from JUnit4 to RobolectricTestRunner for org.json support**
- **Found during:** Task 1 (running tests after writing DocumentEntryTest with @RunWith(JUnit4::class))
- **Issue:** `org.json.JSONObject.put()` and related methods are Android SDK stubs in plain JVM unit tests. Three tests failed with `java.lang.RuntimeException: Method put in org.json.JSONObject not mocked`.
- **Fix:** Changed `@RunWith(JUnit4::class)` to `@RunWith(RobolectricTestRunner::class)` and added `@Config(sdk = [34])`. The plan's "pure JVM" note was based on the assumption that org.json would be available outside Robolectric — it is not in AGP unit test configuration.
- **Files modified:** `app/src/test/java/com/pdfscanner/app/data/DocumentEntryTest.kt`
- **Commit:** `021bf32`

---

**Total deviations:** 1 auto-fixed (org.json Android stub issue)
**Impact on plan:** Both test files still produce the required 9 tests each. The runner change does not affect test semantics — only what runtime they execute against. No scope creep.

## Robolectric SDK Notes

- Both test classes use `@Config(sdk = [34])` — no SDK download issues observed
- Warning: `[Robolectric] WARN: Android SDK 36 requires Java 21 (have Java 17)` — expected, SDK 36 not requested, not an error
- Robolectric SDK JARs downloaded/cached on first run from previous 04-02 execution

## No ML Kit Invocation Confirmed

`ImageProcessor.applyFilter()` dispatches to: `applyEnhanced()`, `applyDocumentBw()`, `applyMagicFilter()`, `applySharpen()` — all use Android's `ColorMatrix`, `Canvas`, and pixel array operations only. `OcrProcessor` is never called. No interface refactor needed for ImageProcessor tests.

## Next Phase Readiness

- `./gradlew testDebugUnitTest` passes for all 40 unit tests (DocumentEntry, ImageProcessor, ScannerViewModel)
- Ready for 04-04: DocumentHistoryRepository tests (SharedPreferences-based persistence)

---
*Phase: 04-test-coverage*
*Completed: 2026-03-01*
