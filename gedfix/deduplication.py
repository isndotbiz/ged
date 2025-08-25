from __future__ import annotations
from pathlib import Path
from typing import Dict, List, Set, Tuple
import re
from rapidfuzz import fuzz
from .validation import GedcomValidator

class DuplicateRemover:
    """GEDCOM duplicate detection and removal with safe merging."""
    
    def __init__(self, threshold: float = 95.0):
        self.threshold = threshold
        self.validator = GedcomValidator()
        self.individuals: Dict[str, Dict] = {}
        self.families: Dict[str, Dict] = {}
        self.merged_individuals: Dict[str, str] = {}  # old_id -> new_id
        self.merge_log: List[Dict] = []
        self.removed_ids: Set[str] = set()
    
    def load_gedcom(self, file_path: Path) -> None:
        """Load GEDCOM file for duplicate processing."""
        self.validator.parse_gedcom_structure(file_path)
        self.individuals = self.validator.individuals.copy()
        self.families = self.validator.families.copy()
    
    def find_duplicates(self, threshold: float = None) -> List[Tuple[str, str, float]]:
        """Find potential duplicate individuals."""
        if threshold is None:
            threshold = self.threshold
        
        duplicates = []
        individuals_list = list(self.individuals.items())
        
        for i, (id1, ind1) in enumerate(individuals_list):
            name1 = self._get_primary_name(ind1)
            birth_year1 = self._get_birth_year(ind1)
            
            for id2, ind2 in individuals_list[i+1:]:
                name2 = self._get_primary_name(ind2)
                birth_year2 = self._get_birth_year(ind2)
                
                # Calculate similarity
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
                
                if total_similarity >= threshold:
                    duplicates.append((id1, id2, total_similarity))
        
        return sorted(duplicates, key=lambda x: x[2], reverse=True)
    
    def _get_primary_name(self, individual: Dict) -> str:
        """Extract primary name from individual record."""
        if 'NAME' in individual['data'] and individual['data']['NAME']:
            return individual['data']['NAME'][0]['value']
        return ""
    
    def _get_birth_year(self, individual: Dict) -> int | None:
        """Extract birth year from individual record."""
        if 'BIRT' not in individual['data']:
            return None
        
        # Look for DATE entries after BIRT
        # This is a simplified extraction - in real implementation you'd need to 
        # parse the hierarchical structure properly
        birth_entries = individual['data']['BIRT']
        for entry in birth_entries:
            if 'DATE' in entry.get('sub_data', {}):
                date_value = entry['sub_data']['DATE'][0]['value']
                # Extract 4-digit year
                year_match = re.search(r'\b(19|20)\d{2}\b', date_value)
                if year_match:
                    return int(year_match.group())
        return None
    
    def merge_individuals(self, id1: str, id2: str, keep_primary: str = None) -> Dict:
        """Merge two individuals, keeping the best data from both."""
        if id1 not in self.individuals or id2 not in self.individuals:
            return {"error": "One or both individuals not found"}
        
        ind1 = self.individuals[id1]
        ind2 = self.individuals[id2]
        
        # Determine which to keep as primary
        primary_id = keep_primary or id1
        secondary_id = id2 if primary_id == id1 else id1
        primary_ind = self.individuals[primary_id]
        secondary_ind = self.individuals[secondary_id]
        
        # Merge data
        merged_data = primary_ind['data'].copy()
        merge_notes = []
        
        # Merge fields from secondary individual
        for tag, entries in secondary_ind['data'].items():
            if tag not in merged_data:
                merged_data[tag] = entries
                merge_notes.append(f"Added {tag} from {secondary_id}")
            elif tag == 'NAME':
                # Keep all name variants
                for entry in entries:
                    if entry not in merged_data[tag]:
                        merged_data[tag].append(entry)
                        merge_notes.append(f"Added alternate name from {secondary_id}")
            elif tag in ['BIRT', 'DEAT', 'MARR']:
                # Compare and keep most complete event data
                if len(entries) > len(merged_data[tag]):
                    merged_data[tag] = entries
                    merge_notes.append(f"Used more complete {tag} data from {secondary_id}")
        
        # Add merge note
        merged_data.setdefault('NOTE', []).append({
            'level': 1,
            'value': f'AutoFix: Merged duplicate individual {secondary_id}. Combined data from both records.',
            'line': 0
        })
        
        # Update the primary individual
        self.individuals[primary_id]['data'] = merged_data
        
        # Track the merge
        self.merged_individuals[secondary_id] = primary_id
        self.removed_ids.add(secondary_id)
        
        merge_info = {
            'primary_id': primary_id,
            'secondary_id': secondary_id,
            'merge_notes': merge_notes,
            'similarity': fuzz.ratio(
                self._get_primary_name(primary_ind),
                self._get_primary_name(secondary_ind)
            )
        }
        self.merge_log.append(merge_info)
        
        # Update family references
        self._update_family_references(secondary_id, primary_id)
        
        return merge_info
    
    def _update_family_references(self, old_id: str, new_id: str) -> None:
        """Update all family references when individuals are merged."""
        for family in self.families.values():
            # Update spouse references
            for spouse_tag in ['HUSB', 'WIFE']:
                if spouse_tag in family['data']:
                    for entry in family['data'][spouse_tag]:
                        if entry['value'] == old_id:
                            entry['value'] = new_id
            
            # Update child references
            if 'CHIL' in family['data']:
                for entry in family['data']['CHIL']:
                    if entry['value'] == old_id:
                        entry['value'] = new_id
    
    def remove_duplicates_batch(self, duplicates: List[Tuple[str, str, float]], 
                              auto_merge_threshold: float = 100.0) -> Dict:
        """Remove duplicates in batch with safety checks."""
        results = {
            'auto_merged': 0,
            'requires_review': 0,
            'errors': 0,
            'merge_log': []
        }
        
        for id1, id2, similarity in duplicates:
            # Skip if either individual was already removed
            if id1 in self.removed_ids or id2 in self.removed_ids:
                continue
            
            if similarity >= auto_merge_threshold:
                # Auto-merge perfect matches
                merge_result = self.merge_individuals(id1, id2)
                if 'error' not in merge_result:
                    results['auto_merged'] += 1
                    results['merge_log'].append(merge_result)
                else:
                    results['errors'] += 1
            else:
                results['requires_review'] += 1
        
        return results
    
    def write_deduplicated_gedcom(self, output_path: Path) -> Dict:
        """Write deduplicated GEDCOM file."""
        output_lines = []
        
        # Write header (simplified)
        output_lines.extend([
            "0 HEAD",
            "1 SOUR gedfix",
            "1 CHAR UTF-8",
            "1 NOTE AutoFix: Duplicates removed using comprehensive automation"
        ])
        
        # Write individuals (excluding removed ones)
        for ind_id, individual in self.individuals.items():
            if ind_id in self.removed_ids:
                continue
            
            output_lines.append(f"0 {ind_id} INDI")
            for tag, entries in individual['data'].items():
                for entry in entries:
                    output_lines.append(f"{entry['level']} {tag} {entry['value']}")
        
        # Write families
        for fam_id, family in self.families.items():
            output_lines.append(f"0 {fam_id} FAM")
            for tag, entries in family['data'].items():
                for entry in entries:
                    output_lines.append(f"{entry['level']} {tag} {entry['value']}")
        
        output_lines.append("0 TRLR")
        
        # Write to file
        with output_path.open('w', encoding='utf-8') as f:
            for line in output_lines:
                f.write(line + '\n')
        
        return {
            'output_file': str(output_path),
            'individuals_remaining': len([i for i in self.individuals if i not in self.removed_ids]),
            'individuals_removed': len(self.removed_ids),
            'families': len(self.families)
        }

def deduplicate_gedcom(input_path: Path, output_path: Path, 
                      threshold: float = 95.0, auto_merge_threshold: float = 100.0,
                      backup_dir: Path = None) -> Dict:
    """Main function to deduplicate a GEDCOM file."""
    from .io_utils import backup_file
    
    # Backup original
    if backup_dir:
        backup_file(input_path, backup_dir)
    
    # Initialize deduplicator
    deduplicator = DuplicateRemover(threshold)
    deduplicator.load_gedcom(input_path)
    
    # Find duplicates
    duplicates = deduplicator.find_duplicates()
    
    # Remove duplicates
    batch_results = deduplicator.remove_duplicates_batch(duplicates, auto_merge_threshold)
    
    # Write output
    write_results = deduplicator.write_deduplicated_gedcom(output_path)
    
    return {
        'duplicates_found': len(duplicates),
        'processing_results': batch_results,
        'output_results': write_results,
        'merge_log': deduplicator.merge_log
    }
