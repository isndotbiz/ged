from __future__ import annotations
from pathlib import Path
from .fixer import fix_file
from .validation import validate_gedcom_structure
from .report import write_json_report

def scan(inp: Path, comprehensive: bool = False) -> dict:
    """Scan GEDCOM file for issues and statistics."""
    lines = sum(1 for _ in inp.open("r", encoding="utf-8", errors="replace"))
    
    basic_report = {
        "file": str(inp),
        "lines": lines,
        "issues": {
            "dates_to_review": 0,
            "names_to_standardize": 0,
            "places_to_normalize": 0
        }
    }
    
    # Add comprehensive validation if requested
    if comprehensive:
        validation_report = validate_gedcom_structure(inp)
        basic_report["validation"] = validation_report
        basic_report["issues"].update({
            "orphaned_individuals": validation_report["issues"]["orphaned_individuals"],
            "missing_references": validation_report["issues"]["missing_references"],
            "structural_issues": validation_report["issues"]["structural_issues"],
            "potential_duplicates": validation_report["issues"]["potential_duplicates"]
        })
    
    return basic_report

def fix(inp: Path, out: Path, level: str = "standard", backup_dir: Path | None = None, note_prefix: str | None = None) -> dict:
    return fix_file(inp, out, level, backup_dir, note_prefix)

def report(data: dict, out_json: Path) -> None:
    write_json_report(out_json, data)
