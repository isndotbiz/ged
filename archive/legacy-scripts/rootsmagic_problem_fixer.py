#!/usr/bin/env python3
"""
RootsMagic Problem List to Command-Line Fixer

This script takes problem reports from RootsMagic and converts them
into targeted command-line fixes for your GEDCOM file.

Usage:
1. In RootsMagic, go to Tools > Problem List
2. Export the problem list to CSV or copy the text
3. Run this script to generate specific fixes
4. Apply the fixes to your GEDCOM
"""

import argparse
import csv
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple, Any
from collections import defaultdict

class RootsMagicProblemProcessor:
    """Process RootsMagic problem lists and generate command-line fixes."""
    
    def __init__(self):
        self.problems: List[Dict] = []
        self.fix_commands: List[str] = []
        self.statistics = defaultdict(int)
    
    def load_problem_list(self, source: Path, format_type: str = 'auto') -> int:
        """Load problems from RootsMagic export."""
        print(f"📋 Loading problem list from {source}")
        
        if format_type == 'auto':
            format_type = 'csv' if source.suffix.lower() == '.csv' else 'text'
        
        if format_type == 'csv':
            return self._load_csv_problems(source)
        else:
            return self._load_text_problems(source)
    
    def _load_csv_problems(self, source: Path) -> int:
        """Load from CSV export."""
        count = 0
        with open(source, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            for row in reader:
                problem = {
                    'type': row.get('Problem Type', ''),
                    'person': row.get('Person', ''),
                    'description': row.get('Description', ''),
                    'details': row.get('Details', ''),
                    'id': row.get('ID', ''),
                    'source_file': str(source)
                }
                self.problems.append(problem)
                self.statistics[problem['type']] += 1
                count += 1
        
        print(f"✅ Loaded {count} problems from CSV")
        return count
    
    def _load_text_problems(self, source: Path) -> int:
        """Load from text export or manual paste."""
        count = 0
        with open(source, 'r', encoding='utf-8') as f:
            current_problem = {}
            
            for line in f:
                line = line.strip()
                if not line:
                    if current_problem:
                        self.problems.append(current_problem)
                        self.statistics[current_problem.get('type', 'Unknown')] += 1
                        count += 1
                        current_problem = {}
                    continue
                
                # Parse different line formats from RootsMagic
                if line.startswith('Problem:'):
                    current_problem['type'] = line.replace('Problem:', '').strip()
                elif line.startswith('Person:'):
                    current_problem['person'] = line.replace('Person:', '').strip()
                elif line.startswith('Description:'):
                    current_problem['description'] = line.replace('Description:', '').strip()
                elif line.startswith('ID:'):
                    current_problem['id'] = line.replace('ID:', '').strip()
                else:
                    # Assume it's part of description or details
                    if 'details' not in current_problem:
                        current_problem['details'] = line
                    else:
                        current_problem['details'] += ' ' + line
            
            # Handle last problem
            if current_problem:
                self.problems.append(current_problem)
                self.statistics[current_problem.get('type', 'Unknown')] += 1
                count += 1
        
        print(f"✅ Loaded {count} problems from text")
        return count
    
    def analyze_problems(self) -> Dict:
        """Analyze loaded problems and categorize them."""
        print("🔍 Analyzing problems...")
        
        analysis = {
            'total_problems': len(self.problems),
            'categories': dict(self.statistics),
            'fixable_automatically': 0,
            'requires_manual_review': 0,
            'data_quality_issues': 0,
            'structural_issues': 0
        }
        
        fixable_types = {
            'duplicate person', 'missing birth date', 'missing death date',
            'inconsistent name spelling', 'missing marriage date',
            'birth after death', 'marriage before birth',
            'child born before parent', 'child born after parent death'
        }
        
        review_types = {
            'possible duplicate', 'unusual age difference',
            'missing parents', 'missing spouse'
        }
        
        for problem in self.problems:
            problem_type = problem.get('type', '').lower()
            
            if any(fix_type in problem_type for fix_type in fixable_types):
                analysis['fixable_automatically'] += 1
            elif any(review_type in problem_type for review_type in review_types):
                analysis['requires_manual_review'] += 1
            
            if any(word in problem_type for word in ['missing', 'incomplete', 'blank']):
                analysis['data_quality_issues'] += 1
            elif any(word in problem_type for word in ['duplicate', 'inconsistent']):
                analysis['structural_issues'] += 1
        
        print(f"📊 Analysis complete:")
        print(f"   • Total problems: {analysis['total_problems']}")
        print(f"   • Auto-fixable: {analysis['fixable_automatically']}")
        print(f"   • Needs review: {analysis['requires_manual_review']}")
        
        return analysis
    
    def generate_fix_commands(self, gedcom_file: Path) -> List[str]:
        """Generate specific command-line fixes."""
        print("🔧 Generating fix commands...")
        
        commands = []
        
        # Group problems by type for batch processing
        problems_by_type = defaultdict(list)
        for problem in self.problems:
            problems_by_type[problem.get('type', 'unknown')].append(problem)
        
        # Generate commands for each problem type
        for problem_type, problem_list in problems_by_type.items():
            type_lower = problem_type.lower()
            
            if 'duplicate' in type_lower:
                commands.extend(self._generate_duplicate_fixes(problem_list, gedcom_file))
            elif 'missing birth' in type_lower:
                commands.extend(self._generate_birth_date_fixes(problem_list, gedcom_file))
            elif 'missing death' in type_lower:
                commands.extend(self._generate_death_date_fixes(problem_list, gedcom_file))
            elif 'inconsistent' in type_lower and 'name' in type_lower:
                commands.extend(self._generate_name_fixes(problem_list, gedcom_file))
            elif 'birth after death' in type_lower:
                commands.extend(self._generate_date_logic_fixes(problem_list, gedcom_file))
            else:
                commands.extend(self._generate_generic_fixes(problem_list, gedcom_file))
        
        self.fix_commands = commands
        print(f"✅ Generated {len(commands)} fix commands")
        return commands
    
    def _generate_duplicate_fixes(self, problems: List[Dict], gedcom_file: Path) -> List[str]:
        """Generate commands to fix duplicate persons."""
        commands = []
        
        # Extract person IDs from problems
        person_pairs = []
        for problem in problems:
            details = problem.get('details', '') + ' ' + problem.get('description', '')
            
            # Look for person ID patterns
            ids = re.findall(r'@I\d+@', details)
            if len(ids) >= 2:
                person_pairs.append((ids[0], ids[1]))
        
        if person_pairs:
            commands.append(f"# Fix {len(person_pairs)} duplicate person issues")
            commands.append(f"python3 scripts/comprehensive_gedcom_processor.py \"{gedcom_file}\" \\")
            commands.append(f"  --duplicate-threshold 95.0 \\")
            commands.append(f"  --output \"{gedcom_file.parent / (gedcom_file.stem + '_deduped.ged')}\" \\")
            commands.append(f"  --report \"{gedcom_file.parent / (gedcom_file.stem + '_dedup_report.json')}\"")
            commands.append("")
        
        return commands
    
    def _generate_birth_date_fixes(self, problems: List[Dict], gedcom_file: Path) -> List[str]:
        """Generate commands to fix missing birth dates."""
        commands = []
        
        person_ids = []
        for problem in problems:
            person_id = problem.get('id', '')
            if person_id:
                person_ids.append(person_id)
        
        if person_ids:
            commands.append(f"# Fix {len(person_ids)} missing birth date issues")
            commands.append(f"python3 scripts/estimate_missing_dates.py \"{gedcom_file}\" \\")
            commands.append(f"  --estimate-births \\")
            commands.append(f"  --person-ids {','.join(person_ids[:10])} \\")  # Limit to first 10
            commands.append(f"  --output \"{gedcom_file.parent / (gedcom_file.stem + '_birth_fixed.ged')}\"")
            commands.append("")
        
        return commands
    
    def _generate_death_date_fixes(self, problems: List[Dict], gedcom_file: Path) -> List[str]:
        """Generate commands to fix missing death dates."""
        commands = []
        
        person_ids = []
        for problem in problems:
            person_id = problem.get('id', '')
            if person_id:
                person_ids.append(person_id)
        
        if person_ids:
            commands.append(f"# Fix {len(person_ids)} missing death date issues")
            commands.append(f"python3 scripts/estimate_missing_dates.py \"{gedcom_file}\" \\")
            commands.append(f"  --estimate-deaths \\")
            commands.append(f"  --person-ids {','.join(person_ids[:10])} \\")  # Limit to first 10
            commands.append(f"  --output \"{gedcom_file.parent / (gedcom_file.stem + '_death_fixed.ged')}\"")
            commands.append("")
        
        return commands
    
    def _generate_name_fixes(self, problems: List[Dict], gedcom_file: Path) -> List[str]:
        """Generate commands to fix name inconsistencies."""
        commands = []
        
        commands.append(f"# Fix {len(problems)} name consistency issues")
        commands.append(f"python3 scripts/comprehensive_gedcom_processor.py \"{gedcom_file}\" \\")
        commands.append(f"  --fix-names \\")
        commands.append(f"  --output \"{gedcom_file.parent / (gedcom_file.stem + '_names_fixed.ged')}\" \\")
        commands.append(f"  --report \"{gedcom_file.parent / (gedcom_file.stem + '_names_report.json')}\"")
        commands.append("")
        
        return commands
    
    def _generate_date_logic_fixes(self, problems: List[Dict], gedcom_file: Path) -> List[str]:
        """Generate commands to fix date logic issues."""
        commands = []
        
        commands.append(f"# Fix {len(problems)} date logic issues")
        commands.append(f"python3 scripts/validate_date_logic.py \"{gedcom_file}\" \\")
        commands.append(f"  --fix-impossible-dates \\")
        commands.append(f"  --output \"{gedcom_file.parent / (gedcom_file.stem + '_dates_fixed.ged')}\" \\")
        commands.append(f"  --report \"{gedcom_file.parent / (gedcom_file.stem + '_date_logic_report.json')}\"")
        commands.append("")
        
        return commands
    
    def _generate_generic_fixes(self, problems: List[Dict], gedcom_file: Path) -> List[str]:
        """Generate generic fixes for other issues."""
        commands = []
        
        problem_type = problems[0].get('type', 'Unknown') if problems else 'Unknown'
        commands.append(f"# Manual review needed for {len(problems)} '{problem_type}' issues")
        commands.append(f"# See detailed report for specific actions required")
        commands.append("")
        
        return commands
    
    def export_problem_analysis(self, output_path: Path) -> Dict:
        """Export detailed problem analysis."""
        print(f"📊 Exporting analysis to {output_path}")
        
        analysis = {
            'summary': {
                'total_problems': len(self.problems),
                'problem_types': dict(self.statistics),
                'generated_fixes': len(self.fix_commands)
            },
            'problems_by_category': {},
            'fix_commands': self.fix_commands,
            'recommendations': []
        }
        
        # Group problems by category for detailed analysis
        for problem in self.problems:
            category = problem.get('type', 'Unknown')
            if category not in analysis['problems_by_category']:
                analysis['problems_by_category'][category] = []
            analysis['problems_by_category'][category].append(problem)
        
        # Generate recommendations
        if self.statistics.get('Duplicate Person', 0) > 5:
            analysis['recommendations'].append("High number of duplicates detected - run comprehensive deduplication")
        
        if self.statistics.get('Missing Birth Date', 0) > 10:
            analysis['recommendations'].append("Many missing birth dates - consider date estimation algorithms")
        
        if self.statistics.get('Inconsistent Name Spelling', 0) > 0:
            analysis['recommendations'].append("Name standardization recommended for better data quality")
        
        # Save analysis
        with open(output_path, 'w') as f:
            json.dump(analysis, f, indent=2)
        
        return analysis
    
    def generate_fix_script(self, output_path: Path, gedcom_file: Path) -> Path:
        """Generate executable script with all fixes."""
        print(f"📝 Generating fix script: {output_path}")
        
        script_content = f"""#!/bin/bash
# Auto-generated GEDCOM fix script from RootsMagic problem list
# Generated on $(date)
# Source GEDCOM: {gedcom_file}

set -euo pipefail

GEDCOM_FILE="{gedcom_file}"
BACKUP_DIR="{gedcom_file.parent / 'backup'}"
OUTPUT_DIR="{gedcom_file.parent / 'fixed'}"

echo "🚀 Starting GEDCOM fixes from RootsMagic problem list..."

# Create directories
mkdir -p "$BACKUP_DIR" "$OUTPUT_DIR"

# Backup original file
cp "$GEDCOM_FILE" "$BACKUP_DIR/$(basename "$GEDCOM_FILE" .ged).$(date +%Y%m%d-%H%M%S).backup.ged"
echo "✅ Backup created"

# Apply fixes
"""
        
        for i, command in enumerate(self.fix_commands):
            if command.strip() and not command.startswith('#'):
                script_content += f"\necho \"🔧 Step {i+1}: Running fix...\"\n"
                script_content += command + "\n"
            else:
                script_content += command + "\n"
        
        script_content += f"""
echo "✅ All fixes applied!"
echo "📁 Check the 'fixed' directory for results"
echo "📊 Review the generated reports for validation"
"""
        
        # Write script
        with open(output_path, 'w') as f:
            f.write(script_content)
        
        # Make executable
        output_path.chmod(0o755)
        
        return output_path

def main():
    parser = argparse.ArgumentParser(
        description="Convert RootsMagic problem lists into command-line fixes",
        epilog="This tool bridges GUI genealogy software with powerful command-line processing."
    )
    parser.add_argument("problem_list", type=Path, help="RootsMagic problem list file (CSV or text)")
    parser.add_argument("gedcom_file", type=Path, help="GEDCOM file to fix")
    parser.add_argument("-f", "--format", choices=['csv', 'text', 'auto'], default='auto',
                       help="Input format (default: auto-detect)")
    parser.add_argument("-o", "--output-dir", type=Path, 
                       help="Output directory for fixes and reports")
    parser.add_argument("--analysis-only", action="store_true",
                       help="Only analyze problems, don't generate fixes")
    
    args = parser.parse_args()
    
    if not args.problem_list.exists():
        print(f"❌ Error: Problem list file not found: {args.problem_list}")
        sys.exit(1)
    
    if not args.gedcom_file.exists():
        print(f"❌ Error: GEDCOM file not found: {args.gedcom_file}")
        sys.exit(1)
    
    # Set output directory
    if not args.output_dir:
        args.output_dir = args.gedcom_file.parent / "rootsmagic_fixes"
    args.output_dir.mkdir(parents=True, exist_ok=True)
    
    try:
        print("🔧 RootsMagic Problem List Processor")
        print("=" * 50)
        
        # Initialize processor
        processor = RootsMagicProblemProcessor()
        
        # Load problem list
        problem_count = processor.load_problem_list(args.problem_list, args.format)
        if problem_count == 0:
            print("❌ No problems found in the input file")
            sys.exit(1)
        
        # Analyze problems
        analysis = processor.analyze_problems()
        
        # Export analysis
        analysis_file = args.output_dir / "problem_analysis.json"
        processor.export_problem_analysis(analysis_file)
        
        if not args.analysis_only:
            # Generate fix commands
            commands = processor.generate_fix_commands(args.gedcom_file)
            
            # Generate executable fix script
            script_file = args.output_dir / "apply_fixes.sh"
            processor.generate_fix_script(script_file, args.gedcom_file)
            
            print("\n" + "=" * 60)
            print("🎉 ROOTSMAGIC PROBLEM PROCESSING COMPLETE!")
            print("=" * 60)
            print(f"📊 Analyzed: {problem_count} problems")
            print(f"🔧 Generated: {len(commands)} fix commands")
            print(f"📁 Output directory: {args.output_dir}")
            print(f"📝 Fix script: {script_file}")
            print(f"📊 Analysis report: {analysis_file}")
            print()
            print("🚀 Next steps:")
            print(f"   1. Review the analysis: cat {analysis_file}")
            print(f"   2. Run the fixes: {script_file}")
            print(f"   3. Validate the results with comprehensive_gedcom_processor.py")
        else:
            print("\n" + "=" * 60)
            print("📊 PROBLEM ANALYSIS COMPLETE!")
            print("=" * 60)
            print(f"📊 Analysis saved: {analysis_file}")
        
    except Exception as e:
        print(f"❌ Error processing problems: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
