#!/bin/bash
set -euo pipefail

echo "🌳 Starting Final GEDCOM cleanup..."

# === CONFIGURATION ===
ROOT_DIR="$HOME/Projects/ged"
SRC_GED="$ROOT_DIR/0819.aggressive.ged"
BACKUP_DIR="$ROOT_DIR/out/backup"
OUT_DIR="$ROOT_DIR/out"
FINAL_OUT="$OUT_DIR/0819.final.ged"

# Activate virtual environment
cd "$ROOT_DIR"
source ./venv/bin/activate

# === Step 1: Create backup ===
mkdir -p "$BACKUP_DIR"
cp "$SRC_GED" "$BACKUP_DIR/0819.original.backup.ged"
echo "✅ Backup created: $BACKUP_DIR/0819.original.backup.ged"

# === Step 2: Use existing aggressive fix ===
echo "✅ Using existing aggressive fix: $SRC_GED"

# === Step 3: Copy aggressive fix as final version ===
cp "$SRC_GED" "$FINAL_OUT"
echo "📤 Final file ready: $FINAL_OUT"

# === Step 4: Generate final reports ===
python -m gedfix.cli scan "$FINAL_OUT" --report "$OUT_DIR/final_scan_report.json"
echo "📊 Final scan report: $OUT_DIR/final_scan_report.json"

# === Step 5: Generate platform-specific summary ===
echo "📋 Generating platform upload summary..."

cat > "$OUT_DIR/platform_upload_guide.md" << 'EOF'
# Platform Upload Guide

## 📁 Files Ready for Upload

**Primary File:** `0819.final.ged`
- Size: Cleaned and optimized
- Issues: See final_scan_report.json for remaining items

## 🏢 Platform-Specific Instructions

### MyHeritage
- Upload: `0819.final.ged`
- Run their Consistency Checker after import
- Review any new issues flagged by their system

### Ancestry.com  
- Upload: `0819.final.ged`
- Note: Ancestry may modify place names during import
- Verify media references if any exist

### RootsMagic
- Import: `0819.final.ged`
- Check that all sources and citations imported correctly
- Verify family relationships are intact

### Family Tree Maker
- Import: `0819.final.ged` 
- Best platform for preserving all GEDCOM structure
- Check media paths and sources

## 🔍 Post-Import Checklist

1. **Individual Count:** Verify same number of people imported
2. **Family Relationships:** Spot-check parent-child relationships  
3. **Dates:** Look for any date formatting changes
4. **Places:** Check if platform normalized place names
5. **Sources:** Verify sources and citations are intact
6. **Media:** Confirm any media files are linked properly

## 📊 Known Issues (from scan)

Check `final_scan_report.json` for:
- Unrecognized date formats (Y, N, 1522/25)
- Any remaining data inconsistencies

## 🔄 Round-Trip Testing

After upload, download the file back from each platform to compare:
```bash
# Compare with original
gedfix scan downloaded_file.ged --report platform_comparison.json
```
EOF

echo "📋 Platform guide created: $OUT_DIR/platform_upload_guide.md"

# === Step 6: Show summary ===
echo ""
echo "🎉 GEDCOM Cleanup Complete!"
echo ""
echo "📁 Files created:"
echo "   Primary: $FINAL_OUT"
echo "   Report:  $OUT_DIR/final_scan_report.json"
echo "   Guide:   $OUT_DIR/platform_upload_guide.md"
echo ""
echo "📤 Ready for upload to:"
echo "   • MyHeritage"
echo "   • Ancestry.com" 
echo "   • RootsMagic"
echo "   • Family Tree Maker"
echo ""
echo "👉 Next: Review the platform guide and start uploading!"
