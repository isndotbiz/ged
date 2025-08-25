from __future__ import annotations
from pathlib import Path
from typing import Iterable
from .dates import sanitize_date_value
from .names import sanitize_name_value
from .places import sanitize_place_value
from .validation import validate_gedcom_structure
from .rules import load_rules
from .io_utils import backup_file

NOTE_PREFIX_DEFAULT = "AutoFix:"

def iterate_lines(p: Path) -> Iterable[str]:
    with p.open("r", encoding="utf-8", errors="replace") as f:
        for line in f:
            yield line.rstrip("\n")

def write_lines(p: Path, lines: Iterable[str]) -> None:
    with p.open("w", encoding="utf-8", newline="\n") as f:
        for line in lines:
            f.write(line + "\n")

def fix_file(inp: Path, out: Path, level: str, backup_dir: Path | None, note_prefix: str | None) -> dict:
    rules = load_rules()
    level_config = rules.get("levels", {}).get(level, {})
    note_prefix = note_prefix or rules.get("note_prefix", NOTE_PREFIX_DEFAULT)
    
    if backup_dir:
        backup_file(inp, backup_dir)

    changed = 0
    notes_added = 0
    output_lines: list[str] = []
    validation_report = None
    
    # Run structural validation if enabled
    if level_config.get("structural_validation", False):
        validation_report = validate_gedcom_structure(inp)

    for line in iterate_lines(inp):
        original_line = line
        # Normalize simple multiple spaces
        stripped = " ".join(line.strip().split())
        
        # Process different GEDCOM tags based on level configuration
        if stripped.startswith("1 ") or stripped.startswith("2 ") or stripped.startswith("3 "):
            parts = stripped.split(" ", 2)
            if len(parts) >= 3:
                tag = parts[1]
                orig_val = parts[2]
                new_val = orig_val
                notes = []
                
                # Handle DATE fields
                if tag == "DATE" and level_config.get("fix_dates", True):
                    new_val, notes = sanitize_date_value(orig_val, level)
                
                # Handle NAME fields
                elif tag == "NAME" and level_config.get("fix_names", True):
                    new_val, name_notes = sanitize_name_value(orig_val, level)
                    notes.extend(name_notes)
                
                # Handle PLAC fields
                elif tag == "PLAC" and level_config.get("fix_places", False):
                    new_val, place_notes = sanitize_place_value(orig_val, level)
                    notes.extend(place_notes)
                
                # Output the processed line
                if new_val != orig_val:
                    changed += 1
                output_lines.append(f"{parts[0]} {tag} {new_val}")
                
                # Add notes if any
                for note in notes:
                    notes_added += 1
                    output_lines.append(f"{parts[0]} NOTE {note_prefix} {note} Original: \"{orig_val}\"")
                
                continue
        
        # Default: write normalized spacing
        if stripped != original_line:
            changed += 1
        output_lines.append(stripped)

    write_lines(out, output_lines)
    
    result = {
        "changed": changed, 
        "notes_added": notes_added, 
        "out": str(out),
        "level": level
    }
    
    # Add validation report if available
    if validation_report:
        result["validation"] = validation_report
    
    return result
