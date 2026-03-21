#!/usr/bin/env python3
"""
Targeted fix for Helen Jean MacKay's duplicate birth facts
"""

import re

def fix_helen_birth_facts(input_file, output_file):
    with open(input_file, 'r', encoding='utf-8') as f:
        content = f.read()
    
    lines = content.split('\n')
    new_lines = []
    in_helen_record = False
    birth_facts_found = 0
    skip_until_next_level_1 = False
    
    for i, line in enumerate(lines):
        # Check if we're starting Helen's record
        if line.strip() == '0 @I4@ INDI':
            in_helen_record = True
            birth_facts_found = 0
            new_lines.append(line)
            continue
        
        # Check if we're leaving Helen's record
        if in_helen_record and line.startswith('0 '):
            in_helen_record = False
            new_lines.append(line)
            continue
        
        # If we're in Helen's record
        if in_helen_record:
            # Check for birth facts
            if line.strip() == '1 BIRT':
                birth_facts_found += 1
                if birth_facts_found == 1:
                    # Keep the first birth fact
                    new_lines.append(line)
                    skip_until_next_level_1 = False
                else:
                    # Skip subsequent birth facts
                    skip_until_next_level_1 = True
                continue
            
            # If we're skipping a birth fact, skip until next level 1
            if skip_until_next_level_1:
                if line.startswith('1 '):
                    skip_until_next_level_1 = False
                    new_lines.append(line)
                # Skip this line (it's part of a duplicate birth fact)
                continue
        
        new_lines.append(line)
    
    # Write the corrected content
    with open(output_file, 'w', encoding='utf-8') as f:
        f.write('\n'.join(new_lines))
    
    print(f"Fixed Helen Jean MacKay's birth facts. Kept 1, removed {birth_facts_found - 1} duplicates.")

if __name__ == "__main__":
    input_file = "~/Projects/ged/out/0819.myheritage.ged"
    output_file = "~/Projects/ged/out/0819.myheritage.fixed.ged"
    
    # Expand ~ to home directory
    import os
    input_file = os.path.expanduser(input_file)
    output_file = os.path.expanduser(output_file)
    
    fix_helen_birth_facts(input_file, output_file)
