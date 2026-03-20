# Changelog

## [1.0.0] - 2026-03-20

### Added
- `gedfix version` — print version
- `gedfix stats` — show tree statistics (individuals, families, surnames, date ranges)
- `gedfix merge` — merge two GEDCOM files with automatic xref collision handling
- `gedfix export --filter-living` — export with living person privacy filtering
- `DateResult` dataclass and `normalize_gedcom_date_safe()` public API
- `fix_gedcom_text()` in-memory fixing API for string input
- `scan_file()` API returning structured issues with rule IDs
- American date order reordering (e.g. "January 15, 1850" to "15 JAN 1850")
- FROM/TO and BET/AND patterns with month+year support
- MIT license
- GitHub Actions CI pipeline (Python 3.11, 3.12)

### Existing Commands (from 0.1.0)
- `gedfix scan` — scan GEDCOM and write JSON report
- `gedfix check` — run consistency checks, write markdown report
- `gedfix fix` — date/name/place normalization with AutoFix notes and backup
- `gedfix dedupe` — fuzzy-match duplicate detection and removal
- `gedfix review` — interactive manual review of consistency issues
