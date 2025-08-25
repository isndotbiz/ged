from __future__ import annotations
import re

GEDCOM_MONTHS = {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"}
MONTH_NAMES = {
    "JANUARY": "JAN", "FEBRUARY": "FEB", "MARCH": "MAR", "APRIL": "APR",
    "MAY": "MAY", "JUNE": "JUN", "JULY": "JUL", "AUGUST": "AUG",
    "SEPTEMBER": "SEP", "OCTOBER": "OCT", "NOVEMBER": "NOV", "DECEMBER": "DEC",
    "SEPT": "SEP", "JAN.": "JAN", "FEB.": "FEB", "MAR.": "MAR", "APR.": "APR",
    "JUN.": "JUN", "JUL.": "JUL", "AUG.": "AUG", "SEP.": "SEP", "SEPT.": "SEP",
    "OCT.": "OCT", "NOV.": "NOV", "DEC.": "DEC"
}
QUALIFIERS = {"ABT","EST","CAL","INT","BEF","AFT","BET","FROM","TO","AND"}
CIRCA_TERMS = {"CIRCA": "ABT", "ABOUT": "ABT", "C.": "ABT", "CA.": "ABT", "APPROX": "ABT"}

_ws = r"[ \t]+"
_token = r"[A-Z0-9][A-Z0-9\-]*"
_day  = r"(0?[1-9]|[12][0-9]|3[01])"
_year = r"([12][0-9]{3}|\d{1,3})"  # allow partial years; we won't reinterpret
_mon  = r"(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)"
# Patterns we consider GEDCOM-safe (do not change semantics, only case/space)
_PATTERNS = [
    re.compile(rf"^{_day}{_ws}{_mon}{_ws}{_year}$"),
    re.compile(rf"^{_mon}{_ws}{_year}$"),
    re.compile(rf"^{_year}$"),
    re.compile(rf"^(ABT|EST|CAL|INT){_ws}{_year}(\s*\(.+\))?$"),
    re.compile(rf"^(ABT|EST|CAL|INT){_ws}{_mon}{_ws}{_year}$"),
    re.compile(rf"^(ABT|EST|CAL|INT){_ws}{_day}{_ws}{_mon}{_ws}{_year}$"),
    re.compile(rf"^(BEF|AFT){_ws}{_year}$"),
    re.compile(rf"^(BEF|AFT){_ws}{_mon}{_ws}{_year}$"),
    re.compile(rf"^(BEF|AFT){_ws}{_day}{_ws}{_mon}{_ws}{_year}$"),
    re.compile(rf"^BET{_ws}{_year}{_ws}AND{_ws}{_year}$"),
    re.compile(rf"^FROM{_ws}{_year}{_ws}TO{_ws}{_year}$"),
]

def normalize_spaces_and_case(raw: str) -> str:
    s = " ".join(raw.strip().split())
    # Uppercase tokens; preserve parentheses content for INT
    parts = []
    buf = ""
    paren = False
    for ch in s:
        if ch == "(":
            paren = True
        if ch == ")":
            paren = False
        buf += ch
    # Simple uppercasing outside parens
    out = []
    token = ""
    in_paren = False
    for ch in s:
        if ch == "(":
            in_paren = True
            if token:
                out.append(token.upper())
                token = ""
            out.append("(")
            continue
        if ch == ")":
            in_paren = False
            if token:
                out.append(token.upper())
                token = ""
            out.append(")")
            continue
        if ch == " ":
            if token:
                out.append(token.upper() if not in_paren else token)
                token = ""
            out.append(" ")
            continue
        token += ch
    if token:
        out.append(token.upper() if not in_paren else token)
    # collapse spaces again
    return " ".join("".join(out).split())

def is_gedcom_safe(s: str) -> bool:
    S = s.strip()
    for p in _PATTERNS:
        if p.match(S):
            return True
    return False

def convert_month_names(text: str) -> str:
    """Convert full month names and abbreviations to GEDCOM standard."""
    words = text.upper().split()
    converted = []
    for word in words:
        # Remove trailing punctuation for lookup
        clean_word = word.rstrip('.,;:')
        
        if clean_word in MONTH_NAMES:
            converted.append(MONTH_NAMES[clean_word])
        elif clean_word in CIRCA_TERMS:
            converted.append(CIRCA_TERMS[clean_word])
        else:
            converted.append(word)
    return ' '.join(converted)

def sanitize_date_value(raw: str, level: str = "standard") -> tuple[str, list[str]]:
    """
    Returns (possibly-normalized-value, notes[])
    Levels:
    - standard: Only normalize spacing/case on recognized patterns
    - aggressive: Convert month names and circa terms
    - comprehensive: Apply all safe transformations
    """
    notes: list[str] = []
    original = raw
    
    # First normalize spaces and case
    norm = normalize_spaces_and_case(raw)
    
    # For aggressive+ levels, try converting month names and circa terms
    if level in ["aggressive", "ultra", "comprehensive"]:
        converted = convert_month_names(norm)
        # Re-normalize after conversion
        converted = normalize_spaces_and_case(converted)
        
        if is_gedcom_safe(converted):
            if converted != original:
                notes.append(f"Date standardized from '{original}' to GEDCOM format.")
            return converted, notes
        
        # If conversion didn't work, fall back to original normalization
        norm = normalize_spaces_and_case(raw)
    
    # Check if basic normalization is GEDCOM-safe
    if is_gedcom_safe(norm):
        return norm, notes
    
    # Not GEDCOM-safe: preserve original, add AutoFix note
    return raw, ["Unrecognized or non-GEDCOM date preserved."]
