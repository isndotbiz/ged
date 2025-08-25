from __future__ import annotations

from pathlib import Path
from typing import Tuple


def read_text_preserve_encoding(path: Path) -> Tuple[str, str]:
    """Try common encodings first, then fallback with replacement."""
    for enc in ("utf-8-sig", "utf-8", "cp1252", "latin-1"):
        try:
            return path.read_text(encoding=enc), enc
        except UnicodeDecodeError:
            continue
    data = path.read_bytes()
    return data.decode("latin-1", errors="replace"), "latin-1"


