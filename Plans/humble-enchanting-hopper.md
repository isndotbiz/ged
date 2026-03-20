# Plan: gedfix CLI v1.0 — Ship Today

## Context

Ship gedfix v1.0 CLI as a polished, installable Python package. The CLI already has 6 commands and solid core library. Three tests import functions that don't exist — must fix first. SwiftUI Mac app deferred to next session.

## Part 1: CLI v1.0 (Python)

### Step 1: Fix Broken Tests
Three test files import nonexistent functions. Add wrapper functions:

- **`gedfix/dates.py`** — Add `DateResult` dataclass + `normalize_gedcom_date_safe()` wrapping existing `is_gedcom_safe` + `normalize_spaces_and_case`
- **`gedfix/fixer.py`** — Add `fix_gedcom_text(text, level, rules)` → `(fixed_text, changes_list)` wrapping existing line-by-line logic
- **`gedfix/api.py`** — Add `scan_file(path)` → `{"issues": [{"rule": "unrecognized_date", ...}]}`
- Run `pytest tests/ -q` to confirm all pass

### Step 2: New CLI Commands
- **`gedfix version`** — Print version from package metadata
- **`gedfix stats <file.ged>`** — Person/family/source counts, top surnames, date range, issue counts. `--json` flag
- **`gedfix merge <a.ged> <b.ged> --out merged.ged`** — Merge two GEDCOMs with xref collision handling. New file: `gedfix/merge.py`
- **`gedfix export <file.ged> --filter-living --out safe.ged`** — Strip living persons' details. New file: `gedfix/export.py`

### Step 3: Package for Release
- **`pyproject.toml`** — Bump to 1.0.0, add license/authors/urls metadata
- **`gedfix/__init__.py`** — Add `__version__ = "1.0.0"`
- **`LICENSE`** — MIT license
- **`CHANGELOG.md`** — v1.0.0 entry
- **`.github/workflows/ci.yml`** — pytest on Python 3.11+3.12, ubuntu-latest
- **New tests:** `test_names.py`, `test_places.py` for normalization edge cases

### Files Modified/Created (Part 1)
| File | Action |
|------|--------|
| `gedfix/dates.py` | Add DateResult + normalize_gedcom_date_safe |
| `gedfix/fixer.py` | Add fix_gedcom_text |
| `gedfix/api.py` | Add scan_file |
| `gedfix/merge.py` | NEW — merge two GEDCOMs |
| `gedfix/export.py` | NEW — export with living filter |
| `gedfix/cli.py` | Add version, stats, merge, export commands |
| `gedfix/__init__.py` | Add __version__, update __all__ |
| `pyproject.toml` | Version 1.0.0 + metadata |
| `LICENSE` | NEW — MIT |
| `CHANGELOG.md` | NEW — v1.0.0 |
| `.github/workflows/ci.yml` | NEW — CI pipeline |
| `tests/test_names.py` | NEW — name normalization tests |
| `tests/test_places.py` | NEW — place normalization tests |

## Execution Order
1. Fix broken tests (dates, fixer, api) → run pytest
2. Add CLI commands (version, stats, merge, export)
3. Package files (LICENSE, CHANGELOG, CI, pyproject)
4. New tests (names, places)
5. Final pytest run + `gedfix --help` verification

## Verification
- `pytest tests/ -q` — all tests pass
- `gedfix version` — prints 1.0.0
- `gedfix stats` on a GEDCOM file — shows person/family counts
- `gedfix merge` on two files — produces merged output
- `gedfix export --filter-living` — strips living persons

## Deferred to Next Session
- SwiftUI Mac app (GedFixApp) with GRDB, GEDCOM parser, NavigationSplitView
