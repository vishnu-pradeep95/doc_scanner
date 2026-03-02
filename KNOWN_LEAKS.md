# Known Library Leaks

## AbstractAppBarOnDestinationChangedListener (Navigation 2.7.x)

**Status:** Known library bug — NOT an app code issue
**Library:** androidx.navigation:navigation-fragment-ktx:2.7.6
**LeakCanary trace:** `AbstractAppBarOnDestinationChangedListener` holds a strong `Context` reference
**Reported:** https://github.com/square/leakcanary/issues/2566 (confirmed real leak, not false positive)
**Decision:** Document and triage; do NOT upgrade Navigation to 2.8.x (adds migration risk across 8 fragments and nav graph)

### Triage Rationale

The leak occurs in library code, not in the app's Fragment/Activity/ViewModel implementations.
All 11 fragments in this project correctly implement `_binding = null` in `onDestroyView()`
(audited during Phase 5 planning). The LeakCanary trace for this specific leak originates in
`AbstractAppBarOnDestinationChangedListener.context` — a Jetpack Navigation internal class.

When LeakCanary reports this leak, ignore it and investigate only leaks originating in
`com.pdfscanner.app.**` classes.

### Suppression (optional — add only if library leak noise is disruptive)

```kotlin
// In a debug-only Application class or DebugLeakConfig.kt if needed:
LeakCanary.config = LeakCanary.config.copy(
    referenceMatchers = AndroidReferenceMatchers.appDefaults +
        LibraryLeakReferenceMatcher(
            pattern = instanceField(
                "androidx.navigation.ui.AbstractAppBarOnDestinationChangedListener",
                "context"
            ),
            description = "Navigation 2.7.x library leak — tracked as known, not app code."
        )
)
```

_Documented: Phase 5 (Release Readiness) — 2026-03-01_
