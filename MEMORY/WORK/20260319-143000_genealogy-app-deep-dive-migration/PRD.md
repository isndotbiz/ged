---
task: Genealogy app deep dive and migration plan
slug: 20260319-143000_genealogy-app-deep-dive-migration
effort: deep
phase: complete
progress: 48/48
mode: interactive
started: 2026-03-19T14:30:00-05:00
updated: 2026-03-19T14:32:00-05:00
---

## Context

Jonathan has Family Tree Maker 2024, RootsMagic 11, Ancestry, and MyHeritage (all paid). He has 3 source files: a DNA data zip, a cleaned GEDCOM, and a raw GEDCOM. He wants a comprehensive analysis covering: which commercial software to use as interim source of truth, what open source projects to borrow from, and a full architecture/roadmap for building a Mac-native genealogy app with AI features, Playwright scraping, and local-first privacy. The existing `gedfix` repo has substantial reusable GEDCOM processing code.

### Risks
- Scope is research-heavy — risk of producing shallow analysis across too many topics
- Commercial software features change frequently — recommendations may drift
- Legal risks around scraping genealogy sites (Ancestry ToS, CFAA)
- Building a full Mac-native app is a multi-month effort — MVP scoping is critical

## Criteria

- [x] ISC-1: FTM 2024 evaluated with pros, cons, export fidelity
- [x] ISC-2: RootsMagic 11 evaluated with pros, cons, export fidelity
- [x] ISC-3: Ancestry.com evaluated with lock-in and API access
- [x] ISC-4: MyHeritage evaluated with data portability and DNA
- [x] ISC-5: Clear recommendation for interim source-of-truth software
- [x] ISC-6: Migration path from each platform documented
- [x] ISC-7: Gramps evaluated as fork/borrow candidate
- [x] ISC-8: webtrees evaluated as fork/borrow candidate
- [x] ISC-9: At least 5 open source projects evaluated
- [x] ISC-10: GEDCOM 7.0 vs 5.5.1 format comparison provided
- [x] ISC-11: Best open source algorithms to borrow identified
- [x] ISC-12: Mac native tech stack recommendation with rationale
- [x] ISC-13: Database architecture recommendation (SQLite vs Core Data)
- [x] ISC-14: AI integration architecture documented (local + cloud)
- [x] ISC-15: Multi-API-key management approach specified
- [x] ISC-16: Specific AI features for genealogy listed with feasibility
- [x] ISC-17: Secure API key storage approach documented
- [x] ISC-18: Playwright scraping architecture documented
- [x] ISC-19: Rate limiting strategy for scraping specified
- [x] ISC-20: Legal considerations for scraping covered
- [x] ISC-21: Plugin system architecture documented
- [x] ISC-22: Sync/merge strategy between platforms documented
- [x] ISC-23: Conflict resolution UX approach described
- [x] ISC-24: Privacy and encryption practices specified
- [x] ISC-25: Living person filtering approach documented
- [x] ISC-26: Automated test strategy recommended
- [x] ISC-27: CI/CD pipeline suggestion provided
- [x] ISC-28: This repo audited — files to keep listed
- [x] ISC-29: This repo audited — files to remove listed
- [x] ISC-30: This repo audited — files to refactor listed
- [x] ISC-31: gedfix library reuse plan documented
- [x] ISC-32: Export scripts/commands for FTM provided
- [x] ISC-33: Export scripts/commands for RootsMagic provided
- [x] ISC-34: GEDCOM validation commands provided
- [x] ISC-35: Dedupe and merge commands provided
- [x] ISC-36: Open source license recommendation provided
- [x] ISC-37: Contributor guidelines outlined
- [x] ISC-38: MVP roadmap with milestones documented
- [x] ISC-39: MVP milestone 1 scope defined
- [x] ISC-40: MVP milestone 2 scope defined
- [x] ISC-41: MVP milestone 3 scope defined
- [x] ISC-42: Learning resources listed (webtrees, Gramps, etc.)
- [x] ISC-43: Example projects to study listed with URLs
- [x] ISC-44: Next-actions checklist with exact commands provided
- [x] ISC-45: DNA data integration approach documented
- [x] ISC-46: Data format comparison table (GEDCOM, XML, CSV, SQLite)
- [x] ISC-47: GEDCOM X / FamilySearch format assessed
- [x] ISC-48: Existing downloaded files (3) assessed and usage plan provided

- [x] ISC-A1: No time estimates in roadmap (milestones only)

## Decisions

## Verification
