# GEDCOM Processing Platform

A production-grade Python platform for cleaning, standardizing, and validating GEDCOM 5.5.1 genealogy datasets with audit trails and zero data loss guarantees.

## Stack

- Python 3.11
- ged4py >= 0.5.2 (GEDCOM parsing), click >= 8.1 (CLI), rapidfuzz >= 3.9 (fuzzy matching), python-dateutil >= 2.9
- Packaged library and CLI in `gedfix/` via `pyproject.toml`; entrypoint: `gedfix`

## Key Directories

- `gedfix/` - Core Python library (fixer, checker, deduplication, dates, names, places, validation, manual_review)
- `scripts/` - ~20 processing, integrity, and rollback scripts (Python + bash)
- `rules/` - Rule configs and source data; subdirs: analysis/, master/, merged/, problems/, processing/
- `tests/` - pytest suite (5 test files)
- `docs/` - Methodology and analysis documentation
- `data/` - Input data; subdirs: master/, processing/, analysis/, merged/, problems/; large files excluded from git
- `fact/` - Geocoding working area: place cache CSV, unresolved place lists, issue CSVs, geocoded GEDs

## Setup

```bash
pip install -e .
pytest tests/ -q
gedfix --help
```

## CLI Commands

```bash
# Scan a file for statistics (writes JSON report)
gedfix scan input.ged --report out/report.json

# Batch consistency checks without modifying the file
gedfix check input.ged --level standard|aggressive|ultra

# Fix: date/name/place normalization with AutoFix notes
gedfix fix input.ged --out cleaned.ged --level standard|aggressive|ultra|comprehensive
gedfix fix input.ged --out cleaned.ged --backup-dir ./backups --approve-dup-facts --approve-suffix
gedfix fix input.ged --dry-run   # preview only

# Fuzzy duplicate removal
gedfix dedupe input.ged --out deduped.ged --threshold 95.0
gedfix dedupe input.ged --dry-run

# Interactive manual review of consistency issues
gedfix review input.ged --issues-file session.json
```

## Fix Levels (gedfix/rules.yaml)

| Level | merge_duplicates | fuzzy_threshold | fix_places | structural_validation |
|-------|-----------------|-----------------|------------|----------------------|
| standard | no | 96 | no | no |
| aggressive | yes | 92 | yes | yes |
| ultra | yes | 88 | yes | yes |
| comprehensive | yes | 90 | yes | yes |

All levels fix dates and names. AutoFix notes use prefix `"AutoFix:"` by default.

## Key Scripts

```bash
scripts/verify_data_integrity.sh original.ged processed.ged   # detect data loss
scripts/setup_processing_workspace.sh data/master/roots_master.ged
scripts/rollback_to_backup.sh <backup_file>
scripts/master_gedcom_workflow.sh data/master/roots_master.ged
```

## Notes

- Large data files (`.txt`, `.large`, `maps/`, `media/`) are excluded from git
- Output files in `out/` are excluded from git
- The project processing run (Aug 2025) is complete; remaining open items are manual genealogical review (265 flagged issues, 365 potential duplicates)
- Place geocoding cache: `fact/places_cache.csv`; unresolved places: `fact/unresolved_places.txt`
