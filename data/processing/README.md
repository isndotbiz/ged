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

