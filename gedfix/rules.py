from __future__ import annotations

from pathlib import Path
from typing import Optional, Dict, Any
import yaml


DEFAULT_RULES: Dict[str, Any] = {
    "note_prefix": "AutoFix:",
    "merge_thresholds": {"place_ratio": 92, "date_ratio": 95},
    "merge_allowed_tags": ["BIRT", "DEAT", "MARR"],
}


def load_rules(external_path: Optional[Path] = None) -> Dict[str, Any]:
    if external_path is not None:
        return _merge(DEFAULT_RULES, _read_yaml(external_path))
    
    # Try to load rules.yaml from the same directory as this file
    rules_path = Path(__file__).resolve().parent / "rules.yaml"
    if rules_path.exists():
        return _merge(DEFAULT_RULES, _read_yaml(rules_path))
    
    return dict(DEFAULT_RULES)


def _read_yaml(path: Path) -> Dict[str, Any]:
    try:
        return yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    except Exception:
        return {}


def _merge(a: Dict[str, Any], b: Dict[str, Any]) -> Dict[str, Any]:
    out = dict(a)
    for k, v in b.items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = _merge(out[k], v)
        else:
            out[k] = v
    return out


