#!/usr/bin/env python3
"""
MyHeritage Consistency Issue Fixer
Addresses specific patterns found by MyHeritage Tree Consistency Checker
"""

import re
import csv
from pathlib import Path
from collections import Counter, defaultdict
import argparse

class MyHeritageFixer:
    def __init__(self, input_file, output_file=None, dry_run=False):
        self.input_file = Path(input_file)
        self.output_file = Path(output_file) if output_file else self.input_file.with_stem(f"{self.input_file.stem}_mh_fixed")
        self.dry_run = dry_run
        self.lines = []
        self.fixes_applied = defaultdict(int)
        
        # Known suffixes to move
        self.suffixes = ['Jr', 'Sr', 'II', 'III', 'IV', 'V', 'Ii', 'Iii', 'PhD', 'MD', 'Esq']
        self.suffix_pattern = re.compile(r'\b(' + '|'.join(re.escape(s) for s in self.suffixes) + r')\b', re.IGNORECASE)
        
    def read_gedcom(self):
        """Read GEDCOM file into memory"""
        with self.input_file.open('r', encoding='utf-8', errors='ignore') as f:
            self.lines = [line.rstrip('\r\n') for line in f]
        print(f"📖 Read {len(self.lines)} lines from {self.input_file}")
        
    def write_gedcom(self):
        """Write processed GEDCOM file"""
        if self.dry_run:
            print("\n🧪 DRY RUN - No file written")
            return
            
        with self.output_file.open('w', encoding='utf-8') as f:
            for line in self.lines:
                f.write(line + '\n')
        print(f"✅ Written {len(self.lines)} lines to {self.output_file}")
        
    def fix_suffix_in_names(self):
        """Fix suffixes embedded in first/last names"""
        print("🔧 Fixing suffix placement...")
        
        i = 0
        while i < len(self.lines):
            line = self.lines[i]
            
            # Look for NAME records
            if line.startswith('1 NAME ') or line.startswith('2 NAME '):
                name_parts = line.split(' ', 2)
                if len(name_parts) >= 3:
                    level = name_parts[0]
                    tag = name_parts[1]  
                    name_value = name_parts[2]
                    
                    # Check if suffix is embedded in name
                    suffix_match = self.suffix_pattern.search(name_value)
                    if suffix_match:
                        suffix = suffix_match.group(1)
                        # Remove suffix from name
                        clean_name = re.sub(r'\b' + re.escape(suffix) + r'\b', '', name_value).strip()
                        clean_name = re.sub(r'\s+', ' ', clean_name)  # Clean up multiple spaces
                        
                        # Update the name line
                        self.lines[i] = f"{level} {tag} {clean_name}"
                        
                        # Add suffix line after name (look for existing NSFX or add new one)
                        suffix_added = False
                        j = i + 1
                        while j < len(self.lines) and (self.lines[j].startswith(f"{int(level)+1} ") or self.lines[j].startswith(f"{int(level)+2} ")):
                            if 'NSFX' in self.lines[j]:
                                # Update existing suffix
                                self.lines[j] = f"{int(level)+1} NSFX {suffix}"
                                suffix_added = True
                                break
                            j += 1
                            
                        if not suffix_added:
                            # Insert new suffix line
                            self.lines.insert(i + 1, f"{int(level)+1} NSFX {suffix}")
                            
                        self.fixes_applied['suffix_moved'] += 1
                        print(f"  ✅ Moved '{suffix}' from name '{name_value}' to suffix field")
            i += 1
            
    def deduplicate_facts(self):
        """Remove duplicate birth, death, and marriage facts"""
        print("🔧 Deduplicating facts...")
        
        current_person = None
        person_facts = defaultdict(list)
        i = 0
        
        while i < len(self.lines):
            line = self.lines[i]
            
            # Track current individual
            if line.startswith('0 ') and 'INDI' in line:
                if current_person:
                    self._process_person_facts(current_person, person_facts)
                current_person = line.split()[1]
                person_facts = defaultdict(list)
                
            # Collect facts for current person
            elif current_person and line.startswith('1 '):
                parts = line.split(' ', 2)
                if len(parts) >= 2 and parts[1] in ['BIRT', 'DEAT', 'MARR']:
                    fact_type = parts[1]
                    fact_data = {'line_num': i, 'content': line}
                    
                    # Collect associated data (dates, places, etc.)
                    j = i + 1
                    while j < len(self.lines) and self.lines[j].startswith('2 '):
                        fact_data.setdefault('details', []).append(self.lines[j])
                        j += 1
                    
                    person_facts[fact_type].append(fact_data)
            i += 1
            
        # Process final person
        if current_person:
            self._process_person_facts(current_person, person_facts)
            
    def _process_person_facts(self, person_id, facts):
        """Process facts for a single person, removing duplicates"""
        for fact_type, fact_list in facts.items():
            if len(fact_list) > 1:
                # Keep the first fact, mark others for deletion
                keep_fact = fact_list[0]
                for duplicate_fact in fact_list[1:]:
                    # Mark line for deletion (we'll remove them later)
                    line_num = duplicate_fact['line_num']
                    self.lines[line_num] = f"# REMOVED DUPLICATE {fact_type}: " + self.lines[line_num]
                    
                    # Mark associated detail lines for deletion  
                    for detail in duplicate_fact.get('details', []):
                        detail_line_num = self.lines.index(detail)
                        self.lines[detail_line_num] = "# REMOVED: " + self.lines[detail_line_num]
                
                self.fixes_applied[f'duplicate_{fact_type.lower()}_removed'] += len(fact_list) - 1
                print(f"  ✅ Removed {len(fact_list) - 1} duplicate {fact_type} facts for {person_id}")
                
    def normalize_place_names(self):
        """Normalize inconsistent place name spellings"""
        print("🔧 Normalizing place names...")
        
        # Collect all place names and their frequencies
        place_counts = Counter()
        place_lines = []
        
        for i, line in enumerate(self.lines):
            if 'PLAC ' in line:
                parts = line.split('PLAC ', 1)
                if len(parts) == 2:
                    place = parts[1].strip()
                    place_counts[place] += 1
                    place_lines.append((i, place))
        
        # Find similar place names and use the most common spelling
        normalized_places = self._find_place_normalizations(place_counts)
        
        # Apply normalizations
        for line_num, original_place in place_lines:
            if original_place in normalized_places:
                new_place = normalized_places[original_place]
                if new_place != original_place:
                    old_line = self.lines[line_num]
                    self.lines[line_num] = old_line.replace(f'PLAC {original_place}', f'PLAC {new_place}')
                    self.fixes_applied['place_normalized'] += 1
                    print(f"  ✅ Normalized '{original_place}' → '{new_place}'")
    
    def _find_place_normalizations(self, place_counts):
        """Find place name normalizations based on similarity and frequency"""
        from rapidfuzz import fuzz, process
        
        places = list(place_counts.keys())
        normalizations = {}
        processed = set()
        
        for place in places:
            if place in processed:
                continue
                
            # Find similar places
            matches = process.extract(place, places, scorer=fuzz.ratio, limit=10, score_cutoff=85)
            similar_places = [match[0] for match in matches if match[0] != place]
            
            if similar_places:
                # Find the most common spelling among similar places
                candidates = [place] + similar_places
                most_common = max(candidates, key=lambda x: place_counts[x])
                
                # Normalize all similar places to the most common
                for similar_place in candidates:
                    if similar_place != most_common:
                        normalizations[similar_place] = most_common
                    processed.add(similar_place)
                    
        return normalizations
        
    def remove_marked_lines(self):
        """Remove lines marked for deletion"""
        original_count = len(self.lines)
        self.lines = [line for line in self.lines if not line.startswith('# REMOVED')]
        removed_count = original_count - len(self.lines)
        if removed_count > 0:
            print(f"🗑️  Removed {removed_count} marked lines")
            
    def process(self):
        """Run all fixes"""
        print("🚀 Starting MyHeritage consistency fixes...")
        self.read_gedcom()
        
        # Apply fixes
        self.fix_suffix_in_names()
        self.deduplicate_facts()  
        self.normalize_place_names()
        self.remove_marked_lines()
        
        # Write output
        self.write_gedcom()
        
        # Summary
        print("\n📊 FIXES APPLIED:")
        if self.fixes_applied:
            for fix_type, count in self.fixes_applied.items():
                print(f"  • {fix_type}: {count}")
        else:
            print("  • No fixes were needed")
            
        return dict(self.fixes_applied)

def main():
    parser = argparse.ArgumentParser(description="Fix MyHeritage consistency issues in GEDCOM files")
    parser.add_argument('input_file', help="Input GEDCOM file")
    parser.add_argument('-o', '--output', help="Output GEDCOM file")
    parser.add_argument('--dry-run', action='store_true', help="Show what would be fixed without writing changes")
    
    args = parser.parse_args()
    
    fixer = MyHeritageFixer(args.input_file, args.output, args.dry_run)
    fixes = fixer.process()
    
    if args.dry_run:
        print(f"\n🧪 DRY RUN COMPLETE - {sum(fixes.values())} total fixes identified")
    else:
        print(f"\n✅ PROCESSING COMPLETE - {sum(fixes.values())} total fixes applied")

if __name__ == "__main__":
    main()
