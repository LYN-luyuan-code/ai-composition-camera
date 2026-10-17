---
name: "AI构图相机 · Native Camera"
description: "Full-screen black camera chrome, bare white controls, red selection and inline camera-backed filters."
colors:
  camera-black: "#000000"
  camera-white: "#FFFFFF"
  camera-red: "#FF3B3B"
  control-charcoal: "#242424"
  rail-white: "#FFFFFFAA"
  grid-white: "#FFFFFF55"
  ruler-gray: "#8D8D8D"
  preview-empty: "#171717"
  status-black: "#00000099"
  options-charcoal: "#121212E6"
  zoom-idle-black: "#00000044"
  zoom-active-black: "#000000AA"
  edge-black: "#000000F5"
  fade-middle-black: "#000000C9"
  dialog-red: "#C62828"
  dialog-red-night: "#FF6B6B"
typography:
  navigation:
    fontFamily: "sans"
    fontSize: "15sp"
  strength:
    fontFamily: "sans"
    fontSize: "14sp"
  label:
    fontFamily: "sans"
    fontSize: "13sp"
  compact:
    fontFamily: "sans"
    fontSize: "11sp"
rounded:
  filter-preview: "2dp"
  photo-thumbnail: "3dp"
  status: "8dp"
  options: "12dp"
  chevron: "18dp"
  zoom-ruler: "20dp"
  ripple-mask: "24dp"
spacing:
  "4": "4dp"
  "6": "6dp"
  "8": "8dp"
  "12": "12dp"
  "16": "16dp"
  "20": "20dp"
  "24": "24dp"
components:
  bare-icon:
    backgroundColor: "transparent"
    textColor: "{colors.camera-white}"
    padding: "12dp"
    size: "48dp"
  bare-zoom:
    backgroundColor: "transparent"
    textColor: "{colors.camera-white}"
    typography: "{typography.navigation}"
    size: "48dp"
  bare-zoom-selected:
    backgroundColor: "transparent"
    textColor: "{colors.camera-red}"
    typography: "{typography.navigation}"
    size: "48dp"
  mode:
    backgroundColor: "transparent"
    textColor: "{colors.camera-white}"
    typography: "{typography.navigation}"
    width: "72dp"
    height: "48dp"
  mode-selected:
    backgroundColor: "transparent"
    textColor: "{colors.camera-red}"
    typography: "{typography.navigation}"
    width: "72dp"
    height: "48dp"
  options-chevron:
    backgroundColor: "{colors.control-charcoal}"
    textColor: "{colors.camera-white}"
    rounded: "{rounded.chevron}"
    width: "36dp"
    height: "28dp"
  manual-shutter:
    backgroundColor: "transparent"
    textColor: "{colors.camera-white}"
    size: "80dp"
  filter-card:
    backgroundColor: "transparent"
    textColor: "{colors.camera-white}"
    typography: "{typography.label}"
    width: "76dp"
    height: "92dp"
  filter-strength:
    backgroundColor: "transparent"
    textColor: "{colors.camera-white}"
    typography: "{typography.strength}"
    height: "56dp"
  zoom-ruler:
    backgroundColor: "{colors.zoom-idle-black}"
    textColor: "{colors.camera-white}"
    typography: "{typography.compact}"
    rounded: "{rounded.zoom-ruler}"
    width: "40dp"
  camera-status:
    backgroundColor: "{colors.status-black}"
    textColor: "{colors.camera-white}"
    typography: "{typography.label}"
    rounded: "{rounded.status}"
    padding: "6dp 12dp"
---

# Design System: AI构图相机 · Native Camera

## Overview

**Creative North Star: "Native Camera"**

A familiar native camera puts the live image behind a spare black, white and red control system. Bare white icons and text establish the controls; red indicates the selected stop, active task or ready composition. The manual shutter is the largest white shape.

The user-supplied photo and filter screenshots pin this visual replacement. Their durable language is monochrome contrast, restrained red selection, a double-circle shutter and inline image previews. The app expresses its own rear-camera-only capabilities through recent-photo and AI controls. Auxiliary provider settings keep native neutral day/night form semantics with red theme accents.

**Key Characteristics:**

- Full-screen live image with local black edge fades.
- Bare white drawn controls and red selected text.
- Stable shutter with actual camera-backed inline filter previews.
- Fine reference geometry, bold translucent movement arrow and continuous zoom.

This replaces the previous visual system under the user's 2026-10-16 reference authority. The source is normative: `CameraOverlayView.kt`, `CameraFilterStrip.kt`, `ZoomGestureBar.kt`, `SoftTheme.kt` and day/night `themes.xml`; existing zoom and crop controllers retain their behavior. Frontmatter uses Android dp/sp. Sidecar specimens map one native unit to one CSS px for inspection only; Android ARGB is serialized as CSS RGBA hex.

## Colors

Black and white carry the camera; red is sparse state information.

### Primary

- **Camera Red** (`camera-red`): selected zoom/mode text, active flash, active AI label, strength marker, zoom fill/thumb and ready guidance.
- **Native Form Reds** (`dialog-red`, `dialog-red-night`): system Material day/night accent values in auxiliary settings.

### Neutral

- **Camera Black / Camera White** (`camera-black`, `camera-white`): window ground, controls, shutter, guides, filter labels and selected filter border.
- **Control Charcoal** (`control-charcoal`): inset down-chevron pill and zoom thumb center.
- **Rail White / Grid White** (`rail-white`, `grid-white`): range ticks and fine reference geometry.
- **Ruler Gray / Empty Preview** (`ruler-gray`, `preview-empty`): strength ticks and unpopulated filter preview region.
- **Local Backings** (`status-black`, `options-charcoal`, `zoom-idle-black`, `zoom-active-black`): transient copy, expanded options and the idle/active zoom ruler.
- **Edge Fade Blacks** (`edge-black`, `fade-middle-black`): top and bottom contrast gradients.

**The Bare Controls Rule.** Keep ordinary camera controls transparent at rest. Use the small filled chevron, transient status and narrow ruler only where the implementation establishes a local backing.

**The Red State Rule.** Use red to identify selected controls, ruler position and ready guidance. Keep filter-card selection a white outline.

Custom camera chrome does not invert in day/night. Auxiliary settings inherit native neutral Material forms rather than a custom camera card palette. Declared but unused theme fields are not additional active surface tokens. Sidecar tonal ramps are inspection aids, not shipped colors.

## Typography

**Body Font:** Android system `sans` from the application theme.

The compact native hierarchy uses normal platform metrics; no custom weight, tracking or line height is specified. Custom camera text buttons disable all-caps.

- **Navigation** (`navigation`): bare zoom stops and Photo/Filters row.
- **Strength** (`strength`): filter percentage above the strength ruler.
- **Label** (`label`): filter names, quality/grid options and transient camera status.
- **Compact** (`compact`): current zoom readout and AI control caption.

Provider settings retain native titles, labels, editable fields and actions; their explanatory note is (12sp). These platform defaults do not establish a new custom display or heading ramp.

**The Native Type Rule.** Use Android system sans and the implemented compact sp roles; no display typography layer is established.

## Layout

The preview and guide layer fill the entire viewport. Top chrome places flash at left, an inset down-chevron at center, and filter/settings at right. Each icon has a (48dp) hit target; the bar is (56dp) high and begins (12dp) below the top system inset. Expanded quality/grid options sit below it in a centered container capped at (320dp).

Portrait bottom controls are centered and capped at (480dp), with a (24dp) total horizontal allowance. The action row is (96dp) high and begins (128dp) above the safe bottom. Its shutter is (80dp), recent-photo target (48dp), and AI control (56dp × 64dp). Above it, Photo and Filters occupy (72dp × 48dp) each with a (16dp) gap; bare zoom stops occupy (48dp) each with (4dp) margins on both sides.

**The Stable Shutter Rule.** Opening or closing inline filters must keep the manual shutter in the same position and available for capture.

Filters replace the zoom row with a (148dp) inline region: (92dp) preview strip plus (56dp) strength ruler. The strip sits (16dp) above the action row; the Photo/Filters row moves above the strip. Portrait strip width is capped at (520dp). Cards scroll horizontally with (20dp) side padding and (12dp) gaps.

Landscape is width greater than height. A right-hand action layout area uses the smaller of (248dp) or 40% of viewport width. Filters occupy the remaining preview-side area, capped at (440dp), and the zoom gesture ruler is hidden while landscape filters are open. Tablet controls retain their physical dp sizes rather than scaling with the screen.

The zoom ruler has a (48dp) hit width around a (40dp) visual panel. Its measured height is `max(112dp, min(168dp, safe viewport height − 130dp))`. It sits (4dp) clear of the right inset, or left of the landscape action area, with vertical positioning clamped below top options and above lower controls. No opaque full-width panel frames the preview.

## Elevation & Depth

Custom controls are flat, without button elevation animators. A top fade extends to `min(18% of height, 136dp)` from edge black to transparent. The bottom fade extends over `min(46% of height, 380dp)`, progressing transparent → middle black at 40% → edge black. This is functional contrast behind controls, not decorative surface material.

Text buttons use a black legibility shadow (2dp radius, 1dp downward offset). Status has its own small backing; options and the zoom ruler have local dark fills. Thin guides and the bold arrow retain dark contrast under-strokes from their drawing implementation. Native provider dialogs retain platform depth and focus affordances.

**The Edge Contrast Rule.** Support bare white controls with local top/bottom black fades and small backings; preserve the center of the live image without an opaque scrim.

## Shapes

Bare icons are custom paths on a 24-unit drawable grid with (1.7-unit) rounded strokes. The inset chevron pill uses its own rounded shape inside the larger hit target. Radius roles apply narrowly: filter previews, recent-photo clipping, status, expanded options, chevron and zoom ruler. The (24dp) ripple-mask radius does not imply a visible button tile.

The shutter uses an outer circle of radius 10 and a filled inner circle of radius 8.8 on the 24-unit drawable grid; the outer stroke is 0.65 units. It fills an (80dp) icon view with zero padding. The reference grid uses fractions 0.382 and 0.618, (0.6dp) lines, (1dp) corner marks of length (20dp), and an inactive-composition red center cross with (11dp) arms.

## Components

### Bare controls and navigation

Flash, filter and settings remain white at rest. Active flash turns red. The chevron is a (36dp × 28dp) visible charcoal pill inside a (48dp) target, created with (6dp) horizontal and (10dp) vertical insets; it switches up/down with options visibility. Ordinary native camera ripples use translucent white at alpha 51/255 over transparent fill. Disabled/saving controls use alpha 0.45. Buttons provide native tap haptics.

Zoom stops are 1×, 1.5×, 2× and 3× software magnifications, selected in red with no filled chip. Photo and Filters are task tabs, not additional capture modes. AI sits to the shutter's right; its active label becomes red “结束”. The left recent-photo view displays this app's actual last saved image, inset (6dp), clipped with the thumbnail radius; a drawn gallery icon is the initial empty state.

### Inline filters

The installed catalog contains 22 options including Original: 20 application presets plus the two bundled cinema LUTs 电影60s and 电影80s. Application looks include the original five, custom Hasselblad/Leica-inspired looks and additional film, color and monochrome treatments. Manufacturer-inspired descriptions explicitly identify custom references rather than manufacturer-original LUTs. The cinema looks are bundled from Striped Purple / Nixua's Cinema Through The Ages under MIT, with attribution available in their information dialogs. Imported .cube files extend this catalog. Each (76dp × 92dp) card displays a pre-rendered thumbnail derived from actual ungraded camera pixels, with that option's full-strength LUT. The image region starts (3dp) inside the card and ends at (67dp) vertically; the label baseline is (86dp). Selection adds a (2dp) white border, without a colored fill. Before pixels arrive the image region uses the empty-preview color. Keyboard focus draws a separate red (2dp) outline around the whole card with (4dp) corners; a selected card keeps its white preview border at the same time. Focus changes invalidate the card drawing. Long labels over five characters show the first four plus an ellipsis; accessibility retains the full name and long-press explanation. Long-press opens that filter’s information dialog. Thumbnails render off the UI thread on demand: selected first, then visible cards, then immediate neighbors. A raw-frame request retries for up to eight seconds while the camera becomes ready; the established empty-preview region remains until pixels arrive.

The strip keeps the existing (148dp) layout of cards plus strength only. Category, current-filter information, .cube import and imported-LUT management live in Settings; no toolbar is added above the cards. Categories are 全部、色彩、胶片、单色、我的LUT, with Original retained in every category. Category selection persists. Import selects the saved look in 我的LUT; information for an imported look offers deletion, and deleting the active imported look returns to Original. Imported LUT descriptions identify SDR input and explain that Log/HDR transforms need matching input color space.

Live preview, thumbnails and saved images share the same quantized RGB LUT data and trilinear lookup/strength blend, replacing the old matrix-only path. The GPU preview uses the LUT atlas while thumbnails and save use its matching CPU lookup; capture reads the currently displayed filter/strength. This describes shared processing semantics, not pixel-identical sensor preview and JPEG claims. If real-time LUT processing fails, the camera returns to Original and shows transient feedback.

The strength ruler covers 0–100. Ticks are spaced in 2-point steps; major ticks every 10 points are (14dp) long, minor ticks (9dp), each (1dp) wide. A fixed central red marker is (24dp) long and (2dp) wide; a white number sits above it. Dragging moves the ticks relative to this center; native range accessibility supports set-progress and ±5-point actions. Keyboard focus adds a red (1.5dp) outline with (6dp) corners, inset (2dp) horizontally and (1dp) vertically. Left/Right change strength by 2 percentage points; Home/End set 0/100. Up/Down remain available for native focus navigation. Focus changes invalidate the range drawing. Original disables strength at alpha 0.45. Filter selection updates immediately. Photo or Android Back closes the inline strip without moving the shutter. Reveal fades in over (160ms) when system animators are enabled; otherwise it appears directly.

### Zoom gesture ruler and motion

The vertical zoom indicator uses white readout, translucent white log ticks at 1/2/4/8 within range, and red progress/thumb. Plus/minus are drawn (1.5dp) strokes. Idle fill/thumb are (2dp)/(4.5dp radius); active touch or automatic movement uses (3dp)/(6dp). A charcoal center is (1.5dp). Active panel backing is stronger than idle.

Dragging is relative to current log zoom, preventing a jump at touch-down. Up enlarges; plus/minus taps multiply/divide by 1.15. Native SeekBar semantics expose set-progress and forward/back multiplying/dividing by 1.1. Manual gestures interrupt composition. Keyboard Up/Down multiply/divide zoom by 1.1; plus or equals aliases Up, minus aliases Down. Home/End set minimum/maximum. A focused ruler draws a red (2dp) outline on the existing panel boundary, keeping its (20dp) corners. Focus changes invalidate its drawing; focus alone does not enlarge the thumb or strengthen the panel fill.

Automatic framing retains one continuous log-zoom trajectory with `s(t)=10t³−15t⁴+6t⁵`, duration in seconds `max(0.5, 1.05×abs(ln(goal/start)))`. It immediately applies the target when system animations are disabled. Gesture response is exponential (70/s); preset motion is critically damped (14/s). Automatic enlargement freezes on tracking loss/misalignment and yields to touch. Drawn subject/crop geometry compensates for zoom between tracking samples; free-crop proportions remain intact.

### Composition, crop and reference guides

The straight phone-movement arrow has rounded (8dp) shaft/head strokes at alpha 185/255, (14dp) axial head length, and an (11dp) dark shaft under-stroke at alpha 45/255. It is white while guiding and red when ready. The target ring retains a (20dp) radius, (1.8dp) foreground and (4dp) dark under-stroke; the central aim point is white. Search rotates two local 70° arcs over (1800ms), with a static fallback when animations are disabled. Focus lasts (550ms), capture feedback (100ms), and ordinary transient feedback defaults to (3000ms).

Free crops have independent width and height. AI requests now include the current physical image width/height and an explicit final `aspect_ratio = frameAspectRatio × crop_width / crop_height`. Horizontal, square and vertical outputs remain possible in any phone orientation; equal retained fractions preserve the input shape rather than automatically producing a square. The physical aspect field takes precedence over rounded or inconsistent fractional extents. The outline is white (1dp) over a dark (3dp) under-stroke; corner marks are (2dp), at most (12dp) long or one quarter of the shorter frame dimension. No outside crop scrim is added. Completed crop outlines remain after guidance ends and represent the matching centered, rotation-aware saved region. The former persistent filter/zoom caption is absent; selection is communicated through controls and the strength ruler.

### Status and settings

Transient white status uses a small rounded backing, two ellipsized lines and a polite accessibility live region; it disappears when empty. Automatic discovery contributes one short subject label/reason, without displaying a score system. Artistic ranking is subjective. The Settings entry contains AI composition setup, filter category, current-filter information, .cube import and imported-LUT management. These use native dialogs and the system document picker. Provider fields retain native AlertDialog, ScrollView, Spinner, single-line EditText, password Key input and CheckBox semantics, with neutral day/night forms and red accent; no custom input-card system is established.

## Do's and Don'ts

### Do:

- **Do** preserve full-screen preview, system insets and at least 48dp interactive targets.
- **Do** use red for active camera text and white for the selected filter border.
- **Do** populate filter cards and the recent-photo control with actual camera or saved-image pixels.
- **Do** keep the shutter fixed when showing filters and close the strip through Photo or Android Back.
- **Do** honor disabled system animation for filter reveal, local search and automatic framing.

- **Do** keep filter category, information, import and management in Settings; reserve the viewfinder strip for cards and strength.

### Don't:

- **Don't** bring filled colored tiles or a modal filter sheet into this camera surface.
- **Don't** claim custom manufacturer-inspired looks are manufacturer-original LUTs, copy reference logos, or add beauty/video/front-camera controls or unsupported optics.
- **Don't** replace live filter previews with sample photographs.
- **Don't** present subjective AI ranking as an objective image-quality score.
