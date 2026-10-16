# Camera surface

Mode: Operate. Platform: Android Views. Execution: code-led, user-supplied native reference.

## Authority and thesis
The user's 2026-10-16 request “ui做成这样的” pins the two supplied screenshots. They replace the earlier Soft/mint camera chrome. Reference files are `.impeccable/references/camera-photo.jpg` and `camera-filters.jpg`, copied without alteration from the user's attachments. These are functional and layout references, not shipping image assets or authorization for Leica branding, extra camera modes or unavailable optics.

## Own world
Black camera ground, bare white drawn icons, restrained red selected text and ruler marker. Native system sans. No filled rounded tiles behind every control. Auxiliary settings retain native form semantics and neutral day/night surfaces with red accents.

## First viewport
Full-screen live preview stays behind the chrome. Flash at upper left, a small centered down-chevron for camera options, filter and settings controls at upper right. Fine golden-section grid and corner marks, with a small red center cross. At the bottom: bare 1 / 1.5 / 2 / 3 zoom stops, a photo/filter switch, large double-circle white manual shutter, recent saved-photo thumbnail to its left and AI framing control to its right. The AI control replaces the reference camera-switch action because this app uses the main rear camera.

## Story and interactions
One tap on Filters opens an inline horizontal strip of previews of the actual current camera image, with the existing five filters and a tick ruler for strength. Manual shutter stays in place and remains available. Photo tab or Android Back closes the strip. Down-chevron reveals quality and grid options; settings opens existing supplier/key configuration. Recent-photo control opens this app's last saved image. Continuous quintic zoom, direct gesture takeover, auto subject discovery, free-aspect crop, straight thick semi-transparent guide arrow and local non-obscuring search animation remain functional. The narrow zoom gesture indicator joins the white/red palette and never sits over the filter controls.

## Form and adaptation
Seed key `1728a8b4` was generated for this replacement. The user's pinned reference overrides the assigned visual alternatives, including their topology; native code-led implementation follows the supplied camera screenshots directly. Portrait is the reference form; landscape uses a compact right-hand control rail, tablet uses physically sized controls. Every interactive target is at least 48dp. UI spacing respects system insets and font scaling; system animation disable is honored.

## Acceptance and truth
No opaque viewfinder scrim. Main rear camera only, photo only, manual shutter. Existing encrypted provider keys and crop geometry unchanged. No invented film/Leica presets, beauty modes or optical zoom labels. Camera-backed inline filter cards must use real preview pixels, not sample photos. Native phone/tablet, landscape, large font, filter selection/strength/Back and photo saving are checked in one bounded batch and one confirmation if needed. AI model efficacy and physical-device optics are not inferred from emulator scenes.
