# Craftly (TENIx) — Phase 1

Java + XML Android text/graphics editor. Package: `com.craftly.studio.design`

## Build on GitHub Actions
1. Create a new GitHub repo and push ALL files of this folder (including the hidden `.github` folder).
2. Open the Actions tab, run "Build Craftly APK" (it also runs on every push to main/master).
3. Download the `Craftly-debug-apk` artifact, extract it and install the APK.

## Phases
- Phase 1 (this zip): project setup, canvas + layer system, text engine (font, color, style, size, spacing, alignment), image layers, background, move/scale/rotate/pinch, undo/redo, layers sheet, PNG/JPG export + share.
- Phase 2: text effects (shadow, stroke, glow, gradient, 3D, curve), shapes, stickers, brush drawing, filters, blend modes, crop/flip, custom fonts, project save/load, templates.
- Phase 3: offline AI background remover, Bangla shaping + font pack, path text, masks, Firebase template library, batch export, GPU rendering, backup/restore, polish + release signing.
