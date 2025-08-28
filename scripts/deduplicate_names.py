#!/usr/bin/env python3
"""
GEDCOM Name Deduplicator - Removes duplicate alternate names from GEDCOM files.

This script identifies and removes duplicate NAME records for each individual,
keeping only unique name variations while preserving sources and citations.
"""

import re
import argparse
import logging
from datetime import datetime
from pathlib import Path
from typing import List, Dict, Tuple, Set
import shutil

def setup_logging():
    """Configure logging for the application."""
    logging.basicConfig(
        level=logging.INFO,
        format='%(levelname)s: %(message)s'
    )

def normalize_name_for_comparison(name_line: str) -> str:
    """
    Normalize a name line for comparison by removing extra whitespace and 
    standardizing the format.
    """
    # Extract the name part (everything after "1 NAME ")
    if name_line.startswith('1 NAME '):
        name_part = name_line[7:].strip()
    else:
        name_part = name_line.strip()
    
    # Normalize whitespace
    return ' '.join(name_part.split())

def extract_name_block(lines: List[str], start_idx: int) -> Tuple[List[str], int]:
    """
    Extract a complete NAME block starting from start_idx.
    Returns the complete block and the index after the block.
    """
    block = [lines[start_idx]]  # Include the NAME line
    idx = start_idx + 1
    
    while idx < len(lines):
        line = lines[idx]
        if line.startswith('0 ') or line.startswith('1 '):
            # New record or new level 1 tag, stop here
            break
        block.append(line)
        idx += 1
    
    return block, idx

def merge_name_sources(name_blocks: List[List[str]]) -> List[str]:
    """
    Merge sources from multiple name blocks into a single block.
    Keeps the first name block and adds any unique sources from other blocks.
    """
    if not name_blocks:
        return []
    
    # Start with the first name block
    merged_block = name_blocks[0][:]
    
    # Collect all source references from other blocks
    existing_sources = set()
    for line in merged_block:
        if line.strip().startswith('2 SOUR '):
            existing_sources.add(line.strip())
    
    # Add unique sources from other blocks
    for block in name_blocks[1:]:
        for line in block:
            if line.strip().startswith('2 SOUR ') and line.strip() not in existing_sources:
                # Find where to insert this source (after the last line of level 2 or higher)
                insert_idx = len(merged_block)
                for i in range(len(merged_block) - 1, -1, -1):
                    if merged_block[i].startswith('2 ') or merged_block[i].startswith('3 ') or merged_block[i].startswith('4 '):
                        insert_idx = i + 1
                        break
                
                # Add the source and any sub-records
                source_start = block.index(line)
                j = source_start
                while j < len(block) and (j == source_start or not block[j].strip().startswith('2 SOUR')):
                    merged_block.insert(insert_idx, block[j])
                    insert_idx += 1
                    j += 1
                    if j < len(block) and block[j].startswith('1 '):
                        break
                
                existing_sources.add(line.strip())
    
    return merged_block

def deduplicate_individual_names(individual_lines: List[str]) -> List[str]:
    """
    Remove duplicate NAME records from an individual's record.
    Returns the deduplicated lines.
    """
    result_lines = []
    i = 0
    name_groups = {}  # normalized_name -> list of name_blocks
    
    while i < len(individual_lines):
        line = individual_lines[i]
        
        if line.startswith('1 NAME '):
            # Extract the complete NAME block
            name_block, next_idx = extract_name_block(individual_lines, i)
            
            # Normalize the name for comparison
            normalized = normalize_name_for_comparison(line)
            
            # Group identical names
            if normalized not in name_groups:
                name_groups[normalized] = []
            name_groups[normalized].append(name_block)
            
            i = next_idx
        else:
            # Not a NAME record, add to result
            result_lines.append(line)
            i += 1
    
    # Add deduplicated names back to the result
    names_added = 0
    for normalized_name, blocks in name_groups.items():
        if len(blocks) > 1:
            logging.info(f"  NAME: kept 1 of {len(blocks)} duplicate names")
        
        # Merge sources from all blocks and add the merged block
        merged_block = merge_name_sources(blocks)
        
        # Find the right place to insert the name (after individual header, before other facts)
        insert_idx = 0
        for j, line in enumerate(result_lines):
            if line.startswith('1 ') and not line.startswith('1 NAME'):
                insert_idx = j
                break
        else:
            insert_idx = len(result_lines)
        
        # Insert the merged name block
        for line in reversed(merged_block):
            result_lines.insert(insert_idx, line)
        names_added += 1
    
    return result_lines

def process_gedcom_file(input_path: Path, output_path: Path, backup_dir: Path = None) -> Dict[str, int]:
    """
    Process a GEDCOM file to remove duplicate names.
    Returns statistics about the deduplication.
    """
    logging.info(f"Processing GEDCOM file: {input_path}")
    
    # Read the input file
    with open(input_path, 'r', encoding='utf-8', errors='replace') as f:
        content = f.read()
    
    original_size = len(content)
    logging.info(f"Input file size: {original_size / 1024 / 1024:.1f}MB")
    
    lines = content.split('\n')
    result_lines = []
    
    i = 0
    individuals_processed = 0
    total_names_before = 0
    total_names_after = 0
    
    while i < len(lines):
        line = lines[i]
        
        if re.match(r'^0 @I\d+@ INDI$', line):
            # Found an individual record
            individuals_processed += 1
            
            # Find the end of this individual record
            individual_start = i
            i += 1
            while i < len(lines) and not lines[i].startswith('0 '):
                i += 1
            individual_end = i
            
            # Extract the individual's lines
            individual_lines = lines[individual_start:individual_end]
            
            # Count original names
            original_name_count = sum(1 for line in individual_lines if line.startswith('1 NAME '))
            total_names_before += original_name_count
            
            # Deduplicate names within this individual
            deduplicated_lines = deduplicate_individual_names(individual_lines)
            
            # Count deduplicated names
            final_name_count = sum(1 for line in deduplicated_lines if line.startswith('1 NAME '))
            total_names_after += final_name_count
            
            # Add to result
            result_lines.extend(deduplicated_lines)
        else:
            # Not an individual record, add as-is
            result_lines.append(line)
            i += 1
    
    # Create backup if requested
    if backup_dir and input_path == output_path:
        backup_dir.mkdir(parents=True, exist_ok=True)
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        backup_path = backup_dir / f"{input_path.stem}.bak.{timestamp}.ged"
        shutil.copy2(input_path, backup_path)
        logging.info(f"Created backup: {backup_path}")
    
    # Write the output file
    output_content = '\n'.join(result_lines)
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(output_content)
    
    final_size = len(output_content)
    logging.info(f"Output file size: {final_size / 1024 / 1024:.1f}MB")
    
    stats = {
        'individuals_processed': individuals_processed,
        'names_before': total_names_before,
        'names_after': total_names_after,
        'names_removed': total_names_before - total_names_after,
        'original_size': original_size,
        'final_size': final_size,
        'size_reduction': original_size - final_size
    }
    
    return stats

def main():
    """Main function."""
    parser = argparse.ArgumentParser(
        description='Remove duplicate alternate names from GEDCOM files',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 deduplicate_names.py input.ged
  python3 deduplicate_names.py input.ged -o cleaned.ged
  python3 deduplicate_names.py input.ged --backup-dir backups/
        """
    )
    
    parser.add_argument('input_file', type=Path,
                        help='Input GEDCOM file')
    parser.add_argument('-o', '--output', type=Path,
                        help='Output GEDCOM file (default: overwrite input)')
    parser.add_argument('--backup-dir', type=Path,
                        help='Directory to store backup files (only when overwriting)')
    parser.add_argument('--dry-run', action='store_true',
                        help='Show what would be done without making changes')
    
    args = parser.parse_args()
    
    # Setup logging
    setup_logging()
    
    # Determine output file
    output_path = args.output if args.output else args.input_file
    
    if args.dry_run:
        logging.info("DRY RUN MODE - no files will be modified")
        # For dry run, use a temporary output path
        output_path = Path(f"{args.input_file}.tmp")
    
    try:
        # Process the file
        stats = process_gedcom_file(args.input_file, output_path, args.backup_dir)
        
        # Print statistics
        logging.info("\n" + "="*50)
        logging.info("NAME DEDUPLICATION SUMMARY")
        logging.info("="*50)
        logging.info(f"Individuals processed: {stats['individuals_processed']:,}")
        logging.info(f"NAME records before: {stats['names_before']:,}")
        logging.info(f"NAME records after: {stats['names_after']:,}")
        logging.info(f"Duplicate names removed: {stats['names_removed']:,}")
        logging.info(f"File size reduction: {stats['size_reduction']:,} bytes ({stats['size_reduction']/1024:.1f}KB)")
        
        if args.dry_run:
            # Clean up temporary file
            output_path.unlink()
            logging.info("\nDry run completed - no changes made to original file")
        else:
            logging.info(f"\nDeduplication complete! Output written to: {output_path}")
        
    except Exception as e:
        logging.error(f"Error processing file: {e}")
        return 1
    
    return 0

if __name__ == '__main__':
    exit(main())
