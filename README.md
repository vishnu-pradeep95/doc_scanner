# PDF Scanner App

A modern Android document scanner app built with CameraX, ML Kit, and Material Design 3. Capture documents, apply filters, extract text with OCR, and generate multi-page PDFs.

**🎨 Featuring a Studio Ghibli-inspired design** with warm, natural colors and a gentle aesthetic.

## 🚀 Quick Start

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- JDK 17
- Min SDK: 24 (Android 7.0)

### Clone & Run
```bash
# Clone the repository
git clone https://github.com/yourusername/pdf_scanner_app.git
cd pdf_scanner_app

# Open in Android Studio or build from command line
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

**📖 New to Android development? See [SETUP_GUIDE.md](SETUP_GUIDE.md) for detailed instructions.**

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

### 📄 PDF Operations
- **Multi-page PDFs** - Combine scanned pages into a single PDF
- **Custom PDF Names** - Name your PDFs before saving
- **Merge PDFs** - Combine multiple existing PDFs into one
- **Split PDFs** - Split a PDF into individual page files
- **Compress PDFs** - Reduce PDF file size with JPEG compression (Low/Medium/High)

### 🔍 Text Recognition (OCR)
- **Full OCR** - Extract text from all scanned documents using ML Kit
- **Selected Pages OCR** - Run OCR on just selected pages
- **Copy Text** - Copy extracted text to clipboard
- **Works Offline** - On-device ML processing

### 📚 Document Management
- **Home Screen** - Quick access to scan, history, and recent documents
- **Document History** - Access all previously created PDFs with multi-select
- **Share** - Share single or multiple PDFs via any app
- **Delete** - Delete individual or multiple documents
- **View PDFs** - Built-in PDF viewer or open in external apps

### 🌙 Appearance
- **Dark Mode** - System-synced, light, or dark themes
- **Studio Ghibli Design** - Warm, nature-inspired color palette
- **Smooth UI** - Material Design 3 components

## Key Dependencies

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      MainActivity                           │
│                    (NavHostFragment)                        │
├─────────────────────────────────────────────────────────────┤
│  HomeFragment → CameraFragment → PreviewFragment            │
│       │              ↓                  ↓                   │
│       │         PagesFragment ←─────────┘                   │
│       │              │                                      │
│       └──→ HistoryFragment → PdfViewerFragment              │
│       └──→ SettingsFragment                                 │
└─────────────────────────────────────────────────────────────┘
```

- **Single Activity** with Navigation Component
- **MVVM** pattern with `ScannerViewModel` shared across fragments
- **View Binding** for type-safe view access
- **Coroutines** for async operations (image processing, PDF generation)
- **FileProvider** for secure file sharing between apps

## Key Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| CameraX | 1.3.x | Camera capture with lifecycle awareness |
| CanHub Image Cropper | 4.5.0 | Crop/rotate functionality |
| ML Kit Text Recognition | 16.0.x | On-device OCR |
| ML Kit Document Scanner | 16.0.0-beta1 | Auto edge detection |
| Navigation Component | 2.7.x | Fragment navigation with Safe Args |
| Material Design 3 | 1.11.x | UI components |

## Project Structure

```
app/src/main/
├── java/com/pdfscanner/app/
│   ├── MainActivity.kt              # Single activity entry point
│   │
│   ├── adapter/
│   │   ├── PagesAdapter.kt          # Page grid with drag-drop & multi-select
│   │   ├── HistoryAdapter.kt        # Document history list with selection
│   │   └── RecentDocumentsAdapter.kt # Home screen recent docs
│   │
│   ├── data/
│   │   └── DocumentHistory.kt       # PDF history storage (SharedPrefs + JSON)
│   │
│   ├── ocr/
│   │   └── OcrProcessor.kt          # ML Kit Text Recognition wrapper
│   │
│   ├── ui/
│   │   ├── HomeFragment.kt          # Landing screen with quick actions
│   │   ├── CameraFragment.kt        # CameraX + batch mode + auto-scan
│   │   ├── PreviewFragment.kt       # Image preview, filters, crop
│   │   ├── PagesFragment.kt         # Page management, PDF generation
│   │   ├── HistoryFragment.kt       # Document history with merge/split/compress
│   │   ├── PdfViewerFragment.kt     # Built-in PDF viewer
│   │   └── SettingsFragment.kt      # Theme and app settings
│   │
│   ├── util/
│   │   ├── ImageProcessor.kt        # 7 document filter modes
│   │   ├── PdfUtils.kt              # Merge, split, compress PDF operations
│   │   ├── DocumentScanner.kt       # ML Kit Document Scanner integration
│   │   └── AppPreferences.kt        # SharedPreferences wrapper
│   │
│   └── viewmodel/
│       └── ScannerViewModel.kt      # Shared state across fragments
│
└── res/
    ├── layout/                      # Fragment and item layouts
    ├── menu/                        # Toolbar menus
    ├── navigation/nav_graph.xml     # Navigation flow definition
    ├── drawable/                    # Vector icons, shapes, gradients
    ├── values/                      # Strings, colors, themes, dimensions
    └── xml/file_paths.xml           # FileProvider path configuration
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
- [x] Phase 7: PDF operations (merge, split, compress), improved navigation
- [ ] Phase 8: Cloud backup, annotations, folder organization

## File Storage

All files are stored in app-private storage (no storage permissions needed):

| Type | Location | Example |
|------|----------|---------|
| Scanned Images | `filesDir/scans/` | `SCAN_20251227_143052.jpg` |
| Processed Images | `filesDir/processed/` | `PROC_20251227_143052.jpg` |
| Generated PDFs | `filesDir/pdfs/` | `MyDocument_20251227_143052.pdf` |

**Note:** Uninstalling the app deletes all files.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Changelog

### v1.7.0 (Phase 7) - December 2025
- ✨ **PDF Merge** - Combine multiple PDFs into one document
- ✨ **PDF Split** - Split PDF into individual page files
- ✨ **PDF Compression** - Reduce file size with JPEG compression (3 levels)
- ✨ **Home Button** - Quick navigation to home from all screens
- ✨ **Multi-Select Share/Delete** - Share or delete multiple documents at once
- 🔧 **FileProvider Fix** - Fixed sharing issues with correct authority
- 🎨 **UI Polish** - Rounded corners, consistent styling

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
