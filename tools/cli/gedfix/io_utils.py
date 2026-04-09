from __future__ import annotations
from pathlib import Path
import shutil, time

def timestamp() -> str:
    return time.strftime("%Y%m%d-%H%M%S")

def backup_file(src: Path, backup_dir: Path) -> Path:
    backup_dir.mkdir(parents=True, exist_ok=True)
    dest = backup_dir / f"{src.stem}.{timestamp()}{src.suffix}"
    shutil.copy2(src, dest)
    return dest
