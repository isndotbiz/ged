#!/bin/bash

# GEDCOM Processing Workspace Setup Script
# Creates timestamped backups and proper directory structure for safe processing
# Usage: setup_processing_workspace.sh [gedcom_file]

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

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

# Function to create directory structure
create_directories() {
    local base_dir="$PROJECT_ROOT/data/processing"
    
    print_header "CREATING WORKSPACE DIRECTORIES"
    
    # Create main processing directories
    local dirs=(
        "$base_dir"
        "$base_dir/backup"
        "$base_dir/reports"
        "$base_dir/fixed"
        "$base_dir/fixed/step1_dates"
        "$base_dir/fixed/step2_names"
        "$base_dir/fixed/step3_places"
        "$base_dir/logs"
        "$base_dir/temp"
    )
    
    for dir in "${dirs[@]}"; do
        if [[ ! -d "$dir" ]]; then
            mkdir -p "$dir"
            print_status "$GREEN" "✅ Created: $dir"
        else
            print_status "$BLUE" "ℹ️  Already exists: $dir"
        fi
    done
}

# Function to create a timestamped backup
create_backup() {
    local source_file="$1"
    local backup_dir="$PROJECT_ROOT/data/processing/backup"
    
    if [[ ! -f "$source_file" ]]; then
        print_status "$RED" "ERROR: Source file '$source_file' not found"
        return 1
    fi
    
    local filename=$(basename "$source_file")
    local backup_filename="${filename%.*}_backup_${TIMESTAMP}.${filename##*.}"
    local backup_path="$backup_dir/$backup_filename"
    
    print_header "CREATING BACKUP"
    
    # Copy the file
    cp "$source_file" "$backup_path"
    
    # Verify the backup
    if [[ -f "$backup_path" ]]; then
        local original_size=$(stat -f%z "$source_file" 2>/dev/null || stat -c%s "$source_file")
        local backup_size=$(stat -f%z "$backup_path" 2>/dev/null || stat -c%s "$backup_path")
        
        if [[ "$original_size" == "$backup_size" ]]; then
            print_status "$GREEN" "✅ Backup created successfully: $backup_path"
            print_status "$BLUE" "   File size: $original_size bytes"
            
            # Create a symbolic link to the latest backup
            local latest_link="$backup_dir/latest_backup.ged"
            if [[ -L "$latest_link" ]]; then
                rm "$latest_link"
            fi
            ln -s "$(basename "$backup_path")" "$latest_link"
            print_status "$GREEN" "✅ Latest backup link updated: $latest_link"
            
        else
            print_status "$RED" "❌ Backup verification failed - size mismatch"
            print_status "$RED" "   Original: $original_size bytes, Backup: $backup_size bytes"
            rm "$backup_path"
            return 1
        fi
    else
        print_status "$RED" "❌ Backup creation failed"
        return 1
    fi
    
    # Store backup info for rollback script
    echo "TIMESTAMP=$TIMESTAMP" > "$backup_dir/.last_backup_info"
    echo "SOURCE_FILE=$source_file" >> "$backup_dir/.last_backup_info"
    echo "BACKUP_FILE=$backup_path" >> "$backup_dir/.last_backup_info"
    echo "BACKUP_SIZE=$backup_size" >> "$backup_dir/.last_backup_info"
    
    return 0
}

# Function to create processing log file
create_log_file() {
    local log_dir="$PROJECT_ROOT/data/processing/logs"
    local log_file="$log_dir/processing_log_${TIMESTAMP}.log"
    
    print_header "INITIALIZING PROCESSING LOG"
    
    cat > "$log_file" << EOF
# GEDCOM Processing Log
# Started: $(date)
# Timestamp: $TIMESTAMP

## Initial Setup
- Workspace created: $(date)
- Backup created: $(date)

## Processing Steps (to be updated during processing)
# Step 1: Date standardization
# Step 2: Name standardization  
# Step 3: Place standardization
# Step 4: Problem-specific fixes
# Step 5: Final validation

## Data Integrity Checks
# (Will be populated during processing)

EOF
    
    print_status "$GREEN" "✅ Processing log initialized: $log_file"
    
    # Create a symbolic link to the latest log
    local latest_log_link="$log_dir/latest_processing.log"
    if [[ -L "$latest_log_link" ]]; then
        rm "$latest_log_link"
    fi
    ln -s "$(basename "$log_file")" "$latest_log_link"
    print_status "$GREEN" "✅ Latest log link updated: $latest_log_link"
}

# Function to display workspace status
show_workspace_status() {
    local processing_dir="$PROJECT_ROOT/data/processing"
    
    print_header "WORKSPACE STATUS"
    
    if [[ -d "$processing_dir" ]]; then
        print_status "$BLUE" "Processing workspace: $processing_dir"
        
        # Show backup info
        local backup_count=$(find "$processing_dir/backup" -name "*.ged" 2>/dev/null | wc -l)
        print_status "$BLUE" "Available backups: $backup_count"
        
        if [[ -f "$processing_dir/backup/.last_backup_info" ]]; then
            print_status "$BLUE" "Last backup info:"
            cat "$processing_dir/backup/.last_backup_info" | sed 's/^/    /'
        fi
        
        # Show directory sizes
        echo
        print_status "$BLUE" "Directory sizes:"
        du -sh "$processing_dir"/* 2>/dev/null | sed 's/^/    /' || true
        
    else
        print_status "$YELLOW" "No processing workspace found"
    fi
}

# Function to create README file for the workspace
create_workspace_readme() {
    local readme_file="$PROJECT_ROOT/data/processing/README.md"
    
    cat > "$readme_file" << 'EOF'
# GEDCOM Processing Workspace

This directory contains all files related to safe GEDCOM processing operations.

## Directory Structure

- **backup/** - Timestamped backups of original GEDCOM files
- **reports/** - Processing reports, quality assessments, and analysis
- **fixed/** - Processed GEDCOM files at various stages
  - `step1_dates/` - Files after date standardization
  - `step2_names/` - Files after name standardization  
  - `step3_places/` - Files after place standardization
- **logs/** - Processing logs and audit trails
- **temp/** - Temporary files (safe to clean)

## Important Files

- `backup/latest_backup.ged` - Symbolic link to most recent backup
- `logs/latest_processing.log` - Symbolic link to current processing log
- `backup/.last_backup_info` - Information about last backup created

## Data Integrity

All processing steps are verified using the data integrity verification script:
```bash
scripts/verify_data_integrity.sh original.ged processed.ged
```

## Rollback

If any processing step fails or compromises data, use the rollback script:
```bash
scripts/rollback_to_backup.sh
```

## Safety Guidelines

1. Never modify files in the `backup/` directory
2. Always verify data integrity after each processing step
3. Keep processing logs for audit purposes
4. Use timestamped files to track processing history

EOF
    
    print_status "$GREEN" "✅ Workspace README created: $readme_file"
}

# Function to validate prerequisites
validate_prerequisites() {
    print_header "VALIDATING PREREQUISITES"
    
    # Check if we're in the right directory structure
    if [[ ! -d "$PROJECT_ROOT/data" ]]; then
        print_status "$YELLOW" "⚠️  Creating data directory structure..."
        mkdir -p "$PROJECT_ROOT/data/master"
        mkdir -p "$PROJECT_ROOT/data/problems"
    fi
    
    # Check if scripts directory exists
    if [[ ! -d "$PROJECT_ROOT/scripts" ]]; then
        print_status "$RED" "❌ Scripts directory not found at $PROJECT_ROOT/scripts"
        return 1
    fi
    
    # Check if data integrity script exists
    if [[ ! -f "$PROJECT_ROOT/scripts/verify_data_integrity.sh" ]]; then
        print_status "$RED" "❌ Data integrity verification script not found"
        return 1
    fi
    
    print_status "$GREEN" "✅ Prerequisites validated"
    return 0
}

# Main function
main() {
    local gedcom_file=""
    
    # Parse arguments
    if [[ $# -gt 0 ]]; then
        gedcom_file="$1"
    fi
    
    print_header "GEDCOM PROCESSING WORKSPACE SETUP"
    print_status "$BLUE" "Project root: $PROJECT_ROOT"
    print_status "$BLUE" "Timestamp: $TIMESTAMP"
    print_status "$BLUE" "Setup time: $(date)"
    
    # Validate prerequisites
    if ! validate_prerequisites; then
        print_status "$RED" "❌ Prerequisites validation failed"
        exit 1
    fi
    
    # Create directory structure
    create_directories
    
    # Create backup if GEDCOM file provided
    if [[ -n "$gedcom_file" ]]; then
        if ! create_backup "$gedcom_file"; then
            print_status "$RED" "❌ Backup creation failed"
            exit 1
        fi
    else
        print_status "$YELLOW" "⚠️  No GEDCOM file specified - skipping backup creation"
        print_status "$BLUE" "   Usage: $0 <gedcom_file> to create backup"
    fi
    
    # Create processing log
    create_log_file
    
    # Create workspace README
    create_workspace_readme
    
    # Show final status
    show_workspace_status
    
    print_header "SETUP COMPLETE"
    print_status "$GREEN" "✅ Processing workspace is ready"
    
    if [[ -n "$gedcom_file" ]]; then
        print_status "$GREEN" "✅ Backup created for: $(basename "$gedcom_file")"
        print_status "$BLUE" "🚀 You can now safely begin processing operations"
        echo
        print_status "$BLUE" "Next steps:"
        print_status "$BLUE" "  1. Run processing scripts on your GEDCOM file"
        print_status "$BLUE" "  2. Use scripts/verify_data_integrity.sh after each step"
        print_status "$BLUE" "  3. Check logs in data/processing/logs/ for audit trail"
    else
        echo
        print_status "$BLUE" "To create a backup and begin processing:"
        print_status "$BLUE" "  $0 path/to/your/gedcom_file.ged"
    fi
}

# Run main function with all arguments
main "$@"
