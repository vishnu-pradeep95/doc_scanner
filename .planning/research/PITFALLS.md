# Pitfalls Research

**Domain:** Android Document Scanner App -- Testing & Release Readiness (v1.1)
**Researched:** 2026-03-01
**Confidence:** HIGH for Kotlin/JaCoCo/MockK/ProGuard pitfalls (well-documented, multi-source verified); MEDIUM for CameraX/ML Kit test environment behavior (fewer authoritative sources, some training data)

> **Scope note:** This file supersedes the v1.0 PITFALLS.md for the v1.1 milestone. v1.0 pitfalls (OOM, resource leaks, requireContext crashes) are already fixed or tracked. This file focuses exclusively on the new risks introduced by adding tests and release tooling to an existing codebase.

---

## Critical Pitfalls

### Pitfall 1: JaCoCo Does Not Measure What You Think "70% Coverage" Means

**What goes wrong:**
The project goal is "70% line coverage for `util/`, 50% for `viewmodel/`." JaCoCo tracks six different counters: instructions (bytecode), branches (if/switch decision points), cyclomatic complexity, lines (source lines), methods, and classes. These produce very different percentages from the same test suite. A report can show 72% LINE coverage and simultaneously 41% BRANCH coverage. The project requirement does not specify which counter to enforce, so the threshold can be trivially gamed or accidentally missed depending on which report column you read.

Additionally, Kotlin coroutines introduce phantom branches in JaCoCo reports. Every `launch {}`, `flow {}`, and `flatMapLatest {}` call generates synthetic bytecode branches (suspend state machine transitions) that JaCoCo counts as uncovered branches. A ViewModel with 80% logical coverage can report as low as 55% branch coverage due purely to coroutine machinery, with zero defective code.

**Why it happens:**
The Android Gradle Plugin exposes coverage as a percentage in HTML/XML reports without labeling which counter type is being shown by default. Teams read the headline number without checking counter type. The Kotlin compiler generates internal branching in all coroutine-using code that does not correspond to any user-written conditional.

**How to avoid:**
1. Explicitly declare which counter the 70%/50% thresholds refer to in the JaCoCo task configuration:
   ```kotlin
   // In build.gradle.kts
   jacocoTestReport {
     reports { html.required = true; xml.required = true }
   }
   // Enforce on LINE counter, not BRANCH:
   jacocoTestCoverageVerification {
     violationRules {
       rule {
         element = "PACKAGE"
         includes = listOf("com/example/scanner/util/*")
         limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = 0.70.toBigDecimal() }
       }
       rule {
         element = "PACKAGE"
         includes = listOf("com/example/scanner/viewmodel/*")
         limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = 0.50.toBigDecimal() }
       }
     }
   }
   ```
2. Exclude coroutine-generated classes from branch coverage reports by adding JaCoCo exclusions for Kotlin coroutine internals in the execution data filter.
3. Use LINE coverage as the primary metric for this project (it matches developer mental model and is not distorted by coroutine state machines).

**Warning signs:**
- The HTML report shows wildly different percentages for "Lines" vs "Branches" columns.
- BRANCH coverage is lower than LINE coverage by more than 15 percentage points (coroutine inflation).
- `CoroutineScope.launch` is shown as "partially covered" even when the test triggers the coroutine.

**Phase to address:**
Phase 4 (Test Coverage) -- define counter type before writing a single test. Retrofitting this decision after writing 30+ tests is painful.

---

### Pitfall 2: JaCoCo Reports Inflate or Deflate Coverage Due to Missing Exclusions

**What goes wrong:**
Without exclusion filters, JaCoCo includes Android-generated classes (`R`, `R$*`, `BuildConfig`, `Manifest`, `*$ViewBinding`, Safe Args `*Args`, `*Directions`) in coverage calculations. Because these generated classes are never covered by unit tests (they contain no testable logic), they drag total coverage down -- sometimes by 10-20 percentage points. The project may hit 70% line coverage in actual logic but fail the threshold at 58% because `R.class` has thousands of uncovered lines.

The inverse also occurs: if generated classes happen to be exercised indirectly, they inflate coverage, making real logic look more covered than it is.

**Why it happens:**
The JaCoCo plugin for Android does not automatically exclude generated classes. The `classDirectories` source set must be manually configured with exclusion glob patterns in the `jacocoTestReport` task. This is not shown in basic documentation and requires deliberate configuration.

**How to avoid:**
Add exclusion patterns to the JaCoCo report task in `build.gradle.kts`:
```kotlin
classDirectories.setFrom(
  fileTree(layout.buildDirectory.dir("intermediates/javac/debug")) {
    exclude(
      "**/R.class",
      "**/R\$*.class",
      "**/BuildConfig.*",
      "**/Manifest*.*",
      "**/*Args.*",         // Safe Args generated
      "**/*Directions.*",   // Safe Args generated
      "**/*Binding.*",      // ViewBinding generated
      "**/*\$\$serializer*",// Kotlin serialization generated
    )
  }
)
```
Run the report once without exclusions to establish a baseline, then apply exclusions to see real coverage.

**Warning signs:**
- Total project coverage is suspiciously lower than what manual review of test files suggests.
- HTML report shows `R`, `BuildConfig`, or `*Directions` classes in the coverage tree.
- Adding a trivial test causes coverage to jump by more than expected.

**Phase to address:**
Phase 4 (Test Coverage) -- configure exclusions before running coverage for the first time.

---

### Pitfall 3: CameraX Cannot Be Instantiated in Robolectric -- Tests Crash or Silently No-Op

**What goes wrong:**
Any test that instantiates or imports `ProcessCameraProvider`, `CameraSelector`, `Preview`, or `ImageCapture` in a Robolectric context will either crash with `UnsatisfiedLinkError` (CameraX uses native camera2 bindings that Robolectric cannot load), or the provider will return `null` / throw `IllegalStateException` because no camera implementation is available on the JVM.

This affects `CameraFragment` directly: any attempt to test camera initialization, use case binding, or capture with Robolectric will fail. The same applies to `ImageAnalysis` use cases used for any ML Kit live preview integration.

**Why it happens:**
CameraX's `ProcessCameraProvider` requires a real camera2 backend. Robolectric's shadow implementations do not include camera2 or CameraX shims. The native `.so` libraries required by CameraX cannot be loaded in the JVM environment.

**How to avoid:**
1. Do NOT attempt to unit test CameraX use case binding logic with Robolectric. CameraX testing belongs in instrumented tests on a real device or emulator with camera support.
2. Extract all non-camera logic from `CameraFragment` into the ViewModel (`ScannerViewModel`) or utility classes that can be tested independently.
3. For smoke tests of `CameraFragment` via `FragmentScenario`, use Espresso instrumented tests and either:
   - Use the emulator with "Virtual Scene" camera (API 26+), or
   - Mock `ProcessCameraProvider` at the interface level using `ProcessCameraProvider.configureInstance()` (available since CameraX 1.1.0) to inject a fake `CameraProvider`.
4. The `ProcessCameraProvider.shutdownAsync()` method is a public testing API -- use it in `@After` methods of any instrumented test to allow re-initialization between tests.

**Warning signs:**
- Robolectric test fails with `UnsatisfiedLinkError: dlopen failed: library "libcamera2ndk.so" not found`.
- Test passes but LogCat shows `CameraX is not initialized` or camera preview is never bound.
- `ProcessCameraProvider.getInstance(context).get()` hangs indefinitely in Robolectric environment.

**Phase to address:**
Phase 4 (Test Coverage) -- TEST-07 (fragment smoke tests) must be configured as instrumented tests, not Robolectric unit tests, for any fragment that touches CameraX.

---

### Pitfall 4: ML Kit Cannot Be Tested with Robolectric -- Models Require GMS or Bundled Native Code

**What goes wrong:**
ML Kit uses either the bundled model path (`com.google.mlkit:text-recognition`) or the GMS dynamic module path (`com.google.android.gms.tasks`). Neither works in Robolectric because:
- The bundled model (.tflite) requires TensorFlow Lite native libraries that Robolectric cannot load.
- The GMS path requires Google Play Services which is not present on the JVM.
- `Tasks.await()` and `Task<T>` callbacks never complete or throw `RuntimeException: Cannot block main thread`.

Tests that call `ImageProcessor.applyOcr()` or any ML Kit scanner logic will hang indefinitely or crash with task timeout exceptions.

**Why it happens:**
ML Kit's Java API is a thin wrapper over native TFLite inference. The native binary is loaded at runtime by the model runtime, which Robolectric does not emulate.

**How to avoid:**
1. Define an interface wrapper around ML Kit operations:
   ```kotlin
   interface OcrProcessor { suspend fun recognize(bitmap: Bitmap): String }
   class MlKitOcrProcessor : OcrProcessor { /* real ML Kit impl */ }
   class FakeOcrProcessor : OcrProcessor { override suspend fun recognize(b: Bitmap) = "FAKE_TEXT" }
   ```
2. Inject `OcrProcessor` into `ScannerViewModel` (or wherever OCR is called). Unit tests inject `FakeOcrProcessor`. Production code uses `MlKitOcrProcessor`.
3. ML Kit integration should be tested only in instrumented tests (TEST-06, PdfUtils instrumented tests) on real device or emulator.
4. `ImageProcessor` filter tests (TEST-04) must exclude any method that calls ML Kit directly -- test only the bitmap manipulation logic.

**Warning signs:**
- Test hangs at `Tasks.await()` without completing.
- `java.lang.RuntimeException: Cannot block the main thread` during Robolectric test run.
- `UnsatisfiedLinkError` mentioning `libtensorflowlite_jni.so` or `libmlkit*.so`.

**Phase to address:**
Phase 4 (Test Coverage) -- establish the interface boundary for ML Kit before writing TEST-04. This is a prerequisite for testable `ImageProcessor` logic.

---

### Pitfall 5: Missing ProGuard Rules for ML Kit Cause Silent Release Build Crashes

**What goes wrong:**
The release build uses R8 with `isMinifyEnabled = true`. R8 aggressively removes and obfuscates classes unless keep rules are present. ML Kit ships with consumer ProGuard rules for some modules, but the rules are incomplete or missing for:
- `com.google.mlkit.vision.text` -- text recognition classes stripped if not accessed via reflection
- `com.google.mlkit.vision.documentscanner` -- GMS Document Scanner classes obfuscated
- Native methods in ML Kit JNI bridge (`java.lang.UnsatisfiedLinkError` in release builds)
- Language ID module (known issue with AGP 7.0+: native methods obfuscated by custom rules in versions ≤ 16.1.1)

The crash does not appear in debug builds because debug builds do not run R8/ProGuard.

**Why it happens:**
ML Kit's AAR files include consumer ProGuard rules, but they are incomplete and have known gaps. Navigation Safe Args generates `*Args` and `*Directions` classes at build time, but does NOT auto-generate ProGuard rules for them. Any non-primitive argument type passed through Safe Args must have explicit keep rules or it will be obfuscated and crash at navigation time.

**How to avoid:**
Add the following to `proguard-rules.pro`:
```proguard
# ML Kit -- text recognition, document scanner, common
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-dontwarn com.google.android.gms.**

# ML Kit native JNI bridge (prevents UnsatisfiedLinkError in release)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Navigation Safe Args -- all generated Args and Directions classes
-keep class **.ui.**.*Args { *; }
-keep class **.ui.**.*Directions { *; }
-keep class **.ui.**.*Directions$* { *; }

# Any data class used as a Safe Args argument (add specific ones as needed)
# -keep class com.example.scanner.model.DocumentEntry { *; }
```
Then run `./gradlew assembleRelease` and install the release APK on a physical device to test every screen. Use `adb logcat | grep -E "(ClassNotFound|NoSuchMethod|UnsatisfiedLink)"` to catch R8 regressions quickly.

To generate the complete merged rule set for debugging: add `-printconfiguration full-r8-config.txt` to `proguard-rules.pro` temporarily.

**Warning signs:**
- App works in debug, crashes in release on specific screens (navigation, OCR, document scanning).
- `ClassNotFoundException: com.google.mlkit.vision.text.TextRecognizer` in release logcat.
- `NoSuchMethodError` when navigating between fragments in release build.
- ML Kit Document Scanner opens then immediately closes with no error message.

**Phase to address:**
Phase 5 (Release Readiness) -- RELEASE-03. MUST test release APK on physical device, not emulator. This pitfall cannot be caught by unit tests alone.

---

### Pitfall 6: LeakCanary Reports Navigation Component Leak That Is a Known Library Bug

**What goes wrong:**
LeakCanary 2.x reports a memory leak traced to `AbstractAppBarOnDestinationChangedListener` holding a strong reference to `Context` when using `NavigationUI.setupWithNavController(toolbar, navController)`. This is a real leak in Navigation Component 2.7.x -- the `ToolbarOnDestinationChangedListener` uses a `WeakReference` for the Toolbar but the parent class holds a strong `Context` reference that outlives fragment lifecycle.

The leak appears in logcat as:
```
┬───
│ GC Root: Thread
...
├─ AbstractAppBarOnDestinationChangedListener instance
│    Leaking: YES (ObjectWatcher was watching this)
│    key = ...
│    retainedHeapSize = ...
╰→ Context instance
```

This can be mistaken for a leak in your own code, leading to wasted investigation time.

**Why it happens:**
Navigation Component 2.7.x has a known bug where `OnDestinationChangedListener` is not always properly removed when the associated Fragment is destroyed. This is a library-side issue, not an app-side issue. LeakCanary correctly identifies it as a "Library Leak" -- meaning the leak originates in a dependency, not in your code.

**How to avoid:**
1. Check LeakCanary's leak trace for the full retained path before investigating. If it terminates in `AbstractAppBarOnDestinationChangedListener`, it is the known Navigation bug, not your code.
2. The fix is to explicitly remove the listener in `onDestroyView()`:
   ```kotlin
   private var destinationListener: NavController.OnDestinationChangedListener? = null

   override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
     destinationListener = NavController.OnDestinationChangedListener { _, _, _ -> /* ... */ }
     navController.addOnDestinationChangedListener(destinationListener!!)
   }

   override fun onDestroyView() {
     super.onDestroyView()
     destinationListener?.let { navController.removeOnDestinationChangedListener(it) }
     destinationListener = null
   }
   ```
3. Alternatively, upgrade to Navigation Component 2.8+ which includes the fix.
4. Configure LeakCanary to ignore this specific leak path if it becomes noisy:
   ```kotlin
   AppWatcher.config = AppWatcher.config.copy(enabled = true)
   LeakCanary.config = LeakCanary.config.copy(
     referenceMatchers = AndroidReferenceMatchers.appDefaults +
       AndroidReferenceMatchers.instanceFieldLeak(
         "androidx.navigation.ui.AbstractAppBarOnDestinationChangedListener",
         "context",
         description = "Known Navigation 2.7.x library leak"
       )
   )
   ```

**Warning signs:**
- LeakCanary fires immediately after navigating between fragments with a toolbar.
- All leak traces point to `AbstractAppBarOnDestinationChangedListener`, never to your Fragment or ViewModel classes.
- Leak disappears when toolbar/NavigationUI integration is temporarily removed.

**Phase to address:**
Phase 5 (Release Readiness) -- RELEASE-08. Identify this as a library leak during LeakCanary integration to avoid misdiagnosing as an app code bug.

---

### Pitfall 7: ViewBinding Reference Not Nulled in onDestroyView() -- Real Leaks in Fragments

**What goes wrong:**
When `_binding` is kept as a non-null property after `onDestroyView()`, the Fragment instance (which outlives its View when on the back stack) holds a strong reference to the entire View hierarchy via the binding object. LeakCanary will report this as a leak, and it is a genuine leak -- not a false positive.

This is distinct from the Navigation library leak above. If any of the 8 fragments in this app hold binding references past `onDestroyView()`, LeakCanary will correctly flag them.

**Why it happens:**
Fragment View lifecycle ends at `onDestroyView()`, but Fragment lifecycle ends later at `onDestroy()`. Any view reference stored as a member variable bridges this gap and causes the binding (and its entire view tree) to be retained unnecessarily. This is the standard `Fragment + ViewBinding` leak pattern, well documented by Google since 2019.

**How to avoid:**
The standard pattern for every fragment:
```kotlin
private var _binding: FragmentScannerBinding? = null
private val binding get() = _binding!!

override fun onCreateView(...): View {
  _binding = FragmentScannerBinding.inflate(inflater, container, false)
  return binding.root
}

override fun onDestroyView() {
  super.onDestroyView()
  _binding = null  // CRITICAL: must be here, NOT in onDestroy()
}
```
Audit all 8 fragments to verify this pattern is in place before running LeakCanary.

**Warning signs:**
- LeakCanary reports a leak path going through `FragmentName -> *Binding -> View`.
- Leak trace shows a Fragment instance as the GC root holding a binding reference.
- Navigating forward and back 5+ times causes retained heap to grow linearly.

**Phase to address:**
Phase 4 (Test Coverage) -- audit binding nullification as part of TEST-07 fragment smoke tests. LeakCanary will catch any survivors in Phase 5 (RELEASE-08).

---

### Pitfall 8: Coroutine Testing Uses Deprecated TestCoroutineDispatcher API

**What goes wrong:**
Online tutorials for Android coroutine testing (published 2020-2022) use `TestCoroutineDispatcher` and `runBlockingTest`, both deprecated as of `kotlinx-coroutines-test 1.6`. Tests written with the deprecated API may compile but produce incorrect behavior: `TestCoroutineDispatcher` pauses and resumes in ways that don't match how `StandardTestDispatcher` or `UnconfinedTestDispatcher` work. Subtle ordering bugs appear where tests pass locally but fail under CI due to dispatcher flush differences.

The deprecated `MainCoroutineRule` pattern using `@get:Rule val coroutineRule = MainCoroutineRule()` with `TestCoroutineDispatcher` is still widely copied from old blog posts.

**Why it happens:**
The coroutines test API changed significantly in 1.6. Most search results and StackOverflow answers predate this change. Developers copy the first working example they find without checking the API version.

**How to avoid:**
Use the modern API only:
```kotlin
// build.gradle.kts
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

// MainDispatcherRule.kt (not "MainCoroutineRule")
class MainDispatcherRule(
  private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
  override fun starting(description: Description?) = Dispatchers.setMain(dispatcher)
  override fun finished(description: Description?) = Dispatchers.resetMain()
}

// In test class
@get:Rule val mainDispatcherRule = MainDispatcherRule()

// In test body
@Test fun `page CRUD works`() = runTest {
  viewModel.addPage(uri)
  assertEquals(1, viewModel.pages.value?.size)
}
```
Use `UnconfinedTestDispatcher` for ViewModel tests (runs coroutines eagerly, matches expected synchronous behavior). Use `StandardTestDispatcher` only when you need to control time advancement with `advanceTimeBy()`.

**Warning signs:**
- Compiler warning: `'TestCoroutineDispatcher' is deprecated. Replaced with StandardTestDispatcher or UnconfinedTestDispatcher`.
- Tests pass locally but behave differently on CI (ordering-dependent).
- `viewModel.pages.value` is null immediately after calling `viewModel.addPage()` in a test.

**Phase to address:**
Phase 4 (Test Coverage) -- TEST-01 (dependency setup). Establish the correct dispatcher rule before writing any ViewModel tests.

---

### Pitfall 9: InstantTaskExecutorRule Missing Causes LiveData Tests to Never Emit

**What goes wrong:**
`LiveData` uses `ArchTaskExecutor` internally to enforce main-thread observation. In unit tests (JVM, no Android Looper), `LiveData.setValue()` will fail silently or throw `java.lang.RuntimeException: Method getMainLooper not mocked` unless `InstantTaskExecutorRule` is active. The ViewModel posts values but the test's `assertEquals` on `liveData.value` reads `null` because the executor hasn't delivered the update.

For `ScannerViewModel` tests (TEST-02), which rely on `LiveData<List<Page>>` and filter state LiveData, every assertion on `.value` will return `null` without this rule.

**Why it happens:**
LiveData's main-thread enforcement is correct behavior in production, but test environments don't have a Looper. The `InstantTaskExecutorRule` swaps the background task executor for a synchronous one, making `postValue()` and `setValue()` behave identically in tests.

**How to avoid:**
Add to every test class that touches LiveData:
```kotlin
// build.gradle.kts
testImplementation("androidx.arch.core:core-testing:2.2.0")

// In test class
@get:Rule val instantExecutorRule = InstantTaskExecutorRule()
```
Note: `InstantTaskExecutorRule` is a JUnit 4 `@Rule`. If the project upgrades to JUnit 5, use the `instant-task-executor-extension` library instead.

**Warning signs:**
- `java.lang.RuntimeException: Method getMainLooper in android.os.Looper not mocked`.
- `viewModel.pages.value` returns `null` even after calling methods that should update it.
- LiveData `observeForever` callbacks never fire during test execution.

**Phase to address:**
Phase 4 (Test Coverage) -- TEST-02 (ScannerViewModel unit tests). Add this rule before writing the first ViewModel test.

---

### Pitfall 10: Detekt Baseline Must Be Generated from Clean State, Not Post-Fix State

**What goes wrong:**
When running `./gradlew detektBaseline` on an existing codebase with pre-existing violations, the baseline records all current violations as "known." If the baseline is generated AFTER fixing some violations (but not all), it will fail to record the remaining unfixed violations, causing detekt to flag them as new errors immediately.

Conversely, if the baseline is generated before any cleanup and then violations are fixed, the baseline becomes stale and silently allows those patterns to re-appear without triggering failures.

The baseline file (`detekt-baseline.xml`) is a snapshot in time. It does not track "fixed" vs "unfixed" -- it simply ignores everything recorded at generation time.

**Why it happens:**
The baseline workflow is counterintuitive: you generate it on a "dirty" codebase to establish the starting point, then only NEW violations (not in the baseline) fail the build. Teams often generate the baseline after partial cleanup, or forget to commit the baseline and regenerate it on CI, losing the historical record.

**How to avoid:**
1. Generate baseline ONCE from the completely unmodified codebase (before any fixes): `./gradlew detektBaseline`.
2. Commit `detekt-baseline.xml` immediately to version control.
3. Set `detekt { baseline = file("detekt-baseline.xml") }` in `build.gradle.kts`.
4. From this point forward, the build fails only on violations NOT in the baseline.
5. Fix baseline violations iteratively by re-running `detektBaseline` after fixing a category.
6. The RELEASE-01 goal of "zero blocking errors" means clearing the baseline entirely, not maintaining it.

**Warning signs:**
- `detektBaseline` task output says "0 findings" when you know the codebase has violations.
- Detekt fails on CI but passes locally (baseline not committed or inconsistent).
- The baseline XML contains thousands of entries (baseline was generated after CI already runs rules).

**Phase to address:**
Phase 5 (Release Readiness) -- RELEASE-01. Generate the baseline as the first action of this phase before any other detekt configuration.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Testing only the happy path in ViewModel tests | Fast to write 15 tests | Business logic bugs hide in error/empty branches | Never -- include error state and empty list tests from the start |
| Using `relaxed = true` in MockK for all mocks | No "unnecessary stubbing" errors | Hides untested method calls, masks missing verifications | Only for constructor injection of large dependency trees; verify critical interactions explicitly |
| Skipping release build testing until submission | Faster iteration | ProGuard/R8 bugs only surface in release builds, too late to fix before submission | Never -- test release APK for every phase |
| Writing tests that test MockK, not the ViewModel | Passes 100% of the time, inflates coverage | Tests provide zero regression protection | Never |
| Single JaCoCo report without enforced threshold | Easy to set up | Coverage number is decorative, not enforced; can drop unnoticed | Only during initial setup; enforce threshold before Phase 4 is "done" |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| CameraX + Robolectric | Instantiating `ProcessCameraProvider` in unit tests | Use instrumented tests (Espresso) for any test touching CameraX; mock the interface in unit tests |
| ML Kit + Robolectric | Calling ML Kit APIs directly in Robolectric tests | Wrap ML Kit behind an interface; use a `FakeOcrProcessor` in unit tests |
| MockK + coroutines | Using `every {}` for suspend functions without `coEvery {}` | Use `coEvery { suspendFun() } returns result` and `coVerify { suspendFun() }` for suspend functions |
| LiveData + unit tests | Forgetting `InstantTaskExecutorRule` | Add `@get:Rule val instantExecutorRule = InstantTaskExecutorRule()` to every ViewModel test class |
| JaCoCo + Navigation Safe Args | Generated `*Args`/`*Directions` classes deflating coverage | Exclude `**/*Args.*` and `**/*Directions.*` patterns from JaCoCo `classDirectories` |
| Detekt + View Binding | `UnusedPrivateMember` false positives for `_binding` pattern | Suppress via baseline or add `@Suppress("UnusedPrivateMember")` to the nullable backing property |
| LeakCanary + Navigation | `AbstractAppBarOnDestinationChangedListener` leak | Treat as known library leak; upgrade Navigation to 2.8+ or add a custom `AndroidReferenceMatchers` exclusion |
| FragmentScenario + themed app | Fragments rendered without correct theme, assertions fail | Always pass `R.style.Theme_YourApp` to `launchFragmentInContainer(themeResId = ...)` |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Running all Espresso tests on every CI build | CI pipeline takes 20+ minutes | Separate unit tests (fast) from instrumented tests (slow); run instrumented only on PR merge | At 5+ Espresso test classes |
| JaCoCo generating report on every build | Slow incremental builds | Only run `jacocoTestReport` as an explicit task, not as a dependency of `assemble` | Always -- keep coverage as on-demand task |
| Robolectric downloading SDK JARs on first run | First CI run takes 5+ minutes, appears hung | Pre-seed Robolectric SDK cache in CI or use `@Config(sdk = [34])` to pin a single SDK (avoids downloading all SDKs) | First CI run without caching |
| Detekt with type resolution on large codebases | Detekt takes 3+ minutes per run | Disable type resolution rules for incremental checks; enable only on full CI build | Codebases > 10K LOC |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Committing `google-services.json` or API keys in test fixtures | Key leakage in public repo | Use environment variables or Android `BuildConfig` fields from `local.properties`; never hardcode in test files |
| LeakCanary left in release builds | Memory profiling data exposed, minor performance overhead | LeakCanary dependency must be `debugImplementation` only; verify `BuildConfig.DEBUG` check is not needed because debugImplementation handles it |
| Release APK not tested before Play Store submission | ProGuard crashes discovered by users, not developers | Make `assembleRelease` + physical device test a mandatory gate before any submission |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Coverage gate blocks shipping when coverage target is aspirational, not enforced | Team ignores coverage as a concept because it "always fails" | Set realistic initial thresholds (50% is good for retrofitted tests) and increase gradually |
| Lint errors treated as warnings instead of errors | Accessibility issues (content descriptions, contrast) shipped to users | Set `abortOnError = true` and `checkReleaseBuilds = true` in `lint.xml` from the start |
| "Zero Detekt errors" achieved by suppressing everything | Appears clean but policy is meaningless | Limit `@Suppress` annotations per class; prefer fixing over suppressing |

---

## "Looks Done But Isn't" Checklist

- [ ] **JaCoCo threshold:** Coverage report exists but threshold enforcement (`jacocoTestCoverageVerification` task) is not wired to CI -- coverage can drop without failing the build. Verify the verify task runs on CI.
- [ ] **JaCoCo exclusions:** Generated classes (`R`, `BuildConfig`, Safe Args `*Directions`, `*Args`, View Bindings) are not excluded -- coverage numbers are wrong. Check the HTML report for generated class entries.
- [ ] **Release APK tested:** Tests pass but only debug APK was installed on device -- ProGuard rules have not been validated. Run `./gradlew assembleRelease` and install the `.apk` manually.
- [ ] **CameraFragment tests:** TEST-07 claims fragment smoke tests are written, but `CameraFragment` tests run as Robolectric -- they are silently no-ops. Verify tests are `androidTest` instrumented tests.
- [ ] **ML Kit interface boundary:** TEST-04 ImageProcessor tests exist, but they call ML Kit directly and hang on CI (no device). Verify the ML Kit calls are mocked out in unit tests.
- [ ] **LeakCanary false positive triaged:** RELEASE-08 shows "zero leaks" but the Navigation library leak was suppressed via baseline without investigation. Verify each suppressed leak path is genuinely a library issue.
- [ ] **Detekt baseline committed:** `detekt-baseline.xml` exists in `/app` directory but is in `.gitignore` -- CI generates a fresh baseline on every run, defeating the purpose. Verify the file is tracked in git.
- [ ] **Counter type documented:** JaCoCo report shows "72%" but whether that is lines, branches, or instructions is not specified in the acceptance criteria. Verify `jacocoTestCoverageVerification` uses `counter = "LINE"` explicitly.
- [ ] **MockK clearance between tests:** `clearAllMocks()` or `unmockkAll()` is not called in `@After`, causing test ordering dependencies. Verify mocks are reset between tests.
- [ ] **`_binding = null` in all fragments:** 8 fragments must null the binding in `onDestroyView()`. Verify all 8 before declaring RELEASE-08 complete.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Wrong coverage counter type discovered after Phase 4 | MEDIUM | Reconfigure `jacocoTestCoverageVerification` counter; re-run tests to see true number; may need additional tests if LINE coverage is lower than reported BRANCH |
| Missing JaCoCo exclusions discovered after setting threshold | LOW | Add exclusion patterns to `classDirectories`; re-run report; threshold target may need adjustment |
| CameraX tests written as Robolectric (silent failures) | MEDIUM | Move tests to `androidTest` directory; set up instrumented test CI pipeline; may require emulator or physical device in CI |
| Missing ProGuard rules discovered post-submission | HIGH | Emergency update required; users on release see crashes; must publish a patched release; test ALL features in release builds from day one |
| Detekt baseline not committed; CI failing | LOW | Re-generate baseline from current state, commit, adjust violation count expectations |
| Deprecated coroutine test API causes ordering flakiness | MEDIUM | Migrate test files from `TestCoroutineDispatcher`/`runBlockingTest` to `UnconfinedTestDispatcher`/`runTest`; verify all tests pass in isolation AND in suite |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| JaCoCo counter type ambiguity (LINE vs BRANCH vs INSTRUCTION) | Phase 4 -- before first test | `jacocoTestCoverageVerification` task has explicit `counter = "LINE"` in rules |
| JaCoCo generated class inflation | Phase 4 -- JaCoCo configuration | HTML report tree contains no `R`, `BuildConfig`, `*Directions`, or `*Args` entries |
| CameraX Robolectric incompatibility | Phase 4 -- TEST-07 fragment tests | CameraFragment tests are in `androidTest/` directory, not `test/` |
| ML Kit Robolectric incompatibility | Phase 4 -- TEST-04 ImageProcessor | `ImageProcessor` unit tests use a fake/mock for ML Kit; no `Tasks.await()` in unit tests |
| Missing ProGuard rules (ML Kit, Safe Args) | Phase 5 -- RELEASE-03 | Release APK installed on physical device; all features exercised without crashes |
| Navigation library leak (LeakCanary) | Phase 5 -- RELEASE-08 | LeakCanary fire for this path is either fixed (Nav 2.8+) or added to exclusion list with documented rationale |
| ViewBinding not nulled in onDestroyView | Phase 4 -- TEST-07 audit | All 8 fragment files have `_binding = null` in `onDestroyView()`; LeakCanary clean after back navigation |
| Deprecated coroutine test API | Phase 4 -- TEST-01 dependency setup | No `TestCoroutineDispatcher` or `runBlockingTest` in any test file |
| InstantTaskExecutorRule missing | Phase 4 -- TEST-02 ViewModel tests | Every ViewModel test class has `@get:Rule val instantExecutorRule = InstantTaskExecutorRule()` |
| Detekt baseline not committed | Phase 5 -- RELEASE-01 | `git ls-files detekt-baseline.xml` returns the file path |

---

## Sources

- [JaCoCo Coverage Counters -- official documentation](https://www.eclemma.org/jacoco/trunk/doc/counters.html) (HIGH confidence)
- [JaCoCo cannot measure coverage inside coroutines blocks -- GitHub issue #1045](https://github.com/jacoco/jacoco/issues/1045) (HIGH confidence -- confirmed in JaCoCo tracker)
- [CoroutineScope.launch reported as partially covered -- JaCoCo issue #1353](https://github.com/jacoco/jacoco/issues/1353) (HIGH confidence)
- [Kotlin 2.x inline functions cause JaCoCo false coverage -- issue #1622](https://github.com/jacoco/jacoco/issues/1622) (HIGH confidence)
- [Navigation Component 2.7.1 library leak -- LeakCanary issue #2566](https://github.com/square/leakcanary/issues/2566) (HIGH confidence)
- [Fragment ViewBinding leak -- LeakCanary issue #2341](https://github.com/square/leakcanary/issues/2341) (HIGH confidence)
- [How to generate ProGuard/R8 rules for Navigation Component arguments](https://koral.dev/blog/androidproguard/) (MEDIUM confidence -- community verified)
- [ML Kit known issues -- ProGuard and AGP 7.0+](https://developers.google.com/ml-kit/known-issues) (HIGH confidence -- official Google docs)
- [ML Kit ProGuard issue -- barcode scanning #213](https://github.com/googlesamples/mlkit/issues/213) (MEDIUM confidence)
- [Robolectric native library UnsatisfiedLinkError -- issue #9099](https://github.com/robolectric/robolectric/issues/9099) (HIGH confidence)
- [Unit testing LiveData -- Android Developers Medium](https://medium.com/androiddevelopers/unit-testing-livedata-and-other-common-observability-problems-bb477262eb04) (HIGH confidence -- official Android team)
- [Testing Kotlin coroutines on Android -- official docs](https://developer.android.com/kotlin/coroutines/test) (HIGH confidence)
- [TestCoroutineDispatcher deprecated migration guide](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/MIGRATION.md) (HIGH confidence)
- [Detekt baseline documentation](https://detekt.dev/docs/introduction/baseline/) (HIGH confidence -- official detekt docs)
- [Robolectric configuration -- official docs](https://robolectric.org/configuring/) (HIGH confidence)
- [Troubleshooting ProGuard issues on Android -- Wojtek Kaliciński, Android Developers](https://medium.com/androiddevelopers/troubleshooting-proguard-issues-on-android-bce9de4f8a74) (HIGH confidence)

---
*Pitfalls research for: Android Testing & Release Readiness -- retrofitting tests onto existing MVVM app (v1.1)*
*Researched: 2026-03-01*
