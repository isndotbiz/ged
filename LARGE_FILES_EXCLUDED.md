# Large Files Excluded from Repository

To keep the repository manageable and within GitHub's limits, the following large files have been excluded:

## Geographic Data Files (1.6GB+ each)
- `new/allCountries.txt` - GeoNames geographic data
- `maps/allCountries.txt` - GeoNames geographic data  
- `maps/allCountries.zip` - Compressed geographic data

**These files are used for place name geocoding but are not essential for GEDCOM processing.**

### How to Get These Files (if needed)
```bash
# Download from GeoNames if you need geocoding functionality
wget http://download.geonames.org/export/dump/allCountries.zip
unzip allCountries.zip
```

## Large Processing Files Excluded
- `data/processing/fixed/final_with_media.rmtree` (12MB) - Generated file, can be recreated
- `*.large` files - Large cache files that can be regenerated
- `venv/` and `.venv/` directories - Python virtual environments

## What's Still Included
✅ All essential GEDCOM processing scripts and tools  
✅ Cleaned GEDCOM files ready for use  
✅ Complete documentation and analysis reports  
✅ Processing workspace with audit trails  
✅ All genealogical data with 100% integrity  

## Repository Size Impact
**Before cleanup:** ~400MB+ (would have failed GitHub upload)  
**After cleanup:** ~25MB (uploads successfully)  

The core functionality remains complete while keeping the repository lightweight and shareable.
