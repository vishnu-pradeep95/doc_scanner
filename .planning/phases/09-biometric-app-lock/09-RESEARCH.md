# Phase 9: Biometric App Lock - Research

**Researched:** 2026-03-04
**Domain:** AndroidX Biometric authentication, ProcessLifecycleOwner, app lock state management
**Confidence:** HIGH

## Summary

Phase 9 implements opt-in biometric/PIN app lock with configurable auto-lock timeout. The AndroidX Biometric library (`androidx.biometric:biometric:1.1.0`) provides a unified API surface that handles API-level differences internally, but three distinct behavioral paths must be understood: API 24-27 (KeyguardManager-based fallback), API 28-29 (BiometricPrompt with restricted authenticator combinations), and API 30+ (full BiometricPrompt with all authenticator combinations). The project already uses `lifecycle:2.6.2`, so adding `lifecycle-process:2.6.2` for ProcessLifecycleOwner is a zero-risk dependency addition.

The critical nuance is that `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` is unsupported on API 28-29, but `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` works. Since app lock does not require crypto-bound authentication (no CryptoObject), using `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` on API 28-29 and `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` on API 30+ provides correct coverage. For API 24-27, the library internally delegates to `KeyguardManager.createConfirmDeviceCredentialIntent()`.

**Primary recommendation:** Use `androidx.biometric:biometric:1.1.0` (stable) with API-tiered `setAllowedAuthenticators()`, ProcessLifecycleOwner for background detection, and SecurePreferences for lock state/timeout persistence.

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SEC-02 | User can enable biometric/PIN app lock via Settings; BiometricPrompt with DEVICE_CREDENTIAL fallback | BiometricPrompt.PromptInfo.Builder with setAllowedAuthenticators(); SwitchMaterial toggle in Settings; AppLockManager utility class stores enabled state in SecurePreferences |
| SEC-13 | App re-requires authentication after configurable timeout when backgrounded (immediate/30s/1min/5min) | ProcessLifecycleOwner tracks ON_STOP timestamp; timeout comparison on ON_START; configurable timeout stored in SecurePreferences; checked in MainActivity.onResume() |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| androidx.biometric:biometric | 1.1.0 | BiometricPrompt + BiometricManager | Stable release (Jan 2021); handles API 23-34 internally; Google-maintained |
| androidx.lifecycle:lifecycle-process | 2.6.2 | ProcessLifecycleOwner for app foreground/background detection | Same version family as existing lifecycle deps (2.6.2); Kotlin-compatible |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| SecurePreferences (existing) | N/A | Store lock enabled flag, timeout preference, last-background timestamp | Already in project; encrypted storage for security-sensitive settings |
| AppPreferences (existing) | N/A | Expose lock settings via consistent API | Already wraps SecurePreferences |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| biometric:1.1.0 (stable) | biometric:1.4.0-alpha05 | Alpha channel; API may change; 1.1.0 is battle-tested and sufficient for app lock |
| ProcessLifecycleOwner | Application.ActivityLifecycleCallbacks | More boilerplate, error-prone counting of started activities; ProcessLifecycleOwner is the standard approach |
| SecurePreferences for timestamp | In-memory only | Loses state on process death; user could kill/restart app to bypass lock |

**Installation:**
```kotlin
// In app/build.gradle.kts dependencies block
implementation("androidx.biometric:biometric:1.1.0")
implementation("androidx.lifecycle:lifecycle-process:2.6.2")
```

## Architecture Patterns

### Recommended Project Structure
```
app/src/main/java/com/pdfscanner/app/
├── util/
│   └── AppLockManager.kt        # Lock state, timeout logic, biometric availability
├── ui/
│   └── SettingsFragment.kt      # Modified: add Security section with lock toggle + timeout selector
└── MainActivity.kt              # Modified: check lock state on onResume, show BiometricPrompt
```

### Pattern 1: AppLockManager Singleton
**What:** Centralized lock state management utility that encapsulates all biometric/lock logic.
**When to use:** Always -- single source of truth for lock state.
**Example:**
```kotlin
// Source: Official BiometricPrompt docs + project conventions
object AppLockManager {
    private const val KEY_LOCK_ENABLED = "${SecurePreferences.APP_PREFIX}lock_enabled"
    private const val KEY_LOCK_TIMEOUT = "${SecurePreferences.APP_PREFIX}lock_timeout"
    private const val KEY_LAST_BACKGROUND_TIME = "${SecurePreferences.APP_PREFIX}last_background_time"

    // Timeout options in milliseconds
    const val TIMEOUT_IMMEDIATE = 0L
    const val TIMEOUT_30_SECONDS = 30_000L
    const val TIMEOUT_1_MINUTE = 60_000L
    const val TIMEOUT_5_MINUTES = 300_000L

    fun isLockEnabled(context: Context): Boolean {
        return SecurePreferences.getInstance(context)
            .getBoolean(KEY_LOCK_ENABLED, false)
    }

    fun setLockEnabled(context: Context, enabled: Boolean) {
        SecurePreferences.getInstance(context).edit()
            .putBoolean(KEY_LOCK_ENABLED, enabled)
            .apply()
    }

    fun getTimeout(context: Context): Long {
        return SecurePreferences.getInstance(context)
            .getLong(KEY_LOCK_TIMEOUT, TIMEOUT_IMMEDIATE)
    }

    fun setTimeout(context: Context, timeoutMs: Long) {
        SecurePreferences.getInstance(context).edit()
            .putLong(KEY_LOCK_TIMEOUT, timeoutMs)
            .apply()
    }

    fun recordBackgroundTime(context: Context) {
        SecurePreferences.getInstance(context).edit()
            .putLong(KEY_LAST_BACKGROUND_TIME, System.currentTimeMillis())
            .apply()
    }

    fun shouldRequireAuth(context: Context): Boolean {
        if (!isLockEnabled(context)) return false
        val lastBackground = SecurePreferences.getInstance(context)
            .getLong(KEY_LAST_BACKGROUND_TIME, 0L)
        if (lastBackground == 0L) return true // First launch with lock enabled
        val timeout = getTimeout(context)
        return System.currentTimeMillis() - lastBackground >= timeout
    }

    /**
     * Check if device supports any form of authentication.
     * Uses BiometricManager for API 28+ and KeyguardManager for API 24-27.
     */
    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = getAllowedAuthenticators()
        val result = biometricManager.canAuthenticate(authenticators)
        return result == BiometricManager.BIOMETRIC_SUCCESS ||
               result == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
    }

    /**
     * Returns the correct authenticator flags for the current API level.
     *
     * API 30+: BIOMETRIC_STRONG | DEVICE_CREDENTIAL (full support)
     * API 28-29: BIOMETRIC_WEAK | DEVICE_CREDENTIAL (STRONG|CREDENTIAL unsupported)
     * API 24-27: BIOMETRIC_WEAK | DEVICE_CREDENTIAL (library delegates to KeyguardManager)
     */
    fun getAllowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }
}
```

### Pattern 2: ProcessLifecycleOwner Observer
**What:** Detect app background/foreground transitions to record timestamps and trigger auth.
**When to use:** In MainActivity or Application class to track when app goes to background.
**Example:**
```kotlin
// Source: ProcessLifecycleOwner official docs
// In MainActivity.onCreate():
ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        // App went to background -- record timestamp
        AppLockManager.recordBackgroundTime(applicationContext)
    }
})
```

### Pattern 3: BiometricPrompt in Activity (not Fragment)
**What:** Show BiometricPrompt from FragmentActivity context in onResume.
**When to use:** On every activity resume when lock is enabled and timeout has elapsed.
**Example:**
```kotlin
// Source: Official BiometricPrompt guide
// In MainActivity:
private var isAuthenticating = false
private var isAuthenticated = false

override fun onResume() {
    super.onResume()
    if (AppLockManager.shouldRequireAuth(this) && !isAuthenticated && !isAuthenticating) {
        showBiometricPrompt()
    }
}

private fun showBiometricPrompt() {
    isAuthenticating = true
    val executor = ContextCompat.getMainExecutor(this)
    val prompt = BiometricPrompt(this, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                isAuthenticated = true
                isAuthenticating = false
                // Reveal content (remove overlay or navigate)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                isAuthenticating = false
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    // User refused -- close app
                    finishAffinity()
                }
            }
            override fun onAuthenticationFailed() {
                // Biometric not recognized -- prompt stays open, user can retry
            }
        })
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(getString(R.string.app_lock_title))
        .setSubtitle(getString(R.string.app_lock_subtitle))
        .setAllowedAuthenticators(AppLockManager.getAllowedAuthenticators())
        // NOTE: Do NOT call setNegativeButtonText when DEVICE_CREDENTIAL is included
        .build()
    prompt.authenticate(promptInfo)
}
```

### Pattern 4: Content Obscuring While Locked
**What:** Hide document content while authentication is pending to prevent glimpses.
**When to use:** Between onResume and successful authentication.
**Example:**
```kotlin
// In activity_main.xml: Add a full-screen overlay View (GONE by default)
// <View android:id="@+id/lockOverlay" android:visibility="gone"
//    android:layout_width="match_parent" android:layout_height="match_parent"
//    android:background="?colorSurface" android:elevation="100dp" />

// In MainActivity:
private fun showLockOverlay() {
    binding.lockOverlay.visibility = View.VISIBLE
}
private fun hideLockOverlay() {
    binding.lockOverlay.visibility = View.GONE
}
```

### Anti-Patterns to Avoid
- **Using CryptoObject for app lock:** App lock only needs identity verification, not crypto-bound keys. Using CryptoObject would break DEVICE_CREDENTIAL fallback on API < 30 and adds unnecessary complexity.
- **Storing lock state in non-encrypted prefs:** Lock-enabled flag and timeout must be in SecurePreferences to prevent tampering.
- **Checking lock in Fragment instead of Activity:** Single-Activity architecture means the lock must be at the Activity level, otherwise navigation between fragments would re-trigger the prompt.
- **Using setNegativeButtonText with DEVICE_CREDENTIAL:** BiometricPrompt.PromptInfo.Builder throws IllegalArgumentException if both are set. When DEVICE_CREDENTIAL is an allowed authenticator, the system provides its own fallback UI.
- **Mixing biometric auth keys with file encryption keys:** Key decision from STATE.md -- these MUST be separate. App lock uses no KeyStore keys at all (non-crypto BiometricPrompt).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Biometric authentication UI | Custom fingerprint dialog | BiometricPrompt | System-managed dialog; handles hardware diversity, accessibility, security |
| API-level detection for biometrics | Manual Build.VERSION checks for each capability | BiometricManager.canAuthenticate() | Library handles 10+ device/API edge cases internally |
| App foreground/background detection | Activity counting with callbacks | ProcessLifecycleOwner | Handles config changes, multi-window; 700ms debounce built in |
| Device credential (PIN/pattern) entry | Custom PIN input UI | DEVICE_CREDENTIAL authenticator | System lock screen; user's existing credential; no storage needed |

**Key insight:** The BiometricPrompt library exists precisely because biometric hardware is wildly inconsistent across Android devices. Samsung, Huawei, and Xiaomi all have unique fingerprint sensor quirks that the library handles. A custom solution would need to handle hundreds of device-specific edge cases.

## Common Pitfalls

### Pitfall 1: BIOMETRIC_STRONG | DEVICE_CREDENTIAL on API 28-29
**What goes wrong:** App crashes with IllegalArgumentException on Android 9/10 devices when building PromptInfo.
**Why it happens:** This authenticator combination is explicitly unsupported on API 28-29 in the AndroidX biometric library.
**How to avoid:** Use `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` on API < 30, `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` on API 30+.
**Warning signs:** Crash reports from Android 9/10 devices; "unsupported authenticator combination" in logcat.

### Pitfall 2: Calling setNegativeButtonText with DEVICE_CREDENTIAL
**What goes wrong:** IllegalArgumentException at PromptInfo.Builder.build() time.
**Why it happens:** When DEVICE_CREDENTIAL is included in allowed authenticators, the system provides its own "Use PIN" fallback button. A custom negative button would conflict.
**How to avoid:** Never call setNegativeButtonText() when DEVICE_CREDENTIAL is in the authenticator set.
**Warning signs:** Build-time crash; "Negative text must not be set if device credential is allowed" error.

### Pitfall 3: BiometricPrompt in Fragment Context
**What goes wrong:** Prompt shows but callback references are lost on config change; potential leaks.
**Why it happens:** BiometricPrompt is designed for FragmentActivity. Using it from a Fragment works but has lifecycle edge cases.
**How to avoid:** Always create BiometricPrompt in MainActivity (the single Activity), not in fragments.
**Warning signs:** Authentication callbacks not firing after rotation; leaked Fragment references.

### Pitfall 4: Process Death Bypasses Lock
**What goes wrong:** User backgrounds app, system kills process, user relaunches -- lock is bypassed because in-memory "last background time" was lost.
**Why it happens:** In-memory timestamp doesn't survive process death.
**How to avoid:** Persist last-background timestamp to SecurePreferences (disk), not just a field.
**Warning signs:** Lock bypassed after extended background period; inconsistent lock behavior on low-memory devices.

### Pitfall 5: Content Flash Before Lock Screen
**What goes wrong:** User sees document content for a split second before BiometricPrompt appears.
**Why it happens:** onResume renders the current fragment's view before the biometric dialog appears.
**How to avoid:** Add an opaque overlay View to activity_main.xml that covers all content; show it in onPause/onStop, hide it only after successful authentication.
**Warning signs:** Sensitive documents visible briefly on resume; screen recording captures content.

### Pitfall 6: Lock Toggle Feedback Without Device Security
**What goes wrong:** User enables app lock but has no PIN/biometric set on device; app locks and user is stuck.
**Why it happens:** BiometricManager.canAuthenticate() not checked before enabling the toggle.
**How to avoid:** Before enabling lock, call BiometricManager.canAuthenticate() -- if BIOMETRIC_ERROR_NONE_ENROLLED, prompt user to set up device security; if BIOMETRIC_ERROR_NO_HARDWARE, hide the toggle entirely.
**Warning signs:** Users reporting "locked out" of app; support requests about inaccessible documents.

### Pitfall 7: onResume Loop from Auth Failure
**What goes wrong:** User cancels BiometricPrompt -> onAuthenticationError fires -> if not handled correctly, onResume re-shows the prompt in a loop.
**Why it happens:** BiometricPrompt dismissal triggers onResume, which checks lock state again.
**How to avoid:** Use `isAuthenticating` guard flag; on user cancel (ERROR_USER_CANCELED, ERROR_NEGATIVE_BUTTON), call finishAffinity() to close app instead of looping.
**Warning signs:** Infinite prompt loop; ANR on lock screen; user has to force-kill app.

## Code Examples

### Checking Biometric Availability Before Enabling Lock
```kotlin
// Source: Official BiometricManager docs
fun checkAndEnableLock(context: Context): Boolean {
    val biometricManager = BiometricManager.from(context)
    val authenticators = AppLockManager.getAllowedAuthenticators()
    return when (biometricManager.canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
            AppLockManager.setLockEnabled(context, true)
            true
        }
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            // Device has no biometric/PIN set up -- guide user to settings
            // On API 30+: Intent(Settings.ACTION_BIOMETRIC_ENROLL)
            // On API < 30: Intent(Settings.ACTION_SECURITY_SETTINGS)
            false
        }
        else -> {
            // BIOMETRIC_ERROR_NO_HARDWARE or BIOMETRIC_ERROR_HW_UNAVAILABLE
            false
        }
    }
}
```

### Settings UI Toggle for App Lock
```kotlin
// Source: Project conventions (existing Settings pattern)
// In SettingsFragment.setupSettings():
binding.switchAppLock.isChecked = AppLockManager.isLockEnabled(requireContext())
binding.switchAppLock.setOnCheckedChangeListener { _, isChecked ->
    if (isChecked) {
        if (!checkAndEnableLock(requireContext())) {
            binding.switchAppLock.isChecked = false // Revert toggle
            showDeviceSecurityRequiredDialog()
        }
    } else {
        AppLockManager.setLockEnabled(requireContext(), false)
    }
}
```

### Timeout Selector Dialog
```kotlin
// Source: Project conventions (existing dialog pattern from showThemeDialog)
private fun showTimeoutDialog() {
    val timeoutLabels = arrayOf(
        getString(R.string.timeout_immediate),
        getString(R.string.timeout_30_seconds),
        getString(R.string.timeout_1_minute),
        getString(R.string.timeout_5_minutes)
    )
    val timeoutValues = longArrayOf(
        AppLockManager.TIMEOUT_IMMEDIATE,
        AppLockManager.TIMEOUT_30_SECONDS,
        AppLockManager.TIMEOUT_1_MINUTE,
        AppLockManager.TIMEOUT_5_MINUTES
    )
    val currentIndex = timeoutValues.indexOf(AppLockManager.getTimeout(requireContext()))

    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.auto_lock_timeout)
        .setSingleChoiceItems(timeoutLabels, currentIndex.coerceAtLeast(0)) { dialog, which ->
            AppLockManager.setTimeout(requireContext(), timeoutValues[which])
            updateLockUI()
            dialog.dismiss()
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| FingerprintManager | BiometricPrompt (AndroidX) | 2019 (biometric:1.0.0) | Single API for all biometric types + device credential |
| setDeviceCredentialAllowed(true) | setAllowedAuthenticators(DEVICE_CREDENTIAL) | biometric:1.1.0 (Jan 2021) | More granular control over authentication types |
| Manual API-level branching | Library handles internally | biometric:1.0.0+ | AndroidX library wraps KeyguardManager for API 24-27, FingerprintManager for 23-27, platform BiometricPrompt for 28+ |
| ActivityLifecycleCallbacks counting | ProcessLifecycleOwner | lifecycle-process:2.0.0 (2018) | Reliable foreground/background detection with 700ms debounce |

**Deprecated/outdated:**
- `FingerprintManager`: Deprecated in API 28; replaced by BiometricPrompt
- `setDeviceCredentialAllowed()`: Deprecated in biometric:1.1.0; use setAllowedAuthenticators()
- `KeyguardManager.createConfirmDeviceCredentialIntent()`: Deprecated in API 29; AndroidX biometric library wraps this internally for older APIs

## API-Level Compatibility Matrix

This is the critical reference for implementation:

| API Level | Authenticators Flag | Behavior | Notes |
|-----------|-------------------|----------|-------|
| 24-27 | `BIOMETRIC_WEAK \| DEVICE_CREDENTIAL` | Library internally uses KeyguardManager | Shows system lock screen (PIN/pattern/password); no fingerprint on API < 28 via BiometricPrompt |
| 28-29 | `BIOMETRIC_WEAK \| DEVICE_CREDENTIAL` | BiometricPrompt with Class 2 biometric + PIN fallback | BIOMETRIC_STRONG \| DEVICE_CREDENTIAL throws IllegalArgumentException |
| 30+ | `BIOMETRIC_STRONG \| DEVICE_CREDENTIAL` | BiometricPrompt with Class 3 biometric + PIN fallback | Full support for all combinations |

**CRITICAL:** `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` is INVALID on API 28-29. The app MUST use `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` for those API levels.

## ProGuard/R8 Considerations

The `androidx.biometric` library bundles its own consumer ProGuard rules via the AAR. No explicit keep rules are needed in `proguard-rules.pro`. This matches the pattern already established for Coil and Coroutines in the project.

## Open Questions

1. **Lock overlay appearance**
   - What we know: An opaque overlay is needed to prevent content flash before auth
   - What's unclear: Should it match the splash screen appearance, or just be a solid colorSurface?
   - Recommendation: Use `?colorSurface` background on the overlay for theme consistency; optionally add app icon centered. Keep it simple.

2. **Behavior when device security is removed**
   - What we know: User could enable app lock, then remove their device PIN/fingerprint from system settings
   - What's unclear: Should the app auto-disable lock, or show an error on next resume?
   - Recommendation: On resume, if `canAuthenticate()` returns `BIOMETRIC_ERROR_NONE_ENROLLED` or `NO_HARDWARE`, auto-disable lock and show a Snackbar explaining why.

## Sources

### Primary (HIGH confidence)
- [Official BiometricPrompt guide](https://developer.android.com/identity/sign-in/biometric-auth) - API usage, authenticator combinations, API level restrictions
- [BiometricManager.Authenticators reference](https://developer.android.com/reference/androidx/biometric/BiometricManager.Authenticators) - Constant values, valid combinations
- [AndroidX Biometric releases](https://developer.android.com/jetpack/androidx/releases/biometric) - Version history, 1.1.0 stable confirmed
- [BiometricPrompt source code](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-biometric-release/biometric/biometric/src/main/java/androidx/biometric/BiometricPrompt.java) - Internal API-level handling, AuthenticatorUtils validation

### Secondary (MEDIUM confidence)
- [ProcessLifecycleOwner docs](https://developer.android.com/reference/android/arch/lifecycle/ProcessLifecycleOwner) - 700ms debounce behavior, ON_DESTROY never fires
- [AndroidX Biometric Overview (Seven Peaks)](https://sevenpeakssoftware.com/blog/androidx-biometric-overview/) - Practical implementation patterns
- [Google Issue Tracker #142740104](https://issuetracker.google.com/issues/142740104) - API 29 device credential bug (fixed in 1.1.0)

### Tertiary (LOW confidence)
- [Banking app lock guide (Medium)](https://medium.com/@sharmapraveen91/building-a-secure-app-lock-for-android-banking-apps-a-comprehensive-guide-af23eeda3329) - Architecture patterns (could not access full content, 403)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - biometric:1.1.0 is stable since Jan 2021; lifecycle-process:2.6.2 matches existing deps
- Architecture: HIGH - Single-Activity + BiometricPrompt in Activity is the documented pattern; ProcessLifecycleOwner is the standard approach
- API compatibility: HIGH - Verified against official docs and library source code; API 28-29 restriction confirmed from multiple sources
- Pitfalls: HIGH - All pitfalls verified through official documentation and known issue tracker bugs

**Research date:** 2026-03-04
**Valid until:** 2026-04-04 (stable APIs, no expected breaking changes)
