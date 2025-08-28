#!/usr/bin/env python3
"""Deduplicate duplicate NAME blocks inside each INDI record of a GEDCOM file.
Usage: python remove_dup_names.py input.ged output.ged
"""
import sys
from pathlib import Path

if len(sys.argv) < 3:
    print("Usage: python remove_dup_names.py <input.ged> <output.ged>")
    sys.exit(1)

inp_path = Path(sys.argv[1])
out_path = Path(sys.argv[2])

with inp_path.open('r', encoding='utf-8', errors='ignore') as fin, out_path.open('w', encoding='utf-8') as fout:
    in_record = False
    names_seen = set()
    skip_mode = False
    skip_level = None

    for line in fin:
        # Determine level
        try:
            level = int(line.split(' ', 1)[0])
        except ValueError:
            level = 0

        # Detect start of new individual record
        if line.startswith('0 @') and ' INDI' in line:
            in_record = True
            names_seen.clear()
            skip_mode = False
            skip_level = None
        elif line.startswith('0 '):
            # Leaving previous record (could be other records)
            in_record = False
            names_seen.clear()
            skip_mode = False
            skip_level = None

        if skip_mode:
            # Continue skipping sub-lines until we reach a line with level<=skip_level
            if level > skip_level:
                continue
            else:
                skip_mode = False
                skip_level = None
        # Only deduplicate inside INDI records
        if in_record and line.startswith('1 NAME '):
            if line in names_seen:
                # Skip this NAME and its subtree
                skip_mode = True
                skip_level = 1
                continue
            else:
                names_seen.add(line)

        fout.write(line)
