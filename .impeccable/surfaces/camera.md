# Camera surface

Mode: Operate. Platform: Android Views. Execution: code-led.

## Direction contract

### Brief and authority
The user explicitly pins Soft design and asks to implement this scope now, with details to follow. That instruction takes precedence over a new aesthetic-choice interview. Existing black-and-gold chrome is replaced.

### Direction
Seed 2906b7bf assigned candidate 7. Grounded candidates considered: soft stationery, muted photo-book paper, pale architectural signage, fabric swatches, rounded print labels, quiet transit wayfinding, and smooth ceramic surfaces. Candidate 7 is used within the user's Soft constraint: tactile, low-shadow neutral surfaces, gentle mint selection, system type, no imitation of camera hardware.

### First viewport
A full-screen live viewfinder under all chrome, with no card frame or separate surface. Compact translucent flash/quality/settings controls float at the top; local dark backings keep small labels readable against bright scenes; a normal white shutter and quiet zoom strip float at the bottom. This follows the user correction “相机界面全屏幕，不要遮住 / UI正常一点”. Filters sit left of the shutter; AI composition sits immediately right. No video, front-camera toggle, basic/AI switch or stack of pills over the image.

### Signature interaction
A straight, thick semi-transparent arrow translates the subject-position error into the direction the user moves the phone; a small target ring approaches the central aim dot as alignment improves. Search motion stays local to the chosen point and composition control. No opaque viewfinder scrim.

### Visual and motion grammar
Camera chrome uses white text on small translucent charcoal controls in both day and night, with mint active states and no full-width opaque panel. Dialogs use Soft neutral day/night palettes. OPPO Find X10 LUMO 灵感构图 is the functional reference (official page https://www.oppo.com/cn/smartphones/series-find-x/find-x10/); the ring/phone-motion arrow is our implementation of the user brief, not a claimed pixel copy. 48dp minimum controls, native accessibility semantics, system insets. Subtle 180–240ms feedback; ongoing searching respects disabled system animation.

### Acceptance
Photo only; default rear camera held at native 1x; software crop and filters reflected in saved output; existing encrypted provider keys preserved. Portrait, landscape, font scaling, dark mode, loading, guidance, failure and disabled states checked on Android emulator. API performance and physical-device optics are not claimed from emulator evidence.

### Free-crop extension
The user supplied a photography-analysis brief and explicitly removed aspect-ratio constraints. AI now chooses independent retained width/height; thin crop outlines and corners mark the photo area without a scrim. Completed free crops persist visibly after ending guidance; the saved JPEG uses the same centered region, with rotation-aware axis mapping. No new scoring screens or analysis-mode navigation are added.

User steering: arrow is straight and bold with slight transparency: 8dp shaft/head strokes, 185/255 alpha, 14dp head, rounded caps; 11dp low-alpha contrast under-stroke.

### Automatic discovery and smooth zoom extension
User steering: automatically discover the highest-priority artistic subject, including light/shadow/reflection/geometry/narrative; increase zoom easing; continuously enlarge after confirming the free crop; add a gesture indicator. Native right-edge 48dp touch ruler shows a compact plus/minus, current zoom and log-spaced ticks, supports direct dragging/accessibility, and yields to other camera gestures. Automatic framing uses a single continuous quintic log-zoom trajectory, interruptible and paused on tracking loss; preserves free-crop aspect and manual shutter. Composition point rendering compensates for the display zoom between tracking samples. No new navigation or full-width panels.
