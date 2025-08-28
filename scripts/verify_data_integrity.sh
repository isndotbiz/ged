#!/bin/bash

# GEDCOM Data Integrity Verification Script
# Ensures no genealogical data is lost during processing
# Usage: verify_data_integrity.sh <original_file> <processed_file> [--detailed]

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DETAILED_OUTPUT=false

# Function to print colored output
print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to print section headers
print_header() {
    echo
    print_status "$BLUE" "=== $1 ==="
}

# Function to count records with specific pattern
count_records() {
    local file=$1
    local pattern=$2
    local description=$3
    
    if [[ ! -f "$file" ]]; then
        print_status "$RED" "ERROR: File $file not found"
        return 1
    fi
    
    local count=$(grep -c "$pattern" "$file" 2>/dev/null || echo "0")
    # Handle multi-line output from grep -c by taking only the first line
    echo "$count" | head -1
}

# Function to extract and compare specific data
extract_gedcom_stats() {
    local file=$1
    local temp_file=$(mktemp)
    
    if [[ ! -f "$file" ]]; then
        print_status "$RED" "ERROR: File $file not found"
        return 1
    fi
    
    echo "=== GEDCOM Statistics for: $(basename "$file") ===" > "$temp_file"
    echo "File size: $(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "unknown") bytes" >> "$temp_file"
    echo "Total lines: $(wc -l < "$file")" >> "$temp_file"
    echo >> "$temp_file"
    
    # Core record counts
    echo "CORE RECORDS:" >> "$temp_file"
    echo "  Individuals (INDI): $(count_records "$file" "^0.*INDI" "individuals")" >> "$temp_file"
    echo "  Families (FAM): $(count_records "$file" "^0.*FAM" "families")" >> "$temp_file"
    echo "  Sources (SOUR): $(count_records "$file" "^0.*SOUR" "sources")" >> "$temp_file"
    echo "  Repositories (REPO): $(count_records "$file" "^0.*REPO" "repositories")" >> "$temp_file"
    echo "  Notes (NOTE): $(count_records "$file" "^0.*NOTE" "notes")" >> "$temp_file"
    echo "  Media (OBJE): $(count_records "$file" "^0.*OBJE" "media objects")" >> "$temp_file"
    echo >> "$temp_file"
    
    # Data element counts
    echo "DATA ELEMENTS:" >> "$temp_file"
    echo "  Birth dates (1 BIRT): $(count_records "$file" "^1 BIRT" "birth events")" >> "$temp_file"
    echo "  Death dates (1 DEAT): $(count_records "$file" "^1 DEAT" "death events")" >> "$temp_file"
    echo "  Marriage dates (1 MARR): $(count_records "$file" "^1 MARR" "marriage events")" >> "$temp_file"
    echo "  All DATE records: $(count_records "$file" "^2 DATE" "date records")" >> "$temp_file"
    echo "  Place records (2 PLAC): $(count_records "$file" "^2 PLAC" "place records")" >> "$temp_file"
    echo "  Name records (1 NAME): $(count_records "$file" "^1 NAME" "name records")" >> "$temp_file"
    echo >> "$temp_file"
    
    # Source citations
    echo "SOURCE CITATIONS:" >> "$temp_file"
    echo "  Source citations (1 SOUR): $(count_records "$file" "^1 SOUR" "source citations")" >> "$temp_file"
    echo "  Source citations (2 SOUR): $(count_records "$file" "^2 SOUR" "nested source citations")" >> "$temp_file"
    echo "  Page references (2 PAGE): $(count_records "$file" "^2 PAGE" "page references")" >> "$temp_file"
    echo >> "$temp_file"
    
    # Family relationships
    echo "RELATIONSHIPS:" >> "$temp_file"
    echo "  Child relationships (1 CHIL): $(count_records "$file" "^1 CHIL" "child relationships")" >> "$temp_file"
    echo "  Husband relationships (1 HUSB): $(count_records "$file" "^1 HUSB" "husband relationships")" >> "$temp_file"
    echo "  Wife relationships (1 WIFE): $(count_records "$file" "^1 WIFE" "wife relationships")" >> "$temp_file"
    echo "  Family child links (1 FAMC): $(count_records "$file" "^1 FAMC" "family child links")" >> "$temp_file"
    echo "  Family spouse links (1 FAMS): $(count_records "$file" "^1 FAMS" "family spouse links")" >> "$temp_file"
    echo >> "$temp_file"
    
    cat "$temp_file"
    rm "$temp_file"
}

# Function to compare two files and report differences
compare_files() {
    local original=$1
    local processed=$2
    local issues_found=0
    
    print_header "COMPARING DATA INTEGRITY"
    
    # Create temporary files for stats
    local original_stats=$(mktemp)
    local processed_stats=$(mktemp)
    
    # Extract stats (without headers for easier parsing)
    extract_gedcom_stats "$original" | grep -E "^  " > "$original_stats"
    extract_gedcom_stats "$processed" | grep -E "^  " > "$processed_stats"
    
    # Compare each line
    while IFS= read -r original_line; do
        local field=$(echo "$original_line" | cut -d: -f1)
        local original_count=$(echo "$original_line" | cut -d: -f2 | tr -d ' ')
        
        local processed_line=$(grep "^$field:" "$processed_stats" || echo "")
        if [[ -z "$processed_line" ]]; then
            print_status "$RED" "⚠️  Field missing in processed file: $field"
            ((issues_found++))
            continue
        fi
        
        local processed_count=$(echo "$processed_line" | cut -d: -f2 | tr -d ' ')
        
        if [[ "$original_count" != "$processed_count" ]]; then
            if [[ "$original_count" -gt "$processed_count" ]]; then
                print_status "$RED" "❌ DATA LOSS: $field: $original_count → $processed_count (LOST: $((original_count - processed_count)))"
                ((issues_found++))
            else
                print_status "$YELLOW" "📈 Data increased: $field: $original_count → $processed_count (ADDED: $((processed_count - original_count)))"
            fi
        else
            print_status "$GREEN" "✅ $field: $original_count (unchanged)"
        fi
    done < "$original_stats"
    
    # Clean up
    rm "$original_stats" "$processed_stats"
    
    return $issues_found
}

# Function to perform detailed analysis
detailed_analysis() {
    local original=$1
    local processed=$2
    
    print_header "DETAILED ANALYSIS"
    
    # Check for specific data patterns that might indicate problems
    echo "Checking for potential data corruption patterns..."
    
    # Check for malformed dates
    local bad_dates_orig=$(grep "^2 DATE" "$original" | grep -c -E "(^2 DATE\s*$|^2 DATE\s+$)" || echo "0")
    local bad_dates_proc=$(grep "^2 DATE" "$processed" | grep -c -E "(^2 DATE\s*$|^2 DATE\s+$)" || echo "0")
    
    if [[ "$bad_dates_proc" -gt "$bad_dates_orig" ]]; then
        print_status "$RED" "⚠️  Malformed dates increased: $bad_dates_orig → $bad_dates_proc"
    elif [[ "$bad_dates_proc" -lt "$bad_dates_orig" ]]; then
        print_status "$GREEN" "✅ Malformed dates decreased: $bad_dates_orig → $bad_dates_proc"
    else
        print_status "$BLUE" "ℹ️  Malformed dates unchanged: $bad_dates_orig"
    fi
    
    # Check for empty name records
    local empty_names_orig=$(grep -A1 "^1 NAME" "$original" | grep -c "^1 NAME\s*$" || echo "0")
    local empty_names_proc=$(grep -A1 "^1 NAME" "$processed" | grep -c "^1 NAME\s*$" || echo "0")
    
    if [[ "$empty_names_proc" -gt "$empty_names_orig" ]]; then
        print_status "$RED" "⚠️  Empty names increased: $empty_names_orig → $empty_names_proc"
    elif [[ "$empty_names_proc" -lt "$empty_names_orig" ]]; then
        print_status "$GREEN" "✅ Empty names decreased: $empty_names_orig → $empty_names_proc"
    else
        print_status "$BLUE" "ℹ️  Empty names unchanged: $empty_names_orig"
    fi
    
    # Sample some individual records to verify they look correct
    echo
    echo "Sample of processed individual records:"
    local sample_individuals=$(grep "^0.*INDI" "$processed" | head -3)
    while IFS= read -r indi_line; do
        local indi_id=$(echo "$indi_line" | cut -d' ' -f2)
        print_status "$BLUE" "Individual $indi_id:"
        
        # Extract a few lines after this individual
        local line_num=$(grep -n "^0.*$indi_id.*INDI" "$processed" | cut -d: -f1)
        sed -n "${line_num},$((line_num + 10))p" "$processed" | head -10 | sed 's/^/    /'
        echo
    done <<< "$sample_individuals"
}

# Main function
main() {
    local original_file=""
    local processed_file=""
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --detailed)
                DETAILED_OUTPUT=true
                shift
                ;;
            *)
                if [[ -z "$original_file" ]]; then
                    original_file=$1
                elif [[ -z "$processed_file" ]]; then
                    processed_file=$1
                else
                    print_status "$RED" "Too many arguments"
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # Validate arguments
    if [[ -z "$original_file" ]] || [[ -z "$processed_file" ]]; then
        print_status "$RED" "Usage: $0 <original_file> <processed_file> [--detailed]"
        print_status "$BLUE" "Examples:"
        print_status "$BLUE" "  $0 data/master/roots_master.ged data/processed/roots_master_fixed.ged"
        print_status "$BLUE" "  $0 original.ged processed.ged --detailed"
        exit 1
    fi
    
    # Check if files exist
    if [[ ! -f "$original_file" ]]; then
        print_status "$RED" "ERROR: Original file '$original_file' not found"
        exit 1
    fi
    
    if [[ ! -f "$processed_file" ]]; then
        print_status "$RED" "ERROR: Processed file '$processed_file' not found"
        exit 1
    fi
    
    print_header "GEDCOM DATA INTEGRITY VERIFICATION"
    print_status "$BLUE" "Original file: $original_file"
    print_status "$BLUE" "Processed file: $processed_file"
    print_status "$BLUE" "Timestamp: $(date)"
    
    # Show individual file stats if detailed output requested
    if [[ "$DETAILED_OUTPUT" == "true" ]]; then
        print_header "ORIGINAL FILE STATISTICS"
        extract_gedcom_stats "$original_file"
        
        print_header "PROCESSED FILE STATISTICS" 
        extract_gedcom_stats "$processed_file"
    fi
    
    # Compare the files
    local comparison_issues=0
    compare_files "$original_file" "$processed_file" || comparison_issues=$?
    
    # Perform detailed analysis if requested
    if [[ "$DETAILED_OUTPUT" == "true" ]]; then
        detailed_analysis "$original_file" "$processed_file"
    fi
    
    # Final verdict
    print_header "VERIFICATION RESULTS"
    
    if [[ $comparison_issues -eq 0 ]]; then
        print_status "$GREEN" "✅ DATA INTEGRITY VERIFIED: No genealogical data was lost during processing"
        print_status "$GREEN" "✅ Safe to proceed with the processed file"
        exit 0
    else
        print_status "$RED" "❌ DATA INTEGRITY COMPROMISED: $comparison_issues issues found"
        print_status "$RED" "❌ DO NOT USE the processed file - data loss detected"
        print_status "$YELLOW" "🔄 Recommend rolling back to original file"
        exit 1
    fi
}

# Run main function with all arguments
main "$@"
