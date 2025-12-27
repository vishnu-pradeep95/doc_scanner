# PDF Scanner App

A modern Android document scanner app built with CameraX, ML Kit, and Material Design 3. Capture documents, apply filters, extract text with OCR, and generate multi-page PDFs.

**🎨 Now featuring a Studio Ghibli-inspired design** with warm, natural colors and a gentle aesthetic.

## 🚀 Quick Start (For Beginners)

**See [SETUP_GUIDE.md](SETUP_GUIDE.md) for detailed instructions including:**
- Installing Android Studio
- Understanding the project structure
- Building and running the app
- Debugging tips
- Key Android concepts explained

## Features

### 📷 Capture & Scan
- **Camera Capture** - High-quality document capture with CameraX
- **Auto Edge Detection** - ML Kit Document Scanner with automatic boundary detection
- **Batch Scanning** - Capture multiple pages quickly without preview interruption
- **Gallery Import** - Import existing photos from gallery

### ✂️ Edit & Enhance
- **Crop & Rotate** - Precise editing with CanHub Image Cropper
- **Page Rotation** - Rotate pages 90° at a time from page view
- **Document Filters** - 7 professional filters including Enhanced, B&W, Sepia, and more
- **Re-edit Pages** - Tap any page to crop/rotate again

### 📄 Organize & Export
- **Page Reordering** - Drag & drop to rearrange pages
- **Multi-Selection** - Long-press to select multiple pages
- **Selection Order PDF** - Create PDF from selected pages in tap order
- **Batch Delete** - Delete multiple selected pages at once
- **Custom PDF Names** - Name your PDFs before saving

### 🔍 Text Recognition
- **OCR** - Extract text from scanned documents using ML Kit
- **Selected Pages OCR** - Run OCR on just selected pages
- **Copy Text** - Copy extracted text to clipboard
- **Clear Icon** - Distinctive "Aa" icon for text recognition

### 📚 Document Management
- **Home Screen** - Quick access to scan, history, and recent documents
- **Document History** - Access all previously created PDFs
- **Secure Sharing** - Share PDFs via FileProvider
- **Privacy First** - All files stored in app-private storage

### 🌙 Appearance
- **Dark Mode** - System-synced, light, or dark themes
- **Studio Ghibli Design** - Warm, nature-inspired color palette
- **Smooth Transitions** - Polished UI with Material 3

## Screenshots

| Camera | Preview + Filters | Pages | Selection Mode |
|--------|-------------------|-------|----------------|
| Capture or auto-scan | Apply filters & crop | Manage & reorder | Multi-select pages |

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
| ML Kit Text Recognition 16.0 | On-device OCR |
| ML Kit Document Scanner 16.0.0-beta1 | Auto edge detection |
| Navigation Component | Fragment navigation |
| Material Design 3 | UI components |

## Project Structure

```
app/src/main/
├── java/com/pdfscanner/app/
│   ├── MainActivity.kt          # App entry point
│   ├── adapter/
│   │   ├── PagesAdapter.kt      # Page thumbnails (drag & drop + multi-select)
│   │   └── HistoryAdapter.kt    # Document history list
│   ├── data/
│   │   └── DocumentHistory.kt   # PDF history storage (SharedPrefs + JSON)
│   ├── ocr/
│   │   └── OcrProcessor.kt      # ML Kit Text Recognition
│   ├── ui/
│   │   ├── HomeFragment.kt      # Home screen with quick actions
│   │   ├── CameraFragment.kt    # Camera + batch mode + auto-scan
│   │   ├── PreviewFragment.kt   # Image preview/edit + filters
│   │   ├── PagesFragment.kt     # Page list, selection mode, PDF generation
│   │   ├── HistoryFragment.kt   # Document history screen
│   │   └── SettingsFragment.kt  # Theme and app settings
│   ├── util/
│   │   ├── ImageProcessor.kt    # Document filters (7 modes)
│   │   ├── DocumentScanner.kt   # ML Kit Document Scanner integration
│   │   └── ThemeManager.kt      # Dark mode handling
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

The app includes 7 professional document filters:

| Filter | Description |
|--------|-------------|
| **Original** | No processing - use captured image as-is |
| **Enhanced** | Contrast boost + brightness adjustment for better text |
| **B&W** | High contrast grayscale for clean document look |
| **Sepia** | Warm vintage tone for documents |
| **High Contrast** | Maximum text legibility |
| **Magic Color** | Enhanced vibrancy for colored documents |
| **Auto** | Smart processing based on document content |

Filters use Android's `ColorMatrix` and `RenderScript` for hardware-accelerated processing.

## Multi-Selection Mode

Long-press any page to enter selection mode:
- Tap pages to select/deselect (numbered badges show selection order)
- Create PDF from selected pages in the order you tapped them
- Delete multiple pages at once
- Exit with the X button in toolbar

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
- [x] Phase 4: OCR with ML Kit Text Recognition
- [x] Phase 5: Auto-edge detection, multi-selection, modern UI
- [x] Phase 6: Home screen, dark mode, advanced filters, Studio Ghibli design
- [ ] Phase 7: Cloud backup, annotations, folder organization

## Changelog

### v1.6.0 (Phase 6) - January 2025
- ✨ **Home Screen** - New landing page with quick actions and recent documents
- ✨ **Dark Mode** - Full dark theme support (system, light, dark)
- ✨ **Studio Ghibli Design** - Warm, nature-inspired color palette with soft gradients
- ✨ **7 Document Filters** - Added Sepia, High Contrast, Magic Color, Auto modes
- ✨ **Page Rotation** - Rotate individual pages from page view
- ✨ **Selected Pages OCR** - Run OCR on just the selected pages
- 🎨 **Modernized UI** - Replaced harsh blue with soft cream/forest gradients
- 🎨 **Improved Cards** - Nature-inspired gradient cards on home screen

### v1.5.0 (Phase 5) - December 2024
- ✨ **Auto Edge Detection** - ML Kit Document Scanner integration
- ✨ **Multi-Selection Mode** - Long-press to select multiple pages
- ✨ **Selection Order PDF** - Create PDF in the order pages were selected
- ✨ **Batch Delete** - Delete multiple pages at once
- 🎨 **Modern UI** - Updated colors, Material 3 styling, improved cards
- 🔧 **OCR Icon** - Clear "Aa" icon for text recognition
- 🔧 **Dynamic Loading Text** - Context-aware loading messages

### v1.4.0 (Phase 4)
- OCR text recognition with ML Kit
- Copy extracted text to clipboard

### v1.3.0 (Phase 3)
- Page drag & drop reordering
- Batch scanning mode
- Document history with timestamps
- Re-edit pages by tapping

### v1.2.0 (Phase 2)
- Document enhancement filters
- Custom PDF naming

### v1.1.0 (Phase 1)
- Initial release
- Camera capture, crop, PDF generation

## License

MIT License
