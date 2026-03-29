---
task: Unlinked media matching + image dedup + GEDCOM export + people dedup
slug: 20260327-cleanup-dedup-export
effort: deep
phase: execute
progress: 0/36
mode: interactive
started: 2026-03-27T00:00:00Z
updated: 2026-03-27T00:00:00Z
---

## Context

From the session summary, the top next steps are:
1. 592+ unlinked images (on disk but not in DB) — need filename-to-person matching
2. Image dedup — one image linked to all relevant people instead of 10 copies
3. GEDCOM 5.5.1 export for re-upload to Ancestry/MyHeritage/FamilySearch
4. People dedup — find and merge duplicate person records
5. Face auto-crop per person from shared group photos

Media dirs: ~/Documents/GedFix/media/photos/ (319 files), ~/Documents/GedFix/renamed_media/ (1108 files)
Root person: I2 (Jonathan Mallinger)

## Criteria

### Unlinked Media Matcher
- [ ] ISC-1: Scan media directories recursively for all image files
- [ ] ISC-2: Match filenames to people by surname (folder name) + given name (file prefix)
- [ ] ISC-3: Create media entries for unlinked files
- [ ] ISC-4: Create media_person_link entries connecting matched files to people
- [ ] ISC-5: Report: matched count, unmatched count, ambiguous matches
- [ ] ISC-6: UI in settings or media page to trigger matching + review results

### Image Deduplication
- [ ] ISC-7: Detect duplicate media entries (same file content / same path)
- [ ] ISC-8: Merge duplicates: keep one media row, transfer all person links to it
- [ ] ISC-9: Delete orphaned media rows after merge
- [ ] ISC-10: Show dedup report with before/after counts
- [ ] ISC-11: Never delete actual files on disk

### GEDCOM 5.5.1 Exporter
- [ ] ISC-12: Export all persons with NAME, SEX, BIRT, DEAT, BURI events
- [ ] ISC-13: Export all families with HUSB, WIFE, CHIL, MARR events
- [ ] ISC-14: Export sources with TITL, AUTH, PUBL
- [ ] ISC-15: Export media with OBJE FILE FORM TITL
- [ ] ISC-16: Valid GEDCOM 5.5.1 header and trailer
- [ ] ISC-17: Export passes basic GEDCOM validation (no structural errors)
- [ ] ISC-18: Download button in settings page
- [ ] ISC-19: Filename: gedfix_export_YYYY-MM-DD.ged

### People Deduplication
- [ ] ISC-20: Find potential duplicates by name similarity (fuzzy match)
- [ ] ISC-21: Score candidates by name + dates + places overlap
- [ ] ISC-22: Show candidate pairs with side-by-side comparison
- [ ] ISC-23: Merge function combines events, media, family links
- [ ] ISC-24: Merged person keeps the richer record (more data)
- [ ] ISC-25: Never lose data during merge — all events/media preserved

### Shared Image Multi-Link
- [ ] ISC-26: When a photo has multiple people, link it to all of them
- [ ] ISC-27: Face crop per person stored in media_person_link (faceX/Y/W/H)
- [ ] ISC-28: Each person's icon uses their individual face crop from the shared image

### Tree Cleanup Summary Page
- [ ] ISC-29: New /cleanup route (or enhanced settings)
- [ ] ISC-30: Shows all cleanup tools: match unlinked, dedup images, dedup people, export
- [ ] ISC-31: Progress bars for each operation
- [ ] ISC-32: Stats dashboard: total people, families, media, links, unlinked, duplicates

### Verification
- [ ] ISC-33: npm run check zero new errors
- [ ] ISC-34: npm run build passes
- [ ] ISC-35: cargo check passes
- [ ] ISC-36: Export produces valid GEDCOM structure
