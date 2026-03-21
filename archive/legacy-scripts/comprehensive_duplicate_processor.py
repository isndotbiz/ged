#!/usr/bin/env python3
"""
Comprehensive Duplicate & Name Processor
- Removes all types of duplicate facts (not just birth/death)
- Adds surnames for people without them (using spouse's surname)
- Standardizes to single surname with alternatives
- Links multiple births (twins/triplets)
- Processes RootsMagic duplicate lists
"""

import argparse
import csv
import re
import sys
from pathlib import Path
from typing import List, Dict, Any, Optional, Tuple, Set
from dataclasses import dataclass
from datetime import datetime

@dataclass
class Individual:
    """Represents a person in the GEDCOM file"""
    id: str
    name: str
    given_names: str = ""
    surname: str = ""
    alternative_names: List[str] = None
    sex: str = ""
    birth_date: str = ""
    birth_place: str = ""
    death_date: str = ""
    families_as_spouse: List[str] = None
    families_as_child: List[str] = None
    raw_lines: List[str] = None
    line_start: int = 0
    line_end: int = 0

    def __post_init__(self):
        if self.alternative_names is None:
            self.alternative_names = []
        if self.families_as_spouse is None:
            self.families_as_spouse = []
        if self.families_as_child is None:
            self.families_as_child = []
        if self.raw_lines is None:
            self.raw_lines = []

@dataclass
class Family:
    """Represents a family in the GEDCOM file"""
    id: str
    husband_id: str = ""
    wife_id: str = ""
    children_ids: List[str] = None
    marriage_date: str = ""
    raw_lines: List[str] = None
    line_start: int = 0
    line_end: int = 0

    def __post_init__(self):
        if self.children_ids is None:
            self.children_ids = []
        if self.raw_lines is None:
            self.raw_lines = []

class ComprehensiveDuplicateProcessor:
    """Comprehensive processor for duplicates and name standardization"""
    
    def __init__(self):
        self.individuals: Dict[str, Individual] = {}
        self.families: Dict[str, Family] = {}
        self.lines: List[str] = []
        self.stats = {
            'individuals_processed': 0,
            'families_processed': 0,
            'duplicates_removed': 0,
            'surnames_added': 0,
            'names_standardized': 0,
            'multiple_births_linked': 0
        }
    
    def load_gedcom(self, gedcom_path: Path) -> None:
        """Load GEDCOM file"""
        print(f"📖 Loading GEDCOM file: {gedcom_path}")
        
        with open(gedcom_path, 'r', encoding='utf-8') as f:
            self.lines = f.readlines()
        
        print(f"✅ Loaded {len(self.lines)} lines")
    
    def parse_gedcom(self) -> None:
        """Parse GEDCOM into individuals and families"""
        print("🔍 Parsing GEDCOM structure...")
        
        i = 0
        while i < len(self.lines):
            line = self.lines[i].strip()
            
            # Parse individuals
            if re.match(r'^0 @I\d+@ INDI', line):
                i = self._parse_individual(i)
            # Parse families
            elif re.match(r'^0 @F\d+@ FAM', line):
                i = self._parse_family(i)
            else:
                i += 1
        
        print(f"✅ Parsed {len(self.individuals)} individuals and {len(self.families)} families")
        self.stats['individuals_processed'] = len(self.individuals)
        self.stats['families_processed'] = len(self.families)
    
    def _parse_individual(self, start_line: int) -> int:
        """Parse an individual record"""
        individual_id = self.lines[start_line].split()[1]
        individual = Individual(id=individual_id, name="", line_start=start_line)
        
        i = start_line
        while i < len(self.lines):
            line = self.lines[i].strip()
            
            # End of individual
            if i > start_line and (line.startswith('0 @') or (line.startswith('0 ') and not line.startswith('0 @'))):
                break
            
            # Parse individual data
            if line.startswith('1 NAME '):
                name_value = line[7:].strip()
                individual.name = name_value
                # Parse name components
                if '/' in name_value:
                    parts = name_value.split('/')
                    individual.given_names = parts[0].strip()
                    if len(parts) > 1:
                        individual.surname = parts[1].strip()
                else:
                    individual.given_names = name_value
            elif line.startswith('1 SEX '):
                individual.sex = line[6:].strip()
            elif line.startswith('1 BIRT'):
                # Look for date and place
                j = i + 1
                while j < len(self.lines) and not self.lines[j].startswith('1 '):
                    subline = self.lines[j].strip()
                    if subline.startswith('2 DATE '):
                        individual.birth_date = subline[7:].strip()
                    elif subline.startswith('2 PLAC '):
                        individual.birth_place = subline[7:].strip()
                    j += 1
            elif line.startswith('1 DEAT'):
                # Look for date
                j = i + 1
                while j < len(self.lines) and not self.lines[j].startswith('1 '):
                    subline = self.lines[j].strip()
                    if subline.startswith('2 DATE '):
                        individual.death_date = subline[7:].strip()
                    j += 1
            elif line.startswith('1 FAMS '):
                family_id = line[7:].strip()
                individual.families_as_spouse.append(family_id)
            elif line.startswith('1 FAMC '):
                family_id = line[7:].strip()
                individual.families_as_child.append(family_id)
            
            individual.raw_lines.append(self.lines[i])
            i += 1
        
        individual.line_end = i - 1
        self.individuals[individual_id] = individual
        return i
    
    def _parse_family(self, start_line: int) -> int:
        """Parse a family record"""
        family_id = self.lines[start_line].split()[1]
        family = Family(id=family_id, line_start=start_line)
        
        i = start_line
        while i < len(self.lines):
            line = self.lines[i].strip()
            
            # End of family
            if i > start_line and (line.startswith('0 @') or (line.startswith('0 ') and not line.startswith('0 @'))):
                break
            
            # Parse family data
            if line.startswith('1 HUSB '):
                family.husband_id = line[7:].strip()
            elif line.startswith('1 WIFE '):
                family.wife_id = line[7:].strip()
            elif line.startswith('1 CHIL '):
                child_id = line[7:].strip()
                family.children_ids.append(child_id)
            elif line.startswith('1 MARR'):
                # Look for date
                j = i + 1
                while j < len(self.lines) and not self.lines[j].startswith('1 '):
                    subline = self.lines[j].strip()
                    if subline.startswith('2 DATE '):
                        family.marriage_date = subline[7:].strip()
                    j += 1
            
            family.raw_lines.append(self.lines[i])
            i += 1
        
        family.line_end = i - 1
        self.families[family_id] = family
        return i
    
    def process_duplicate_list_csv(self, csv_path: Path) -> Set[str]:
        """Process RootsMagic duplicate list CSV and return duplicate individual IDs"""
        print(f"📋 Processing duplicate list: {csv_path}")
        
        duplicate_ids = set()
        
        try:
            with open(csv_path, 'r', encoding='utf-8') as f:
                # Try to detect if this is a CSV with headers
                content = f.read()
                f.seek(0)
                
                # Look for individual IDs in the content
                individual_ids = re.findall(r'@I\d+@', content)
                duplicate_ids.update(individual_ids)
                
                # Also try CSV parsing
                try:
                    reader = csv.DictReader(f)
                    for row in reader:
                        # Look for ID fields in any column
                        for value in row.values():
                            if value and '@I' in str(value):
                                found_ids = re.findall(r'@I\d+@', str(value))
                                duplicate_ids.update(found_ids)
                except:
                    # If CSV parsing fails, we still have the regex matches
                    pass
                    
        except Exception as e:
            print(f"⚠️  Warning: Could not process CSV file: {e}")
            return set()
        
        print(f"🔍 Found {len(duplicate_ids)} individuals mentioned in duplicate list")
        return duplicate_ids
    
    def remove_all_duplicate_facts(self) -> None:
        """Remove all types of duplicate facts from individuals"""
        print("🗑️  Removing all duplicate facts...")
        
        duplicates_removed = 0
        
        for individual in self.individuals.values():
            # Find all fact types in this individual
            fact_counts = {}
            fact_lines = {}  # fact_type -> list of (start_line, end_line, completeness_score)
            
            i = 0
            while i < len(individual.raw_lines):
                line = individual.raw_lines[i].strip()
                
                # Look for level 1 facts
                if re.match(r'^1 (BIRT|DEAT|MARR|DIV|GRAD|OCCU|RELI|RESI|EMIG|IMMI|NATU|BAPM|BARM|BASM|BLES|CHRA|CONF|FCOM|ORDN|ADOP|CENS)', line):
                    fact_type = line.split()[1]
                    
                    if fact_type not in fact_counts:
                        fact_counts[fact_type] = 0
                        fact_lines[fact_type] = []
                    
                    fact_counts[fact_type] += 1
                    
                    # Calculate completeness score for this fact
                    fact_start = i
                    fact_end = i
                    completeness_score = 1  # Base score for having the fact
                    
                    # Look for sub-facts
                    j = i + 1
                    while j < len(individual.raw_lines):
                        sub_line = individual.raw_lines[j].strip()
                        if sub_line.startswith('1 ') or sub_line.startswith('0 '):
                            break
                        
                        fact_end = j
                        
                        if sub_line.startswith('2 DATE '):
                            completeness_score += 10  # Date is very important
                        elif sub_line.startswith('2 PLAC '):
                            completeness_score += 5   # Place is important
                        elif sub_line.startswith('2 SOUR') or sub_line.startswith('1 SOUR'):
                            completeness_score += 3   # Sources are valuable
                        elif sub_line.startswith('2 NOTE') or sub_line.startswith('1 NOTE'):
                            completeness_score += 2   # Notes add value
                        
                        j += 1
                    
                    fact_lines[fact_type].append((fact_start, fact_end, completeness_score))
                    i = fact_end + 1
                else:
                    i += 1
            
            # Remove duplicates for each fact type
            for fact_type, instances in fact_lines.items():
                if len(instances) > 1:
                    # Sort by completeness score (descending)
                    instances.sort(key=lambda x: x[2], reverse=True)
                    
                    # Keep the first (most complete), mark others for removal
                    for start_line, end_line, score in instances[1:]:
                        # Mark lines for removal
                        for line_idx in range(start_line, end_line + 1):
                            individual.raw_lines[line_idx] = "REMOVE_THIS_LINE\n"
                        duplicates_removed += 1
        
        self.stats['duplicates_removed'] = duplicates_removed
        print(f"✅ Removed {duplicates_removed} duplicate facts")
    
    def add_missing_surnames(self) -> None:
        """Add surnames for people without them (use spouse's surname for wives)"""
        print("👤 Adding missing surnames...")
        
        surnames_added = 0
        
        for individual in self.individuals.values():
            if not individual.surname and individual.sex == 'F':
                # Look for husband's surname
                husband_surname = None
                
                for family_id in individual.families_as_spouse:
                    family = self.families.get(family_id)
                    if family and family.husband_id:
                        husband = self.individuals.get(family.husband_id)
                        if husband and husband.surname:
                            husband_surname = husband.surname
                            break
                
                if husband_surname:
                    # Update individual's surname
                    individual.surname = husband_surname
                    
                    # Update the NAME line in raw_lines
                    for i, line in enumerate(individual.raw_lines):
                        if line.strip().startswith('1 NAME '):
                            # Reconstruct name with surname
                            new_name = f"{individual.given_names} /{husband_surname}/"
                            individual.raw_lines[i] = f"1 NAME {new_name}\n"
                            individual.name = new_name
                            surnames_added += 1
                            break
        
        self.stats['surnames_added'] = surnames_added
        print(f"✅ Added {surnames_added} missing surnames")
    
    def standardize_single_surnames(self) -> None:
        """Standardize to single surname, move extras to alternative names"""
        print("📝 Standardizing to single surnames...")
        
        names_standardized = 0
        
        for individual in self.individuals.values():
            if individual.surname and ' ' in individual.surname:
                # Multiple surnames - split them
                surname_parts = individual.surname.split()
                primary_surname = surname_parts[0]
                alternative_surnames = surname_parts[1:]
                
                # Update individual
                individual.surname = primary_surname
                
                # Create alternative names
                for alt_surname in alternative_surnames:
                    alt_name = f"{individual.given_names} /{alt_surname}/"
                    individual.alternative_names.append(alt_name)
                
                # Update the NAME line
                for i, line in enumerate(individual.raw_lines):
                    if line.strip().startswith('1 NAME '):
                        new_name = f"{individual.given_names} /{primary_surname}/"
                        individual.raw_lines[i] = f"1 NAME {new_name}\n"
                        individual.name = new_name
                        
                        # Add alternative name records after the main NAME
                        alt_lines = []
                        for alt_name in individual.alternative_names:
                            alt_lines.append(f"1 NAME {alt_name}\n")
                            alt_lines.append("2 TYPE aka\n")
                        
                        # Insert alternative names
                        individual.raw_lines = (individual.raw_lines[:i+1] + 
                                              alt_lines + 
                                              individual.raw_lines[i+1:])
                        
                        names_standardized += 1
                        break
        
        self.stats['names_standardized'] = names_standardized
        print(f"✅ Standardized {names_standardized} multi-part surnames")
    
    def link_multiple_births(self) -> None:
        """Link multiple births (twins, triplets, etc.)"""
        print("👶 Linking multiple births...")
        
        multiple_births_linked = 0
        
        # Group children by family and birth date
        birth_groups = {}  # family_id -> {date: [child_ids]}
        
        for family in self.families.values():
            birth_groups[family.id] = {}
            
            for child_id in family.children_ids:
                child = self.individuals.get(child_id)
                if child and child.birth_date:
                    # Normalize date for comparison
                    birth_date = child.birth_date.strip()
                    
                    if birth_date not in birth_groups[family.id]:
                        birth_groups[family.id][birth_date] = []
                    
                    birth_groups[family.id][birth_date].append(child_id)
        
        # Link siblings with same birth date
        for family_id, date_groups in birth_groups.items():
            for birth_date, child_ids in date_groups.items():
                if len(child_ids) > 1:
                    # Multiple children with same birth date = twins/triplets
                    print(f"  🔗 Found {len(child_ids)} children born on {birth_date} in family {family_id}")
                    
                    # Add association records to link them
                    for i, child_id in enumerate(child_ids):
                        child = self.individuals[child_id]
                        
                        # Add associations to other siblings
                        for j, sibling_id in enumerate(child_ids):
                            if i != j:
                                # Add association record
                                child.raw_lines.append(f"1 ASSO {sibling_id}\n")
                                child.raw_lines.append("2 RELA Twin\n")
                    
                    multiple_births_linked += len(child_ids)
        
        self.stats['multiple_births_linked'] = multiple_births_linked
        print(f"✅ Linked {multiple_births_linked} individuals in multiple births")
    
    def generate_clean_gedcom(self, output_path: Path) -> None:
        """Generate cleaned GEDCOM file"""
        print(f"💾 Generating cleaned GEDCOM: {output_path}")
        
        with open(output_path, 'w', encoding='utf-8') as f:
            # Write header and other non-individual/family records
            in_individual = False
            in_family = False
            
            for line in self.lines:
                stripped = line.strip()
                
                # Check for individual/family starts
                if re.match(r'^0 @I\d+@ INDI', stripped):
                    in_individual = True
                    individual_id = stripped.split()[1]
                    # Write updated individual
                    individual = self.individuals[individual_id]
                    for raw_line in individual.raw_lines:
                        if raw_line.strip() != "REMOVE_THIS_LINE":
                            f.write(raw_line)
                    in_individual = False
                elif re.match(r'^0 @F\d+@ FAM', stripped):
                    in_family = True
                    family_id = stripped.split()[1]
                    # Write updated family
                    family = self.families[family_id]
                    for raw_line in family.raw_lines:
                        f.write(raw_line)
                    in_family = False
                elif not in_individual and not in_family:
                    # Write other records as-is
                    if not (stripped.startswith('0 @I') or stripped.startswith('0 @F')):
                        f.write(line)
        
        print(f"✅ Clean GEDCOM written to: {output_path}")
    
    def print_summary(self) -> None:
        """Print processing summary"""
        print("\n" + "="*60)
        print("🔧 COMPREHENSIVE PROCESSING SUMMARY")
        print("="*60)
        
        print(f"👥 Individuals processed: {self.stats['individuals_processed']}")
        print(f"👪 Families processed: {self.stats['families_processed']}")
        print(f"🗑️  Duplicate facts removed: {self.stats['duplicates_removed']}")
        print(f"👤 Surnames added: {self.stats['surnames_added']}")
        print(f"📝 Names standardized: {self.stats['names_standardized']}")
        print(f"👶 Multiple births linked: {self.stats['multiple_births_linked']}")
        
        total_improvements = (self.stats['duplicates_removed'] + 
                            self.stats['surnames_added'] + 
                            self.stats['names_standardized'] + 
                            self.stats['multiple_births_linked'])
        
        print(f"\n🎯 Total improvements: {total_improvements}")

def main():
    parser = argparse.ArgumentParser(
        description='Comprehensive duplicate and name processing for GEDCOM files',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s input.ged -o output.ged
  %(prog)s input.ged -o output.ged --duplicate-list duplicates.csv
        """
    )
    
    parser.add_argument('input', help='Input GEDCOM file')
    parser.add_argument('-o', '--output', required=True, help='Output GEDCOM file')
    parser.add_argument('--duplicate-list', help='RootsMagic duplicate list CSV file')
    parser.add_argument('-v', '--verbose', action='store_true', help='Verbose output')
    
    args = parser.parse_args()
    
    try:
        input_path = Path(args.input)
        output_path = Path(args.output)
        
        processor = ComprehensiveDuplicateProcessor()
        
        # Load and parse GEDCOM
        processor.load_gedcom(input_path)
        processor.parse_gedcom()
        
        # Process duplicate list if provided
        if args.duplicate_list:
            duplicate_ids = processor.process_duplicate_list_csv(Path(args.duplicate_list))
        
        # Apply all processing steps
        processor.remove_all_duplicate_facts()
        processor.add_missing_surnames()
        processor.standardize_single_surnames()
        processor.link_multiple_births()
        
        # Generate clean GEDCOM
        processor.generate_clean_gedcom(output_path)
        
        # Show summary
        processor.print_summary()
        
        print(f"\n✅ Comprehensive processing complete!")
        
    except Exception as e:
        print(f"❌ Error: {e}")
        if args.verbose:
            import traceback
            traceback.print_exc()
        sys.exit(1)

if __name__ == '__main__':
    main()
