# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep CameraX classes
-keep class androidx.camera.** { *; }

# Keep CanHub Image Cropper
-keep class com.canhub.cropper.** { *; }

# ===== Phase 5: Release Readiness — ProGuard/R8 Keep Rules =====

# ML Kit Text Recognition (com.google.mlkit:text-recognition:16.0.0)
# ML Kit loads model processors via reflection — R8 would strip these without explicit keeps.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ML Kit Document Scanner (com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1)
# GMS-based scanner uses internal GMS task machinery loaded by class name.
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Navigation SafeArgs Generated Classes
# SafeArgs generates *Args and *Directions classes referenced by class name in the nav graph.
# All nav graph arguments in this project use primitive types (string, integer) — confirmed.
-keepnames class com.pdfscanner.app.**.*Args { *; }
-keepnames class com.pdfscanner.app.**.*Directions { *; }

# Coil 2.7.0 — consumer rules are AUTO-BUNDLED in the AAR via R8.
# No explicit rules needed when using R8 (default since AGP 3.4.0).

# Kotlin Coroutines 1.7.3 — consumer rules are AUTO-BUNDLED in kotlinx-coroutines-android.
# No explicit rules needed.

# ===== SEC-03: Strip verbose/debug/info log calls from release builds =====
# R8 removes the method calls AND associated string concatenation (AGP 7.3+ / R8 3.3.70+).
# Log.w and Log.e are intentionally retained for crash diagnostics.
# Only effective when isMinifyEnabled = true (release builds).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ===== SEC-08: Tink (via security-crypto:1.1.0) R8 compatibility =====
# Tink references error-prone annotations at compile time but they are not
# runtime dependencies. R8 full mode (AGP 8.0+) flags these as missing.
-dontwarn com.google.errorprone.annotations.**

# ===== SEC-09: Tink 1.20.0 StreamingAead — file encryption at rest =====
# Tink loads key managers and primitive wrappers via reflection.
# R8 full mode strips these without explicit keeps.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
# Tink depends on protobuf-lite at runtime for keyset serialization
-dontwarn com.google.protobuf.**
