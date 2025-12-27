# 📄 PDF Scanner - Beautiful Document Scanner for Android

<div align="center">

**Scan • Edit • Share**

*A delightful document scanner with playful mascot icons and smooth animations!*

[![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-blue)](https://kotlinlang.org)
[![Material Design 3](https://img.shields.io/badge/Material%20Design-3-purple)](https://m3.material.io)

</div>

---

## ✨ What Makes This Special

This isn't just another document scanner - it's a **joyful experience**! Every icon has personality with googly eyes 👀, every tap has a satisfying bounce animation, and the entire UI follows a playful cartoon theme that makes scanning documents actually fun.

### 🎨 Design Philosophy
- **Mascot-Style Icons** - Inspired by playful web service logos (REST, SOAP, GraphQL style)
- **Googly Eyes Everywhere** - Camera smiles, documents hug, scissors wink
- **Thick Black Outlines** - That sticker aesthetic (2.5-3px strokes)
- **Vibrant Colors** - Coral Red, Turquoise, Yellow from our cartoon palette
- **Smooth Animations** - Bounce effects, sparkle rings, delightful feedback

---

## 🚀 Features

### 📸 Scanning & Capture
- **CameraX Integration** - Professional camera with live preview
- **Auto Document Scanner** - ML Kit powered edge detection
- **Batch Mode** - Quickly capture multiple pages in sequence
- **Document Filters** - Original, Magic, Enhanced, Sharpen, B&W modes
- **Smart Cropping** - Precise edge adjustment with CanHub
- **Import Images** - Add photos from gallery

### 🎨 Beautiful Mascot UI
- **Playful Mascot Icons** - 20+ custom sticker-style icons with googly eyes
- **Bounce Animations** - Every card tap feels satisfying
- **Sparkle Effects** - Camera capture button with animated ring
- **Nunito Font** - Custom bundled fonts (4 weights)
- **Soft Corners** - 28dp radius for friendly, approachable feel
- **Cartoon Theme** - Coral Red (#FF6B6B), Turquoise (#4ECDC4), Yellow (#FFE66D)
- **Dark Mode Ready** - Full dark theme support

### 📋 Document Management
- **Multi-Page PDFs** - Combine pages with custom naming
- **Drag & Drop** - Reorder pages with smooth animations
- **Selection Mode** - Multi-select pages (long-press to activate)
- **Recent Documents** - Beautiful thumbnails with rounded corners
- **Document History** - Track all your scanned PDFs
- **Easy Sharing** - Share via any app with FileProvider
- **PDF Rename** - Custom names for your documents

### 🛠️ PDF Tools
- **Merge PDFs** 💕 - Combine multiple PDFs into one
- **Split PDFs** ✂️ - Extract specific pages from a PDF
- **Compress PDFs** 💨 - Reduce file size for sharing

### 🔍 OCR (Text Recognition)
- **ML Kit Integration** - Extract text from documents
- **Multi-Page OCR** - Process all pages at once
- **Copy to Clipboard** - One-tap text copying
- **Offline Processing** - Works without internet

---

## 📱 Screenshots

*Coming soon - This app looks amazing!*

---

## 🛠️ Tech Stack

### Core
- **Language**: Kotlin (100%)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM with Single Activity

### Key Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| **CameraX** | 1.3.1 | Camera preview & capture |
| **CanHub Image Cropper** | 4.5.0 | Document cropping |
| **ML Kit Text Recognition** | 16.0.x | OCR functionality |
| **ML Kit Document Scanner** | 16.0.0-beta1 | Auto edge detection |
| **Navigation Component** | 2.7.x | Fragment navigation |
| **Material Design 3** | Latest | UI components |
| **Nunito Font** | Bundled | Custom typography |

---

## 🏗️ Architecture

### Single-Activity + Navigation Component
```
MainActivity (NavHostFragment)
    ↓
┌─────────────────────────────────┐
│  HomeFragment (Landing Page)    │
│    - Quick Actions              │
│    - Recent Documents           │
│    - PDF Tools                  │
└─────────────────────────────────┘
           ↓
    ┌──────────┬──────────┐
    │          │          │
CameraFragment PreviewFragment PagesFragment
    ↓          ↓          ↓
Capture →   Filter   →  Review  → PDF Created!
            Crop         Reorder
                        Add More
```

### MVVM Pattern
- **`ScannerViewModel`** - Shared state across fragments via `activityViewModels()`
- **LiveData** - Reactive UI updates
- **View Binding** - Type-safe view access
- **Coroutines** - Async operations (image processing, PDF generation)

### File Storage
```
filesDir/
├── scans/       # Captured images (SCAN_*.jpg)
├── processed/   # Filtered images (PROC_*.jpg)
└── pdfs/        # Generated PDFs (*.pdf)
```

All files in app-private storage - no storage permissions needed!

---

## 🎨 The Mascot Icons

Each icon tells a story:

| Icon | Personality | Feature |
|------|-------------|---------|
| 📷 Camera | Smiling camera with googly eyes | New Scan |
| ✨ Magic Wand | Sparkles everywhere! | Auto Scan |
| 🖼️ Gallery | Photo frames hugging | Import |
| 💕 Hugging Docs | Two PDFs with a heart | Merge |
| ✂️ Scissors | Winking scissors cutting | Split |
| 💨 Squeezed Doc | Dizzy from compression | Compress |
| 🎯 Capture Button | Playful shutter with camera | Take Photo |

**Design Features:**
- Thick black outlines (3px)
- Bright solid colors
- Googly eyes with white circles
- Happy expressions
- Rounded shapes everywhere

---

## 🚀 Quick Start

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- Gradle 8.2+

### Build & Run
```bash
# Clone the repository
git clone <your-repo-url>
cd pdf_scanner_app

# Build the project
./gradlew assembleDebug

# Install on connected device or emulator
./gradlew installDebug
```

**📖 For detailed setup instructions, see [SETUP_GUIDE.md](SETUP_GUIDE.md)**

---

## 📁 Project Structure

```
app/src/main/
├── java/com/pdfscanner/app/
│   ├── MainActivity.kt                    # Single activity entry point
│   ├── adapter/
│   │   ├── PagesAdapter.kt                # Page grid with drag-drop
│   │   ├── HistoryAdapter.kt              # Document history
│   │   └── RecentDocumentsAdapter.kt      # Home screen recent docs
│   ├── data/
│   │   └── DocumentHistory.kt             # PDF history storage
│   ├── ocr/
│   │   └── OcrProcessor.kt                # ML Kit OCR wrapper
│   ├── ui/
│   │   ├── HomeFragment.kt                # Landing screen
│   │   ├── CameraFragment.kt              # Camera capture
│   │   ├── PreviewFragment.kt             # Filter & crop
│   │   ├── PagesFragment.kt               # Page management
│   │   ├── HistoryFragment.kt             # Document history
│   │   └── SettingsFragment.kt            # App settings
│   ├── util/
│   │   ├── ImageProcessor.kt              # Document filters
│   │   ├── PdfUtils.kt                    # PDF operations
│   │   └── DocumentScanner.kt             # ML Kit integration
│   └── viewmodel/
│       └── ScannerViewModel.kt            # Shared state
│
└── res/
    ├── drawable/
    │   ├── ic_camera_mascot.xml           # Smiling camera icon
    │   ├── ic_auto_scan_mascot.xml        # Magic wand icon
    │   ├── ic_import_mascot.xml           # Gallery icon
    │   ├── ic_merge_mascot.xml            # Hugging docs icon
    │   ├── ic_split_mascot.xml            # Scissors icon
    │   ├── ic_compress_mascot.xml         # Squeezed doc icon
    │   ├── ic_capture_button_mascot.xml   # Capture button
    │   ├── illustration_empty_folder.xml  # Empty state
    │   ├── illustration_camera.xml        # Permission screen
    │   └── illustration_success.xml       # Success dialog
    ├── font/
    │   ├── nunito_regular.ttf
    │   ├── nunito_semibold.ttf
    │   ├── nunito_bold.ttf
    │   └── nunito_extrabold.ttf
    ├── layout/
    ├── navigation/nav_graph.xml           # Navigation flow
    └── values/themes_cartoon.xml          # Cartoon theme
```

---

## 🎨 Customization

### Theme Colors
Edit `res/values/themes_cartoon.xml`:

```xml
<item name="colorPrimary">#FF6B6B</item>      <!-- Coral Red -->
<item name="colorSecondary">#4ECDC4</item>    <!-- Turquoise -->
<item name="colorTertiary">#FFE66D</item>     <!-- Yellow -->
<item name="colorSurface">#FFF8F0</item>      <!-- Creamy Off-White -->
<item name="colorBackground">#FFFAF5</item>   <!-- Soft Beige -->
```

### Creating Your Own Mascot Icons
1. Use vector paths (Android doesn't support `<circle>` tags)
2. Add thick black outlines: `android:strokeWidth="2.5"`
3. Use solid fills from the color palette
4. Add googly eyes: white circles with black pupils
5. Include happy expressions: smiles, hearts, character

---

## 🎬 User Journey

1. **Open App** → See playful home screen with mascot icons
2. **Tap Camera** → Camera opens with sparkle capture button
3. **Capture Document** → Bounce animation confirms capture
4. **Apply Filter** → Choose Enhanced, B&W, or Original
5. **Crop Edges** → Fine-tune document boundaries
6. **Add More Pages** → Repeat for multi-page documents
7. **Review Pages** → Drag to reorder, tap to edit
8. **Name PDF** → Give it a friendly name
9. **Share** → Send via any app with one tap!

---

## 🚧 Roadmap

### ✅ Completed Phases

- [x] **Phase 1**: Basic scanning (capture, crop, PDF)
- [x] **Phase 2**: Document filters & enhancement  
- [x] **Phase 3**: Page reordering & batch scanning
- [x] **Phase 4**: OCR with ML Kit
- [x] **Phase 5**: Auto edge detection
- [x] **Phase 6**: Dark mode support
- [x] **Phase 7**: PDF merge, split, compress tools
- [x] **Phase 8**: Cartoon theme with mascot icons 🎨
- [x] **Phase 9**: Enhanced UI/UX polish ← **Current Release!** 🎉

### 🔜 Upcoming Features

#### Phase 10: Smart Document Features
- [ ] **Searchable PDFs** - Embed OCR text layer for search
- [ ] **PDF Import** - Import existing PDFs and add/edit pages
- [ ] **Signature Support** - Draw or import signatures
- [ ] **Annotations** - Add text, highlights, and stamps

#### Phase 11: Organization & Management
- [ ] **Folders & Tags** - Organize documents by category
- [ ] **Search Documents** - Find by name, date, or content
- [ ] **Favorites** - Quick access to important documents
- [ ] **Archive** - Move old documents out of main view
- [ ] **Document Templates** - ID cards, receipts, contracts

#### Phase 12: Cloud & Sync
- [ ] **Google Drive Integration** - Auto-backup to cloud
- [ ] **Dropbox Support** - Sync across devices
- [ ] **OneDrive Integration** - Microsoft ecosystem support
- [ ] **Share to Cloud** - Direct upload from app

#### Phase 13: Advanced Editing
- [ ] **Perspective Correction** - Auto-straighten skewed documents
- [ ] **Background Removal** - Clean document backgrounds
- [ ] **Text Recognition Editing** - Edit OCR'd text before saving
- [ ] **Page Numbering** - Auto-add page numbers to PDFs
- [ ] **Watermarks** - Add custom watermarks to documents

#### Phase 14: Export & Integration
- [ ] **Export Formats** - DOCX, PNG, JPEG, TIFF
- [ ] **Email Integration** - Send directly via email
- [ ] **Print Support** - Print to wireless printers
- [ ] **Fax Integration** - Send documents via fax service
- [ ] **QR Code Scanning** - Scan QR codes in documents

#### Phase 15: Accessibility & Localization
- [ ] **Voice Commands** - "Take photo", "Save PDF"
- [ ] **Screen Reader Support** - TalkBack optimization
- [ ] **Multi-language OCR** - Support 50+ languages
- [ ] **RTL Support** - Right-to-left language layouts
- [ ] **App Localization** - UI in multiple languages

#### Phase 16: Pro Features (Future)
- [ ] **Batch Processing** - Process multiple documents at once
- [ ] **Custom Themes** - Create your own color schemes
- [ ] **Widgets** - Home screen quick-scan widget
- [ ] **Shortcuts** - App shortcuts for common actions
- [ ] **Apple Watch / WearOS** - Quick capture from wrist

---

## 💡 Feature Ideas & Suggestions

Have an idea? We'd love to hear it! Open an issue with the `feature-request` label.

**Popular Requests:**
- 📁 Document categorization
- 🔐 Password-protected PDFs
- 📊 Analytics dashboard
- 🌙 AMOLED dark mode
- 📱 Tablet-optimized layout

---

## 🤝 Contributing

We'd love your help making this app even more delightful!

1. Fork the repository
2. Create your feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Commit with descriptive messages: `git commit -m 'Add googly eyes to merge icon'`
5. Push to your branch: `git push origin feature/amazing-feature`
6. Open a Pull Request

---

## 📄 License

*[Add your license here - MIT, Apache 2.0, etc.]*

---

## 💝 Acknowledgments

- **CanHub** - Amazing image cropper library
- **Google** - CameraX, ML Kit, Material Design
- **Vernon Adams** - Nunito font family
- **Playful Web Icons** - Design inspiration for mascot style

---

<div align="center">

**Made with ❤️ and lots of googly eyes 👀**

*Scan documents, spread joy!*

---

⭐ If you like this project, give it a star!

</div>
