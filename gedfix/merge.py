"""Merge two GEDCOM files with xref collision handling."""
from __future__ import annotations
from pathlib import Path
import re


def _find_xrefs(lines: list[str]) -> set[str]:
    """Extract all xref definitions (@X@) from GEDCOM lines."""
    xrefs: set[str] = set()
    for line in lines:
        m = re.match(r"^0\s+(@\S+@)", line)
        if m:
            xrefs.add(m.group(1))
    return xrefs


def _remap_xrefs(lines: list[str], mapping: dict[str, str]) -> list[str]:
    """Replace xrefs in all lines according to mapping."""
    if not mapping:
        return lines
    # Build a regex that matches any of the old xrefs
    pattern = re.compile("|".join(re.escape(k) for k in mapping))
    return [pattern.sub(lambda m: mapping[m.group(0)], line) for line in lines]


def merge_gedcom(file_a: Path, file_b: Path, out: Path) -> dict:
    """Merge two GEDCOM files, remapping xrefs from file_b to avoid collisions.

    Returns a summary dict with counts.
    """
    lines_a = file_a.read_text(encoding="utf-8", errors="replace").splitlines()
    lines_b = file_b.read_text(encoding="utf-8", errors="replace").splitlines()

    xrefs_a = _find_xrefs(lines_a)
    xrefs_b = _find_xrefs(lines_b)

    # Find collisions and create remapping for file_b
    collisions = xrefs_a & xrefs_b
    mapping: dict[str, str] = {}
    if collisions:
        # Find the highest numeric xref in file_a to start remapping from
        max_num = 0
        for xref in xrefs_a | xrefs_b:
            m = re.search(r"(\d+)", xref)
            if m:
                max_num = max(max_num, int(m.group(1)))

        for xref in sorted(collisions):
            max_num += 1
            # Preserve the letter prefix (I for INDI, F for FAM, S for SOUR, etc.)
            prefix = re.match(r"@([A-Z]*)", xref)
            letter = prefix.group(1) if prefix else ""
            mapping[xref] = f"@{letter}{max_num}@"

    lines_b_remapped = _remap_xrefs(lines_b, mapping)

    # Extract records from each file (skip HEAD and TRLR from file_b)
    records_a: list[str] = []
    records_b: list[str] = []
    head_a: list[str] = []
    trlr_line = "0 TRLR"

    # Parse file_a — keep everything
    in_head = False
    for line in lines_a:
        stripped = line.strip()
        if stripped.startswith("0 HEAD"):
            in_head = True
        if in_head:
            head_a.append(line)
            if stripped.startswith("0 ") and not stripped.startswith("0 HEAD"):
                in_head = False
                records_a.append(line)
            continue
        if stripped == "0 TRLR":
            continue
        records_a.append(line)

    # Parse file_b — skip HEAD and TRLR
    skip_head = False
    for line in lines_b_remapped:
        stripped = line.strip()
        if stripped.startswith("0 HEAD"):
            skip_head = True
            continue
        if skip_head:
            if stripped.startswith("0 ") and not stripped.startswith("0 HEAD"):
                skip_head = False
            else:
                continue
        if stripped == "0 TRLR":
            continue
        records_b.append(line)

    # Combine: HEAD from A + records from A + records from B + TRLR
    merged = head_a + records_a + records_b + [trlr_line]
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(merged) + "\n", encoding="utf-8")

    return {
        "file_a": str(file_a),
        "file_b": str(file_b),
        "xrefs_a": len(xrefs_a),
        "xrefs_b": len(xrefs_b),
        "collisions_remapped": len(mapping),
        "records_merged": len(records_b),
        "out": str(out),
    }
