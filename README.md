# PDF Scanner App

A simple Android app to scan documents using CameraX, edit them with crop/rotate via CanHub Image Cropper, apply document-style filters, and generate multi-page PDFs.

## 🚀 Quick Start (For Beginners)

**See [SETUP_GUIDE.md](SETUP_GUIDE.md) for detailed instructions including:**
- Installing Android Studio
- Understanding the project structure
- Building and running the app
- Debugging tips
- Key Android concepts explained

## Features

- 📷 **Camera Capture** - Use CameraX to capture document images
- ⚡ **Batch Scanning** - Capture multiple pages quickly without preview interruption
- ✂️ **Crop & Rotate** - Edit scanned images with CanHub Image Cropper
- 🎨 **Document Filters** - Enhance text with Original, Enhanced, and B&W modes
- 🔀 **Page Reordering** - Drag & drop to rearrange pages before PDF creation
- 📝 **Custom PDF Names** - Name your PDFs before saving
- 📄 **Multi-page PDF** - Combine multiple scans into a single PDF
- 📤 **Secure Sharing** - Share PDFs via FileProvider with proper permissions
- 📚 **Document History** - Access and manage all previously created PDFs
- 🔒 **Privacy First** - All files stored in app-private storage
- 🔮 **OCR Ready** - Text recognition framework in place (coming soon)

## Screenshots

| Camera | Preview + Filters | Pages |
|--------|-------------------|-------|
| Capture documents | Apply filters & crop | Manage & create PDF |

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
│   │   ├── PagesAdapter.kt      # Page thumbnails (with drag & drop)
│   │   └── HistoryAdapter.kt    # Document history list
│   ├── data/
│   │   └── DocumentHistory.kt   # PDF history storage (SharedPrefs + JSON)
│   ├── ocr/
│   │   └── OcrProcessor.kt      # OCR design stub (ML Kit ready)
│   ├── ui/
│   │   ├── CameraFragment.kt    # Camera screen + batch mode
│   │   ├── PreviewFragment.kt   # Image preview/edit + filters
│   │   ├── PagesFragment.kt     # Page list & PDF generation
│   │   └── HistoryFragment.kt   # Document history screen
│   ├── util/
│   │   └── ImageProcessor.kt    # Document filters (Enhanced, B&W)
│   └── viewmodel/
│       └── ScannerViewModel.kt  # Shared data holder
└── res/
    ├── layout/                  # XML UI layouts
    ├── menu/                    # Toolbar menus
    ├── navigation/              # Navigation graph
    ├── drawable/                # Vector icons & shapes
    ├── values/                  # Strings, colors, themes
    └── xml/                     # FileProvider config
```

## Document Filters

The app includes document-style filters to improve text legibility:

| Filter | Description |
|--------|-------------|
| **Original** | No processing - use captured image as-is |
| **Enhanced** | 30% contrast boost + brightness adjustment |
| **B&W** | Grayscale + high contrast for clean document look |

Filters use Android's `ColorMatrix` for hardware-accelerated processing.

## Code Documentation

All Kotlin source files contain extensive comments explaining:
- What each class/function does
- Why certain patterns are used
- Android concepts (lifecycle, binding, etc.)
- Analogies to C++/Python where applicable

## Roadmap

- [x] Phase 1: Basic scanning (capture, crop, PDF)
- [x] Phase 2: Document filters & UX improvements
- [x] Phase 3: Page reordering, batch scanning, document history
- [ ] Phase 4: OCR with ML Kit Text Recognition
- [ ] Phase 5: Auto-edge detection, folders, search

## License

MIT License
