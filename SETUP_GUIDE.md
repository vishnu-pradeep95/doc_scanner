# Android PDF Scanner App - Beginner's Complete Guide

## Table of Contents
1. [Setting Up Your Development Environment](#1-setting-up-your-development-environment)
2. [Understanding the Project Structure](#2-understanding-the-project-structure)
3. [Building the App](#3-building-the-app)
4. [Running & Testing](#4-running--testing)
5. [Debugging](#5-debugging)
6. [Key Android Concepts](#6-key-android-concepts)
7. [Common Issues & Solutions](#7-common-issues--solutions)

---

## 1. Setting Up Your Development Environment

### Step 1: Install Android Studio
1. Download Android Studio from: https://developer.android.com/studio
2. Run the installer and follow the wizard
3. Select "Standard" installation (includes Android SDK, Emulator, etc.)
4. Wait for downloads to complete (~2-4 GB)

### Step 2: First Launch Configuration
1. Open Android Studio
2. It will download additional SDK components automatically
3. Go to **File → Settings → Appearance & Behavior → System Settings → Android SDK**
4. Ensure **Android 14 (API 34)** is checked and installed
5. Under **SDK Tools** tab, ensure these are installed:
   - Android SDK Build-Tools
   - Android SDK Platform-Tools
   - Android Emulator
   - Intel x86 Emulator Accelerator (HAXM) - for faster emulation

### Step 3: Open This Project
1. In Android Studio: **File → Open**
2. Navigate to `C:\Users\vishn\Documents\pdf_scanner_app`
3. Click **OK**
4. Wait for Gradle sync (bottom progress bar) - first time takes 5-10 minutes
5. If prompted to update Gradle or plugins, click **Update**

### Step 4: Create an Emulator (Virtual Device)
1. Go to **Tools → Device Manager** (or click phone icon in toolbar)
2. Click **Create Device**
3. Select **Pixel 6** (or any phone with Play Store icon)
4. Click **Next**
5. Download **API 34** system image (click download icon)
6. Click **Next → Finish**

---

## 2. Understanding the Project Structure

```
pdf_scanner_app/
│
├── build.gradle.kts          # Root build file (project-level settings)
├── settings.gradle.kts       # Defines which modules to include
├── gradle.properties         # Gradle configuration
│
└── app/                      # Main application module
    ├── build.gradle.kts      # App dependencies & config (IMPORTANT!)
    │
    └── src/main/
        ├── AndroidManifest.xml    # App declaration, permissions, components
        │
        ├── java/.../              # Kotlin/Java source code
        │   ├── MainActivity.kt    # Entry point (like main() in C++)
        │   ├── ui/                # Screen fragments (UI controllers)
        │   ├── viewmodel/         # Data holders (survives rotation)
        │   └── adapter/           # RecyclerView data binders
        │
        └── res/                   # Resources (non-code files)
            ├── layout/            # XML UI definitions (like HTML)
            ├── values/            # Strings, colors, dimensions
            ├── drawable/          # Vector graphics, shapes
            ├── navigation/        # Screen flow definition
            └── xml/               # Other XML configs
```

### Key Files Explained (C++/Python Analogies)

| Android File | Similar To | Purpose |
|-------------|------------|---------|
| `AndroidManifest.xml` | Header/Config | Declares app name, permissions, entry points |
| `build.gradle.kts` | CMakeLists.txt / requirements.txt | Dependencies & build settings |
| `MainActivity.kt` | `main()` function | App entry point |
| `Fragment` | A UI module/screen | Controls one screen |
| `ViewModel` | Global state manager | Holds data across screen rotations |
| `layout/*.xml` | HTML templates | Define UI structure |
| `R.id.xxx` | Auto-generated IDs | References to UI elements |

---

## 3. Building the App

### Method 1: Using Android Studio UI
1. Click the green **Run** button (▶) in toolbar
2. Select your emulator or connected device
3. Wait for build & installation

### Method 2: Using Terminal (in Android Studio)
```powershell
# Open Terminal in Android Studio: View → Tool Windows → Terminal

# Build debug APK
./gradlew assembleDebug

# Build release APK (unsigned)
./gradlew assembleRelease

# APK location after build:
# app/build/outputs/apk/debug/app-debug.apk
```

### Build Variants
- **Debug**: For development, includes debugging symbols, slower
- **Release**: Optimized, minified, for distribution

---

## 4. Running & Testing

### On Emulator
1. Start emulator from Device Manager
2. Click **Run** (▶) → Select emulator
3. App installs and launches automatically

### On Physical Device
1. **Enable Developer Options** on your phone:
   - Go to Settings → About Phone
   - Tap "Build Number" 7 times
   - Go back, find "Developer Options"
   - Enable "USB Debugging"

2. Connect phone via USB cable

3. On phone, tap "Allow" when prompted for USB debugging

4. In Android Studio, your device appears in the dropdown

5. Click **Run** (▶)

### Testing Camera on Emulator
- Emulator has a virtual camera (shows a moving room scene)
- For real testing, use a physical device
- To simulate camera in emulator: **Extended Controls (...)** → **Camera**

---

## 5. Debugging

### Logcat (Console Output)
1. **View → Tool Windows → Logcat**
2. Filter by your app: Select `com.pdfscanner.app` in dropdown
3. Use these log levels in code:
```kotlin
import android.util.Log

Log.d("MyTag", "Debug message")      // Debug
Log.i("MyTag", "Info message")       // Info
Log.w("MyTag", "Warning message")    // Warning
Log.e("MyTag", "Error message")      // Error
```

### Breakpoints (Like GDB/Python debugger)
1. Click in the gutter (left of line numbers) to set breakpoint
2. Click **Debug** (🐛 bug icon) instead of Run
3. App pauses at breakpoint
4. Use Debug panel to:
   - **Step Over (F8)**: Next line
   - **Step Into (F7)**: Go into function
   - **Resume (F9)**: Continue execution
   - **Evaluate**: Check variable values

### Common Debug Scenarios
```kotlin
// Add this to see values at runtime
Log.d("DEBUG", "Variable value: $myVariable")
Log.d("DEBUG", "List size: ${myList.size}")
```

---

## 6. Key Android Concepts

### Activity vs Fragment
```
┌─────────────────────────────────────┐
│  Activity (MainActivity)            │  ← Container, like a window
│  ┌───────────────────────────────┐  │
│  │  Fragment (CameraFragment)    │  │  ← Actual UI screen
│  │  - Has its own lifecycle      │  │
│  │  - Can be swapped in/out      │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

### Lifecycle (When things happen)
```
Fragment Created → onCreateView() → UI inflated
                → onViewCreated() → Setup listeners, observe data
                
User leaves     → onDestroyView() → Cleanup, avoid memory leaks

// Always cleanup in onDestroyView():
override fun onDestroyView() {
    super.onDestroyView()
    _binding = null  // Prevent memory leak
}
```

### View Binding (Accessing UI elements)
```kotlin
// OLD way (error-prone):
val button = findViewById<Button>(R.id.myButton)

// NEW way (type-safe, used in this project):
// 1. Enable in build.gradle.kts:
buildFeatures { viewBinding = true }

// 2. In Fragment:
private var _binding: FragmentCameraBinding? = null
private val binding get() = _binding!!

// 3. Access views:
binding.btnCapture.setOnClickListener { ... }
```

### LiveData & ViewModel (Reactive data)
```kotlin
// ViewModel holds data that survives screen rotation
class ScannerViewModel : ViewModel() {
    private val _pages = MutableLiveData<List<Uri>>()  // Private, mutable
    val pages: LiveData<List<Uri>> = _pages            // Public, read-only
}

// Fragment observes changes
viewModel.pages.observe(viewLifecycleOwner) { pageList ->
    // This runs automatically when data changes
    updateUI(pageList)
}
```

### Navigation Component (Moving between screens)
```kotlin
// Define in nav_graph.xml, then:
findNavController().navigate(R.id.action_camera_to_preview)

// With arguments:
val action = CameraFragmentDirections.actionCameraToPreview(imageUri.toString())
findNavController().navigate(action)
```

---

## 7. Common Issues & Solutions

### Issue: "SDK location not found"
**Solution**: 
1. Create `local.properties` in project root
2. Add: `sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk`

### Issue: "Gradle sync failed"
**Solutions**:
1. **File → Invalidate Caches → Invalidate and Restart**
2. Delete `.gradle` folder in project and home directory
3. Check internet connection (Gradle downloads dependencies)

### Issue: "Cannot resolve symbol R"
**Solutions**:
1. **Build → Clean Project**, then **Build → Rebuild Project**
2. Check for XML errors (red highlighting in layout files)

### Issue: Camera permission denied
**Solution**: 
- On Android 6+, permissions are requested at runtime
- The app handles this in `CameraFragment.kt`
- If denied, go to phone Settings → Apps → PDF Scanner → Permissions

### Issue: App crashes on startup
**Debug steps**:
1. Check Logcat for red error messages
2. Look for "FATAL EXCEPTION" 
3. Stack trace shows which line caused crash

### Issue: Emulator too slow
**Solutions**:
1. Enable hardware acceleration (HAXM on Intel, Hyper-V on AMD)
2. Use x86_64 system image (not ARM)
3. Allocate more RAM to emulator
4. Use a physical device instead

---

## Quick Reference: Gradle Commands

```powershell
# In Android Studio Terminal or PowerShell in project folder:

# Sync dependencies
./gradlew --refresh-dependencies

# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Check for dependency updates
./gradlew dependencyUpdates

# See all available tasks
./gradlew tasks
```

---

## Next Steps for Learning

1. **Official Codelabs**: https://developer.android.com/courses
2. **Kotlin Basics**: https://kotlinlang.org/docs/basic-syntax.html
3. **Android Basics in Kotlin**: Free Google course
4. **YouTube**: Philipp Lackner, Coding in Flow (great Android tutorials)

---

## Project-Specific Notes

### File Storage Locations
- Scanned images: `Internal Storage/Android/data/com.pdfscanner.app/files/scans/`
- Generated PDFs: `Internal Storage/Android/data/com.pdfscanner.app/files/pdfs/`
- These are **app-private** - uninstalling app deletes them

### Permissions Used
- `CAMERA` - Required for CameraX to capture images
- No storage permissions needed (using app-private storage)

### Third-Party Libraries
- **CameraX**: Google's camera library (handles device quirks)
- **CanHub Image Cropper**: Fork of uCrop for crop/rotate
- **Navigation Component**: Screen navigation
- **Material Design 3**: UI components (buttons, cards, etc.)
