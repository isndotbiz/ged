from __future__ import annotations
import yaml, importlib.resources as res

def load_rules() -> dict:
    with res.files("gedfix").joinpath("rules.yaml").open("r", encoding="utf-8") as f:
        return yaml.safe_load(f)
