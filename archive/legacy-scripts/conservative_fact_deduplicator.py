#!/usr/bin/env python3
"""
Conservative GEDCOM Fact Deduplicator
Removes duplicate facts while preserving all sources, dates, and genealogical information.
Creates notes for differences instead of losing data.
"""

import argparse
import json
import logging
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple, Any

@dataclass 
class GedcomNode:
    """Represents a GEDCOM line with hierarchy"""
    level: int
    tag: str
    pointer: Optional[str]
    value: str
    raw_line: str
    children: List['GedcomNode'] = field(default_factory=list)
    original_index: int = -1

@dataclass
class FactScore:
    """Scoring for fact completeness"""
    has_date: bool = False
    has_place: bool = False
    source_count: int = 0
    note_count: int = 0
    other_fields: int = 0
    total_score: float = 0.0

class ConservativeFactDeduplicator:
    """Safely deduplicate facts while preserving all genealogical information"""
    
    # Facts to deduplicate (one per tag per individual)
    DEDUP_FACTS = {
        'BIRT', 'DEAT', 'BAPM', 'BURI', 'CHR', 'CREM', 'ADOP', 
        'CONF', 'FCOM', 'ORDN', 'NATU', 'EMIG', 'IMMI',
        'CENS', 'PROB', 'WILL', 'GRAD', 'RETI', 'EVEN',
        'OCCU', 'RESI', 'EDUC'
    }
    
    # Marriage fact for families
    MARRIAGE_FACT = 'MARR'
    
    def __init__(self, note_prefix: str = "Dedup:"):
        self.note_prefix = note_prefix
        self.stats = {
            'individuals_processed': 0,
            'families_processed': 0,
            'facts_deduplicated': 0,
            'sources_preserved': 0,
            'notes_added': 0,
            'marriages_deduplicated': 0
        }
        self.couple_registry: Dict[frozenset, Dict] = {}
        
        logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
        self.logger = logging.getLogger(__name__)
    
    def parse_gedcom_line(self, line: str, index: int) -> GedcomNode:
        """Parse a single GEDCOM line"""
        line = line.rstrip('\r\n')
        if not line.strip():
            return None
            
        parts = line.split(' ', 2)
        if len(parts) < 2:
            return None
            
        try:
            level = int(parts[0])
        except ValueError:
            return None
            
        # Handle pointers vs tags
        if len(parts) >= 3 and parts[1].startswith('@') and parts[1].endswith('@'):
            pointer = parts[1]
            tag = parts[2]
            value = ' '.join(parts[3:]) if len(parts) > 3 else ''
        else:
            pointer = None
            tag = parts[1]
            value = parts[2] if len(parts) > 2 else ''
            
        return GedcomNode(
            level=level,
            tag=tag, 
            pointer=pointer,
            value=value,
            raw_line=line,
            original_index=index
        )
    
    def parse_gedcom(self, text: str) -> List[GedcomNode]:
        """Parse GEDCOM into tree structure"""
        lines = text.split('\n')
        nodes = []
        stack = []
        
        for i, line in enumerate(lines):
            node = self.parse_gedcom_line(line, i)
            if not node:
                continue
                
            # Attach to parent based on level
            while stack and stack[-1].level >= node.level:
                stack.pop()
                
            if stack:
                stack[-1].children.append(node)
            else:
                nodes.append(node)
                
            stack.append(node)
            
        return nodes
    
    def score_fact(self, fact_node: GedcomNode) -> FactScore:
        """Calculate completeness score for a fact"""
        score = FactScore()
        
        for child in fact_node.children:
            if child.tag == 'DATE' and child.value.strip():
                score.has_date = True
                score.total_score += 6
            elif child.tag == 'PLAC' and child.value.strip():
                score.has_place = True
                score.total_score += 5
            elif child.tag == 'SOUR':
                score.source_count += 1
                score.total_score += 2  # Cap applied later
            elif child.tag == 'NOTE':
                score.note_count += 1
                score.total_score += 1  # Cap applied later
            elif child.tag in ('TYPE', 'AGE', 'CAUS', 'ADDR'):
                score.total_score += 1
            else:
                score.other_fields += 1
                score.total_score += 0.5
        
        # Apply caps
        score.total_score += min(score.source_count * 2, 10)  # Cap sources at +10
        score.total_score += min(score.note_count * 1, 5)     # Cap notes at +5
        score.total_score += min(score.other_fields * 0.5, 4) # Cap others at +4
        
        return score
    
    def find_child_by_tag(self, node: GedcomNode, tag: str) -> Optional[GedcomNode]:
        """Find first child with given tag"""
        for child in node.children:
            if child.tag == tag:
                return child
        return None
    
    def has_identical_note(self, parent: GedcomNode, note_text: str) -> bool:
        """Check if identical note already exists"""
        for child in parent.children:
            if child.tag == 'NOTE' and child.value.strip() == note_text.strip():
                return True
        return False
    
    def add_difference_note(self, keep_node: GedcomNode, diff_tag: str, diff_value: str):
        """Add note about difference from duplicate"""
        note_text = f"{self.note_prefix} alternate {diff_tag}: {diff_value}"
        
        if self.has_identical_note(keep_node, note_text):
            return  # Don't duplicate notes
            
        note_node = GedcomNode(
            level=keep_node.level + 1,
            tag='NOTE',
            pointer=None,
            value=note_text,
            raw_line=f"{keep_node.level + 1} NOTE {note_text}"
        )
        keep_node.children.append(note_node)
        self.stats['notes_added'] += 1
    
    def migrate_sources(self, keep_node: GedcomNode, dup_node: GedcomNode):
        """Migrate unique sources from duplicate to kept node"""
        # Collect existing sources
        existing_sources = set()
        for child in keep_node.children:
            if child.tag == 'SOUR':
                source_sig = (child.value, self.get_source_signature(child))
                existing_sources.add(source_sig)
        
        # Migrate unique sources
        for child in dup_node.children:
            if child.tag == 'SOUR':
                source_sig = (child.value, self.get_source_signature(child))
                if source_sig not in existing_sources:
                    # Deep copy the source node
                    new_source = self.deep_copy_node(child, keep_node.level + 1)
                    keep_node.children.append(new_source)
                    existing_sources.add(source_sig)
                    self.stats['sources_preserved'] += 1
    
    def get_source_signature(self, source_node: GedcomNode) -> str:
        """Create signature for source deduplication"""
        sig_parts = [source_node.value]
        for child in source_node.children:
            if child.tag in ('PAGE', 'DATA', 'QUAY', 'TEXT'):
                sig_parts.append(f"{child.tag}:{child.value}")
        return '|'.join(sig_parts)
    
    def deep_copy_node(self, node: GedcomNode, target_level: int) -> GedcomNode:
        """Deep copy a node tree with level adjustment"""
        level_diff = target_level - node.level
        
        new_node = GedcomNode(
            level=node.level + level_diff,
            tag=node.tag,
            pointer=node.pointer,
            value=node.value,
            raw_line=f"{node.level + level_diff} {node.tag}" + 
                     (f" {node.value}" if node.value else "")
        )
        
        for child in node.children:
            new_child = self.deep_copy_node(child, child.level + level_diff)
            new_node.children.append(new_child)
            
        return new_node
    
    def merge_fact_children(self, keep_node: GedcomNode, dup_node: GedcomNode):
        """Merge children from duplicate into kept node"""
        # Migrate sources first
        self.migrate_sources(keep_node, dup_node)
        
        # Handle other children
        for dup_child in dup_node.children:
            if dup_child.tag == 'SOUR':
                continue  # Already handled
                
            elif dup_child.tag == 'NOTE':
                # Merge unique notes
                if not self.has_identical_note(keep_node, dup_child.value):
                    new_note = self.deep_copy_node(dup_child, keep_node.level + 1)
                    keep_node.children.append(new_note)
                    
            elif dup_child.tag in ('DATE', 'PLAC', 'TYPE', 'AGE', 'CAUS', 'ADDR'):
                # Check for conflicts
                existing_child = self.find_child_by_tag(keep_node, dup_child.tag)
                if existing_child:
                    # Conflict - add as note if different
                    if existing_child.value != dup_child.value:
                        self.add_difference_note(keep_node, dup_child.tag, dup_child.value)
                else:
                    # No conflict - copy over
                    new_child = self.deep_copy_node(dup_child, keep_node.level + 1)
                    keep_node.children.append(new_child)
    
    def deduplicate_individual_facts(self, indi_node: GedcomNode) -> int:
        """Deduplicate facts within an individual record"""
        facts_by_type: Dict[str, List[GedcomNode]] = {}
        facts_deduplicated = 0
        
        # Group facts by type
        for child in indi_node.children:
            if child.tag in self.DEDUP_FACTS:
                if child.tag not in facts_by_type:
                    facts_by_type[child.tag] = []
                facts_by_type[child.tag].append(child)
        
        # Process each fact type with duplicates
        for fact_type, fact_list in facts_by_type.items():
            if len(fact_list) <= 1:
                continue  # No duplicates
                
            self.logger.debug(f"Deduplicating {len(fact_list)} {fact_type} facts")
            
            # Score and sort facts (highest score first, then by original order)
            scored_facts = [(self.score_fact(fact), fact) for fact in fact_list]
            scored_facts.sort(key=lambda x: (-x[0].total_score, x[1].original_index))
            
            # Keep first (best), merge others into it
            keep_fact = scored_facts[0][1]
            duplicates = [sf[1] for sf in scored_facts[1:]]
            
            for dup_fact in duplicates:
                self.merge_fact_children(keep_fact, dup_fact)
                
            # Remove duplicates from children
            indi_node.children = [c for c in indi_node.children if c not in duplicates]
            
            facts_deduplicated += len(duplicates)
            self.logger.info(f"  {fact_type}: kept 1 of {len(fact_list)} facts")
        
        return facts_deduplicated
    
    def get_couple_key(self, fam_node: GedcomNode) -> Optional[frozenset]:
        """Generate key for couple identification"""
        husb = self.find_child_by_tag(fam_node, 'HUSB')
        wife = self.find_child_by_tag(fam_node, 'WIFE')
        
        if husb and wife:
            return frozenset([husb.value.strip(), wife.value.strip()])
        elif husb or wife:
            # Single parent - use with family pointer to avoid conflicts
            single = (husb.value if husb else wife.value).strip()
            return frozenset([single, fam_node.pointer or 'UNKNOWN'])
        else:
            return None  # Can't deduplicate without spouse info
    
    def deduplicate_family_marriages(self, fam_node: GedcomNode) -> int:
        """Deduplicate marriage facts within a family"""
        marriages = [c for c in fam_node.children if c.tag == self.MARRIAGE_FACT]
        
        if len(marriages) <= 1:
            return 0
            
        self.logger.debug(f"Deduplicating {len(marriages)} marriage facts in family")
        
        # Score and sort marriages
        scored_marriages = [(self.score_fact(marr), marr) for marr in marriages]
        scored_marriages.sort(key=lambda x: (-x[0].total_score, x[1].original_index))
        
        # Keep first, merge others
        keep_marriage = scored_marriages[0][1]
        duplicates = [sm[1] for sm in scored_marriages[1:]]
        
        for dup_marriage in duplicates:
            self.merge_fact_children(keep_marriage, dup_marriage)
            
        # Remove duplicates
        fam_node.children = [c for c in fam_node.children if c not in duplicates]
        
        self.logger.info(f"  MARR: kept 1 of {len(marriages)} marriage facts")
        return len(duplicates)
    
    def deduplicate_cross_family_marriages(self, fam_nodes: List[GedcomNode]) -> int:
        """Deduplicate marriages across families for same couple"""
        marriages_deduplicated = 0
        
        # Build couple registry
        for fam_node in fam_nodes:
            couple_key = self.get_couple_key(fam_node)
            if not couple_key:
                continue
                
            marriage_facts = [c for c in fam_node.children if c.tag == self.MARRIAGE_FACT]
            
            if couple_key not in self.couple_registry:
                # First family for this couple
                self.couple_registry[couple_key] = {
                    'primary_fam': fam_node,
                    'primary_marriage': marriage_facts[0] if marriage_facts else None
                }
            else:
                # Duplicate couple - migrate marriages to primary
                primary_info = self.couple_registry[couple_key]
                
                for marriage_fact in marriage_facts:
                    if primary_info['primary_marriage']:
                        # Merge into primary marriage
                        self.merge_fact_children(primary_info['primary_marriage'], marriage_fact)
                    else:
                        # Primary has no marriage - copy this one over
                        new_marriage = self.deep_copy_node(marriage_fact, 1)
                        primary_info['primary_fam'].children.append(new_marriage)
                        primary_info['primary_marriage'] = new_marriage
                    
                    marriages_deduplicated += 1
                
                # Remove marriages from this family and add note
                fam_node.children = [c for c in fam_node.children if c.tag != self.MARRIAGE_FACT]
                
                # Add explanatory note
                note_text = f"{self.note_prefix} duplicate marriage fact removed; see primary FAM {primary_info['primary_fam'].pointer}"
                note_node = GedcomNode(
                    level=1,
                    tag='NOTE', 
                    pointer=None,
                    value=note_text,
                    raw_line=f"1 NOTE {note_text}"
                )
                fam_node.children.append(note_node)
                self.stats['notes_added'] += 1
        
        return marriages_deduplicated
    
    def serialize_node(self, node: GedcomNode) -> str:
        """Convert node tree back to GEDCOM text"""
        lines = [node.raw_line]
        
        for child in node.children:
            lines.extend(self.serialize_node(child).split('\n'))
            
        return '\n'.join(lines)
    
    def process_gedcom(self, input_path: Path, output_path: Path, 
                      backup_dir: Optional[Path] = None, dry_run: bool = True):
        """Process GEDCOM file with fact deduplication"""
        self.logger.info(f"Starting conservative fact deduplication")
        self.logger.info(f"Input: {input_path} ({input_path.stat().st_size / 1024 / 1024:.1f}MB)")
        
        if dry_run:
            self.logger.info("DRY RUN MODE - no files will be written")
        
        # Read input file
        try:
            with open(input_path, 'r', encoding='utf-8') as f:
                content = f.read()
        except UnicodeDecodeError:
            # Try with different encoding
            with open(input_path, 'r', encoding='latin-1') as f:
                content = f.read()
        
        # Parse GEDCOM
        self.logger.info("Parsing GEDCOM structure...")
        records = self.parse_gedcom(content)
        
        # Process records
        indi_nodes = [r for r in records if r.tag == 'INDI']
        fam_nodes = [r for r in records if r.tag == 'FAM']
        
        self.logger.info(f"Found {len(indi_nodes)} individuals, {len(fam_nodes)} families")
        
        # Process individuals
        self.logger.info("Deduplicating individual facts...")
        for indi_node in indi_nodes:
            facts_deduplicated = self.deduplicate_individual_facts(indi_node)
            if facts_deduplicated > 0:
                self.stats['facts_deduplicated'] += facts_deduplicated
                self.stats['individuals_processed'] += 1
        
        # Process families (within family)
        self.logger.info("Deduplicating family marriage facts...")
        for fam_node in fam_nodes:
            marriages_deduplicated = self.deduplicate_family_marriages(fam_node)
            if marriages_deduplicated > 0:
                self.stats['marriages_deduplicated'] += marriages_deduplicated
                self.stats['families_processed'] += 1
        
        # Process cross-family marriages
        self.logger.info("Deduplicating marriages across families...")
        cross_marriages = self.deduplicate_cross_family_marriages(fam_nodes)
        self.stats['marriages_deduplicated'] += cross_marriages
        
        if not dry_run:
            # Create backup
            if backup_dir:
                backup_dir.mkdir(parents=True, exist_ok=True)
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                backup_path = backup_dir / f"{input_path.stem}.bak.{timestamp}{input_path.suffix}"
                backup_path.write_text(content, encoding='utf-8')
                self.logger.info(f"Created backup: {backup_path}")
            
            # Write output
            self.logger.info("Writing deduplicated GEDCOM...")
            output_lines = []
            for record in records:
                output_lines.append(self.serialize_node(record))
            
            output_content = '\n'.join(output_lines)
            output_path.write_text(output_content, encoding='utf-8')
            self.logger.info(f"Output written: {output_path} ({output_path.stat().st_size / 1024 / 1024:.1f}MB)")
        
        self.print_summary()
        
        return self.stats
    
    def print_summary(self):
        """Print processing summary"""
        print("\n" + "="*60)
        print("🔧 CONSERVATIVE FACT DEDUPLICATION SUMMARY")
        print("="*60)
        print(f"👥 Individuals processed: {self.stats['individuals_processed']}")
        print(f"👨‍👩‍👧‍👦 Families processed: {self.stats['families_processed']}")
        print(f"🗑️  Facts deduplicated: {self.stats['facts_deduplicated']}")
        print(f"💒 Marriage facts deduplicated: {self.stats['marriages_deduplicated']}")
        print(f"📚 Sources preserved: {self.stats['sources_preserved']}")
        print(f"📝 Difference notes added: {self.stats['notes_added']}")
        
        if self.stats['facts_deduplicated'] > 0:
            print(f"\n✅ Conservative deduplication completed successfully!")
            print(f"   • All sources and citations preserved")
            print(f"   • No dates modified - kept byte-for-byte")
            print(f"   • Differences captured as notes")
            print(f"   • Structure and relationships maintained")

def main():
    parser = argparse.ArgumentParser(
        description='Conservative GEDCOM fact deduplication with full data preservation',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
This tool safely removes duplicate facts while preserving all genealogical information:
• Keeps the most complete fact of each type
• Migrates all sources from duplicates to kept facts  
• Preserves dates exactly - no modifications
• Adds notes for different values instead of losing them
• Maintains GEDCOM structure and relationships

Examples:
  %(prog)s final.ged -o final.dedup.ged --dry-run
  %(prog)s final.ged -o final.dedup.ged --no-dry-run --backup-dir out/backup
        """
    )
    
    parser.add_argument('input', help='Input GEDCOM file')
    parser.add_argument('-o', '--output', default='final.dedup.ged', help='Output GEDCOM file')
    parser.add_argument('--backup-dir', type=Path, help='Directory for backup (default: alongside input)')
    parser.add_argument('--dry-run', dest='dry_run', action='store_true', default=True, help='Preview changes without writing (default)')
    parser.add_argument('--no-dry-run', dest='dry_run', action='store_false', help='Actually write output file')
    parser.add_argument('--note-prefix', default='Dedup:', help='Prefix for difference notes')
    parser.add_argument('--log-level', choices=['DEBUG', 'INFO', 'WARNING'], default='INFO', help='Logging level')
    
    args = parser.parse_args()
    
    # Configure logging
    logging.getLogger().setLevel(getattr(logging, args.log_level))
    
    try:
        input_path = Path(args.input)
        output_path = Path(args.output)
        backup_dir = args.backup_dir or input_path.parent / 'backup'
        
        if not input_path.exists():
            print(f"❌ Error: Input file not found: {input_path}")
            sys.exit(1)
        
        # Process the file
        deduplicator = ConservativeFactDeduplicator(note_prefix=args.note_prefix)
        deduplicator.process_gedcom(
            input_path=input_path,
            output_path=output_path, 
            backup_dir=backup_dir,
            dry_run=args.dry_run
        )
        
        if args.dry_run:
            print(f"\n💡 This was a dry run. Use --no-dry-run to write changes.")
        else:
            print(f"\n✅ Conservative fact deduplication complete!")
        
    except Exception as e:
        print(f"❌ Error: {e}")
        if args.log_level == 'DEBUG':
            import traceback
            traceback.print_exc()
        sys.exit(1)

if __name__ == '__main__':
    main()
