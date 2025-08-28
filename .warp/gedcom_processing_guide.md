# 📚 GEDCOM Processing Application Guide

## 🎯 Application Overview

This project is a comprehensive GEDCOM (GEnealogical Data COMmunication) file processing toolkit designed to clean, deduplicate, and optimize genealogy data files. The application handles common genealogy data quality issues while preserving data integrity and GEDCOM format compliance.

## 📁 Project Structure

```
/Users/jonathanmallinger/Projects/ged/
├── data/
│   ├── original/                    # Original GEDCOM files
│   └── processing/
│       ├── fixed/                   # Processed output files
│       └── reports/                 # Processing reports and logs
├── scripts/                         # Processing scripts
│   ├── remove_duplicate_facts.py    # MyHeritage duplicate fact remover
│   ├── aggressive_fact_cleaner.py   # Comprehensive fact deduplication
│   ├── source_preserving_person_dedupe.py  # Person deduplication (source-safe)
│   ├── fixed_deduplicate.py         # Original person deduplicator (has source issues)
│   └── [other utility scripts]
├── requirements.txt                 # Python dependencies
└── .warp/                          # Warp configuration files
```

## 🧬 GEDCOM File Format Understanding

### Core Structure
GEDCOM files use a hierarchical line-based format:
```
LEVEL [XREF] TAG [VALUE]
```

Example:
```gedcom
0 @I123@ INDI              # Individual record with ID @I123@
1 NAME John /Smith/         # Name: John Smith (surname in slashes)
2 GIVN John                 # Given names
2 SURN Smith                # Surname
1 BIRT                      # Birth event
2 DATE 1 JAN 1950           # Birth date
2 PLAC New York, USA        # Birth place
2 SOUR @S456@               # Source reference
1 DEAT                      # Death event
2 DATE 15 DEC 2020          # Death date
2 PLAC Miami, Florida, USA  # Death place
1 FAMS @F789@               # Family as spouse
1 FAMC @F101@               # Family as child

0 @S456@ SOUR               # Source record
1 TITL Birth Certificate    # Source title
1 PUBL County Clerk         # Publisher
```

### Critical GEDCOM Principles

1. **Hierarchy Preservation**: Sub-records must maintain proper level relationships
2. **Reference Integrity**: All @ID@ references must point to valid records
3. **Source Preservation**: Source records (@S123@) and their references are crucial for data credibility
4. **Family Relationships**: FAMS (spouse) and FAMC (child) links maintain genealogical connections

## 🛠️ Processing Scripts Detailed Guide

### 1. MyHeritage Duplicate Fact Remover (`remove_duplicate_facts.py`)

**Purpose**: Remove specific duplicate birth/death facts identified by MyHeritage analysis.

**Key Features**:
- Targets exactly 333 duplicate facts from MyHeritage CSV list
- Preserves source information
- Uses completeness scoring to keep the best version of each fact
- Generates detailed removal report

**Usage**:
```bash
python3 scripts/remove_duplicate_facts.py input.ged -o output.ged -c duplicates.csv -v
```

**Scoring System**:
- Base fact: 1 point
- Has date: +10 points
- Has place: +5 points
- Has source: +3 points
- Has note: +2 points

### 2. Aggressive Fact Cleaner (`aggressive_fact_cleaner.py`)

**Purpose**: Remove ALL types of duplicate facts, keeping only the most complete instance of each fact type per person.

**Key Features**:
- Processes ALL fact types (BIRT, DEAT, MARR, RESI, EVEN, SOUR, etc.)
- Ensures each person has exactly one of each fact type
- Uses intelligent scoring to preserve the most complete facts
- Dramatically reduces file size while preserving information quality

**Usage**:
```bash
python3 scripts/aggressive_fact_cleaner.py input.ged -o output.ged -v
```

**Fact Types Handled**:
- BIRT (Birth), DEAT (Death), MARR (Marriage)
- RESI (Residence), EVEN (Events), OCCU (Occupation)
- SOUR (Sources), BAPM (Baptism), BURI (Burial)
- CHR (Christening), CENS (Census), and more

### 3. Source-Preserving Person Deduplicator (`source_preserving_person_dedupe.py`)

**Purpose**: Identify and merge duplicate people while preserving ALL sources and data integrity.

**Key Features**:
- **PRESERVES ALL SOURCES**: Critical improvement over previous versions
- Uses name similarity matching with birth year verification
- Maintains family relationships during merges
- Provides configurable similarity thresholds
- Generates comprehensive merge logs

**Usage**:
```bash
python3 scripts/source_preserving_person_dedupe.py input.ged output.ged \
  --similarity-threshold 90.0 \
  --auto-merge-threshold 98.0 \
  --report report.json \
  --verbose
```

**Matching Algorithm**:
1. **Name Similarity**: Uses fuzzy string matching (rapidfuzz library)
2. **Birth Year Matching**: 
   - Exact match: +20 points
   - Within 2 years: +10 points  
   - >10 years difference: -15 points
3. **Sex Matching**: Same sex +5, different sex -10
4. **Total Score**: Combined score determines duplicate likelihood

**Merge Strategy**:
- Appends all data from secondary individual to primary
- Updates family references to point to merged individual
- Adds merge notes for audit trail
- Preserves ALL source records and references

## ⚠️ Critical Lessons Learned

### The Source Preservation Issue

**Problem**: Early deduplication scripts (`fixed_deduplicate.py`) were losing source records during the merge process, causing critical genealogical documentation to be lost.

**Root Cause**: The scripts were not properly handling the three-way relationship between:
1. Source records (`0 @S123@ SOUR`)
2. Source references in facts (`2 SOUR @S123@`)  
3. The overall GEDCOM structure preservation

**Solution**: The `source_preserving_person_dedupe.py` script specifically:
- Parses and stores ALL source records separately
- Writes them out completely unchanged
- Preserves source references within individual records
- Maintains proper GEDCOM hierarchy throughout

### GEDCOM Processing Best Practices

1. **Always Parse Sources Separately**: Source records must be identified, stored, and written independently
2. **Preserve Hierarchy**: GEDCOM level structures are critical - never break them
3. **Reference Integrity**: When merging individuals, update ALL family references
4. **Audit Trail**: Always log what changes were made for verification
5. **Validate Results**: Count key record types (individuals, families, sources) before/after

## 🔄 Complete Processing Workflow

### Standard 3-Stage Pipeline:

1. **Stage 1: Targeted Duplicate Removal**
   ```bash
   python3 scripts/remove_duplicate_facts.py original.ged \
     -o stage1_duplicates_removed.ged \
     -c myheritage_duplicates.csv -v
   ```

2. **Stage 2: Aggressive Fact Cleaning**
   ```bash
   python3 scripts/aggressive_fact_cleaner.py stage1_duplicates_removed.ged \
     -o stage2_facts_cleaned.ged -v
   ```

3. **Stage 3: Person Deduplication (Source-Safe)**
   ```bash
   python3 scripts/source_preserving_person_dedupe.py stage2_facts_cleaned.ged \
     stage3_people_deduplicated.ged \
     --similarity-threshold 90.0 \
     --auto-merge-threshold 98.0 \
     --report final_report.json -v
   ```

### Quality Assurance Checks:

After each stage, verify:
```bash
# Count individuals
grep -c "^0 @I" file.ged

# Count families  
grep -c "^0 @F" file.ged

# Count sources
grep -c "^0 @S" file.ged

# Check file size
wc -l file.ged
```

## 🐍 Python Code Patterns for GEDCOM Processing

### Parsing GEDCOM Lines
```python
def parse_gedcom_line(line: str) -> Dict[str, Any]:
    """Parse a single GEDCOM line"""
    line = line.strip()
    parts = line.split(' ', 2)
    
    level = int(parts[0])
    
    # Handle XREF format: 0 @I123@ INDI
    if parts[1].startswith('@') and parts[1].endswith('@'):
        xref_id = parts[1]
        tag = parts[2].split(' ')[0] if len(parts) > 2 else ""
        value = ' '.join(parts[2].split(' ')[1:]) if len(parts) > 2 else ""
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
```

### Preserving Hierarchy During Parsing
```python
def parse_individual_record(lines: List[str], start_index: int) -> Individual:
    """Parse complete individual record maintaining hierarchy"""
    i = start_index
    individual = Individual(...)
    
    # Parse until next level 0 record
    while i < len(lines) and not lines[i].strip().startswith('0 '):
        line = lines[i].strip()
        
        if line.startswith('1 NAME '):
            # Handle name parsing
            pass
        elif line.startswith('1 BIRT'):
            # Parse birth event and sub-records
            i += 1
            while i < len(lines) and lines[i].strip().startswith('2 '):
                # Handle birth sub-facts (date, place, sources)
                i += 1
            continue  # Don't increment i again
            
        i += 1
    
    return individual
```

### Source-Safe Record Writing
```python
def write_gedcom_with_sources(output_file: Path, individuals: Dict, 
                            families: Dict, sources: Dict):
    """Write GEDCOM ensuring all sources are preserved"""
    with open(output_file, 'w', encoding='utf-8') as f:
        # Write header
        f.write("0 HEAD\n")
        f.write("1 SOUR YourApplication\n")
        # ... header details
        
        # Write ALL sources first (completely unchanged)
        for source_id, source_lines in sources.items():
            for line in source_lines:
                f.write(line)
            f.write("\n")
        
        # Write individuals (preserving all source references)
        for individual in individuals.values():
            for line in individual.raw_lines:
                f.write(line)
            f.write("\n")
        
        # Write families  
        for family_lines in families.values():
            for line in family_lines:
                f.write(line)
            f.write("\n")
        
        f.write("0 TRLR\n")
```

## 📊 Performance Metrics & Results

### Typical Processing Results:
- **Original File**: ~63,000 lines, 1,469 individuals
- **After Fact Cleaning**: ~54,000 lines (-14%), same individuals
- **After Person Deduplication**: ~45,000 lines (-21% total), ~1,270 individuals (-13%)
- **Sources Preserved**: 294 source records maintained throughout
- **Zero Data Loss**: All valuable information preserved via intelligent merging

## 🔧 Dependencies & Environment

### Required Python Packages:
```txt
rapidfuzz>=3.0.0    # Fast string similarity matching
pathlib             # Path manipulation (standard library)
argparse            # Command line argument parsing (standard library)
json                # JSON handling (standard library)
re                  # Regular expressions (standard library)
```

### Installation:
```bash
pip install rapidfuzz
```

## 🚨 Common Pitfalls & Solutions

### 1. Source Loss Prevention
**Problem**: Sources disappearing during processing
**Solution**: Always parse, store, and write sources as a separate collection

### 2. Reference Integrity  
**Problem**: @ID@ references becoming invalid after merging
**Solution**: Update ALL family records when merging individuals

### 3. Hierarchy Corruption
**Problem**: GEDCOM level structure getting broken
**Solution**: Preserve raw lines and maintain proper indentation

### 4. Character Encoding Issues
**Problem**: Special characters causing file corruption
**Solution**: Always use UTF-8 encoding for file operations

### 5. Memory Issues with Large Files
**Problem**: Large GEDCOM files causing memory problems
**Solution**: Use streaming parsers for files >100MB

## 🎯 Future Enhancements

### Possible Improvements:
1. **GUI Interface**: Desktop application for non-technical users
2. **Batch Processing**: Process multiple GEDCOM files simultaneously  
3. **Advanced Matching**: Machine learning for better duplicate detection
4. **Validation Tools**: Comprehensive GEDCOM compliance checking
5. **Backup Integration**: Automatic backup before processing
6. **Undo Functionality**: Ability to reverse processing operations

## 📚 Additional Resources

### GEDCOM Specification:
- Official GEDCOM 5.5.1 specification
- FamilySearch GEDCOM documentation
- GEDCOM validation tools

### Genealogy Software Compatibility:
- Family Tree Maker
- RootsMagic  
- Legacy Family Tree
- MyHeritage Family Tree Builder
- Ancestry.com Family Trees

---

*This guide serves as a comprehensive reference for understanding and working with GEDCOM files using this processing toolkit. Always backup original files before processing.*
