# Android PDF Scanner App - Developer Setup Guide

## Table of Contents
1. [Setting Up Your Development Environment](#1-setting-up-your-development-environment)
2. [Understanding the Project Structure](#2-understanding-the-project-structure)
3. [Building the App](#3-building-the-app)
4. [Running & Testing](#4-running--testing)
5. [Debugging](#5-debugging)
6. [Key Android Concepts](#6-key-android-concepts)
7. [Common Issues & Solutions](#7-common-issues--solutions)
8. [Code Style & Conventions](#8-code-style--conventions)
9. [Feature Implementation Guide](#9-feature-implementation-guide)

---

## 1. Setting Up Your Development Environment

### Prerequisites
- **Operating System**: Windows 10/11, macOS 10.14+, or Linux
- **RAM**: 8GB minimum (16GB recommended)
- **Disk Space**: 10GB for Android Studio + SDK

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
   - Android SDK Build-Tools 34.0.0
   - Android SDK Platform-Tools
   - Android Emulator
   - Intel x86 Emulator Accelerator (HAXM) - for faster emulation

### Step 3: Clone and Open the Project
```bash
# Clone the repository
git clone https://github.com/yourusername/pdf_scanner_app.git
cd pdf_scanner_app

# Or open existing project
# In Android Studio: File → Open → Navigate to project folder
```

1. In Android Studio: **File → Open**
2. Navigate to the project folder
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
- Processed images: `Internal Storage/Android/data/com.pdfscanner.app/files/processed/`
- Generated PDFs: `Internal Storage/Android/data/com.pdfscanner.app/files/pdfs/`
- These are **app-private** - uninstalling app deletes them

### Permissions Used
- `CAMERA` - Required for CameraX to capture images
- No storage permissions needed (using app-private storage)

### Third-Party Libraries
- **CameraX**: Google's camera library (handles device quirks)
- **CanHub Image Cropper**: Fork of uCrop for crop/rotate
- **ML Kit Text Recognition**: On-device OCR for text extraction
- **ML Kit Document Scanner**: Automatic document edge detection
- **Navigation Component**: Screen navigation
- **Material Design 3**: UI components (buttons, cards, etc.)

### Key Features Guide

#### Home Screen
The new home screen provides quick access to:
- **New Scan** - Jump directly to camera
- **Import** - Import images AND PDFs from device
- **Document History** - View all saved PDFs
- **Recent Documents** - Quick access to last 3 PDFs
- **PDF Tools** - Merge, Split, Compress PDFs
- **Settings** - Theme and app preferences

#### Import (Images & PDFs)
Tap the import icon to pick files:
- **Images**: Added directly to pages for editing
- **PDFs**: Pages are extracted as images for editing
- **Mixed**: Can select both images and PDFs at once
- Extracted PDF pages can be cropped, filtered, and recombined

#### PDF Editor
Open any PDF from history and tap "Edit" to access:
- **Annotations Toolbar**: Select, Sign, Text, Highlight, Stamp, Draw, Shapes
- **Selection Tool**: Tap annotations to select, then resize or delete
- **Signature Tool**: Draw signatures, save for reuse
- **Text Tool**: Add text with customizable size and color
- **Highlight Tool**: Semi-transparent rectangles for marking
- **Stamp Tool**: Pre-made stamps (Approved, Confidential, etc.)
- **Draw Tool**: Freehand drawing with color picker
- **Shapes Tool**: Rectangles, ovals, lines, arrows, checkmarks
- **Undo/Redo**: Full history of edits
- **Save**: Export annotated PDF

#### Auto-Scan (ML Kit Document Scanner)
Tap the magic wand icon in the camera screen to launch ML Kit's built-in document scanner:
- Automatic edge detection
- Real-time guidance for optimal capture angle
- Automatic perspective correction
- Multi-page scanning in one session

#### Multi-Selection Mode
Long-press any page thumbnail to enter selection mode:
- Selected pages show numbered badges (1, 2, 3...)
- Numbers indicate the order you selected them
- "Create PDF" uses this selection order
- "Delete" removes all selected pages
- "OCR" extracts text from selected pages only
- Tap X in toolbar to exit selection mode

#### Page Rotation
Tap the rotate button below any page thumbnail to rotate it 90° clockwise.

#### OCR (Text Recognition)
Tap the "Aa" icon in pages screen:
- Text is extracted from all pages (or just selected pages in selection mode)
- View results in a dialog
- Copy text to clipboard with one tap
- Works offline using on-device ML

#### Document Filters
In preview screen, choose from 7 filters:
- **Original**: No processing
- **Enhanced**: Better contrast for text
- **B&W**: High contrast grayscale
- **Sepia**: Warm vintage tone
- **High Contrast**: Maximum text legibility
- **Magic Color**: Enhanced vibrancy
- **Auto**: Smart processing based on content

#### Dark Mode
Go to Settings to choose your theme:
- **System Default** - Follows your phone's theme
- **Light** - Always light
- **Dark** - Always dark with Studio Ghibli-inspired night colors

#### Studio Ghibli Design
The app uses a warm, nature-inspired color palette:
- Soft sky blues and forest greens
- Warm sunset accents
- Cream-colored surfaces
- Gentle gradients instead of harsh solid colors

---

## 8. Code Style & Conventions

### Kotlin Style
```kotlin
// Use meaningful names
val selectedDocuments: List<DocumentEntry>  // Good
val docs: List<DocumentEntry>               // Avoid

// Use extension functions for readability
fun Uri.toFile(): File = File(this.path!!)

// Use scope functions appropriately
binding.apply {
    btnCapture.setOnClickListener { capture() }
    btnGallery.setOnClickListener { openGallery() }
}
```

### View Binding Pattern
```kotlin
// Always use this pattern in Fragments
private var _binding: FragmentCameraBinding? = null
private val binding get() = _binding!!

override fun onCreateView(...): View {
    _binding = FragmentCameraBinding.inflate(inflater, container, false)
    return binding.root
}

override fun onDestroyView() {
    super.onDestroyView()
    _binding = null  // Prevent memory leaks!
}
```

### LiveData Encapsulation
```kotlin
// In ViewModel - private mutable, public immutable
private val _pages = MutableLiveData<List<Uri>>(emptyList())
val pages: LiveData<List<Uri>> = _pages

// To update, reassign the entire list
fun addPage(uri: Uri) {
    val current = _pages.value.orEmpty().toMutableList()
    current.add(uri)
    _pages.value = current  // Triggers observers
}
```

### FileProvider Usage
```kotlin
// Always use the correct authority from AndroidManifest.xml
val uri = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",  // Must match manifest!
    file
)
```

---

## 9. Feature Implementation Guide

### Adding a New Fragment
1. Create layout file: `res/layout/fragment_new.xml`
2. Create Kotlin class: `ui/NewFragment.kt`
3. Add to navigation graph: `res/navigation/nav_graph.xml`
4. Add navigation actions from other fragments

### Adding a New Menu Item
1. Create/edit menu XML: `res/menu/menu_fragment.xml`
2. Add menu to toolbar in layout: `app:menu="@menu/menu_fragment"`
3. Handle clicks in Fragment:
```kotlin
binding.toolbar.setOnMenuItemClickListener { menuItem ->
    when (menuItem.itemId) {
        R.id.action_new -> { doSomething(); true }
        else -> false
    }
}
```

### Adding a New Filter
1. Open `util/ImageProcessor.kt`
2. Add new enum value to `FilterMode`
3. Implement filter in `applyFilter()` function
4. Add button in `fragment_preview.xml`

### Adding PDF Operations
1. Open `util/PdfUtils.kt`
2. Add new suspend function following existing patterns
3. Handle in Fragment with coroutines:
```kotlin
lifecycleScope.launch {
    val result = PdfUtils.newOperation(context, params)
    if (result.success) {
        // Update UI
    }
}
```

---

## Quick Command Reference

```powershell
# Build commands
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew installDebug           # Build and install on device
./gradlew clean                  # Clean build outputs

# Testing
./gradlew test                   # Run unit tests
./gradlew connectedAndroidTest   # Run instrumented tests

# Code quality
./gradlew lint                   # Run lint checks
./gradlew ktlintCheck            # Check Kotlin style

# Dependency management
./gradlew dependencies           # Show dependency tree
./gradlew --refresh-dependencies # Force refresh dependencies
```

---

## Getting Help

- **Android Documentation**: https://developer.android.com/docs
- **Kotlin Documentation**: https://kotlinlang.org/docs/
- **Stack Overflow**: Tag questions with `android`, `kotlin`
- **Project Issues**: Open an issue on GitHub

---

## License

MIT License - See LICENSE file for details