#!/usr/bin/env python3
"""Offline-friendly place geocoder for GEDCOM.

If --offline is given, the script only uses an existing CSV cache that
maps place strings to latitude/longitude. Missing entries are reported
but not queried from the internet.

Cache CSV format: place,lat,long
"""
import csv
import argparse
from pathlib import Path
import sys
import time
try:
    from geopy.geocoders import Nominatim
except ImportError:
    Nominatim = None

def load_cache(path: Path):
    cache = {}
    if path.exists():
        with path.open(newline='', encoding='utf-8') as fh:
            for row in csv.reader(fh):
                if len(row) == 3:
                    place, lat, lon = row
                    cache[place] = (lat, lon)
    return cache

def save_cache(path: Path, cache: dict):
    with path.open('w', newline='', encoding='utf-8') as fh:
        w = csv.writer(fh)
        for place, (lat, lon) in sorted(cache.items()):
            w.writerow([place, lat, lon])

def extract_places(ged_lines):
    places = set()
    for line in ged_lines:
        if ' PLAC ' in line:
            places.add(line.split(' PLAC ',1)[1].strip())
    return places

def add_map_blocks(lines, cache):
    out = []
    i = 0
    total = len(lines)
    while i < total:
        line = lines[i]
        out.append(line)
        if ' PLAC ' in line:
            place = line.split(' PLAC ',1)[1].strip()
            if place in cache:
                lat, lon = cache[place]
                # check if next line already MAP
                if i+1 < total and lines[i+1].startswith('3 MAP'):
                    i += 1
                    continue
                out.append('3 MAP\n')
                out.append(f'4 LATI {lat}\n')
                out.append(f'4 LONG {lon}\n')
        i += 1
    return out

def main():
    parser = argparse.ArgumentParser(description='Add MAP blocks to GEDCOM using cache/Nominatim')
    parser.add_argument('--input', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--cache', required=True)
    parser.add_argument('--unresolved', required=True)
    parser.add_argument('--offline', action='store_true')
    args = parser.parse_args()

    inp = Path(args.input)
    outp = Path(args.output)
    cache_path = Path(args.cache)

    lines = inp.read_text(encoding='utf-8', errors='ignore').splitlines(keepends=True)
    places = extract_places(lines)

    cache = load_cache(cache_path)
    geocoder = None
    if not args.offline and Nominatim is not None:
        geocoder = Nominatim(user_agent='gedcom_geocoder')

    unresolved = []
    for p in sorted(places):
        if p in cache:
            continue
        if args.offline or geocoder is None:
            unresolved.append(p)
            continue
        try:
            loc = geocoder.geocode(p, timeout=10)
            if loc:
                cache[p] = (f"{loc.latitude:+.4f}", f"{loc.longitude:+.4f}")
                time.sleep(1)
            else:
                unresolved.append(p)
        except Exception:
            unresolved.append(p)
    # save cache
    save_cache(cache_path, cache)

    new_lines = add_map_blocks(lines, cache)
    outp.write_text(''.join(new_lines), encoding='utf-8')

    Path(args.unresolved).write_text('\n'.join(unresolved), encoding='utf-8')
    print(f'✅ MAP blocks added. unresolved: {len(unresolved)} / {len(places)}')

if __name__ == '__main__':
    main()
