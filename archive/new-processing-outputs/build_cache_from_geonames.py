#!/usr/bin/env python3
"""Create places_cache.csv for GEDCOM from GeoNames allCountries.txt.

Reads a list of unresolved places (one per line) and looks for exact
matches (case-insensitive, compares only the first place-name token
before the first comma) in allCountries.txt.  Writes places_cache.csv
with place string → lat/long.
"""
import argparse
from pathlib import Path
import csv

parser = argparse.ArgumentParser()
parser.add_argument('--geonames', required=True, help='allCountries.txt path')
parser.add_argument('--unresolved', required=True, help='unresolved_places.txt')
parser.add_argument('--cache', required=True, help='places_cache.csv to append')
args = parser.parse_args()

gn_path = Path(args.geonames)
if not gn_path.exists():
    print('GeoNames file not found')
    exit(1)

with open(args.unresolved, encoding='utf-8') as f:
    target_places = [line.strip() for line in f if line.strip()]

# map key token.lower() -> list of full place strings that start with that token
key_to_places = {}
for p in target_places:
    key = p.split(',')[0].strip().lower()
    key_to_places.setdefault(key, []).append(p)

found = {}
with open(gn_path, encoding='utf-8') as gn:
    for line in gn:
        parts = line.split('\t')
        if len(parts) < 5:
            continue
        name = parts[1].strip() or parts[0].strip()
        key = name.lower()
        if key in key_to_places:
            lat = parts[4]
            lon = parts[5]
            for original in key_to_places[key]:
                if original not in found:
                    found[original] = (lat, lon)
            if len(found) == len(target_places):
                break
print(f"Matched {len(found)}/{len(target_places)} places via GeoNames exact token match")

# append to cache
with open(args.cache, 'a', newline='', encoding='utf-8') as fh:
    w = csv.writer(fh)
    for place, (lat, lon) in found.items():
        w.writerow([place, lat, lon])
