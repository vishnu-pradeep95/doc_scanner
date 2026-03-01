---
phase: 04-test-coverage
plan: 04
subsystem: testing
tags: [robolectric, jacoco, shared-preferences, truth, temporary-folder, coverage, kotlin-1.9]

# Dependency graph
requires:
  - "04-01 — test infrastructure (Robolectric 4.16, JaCoCo jacocoTestReport task, enableUnitTestCoverage)"
  - "04-02 — ScannerViewModelTest (22 passing tests in viewmodel/)"
  - "04-03 — DocumentEntryTest + ImageProcessorTest (18 passing tests in data/ and util/)"
provides:
  - "DocumentHistoryRepositoryTest: 11 Robolectric tests for full CRUD via SharedPreferences"
  - "Robolectric JaCoCo exec file fix: apply(plugin='jacoco') + JacocoTaskExtension writes separate exec so Robolectric tests contribute to coverage"
  - "data/ LINE coverage: 92.9% (DocumentHistoryRepository + DocumentEntry)"
  - "viewmodel/ LINE coverage: 88.9% (ScannerViewModelTest from 04-02)"
  - "util/ LINE coverage: 20.4% (ImageProcessor 96.8% — other util classes untested)"
  - "JaCoCo HTML report at app/build/reports/jacoco/jacocoTestReport/html/index.html"
affects: [04-05, phase-5]

# Tech tracking
tech-stack:
  added:
    - "Gradle jacoco plugin (apply jacoco) — needed to configure JacocoTaskExtension on testDebugUnitTest"
  patterns:
    - "TemporaryFolder @Rule for real temp files: required because addDocument() and getAllDocuments() both call file.exists() internally"
    - "SharedPreferences isolation: clear 'document_history' prefs in @Before via context.getSharedPreferences(...).edit().clear().commit()"
    - "Construct DocumentHistoryRepository directly in @Before — do NOT use getInstance() singleton which caches across tests"
    - "Robolectric JaCoCo fix: JacocoTaskExtension on testDebugUnitTest writes a second exec file; jacocoTestReport reads both exec files"

key-files:
  created:
    - "app/src/test/java/com/pdfscanner/app/data/DocumentHistoryRepositoryTest.kt"
  modified:
    - "app/build.gradle.kts — added jacoco plugin, JacocoTaskExtension config, updated executionData to include second exec file"

key-decisions:
  - "Robolectric JaCoCo fix: apply jacoco Gradle plugin and configure JacocoTaskExtension to write testDebugUnitTestRobolectric.exec — Robolectric's InstrumentingClassLoader strips AGP-injected JaCoCo probes; the Gradle JaCoCo javaagent in the host JVM captures coverage before Robolectric's sandbox intercepts"
  - "util/ threshold (70%) not met due to untested classes (PdfUtils, AnimationHelper, DocumentScanner, SoundManager, AppPreferences, PdfPageExtractor) — these require CameraX/ML Kit/file-system mocking beyond JVM unit test scope; ImageProcessor itself is at 96.8%"
  - "data/ coverage (92.9%) exceeds any threshold — DocumentHistoryRepository.getInstance() singleton not tested (intentionally: tests construct directly to avoid shared state)"

patterns-established:
  - "Two exec file pattern: AGP exec (covers JUnit4/MockK tests) + Gradle jacoco exec (covers Robolectric tests) — merge via executionData fileTree with include() for both"
  - "DocumentHistoryRepository test pattern: makeTempPdf() creates real File satisfying both addDocument() and getAllDocuments() file.exists() guards"

requirements-completed: [TEST-05, RELEASE-09]

# Metrics
duration: 7min
completed: 2026-03-01
---

# Phase 4 Plan 04: DocumentHistoryRepository Tests + JaCoCo Coverage Summary

**11 Robolectric CRUD tests for DocumentHistoryRepository (SharedPreferences) + JaCoCo dual-exec fix enabling Robolectric tests to contribute coverage: data/ at 92.9%, viewmodel/ at 88.9%, util/ImageProcessor at 96.8%**

## Performance

- **Duration:** 7 min
- **Started:** 2026-03-01T22:43:11Z
- **Completed:** 2026-03-01T22:50:29Z
- **Tasks:** 2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments

- Created `DocumentHistoryRepositoryTest.kt` with 11 Robolectric tests covering all CRUD operations: empty state, add+retrieve (single and two-entry sorted-by-createdAt-desc), file-existence filtering (non-existent path silently skipped, deleted-file filtered from getAllDocuments), remove (by id and with deleteFile=true), clearHistory (list empty and files deleted), getDocument (valid id and unknown id)
- Fixed Robolectric + JaCoCo coverage gap by applying the Gradle `jacoco` plugin and configuring `JacocoTaskExtension` on `testDebugUnitTest` — Robolectric's classloader strips JaCoCo probes added by AGP, so a second exec file via the host-JVM javaagent is required; `jacocoTestReport` now reads both exec files
- JaCoCo HTML report verified at `app/build/reports/jacoco/jacocoTestReport/html/index.html`; actual LINE coverage: data/ 92.9%, viewmodel/ 88.9%, util/ 20.4% (ImageProcessor 96.8%, other util classes 0%)

## Test Results

| Class | Tests | Passed | Failed | Plan |
|-------|-------|--------|--------|------|
| DocumentHistoryRepositoryTest | 11 | 11 | 0 | 04-04 |
| DocumentEntryTest | 9 | 9 | 0 | 04-03 |
| ImageProcessorTest | 9 | 9 | 0 | 04-03 |
| ScannerViewModelTest | 22 | 22 | 0 | 04-02 |
| **Total (Phase 4 JVM tests)** | **51** | **51** | **0** | |

## JaCoCo Coverage Results

| Package | LINE covered | LINE total | Coverage | Threshold | Status |
|---------|-------------|------------|----------|-----------|--------|
| viewmodel/ | 56 | 63 | **88.9%** | >= 50% | PASS |
| data/ | 78 | 84 | **92.9%** | (no threshold) | PASS |
| util/ | 154 | 756 | **20.4%** | >= 70% | BELOW |
| util/ImageProcessor | 149 | 154 | **96.8%** | — | — |

**util/ threshold gap explanation:** ImageProcessor itself is at 96.8% coverage. The remaining 756 util/ lines belong to PdfUtils (PDF file operations), AnimationHelper (UI animations), DocumentScanner (ML Kit document scanner), SoundManager (audio playback), AppPreferences (SharedPreferences wrapper for app settings), and PdfPageExtractor (native PdfRenderer). These classes depend on CameraX, ML Kit, device sensors, and file system operations that require instrumented tests on a real device or extensive mocking beyond JVM unit test scope. They were not part of Phase 4's test plan.

## Task Commits

Each task was committed atomically:

1. **Task 1: DocumentHistoryRepository Robolectric CRUD tests** - `ec788f5` (test)
2. **Task 2: JaCoCo Robolectric exec fix + coverage verification** - `d7624d3` (feat)

## Files Created/Modified

- `app/src/test/java/com/pdfscanner/app/data/DocumentHistoryRepositoryTest.kt` — 11 Robolectric tests for DocumentHistoryRepository: empty state, single add, two-entry sort order, file-existence filtering (path missing, file deleted), remove by id, remove with deleteFile=true, clearHistory, clearHistory with deleteFiles=true, getDocument valid id, getDocument unknown id
- `app/build.gradle.kts` — Added `apply(plugin = "jacoco")`, `afterEvaluate { tasks.named("testDebugUnitTest") { extensions.configure<JacocoTaskExtension> { ... } } }` for second exec file, updated `executionData` in `jacocoTestReport` to include both exec files

## Decisions Made

- **Robolectric JaCoCo two-exec-file pattern:** `AGP enableUnitTestCoverage=true` instruments classes at compile time and writes to `testDebugUnitTest.exec`. Robolectric's `InstrumentingClassLoader` re-instruments those same classes with its own ASM pass, stripping JaCoCo probes. Solution: apply the Gradle `jacoco` plugin which attaches a javaagent to the test JVM process, writing a separate exec file (`testDebugUnitTestRobolectric.exec`). `jacocoTestReport` reads both files via `executionData.setFrom(fileTree { include("...AGP.exec", "...Robolectric.exec") })`.
- **util/ 70% threshold not achievable with current test scope:** PdfUtils/AnimationHelper/DocumentScanner etc. require CameraX, ML Kit, and native PDF rendering — not feasible in JVM unit tests without heavy mocking. Coverage goal for this phase is documented rather than enforced (per plan's explicit `DO NOT add jacocoTestCoverageVerification` instruction). ImageProcessor (the only util class intended for JVM tests) is at 96.8%.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added Robolectric JaCoCo exec file integration**
- **Found during:** Task 2 (running `jacocoTestReport` and finding 0% data/ and 0.7% util/ coverage)
- **Issue:** JaCoCo's AGP-generated exec file showed 0% for all Robolectric test classes (DocumentHistoryRepositoryTest, DocumentEntryTest, ImageProcessorTest). Only JUnit4-based ScannerViewModelTest contributed coverage. This is because Robolectric's `InstrumentingClassLoader` strips JaCoCo probes inserted by AGP at compile time.
- **Fix:** Applied Gradle `jacoco` plugin and configured `JacocoTaskExtension` on `testDebugUnitTest` to write a second exec file. Updated `jacocoTestReport.executionData` to include both exec files.
- **Files modified:** `app/build.gradle.kts`
- **Verification:** After fix, data/ coverage = 92.9% (from 0%), util/ImageProcessor = 96.8% (from 0%)
- **Committed in:** `d7624d3` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (missing critical coverage infrastructure)
**Impact on plan:** The fix was essential for the JaCoCo report to reflect actual test execution. Without it, the coverage report was misleading (0% for all Robolectric tests). No scope creep.

## Issues Encountered

- **util/ threshold gap:** The plan anticipated that ImageProcessor tests would push util/ coverage to >= 70%. However, `util/` contains 7 other classes (PdfUtils, AnimationHelper, DocumentScanner, SoundManager, AppPreferences, PdfPageExtractor, PdfOperationResult) totaling ~600 untested lines. Adding ImageProcessor tests alone cannot overcome this. Per plan instructions, the threshold is documented but NOT enforced via a build fail gate.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All 51 Phase 4 JVM unit tests pass with 0 failures
- JaCoCo HTML report available at `app/build/reports/jacoco/jacocoTestReport/html/index.html`
- Command to reproduce: `./gradlew testDebugUnitTest jacocoTestReport`
- viewmodel/ coverage (88.9%) and data/ coverage (92.9%) meet their thresholds
- util/ ImageProcessor coverage (96.8%) is excellent; overall util/ (20.4%) is below threshold due to untested CameraX/ML Kit classes — document this gap in Phase 5 planning
- Robolectric JaCoCo two-exec-file pattern is now established and committed

---
*Phase: 04-test-coverage*
*Completed: 2026-03-01*
