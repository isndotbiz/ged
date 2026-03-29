# GedFix Data Inventory — 2026-03-25

## GEDCOM Files
| File | Location | Lines | Individuals | Families | Media Refs |
|------|----------|-------|-------------|----------|------------|
| Ancestry fresh export | ~/Documents/Family Tree Maker/Ancestry_2026-03-25.ged | 44,240 | 1,584 | 737 | 155 OBJE links + 352 OBJE defs |
| Mallinger Cleaned (Mar 20) | ~/Documents/Family Tree Maker/Mallinger Family Tree Cleaned_2026-03-20.ged | 44,110 | ~1,584 | ~737 | ~352 |
| GedFix cleaned | ~/Documents/GedFix/mallinger_cleaned.ged | 44,359 | ~1,584 | ~737 | ~352 |

## Media Directories
| Directory | Files | Purpose |
|-----------|-------|---------|
| ~/Documents/Family Tree Maker/Ancestry Media/ | 944 | Canonical Ancestry-synced media |
| ~/Documents/Family Tree Maker/Mallinger Family Tree Cleaned Media/ | 940 | Cleaned copy (901 overlap with Ancestry) |
| ~/Documents/GedFix/media/ | 327 (photos + documents subdirs) | GedFix app working media |
| ~/Documents/GedFix/renamed_media/ | 1,108 | Renamed/processed media from earlier workflow |

## Key Finding: Media Linking Structure
- GEDCOM uses `@M###@ OBJE` top-level records with FILE paths
- Individual records link via `1 OBJE @M###@` references
- One OBJE can be linked from MULTIPLE individuals (many-to-many is native GEDCOM)
- File paths point to ~/Documents/Family Tree Maker/Ancestry Media/
- The fresh Ancestry export has the authoritative person-to-media mappings
- 352 media objects defined, 944 actual files in directory (592 files not in GEDCOM)

## Recommended Source of Truth
Use `Ancestry_2026-03-25.ged` as the authoritative GEDCOM — it's the freshest export with correct person-media links from Ancestry.
