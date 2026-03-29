---
task: Build best possible SwiftUI Mac genealogy app
slug: 20260320-010000_gedfix-swiftui-app
effort: deep
phase: execute
progress: 0/24
mode: interactive
started: 2026-03-20T01:00:00-05:00
updated: 2026-03-20T01:00:00-05:00
---

## Context

Build a production-quality SwiftUI Mac app (GedFix.app) that competes with MacFamilyTree and RootsMagic on UX while integrating the gedfix Python CLI. Research-first design, GRDB + SQLite backend, modern macOS look.

## Criteria

- [ ] ISC-1: Xcode project builds without errors on macOS 14+
- [ ] ISC-2: GRDB.swift dependency resolves via SPM
- [ ] ISC-3: SQLite schema created with persons, families, events, places, sources tables
- [ ] ISC-4: GEDCOM 5.5.1 parser in Swift extracts INDI records
- [ ] ISC-5: GEDCOM parser extracts FAM records with spouse/child links
- [ ] ISC-6: GEDCOM parser extracts events (BIRT, DEAT, MARR) with dates and places
- [ ] ISC-7: Import GEDCOM file via File > Open menu
- [ ] ISC-8: NavigationSplitView with sidebar, list, detail columns
- [ ] ISC-9: Sidebar shows People, Families, Places, Sources categories with counts
- [ ] ISC-10: Person list is searchable and sortable by name
- [ ] ISC-11: Person detail shows name, sex, events, family links
- [ ] ISC-12: Family detail shows spouses and children
- [ ] ISC-13: Navigation between related persons via clickable links
- [ ] ISC-14: Living persons visually indicated (privacy badge)
- [ ] ISC-15: Stats view showing tree overview (count, date range, top surnames)
- [ ] ISC-16: Modern macOS styling (SF Symbols, proper spacing, accent colors)
- [ ] ISC-17: CLIBridge shells out to gedfix for fix/check operations
- [ ] ISC-18: Import progress indicator during GEDCOM loading
- [ ] ISC-19: Place list view with location data
- [ ] ISC-20: Source list view
- [ ] ISC-21: App icon and window title set
- [ ] ISC-22: Dark mode support
- [ ] ISC-23: Toolbar with import and search actions
- [ ] ISC-24: App compiles and runs showing real data from a GEDCOM file

## Decisions

## Verification
