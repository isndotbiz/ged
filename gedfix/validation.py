from __future__ import annotations
from pathlib import Path
from typing import Dict, List, Set, Tuple
import re

class GedcomValidator:
    """GEDCOM structural validation and consistency checking."""
    
    def __init__(self):
        self.individuals: Dict[str, Dict] = {}
        self.families: Dict[str, Dict] = {}
        self.sources: Dict[str, Dict] = {}
        self.repositories: Dict[str, Dict] = {}
        self.orphaned_records: List[str] = []
        self.missing_references: List[Tuple[str, str]] = []
        self.duplicate_candidates: List[Tuple[str, str, float]] = []
        self.structural_issues: List[Dict] = []
    
    def parse_gedcom_structure(self, file_path: Path) -> Dict:
        """Parse GEDCOM file and extract structural information."""
        current_record = None
        current_level = 0
        line_num = 0
        
        with file_path.open('r', encoding='utf-8', errors='replace') as f:
            for line in f:
                line_num += 1
                line = line.strip()
                if not line:
                    continue
                
                # Parse GEDCOM line format: LEVEL [XREF] TAG [VALUE]
                parts = line.split(' ', 2)
                if len(parts) < 2:
                    continue
                
                try:
                    level = int(parts[0])
                except ValueError:
                    continue
                
                # Handle XREF and TAG
                if parts[1].startswith('@') and parts[1].endswith('@'):
                    # This is a record definition: 0 @I1@ INDI
                    if len(parts) >= 3:
                        xref = parts[1]
                        tag = parts[2]
                        value = ' '.join(parts[3:]) if len(parts) > 3 else ''
                        
                        if level == 0:
                            current_record = xref
                            record_info = {
                                'xref': xref,
                                'tag': tag,
                                'line': line_num,
                                'data': {}
                            }
                            
                            if tag == 'INDI':
                                self.individuals[xref] = record_info
                            elif tag == 'FAM':
                                self.families[xref] = record_info
                            elif tag == 'SOUR':
                                self.sources[xref] = record_info
                            elif tag == 'REPO':
                                self.repositories[xref] = record_info
                else:
                    # This is a regular line: 1 NAME John /Doe/
                    tag = parts[1]
                    value = ' '.join(parts[2:]) if len(parts) > 2 else ''
                    
                    # Store data in current record
                    if current_record and level > 0:
                        record = None
                        if current_record in self.individuals:
                            record = self.individuals[current_record]
                        elif current_record in self.families:
                            record = self.families[current_record]
                        elif current_record in self.sources:
                            record = self.sources[current_record]
                        elif current_record in self.repositories:
                            record = self.repositories[current_record]
                        
                        if record:
                            if tag not in record['data']:
                                record['data'][tag] = []
                            record['data'][tag].append({
                                'level': level,
                                'value': value,
                                'line': line_num
                            })
        
        return {
            'individuals': len(self.individuals),
            'families': len(self.families),
            'sources': len(self.sources),
            'repositories': len(self.repositories)
        }
    
    def validate_references(self):
        """Validate all cross-references in the GEDCOM file."""
        all_xrefs = set()
        all_xrefs.update(self.individuals.keys())
        all_xrefs.update(self.families.keys())
        all_xrefs.update(self.sources.keys())
        all_xrefs.update(self.repositories.keys())
        
        # Check family references
        for fam_id, family in self.families.items():
            # Check HUSB and WIFE references
            for tag in ['HUSB', 'WIFE']:
                if tag in family['data']:
                    for entry in family['data'][tag]:
                        ref = entry['value']
                        if ref not in self.individuals:
                            self.missing_references.append((fam_id, ref))
            
            # Check CHIL references
            if 'CHIL' in family['data']:
                for entry in family['data']['CHIL']:
                    ref = entry['value']
                    if ref not in self.individuals:
                        self.missing_references.append((fam_id, ref))
        
        # Check individual family references
        for ind_id, individual in self.individuals.items():
            # Check FAMS and FAMC references
            for tag in ['FAMS', 'FAMC']:
                if tag in individual['data']:
                    for entry in individual['data'][tag]:
                        ref = entry['value']
                        if ref not in self.families:
                            self.missing_references.append((ind_id, ref))
    
    def detect_orphaned_individuals(self):
        """Detect individuals with no family connections."""
        for ind_id, individual in self.individuals.items():
            has_family = False
            
            # Check if individual has any family connections
            if 'FAMS' in individual['data'] or 'FAMC' in individual['data']:
                has_family = True
            
            # Check if individual is referenced by any family
            for family in self.families.values():
                for tag in ['HUSB', 'WIFE', 'CHIL']:
                    if tag in family['data']:
                        for entry in family['data'][tag]:
                            if entry['value'] == ind_id:
                                has_family = True
                                break
            
            if not has_family:
                self.orphaned_records.append(ind_id)
    
    def validate_required_fields(self):
        """Check for missing required fields."""
        # Individuals should have at least a name
        for ind_id, individual in self.individuals.items():
            if 'NAME' not in individual['data']:
                self.structural_issues.append({
                    'type': 'missing_required_field',
                    'record': ind_id,
                    'field': 'NAME',
                    'line': individual['line']
                })
        
        # Families should have at least one spouse or child
        for fam_id, family in self.families.items():
            has_members = any(tag in family['data'] for tag in ['HUSB', 'WIFE', 'CHIL'])
            if not has_members:
                self.structural_issues.append({
                    'type': 'empty_family',
                    'record': fam_id,
                    'line': family['line']
                })
    
    def detect_potential_duplicates(self, threshold: float = 85.0):
        """Detect potential duplicate individuals using name similarity."""
        from rapidfuzz import fuzz
        
        individuals_list = list(self.individuals.items())
        
        for i, (id1, ind1) in enumerate(individuals_list):
            name1 = ""
            if 'NAME' in ind1['data'] and ind1['data']['NAME']:
                name1 = ind1['data']['NAME'][0]['value']
            
            for id2, ind2 in individuals_list[i+1:]:
                name2 = ""
                if 'NAME' in ind2['data'] and ind2['data']['NAME']:
                    name2 = ind2['data']['NAME'][0]['value']
                
                if name1 and name2:
                    similarity = fuzz.ratio(name1.upper(), name2.upper())
                    if similarity >= threshold:
                        self.duplicate_candidates.append((id1, id2, similarity))
    
    def validate_dates(self):
        """Validate date consistency (birth before death, marriage dates, etc.)."""
        for ind_id, individual in self.individuals.items():
            birth_date = None
            death_date = None
            
            # Extract birth and death dates
            if 'BIRT' in individual['data']:
                # Look for DATE under BIRT
                pass  # TODO: Implement date parsing and comparison
            
            if 'DEAT' in individual['data']:
                # Look for DATE under DEAT
                pass  # TODO: Implement date parsing and comparison
    
    def generate_report(self) -> Dict:
        """Generate comprehensive validation report."""
        return {
            'summary': {
                'individuals': len(self.individuals),
                'families': len(self.families),
                'sources': len(self.sources),
                'repositories': len(self.repositories)
            },
            'issues': {
                'orphaned_individuals': len(self.orphaned_records),
                'missing_references': len(self.missing_references),
                'structural_issues': len(self.structural_issues),
                'potential_duplicates': len(self.duplicate_candidates)
            },
            'details': {
                'orphaned_records': self.orphaned_records,
                'missing_references': self.missing_references,
                'structural_issues': self.structural_issues,
                'duplicate_candidates': self.duplicate_candidates
            }
        }

def validate_gedcom_structure(file_path: Path) -> Dict:
    """Run comprehensive GEDCOM validation."""
    validator = GedcomValidator()
    
    # Parse structure
    structure_info = validator.parse_gedcom_structure(file_path)
    
    # Run validations
    validator.validate_references()
    validator.detect_orphaned_individuals()
    validator.validate_required_fields()
    validator.detect_potential_duplicates()
    validator.validate_dates()
    
    return validator.generate_report()
