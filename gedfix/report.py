from __future__ import annotations

from collections import Counter
from typing import Iterable, Dict, Any


def summarize_report(items: Iterable[Dict[str, Any]]) -> Dict[str, int]:
    """Items may be issues from scan or changes from fix."""
    rules = [i.get("rule", "unknown") for i in items]
    counts = Counter(rules)
    out = {"total": sum(counts.values())}
    for k, v in counts.items():
        out[f"rule:{k}"] = v
    return out


