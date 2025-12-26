# PDF Scanner App

A simple Android app to scan documents using CameraX, edit them with crop/rotate via CanHub Image Cropper, and generate multi-page PDFs.

## 🚀 Quick Start (For Beginners)

**See [SETUP_GUIDE.md](SETUP_GUIDE.md) for detailed instructions including:**
- Installing Android Studio
- Understanding the project structure
- Building and running the app
- Debugging tips
- Key Android concepts explained

## Features

- 📷 **Camera Capture** - Use CameraX to capture document images
- ✂️ **Crop & Rotate** - Edit scanned images with CanHub Image Cropper
- 📄 **Multi-page PDF** - Combine multiple scans into a single PDF
- 📤 **Secure Sharing** - Share PDFs via FileProvider with proper permissions
- 🔒 **Privacy First** - All files stored in app-private storage

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- Kotlin 1.9.x
- Min SDK: 24 (Android 7.0)

## Setup

1. Open the project in Android Studio
2. Sync Gradle files (happens automatically, or File → Sync Project with Gradle Files)
3. Run on device or emulator with camera support

## Architecture

- **Single Activity** with Navigation Component
- **MVVM** pattern with `ScannerViewModel`
- **View Binding** for type-safe view access

## Key Dependencies

| Library | Purpose |
|---------|---------|
| CameraX 1.3.x | Camera capture |
| CanHub Image Cropper 4.5.0 | Crop/rotate functionality |
| Navigation Component | Fragment navigation |
| Material Design 3 | UI components |

## Project Structure

```
app/src/main/
├── java/com/pdfscanner/app/
│   ├── MainActivity.kt          # App entry point
│   ├── adapter/
│   │   └── PagesAdapter.kt      # RecyclerView adapter
│   ├── ui/
│   │   ├── CameraFragment.kt    # Camera screen
│   │   ├── PreviewFragment.kt   # Image preview/edit
│   │   └── PagesFragment.kt     # Page list & PDF
│   └── viewmodel/
│       └── ScannerViewModel.kt  # Shared data holder
└── res/
    ├── layout/                  # XML UI layouts
    ├── navigation/              # Navigation graph
    ├── drawable/                # Vector icons & shapes
    ├── values/                  # Strings, colors, themes
    └── xml/                     # FileProvider config
```

## Code Documentation

All Kotlin source files contain extensive comments explaining:
- What each class/function does
- Why certain patterns are used
- Android concepts (lifecycle, binding, etc.)
- Analogies to C++/Python where applicable

## License

MIT License
