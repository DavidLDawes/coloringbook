# Coloring Book
There is an example for using SVG images in Android

Used libraries from:  
https://github.com/Pixplicity/sharp  
https://github.com/chrisbanes/PhotoView  

**Tap-to-Sector Flow**

PhotoViewAttacher delivers (x, y) as 0.0–1.0 fractions of drawable size
→ PhilImageView.onPhotoTap()
→ VectorImageView.getSector(x, y)
lX = round(x * actW)        // scale to SVG pixel coords
lY = round(y * actH)
sector = ((bitmapMap.getPixel(lX, lY) << 16) >>> 16) - 1
→ setSectorColor(sector, color)  // update in-memory + async DB write
→ updatePicture()                // redraw all paths with current colors

**Key Mechanisms**

**Hit-map** bitmap (createMap(), line 250): Each sector path is drawn with color i+1 and alpha=0xFF, anti-aliasing OFF.
This creates a lookup table where pixel color encodes sector ID. The bit extraction (pixel << 16) >>> 16 isolates the
lower 16 bits (green+blue channels), supporting up to 65534 sectors.

**SVG element classification** (onSvgElement(), line 146): Paths with an id attribute become colorable sectors (stored in
sectorsPaths). Paths without id become background/outlines (stored in bckgSectorsPaths). The sectorsFlags array
preserves the original SVG draw order so updatePicture() layers them correctly.

**Coordinate consistency**: actW/actH are set from sharpDrawable.getPicture().getWidth()/getHeight() in createMap(), and
bitmapMap is created at exactly those dimensions. getSector() uses the same actW/actH for scaling — no mismatch
possible.

**Edge bounds**: Math.round(1.0f * actW) = actW, which fails lX < bitmapMap.getWidth(), so taps at the exact right/bottom
edge return -1 (invalid). This is correct — avoids off-by-one out-of-bounds.

**Overall**: The design of encoding sector IDs as pixel colors in a hidden bitmap is a clean O(1)
hit-test approach, and the anti-aliasing-off setting prevents ambiguous boundary pixels.