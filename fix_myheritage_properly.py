#!/usr/bin/env python3
"""
MyHeritage GEDCOM Fixer - Properly preserves dates and places
Removes duplicate birth and marriage facts while keeping the one with the most information
"""

import re
import json

def count_info_in_fact(lines, start_idx):
    """Count how much information (DATE, PLAC, etc.) is in a fact"""
    info_count = 0
    i = start_idx + 1
    
    while i < len(lines) and not lines[i].startswith('1 '):
        line = lines[i].strip()
        if line.startswith('2 DATE') or line.startswith('2 PLAC'):
            info_count += 1
        i += 1
    
    return info_count, i

def process_duplicate_facts(lines, fact_type):
    """Process duplicate facts, keeping the one with the most information"""
    new_lines = []
    i = 0
    current_individual = None
    stats = {'individuals_processed': 0, 'facts_removed': 0}
    
    while i < len(lines):
        line = lines[i]
        
        # Track current individual
        if line.startswith('0 ') and ' INDI' in line:
            current_individual = line.split()[1]
        
        # Check for fact type
        if line.strip() == f'1 {fact_type}':
            # Found a fact, look for duplicates in this individual
            fact_indices = []
            fact_info_counts = []
            
            # Find all facts of this type for current individual
            j = i
            while j < len(lines) and not (lines[j].startswith('0 ') and ' INDI' in lines[j] and j > i):
                if lines[j].strip() == f'1 {fact_type}':
                    info_count, next_idx = count_info_in_fact(lines, j)
                    fact_indices.append((j, next_idx))
                    fact_info_counts.append(info_count)
                j += 1
            
            if len(fact_indices) > 1:
                # Multiple facts found - keep the one with most information
                best_idx = fact_info_counts.index(max(fact_info_counts))
                stats['individuals_processed'] += 1
                stats['facts_removed'] += len(fact_indices) - 1
                
                print(f"Individual {current_individual}: Found {len(fact_indices)} {fact_type} facts, keeping #{best_idx + 1} (has {fact_info_counts[best_idx]} pieces of info)")
                
                # Add the best fact
                best_start, best_end = fact_indices[best_idx]
                for k in range(best_start, best_end):
                    new_lines.append(lines[k])
                
                # Skip past all the facts
                i = max(end for _, end in fact_indices)
                continue
        
        new_lines.append(line)
        i += 1
    
    return new_lines, stats

def fix_myheritage_gedcom(input_file, output_file):
    """Main function to fix GEDCOM for MyHeritage"""
    
    with open(input_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    print(f"Processing {input_file}...")
    lines = content.split('\n')
    
    # Process birth facts
    print("\n=== Processing Birth Facts ===")
    lines, birth_stats = process_duplicate_facts(lines, 'BIRT')
    
    # Process marriage facts  
    print("\n=== Processing Marriage Facts ===")
    lines, marriage_stats = process_duplicate_facts(lines, 'MARR')
    
    # Write output
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines))
    
    # Summary
    total_individuals = birth_stats['individuals_processed'] + marriage_stats['individuals_processed']
    total_facts_removed = birth_stats['facts_removed'] + marriage_stats['facts_removed']
    
    print(f"\n=== SUMMARY ===")
    print(f"Individuals with duplicate birth facts: {birth_stats['individuals_processed']}")
    print(f"Duplicate birth facts removed: {birth_stats['facts_removed']}")
    print(f"Individuals with duplicate marriage facts: {marriage_stats['individuals_processed']}")  
    print(f"Duplicate marriage facts removed: {marriage_stats['facts_removed']}")
    print(f"Total duplicate facts removed: {total_facts_removed}")
    print(f"Output saved to: {output_file}")

if __name__ == "__main__":
    import os
    
    input_file = os.path.expanduser("~/Projects/ged/out/0819.cleaned.final.ged")
    output_file = os.path.expanduser("~/Projects/ged/out/0819.myheritage.fixed.ged")
    
    fix_myheritage_gedcom(input_file, output_file)
