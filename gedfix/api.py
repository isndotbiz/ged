from __future__ import annotations

from dataclasses import dataclass, asdict
from typing import Dict, List, Optional, Tuple
from pathlib import Path
import json

from .dates import normalize_gedcom_date_safe, DateValidationResult
from .fixer import fix_gedcom_text
from .rules import load_rules
from .report import summarize_report
from .io_utils import read_text_preserve_encoding


@dataclass
class Issue:
    rule: str
    message: str
    count: int = 1


def scan_file(path: Path) -> Dict:
    text, encoding = read_text_preserve_encoding(path)
    rules = load_rules()

    # Very light scanner: count suspicious DATE tokens and non-GEDCOM dates
    issues: List[Issue] = []

    for line in text.splitlines():
        # Heuristic: find DATE lines of form "n DATE value"
        if " DATE " in line:
            parts = line.split(" DATE ", 1)
            date_value = parts[1].strip()
            result: DateValidationResult = normalize_gedcom_date_safe(date_value)
            if not result.is_valid:
                issues.append(Issue(
                    rule="unrecognized_date",
                    message=f"Unrecognized DATE '{date_value}'",
                ))

    summary = summarize_report([asdict(i) for i in issues])
    return {
        "file": str(path),
        "encoding": encoding,
        "issues": [asdict(i) for i in issues],
        "summary": summary,
        "rules": rules,
    }


def fix_file(path: Path, level: str = "standard", dry_run: bool = True) -> Tuple[Dict, Optional[str]]:
    text, encoding = read_text_preserve_encoding(path)
    rules = load_rules()
    fixed_text, changes = fix_gedcom_text(text, level=level, rules=rules)

    report = {
        "file": str(path),
        "level": level,
        "encoding": encoding,
        "changes": changes,
        "summary": summarize_report(changes),
    }

    return report, (None if dry_run else fixed_text)


def write_markdown_report(data: Dict) -> str:
    lines: List[str] = []
    lines.append("# gedfix report")
    if "file" in data:
        lines.append(f"File: {data['file']}")
    if "level" in data:
        lines.append(f"Level: {data['level']}")
    lines.append("")
    lines.append("## Summary")
    summary = data.get("summary", {})
    for key, value in summary.items():
        lines.append(f"- {key}: {value}")
    lines.append("")
    changes = data.get("changes") or data.get("issues") or []
    if changes:
        lines.append("## Details")
        for change in changes:
            rule = change.get("rule", "-")
            message = change.get("message", "")
            before = change.get("before")
            after = change.get("after")
            if before or after:
                lines.append(f"- {rule}: {message} | before: {before!r} after: {after!r}")
            else:
                lines.append(f"- {rule}: {message}")
    return "\n".join(lines) + "\n"


