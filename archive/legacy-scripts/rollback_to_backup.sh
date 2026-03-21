#!/bin/bash

# GEDCOM Rollback Recovery Script
# Automatically restores from backups when data integrity checks fail
# Usage: rollback_to_backup.sh [--backup-timestamp TIMESTAMP] [--restore-to PATH]

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
BACKUP_DIR="$PROJECT_ROOT/data/processing/backup"
LOGS_DIR="$PROJECT_ROOT/data/processing/logs"

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

# Function to list available backups
list_backups() {
    print_header "AVAILABLE BACKUPS"
    
    if [[ ! -d "$BACKUP_DIR" ]]; then
        print_status "$RED" "❌ No backup directory found at $BACKUP_DIR"
        return 1
    fi
    
    local backups=($(find "$BACKUP_DIR" -name "*_backup_*.ged" -type f | sort -r))
    
    if [[ ${#backups[@]} -eq 0 ]]; then
        print_status "$YELLOW" "⚠️  No backup files found in $BACKUP_DIR"
        return 1
    fi
    
    echo "Available backup files:"
    echo
    printf "%-5s %-30s %-15s %-10s\n" "ID" "Filename" "Timestamp" "Size"
    printf "%-5s %-30s %-15s %-10s\n" "---" "------------------------------" "---------------" "----------"
    
    local counter=1
    for backup in "${backups[@]}"; do
        local filename=$(basename "$backup")
        local timestamp=$(echo "$filename" | grep -o '[0-9]\{8\}_[0-9]\{6\}' || echo "unknown")
        local size=$(stat -f%z "$backup" 2>/dev/null || stat -c%s "$backup" 2>/dev/null || echo "unknown")
        local size_human=""
        
        # Convert size to human readable format
        if [[ "$size" != "unknown" ]] && [[ "$size" -gt 0 ]]; then
            if [[ "$size" -gt 1048576 ]]; then
                size_human="$(($size / 1048576))MB"
            elif [[ "$size" -gt 1024 ]]; then
                size_human="$(($size / 1024))KB"
            else
                size_human="${size}B"
            fi
        else
            size_human="unknown"
        fi
        
        printf "%-5s %-30s %-15s %-10s\n" "$counter" "$filename" "$timestamp" "$size_human"
        ((counter++))
    done
    
    echo
    return 0
}

# Function to get backup info
get_backup_info() {
    local backup_file="$1"
    
    if [[ ! -f "$backup_file" ]]; then
        print_status "$RED" "❌ Backup file not found: $backup_file"
        return 1
    fi
    
    print_header "BACKUP INFORMATION"
    
    local filename=$(basename "$backup_file")
    local size=$(stat -f%z "$backup_file" 2>/dev/null || stat -c%s "$backup_file" 2>/dev/null || echo "unknown")
    local timestamp=$(echo "$filename" | grep -o '[0-9]\{8\}_[0-9]\{6\}' || echo "unknown")
    local creation_date=""
    
    # Try to extract creation date from timestamp
    if [[ "$timestamp" != "unknown" ]]; then
        local date_part="${timestamp%_*}"
        local time_part="${timestamp#*_}"
        creation_date="${date_part:0:4}-${date_part:4:2}-${date_part:6:2} ${time_part:0:2}:${time_part:2:2}:${time_part:4:2}"
    fi
    
    print_status "$BLUE" "File: $filename"
    print_status "$BLUE" "Path: $backup_file"
    print_status "$BLUE" "Size: $size bytes"
    print_status "$BLUE" "Timestamp: $timestamp"
    if [[ -n "$creation_date" ]]; then
        print_status "$BLUE" "Created: $creation_date"
    fi
    
    # Show GEDCOM statistics
    if [[ -f "$SCRIPT_DIR/verify_data_integrity.sh" ]]; then
        echo
        print_status "$BLUE" "GEDCOM Content Summary:"
        bash "$SCRIPT_DIR/verify_data_integrity.sh" "$backup_file" "$backup_file" --detailed 2>/dev/null | grep -E "^  " | head -10 || true
    fi
    
    return 0
}

# Function to validate backup integrity
validate_backup() {
    local backup_file="$1"
    
    print_header "VALIDATING BACKUP INTEGRITY"
    
    if [[ ! -f "$backup_file" ]]; then
        print_status "$RED" "❌ Backup file not found: $backup_file"
        return 1
    fi
    
    # Check if file is readable
    if [[ ! -r "$backup_file" ]]; then
        print_status "$RED" "❌ Backup file is not readable: $backup_file"
        return 1
    fi
    
    # Check file size
    local size=$(stat -f%z "$backup_file" 2>/dev/null || stat -c%s "$backup_file" 2>/dev/null || echo "0")
    if [[ "$size" -eq 0 ]]; then
        print_status "$RED" "❌ Backup file is empty: $backup_file"
        return 1
    fi
    
    # Check if it looks like a GEDCOM file
    local header=$(head -1 "$backup_file" 2>/dev/null || echo "")
    if [[ ! "$header" =~ ^0.*HEAD ]]; then
        print_status "$YELLOW" "⚠️  Warning: File doesn't start with GEDCOM header (0 HEAD)"
    fi
    
    # Check for basic GEDCOM structure
    local indi_count=$(grep -c "^0.*INDI" "$backup_file" 2>/dev/null || echo "0")
    if [[ "$indi_count" -eq 0 ]]; then
        print_status "$YELLOW" "⚠️  Warning: No individuals (INDI) found in backup file"
    else
        print_status "$GREEN" "✅ Found $indi_count individuals in backup"
    fi
    
    local sour_count=$(grep -c "^0.*SOUR" "$backup_file" 2>/dev/null || echo "0")
    print_status "$GREEN" "✅ Found $sour_count sources in backup"
    
    print_status "$GREEN" "✅ Backup file appears valid"
    return 0
}

# Function to perform rollback
perform_rollback() {
    local backup_file="$1"
    local restore_target="$2"
    
    print_header "PERFORMING ROLLBACK"
    
    # Validate inputs
    if [[ ! -f "$backup_file" ]]; then
        print_status "$RED" "❌ Backup file not found: $backup_file"
        return 1
    fi
    
    # Create directory for restore target if needed
    local restore_dir=$(dirname "$restore_target")
    if [[ ! -d "$restore_dir" ]]; then
        mkdir -p "$restore_dir"
        print_status "$GREEN" "✅ Created directory: $restore_dir"
    fi
    
    # Create a backup of the current file if it exists
    if [[ -f "$restore_target" ]]; then
        local current_backup="${restore_target}.pre-rollback.$(date +%Y%m%d_%H%M%S)"
        cp "$restore_target" "$current_backup"
        print_status "$YELLOW" "💾 Current file backed up to: $current_backup"
    fi
    
    # Copy the backup file to the restore target
    print_status "$BLUE" "Restoring from: $backup_file"
    print_status "$BLUE" "Restoring to: $restore_target"
    
    if cp "$backup_file" "$restore_target"; then
        print_status "$GREEN" "✅ File restored successfully"
        
        # Verify the restore
        local backup_size=$(stat -f%z "$backup_file" 2>/dev/null || stat -c%s "$backup_file")
        local restored_size=$(stat -f%z "$restore_target" 2>/dev/null || stat -c%s "$restore_target")
        
        if [[ "$backup_size" == "$restored_size" ]]; then
            print_status "$GREEN" "✅ Restore verified - sizes match ($backup_size bytes)"
        else
            print_status "$RED" "❌ Restore verification failed - size mismatch"
            print_status "$RED" "   Backup: $backup_size bytes, Restored: $restored_size bytes"
            return 1
        fi
        
    else
        print_status "$RED" "❌ Failed to restore file"
        return 1
    fi
    
    # Log the rollback
    log_rollback "$backup_file" "$restore_target"
    
    return 0
}

# Function to log rollback operation
log_rollback() {
    local backup_file="$1"
    local restore_target="$2"
    local log_file="$LOGS_DIR/rollback_log.txt"
    
    # Create logs directory if it doesn't exist
    if [[ ! -d "$LOGS_DIR" ]]; then
        mkdir -p "$LOGS_DIR"
    fi
    
    # Append to log file
    cat >> "$log_file" << EOF

ROLLBACK OPERATION - $(date)
================================
Backup file: $backup_file
Restored to: $restore_target
Timestamp: $(date)
User: $USER
Backup size: $(stat -f%z "$backup_file" 2>/dev/null || stat -c%s "$backup_file" || echo "unknown") bytes

EOF
    
    print_status "$BLUE" "📝 Rollback logged to: $log_file"
}

# Function for interactive backup selection
interactive_backup_selection() {
    list_backups || return 1
    
    echo
    print_status "$BLUE" "Please select a backup to restore (enter ID number, or 'q' to quit):"
    read -p "Selection: " selection
    
    if [[ "$selection" == "q" ]] || [[ "$selection" == "Q" ]]; then
        print_status "$YELLOW" "Operation cancelled by user"
        exit 0
    fi
    
    # Validate selection is a number
    if ! [[ "$selection" =~ ^[0-9]+$ ]]; then
        print_status "$RED" "❌ Invalid selection. Please enter a number."
        return 1
    fi
    
    # Get the selected backup
    local backups=($(find "$BACKUP_DIR" -name "*_backup_*.ged" -type f | sort -r))
    local selected_backup="${backups[$((selection - 1))]}"
    
    if [[ ! -f "$selected_backup" ]]; then
        print_status "$RED" "❌ Invalid selection ID: $selection"
        return 1
    fi
    
    echo "$selected_backup"
}

# Main function
main() {
    local backup_timestamp=""
    local restore_to=""
    local backup_file=""
    local interactive=true
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --backup-timestamp)
                backup_timestamp="$2"
                interactive=false
                shift 2
                ;;
            --restore-to)
                restore_to="$2"
                shift 2
                ;;
            --list)
                list_backups
                exit 0
                ;;
            --help|-h)
                print_status "$BLUE" "Usage: $0 [OPTIONS]"
                print_status "$BLUE" "Options:"
                print_status "$BLUE" "  --backup-timestamp TIMESTAMP  Restore from specific backup timestamp"
                print_status "$BLUE" "  --restore-to PATH             Restore to specific path"
                print_status "$BLUE" "  --list                        List available backups"
                print_status "$BLUE" "  --help                        Show this help"
                exit 0
                ;;
            *)
                print_status "$RED" "Unknown option: $1"
                exit 1
                ;;
        esac
    done
    
    print_header "GEDCOM ROLLBACK RECOVERY"
    print_status "$BLUE" "Project root: $PROJECT_ROOT"
    print_status "$BLUE" "Backup directory: $BACKUP_DIR"
    print_status "$BLUE" "Operation time: $(date)"
    
    # Check if backup directory exists
    if [[ ! -d "$BACKUP_DIR" ]]; then
        print_status "$RED" "❌ No backup directory found"
        print_status "$RED" "   Run setup_processing_workspace.sh first to create backups"
        exit 1
    fi
    
    # Find backup file
    if [[ -n "$backup_timestamp" ]]; then
        # Look for backup with specific timestamp
        backup_file=$(find "$BACKUP_DIR" -name "*_backup_${backup_timestamp}.ged" -type f | head -1)
        if [[ -z "$backup_file" ]]; then
            print_status "$RED" "❌ No backup found with timestamp: $backup_timestamp"
            list_backups
            exit 1
        fi
    elif [[ "$interactive" == "true" ]]; then
        # Interactive mode
        backup_file=$(interactive_backup_selection)
        if [[ $? -ne 0 ]] || [[ -z "$backup_file" ]]; then
            exit 1
        fi
    else
        # Use latest backup
        backup_file=$(find "$BACKUP_DIR" -name "*_backup_*.ged" -type f | sort -r | head -1)
        if [[ -z "$backup_file" ]]; then
            print_status "$RED" "❌ No backup files found"
            exit 1
        fi
    fi
    
    # Determine restore target
    if [[ -z "$restore_to" ]]; then
        # Check if we have backup info to determine original location
        if [[ -f "$BACKUP_DIR/.last_backup_info" ]]; then
            local original_file=$(grep "^SOURCE_FILE=" "$BACKUP_DIR/.last_backup_info" | cut -d= -f2)
            if [[ -n "$original_file" ]]; then
                restore_to="$original_file"
                print_status "$BLUE" "Restore target determined from backup info: $restore_to"
            fi
        fi
        
        if [[ -z "$restore_to" ]]; then
            print_status "$YELLOW" "⚠️  No restore target specified"
            print_status "$BLUE" "Please specify --restore-to path/to/restore/file.ged"
            exit 1
        fi
    fi
    
    # Show backup information
    get_backup_info "$backup_file"
    
    # Validate backup
    if ! validate_backup "$backup_file"; then
        print_status "$RED" "❌ Backup validation failed"
        exit 1
    fi
    
    # Confirm rollback operation
    echo
    print_status "$YELLOW" "⚠️  ROLLBACK CONFIRMATION"
    print_status "$YELLOW" "This will restore:"
    print_status "$YELLOW" "  From: $(basename "$backup_file")"
    print_status "$YELLOW" "  To:   $restore_to"
    echo
    print_status "$RED" "⚠️  Any existing file at the target location will be backed up and replaced."
    echo
    read -p "Are you sure you want to proceed? (yes/no): " confirm
    
    if [[ "$confirm" != "yes" ]] && [[ "$confirm" != "y" ]]; then
        print_status "$YELLOW" "Operation cancelled by user"
        exit 0
    fi
    
    # Perform rollback
    if perform_rollback "$backup_file" "$restore_to"; then
        print_header "ROLLBACK COMPLETE"
        print_status "$GREEN" "✅ Rollback successful"
        print_status "$GREEN" "✅ File restored to: $restore_to"
        print_status "$BLUE" "🔍 You may want to verify the restored file:"
        print_status "$BLUE" "   bash scripts/verify_data_integrity.sh $backup_file $restore_to"
        exit 0
    else
        print_status "$RED" "❌ Rollback failed"
        exit 1
    fi
}

# Run main function with all arguments
main "$@"
