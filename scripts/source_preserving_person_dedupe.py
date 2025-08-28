#!/usr/bin/env python3
"""
Source-Preserving Person Deduplication Script

This script identifies and merges duplicate people in GEDCOM files while:
- PRESERVING ALL SOURCES and source references
- Maintaining proper GEDCOM hierarchy and structure  
- Keeping all family relationships intact
- Merging data intelligently without loss
"""

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple, Optional
from dataclasses import dataclass
from rapidfuzz import fuzz

@dataclass
class GedcomLine:
    """Represents a single GEDCOM line with its context"""
    level: int
    tag: str
    value: str
    xref: Optional[str] = None
    raw_line: str = ""
    line_number: int = 0

@dataclass 
class Individual:
    """Represents a person in the GEDCOM file"""
    id: str
    name: str
    given_names: str = ""
    surname: str = ""
    sex: str = ""
    birth_date: str = ""
    birth_year: int = 0
    death_date: str = ""
    line_start: int = 0
    line_end: int = 0
    raw_lines: List[str] = None
    families_as_spouse: List[str] = None
    families_as_child: List[str] = None

    def __post_init__(self):
        if self.raw_lines is None:
            self.raw_lines = []
        if self.families_as_spouse is None:
            self.families_as_spouse = []
        if self.families_as_child is None:
            self.families_as_child = []

class SourcePreservingPersonDeduplicator:
    """Person deduplicator that preserves all sources and GEDCOM structure"""
    
    def __init__(self, similarity_threshold: float = 90.0, auto_merge_threshold: float = 98.0):
        self.similarity_threshold = similarity_threshold
        self.auto_merge_threshold = auto_merge_threshold
        self.individuals: Dict[str, Individual] = {}
        self.families: Dict[str, List[str]] = {}
        self.sources: Dict[str, List[str]] = {}
        self.all_lines: List[str] = []
        self.removed_individuals: Set[str] = set()
        self.merge_log: List[Dict] = []
        self.line_mapping: Dict[int, str] = {}  # line_number -> individual_id
        
    def load_gedcom(self, file_path: Path) -> None:
        """Load and parse GEDCOM file"""
        print(f"📖 Loading GEDCOM file: {file_path}")
        
        with open(file_path, 'r', encoding='utf-8') as f:
            self.all_lines = f.readlines()
        
        print(f"✅ Loaded {len(self.all_lines)} lines")
        
        # Parse the file
        self._parse_gedcom()
        
    def _parse_gedcom(self) -> None:
        """Parse GEDCOM into structured data"""
        print("🔍 Parsing GEDCOM structure...")
        
        current_individual = None
        current_family = None
        current_source = None
        i = 0
        
        while i < len(self.all_lines):
            line = self.all_lines[i].strip()
            if not line:
                i += 1
                continue
                
            # Parse individual records
            if re.match(r'^0 @I\d+@ INDI', line):
                individual_id = line.split()[1]
                individual = Individual(id=individual_id, name="", line_start=i)
                current_individual = individual
                
                # Parse individual details
                j = i + 1
                while j < len(self.all_lines) and not self.all_lines[j].strip().startswith('0 '):
                    sub_line = self.all_lines[j].strip()
                    
                    if sub_line.startswith('1 NAME '):
                        name_value = sub_line[7:].strip()
                        individual.name = name_value
                        if '/' in name_value:
                            parts = name_value.split('/')
                            individual.given_names = parts[0].strip()
                            if len(parts) > 1:
                                individual.surname = parts[1].strip()
                        else:
                            individual.given_names = name_value
                            
                    elif sub_line.startswith('1 SEX '):
                        individual.sex = sub_line[6:].strip()
                        
                    elif sub_line.startswith('1 BIRT'):
                        # Look for birth date
                        k = j + 1
                        while k < len(self.all_lines) and self.all_lines[k].strip().startswith('2 '):
                            date_line = self.all_lines[k].strip()
                            if date_line.startswith('2 DATE '):
                                individual.birth_date = date_line[7:].strip()
                                # Extract year
                                year_match = re.search(r'\b(19|20)\d{2}\b', individual.birth_date)
                                if year_match:
                                    individual.birth_year = int(year_match.group())
                            k += 1
                            
                    elif sub_line.startswith('1 DEAT'):
                        # Look for death date
                        k = j + 1
                        while k < len(self.all_lines) and self.all_lines[k].strip().startswith('2 '):
                            date_line = self.all_lines[k].strip()
                            if date_line.startswith('2 DATE '):
                                individual.death_date = date_line[7:].strip()
                            k += 1
                            
                    elif sub_line.startswith('1 FAMS '):
                        family_id = sub_line[7:].strip()
                        individual.families_as_spouse.append(family_id)
                        
                    elif sub_line.startswith('1 FAMC '):
                        family_id = sub_line[7:].strip()
                        individual.families_as_child.append(family_id)
                    
                    j += 1
                
                individual.line_end = j - 1
                individual.raw_lines = self.all_lines[i:j]
                
                # Map line numbers to individual
                for line_num in range(i, j):
                    self.line_mapping[line_num] = individual_id
                    
                self.individuals[individual_id] = individual
                i = j
                
            # Parse family records
            elif re.match(r'^0 @F\d+@ FAM', line):
                family_id = line.split()[1]
                family_lines = []
                
                j = i + 1
                while j < len(self.all_lines) and not self.all_lines[j].strip().startswith('0 '):
                    family_lines.append(self.all_lines[j])
                    j += 1
                    
                self.families[family_id] = self.all_lines[i:j]
                i = j
                
            # Parse source records  
            elif re.match(r'^0 @S\d+@ SOUR', line):
                source_id = line.split()[1]
                source_lines = []
                
                j = i + 1
                while j < len(self.all_lines) and not self.all_lines[j].strip().startswith('0 '):
                    source_lines.append(self.all_lines[j])
                    j += 1
                    
                self.sources[source_id] = self.all_lines[i:j]
                i = j
                
            else:
                i += 1
        
        print(f"✅ Parsed {len(self.individuals)} individuals, {len(self.families)} families, {len(self.sources)} sources")
    
    def find_duplicate_people(self) -> List[Tuple[str, str, float]]:
        """Find potential duplicate people based on name similarity and birth years"""
        print("🔍 Finding duplicate people...")
        
        duplicates = []
        individuals_list = list(self.individuals.items())
        
        for i, (id1, ind1) in enumerate(individuals_list):
            if i % 100 == 0:
                print(f"  Processed {i}/{len(individuals_list)} individuals...")
                
            for id2, ind2 in individuals_list[i+1:]:
                # Skip if names are empty
                if not ind1.name.strip() or not ind2.name.strip():
                    continue
                    
                # Calculate name similarity
                name1_clean = ind1.name.replace('/', '').strip().upper()
                name2_clean = ind2.name.replace('/', '').strip().upper()
                
                name_similarity = fuzz.ratio(name1_clean, name2_clean)
                
                # Birth year factor
                year_bonus = 0
                if ind1.birth_year and ind2.birth_year:
                    year_diff = abs(ind1.birth_year - ind2.birth_year)
                    if year_diff == 0:
                        year_bonus = 20  # Exact match
                    elif year_diff <= 2:
                        year_bonus = 10  # Close match
                    elif year_diff > 10:
                        year_bonus = -15  # Penalty for large differences
                
                # Sex matching bonus
                sex_bonus = 0
                if ind1.sex and ind2.sex:
                    if ind1.sex == ind2.sex:
                        sex_bonus = 5
                    else:
                        sex_bonus = -10  # Penalty for different sexes
                
                total_similarity = min(100, name_similarity + year_bonus + sex_bonus)
                
                if total_similarity >= self.similarity_threshold:
                    duplicates.append((id1, id2, total_similarity))
        
        duplicates = sorted(duplicates, key=lambda x: x[2], reverse=True)
        print(f"✅ Found {len(duplicates)} potential duplicate pairs")
        
        return duplicates
    
    def merge_individuals(self, primary_id: str, secondary_id: str, similarity: float) -> Dict:
        """Merge two individuals while preserving ALL sources and data"""
        print(f"  🔗 Merging {secondary_id} into {primary_id} (similarity: {similarity:.1f}%)")
        
        primary = self.individuals[primary_id]
        secondary = self.individuals[secondary_id]
        
        merge_info = {
            'primary_id': primary_id,
            'secondary_id': secondary_id,
            'similarity': similarity,
            'preserved_data': [],
            'merged_facts': []
        }
        
        # Strategy: Append secondary's data to primary's data, preserving everything
        # We'll insert the secondary's facts into the primary record
        
        primary_lines = primary.raw_lines[:]
        secondary_lines = secondary.raw_lines[:]
        
        # Find insertion point (before the individual ends)
        insertion_point = len(primary_lines)
        
        # Extract facts from secondary (skip the header line)
        facts_to_merge = []
        for line in secondary_lines[1:]:  # Skip "0 @I123@ INDI"
            if line.strip() and not line.strip().startswith('0 '):
                facts_to_merge.append(line)
                
        # Insert secondary facts into primary
        if facts_to_merge:
            # Add comment about merge
            merge_comment = f"1 NOTE Merged data from {secondary_id} (similarity: {similarity:.1f}%)\n"
            primary_lines.insert(insertion_point, merge_comment)
            
            # Add all facts from secondary
            for fact_line in facts_to_merge:
                primary_lines.insert(insertion_point + 1, fact_line)
                insertion_point += 1
                
            merge_info['preserved_data'].append(f"Added {len(facts_to_merge)} fact lines from {secondary_id}")
        
        # Update primary individual's raw lines
        primary.raw_lines = primary_lines
        
        # Merge family relationships
        for fam_id in secondary.families_as_spouse:
            if fam_id not in primary.families_as_spouse:
                primary.families_as_spouse.append(fam_id)
                merge_info['preserved_data'].append(f"Added spouse family {fam_id}")
                
        for fam_id in secondary.families_as_child:
            if fam_id not in primary.families_as_child:
                primary.families_as_child.append(fam_id)
                merge_info['preserved_data'].append(f"Added child family {fam_id}")
        
        # Update family records to point to primary instead of secondary
        self._update_family_references(secondary_id, primary_id)
        
        # Mark secondary for removal
        self.removed_individuals.add(secondary_id)
        
        self.merge_log.append(merge_info)
        return merge_info
    
    def _update_family_references(self, old_id: str, new_id: str) -> None:
        """Update family records to reference the merged individual"""
        for family_id, family_lines in self.families.items():
            updated_lines = []
            for line in family_lines:
                # Replace references to old ID with new ID
                if old_id in line:
                    line = line.replace(old_id, new_id)
                updated_lines.append(line)
            self.families[family_id] = updated_lines
    
    def process_duplicates(self, duplicates: List[Tuple[str, str, float]]) -> Dict:
        """Process duplicate pairs"""
        print("🔄 Processing duplicates...")
        
        results = {
            'auto_merged': 0,
            'requires_review': [],
            'errors': 0
        }
        
        for primary_id, secondary_id, similarity in duplicates:
            # Skip if either individual was already removed
            if primary_id in self.removed_individuals or secondary_id in self.removed_individuals:
                continue
                
            try:
                if similarity >= self.auto_merge_threshold:
                    # Auto-merge high-confidence matches
                    self.merge_individuals(primary_id, secondary_id, similarity)
                    results['auto_merged'] += 1
                else:
                    # Flag for manual review
                    results['requires_review'].append({
                        'primary_id': primary_id,
                        'secondary_id': secondary_id, 
                        'similarity': similarity,
                        'primary_name': self.individuals[primary_id].name,
                        'secondary_name': self.individuals[secondary_id].name,
                        'primary_birth': self.individuals[primary_id].birth_date,
                        'secondary_birth': self.individuals[secondary_id].birth_date
                    })
            except Exception as e:
                print(f"    ❌ Error merging {primary_id} + {secondary_id}: {e}")
                results['errors'] += 1
        
        print(f"✅ Auto-merged: {results['auto_merged']}")
        print(f"📋 Requires review: {len(results['requires_review'])}")
        print(f"❌ Errors: {results['errors']}")
        
        return results
    
    def write_cleaned_gedcom(self, output_path: Path) -> Dict:
        """Write the deduplicated GEDCOM file preserving all sources"""
        print(f"💾 Writing cleaned GEDCOM to: {output_path}")
        
        with open(output_path, 'w', encoding='utf-8') as f:
            # Write header
            f.write("0 HEAD\n")
            f.write("1 SOUR SourcePreservingDeduplicator\n")
            f.write("2 NAME Source-Preserving Person Deduplicator\n")
            f.write("2 VERS 1.0\n")
            f.write("1 CHAR UTF-8\n")
            f.write("1 GEDC\n")
            f.write("2 VERS 5.5.1\n")
            f.write("2 FORM LINEAGE-LINKED\n")
            f.write("1 NOTE Person deduplication completed while preserving all sources\n")
            f.write("\n")
            
            # Write all sources first (completely preserved)
            for source_id, source_lines in self.sources.items():
                for line in source_lines:
                    f.write(line)
                f.write("\n")
            
            # Write individuals (excluding removed ones)
            individuals_written = 0
            for individual_id, individual in self.individuals.items():
                if individual_id in self.removed_individuals:
                    continue
                    
                # Write all lines for this individual
                for line in individual.raw_lines:
                    f.write(line)
                f.write("\n")
                individuals_written += 1
            
            # Write families (with updated references)
            families_written = 0
            for family_id, family_lines in self.families.items():
                for line in family_lines:
                    f.write(line)
                f.write("\n")
                families_written += 1
            
            # Write trailer
            f.write("0 TRLR\n")
        
        result = {
            'output_file': str(output_path),
            'individuals_written': individuals_written,
            'individuals_removed': len(self.removed_individuals),
            'families_written': families_written,
            'sources_written': len(self.sources)
        }
        
        print(f"✅ Written: {individuals_written} individuals, {families_written} families, {len(self.sources)} sources")
        return result

def main():
    parser = argparse.ArgumentParser(description="Source-preserving person deduplication")
    parser.add_argument("input_file", type=Path, help="Input GEDCOM file")
    parser.add_argument("output_file", type=Path, help="Output GEDCOM file")
    parser.add_argument("--similarity-threshold", type=float, default=90.0, help="Similarity threshold for detection")
    parser.add_argument("--auto-merge-threshold", type=float, default=98.0, help="Auto-merge threshold")
    parser.add_argument("--report", type=Path, help="Output report file")
    parser.add_argument("--verbose", "-v", action="store_true", help="Verbose output")
    
    args = parser.parse_args()
    
    if not args.input_file.exists():
        print(f"❌ Error: Input file not found: {args.input_file}")
        sys.exit(1)
    
    # Ensure output directory exists
    args.output_file.parent.mkdir(parents=True, exist_ok=True)
    
    try:
        print("🚀 Starting source-preserving person deduplication")
        
        # Initialize deduplicator
        deduplicator = SourcePreservingPersonDeduplicator(
            args.similarity_threshold, 
            args.auto_merge_threshold
        )
        
        # Load and process
        deduplicator.load_gedcom(args.input_file)
        duplicates = deduplicator.find_duplicate_people()
        processing_results = deduplicator.process_duplicates(duplicates)
        output_results = deduplicator.write_cleaned_gedcom(args.output_file)
        
        # Generate report
        report = {
            'input_file': str(args.input_file),
            'output_file': str(args.output_file),
            'settings': {
                'similarity_threshold': args.similarity_threshold,
                'auto_merge_threshold': args.auto_merge_threshold
            },
            'results': {
                'duplicates_found': len(duplicates),
                'auto_merged': processing_results['auto_merged'],
                'requires_manual_review': len(processing_results['requires_review']),
                'errors': processing_results['errors'],
                'individuals_removed': output_results['individuals_removed'],
                'sources_preserved': output_results['sources_written']
            },
            'manual_review_needed': processing_results['requires_review'],
            'merge_log': deduplicator.merge_log
        }
        
        if args.report:
            with open(args.report, 'w', encoding='utf-8') as f:
                json.dump(report, f, indent=2, ensure_ascii=False)
            print(f"📊 Report written to: {args.report}")
        
        print("\n✅ Source-preserving person deduplication completed!")
        print(f"   Original: {len(deduplicator.individuals)} individuals")
        print(f"   Final: {output_results['individuals_written']} individuals") 
        print(f"   Merged: {output_results['individuals_removed']} duplicates")
        print(f"   Sources: {output_results['sources_written']} preserved")
        print(f"   Manual review needed: {len(processing_results['requires_review'])}")
        
    except Exception as e:
        print(f"❌ Error: {e}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
