from __future__ import annotations

from dataclasses import dataclass
import re
from typing import Optional


GEDCOM_MONTHS = {
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
    "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
}


DATE_PREFIXES = {"ABT", "EST", "CAL", "INT", "BEF", "AFT", "BET", "FROM", "TO"}


@dataclass
class DateValidationResult:
    is_valid: bool
    normalized: Optional[str]
    reason: Optional[str] = None


_SPACE_RE = re.compile(r"\s+")


def _collapse_spaces(value: str) -> str:
    return _SPACE_RE.sub(" ", value.strip())


def _uppercase_month_tokens(value: str) -> str:
    tokens = value.split(" ")
    out = []
    for tok in tokens:
        if tok.upper() in GEDCOM_MONTHS:
            out.append(tok.upper())
        else:
            out.append(tok)
    return " ".join(out)


_YEAR_RE = re.compile(r"^[0-9]{3,4}$")
_DAY_RE = re.compile(r"^[0-9]{1,2}$")


def _is_year(token: str) -> bool:
    return bool(_YEAR_RE.match(token))


def _is_day(token: str) -> bool:
    try:
        if not _DAY_RE.match(token):
            return False
        day = int(token)
        return 1 <= day <= 31
    except ValueError:
        return False


def _validate_simple_date(tokens: list[str]) -> bool:
    # Forms: YYYY | MON YYYY | DD MON YYYY
    if len(tokens) == 1 and _is_year(tokens[0]):
        return True
    if len(tokens) == 2 and tokens[0].upper() in GEDCOM_MONTHS and _is_year(tokens[1]):
        return True
    if (
        len(tokens) == 3
        and _is_day(tokens[0])
        and tokens[1].upper() in GEDCOM_MONTHS
        and _is_year(tokens[2])
    ):
        return True
    return False


def _validate_ranged(prefix_left: str, middle_tokens: list[str], joiner: str, right_tokens: list[str]) -> bool:
    # Handles: BET <date> AND <date> ; FROM <date> TO <date>
    if prefix_left == "BET" and joiner == "AND":
        return _validate_simple_date(middle_tokens) and _validate_simple_date(right_tokens)
    if prefix_left == "FROM" and joiner == "TO":
        return _validate_simple_date(middle_tokens) and _validate_simple_date(right_tokens)
    return False


def normalize_gedcom_date_safe(value: str) -> DateValidationResult:
    # Collapse whitespace and uppercase month tokens only
    collapsed = _collapse_spaces(value)
    month_up = _uppercase_month_tokens(collapsed)

    tokens = month_up.split(" ") if month_up else []
    if not tokens:
        return DateValidationResult(is_valid=False, normalized=month_up, reason="empty")

    # Allow prefixes ABT/EST/CAL/INT/BEF/AFT
    first = tokens[0].upper()
    if first in {"ABT", "EST", "CAL", "INT", "BEF", "AFT"}:
        if _validate_simple_date(tokens[1:]):
            return DateValidationResult(is_valid=True, normalized=month_up)
        return DateValidationResult(is_valid=False, normalized=month_up, reason="invalid_after_prefix")

    # Ranged forms
    if first in {"BET", "FROM"}:
        # Find joiner
        try:
            if first == "BET":
                join_index = tokens.index("AND", 1)
                left = tokens[1:join_index]
                right = tokens[join_index + 1 :]
                ok = _validate_ranged("BET", left, "AND", right)
            else:
                join_index = tokens.index("TO", 1)
                left = tokens[1:join_index]
                right = tokens[join_index + 1 :]
                ok = _validate_ranged("FROM", left, "TO", right)
            if ok:
                return DateValidationResult(is_valid=True, normalized=month_up)
            return DateValidationResult(is_valid=False, normalized=month_up, reason="invalid_range")
        except ValueError:
            return DateValidationResult(is_valid=False, normalized=month_up, reason="missing_joiner")

    # Simple forms
    if _validate_simple_date(tokens):
        return DateValidationResult(is_valid=True, normalized=month_up)

    # If contains slashes or hyphenated numeric patterns, classify as unrecognized but preserved
    if re.search(r"[0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4}", value) or re.search(r"^[0-9]{4}-[0-9]{2}-[0-9]{2}$", value):
        return DateValidationResult(is_valid=False, normalized=month_up, reason="non_gedcom_freeform")

    # Any other unknown
    return DateValidationResult(is_valid=False, normalized=month_up, reason="unrecognized")


