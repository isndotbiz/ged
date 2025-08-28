#!/usr/bin/env python3
"""
Quick script to start manual review of genealogical issues
"""
import sys
from pathlib import Path

# Add current directory to path
sys.path.append('.')

from gedfix.manual_review import ManualReviewSession

def main():
    # Your best GEDCOM file
    best_file = Path('data/processing/fixed/final_comprehensive_clean.ged')
    
    if not best_file.exists():
        print(f"❌ File not found: {best_file}")
        print("Available .ged files in data/processing/fixed/:")
        for f in Path('data/processing/fixed/').glob('*.ged'):
            print(f"   {f.name}")
        return
    
    print(f"🔍 Starting manual review with: {best_file.name}")
    print(f"📊 File size: {best_file.stat().st_size / 1024 / 1024:.1f}MB")
    
    # Create session
    session = ManualReviewSession(best_file)
    print(f"✅ Loaded {len(session.people)} individuals, {len(session.families)} families")
    
    # Load manual review requirements
    md_file = Path('data/processing/reports/MANUAL_REVIEW_REQUIRED.md')
    if md_file.exists():
        session.parse_manual_review_md(md_file)
        print(f"📋 Loaded {len(session.issues)} issues for review")
    
    if session.issues:
        print(f"\n🎯 Issues breakdown:")
        categories = {}
        for issue in session.issues:
            cat = getattr(issue, 'category', 'Unknown')
            categories[cat] = categories.get(cat, 0) + 1
        
        for cat, count in categories.items():
            print(f"   • {cat}: {count} issues")
        
        print(f"\n🚀 Starting interactive review...")
        session.run_interactive_session()
    else:
        print("⚠️  No issues found for manual review")
        print("Check that MANUAL_REVIEW_REQUIRED.md exists and has content")

if __name__ == "__main__":
    main()
