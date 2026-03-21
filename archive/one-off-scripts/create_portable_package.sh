#!/bin/bash
# Create Portable Family Tree Package
# This creates a complete package with GEDCOM + media files for sharing with family

echo "🚀 Creating Portable Mallinger Family Tree Package"
echo "================================================="

# Set variables
PACKAGE_DIR="$HOME/Desktop/mallinger_family_tree_complete"
MEDIA_SOURCE="/Users/jonathanmallinger/Documents/mallinger_media"
GEDCOM_SOURCE="data/processing/exports/FINAL_CLEANED_WITH_MEDIA_master_geo_media_20250828.ged"

# Create package directory
echo "📁 Creating package directory..."
mkdir -p "$PACKAGE_DIR/media"
mkdir -p "$PACKAGE_DIR/documentation"

# Copy media files
echo "📸 Copying media files..."
if [ -d "$MEDIA_SOURCE" ]; then
    cp -r "$MEDIA_SOURCE"/* "$PACKAGE_DIR/media/"
    echo "✅ Copied $(find "$PACKAGE_DIR/media" -type f | wc -l) media files"
else
    echo "❌ Media source folder not found: $MEDIA_SOURCE"
    exit 1
fi

# Copy GEDCOM file
echo "📊 Copying GEDCOM file..."
if [ -f "$GEDCOM_SOURCE" ]; then
    cp "$GEDCOM_SOURCE" "$PACKAGE_DIR/mallinger_family_tree.ged"
    echo "✅ GEDCOM file copied"
else
    echo "❌ GEDCOM file not found: $GEDCOM_SOURCE"
    exit 1
fi

# Copy documentation
echo "📚 Copying documentation..."
cp data/processing/exports/media_suggestions.txt "$PACKAGE_DIR/documentation/"
cp data/processing/reports/manual_review_required.md "$PACKAGE_DIR/documentation/"
cp GENEALOGY_SOFTWARE_WORKFLOW.md "$PACKAGE_DIR/documentation/"
cp PROJECT_COMPLETE.md "$PACKAGE_DIR/documentation/"

# Create README for the package
cat > "$PACKAGE_DIR/README.md" << 'EOF'
# Mallinger Family Tree - Complete Package

## 📁 Contents

- **mallinger_family_tree.ged** - Complete family tree with 1,401 individuals and 584 media references
- **media/** - All 584 family photos and documents  
- **documentation/** - Guides for importing and using the data

## 🚀 Getting Started

1. **Choose your genealogy software** (RootsMagic, Family Tree Maker, etc.)
2. **Import the GEDCOM file:** mallinger_family_tree.ged
3. **Set media folder path:** Point to the media/ folder in this package
4. **Follow the workflow guide:** See documentation/GENEALOGY_SOFTWARE_WORKFLOW.md

## 📊 What's Included

- ✅ 1,401 individuals with complete genealogical data
- ✅ 600 families with all relationships preserved  
- ✅ 295 sources with citations and references
- ✅ 584 media files (photos, documents, certificates)
- ✅ Professional data integrity - no information lost
- ✅ 2,272 date fixes with AutoFix preservation notes
- ✅ Complete documentation and setup guides

## 📋 Next Steps

1. **Import into genealogy software** using the workflow guide
2. **Review manual items** in documentation/manual_review_required.md
3. **Link photos to people** using documentation/media_suggestions.txt
4. **Share with family** - this package contains everything needed

**🌳 Happy genealogy research! Your family history is preserved and ready to explore.**
EOF

# Create ZIP package
echo "📦 Creating ZIP package..."
cd "$HOME/Desktop"
zip -r mallinger_family_tree_complete.zip mallinger_family_tree_complete/

# Calculate sizes
PACKAGE_SIZE=$(du -sh mallinger_family_tree_complete | cut -f1)
ZIP_SIZE=$(du -sh mallinger_family_tree_complete.zip | cut -f1)

# Final summary
echo ""
echo "✅ PACKAGE CREATION COMPLETE!"
echo "============================="
echo "📦 Package location: $HOME/Desktop/mallinger_family_tree_complete"
echo "🗜️  ZIP file: $HOME/Desktop/mallinger_family_tree_complete.zip"
echo "📏 Package size: $PACKAGE_SIZE"
echo "📏 ZIP size: $ZIP_SIZE"
echo ""
echo "🎯 Ready to share with family members!"
echo "   - They can download the ZIP file"
echo "   - Extract it anywhere on their computer"  
echo "   - Follow the README.md instructions"
echo "   - Import into their preferred genealogy software"
echo ""
echo "📧 Share via:"
echo "   - Email (if under 25MB)"
echo "   - Google Drive, iCloud, or Dropbox"
echo "   - USB drive for in-person sharing"
echo ""
echo "🌳 Your complete family tree is now portable!"
