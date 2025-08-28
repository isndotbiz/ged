#!/usr/bin/env python3
"""Deduplicate redundant family links inside a GEDCOM file.

For INDI records: removes duplicate FAMC and FAMS lines (and their subtrees).
For FAM records: removes duplicate HUSB, WIFE, CHIL lines (and their subtrees).
Preserves all other data.

Usage: python remove_dup_links.py input.ged output.ged [--dry-run]
"""
import sys
from pathlib import Path

if len(sys.argv) < 3:
    print("Usage: python remove_dup_links.py <input.ged> <output.ged> [--dry-run]")
    sys.exit(1)

inp = Path(sys.argv[1])
out = Path(sys.argv[2])
dry_run = '--dry-run' in sys.argv

dup_count = 0

fin = inp.open('r', encoding='utf-8', errors='ignore')
if dry_run:
    fout = None
else:
    fout = out.open('w', encoding='utf-8')

with fin:
    if fout is not None:
        ctx = fout
    else:
        class _Dummy:
            def write(self, *_):
                pass
        ctx = _Dummy()
    fout_obj = ctx
    current_type = None  # 'INDI', 'FAM', or None
    links_seen = set()
    skip_mode = False
    skip_level = None

    for line in fin:
        # Get level
        try:
            level = int(line.split(' ', 1)[0])
        except ValueError:
            level = 0

        # Detect new 0-level record
        if level == 0:
            current_type = None
            links_seen.clear()
            skip_mode = False
            skip_level = None
            if ' INDI' in line:
                current_type = 'INDI'
            elif ' FAM' in line:
                current_type = 'FAM'
        if skip_mode:
            if level > skip_level:
                continue
            else:
                skip_mode = False
                skip_level = None
        if current_type == 'INDI' and level == 1 and (line.startswith('1 FAMC ') or line.startswith('1 FAMS ')):
            key = line.strip()
            if key in links_seen:
                dup_count += 1
                skip_mode = True
                skip_level = 1
                continue
            links_seen.add(key)
        elif current_type == 'FAM' and level == 1 and (line.startswith('1 HUSB ') or line.startswith('1 WIFE ') or line.startswith('1 CHIL ')):
            key = line.strip()
            if key in links_seen:
                dup_count += 1
                skip_mode = True
                skip_level = 1
                continue
            links_seen.add(key)
        # write out
        if fout_obj is not None:
            try:
                fout_obj.write(line)
            except AttributeError:
                pass

print(f"Duplicate links removed: {dup_count}")
