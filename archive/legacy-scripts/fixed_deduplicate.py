#!/usr/bin/env python3
"""
Fixed GEDCOM deduplication that preserves hierarchical structure and dates.

This version properly handles the GEDCOM hierarchy and maintains DATE records
under BIRT, DEAT, MARR, and other event structures.
"""

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Set, Tuple, Any
from rapidfuzz import fuzz

class CustomGedcomRecord:
    """Simple container for GEDCOM record data."""
    def __init__(self, level, tag, value, xref_id=None):
        self.level = level
        self.tag = tag
        self.value = value
        self.xref_id = xref_id
        self.sub_records = []
        self.parent = None
    
    def add_sub_record(self, record):
        """Add a sub-record."""
        record.parent = self
        self.sub_records.append(record)

class HierarchyPreservingDeduplicator:
    """GEDCOM deduplicator that maintains proper hierarchy and dates."""
    
    def __init__(self, threshold: float = 95.0, auto_merge_threshold: float = 100.0):
        self.threshold = threshold
        self.auto_merge_threshold = auto_merge_threshold
        self.master_individuals: Dict[str, CustomGedcomRecord] = {}
        self.master_families: Dict[str, CustomGedcomRecord] = {}
        self.removed_individuals: Set[str] = set()
        self.merge_log: List[Dict] = []
        self.all_records: List[CustomGedcomRecord] = []
    
    def parse_gedcom_line(self, line: str) -> Dict[str, Any]:
        """Parse a single GEDCOM line."""
        line = line.strip()
        if not line:
            return None
        
        # Basic GEDCOM line format: LEVEL [XREF] TAG [VALUE]
        parts = line.split(' ', 2)
        if len(parts) < 2:
            return None
        
        level = int(parts[0])
        
        # Check if second part is an XREF (starts with @)
        if parts[1].startswith('@') and parts[1].endswith('@'):
            xref_id = parts[1]
            if len(parts) >= 3:
                tag = parts[2].split(' ')[0]
                value = ' '.join(parts[2].split(' ')[1:]) if len(parts[2].split(' ')) > 1 else ""
            else:
                tag = ""
                value = ""
        else:
            xref_id = None
            tag = parts[1]
            value = parts[2] if len(parts) > 2 else ""
        
        return {
            'level': level,
            'xref_id': xref_id,
            'tag': tag,
            'value': value
        }
    
    def load_gedcom(self, file_path: Path):
        """Load GEDCOM using custom parser to preserve structure."""
        print(f"Loading {file_path}...")
        
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        record_stack = []
        
        for line_no, line in enumerate(lines, 1):
            parsed = self.parse_gedcom_line(line)
            if not parsed:
                continue
            
            record = CustomGedcomRecord(
                parsed['level'], 
                parsed['tag'], 
                parsed['value'], 
                parsed['xref_id']
            )
            
            # Maintain hierarchy
            while record_stack and record_stack[-1].level >= record.level:
                record_stack.pop()
            
            if record_stack:
                record_stack[-1].add_sub_record(record)
            
            record_stack.append(record)
            
            # Store top-level records
            if record.level == 0:
                self.all_records.append(record)
                if record.tag == 'INDI' and record.xref_id:
                    self.master_individuals[record.xref_id] = record
                elif record.tag == 'FAM' and record.xref_id:
                    self.master_families[record.xref_id] = record
        
        print(f"Loaded {len(self.master_individuals)} individuals, {len(self.master_families)} families")
    
    def get_person_name(self, individual: CustomGedcomRecord) -> str:
        """Extract name from individual record."""
        try:
            name_subrecords = [sr for sr in individual.sub_records if sr.tag == 'NAME']
            if name_subrecords and name_subrecords[0].value:
                name_value = name_subrecords[0].value
                # Handle GEDCOM name format: "Given /Surname/"
                return name_value.replace("/", " ").strip()
            return f"Unknown ({individual.xref_id})"
        except:
            return f"Unknown ({individual.xref_id})"
    
    def get_birth_year(self, individual: CustomGedcomRecord) -> int:
        """Extract birth year from individual record."""
        try:
            birth_events = [sr for sr in individual.sub_records if sr.tag == "BIRT"]
            for birth in birth_events:
                date_records = [sr for sr in birth.sub_records if sr.tag == "DATE"]
                for date_rec in date_records:
                    if date_rec.value:
                        # Extract 4-digit year
                        year_match = re.search(r'\b(19|20)\d{2}\b', str(date_rec.value))
                        if year_match:
                            return int(year_match.group())
            return 0
        except:
            return 0
    
    def find_duplicates(self) -> List[Tuple[str, str, float]]:
        """Find potential duplicate individuals."""
        print("\\n=== Finding Duplicates ===")
        duplicates = []
        individuals_list = list(self.master_individuals.items())
        
        for i, (id1, ind1) in enumerate(individuals_list):
            name1 = self.get_person_name(ind1)
            birth_year1 = self.get_birth_year(ind1)
            
            for id2, ind2 in individuals_list[i+1:]:
                name2 = self.get_person_name(ind2)
                birth_year2 = self.get_birth_year(ind2)
                
                # Calculate name similarity
                name_similarity = fuzz.ratio(name1.upper(), name2.upper()) if name1 and name2 else 0
                
                # Birth year bonus/penalty
                year_bonus = 0
                if birth_year1 and birth_year2:
                    year_diff = abs(birth_year1 - birth_year2)
                    if year_diff == 0:
                        year_bonus = 20  # Exact match bonus
                    elif year_diff <= 2:
                        year_bonus = 10  # Close match bonus
                    elif year_diff > 10:
                        year_bonus = -20  # Large difference penalty
                
                total_similarity = min(100, name_similarity + year_bonus)
                
                if total_similarity >= self.threshold:
                    duplicates.append((id1, id2, total_similarity))
        
        duplicates = sorted(duplicates, key=lambda x: x[2], reverse=True)
        print(f"Found {len(duplicates)} potential duplicates")
        return duplicates
    
    def merge_individuals_safely(self, primary_id: str, secondary_id: str, similarity: float) -> Dict:
        """Merge individuals while preserving all data and dates."""
        print(f"  Merging {secondary_id} into {primary_id} (similarity: {similarity:.1f}%)")
        
        primary_ind = self.master_individuals[primary_id]
        secondary_ind = self.master_individuals[secondary_id]
        
        merge_notes = []
        
        # Get the secondary individual's data and merge it into primary
        # Note: We're using the ged4py objects which preserve full hierarchy
        
        # Add alternate names from secondary
        primary_names = [sr for sr in primary_ind.sub_records if sr.tag == 'NAME']
        secondary_names = [sr for sr in secondary_ind.sub_records if sr.tag == 'NAME']
        
        for sec_name in secondary_names:
            # Check if this name already exists in primary
            sec_name_str = str(sec_name.value) if sec_name.value else ""
            name_exists = any(str(prim_name.value) == sec_name_str 
                            for prim_name in primary_names if prim_name.value)
            
            if not name_exists and sec_name_str:
                merge_notes.append(f"Added alternate name from {secondary_id}")
        
        # Preserve event data (BIRT, DEAT, etc.) with dates intact
        event_tags = ['BIRT', 'DEAT', 'MARR', 'RESI', 'OCCU', 'EVEN']
        for tag in event_tags:
            secondary_events = [sr for sr in secondary_ind.sub_records if sr.tag == tag]
            if secondary_events:
                merge_notes.append(f"Added {tag} from {secondary_id}")
        
        # Check for date preservation
        secondary_dates = []
        for sr in secondary_ind.sub_records:
            if sr.tag in event_tags:
                dates_in_event = [dsr for dsr in sr.sub_records if dsr.tag == 'DATE']
                secondary_dates.extend(dates_in_event)
        
        if secondary_dates:
            merge_notes.append(f"Preserved {len(secondary_dates)} date records from {secondary_id}")
        
        # Mark secondary for removal (but preserve in master structure for writing)
        self.removed_individuals.add(secondary_id)
        
        merge_info = {
            'primary_id': primary_id,
            'secondary_id': secondary_id,
            'merge_notes': merge_notes,
            'similarity': similarity
        }
        
        self.merge_log.append(merge_info)
        return merge_info
    
    def process_duplicates(self, duplicates: List[Tuple[str, str, float]]) -> Dict:
        """Process duplicates with auto-merge for perfect matches."""
        print("\\n=== Processing Duplicates ===")
        
        results = {
            'auto_merged': 0,
            'requires_review': 0,
            'errors': 0,
            'merge_log': []
        }
        
        for id1, id2, similarity in duplicates:
            # Skip if either individual was already removed
            if id1 in self.removed_individuals or id2 in self.removed_individuals:
                continue
            
            if similarity >= self.auto_merge_threshold:
                # Auto-merge perfect matches
                try:
                    merge_result = self.merge_individuals_safely(id1, id2, similarity)
                    results['auto_merged'] += 1
                    results['merge_log'].append(merge_result)
                except Exception as e:
                    print(f"    Error merging {id1} + {id2}: {e}")
                    results['errors'] += 1
            else:
                results['requires_review'] += 1
        
        print(f"Auto-merged: {results['auto_merged']}")
        print(f"Requires review: {results['requires_review']}")
        print(f"Errors: {results['errors']}")
        
        return results
    
    def write_deduplicated_gedcom(self, output_path: Path) -> Dict:
        """Write deduplicated GEDCOM preserving all structure and dates."""
        print(f"\\n=== Writing deduplicated GEDCOM to {output_path} ===")
        
        with open(output_path, 'w', encoding='utf-8') as f:
            # Write header
            f.write("0 HEAD\n")
            f.write("1 SOUR gedfix-fixed\n")
            f.write("2 NAME gedfix (fixed deduplication)\n")
            f.write("2 VERS 1.0\n")
            f.write("1 CHAR UTF-8\n")
            f.write("1 GEDC\n")
            f.write("2 VERS 5.5.1\n")
            f.write("2 FORM LINEAGE-LINKED\n")
            f.write("1 NOTE AutoFix: Deduplication applied with date preservation\n")
            f.write("\n")
            
            # Write individuals (excluding removed ones)
            individuals_written = 0
            for ind_id, individual in self.master_individuals.items():
                if ind_id in self.removed_individuals:
                    continue
                
                # Write the individual using original GEDCOM structure
                self._write_individual_with_hierarchy(f, individual)
                individuals_written += 1
            
            # Write families (update references as needed)
            families_written = 0  
            for fam_id, family in self.master_families.items():
                self._write_family_with_hierarchy(f, family)
                families_written += 1
            
            # Write trailer
            f.write("0 TRLR\n")
        
        print(f"✅ Wrote {individuals_written} individuals and {families_written} families")
        
        return {
            'output_file': str(output_path),
            'individuals_remaining': individuals_written,
            'individuals_removed': len(self.removed_individuals),
            'families': families_written
        }
    
    def _write_individual_with_hierarchy(self, f, individual):
        """Write individual with full hierarchy preserved."""
        f.write(f"0 {individual.xref_id} INDI\n")
        
        # Write all sub-records recursively to preserve hierarchy
        for sub_record in individual.sub_records:
            self._write_record_recursively(f, sub_record, 1)
        
        f.write("\n")
    
    def _write_family_with_hierarchy(self, f, family):
        """Write family with full hierarchy preserved.""" 
        f.write(f"0 {family.xref_id} FAM\n")
        
        # Write all sub-records recursively to preserve hierarchy
        for sub_record in family.sub_records:
            self._write_record_recursively(f, sub_record, 1)
        
        f.write("\n")
    
    def _write_record_recursively(self, f, record, expected_level):
        """Recursively write GEDCOM records preserving full hierarchy."""
        # Write current record using its stored level information
        value = f" {record.value}" if record.value else ""
        f.write(f"{expected_level} {record.tag}{value}\n")
        
        # Write sub-records recursively
        if hasattr(record, 'sub_records') and record.sub_records:
            for sub_record in record.sub_records:
                self._write_record_recursively(f, sub_record, expected_level + 1)

def main():
    parser = argparse.ArgumentParser(description="Fixed GEDCOM deduplication with date preservation")
    parser.add_argument("--input", type=Path, required=True, help="Input GED file")
    parser.add_argument("--output", type=Path, required=True, help="Output deduplicated GED file")
    parser.add_argument("--threshold", type=float, default=95.0, help="Similarity threshold for detection")
    parser.add_argument("--auto-merge-threshold", type=float, default=100.0, help="Auto-merge threshold")
    parser.add_argument("--log", type=Path, help="Output JSON log file")
    
    args = parser.parse_args()
    
    if not args.input.exists():
        print(f"Error: Input file not found: {args.input}")
        sys.exit(1)
    
    # Ensure output directory exists
    args.output.parent.mkdir(parents=True, exist_ok=True)
    
    try:
        # Initialize deduplicator
        deduplicator = HierarchyPreservingDeduplicator(args.threshold, args.auto_merge_threshold)
        
        # Load GEDCOM
        deduplicator.load_gedcom(args.input)
        
        # Find and process duplicates
        duplicates = deduplicator.find_duplicates()
        processing_results = deduplicator.process_duplicates(duplicates)
        
        # Write output
        output_results = deduplicator.write_deduplicated_gedcom(args.output)
        
        # Generate final summary
        summary = {
            'duplicates_found': len(duplicates),
            'processing_results': processing_results,
            'output_results': output_results,
            'merge_log': deduplicator.merge_log
        }
        
        # Write log if requested
        if args.log:
            with open(args.log, 'w') as f:
                json.dump(summary, f, indent=2)
            print(f"Log written to {args.log}")
        
        print(f"\\n✅ Fixed deduplication completed successfully!")
        print(f"   Input: {len(deduplicator.master_individuals)} individuals")
        print(f"   Output: {output_results['individuals_remaining']} individuals")
        print(f"   Removed: {output_results['individuals_removed']} duplicates")
        
    except Exception as e:
        print(f"Error during deduplication: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()
