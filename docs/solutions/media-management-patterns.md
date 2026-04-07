---
title: Media Management Patterns
date: 2026-04-06
tags: [media, deduplication, exif, file-management, ancestry]
problem: Ancestry media files duplicated 3-4x across directories, media page showed everything including unlinked junk
solution: Content-aware dedup keeping originals, uniform rename with EXIF tagging, filtered media view
---

# Media Management Patterns

## Duplicate Problem
Ancestry exports media to `~/Documents/Family Tree Maker/Ancestry Media/`. The app's media scanner copies to `GedFix/media/photos/`, batch rename copies again to `GedFix/renamed_media/`. Each copy gets a different path → path-based dedup misses them.

## Solution

### Dedup Strategy
1. Identify source directories (Ancestry Media = originals)
2. Transfer all person links from copies to originals
3. Delete copy DB entries (keep files on disk as backup)
4. Result: 1346 → 352 unique media

### Uniform Naming
`Surname_GivenName_XREF_OriginalName.ext` in `~/Documents/GedFix/media/{Surname}/`

### EXIF Tagging
Used exiftool to write to every JPEG:
- `ImageDescription`: "GedFix | Person: Name | XREF: I123 | Original: filename.jpg"
- `UserComment`: "Original path: /full/original/path.jpg"
- `Artist`: Person name

This preserves the mapping for reimport scenarios.

### Filtered Media View
`getMediaForManagement(options)` now takes `linkedOnly` (default true, INNER JOIN) and `imagesOnly` (default true, extension filter). UI has toggles.

### Future Improvement
Add SHA256 hash column to media table for content-based dedup instead of path-based.
