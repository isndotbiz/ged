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


def fix_gedcom_text(text: str, level: str = "standard", rules: dict | None = None) -> tuple[str, list[dict]]:
    """Fix GEDCOM text in-memory and return (fixed_text, changes_list).

    Each change is a dict with keys: rule, line, original, fixed.
    """
    if rules is None:
        rules = load_rules()
    level_config = rules.get("levels", {}).get(level, {})
    note_prefix = rules.get("note_prefix", NOTE_PREFIX_DEFAULT)

    changes: list[dict] = []
    output_lines: list[str] = []
    all_lines = text.splitlines()
    text_joined = text  # for checking existing notes

    for i, line in enumerate(all_lines):
        stripped = " ".join(line.strip().split())

        if stripped.startswith(("1 ", "2 ", "3 ")):
            parts = stripped.split(" ", 2)
            if len(parts) >= 3:
                tag = parts[1]
                orig_val = parts[2]
                new_val = orig_val
                notes: list[str] = []

                if tag == "DATE" and level_config.get("fix_dates", True):
                    new_val, notes = sanitize_date_value(orig_val, level)

                elif tag == "NAME" and level_config.get("fix_names", True):
                    new_val, name_notes = sanitize_name_value(orig_val, level)
                    notes.extend(name_notes)

                elif tag == "PLAC" and level_config.get("fix_places", False):
                    new_val, place_notes = sanitize_place_value(orig_val, level)
                    notes.extend(place_notes)

                if new_val != orig_val:
                    changes.append({"rule": "date_normalized" if tag == "DATE" else f"{tag.lower()}_normalized",
                                    "line": stripped, "original": orig_val, "fixed": new_val})

                output_lines.append(f"{parts[0]} {tag} {new_val}")

                # Only add notes if an AutoFix note for this value doesn't already exist
                for note in notes:
                    note_marker = f'{note_prefix} {note} Original: "{orig_val}"'
                    if note_marker in text_joined:
                        continue  # already has this note — skip (idempotent)
                    rule = "date_unrecognized_note" if "Unrecognized" in note else "autofix_note"
                    changes.append({"rule": rule, "line": stripped, "original": orig_val, "note": note})
                    output_lines.append(f"{parts[0]} NOTE {note_prefix} {note} Original: \"{orig_val}\"")

                continue

        output_lines.append(stripped)

    return "\n".join(output_lines) + "\n", changes
