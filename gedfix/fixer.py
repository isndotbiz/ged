from __future__ import annotations

from typing import Dict, List, Tuple
import re

from .dates import normalize_gedcom_date_safe


AUTO_PREFIX = "AutoFix:"


def _has_autofix_note(lines: List[str], idx: int) -> bool:
    # Check following line for a NOTE with AutoFix prefix
    if idx + 1 < len(lines):
        nxt = lines[idx + 1]
        if re.search(r"\bNOTE\b", nxt) and AUTO_PREFIX in nxt:
            return True
    return False


def _append_note(lines: List[str], idx: int, message: str) -> None:
    # Preserve level indentation: level is first integer at line start
    m = re.match(r"^(\s*)(\d+)(.*)$", lines[idx])
    if not m:
        level_str = "1"
        prefix_ws = ""
        rest = ""
    else:
        prefix_ws, level_str, rest = m.groups()
    try:
        level = int(level_str)
    except ValueError:
        level = 1
    note_level = level + 1
    note_line = f"{prefix_ws}{note_level} NOTE {AUTO_PREFIX} {message}"
    lines.insert(idx + 1, note_line)


def fix_gedcom_text(text: str, level: str, rules: Dict) -> Tuple[str, List[Dict]]:
    # For now, implement safe DATE normalization and NOTE trail
    lines = text.splitlines()
    changes: List[Dict] = []

    line_re = re.compile(r"^\s*(\d+)\s+(?:@[^@\s]+@\s+)?([A-Za-z0-9_]+)\s*(.*)$")

    for i, line in enumerate(list(lines)):
        m = line_re.match(line)
        if not m:
            continue
        _level, tag, remainder = m.groups()
        if tag.upper() != "DATE":
            continue
        before_value = remainder.strip()
        result = normalize_gedcom_date_safe(before_value)

        # Normalize whitespace and month tokens only; never change semantics
        normalized_value = result.normalized if result.normalized is not None else before_value

        if result.is_valid:
            # If already equal, skip. Else rewrite safely (idempotent spacing/month case only)
            if normalized_value != before_value:
                # Reconstruct line preserving original leading level and tag spacing minimally
                leading = re.match(r"^(\s*\d+\s+(?:@[^@\s]+@\s+)?DATE)\b", line, re.IGNORECASE)
                if leading:
                    prefix = leading.group(1)
                else:
                    prefix = "2 DATE"
                lines[i] = f"{prefix} {normalized_value}"
                changes.append({
                    "rule": "date_normalize",
                    "message": "Normalized DATE whitespace/month tokens",
                    "before": before_value,
                    "after": normalized_value,
                })
        else:
            # Do not change original value; add NOTE once
            if not _has_autofix_note(lines, i):
                _append_note(
                    lines,
                    i,
                    f"Unrecognized DATE \"{before_value}\" preserved; please verify.",
                )
                changes.append({
                    "rule": "date_unrecognized_note",
                    "message": "Added NOTE for unrecognized DATE",
                    "before": before_value,
                    "after": None,
                })

    fixed_text = "\n".join(lines) + ("\n" if text.endswith("\n") else "")
    return fixed_text, changes


