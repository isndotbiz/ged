---
task: Archival atlas redesign of GedFix app shell
slug: 20260323-180000_archival-atlas-gedfix-redesign
effort: advanced
phase: complete
progress: 28/28
mode: interactive
started: 2026-03-23T18:00:00-07:00
updated: 2026-03-23T18:05:00-07:00
---

## Context

Redesign the GedFix desktop app shell (+layout.svelte), overview page (+page.svelte), and CSS tokens (app.css) from generic Apple-style UI to a distinctive "archival atlas meets research desk" aesthetic. Warm paper/ink base, editorial serif/sans typography, genealogy-specific visual language. 3 files in scope. Zero behavior changes, zero new dependencies, zero external assets.

### Risks
- Tailwind CSS 4 uses `@import "tailwindcss"` — custom CSS vars must coexist cleanly — MITIGATED
- Existing face-crop overlay coordinates are pixel-sensitive — MITIGATED by preserving all crop logic
- Tree card classes (.m-card, .f-card, .u-card) are in `<style>` block — RETHEMED with CSS vars
- Other route pages inherit layout shell — sidebar changes are cosmetic only

## Criteria

### CSS Token System (app.css)
- [x] ISC-1: CSS variables define warm paper palette (background, surface, ink, accent)
- [x] ISC-2: CSS variables define serif and sans font stacks from system fonts
- [x] ISC-3: CSS variables define shadow, border, and surface elevation tokens
- [x] ISC-4: CSS variables define motion duration and easing tokens
- [x] ISC-5: Body uses warm paper background instead of #F5F5F7 Apple gray
- [x] ISC-6: Scrollbar thumb styled to match warm palette
- [x] ISC-7: Fade-in animation updated for staged reveal effect

### Sidebar Shell (+layout.svelte)
- [x] ISC-8: Sidebar background uses warm archival surface (dark walnut #2C2418)
- [x] ISC-9: App title "GedFix" styled with serif font as editorial masthead
- [x] ISC-10: Section headers styled as archive tab labels with ruled lines
- [x] ISC-11: Active nav item uses ink/warm accent, not Apple blue
- [x] ISC-12: Inactive nav items show warm gray with hover lift effect
- [x] ISC-13: Nav item count badges styled as archival reference numbers (mono font, subtle bg)
- [x] ISC-14: Sidebar footer stats styled as archive catalog notation
- [x] ISC-15: Import loading state renders coherently in new shell

### Overview Page (+page.svelte)
- [x] ISC-16: Page background uses warm parchment treatment
- [x] ISC-17: Toolbar styled as research desk header bar with ruled underline
- [x] ISC-18: Tree generation controls (+/-) styled as archival buttons
- [x] ISC-19: Male ancestor cards rethemed to warm ink palette via CSS vars
- [x] ISC-20: Female ancestor cards rethemed to warm ink palette via CSS vars
- [x] ISC-21: Unknown cards rethemed to neutral archive card style
- [x] ISC-22: Tree connection lines styled as ruled genealogy lines
- [x] ISC-23: Face picker overlay uses archival surface and typography
- [x] ISC-24: Crop tool overlay uses archival surface and typography

### Behavior Preservation
- [x] ISC-25: All sidebar navigation links remain functional (code unchanged)
- [x] ISC-26: History back button still works (code unchanged)
- [x] ISC-27: npm run check passes (4 pre-existing errors, 0 new)
- [x] ISC-28: npm run build passes (clean build, wrote to build/)

### Anti-Criteria
- [x] ISC-A1: No purple gradients present
- [x] ISC-A2: No new npm dependencies added
- [x] ISC-A3: No behavioral logic changed in any file

## Decisions

- Dark sidebar (#2C2418 walnut) instead of light sidebar — creates stronger visual anchor and editorial gravitas
- Gold accent (#8B6914) instead of #007AFF — archival feel, avoids tech-blue
- Georgia/Palatino serif stack for headings, Gill Sans/Calibri for body — system fonts with editorial character
- Inline style attributes for CSS var references — avoids Tailwind arbitrary value syntax clutter while keeping token system clean
- Kept .m-card/.f-card/.u-card class names but rewired to CSS vars — backwards compatible with existing code
- Subtle SVG noise texture on paper background — adds physicality without external assets

## Verification

- npm run check: 4 pre-existing errors (gedcom-parser.ts, media, people pages), 0 new errors introduced
- npm run build: Clean production build, 670ms client + 1.90s server, wrote to build/
- All behavioral code (loadTree, nav, back, openPicker, startCrop, saveCrop, autoImport) preserved verbatim
- No new dependencies in package.json
- No purple, no #007AFF, no Apple-style patterns remain in modified files
