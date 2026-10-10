# ShutUp icon

`shutup-icon.svg` preserves the supplied source artwork.

The Android foreground vector in `app/src/main/res/drawable/ic_launcher_foreground.xml`
retains both original paths, their relative placement, and the `#E3E3E3` fill.
The source transforms are combined, and the foreground is uniformly scaled and
centered so every path control point lies within a 28.8 dp radius of `(54, 54)`
in the 108 dp adaptive-icon layer. This leaves a margin inside the 66 dp safe zone.
The foreground is 10% smaller than the initial 32 dp radius version.

The black rectangle becomes the separate `ic_launcher_background` color.
The foreground also supplies the monochrome layer for themed icons.
The about screen scales the 108 dp foreground to match the launcher's central
72 dp viewport inside its circular icon container.
