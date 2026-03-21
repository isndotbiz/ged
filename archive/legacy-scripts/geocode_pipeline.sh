#!/bin/bash
set -euo pipefail

# === CONFIG ===
MAP_DIR="$HOME/Projects/ged/maps"
PY_MATCHER="$MAP_DIR/build_geocoded_places.py"

mkdir -p "$MAP_DIR"

# === STEP 1: Download GeoNames allCountries.txt if needed ===
if [[ ! -f "$MAP_DIR/allCountries.zip" ]]; then
  echo "⬇️ Downloading GeoNames data..."
  curl -o "$MAP_DIR/allCountries.zip" http://download.geonames.org/export/dump/allCountries.zip
fi

# === STEP 2: Extract + filter first 100K entries (place, lat, lon) ===
echo "📦 Extracting and processing GeoNames..."
unzip -o "$MAP_DIR/allCountries.zip" -d "$MAP_DIR"
cut -f2,5,6 "$MAP_DIR/allCountries.txt" | head -n 100000 > "$MAP_DIR/geonames_lite.tsv"
echo "✅ Created GeoNames Lite: $MAP_DIR/geonames_lite.tsv"

# === STEP 3: Run Python fuzzy matcher ===
python3 "$PY_MATCHER"
