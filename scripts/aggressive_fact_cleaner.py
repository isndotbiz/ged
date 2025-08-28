#!/usr/bin/env python3
"""
Aggressive Duplicate Fact Cleaner
Removes ALL duplicate facts - keeps only ONE of each fact type per person
No alternative names, no duplicates - just clean, single facts
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
    """Represents a single fact instance"""
    fact_type: str
    start_line: int
    end_line: int
    date_value: Optional[str] = None
    place_value: Optional[str] = None
    source_count: int = 0
    note_count: int = 0
    completeness_score: int = 0
    raw_lines: List[str] = None

class AggressiveFactCleaner:
    """Removes ALL duplicate facts, keeping only the best one of each type"""
    
    def __init__(self):
        self.stats = {
            'individuals_processed': 0,
            'facts_removed': 0,
            'facts_kept': 0,
            'lines_removed': 0
        }
    
    def load_gedcom(self, gedcom_path: Path) -> List[str]:
        """Load GEDCOM file"""
        print(f"📖 Loading GEDCOM file: {gedcom_path}")
        
        with open(gedcom_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        print(f"✅ Loaded {len(lines)} lines")
        return lines
    
    def find_individual_blocks(self, lines: List[str]) -> List[Tuple[int, int, str]]:
        """Find all individual blocks (start_line, end_line, individual_id)"""
        individuals = []
        
        i = 0
        while i < len(lines):
            line = lines[i].strip()
            
            if re.match(r'^0 @I\d+@ INDI', line):
                individual_id = line.split()[1]
                start_line = i
                
                # Find end of individual
                i += 1
                while i < len(lines):
                    next_line = lines[i].strip()
                    if next_line.startswith('0 @') or (next_line.startswith('0 ') and not next_line.startswith('0 @')):
                        break
                    i += 1
                
                end_line = i - 1
                individuals.append((start_line, end_line, individual_id))
            else:
                i += 1
        
        return individuals
    
    def analyze_facts_in_individual(self, lines: List[str], start_line: int, end_line: int) -> Dict[str, List[FactInstance]]:
        """Find all facts in an individual and group by type"""
        facts_by_type = {}
        
        # Look at lines within this individual
        individual_lines = lines[start_line:end_line+1]
        
        i = 0
        while i < len(individual_lines):
            line = individual_lines[i].strip()
            
            # Look for level 1 facts (all types)
            if re.match(r'^1 [A-Z]{3,4}($| )', line):
                fact_type = line.split()[1]
                
                # Skip certain record types that aren't "facts"
                if fact_type in ['NAME', 'SEX', 'FAMS', 'FAMC', 'CHAN', '_UID', 'REFN', 'RIN']:
                    i += 1
                    continue
                
                # This is a fact - analyze it
                fact_start = i
                fact_end = i
                date_value = None
                place_value = None
                source_count = 0
                note_count = 0
                
                # Look for sub-records
                j = i + 1
                while j < len(individual_lines):
                    sub_line = individual_lines[j].strip()
                    if sub_line.startswith('1 ') or sub_line.startswith('0 '):
                        break
                    
                    fact_end = j
                    
                    # Extract information for scoring
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
                completeness_score = 1  # Base score
                if date_value:
                    completeness_score += 10
                if place_value:
                    completeness_score += 5
                completeness_score += source_count * 3
                completeness_score += note_count * 2
                
                # Create fact instance
                fact_instance = FactInstance(
                    fact_type=fact_type,
                    start_line=start_line + fact_start,  # Absolute line number
                    end_line=start_line + fact_end,      # Absolute line number
                    date_value=date_value,
                    place_value=place_value,
                    source_count=source_count,
                    note_count=note_count,
                    completeness_score=completeness_score,
                    raw_lines=individual_lines[fact_start:fact_end+1]
                )
                
                # Group by fact type
                if fact_type not in facts_by_type:
                    facts_by_type[fact_type] = []
                facts_by_type[fact_type].append(fact_instance)
                
                i = fact_end + 1
            else:
                i += 1
        
        return facts_by_type
    
    def select_best_facts(self, facts_by_type: Dict[str, List[FactInstance]]) -> Tuple[List[FactInstance], List[FactInstance]]:
        """Select the best fact of each type, mark others for removal"""
        facts_to_keep = []
        facts_to_remove = []
        
        for fact_type, fact_instances in facts_by_type.items():
            if len(fact_instances) == 1:
                # Only one instance - keep it
                facts_to_keep.extend(fact_instances)
                self.stats['facts_kept'] += 1
            else:
                # Multiple instances - keep only the best one
                # Sort by completeness score (descending)
                fact_instances.sort(key=lambda x: x.completeness_score, reverse=True)
                
                # Keep the first (best), remove the rest
                facts_to_keep.append(fact_instances[0])
                facts_to_remove.extend(fact_instances[1:])
                
                self.stats['facts_kept'] += 1
                self.stats['facts_removed'] += len(fact_instances) - 1
                
                print(f"    📊 {fact_type}: Keeping 1 of {len(fact_instances)} instances (score: {fact_instances[0].completeness_score})")
        
        return facts_to_keep, facts_to_remove
    
    def process_gedcom(self, input_path: Path, output_path: Path) -> None:
        """Process the entire GEDCOM file"""
        print(f"🚀 Starting aggressive fact cleaning")
        print(f"📁 Input: {input_path}")
        print(f"📁 Output: {output_path}")
        
        # Load GEDCOM
        lines = self.load_gedcom(input_path)
        original_line_count = len(lines)
        
        # Find all individuals
        individuals = self.find_individual_blocks(lines)
        self.stats['individuals_processed'] = len(individuals)
        print(f"🔍 Found {len(individuals)} individuals to process")
        
        # Track lines to remove
        lines_to_remove = set()
        
        # Process each individual
        for start_line, end_line, individual_id in individuals:
            print(f"  🔍 Processing {individual_id}...")
            
            # Find all facts in this individual
            facts_by_type = self.analyze_facts_in_individual(lines, start_line, end_line)
            
            if not facts_by_type:
                continue  # No facts to process
            
            # Select best facts and mark duplicates for removal
            facts_to_keep, facts_to_remove = self.select_best_facts(facts_by_type)
            
            # Mark lines for removal
            for fact in facts_to_remove:
                for line_num in range(fact.start_line, fact.end_line + 1):
                    lines_to_remove.add(line_num)
        
        # Create cleaned lines by skipping marked lines
        cleaned_lines = []
        for i, line in enumerate(lines):
            if i not in lines_to_remove:
                cleaned_lines.append(line)
        
        self.stats['lines_removed'] = original_line_count - len(cleaned_lines)
        
        # Write cleaned GEDCOM
        with open(output_path, 'w', encoding='utf-8') as f:
            f.writelines(cleaned_lines)
        
        print(f"✅ Clean GEDCOM written to: {output_path}")
        print(f"📉 Reduced from {original_line_count} to {len(cleaned_lines)} lines")
    
    def print_summary(self) -> None:
        """Print processing summary"""
        print("\\n" + "="*60)
        print("🧹 AGGRESSIVE FACT CLEANING SUMMARY")
        print("="*60)
        
        print(f"👥 Individuals processed: {self.stats['individuals_processed']}")
        print(f"✅ Facts kept (best of each type): {self.stats['facts_kept']}")
        print(f"🗑️  Duplicate facts removed: {self.stats['facts_removed']}")
        print(f"📉 Lines removed from file: {self.stats['lines_removed']}")
        
        if self.stats['facts_removed'] > 0:
            print(f"\\n📈 Impact:")
            print(f"   • Eliminated {self.stats['facts_removed']} redundant facts")
            print(f"   • Kept only the most complete fact of each type")
            print(f"   • Significantly reduced file size and complexity")
            print(f"   • Each person now has exactly ONE of each fact type")

def main():
    parser = argparse.ArgumentParser(
        description='Aggressively remove ALL duplicate facts from GEDCOM files',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
This tool removes ALL duplicate facts, keeping only the most complete one of each type.
For example, if someone has 5 birth records, it keeps only the best one and removes 4.

Examples:
  %(prog)s input.ged -o output.ged
  %(prog)s messy.ged --output clean.ged
        """
    )
    
    parser.add_argument('input', help='Input GEDCOM file')
    parser.add_argument('-o', '--output', required=True, help='Output GEDCOM file')
    parser.add_argument('-v', '--verbose', action='store_true', help='Verbose output')
    
    args = parser.parse_args()
    
    try:
        input_path = Path(args.input)
        output_path = Path(args.output)
        
        if not input_path.exists():
            print(f"❌ Error: Input file not found: {input_path}")
            sys.exit(1)
        
        # Process the file
        cleaner = AggressiveFactCleaner()
        cleaner.process_gedcom(input_path, output_path)
        
        # Show summary
        cleaner.print_summary()
        
        print(f"\\n✅ Aggressive fact cleaning complete!")
        print(f"🎯 Your file now has exactly ONE of each fact type per person")
        
    except Exception as e:
        print(f"❌ Error: {e}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        sys.exit(1)

if __name__ == '__main__':
    main()
