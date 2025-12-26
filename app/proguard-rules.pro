# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep CameraX classes
-keep class androidx.camera.** { *; }

# Keep CanHub Image Cropper
-keep class com.canhub.cropper.** { *; }
