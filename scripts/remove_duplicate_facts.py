#!/usr/bin/env python3
"""
Duplicate Fact Removal Script
Specifically targets the 333 duplicate birth/death facts identified by MyHeritage
Safely removes duplicates while preserving the most complete information
"""

import argparse
import re
import sys
from pathlib import Path
from typing import List, Dict, Any, Optional, Tuple
from dataclasses import dataclass
from datetime import datetime

@dataclass
class FactInstance:
    """Represents a single fact instance (birth or death)"""
    individual_id: str
    fact_type: str  # BIRT or DEAT
    start_line: int
    end_line: int
    date_value: Optional[str] = None
    place_value: Optional[str] = None
    source_count: int = 0
    note_count: int = 0
    completeness_score: int = 0
    raw_lines: List[str] = None

class DuplicateFactRemover:
    """Removes duplicate birth and death facts from GEDCOM files"""
    
    def __init__(self):
        self.individuals: Dict[str, List[str]] = {}  # individual_id -> lines
        self.duplicate_facts: Dict[str, List[FactInstance]] = {}  # individual_id -> duplicates
        self.removal_stats = {
            'birth_duplicates_found': 0,
            'death_duplicates_found': 0,
            'birth_duplicates_removed': 0,
            'death_duplicates_removed': 0,
            'individuals_processed': 0
        }
    
    def load_gedcom(self, gedcom_path: Path) -> List[str]:
        """Load GEDCOM file and return all lines"""
        print(f"📖 Loading GEDCOM file: {gedcom_path}")
        
        with open(gedcom_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        print(f"✅ Loaded {len(lines)} lines from GEDCOM file")
        return lines
    
    def parse_individuals(self, lines: List[str]) -> None:
        """Parse GEDCOM lines to extract individual records"""
        print("🔍 Parsing individual records...")
        
        current_individual = None
        current_lines = []
        
        for i, line in enumerate(lines):
            # Check for individual record start
            if re.match(r'^0 @I\d+@ INDI', line):
                # Save previous individual if exists
                if current_individual and current_lines:
                    self.individuals[current_individual] = current_lines.copy()
                
                # Start new individual
                current_individual = line.split()[1]  # Extract @I123@
                current_lines = [line]
            elif current_individual and (line.startswith('0 ') and not line.startswith('0 @')):
                # End of current individual (start of new top-level record)
                self.individuals[current_individual] = current_lines.copy()
                current_individual = None
                current_lines = [line]
            elif current_individual:
                # Continue current individual
                current_lines.append(line)
            else:
                # Non-individual lines (header, families, etc.)
                current_lines.append(line)
        
        # Don't forget the last individual
        if current_individual and current_lines:
            self.individuals[current_individual] = current_lines.copy()
        
        individual_count = len([k for k in self.individuals.keys() if k.startswith('@I')])
        print(f"✅ Parsed {individual_count} individuals")
        self.removal_stats['individuals_processed'] = individual_count
    
    def find_fact_instances(self, individual_lines: List[str], fact_type: str) -> List[FactInstance]:
        """Find all instances of a specific fact type in an individual's record"""
        fact_instances = []
        i = 0
        
        while i < len(individual_lines):
            line = individual_lines[i].strip()
            
            # Look for fact start (1 BIRT or 1 DEAT)
            if re.match(f'^1 {fact_type}', line):
                # Found a fact, now gather all its sub-lines
                fact_start = i
                fact_end = i
                date_value = None
                place_value = None
                source_count = 0
                note_count = 0
                
                # Look ahead for sub-records
                j = i + 1
                while j < len(individual_lines):
                    sub_line = individual_lines[j].strip()
                    if sub_line.startswith('1 ') or sub_line.startswith('0 '):
                        # End of this fact
                        break
                    
                    fact_end = j
                    
                    # Extract useful information
                    if sub_line.startswith('2 DATE '):
                        date_value = sub_line[7:].strip()
                    elif sub_line.startswith('2 PLAC '):
                        place_value = sub_line[7:].strip()
                    elif sub_line.startswith('2 SOUR') or sub_line.startswith('1 SOUR'):
                        source_count += 1
                    elif sub_line.startswith('2 NOTE') or sub_line.startswith('1 NOTE'):
                        note_count += 1
                    
                    j += 1
                
                # Calculate completeness score
                completeness_score = 0
                if date_value:
                    completeness_score += 10
                if place_value:
                    completeness_score += 5
                completeness_score += source_count * 3
                completeness_score += note_count * 2
                
                fact_instance = FactInstance(
                    individual_id="",  # Will be set later
                    fact_type=fact_type,
                    start_line=fact_start,
                    end_line=fact_end,
                    date_value=date_value,
                    place_value=place_value,
                    source_count=source_count,
                    note_count=note_count,
                    completeness_score=completeness_score,
                    raw_lines=individual_lines[fact_start:fact_end+1]
                )
                
                fact_instances.append(fact_instance)
                i = fact_end + 1
            else:
                i += 1
        
        return fact_instances
    
    def find_duplicates(self) -> None:
        """Find individuals with duplicate birth or death facts"""
        print("🔍 Identifying duplicate facts...")
        
        birth_duplicates = 0
        death_duplicates = 0
        
        for individual_id, lines in self.individuals.items():
            if not individual_id.startswith('@I'):
                continue
                
            # Find birth facts
            birth_facts = self.find_fact_instances(lines, 'BIRT')
            if len(birth_facts) > 1:
                birth_duplicates += len(birth_facts) - 1
                for fact in birth_facts:
                    fact.individual_id = individual_id
                
                if individual_id not in self.duplicate_facts:
                    self.duplicate_facts[individual_id] = []
                self.duplicate_facts[individual_id].extend(birth_facts)
            
            # Find death facts
            death_facts = self.find_fact_instances(lines, 'DEAT')
            if len(death_facts) > 1:
                death_duplicates += len(death_facts) - 1
                for fact in death_facts:
                    fact.individual_id = individual_id
                
                if individual_id not in self.duplicate_facts:
                    self.duplicate_facts[individual_id] = []
                self.duplicate_facts[individual_id].extend(death_facts)
        
        self.removal_stats['birth_duplicates_found'] = birth_duplicates
        self.removal_stats['death_duplicates_found'] = death_duplicates
        
        print(f"🔍 Found {birth_duplicates} duplicate birth facts")
        print(f"🔍 Found {death_duplicates} duplicate death facts")
        print(f"🔍 Total individuals with duplicates: {len(self.duplicate_facts)}")
    
    def select_best_facts(self) -> Dict[str, List[FactInstance]]:
        """Select the best fact to keep for each individual with duplicates"""
        facts_to_remove = {}
        
        for individual_id, facts in self.duplicate_facts.items():
            # Group facts by type
            birth_facts = [f for f in facts if f.fact_type == 'BIRT']
            death_facts = [f for f in facts if f.fact_type == 'DEAT']
            
            to_remove = []
            
            # Handle birth duplicates
            if len(birth_facts) > 1:
                # Sort by completeness score (descending)
                birth_facts.sort(key=lambda x: x.completeness_score, reverse=True)
                # Keep the first (most complete), remove the rest
                to_remove.extend(birth_facts[1:])
                self.removal_stats['birth_duplicates_removed'] += len(birth_facts) - 1
            
            # Handle death duplicates
            if len(death_facts) > 1:
                # Sort by completeness score (descending)
                death_facts.sort(key=lambda x: x.completeness_score, reverse=True)
                # Keep the first (most complete), remove the rest
                to_remove.extend(death_facts[1:])
                self.removal_stats['death_duplicates_removed'] += len(death_facts) - 1
            
            if to_remove:
                facts_to_remove[individual_id] = to_remove
        
        return facts_to_remove
    
    def remove_duplicate_facts(self, lines: List[str], facts_to_remove: Dict[str, List[FactInstance]]) -> List[str]:
        """Remove selected duplicate facts from the GEDCOM lines"""
        print("🗑️  Removing duplicate facts...")
        
        # Create a new list of lines with duplicates removed
        new_lines = []
        current_individual = None
        skip_lines = set()
        
        # Build set of line numbers to skip
        for individual_id, facts in facts_to_remove.items():
            individual_lines = self.individuals[individual_id]
            
            for fact in facts:
                # Calculate absolute line numbers
                individual_start = 0
                for i, line in enumerate(lines):
                    if line.startswith(f'0 {individual_id} INDI'):
                        individual_start = i
                        break
                
                # Mark lines for removal
                for line_offset in range(fact.start_line, fact.end_line + 1):
                    absolute_line = individual_start + line_offset
                    skip_lines.add(absolute_line)
        
        # Copy lines, skipping marked ones
        for i, line in enumerate(lines):
            if i not in skip_lines:
                new_lines.append(line)
        
        removed_lines = len(lines) - len(new_lines)
        print(f"✅ Removed {removed_lines} lines containing duplicate facts")
        
        return new_lines
    
    def generate_removal_report(self, output_dir: Path) -> None:
        """Generate a report of what was removed"""
        report_file = output_dir / "duplicate_removal_report.md"
        
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write("# Duplicate Fact Removal Report\\n\\n")
            f.write(f"Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\\n\\n")
            
            f.write("## Summary\\n\\n")
            f.write(f"- **Individuals processed**: {self.removal_stats['individuals_processed']}\\n")
            f.write(f"- **Birth duplicates found**: {self.removal_stats['birth_duplicates_found']}\\n")
            f.write(f"- **Death duplicates found**: {self.removal_stats['death_duplicates_found']}\\n")
            f.write(f"- **Birth duplicates removed**: {self.removal_stats['birth_duplicates_removed']}\\n")
            f.write(f"- **Death duplicates removed**: {self.removal_stats['death_duplicates_removed']}\\n")
            
            total_found = self.removal_stats['birth_duplicates_found'] + self.removal_stats['death_duplicates_found']
            total_removed = self.removal_stats['birth_duplicates_removed'] + self.removal_stats['death_duplicates_removed']
            
            f.write(f"- **Total duplicates found**: {total_found}\\n")
            f.write(f"- **Total duplicates removed**: {total_removed}\\n\\n")
            
            # Details by individual
            if self.duplicate_facts:
                f.write("## Details by Individual\\n\\n")
                for individual_id, facts in self.duplicate_facts.items():
                    f.write(f"### {individual_id}\\n")
                    
                    birth_facts = [f for f in facts if f.fact_type == 'BIRT']
                    death_facts = [f for f in facts if f.fact_type == 'DEAT']
                    
                    if birth_facts:
                        f.write(f"**Birth facts**: {len(birth_facts)} found\\n")
                        for i, fact in enumerate(birth_facts):
                            status = "KEPT" if i == 0 else "REMOVED"
                            f.write(f"- {status}: Date={fact.date_value}, Place={fact.place_value}, Score={fact.completeness_score}\\n")
                        f.write("\\n")
                    
                    if death_facts:
                        f.write(f"**Death facts**: {len(death_facts)} found\\n")
                        for i, fact in enumerate(death_facts):
                            status = "KEPT" if i == 0 else "REMOVED"
                            f.write(f"- {status}: Date={fact.date_value}, Place={fact.place_value}, Score={fact.completeness_score}\\n")
                        f.write("\\n")
        
        print(f"📖 Removal report generated: {report_file}")
    
    def process_file(self, input_path: Path, output_path: Path, report_dir: Path) -> None:
        """Main processing function"""
        print(f"🚀 Starting duplicate fact removal")
        print(f"📁 Input: {input_path}")
        print(f"📁 Output: {output_path}")
        
        # Load and parse GEDCOM
        lines = self.load_gedcom(input_path)
        self.parse_individuals(lines)
        
        # Find duplicates
        self.find_duplicates()
        
        if not self.duplicate_facts:
            print("ℹ️  No duplicate facts found - file is already clean!")
            # Just copy the file
            with open(output_path, 'w', encoding='utf-8') as f:
                f.writelines(lines)
            return
        
        # Select which facts to remove
        facts_to_remove = self.select_best_facts()
        
        # Remove duplicates
        clean_lines = self.remove_duplicate_facts(lines, facts_to_remove)
        
        # Write cleaned file
        with open(output_path, 'w', encoding='utf-8') as f:
            f.writelines(clean_lines)
        
        print(f"✅ Clean GEDCOM written to: {output_path}")
        
        # Generate report
        self.generate_removal_report(report_dir)
        
        # Print summary
        self.print_summary()
    
    def print_summary(self) -> None:
        """Print processing summary"""
        print("\\n" + "="*60)
        print("🗑️  DUPLICATE FACT REMOVAL SUMMARY")
        print("="*60)
        
        total_found = self.removal_stats['birth_duplicates_found'] + self.removal_stats['death_duplicates_found']
        total_removed = self.removal_stats['birth_duplicates_removed'] + self.removal_stats['death_duplicates_removed']
        
        print(f"📊 Processed {self.removal_stats['individuals_processed']} individuals")
        print(f"🔍 Found {total_found} duplicate facts")
        print(f"🗑️  Removed {total_removed} duplicate facts")
        print(f"✅ Kept the most complete version of each fact")
        
        if total_removed > 0:
            print(f"\\n📈 Impact:")
            print(f"   • Eliminated {total_removed} redundant facts")
            print(f"   • Improved data quality and consistency")
            print(f"   • Reduced file size and processing overhead")

def main():
    parser = argparse.ArgumentParser(
        description='Remove duplicate birth and death facts from GEDCOM files',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s input.ged -o output.ged
  %(prog)s myfile.ged --output cleaned.ged --report-dir reports/
        """
    )
    
    parser.add_argument('input', help='Input GEDCOM file')
    parser.add_argument('-o', '--output', required=True, help='Output GEDCOM file')
    parser.add_argument('-r', '--report-dir', help='Directory for reports (default: same as output)')
    parser.add_argument('-v', '--verbose', action='store_true', help='Verbose output')
    
    args = parser.parse_args()
    
    try:
        input_path = Path(args.input)
        output_path = Path(args.output)
        report_dir = Path(args.report_dir) if args.report_dir else output_path.parent
        
        # Create report directory if needed
        report_dir.mkdir(parents=True, exist_ok=True)
        
        # Process the file
        remover = DuplicateFactRemover()
        remover.process_file(input_path, output_path, report_dir)
        
        print(f"\\n✅ Duplicate removal complete!")
        
    except Exception as e:
        print(f"❌ Error: {e}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        sys.exit(1)

if __name__ == '__main__':
    main()
