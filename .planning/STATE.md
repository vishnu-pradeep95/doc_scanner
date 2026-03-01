---
gsd_state_version: 1.0
milestone: v1.1
milestone_name: Quality Gates
status: in_progress
last_updated: "2026-03-01"
progress:
  total_phases: 2
  completed_phases: 0
  total_plans: 8
  completed_plans: 3
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-03-01 after v1.1 milestone started)

**Core value:** Every feature that exists must work flawlessly, feel delightful, and be verified — no rough edges, no untested flows.
**Current focus:** Phase 4 — Test Coverage (first phase of v1.1)

## Current Position

Phase: 4 of 5 (Test Coverage)
Plan: 3 of 5 in current phase (04-03 complete)
Status: In progress — 04-03 complete, DocumentEntry and ImageProcessor tests written (9+9 tests)
Last activity: 2026-03-01 — 04-03 complete: 9 Robolectric tests for DocumentEntry JSON + formattedSize; 9 Robolectric tests for ImageProcessor filters (all 5 FilterTypes, dimension capping, file write)

Progress: [###░░░░░░░] 37% (3/8 plans complete)

## Accumulated Context

### Key Decisions (Full log in PROJECT.md)

Critical decisions carrying forward from v1.0:
- Transitions must be set in `onCreate()` not `onViewCreated()` — transition system ignores post-creation changes
- `limitedParallelism(1)` for PdfRenderer serialization — use instead of Mutex
- Context capture: `val ctx = context ?: return@launch` at TOP of every `lifecycleScope.launch` body

Testing decisions locked for Phase 4:
- JaCoCo counter type MUST be LINE (not BRANCH) — coroutines inflate BRANCH by 15–25% with synthetic branches
- Detekt MUST stay at 1.23.x — 2.x is Kotlin 2.x only
- coroutines-test MUST match coroutines-android version exactly (both 1.7.3)
- Use `UnconfinedTestDispatcher` + `runTest` (NOT deprecated `TestCoroutineDispatcher`)
- `InstantTaskExecutorRule` required in EVERY ViewModel test class
- CameraX and ML Kit INCOMPATIBLE with Robolectric — instrumented tests only
- Detekt baseline: generate ONCE from unmodified codebase, commit immediately

Decisions from 04-01 execution (2026-03-01):
- CRITICAL: Force coroutines to 1.7.3 via `configurations.all { resolutionStrategy { force() } }` — mockk 1.14.7 and Robolectric 4.16 pull in kotlinx-coroutines-bom:1.10.1 (Kotlin 2.1.0 binary), incompatible with project's Kotlin 1.9.21
- Also force kotlin-stdlib to 1.9.21 to prevent Kotlin 2.x stdlib from entering classpath
- WindowCompat.enableEdgeToEdge() does not exist; correct API is setDecorFitsSystemWindows(window, false) — this pre-existing bug was fixed

Decisions from 04-02 execution (2026-03-01):
- Uri.parse() NOT available on plain JVM (JUnit4 runner without Robolectric) — use mockk(name = "label") for Uri instances in JVM unit tests; reference equality is sufficient for ViewModel list operations
- Derived LiveData (Transformations.map) requires observeForever to activate — attach in @Before, remove in @After. Direct SavedStateHandle.getLiveData() does not need this.

Decisions from 04-03 execution (2026-03-01):
- org.json.JSONObject requires RobolectricTestRunner — plain JVM Android stubs throw RuntimeException on put/getString; DocumentEntryTest uses @RunWith(RobolectricTestRunner::class) instead of JUnit4
- ImageProcessor does NOT call OcrProcessor — no ML Kit interface refactor needed; confirmed via test run with 0 UnsatisfiedLinkError

### Blockers/Concerns

- **Build environment (RELEASE-04)**: WSL2 lacks JDK — `./gradlew assembleRelease` blocked. Phase 5's terminal gate (real-device E2E) requires host machine with Android Studio. All unit tests and static analysis CAN run in WSL2 after JDK is available.
- **OcrProcessor interface (TEST-04)**: Verify at Phase 4 start whether `ocr/OcrProcessor.kt` already exists as an interface — if not, refactoring step is required before Robolectric test writing can begin.
- **Navigation 2.7.x library leak**: LeakCanary will fire for `AbstractAppBarOnDestinationChangedListener` — document as library bug immediately, do not investigate as app code.

### Pending Todos

None.

## Session Continuity

Last session: 2026-03-01
Stopped at: Completed 04-03 — DocumentEntry and ImageProcessor unit tests (9+9 Robolectric tests)
Resume file: None
