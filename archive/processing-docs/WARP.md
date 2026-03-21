# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

# WARP guide: gedfix CLI

This guide orients future AI agents and contributors working on gedfix, a date-safe GEDCOM 5.5 and 5.5.1 cleanup tool. It includes mission, invariants, architecture, workflows, coding conventions, and a roadmap aligned with MyHeritage Consistency Checker categories, structural orphan detection via validator JSON, configurable rules, and detailed logging.

## Quickstart
- Python version: 3.11 or newer
- Install:
  - `python -m venv .venv`
  - `source .venv/bin/activate` on macOS or Linux
  - `.venv\Scripts\activate` on Windows
  - `pip install -e .[dev]`
- CLI examples:
  - `gedfix scan INPUT.ged --report out/report.json`
  - `gedfix fix INPUT.ged --level standard --out out/gedfix_fixed.ged --backup-dir out/backup --dry-run`
  - `gedfix report out/report.json --markdown out/report.md`

## Repository map
- `README.md`
- `pyproject.toml`
- `gedfix/` package
  - `cli.py`: Click CLI entrypoint and commands scan, fix, report
  - `api.py`: Orchestration, file IO, and report generation helpers
  - `dates.py`: Safe GEDCOM date normalization and validation
  - `fixer.py`: Line-by-line DATE normalization and NOTE injection
  - `rules.yaml`: Config defaults including note_prefix and merge thresholds
  - **MISSING BUT REFERENCED BY CODE**: `__init__.py`, `rules.py`, `report.py`, `io_utils.py`
- `tests/`
  - `test_dates.py`

## Core invariants and safety rules
- **Date safety**
  - Only whitespace collapsing and month token uppercasing are applied to valid dates
  - No semantic changes to valid GEDCOM dates
- **Unrecognized dates**
  - Original value is preserved
  - A single NOTE line is added immediately after the DATE line with prefix `AutoFix:`
  - The fixer is idempotent and will not duplicate notes
- **Default behavior of fix**
  - `fix` uses `--dry-run` by default and will not write a new GED file unless `--no-dry-run` is specified
  - When not a dry run, a backup of the original is written to `--backup-dir`
  - Current implementation writes fixed output using UTF-8 encoding
- **Configuration**
  - `rules.yaml` is the primary config file
  - `note_prefix` must match the prefix used by fixer to avoid drift
- **Performance and safety**
  - Operates line-by-line and avoids loading large structures into memory where possible
  - Use incremental improvements; prefer explicit, easily auditable transformations

## CLI usage patterns
- **Scan a file and produce a JSON report:**
  - `gedfix scan input.ged --report out/report.json`
- **Fix a file safely, normalize valid DATE lines, and add NOTE to unrecognized ones:**
  - `gedfix fix input.ged --level standard --out out/gedfix_fixed.ged --backup-dir out/backup --dry-run`
  - Remove the `--dry-run` flag to write changes and create a backup
- **Convert a JSON report to Markdown:**
  - `gedfix report out/report.json --markdown out/report.md`

## Architecture overview
- **cli.py**
  - Click command group with version option and three subcommands: scan, fix, report
- **api.py**
  - `scan_file` reads text with encoding, heuristically validates DATE lines using `dates.normalize_gedcom_date_safe`, aggregates issues, and returns a JSON-serializable dict
  - `fix_file` coordinates `fixer.fix_gedcom_text`, collects changes, and returns a structured report and optionally fixed text
  - `write_markdown_report` renders a simple Markdown summary
- **dates.py**
  - `normalize_gedcom_date_safe` performs space collapsing and uppercase month tokens only
  - Recognizes simple forms like YYYY, MON YYYY, DD MON YYYY, ranged forms like BET and AND, FROM and TO, and prefixed forms like ABT, EST, CAL, INT, BEF, AFT
  - Classifies freeform dates like 01/02/1903 as `non_gedcom_freeform` and unrecognized forms as `unrecognized`
- **fixer.py**
  - Scans for DATE lines, applies safe normalization only on valid dates
  - Adds a single NOTE line with `AutoFix:` message for unrecognized dates
  - Idempotent behavior enforced by checking for a NOTE on the next line
- **Configuration and rules**
  - `rules.yaml` provides defaults for note prefix and merge thresholds
  - `load_rules` is referenced in code but not yet implemented in the provided context
- **Reporting**
  - `summarize_report` is referenced but not yet implemented in the provided context
- **IO**
  - `read_text_preserve_encoding` is referenced but not yet implemented in the provided context

## Known missing modules and recommended minimal implementations
Create these to satisfy current imports and enable basic functionality.

1. **gedfix/__init__.py**
```python
from importlib.metadata import PackageNotFoundError, version as pkg_version

try:
    __version__ = pkg_version("gedfix")
except PackageNotFoundError:
    __version__ = "0.0.0"
```

2. **gedfix/io_utils.py**
```python
from __future__ import annotations

from pathlib import Path

def read_text_preserve_encoding(path: Path) -> tuple[str, str]:
    # Try common encodings first, then fallback with replacement
    for enc in ("utf-8-sig", "utf-8", "cp1252", "latin-1"):
        try:
            return path.read_text(encoding=enc), enc
        except UnicodeDecodeError:
            continue
    data = path.read_bytes()
    return data.decode("latin-1", errors="replace"), "latin-1"
```

3. **gedfix/report.py**
```python
from __future__ import annotations

from collections import Counter
from typing import Iterable, Dict, Any

def summarize_report(items: Iterable[Dict[str, Any]]) -> Dict[str, int]:
    # Items may be issues from scan or changes from fix
    rules = [i.get("rule", "unknown") for i in items]
    counts = Counter(rules)
    out = {"total": sum(counts.values())}
    for k, v in counts.items():
        out[f"rule:{k}"] = v
    return out
```

4. **gedfix/rules.py**
```python
from __future__ import annotations

from pathlib import Path
from typing import Optional, Dict, Any
import yaml

try:
    from importlib.resources import files
except ImportError:
    # Python 3.11 has importlib.resources.files
    from importlib_resources import files  # type: ignore

DEFAULT_RULES: Dict[str, Any] = {
    "note_prefix": "AutoFix:",
    "merge_thresholds": {"place_ratio": 92, "date_ratio": 95},
    "merge_allowed_tags": ["BIRT", "DEAT", "MARR"],
}

def load_rules(external_path: Optional[Path] = None) -> Dict[str, Any]:
    if external_path is not None:
        return _merge(DEFAULT_RULES, _read_yaml(external_path))
    try:
        text = files("gedfix").joinpath("rules.yaml").read_text(encoding="utf-8")
        data = yaml.safe_load(text) or {}
        return _merge(DEFAULT_RULES, data)
    except Exception:
        return dict(DEFAULT_RULES)

def _read_yaml(path: Path) -> Dict[str, Any]:
    try:
        return yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    except Exception:
        return {}

def _merge(a: Dict[str, Any], b: Dict[str, Any]) -> Dict[str, Any]:
    out = dict(a)
    for k, v in b.items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = _merge(out[k], v)
        else:
            out[k] = v
    return out
```

**Packaging note for rules.yaml:**
- Ensure rules.yaml is included in the built wheel and sdist. Add to pyproject.toml:
```toml
[tool.setuptools.package-data]
"gedfix" = ["rules.yaml"]
```

## Configuration and rules
- **rules.yaml keys**
  - `note_prefix`: default string used to prefix NOTE lines for unrecognized dates
  - `merge_thresholds`: reserved for future record merge heuristics based on rapidfuzz
  - `merge_allowed_tags`: tags that may be considered in merge operations
- **Single source of truth**
  - Prefer wiring fixer to read the note_prefix from load_rules rather than hardcoding `AutoFix:` in fixer.py
  - Until wired, keep rules.yaml note_prefix synchronized with `fixer.AUTO_PREFIX`

## Logging guidance
- **Goal**: detailed, structured logging with verbosity controls
- **Implementation sketch:**
  - Add a `--verbose` or `--log-level` option to CLI group
  - Initialize Python logging with JSON or key-value formatting
  - Log at INFO for high-level steps and DEBUG for line transforms and decisions
- **Example snippet for cli.py:**
```python
import logging

@click.group()
@click.version_option(__version__)
@click.option("--log-level", default="INFO", show_default=True, type=click.Choice(["CRITICAL","ERROR","WARNING","INFO","DEBUG"]))
def main(log_level: str) -> None:
    logging.basicConfig(level=getattr(logging, log_level), format="%(levelname)s %(name)s %(message)s")
```

## MyHeritage Consistency Checker alignment roadmap
- **Objectives**
  - Expand scan to detect issues analogous to MyHeritage categories
  - Emit rule identifiers and messages matching or mapping to those categories
- **Approach**
  - Switch or augment scanning to parse records using ged4py for structural awareness
  - Build a rule registry mapping category ids to validation functions
  - Enrich report JSON with category fields for downstream rendering
- **Examples of target categories**
  - Birth after death, parental age anomalies, missing essential facts, inconsistent dates, out-of-range ages, place mismatches

## Structural orphan detection via validator JSON
- **Purpose**
  - Detect references that point to missing records and invalid hierarchical structures
- **validator.json shape suggestion**
```json
{
  "entities": {
    "INDI": {
      "required_child_tags": ["NAME"],
      "allowed_child_tags": ["BIRT", "DEAT", "MARR", "FAMC", "FAMS"]
    },
    "FAM": {
      "required_child_tags": [],
      "allowed_child_tags": ["HUSB", "WIFE", "CHIL", "MARR", "DIV"]
    }
  },
  "orphan_rules": [
    "INDI.FAMC must reference existing FAM",
    "INDI.FAMS must reference existing FAM",
    "FAM.CHIL must reference existing INDI",
    "XREFs must be unique per type"
  ]
}
```
- **Integration plan**
  - Add a validator module that builds indices of xrefs and validates links
  - Load validator.json and apply structural checks during scan
  - Emit issues with rule ids like `orphan_ref_missing_target` and `orphan_duplicate_xref`

## Reports
- **JSON structure from scan or fix**
  - file, encoding, level, issues or changes, summary, rules
- **Markdown rendering**
  - `api.write_markdown_report` produces a simple readable summary
  - Consider adding a richer Markdown or HTML template

## Testing
- **Existing**
  - `tests/test_dates.py` validates `dates.normalize_gedcom_date_safe`
- **Additions recommended**
  - fixer idempotence and note injection tests
  - API scan and fix integration tests with small GED snippets
  - CLI smoke tests using `click.testing.CliRunner`
- **Run with:**
  - `pytest -q`

## Performance notes
- Current line-by-line scanning is memory-friendly
- Parsing with ged4py will require careful streaming for large files
- Prefer iterating records and indexing xrefs without loading entire file when feasible

## Known gaps and TODOs
- Implement missing modules listed above
- Unify note_prefix between fixer and rules.yaml
- Preserve original file encoding when requested or record encoding in output metadata
- Add verbose logging and structured logs
- Implement MyHeritage category scanning and validator JSON checks
- Package rules.yaml in distributions

## Troubleshooting
- **UnicodeDecodeError on read**
  - `io_utils.read_text_preserve_encoding` tries several encodings; extend list if needed
- **Duplicate notes**
  - Check `fixer._has_autofix_note` logic; ensure consistent note prefix
- **Version import error**
  - Ensure `gedfix/__init__.py` defines `__version__` using `importlib.metadata`

## Contribution checklist
- Adhere to the core invariants above
- Include or update tests for new behavior
- Update rules.yaml and docs when changing configuration
- Run pytest and supply sample before/after snippets when modifying fixer logic
- Keep changes idempotent and auditable

## Glossary
- **GEDCOM**: genealogical data format, versions 5.5 and 5.5.1
- **DATE line**: GEDCOM tag DATE with date literal following
- **Idempotent**: repeated runs do not produce additional changes beyond the first
