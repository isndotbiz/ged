from __future__ import annotations
from pathlib import Path
from .fixer import fix_file
from .dates import normalize_gedcom_date_safe
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

def scan_file(inp: Path) -> dict:
    """Scan a GEDCOM file and return issues with rule IDs.

    Returns {"file": ..., "issues": [{"rule": "unrecognized_date", "line": N, "value": ...}, ...]}
    """
    issues: list[dict] = []
    line_num = 0

    with inp.open("r", encoding="utf-8", errors="replace") as f:
        for raw_line in f:
            line_num += 1
            line = raw_line.strip()
            parts = line.split(" ", 2)
            if len(parts) >= 3 and parts[1] == "DATE":
                date_val = parts[2]
                result = normalize_gedcom_date_safe(date_val)
                if not result.is_valid:
                    issues.append({
                        "rule": "unrecognized_date",
                        "line": line_num,
                        "value": date_val,
                        "reason": result.reason,
                    })

    return {"file": str(inp), "issues": issues}


def fix(inp: Path, out: Path, level: str = "standard", backup_dir: Path | None = None, note_prefix: str | None = None) -> dict:
    return fix_file(inp, out, level, backup_dir, note_prefix)

def report(data: dict, out_json: Path) -> None:
    write_json_report(out_json, data)
