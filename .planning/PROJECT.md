# PDF Scanner — Android App Polish & Quality Pass

## What This Is

A feature-rich Android document scanner app built with Kotlin, CameraX, and Material Design 3. It has a distinctive playful cartoon/mascot theme with googly-eyed icons, Nunito fonts, and bounce animations. The app is functionally complete through Phase 10 but needs a systematic polish pass to reach portfolio and Play Store quality.

## Core Value

Every feature that exists must work flawlessly, feel delightful, and be verified — no rough edges, no untested flows.

## Requirements

### Validated

- ✓ Camera scanning with CameraX — live preview, auto capture — existing
- ✓ ML Kit auto document edge detection — existing
- ✓ Document filters (Original, Magic, Enhanced, Sharpen, B&W) — existing
- ✓ Smart cropping with CanHub Image Cropper — existing
- ✓ Batch mode (multiple pages in one session) — existing
- ✓ Import images from gallery — existing
- ✓ Import existing PDFs and extract pages — existing
- ✓ Multi-page PDF generation with custom naming — existing
- ✓ Drag-and-drop page reordering — existing
- ✓ Multi-select pages (long-press) — existing
- ✓ OCR text recognition (ML Kit, offline) with copy to clipboard — existing
- ✓ PDF merge, split, compress tools — existing
- ✓ PDF Editor with annotations (text, shapes, stamps, highlights, drawing) — existing
- ✓ Digital signatures (create, save, reuse) — existing
- ✓ Undo/redo in PDF editor — existing
- ✓ Document history with thumbnails — existing
- ✓ Share via any app (FileProvider) — existing
- ✓ PDF rename — existing
- ✓ Dark mode support — existing
- ✓ Cartoon mascot theme with 30+ custom icons — existing
- ✓ Bounce animations and sparkle effects — existing
- ✓ Sound effects (SoundManager) — existing
- ✓ Nunito custom fonts (4 weights) — existing

### Active

- [ ] All existing features pass manual QA — no crashes, broken flows, or wrong behavior
- [ ] All existing features have automated test coverage (unit + instrumentation)
- [ ] UI consistency audit — spacing, typography, colors, icon sizing consistent across all screens
- [ ] UX flow audit — reduce unnecessary taps, improve feedback, fix confusing interactions
- [ ] Performance audit — no jank, fast PDF rendering, responsive camera
- [ ] Edge case handling — empty states, error states, large files, permission denials
- [ ] Accessibility baseline — content descriptions, touch targets, contrast ratios

### Out of Scope

- Phase 11+ features (searchable PDFs, cloud sync, Google Drive) — next milestone
- New export formats (DOCX, TIFF) — future milestone
- WearOS / widgets — future milestone
- In-app purchases / Pro tier — not in scope

## Context

- **Tech stack**: Kotlin, CameraX 1.3.1, CanHub Image Cropper 4.5.0, ML Kit Text Recognition + Document Scanner, Navigation Component 2.7.x, Material Design 3
- **Architecture**: Single Activity + Navigation Component + MVVM (ScannerViewModel shared via activityViewModels), LiveData, View Binding, Coroutines
- **File storage**: App-private storage in filesDir (scans/, processed/, pdfs/) — no storage permissions needed
- **Min SDK**: 24 (Android 7.0), Target SDK: 34
- **Current state**: Phase 10 complete — fully featured but unpolished; no existing test suite
- **Design system**: Cartoon/mascot theme, coral red (#FF6B6B) / turquoise (#4ECDC4) / yellow (#FFE66D) palette, Nunito font, 28dp corner radius, thick black outlines on icons

## Constraints

- **No new features**: This pass is strictly polish + testing — no Phase 11+ work
- **Android only**: No cross-platform considerations
- **Existing architecture**: Work within MVVM + Navigation Component, don't restructure
- **Play Store target**: End result must meet Play Store quality bar

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Polish-only scope | Get existing features rock-solid before expanding | — Pending |
| Both unit + instrumentation tests | Unit for logic, instrumentation for UI flows | — Pending |
| Work within existing architecture | App structure is sound, don't risk breaking things | — Pending |

---
*Last updated: 2026-02-28 after initialization*
