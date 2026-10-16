---
name: "街拍相机 · Soft Android"
description: "Full-screen native camera with quiet mint guidance and compact floating controls."
colors:
  dialog-accent: "#2E6951"
  dialog-accent-night: "#A9D5BD"
  camera-mint: "#C3EAD2"
  dialog-selected: "#DDECE1"
  dialog-selected-night: "#3C5649"
  camera-selected: "#345C47EE"
  dialog-surface: "#F1F4F1"
  dialog-surface-night: "#202624"
  dialog-raised: "#FDFEFC"
  dialog-raised-night: "#303835"
  dialog-ink: "#233C31"
  dialog-ink-night: "#F0F4F1"
  dialog-muted: "#536A5D"
  dialog-muted-night: "#BBC8C1"
  camera-backing: "#202523B3"
  camera-ink: "#FFFFFF"
  camera-muted: "#E2E7E4"
  camera-rail: "#FFFFFFAA"
typography:
  title:
    fontFamily: "sans"
    fontSize: "20sp"
  body:
    fontFamily: "sans"
    fontSize: "14sp"
  label:
    fontFamily: "sans"
    fontSize: "13sp"
  caption:
    fontFamily: "sans"
    fontSize: "12sp"
  zoom-readout:
    fontFamily: "sans"
    fontSize: "11sp"
rounded:
  message: "12dp"
  control: "16dp"
  sheet: "24dp"
  zoom-ruler: "20dp"
spacing:
  4: "4dp"
  8: "8dp"
  10: "10dp"
  12: "12dp"
  16: "16dp"
  20: "20dp"
  24: "24dp"
components:
  zoom-ruler:
    backgroundColor: "{colors.camera-backing}"
    textColor: "{colors.camera-ink}"
    typography: "{typography.zoom-readout}"
    rounded: "{rounded.zoom-ruler}"
    width: "40dp"
  camera-control:
    backgroundColor: "{colors.camera-backing}"
    textColor: "{colors.camera-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.control}"
    padding: "4dp"
    height: "48dp"
  camera-zoom-selected:
    backgroundColor: "{colors.camera-selected}"
    textColor: "{colors.camera-mint}"
    typography: "{typography.caption}"
    rounded: "{rounded.control}"
    size: "48dp"
  camera-action:
    backgroundColor: "{colors.camera-backing}"
    textColor: "{colors.camera-ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.control}"
    size: "64dp"
  camera-shutter:
    backgroundColor: "transparent"
    textColor: "{colors.camera-ink}"
    padding: "10dp"
    size: "80dp"
  camera-status:
    backgroundColor: "{colors.camera-backing}"
    textColor: "{colors.camera-muted}"
    typography: "{typography.label}"
    rounded: "{rounded.message}"
    padding: "4dp 10dp"
  filter-preset:
    backgroundColor: "{colors.dialog-raised}"
    textColor: "{colors.dialog-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "6dp 12dp"
    width: "76dp"
    height: "52dp"
  filter-preset-selected:
    backgroundColor: "{colors.dialog-selected}"
    textColor: "{colors.dialog-ink}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "6dp 12dp"
    width: "76dp"
    height: "52dp"
  filter-done:
    backgroundColor: "{colors.dialog-selected}"
    textColor: "{colors.dialog-accent}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    height: "48dp"
  filter-sheet:
    backgroundColor: "{colors.dialog-surface}"
    rounded: "{rounded.sheet}"
    padding: "20dp 24dp 24dp"
---

# Design System: 街拍相机 · Soft Android

## Overview

**Creative North Star: "Soft Android"**

Soft Android treats the live image as the main material. Small rounded controls float on charcoal backings; white camera controls and mint guidance remain consistent in day and night. Auxiliary dialogs use quiet neutral surfaces that follow the system theme.

The visual character is gentle, compact and familiar to phone-camera users. Native Android Views, system sans typography, geometric drawn icons and restrained depth carry the interface. AI guidance assists the photographer while the manual shutter remains the capture control.

**Key Characteristics:**

- Full-screen image with compact edge controls.
- Mint selection, white capture affordance and local charcoal contrast.
- Rounded native controls and neutral day/night dialogs.
- Local guidance motion with system animation settings respected.

This is a scan of the implemented native Android Views. Sources: `SoftTheme.kt`, `CameraOverlayView.kt`, `FilterSheet.kt`, `AiSettingsDialog.kt`, day/night `themes.xml`, `ZoomGestureBar.kt`, `ZoomMotion.kt`, `SmoothZoomController.kt` and `MainActivity.kt`. Frontmatter dimensions retain native dp/sp; the sidecar browser specimens map one dp/sp unit to one CSS px only for inspection. Android ARGB literals are serialized as CSS RGBA hex without changing their alpha.

## Colors

A muted green accent sits among softly tinted neutrals; live-view chrome uses a dedicated contrast palette.

### Primary

- **Quiet Forest** (`dialog-accent`): native day-theme accent and filter completion text.
- **Pale Sage** (`dialog-accent-night`): corresponding native night-theme accent.
- **Camera Mint** (`camera-mint`): selected zoom text, local search arcs and composition guidance.
- **Soft Selection** (`dialog-selected`, `dialog-selected-night`): selected presets and completion control surfaces.
- **Deep Mint Selection** (`camera-selected`): nearly opaque active zoom and composition backing.

### Neutral

- **Soft Paper / Deep Green Charcoal** (`dialog-surface`, `dialog-surface-night`): auxiliary surface and window backgrounds.
- **Raised Paper / Raised Charcoal** (`dialog-raised`, `dialog-raised-night`): idle filter controls.
- **Forest Ink / Light Ink** (`dialog-ink`, `dialog-ink-night`): main dialog text.
- **Muted Forest / Muted Sage** (`dialog-muted`, `dialog-muted-night`): supporting copy.
- **Local Charcoal** (`camera-backing`): individual controls, transient status and compact caption; its alpha is the implemented B3 value.
- **White / Soft White** (`camera-ink`, `camera-muted`): shutter, camera labels and supporting camera text.
- **Translucent White Rail** (`camera-rail`): idle zoom track and log-spaced ticks over local charcoal.

**The Local Contrast Rule.** Camera labels use their own charcoal backing; contrast support belongs to the control, rather than an opaque panel over the image.

The frontmatter is the normative palette. Day/night switching applies to auxiliary dialogs; it does not invert live-view chrome. Native ripple uses the current accent at Android alpha 35/255. Tonal ramps in the sidecar are generated inspection aids, not additional shipped colors.

## Typography

**Body Font:** Android system `sans`, supplied by the application theme.

**Character:** Familiar native typography with a compact ramp and mixed-case controls. Text scales through Android sp; custom camera buttons disable all-caps. The implementation does not specify custom font weight, tracking or line height.

### Hierarchy

- **Title** (`title`): filter-sheet heading.
- **Body** (`body`): intensity description and completion action.
- **Label** (`label`): filter presets and camera transient status.
- **Caption** (`caption`): camera buttons, zoom stops, zoom/filter caption and provider explanatory note.
- **Zoom Readout** (`zoom-readout`): compact currentzoom label drawn on the gesture ruler with Android scaled density.

Settings titles, field labels, spinner, text fields, checkbox and dialog actions retain the platform Material theme's native typography. Do not infer exact sizes for these from the custom filter ramp.

**The Native Type Rule.** Use system sans for native controls and supporting copy; the camera has no display typography layer.

## Layout

**The Image First Rule.** Keep the viewfinder full-screen and let controls float at its edges. Auxiliary sheets and settings dialogs open only for their task.

The camera preview and guide layer occupy the complete measured viewport. In portrait, top controls sit inside the side insets plus a (16dp) gutter, with a (4dp) top inset offset. The bottom controls are centered, limited to (520dp), and inset (8dp) above the safe bottom. Zoom stops are (48dp) squares with (8dp) gaps. The action row uses a central (80dp) shutter view, flanked by (64dp) filter and composition controls with (12dp) gaps; AI is immediately right of the shutter.

Landscape is detected by width greater than height. Bottom controls move to a transparent right-side layout area capped at (264dp) or 45% of width, whichever is smaller; the preview remains full-screen. This is a layout allocation, not a visible panel.

The zoom ruler has a (48dp) touch width around a (40dp) translucent rounded panel. Height is `max(120dp, min(204dp, safe viewport height − 100dp))`. It sits near the right edge, (8dp) clear of the right inset; in landscape it moves left of the action layout area. Its vertical position is centered then clamped between the top controls plus (8dp) and bottom controls minus (12dp), using available viewport space. It adds no full-width panel.

The filter sheet uses padding (24dp) horizontally, (20dp) at top and (24dp) at bottom. Presets scroll horizontally. Settings use a native scrolling dialog with (24dp) side padding, (8dp) top and (16dp) bottom; labels are separated by native local padding. There is no web breakpoint system.

## Elevation & Depth

Custom camera controls are flat: button state animators are removed and the shutter has zero elevation. Color and rounded silhouettes establish separation; the live image receives no decorative blur or global chrome scrim. Filter-sheet presentation uses a native dialog dim amount of 0.2. Settings retain native dialog elevation and field affordances rather than custom shadow tokens.

Camera status text carries a black shadow (3dp radius, 1dp downward offset). Guidance is outlined by a darker path under the mint stroke so it remains legible on changing scenes. These are contrast aids, not a general card-shadow vocabulary.

**The Quiet Depth Rule.** Use tonal separation and native ripple feedback for controls; reserve dark strokes and text shadow for legibility over live imagery.

## Shapes

Controls use the gently rounded `control` radius; compact camera messages use `message`; the filter sheet uses `sheet`. No general card component is implemented. The shutter is circular, drawn as a thin outer ring and solid central disk. Icons are custom stroked geometric drawables at (24dp), with rounded joins and caps. The composition ring has a (20dp) radius; the search arc uses (24dp). Focus uses a (40dp) outlined square with (6dp) corners.

## Components

### Camera controls

Compact, locally backed native buttons. Flash, quality, settings and zoom preserve at least (48dp) bounds. Text buttons use the camera caption role and (4dp) padding; settings use a drawn icon with (12dp) padding. Zoom selection changes both backing and text to the camera selected/mint pair. Composition selection changes its backing and label to “结束”; it does not reuse zoom's mint text change. Disabled and saving controls use alpha 0.45. Native ripples and tap haptics provide feedback; no custom hover design exists on Android.

### Zoom gesture ruler

A narrow native range control makes continuous zoom visible and operable. The current zoom uses `zoom-readout`; plus/minus are drawn (1.5dp) strokes, not text glyphs. The log-spaced rail marks 1×, 2×, 4× and 8× when those stops are inside the supported range. The idle rail is (2dp) with a (4.5dp) mint thumb; touch or automatic zoom uses a (3dp) active fill and (6dp) thumb. The thumb retains a small charcoal center (1.5dp). The panel uses the existing camera backing and the `zoom-ruler` radius.

Dragging is relative to the current log-zoom fraction, so touching the ruler does not jump to an absolute value. Up enlarges, down reduces; plus/minus taps multiply or divide by 1.15. Native SeekBar range semantics expose the current zoom, set-progress, and forward/back actions multiplying or dividing by 1.1. Gesture takeover interrupts automatic composition. The 48dp hit region remains distinct from the narrower visible panel.

### Zoom motion

Confirmed framing proceeds as one continuous trajectory in log zoom. Its quintic ease is `s(t) = 10t³ − 15t⁴ + 6t⁵`, with duration in seconds `max(0.5, 1.05 × abs(ln(goal/start)))`. Endpoints settle without an overshoot. This automatic trajectory immediately applies its target when Android system animations are disabled. Direct gestures use an exponential response coefficient of (70/s); preset-button transitions use the critically damped response coefficient (14/s). These separate motion modes preserve direct touch response while making automatic framing visibly gradual.

Automatic enlargement freezes on tracking loss or misalignment and can resume after alignment; user gestures take control immediately. Subject and crop geometry are remapped for display zoom between tracking samples, keeping the arrow and outline attached to their intended image positions. Free-crop proportions remain intact throughout enlargement.

### Shutter

A familiar white ring and disk inside an (80dp) hit view, with (10dp) image padding. It remains independent of AI progress. Drawing fits the custom 24-unit icon geometry into the image bounds; the view itself has transparent background.

### Status and caption

Supporting labels use a compact charcoal backing with the message radius. Status allows two ellipsized lines, uses a polite accessibility live region and disappears when empty. Caption uses one ellipsized line and a (24dp) height. They expand only around their content. Automatic discovery contributes one short subject label and reason to the existing status area; it does not add a scoring screen. Artistic interest, story, visual geometry and clarity are subjective selection signals, not objective photo-quality grades. The caption adds “自由裁切” when either retained photo dimension is below 0.999 of the viewfinder.

### Filter sheet and presets

A rounded native bottom dialog. Preset buttons are (76dp × 52dp), use the label role, and change raised fill to soft selection fill without changing text color. The intensity SeekBar is native, (48dp) tall, and disabled for the original preset. The full-width completion button uses the body role and selected fill. Selection applies immediately; completion dismisses the sheet.

### Settings and fields

Native Material AlertDialog, ScrollView, Spinner, EditText and CheckBox. Fields are single-line and retain native focus/underline feedback; API Key uses password input. The custom explanatory note uses the caption role. No additional input radius, border or card token is established by this implementation.

### Composition guidance

A small central aim mark, target ring and straight, thick, slightly transparent arrow communicate phone movement. The shaft and chevron use rounded (8dp) strokes at alpha (185/255); the head extends (14dp) along the movement direction. The shaft has a dark green contrast under-stroke (11dp) at alpha (45/255). Its path is a direct line, with no bend. The arrow uses camera mint during guidance and the implemented ready mint when aligned. The target ring retains its (1.8dp) stroke over a (4dp) contrast stroke. Search rotates two local (70°) arcs over (1800ms) only when Android animators are enabled; otherwise it draws a static arc. Focus lasts (550ms), capture feedback (100ms), and a transient message normally lasts (3000ms). These observed timings take precedence over provisional surface-brief timing language.

### Free-crop outline

Independent retained width and height support any aspect ratio; there is no fixed-ratio frame family. A thin mint rectangle (1dp) over a dark green contrast stroke (3dp) marks the photo area. Mint corner marks use (2dp) strokes and a length capped at (12dp), reduced to one quarter of the shorter frame dimension for small crops. The image inside and outside the frame stays visible without a scrim.

Active guidance may show its proposed crop frame; otherwise the completed retained crop is centered in the full-screen viewfinder. That completed outline remains after guidance ends. The saved JPEG uses the corresponding centered region with rotation-aware axis mapping, so the visible boundary describes the output rather than a decorative composition overlay.

## Do's and Don'ts

### Do:

- **Do** use the camera palette for live-view controls in both system themes.
- **Do** preserve the familiar shutter silhouette and keep AI composition immediately beside it.
- **Do** keep interactive camera controls at least 48dp and respect system insets.
- **Do** use the day/night dialog palette for auxiliary tasks.
- **Do** retain local search animation and its static fallback when system animation is disabled.

### Don't:

- **Don't** put the live viewfinder inside a card or add opaque top/bottom control panels.
- **Don't** replace the manual shutter with an AI-triggered capture action.
- **Don't** introduce a display face, decorative typography or hardware imitation into this Soft world.
- **Don't** treat OPPO functional composition inspiration as authorization for a pixel copy.
