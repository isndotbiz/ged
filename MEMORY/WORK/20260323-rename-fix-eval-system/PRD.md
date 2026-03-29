---
task: Fix rename bugs and build data quality eval system
slug: 20260323-rename-fix-eval-system
effort: advanced
phase: complete
progress: 0/28
mode: interactive
started: 2026-03-23T00:20:00Z
updated: 2026-03-23T00:22:00Z
---

## Context

Fix the batch_rename_media Rust command (space-before-extension bug + silent failures), then upgrade the Issues page from a read-only checker into an actionable data quality eval system with auto-fix, bulk select, and dismiss functionality.

### Risks
- Auto-fix modifying data incorrectly (swap birth/death when only one is wrong)
- Bulk operations without undo destroying genealogical data
- Rust build failures from syntax errors
- Tree analyzer changes breaking existing analysis flow

## Criteria

### Rename Fix (Rust)
- [ ] ISC-1: Extension format uses no space: `{name}.{ext}` not `{name}. {ext}`
- [ ] ISC-2: Failed items returned as array of paths in RenameResult
- [ ] ISC-3: RenameResult includes `failed_paths: Vec<String>` field
- [ ] ISC-4: Settings UI displays failed paths when failures occur

### TreeIssue Model Enhancement
- [ ] ISC-5: TreeIssue type has `autoFixable: boolean` field
- [ ] ISC-6: TreeIssue type has `fixDescription: string` field
- [ ] ISC-7: Tree analyzer sets autoFixable true for fixable issue types
- [ ] ISC-8: Tree analyzer provides fixDescription for each fixable issue

### Auto-Fix Engine (db.ts + tree-analyzer.ts)
- [ ] ISC-9: `applyFix(issue: TreeIssue)` function exists in db.ts or new file
- [ ] ISC-10: "Death before birth" fix swaps birth and death dates
- [ ] ISC-11: "Future birth date" fix clears the birth date field
- [ ] ISC-12: "Person married to self" fix clears partner2 on the family
- [ ] ISC-13: "Family with no partners" fix deletes the family record
- [ ] ISC-14: Fix function returns success/failure result

### Issues Page UI — Bulk Select
- [ ] ISC-15: Each issue row has a checkbox for selection
- [ ] ISC-16: "Select All" checkbox in header selects all visible issues
- [ ] ISC-17: Selected count displayed in toolbar
- [ ] ISC-18: Selection persists across filter changes

### Issues Page UI — Fix Actions
- [ ] ISC-19: "Fix" button visible on auto-fixable issues
- [ ] ISC-20: "Fix Selected" bulk button fixes all selected auto-fixable issues
- [ ] ISC-21: Fixed issues removed from list after successful fix
- [ ] ISC-22: Non-fixable selected issues skipped with count shown

### Issues Page UI — Dismiss
- [ ] ISC-23: "Dismiss" button on each issue row
- [ ] ISC-24: "Dismiss Selected" bulk button removes selected issues from view
- [ ] ISC-25: Dismissed issues stored so they don't reappear on re-analysis

### Verification
- [ ] ISC-26: npm run check has zero new errors
- [ ] ISC-27: npm run build passes
- [ ] ISC-28: Rust cargo check passes for src-tauri

## Decisions

## Verification
