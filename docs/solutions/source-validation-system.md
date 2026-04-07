---
title: Source Validation System
date: 2026-04-06
tags: [genealogy, data-quality, sources, validation, sqlite]
problem: No way to distinguish online tree hints from primary record sources
solution: sourceType column with auto-classification + per-person validationStatus
---

# Source Validation System

## Problem
A person with 3 "Ancestry Member Tree" citations looked the same as one with 3 census records. `sourceCount > 0` was the only check — meaningless for data quality.

## Solution

### Schema
```sql
ALTER TABLE source ADD COLUMN sourceType TEXT DEFAULT 'unknown';
-- Values: online_tree, vital_record, census, newspaper, church_record, military, immigration, other, unknown

ALTER TABLE person ADD COLUMN validationStatus TEXT DEFAULT 'unvalidated';
-- Values: validated (non-tree source), tree_only (only online trees), unvalidated (no sources)
```

### Auto-Classification
Pattern match on source title/publisher at import time:
- `%ancestry%tree%`, `%familysearch%family%tree%`, `%myheritage%tree%` → `online_tree`
- `%birth%record%`, `%certificate%`, `%vital%` → `vital_record`
- `%census%` → `census`
- `%church%`, `%parish%`, `%baptis%` → `church_record`
- etc.

### Validation Status Computation
```sql
-- Validated: has at least one non-tree source
UPDATE person SET validationStatus = 'validated'
WHERE xref IN (
  SELECT DISTINCT c.personXref FROM citation c
  JOIN source s ON s.xref = c.sourceXref
  WHERE s.sourceType NOT IN ('online_tree', 'unknown')
);
```

### Known Limitation
GEDCOM parser doesn't extract SOUR references on INDI records into the citation table. Until that's fixed, all persons show as 'unvalidated' even if sources exist. The source classification itself works.

### UI
- Dashboard shows validated/tree_only/unvalidated counts with progress bar
- Sources page has colored type badges + filter tabs + manual reclassify dropdown
- Tree analyzer flags tree-only sources as warnings
