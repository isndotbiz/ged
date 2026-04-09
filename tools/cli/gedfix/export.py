"""Export GEDCOM with privacy filtering for living persons."""
from __future__ import annotations
from pathlib import Path
import re
from datetime import date


def _extract_birth_year(lines: list[str], start: int) -> int | None:
    """Extract birth year from an INDI record starting at the given index."""
    i = start + 1
    in_birt = False
    while i < len(lines):
        stripped = lines[i].strip()
        if stripped.startswith("0 "):
            break  # next record
        if stripped == "1 BIRT":
            in_birt = True
        elif in_birt and stripped.startswith("2 DATE"):
            date_val = stripped[7:].strip()
            m = re.search(r"(\d{4})", date_val)
            if m:
                return int(m.group(1))
            in_birt = False
        elif stripped.startswith("1 "):
            in_birt = False
        i += 1
    return None


def _has_death(lines: list[str], start: int) -> bool:
    """Check if an INDI record has a death event."""
    i = start + 1
    while i < len(lines):
        stripped = lines[i].strip()
        if stripped.startswith("0 "):
            break
        if stripped in ("1 DEAT", "1 BURI", "1 CREM"):
            return True
        i += 1
    return False


def _extract_surname(lines: list[str], start: int) -> str:
    """Extract surname from INDI record NAME tag."""
    i = start + 1
    while i < len(lines):
        stripped = lines[i].strip()
        if stripped.startswith("0 "):
            break
        if stripped.startswith("1 NAME"):
            name_val = stripped[7:].strip()
            m = re.search(r"/([^/]+)/", name_val)
            if m:
                return m.group(1)
        i += 1
    return ""


def is_living(lines: list[str], start: int, cutoff_years: int = 110) -> bool:
    """Determine if a person at the given line index should be treated as living."""
    if _has_death(lines, start):
        return False
    birth_year = _extract_birth_year(lines, start)
    if birth_year and (date.today().year - birth_year) > cutoff_years:
        return False
    # No death and either recent birth or unknown birth — assume living
    return True


def export_filtered(inp: Path, out: Path, filter_living: bool = True, cutoff_years: int = 110) -> dict:
    """Export a GEDCOM file with living persons' details redacted.

    Living persons keep their xref and family links but have names replaced
    with "Living /Surname/" and all events/notes/sources stripped.
    """
    lines = inp.read_text(encoding="utf-8", errors="replace").splitlines()

    # First pass: identify living person xrefs
    living_xrefs: set[str] = set()
    if filter_living:
        for i, line in enumerate(lines):
            stripped = line.strip()
            m = re.match(r"^0\s+(@\S+@)\s+INDI", stripped)
            if m:
                xref = m.group(1)
                if is_living(lines, i, cutoff_years):
                    living_xrefs.add(xref)

    # Second pass: filter output
    output: list[str] = []
    current_xref: str | None = None
    in_living_record = False
    skip_until_level0 = False

    for line in lines:
        stripped = line.strip()

        # Detect record boundaries
        if stripped.startswith("0 "):
            skip_until_level0 = False
            m = re.match(r"^0\s+(@\S+@)\s+INDI", stripped)
            if m:
                current_xref = m.group(1)
                in_living_record = current_xref in living_xrefs
                output.append(stripped)
                continue
            else:
                in_living_record = False
                current_xref = None
                output.append(stripped)
                continue

        if in_living_record:
            # Keep FAMC and FAMS links (relationships), SEX
            if stripped.startswith("1 FAMC") or stripped.startswith("1 FAMS") or stripped.startswith("1 SEX"):
                output.append(stripped)
            elif stripped.startswith("1 NAME"):
                surname = _extract_surname(lines, lines.index(f"0 {current_xref} INDI") if current_xref else 0)
                output.append(f"1 NAME Living /{surname}/")
            # Skip everything else (events, notes, sources) for living persons
            continue

        output.append(stripped)

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(output) + "\n", encoding="utf-8")

    return {
        "input": str(inp),
        "output": str(out),
        "total_individuals": sum(1 for l in lines if re.match(r"^0\s+@\S+@\s+INDI", l.strip())),
        "living_filtered": len(living_xrefs),
    }
