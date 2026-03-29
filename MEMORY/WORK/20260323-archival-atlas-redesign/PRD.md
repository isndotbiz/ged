---
task: Archival atlas redesign of GedFix shell
slug: 20260323-archival-atlas-redesign
effort: extended
phase: complete
progress: 16/16
mode: interactive
started: 2026-03-23T00:00:00Z
updated: 2026-03-23T00:10:00Z
---

## Context

Redesign the GedFix app shell (+layout.svelte), overview page (+page.svelte), and shared CSS tokens (app.css) into a distinctive "archival atlas meets research desk" aesthetic. The current implementation already has a warm archival foundation but reads as generic — push it to be bold, memorable, and editorially distinctive. Desktop genealogy app built with SvelteKit 2, Svelte 5, Tailwind CSS 4, Tauri.

### Risks
- Breaking existing tree navigation, import/loading state, or face-crop workflow
- CSS token changes rippling into other routes that already use arch-* classes
- Accessibility regression from contrast or focus state changes
- Build failures from Svelte/TS type issues introduced during template changes

## Criteria

- [x] ISC-1: Sidebar masthead has distinctive decorative treatment beyond plain text
- [x] ISC-2: Sidebar section labels use archival divider styling with ruled lines
- [x] ISC-3: Sidebar active nav state visually distinct with stronger indicator
- [x] ISC-4: Sidebar hover states have visible feedback on inactive items
- [x] ISC-5: App background uses layered paper texture with depth
- [x] ISC-6: Overview toolbar has editorial character beyond plain flex row
- [x] ISC-7: Tree ancestor cards have richer archival surface treatment
- [x] ISC-8: Generation connector lines styled as archival lineage marks
- [x] ISC-9: Empty tree slots have subtle placeholder styling
- [x] ISC-10: Import/loading state has polished archival presentation
- [x] ISC-11: Overlay panels (face picker, crop tool) have atmospheric backdrop
- [x] ISC-12: CSS variables expanded with richer palette and depth tokens
- [x] ISC-13: Motion system has staged page-load reveal animation
- [x] ISC-14: Focus-visible states present on all interactive elements
- [x] ISC-15: npm run check passes with zero new errors
- [x] ISC-16: npm run build passes successfully

## Decisions

- Only added/modified CSS variable values, never renamed — prevents breaking 19 other routes
- Used system font stacks (Georgia, Gill Sans) — no external font downloads needed
- Kept sidebar width increase minimal (230→240px) — preserves content area
- Used inline SVG shield for masthead — no external asset dependency

## Verification

- ISC-1: Shield SVG crest + tagline in layout.svelte:143-153
- ISC-2: Left accent border on section spans, layout.svelte:162
- ISC-3: Gold left border on active nav items, layout.svelte:176
- ISC-4: CSS hover state via .nav-link:not(.nav-active):hover, app.css:319
- ISC-5: Crosshatch linen SVG pattern in --texture-paper, app.css:79
- ISC-6: animate-fade-in, letter-spacing, gold separator, page.svelte:234,246,254
- ISC-7: border-radius:10px + inner shadow on hover, page.svelte:200-228
- ISC-8: Solid lines with opacity:0.4, page.svelte:311
- ISC-9: Dashed border circle, page.svelte:304
- ISC-10: Serif font, 360px width, double rule, layout.svelte:211-216
- ISC-11: animate-fade-in + animate-dossier on overlays, page.svelte:326-327,369-370
- ISC-12: 5 new color vars, shadow-inset, shadow-deep, border-decorative, app.css:29-41
- ISC-13: dossierReveal keyframe + stagger to 10 children, app.css:380-387
- ISC-14: Global :focus-visible with accent outline, app.css:146-150
- ISC-15: 4 errors all pre-existing in untouched files, 0 new errors
- ISC-16: Build succeeds, static site written to build/
