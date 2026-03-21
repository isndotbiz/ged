#!/usr/bin/env bash
set -euo pipefail

# Run from anywhere; resolve to this repo root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default input; allow override via first positional arg
INPUT_PATH_DEFAULT="$SCRIPT_DIR/0819.aggressive.ged"
INPUT_PATH="${1:-$INPUT_PATH_DEFAULT}"

if [[ ! -f "$INPUT_PATH" ]]; then
  echo "Input GEDCOM not found: $INPUT_PATH" >&2
  echo "Usage: $0 [optional_absolute_path_to_input.ged]" >&2
  exit 1
fi

# Expected layout
BACKUPS_DIR="$SCRIPT_DIR/backups"
OUT_DIR="$SCRIPT_DIR/out"
MAPS_DIR="$SCRIPT_DIR/maps"
MEDIA_DIR="$SCRIPT_DIR/media"
RULES_DIR="$SCRIPT_DIR/rules"

mkdir -p "$BACKUPS_DIR" "$OUT_DIR" "$MAPS_DIR" "$MEDIA_DIR" "$RULES_DIR"

# Placeholders for rules if user wants to customize later
[[ -f "$RULES_DIR/standard.yaml" ]] || cat > "$RULES_DIR/standard.yaml" <<'YAML'
# Custom rules placeholder (not consumed by current CLI). Keep for future use.
profile: standard
overrides: {}
YAML

[[ -f "$RULES_DIR/aggressive.yaml" ]] || cat > "$RULES_DIR/aggressive.yaml" <<'YAML'
# Custom rules placeholder (not consumed by current CLI). Keep for future use.
profile: aggressive
overrides: {}
YAML

# Output naming
input_filename="$(basename -- "$INPUT_PATH")"
input_stem="${input_filename%.*}"
OUT_GED="$OUT_DIR/${input_stem%.aggressive}.cleaned.final.ged"

# Prefer local source without requiring install
PYTHON_BIN="python3"
if command -v "$SCRIPT_DIR/venv/bin/python" >/dev/null 2>&1; then
  PYTHON_BIN="$SCRIPT_DIR/venv/bin/python"
fi

export PYTHONPATH="$SCRIPT_DIR:$PYTHONPATH"

echo "Fixing: $INPUT_PATH"
"$PYTHON_BIN" -m gedfix.cli fix "$INPUT_PATH" \
  --level aggressive \
  --out "$OUT_GED" \
  --backup-dir "$BACKUPS_DIR" \
  --no-dry-run | cat

# Copy a friendly-named backup alongside the .bak emitted by the CLI
backup_friendly="$BACKUPS_DIR/${input_stem}.backup.ged"
if [[ -f "$INPUT_PATH" ]]; then
  cp -f "$INPUT_PATH" "$backup_friendly"
fi

# Convert JSON report (emitted next to OUT_GED) to Markdown
OUT_JSON="${OUT_GED%.ged}.report.json"
OUT_MD="$OUT_DIR/scan_final_report.md"
"$PYTHON_BIN" -m gedfix.cli report "$OUT_JSON" --markdown "$OUT_MD" | cat

echo
echo "Done. Outputs:"
echo "- Cleaned GEDCOM: $OUT_GED"
echo "- Backup (tool):  $BACKUPS_DIR/${input_filename}.bak"
echo "- Backup (copy):  $backup_friendly"
echo "- Report (JSON):  $OUT_JSON"
echo "- Report (MD):    $OUT_MD"
echo
echo "If geocoding is needed, populate: $MAPS_DIR/geocoded_places.tsv"


