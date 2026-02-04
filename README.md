# GEDCOM Processing and Data Integrity Platform

A production-grade system for cleaning, standardizing, and validating GEDCOM 5.5.1 datasets with audit trails and zero data loss guarantees.

## Investor Summary
Genealogy data is high-value but messy, fragmented, and expensive to clean. This project delivers an automated, integrity-first pipeline that transforms raw GEDCOM files into standardized, import-ready assets while preserving every relationship. It combines a reusable Python library, a CLI, and a full processing workflow designed for repeatable, professional-grade outcomes.

## Product Scope and Capabilities
- GEDCOM scanning, issue detection, and prioritized reporting.
- Date normalization, name and place standardization with AutoFix notes.
- Relationship-preserving deduplication and safe merges.
- End-to-end processing workflows with backups, verification, and rollback.
- Exported outputs for major genealogy platforms plus actionable review reports.

## Differentiation and Moat
- Integrity-first design with explicit verification and rollback tooling.
- Auditable processing with documented change trails.
- A packaged CLI and library that can be embedded into larger systems.

## Evidence of Execution
- A fully packaged Python library and CLI in `gedfix/` with `pyproject.toml`.
- Complete processing workflow scripts in `scripts/`.
- Documented project completion and quality metrics in `PROJECT_COMPLETE.md`.
- Methodology and safety tooling in `PROCESSING_PLAN.md` and `DATA_INTEGRITY_TOOLS.md`.

## Technology Stack
- Python 3.11
- ged4py, click, rapidfuzz, python-dateutil

## Commercial Use Cases
- Genealogy services and archives with large data migrations.
- Data cleansing for legacy family tree products.
- Premium integrity-verified processing for professional genealogists.

## Repository Map
```
gedfix/                  Core library and CLI
scripts/                 Processing, integrity, and rollback utilities
data/processing/          Workspaces, exports, and reports
docs/                     Methodology and analysis docs
```

## Quick Start
```
python -m venv venv
source venv/bin/activate
pip install -e .

gedfix scan input.ged --report scan_report.json
gedfix fix input.ged --out cleaned.ged --level standard --backup-dir ./backups
./scripts/verify_data_integrity.sh input.ged cleaned.ged
```

## License
MIT
