---
phase: 02-design-system
plan: 01
subsystem: ui
tags: [android, material3, nunito, cartoon-theme, dark-mode, typography, spacing]

# Dependency graph
requires:
  - phase: 01-stability
    provides: Stable fragment lifecycle and context-safe coroutines — required for UI changes not to break runtime

provides:
  - Cartoon theme active app-wide via AndroidManifest.xml
  - 8dp spacing grid in dimens.xml (spacing_xxs through spacing_xxl + semantic tokens + corner radii)
  - Dark-mode-safe TextAppearance styles in themes_cartoon.xml (colorOnSurface/colorOnSurfaceVariant)
  - Nunito font rendered via textAppearance on all primary fragment and item layouts

affects:
  - 02-design-system (remaining plans build on this theme/dimens foundation)
  - 03-performance (layout inflation costs now baseline-measured after this change)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "8dp grid via @dimen/spacing_* references instead of hardcoded dp in layouts"
    - "TextAppearance.Cartoon.* styles for all text sizing (never android:textSize on TextViews)"
    - "?attr/colorOnSurface and ?attr/colorOnSurfaceVariant for dark-mode-safe text colors in theme styles"

key-files:
  created:
    - app/src/main/res/values/dimens.xml
  modified:
    - app/src/main/AndroidManifest.xml
    - app/src/main/res/values/themes_cartoon.xml
    - app/src/main/res/layout/fragment_settings.xml
    - app/src/main/res/layout/fragment_home.xml
    - app/src/main/res/layout/fragment_history.xml
    - app/src/main/res/layout/fragment_pages.xml
    - app/src/main/res/layout/fragment_preview.xml
    - app/src/main/res/layout/fragment_camera.xml
    - app/src/main/res/layout/item_page.xml
    - app/src/main/res/layout/item_document.xml
    - app/src/main/res/layout/item_recent_document.xml

key-decisions:
  - "Use ?attr/colorOnSurface (not @color/cartoon_text_primary) in TextAppearance styles — theme attribute is dark-mode aware, direct color reference is light-mode only"
  - "Use ?attr/colorOnSurfaceVariant (not @color/cartoon_text_secondary) for secondary text in BodyMedium and BodySmall — enables Material3 dark theme to provide correct contrast automatically"
  - "Keep android:textSize on MaterialButton elements — button textSize is a widget style concern, not a typography system violation; only TextView textSize is replaced"
  - "Preserve explicit fontFamily attributes alongside textAppearance for special cases (app name in fragment_home has shadow + custom letterSpacing that must remain)"

patterns-established:
  - "textAppearance-first: All TextViews use android:textAppearance from TextAppearance.Cartoon.* family, never android:textSize directly"
  - "dimens-first: All layout margins/paddings map to @dimen/spacing_* tokens for 4/8/12/16/24/32/40dp values"

requirements-completed: [DSYS-01, DSYS-02]

# Metrics
duration: 6min
completed: 2026-02-28
---

# Phase 2 Plan 1: Design System Foundation Summary

**Cartoon theme activated app-wide with Nunito font via TextAppearance.Cartoon.* styles, 8dp spacing grid in dimens.xml, and dark-mode-safe textColors via ?attr/colorOnSurface in all TextAppearance definitions**

## Performance

- **Duration:** 6 min
- **Started:** 2026-03-01T03:55:19Z
- **Completed:** 2026-03-01T04:01:19Z
- **Tasks:** 2
- **Files modified:** 12 (1 created, 11 modified)

## Accomplishments

- AndroidManifest.xml switched to Theme.PDFScanner.Cartoon — Nunito font now active for every Activity and Fragment
- dimens.xml created with complete 8dp grid (spacing_xxs=4dp through spacing_xxl=40dp), semantic tokens (screen_horizontal_padding, card_padding, card_margin, bottom_nav_height), and corner radius tokens (corner_radius_small through corner_radius_pill)
- All 7 TextAppearance headline/title/body styles fixed: @color/cartoon_text_primary replaced with ?attr/colorOnSurface, @color/cartoon_text_secondary with ?attr/colorOnSurfaceVariant — dark mode text now readable
- All 9 primary layouts updated: TextViews use android:textAppearance from Cartoon type scale, hardcoded dp margins/paddings replaced with @dimen/ token references

## Task Commits

Each task was committed atomically:

1. **Task 1: Switch manifest theme + create dimens.xml + fix TextAppearance dark mode colors** - `033868f` (feat)
2. **Task 2: Apply textAppearance and dimens to primary fragment and item layouts** - `f3431ac` (feat)

**Plan metadata:** (docs commit — see below)

## Files Created/Modified

- `app/src/main/AndroidManifest.xml` - Theme switched to Theme.PDFScanner.Cartoon on application element
- `app/src/main/res/values/dimens.xml` - NEW: 8dp grid spacing + semantic tokens + corner radii
- `app/src/main/res/values/themes_cartoon.xml` - 9 TextAppearance styles updated with dark-mode-safe color attrs
- `app/src/main/res/layout/fragment_settings.xml` - All textSize eliminated (TitleSmall for headers, TitleMedium for rows, BodySmall for secondary); dp replaced with @dimen/
- `app/src/main/res/layout/fragment_home.xml` - HeadlineMedium for app name, TitleLarge for section headers, LabelLarge for action cards, BodySmall for subtitles; @dimen/ throughout
- `app/src/main/res/layout/fragment_history.xml` - BodySmall for help text, Cartoon TextAppearance on empty state; @dimen/ tokens
- `app/src/main/res/layout/fragment_pages.xml` - BodySmall for hint text; @dimen/ for button bar padding and margins
- `app/src/main/res/layout/fragment_preview.xml` - @dimen/ for card margin, filter bar padding, button bar padding
- `app/src/main/res/layout/fragment_camera.xml` - LabelLarge for batch count; @dimen/ for card/overlay margins
- `app/src/main/res/layout/item_page.xml` - BodySmall for selection/page number badges; @dimen/ for badge margins
- `app/src/main/res/layout/item_document.xml` - TitleMedium + colorOnSurface for document name, BodySmall for details; @dimen/ for padding/margins
- `app/src/main/res/layout/item_recent_document.xml` - TitleSmall for doc name, BodySmall for date, LabelSmall for pages; @dimen/ for all spacing

## Decisions Made

- Used ?attr/colorOnSurface (not @color/cartoon_text_primary) in TextAppearance styles — theme attribute is dark-mode aware, direct color reference is light-mode only
- Used ?attr/colorOnSurfaceVariant (not @color/cartoon_text_secondary) for secondary text in BodyMedium and BodySmall — enables Material3 dark theme to provide correct contrast automatically
- Kept android:textSize on MaterialButton elements — button textSize is a widget style concern, not a typography system violation; only TextView textSize is replaced
- Preserved explicit fontFamily attributes alongside textAppearance for special cases (app name in fragment_home has shadow + custom letterSpacing that must remain)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Design system foundation is complete: Nunito renders everywhere, spacing is grid-anchored, dark mode body text is safe
- Plans 02-05 can build on this foundation: custom empty states, animations, PDF preview polish, bottom nav styling
- Remaining textSize on MaterialButton elements (button text in fragment_pages and fragment_preview) is intentional — button styling is a separate concern from the TextAppearance system

---
*Phase: 02-design-system*
*Completed: 2026-02-28*
