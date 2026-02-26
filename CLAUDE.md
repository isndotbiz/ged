# GEDCOM Processing Platform

A production-grade system for cleaning, standardizing, and validating GEDCOM 5.5.1 datasets.

## Stack

- Python 3.11
- ged4py, click, rapidfuzz, python-dateutil
- Library and CLI in `gedfix/` with `pyproject.toml`

## Key Directories

- `gedfix/` - Core Python library and CLI
- `scripts/` - Processing workflow scripts
- `rules/` - Validation and standardization rules
- `tests/` - Test suite
- `docs/` - Documentation
- `data/` - Input data (large files excluded from git)

## Common Tasks

```bash
# Install in dev mode
pip install -e .

# Run tests
pytest tests/

# Run CLI
gedfix --help
```

## Notes

- Large data files (`.txt`, `.large`, `maps/`, `media/`) are excluded from git
- Output files in `out/` are excluded from git
