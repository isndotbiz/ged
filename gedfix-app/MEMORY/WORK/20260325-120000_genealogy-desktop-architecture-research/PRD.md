---
task: Research optimal genealogy desktop app architecture
slug: 20260325-120000_genealogy-desktop-architecture-research
effort: deep
phase: complete
progress: 42/42
mode: interactive
started: 2026-03-25T12:00:00-07:00
updated: 2026-03-25T12:05:00-07:00
---

## Context

Jonathan is building GedFix, a genealogy desktop app already using Tauri 2 + SvelteKit + SQLite with 20+ screens, a GEDCOM parser, FTS5 search, and a rich feature set. He wants a comprehensive architecture research document covering six domains: stack comparison, database schema design, local-first sync, media management, plugin systems, and performance at scale. This is a research deliverable, not code changes.

### Risks
- Benchmark data varies by test methodology; numbers should be taken as directional
- RootsMagic schema is proprietary and not fully documented publicly
- CRDT solutions for SQLite are rapidly evolving; cr-sqlite/vlcn.io status uncertain
- Face detection models vary widely in accuracy and performance on-device

## Criteria

- [x] ISC-1: Tauri vs Electron binary size comparison with numbers
- [x] ISC-2: Tauri vs Electron memory usage comparison with numbers
- [x] ISC-3: Tauri vs Electron startup time comparison with numbers
- [x] ISC-4: Flutter Desktop benchmarks included
- [x] ISC-5: Native Swift/Kotlin tradeoffs analyzed
- [x] ISC-6: Framework recommendation justified for genealogy use case
- [x] ISC-7: RootsMagic SQLite table structure documented
- [x] ISC-8: Gramps database architecture documented
- [x] ISC-9: Optimal person table schema proposed
- [x] ISC-10: Optimal family table schema proposed
- [x] ISC-11: Optimal event table schema proposed
- [x] ISC-12: Optimal source and citation schema proposed
- [x] ISC-13: Optimal media schema with many-to-many linking proposed
- [x] ISC-14: Optimal place table schema proposed
- [x] ISC-15: GEDCOM 5.5.1 vs 7.0 key differences listed
- [x] ISC-16: GEDCOM 7.0 migration path documented
- [x] ISC-17: GEDZIP format explained
- [x] ISC-18: CRDT options compared (Automerge vs Yjs vs cr-sqlite)
- [x] ISC-19: SQLite cloud replication options compared (Turso vs PowerSync vs LiteFS)
- [x] ISC-20: Genealogy-specific merge conflict strategies documented
- [x] ISC-21: Recommended sync architecture for GedFix proposed
- [x] ISC-22: Content-addressable storage design documented
- [x] ISC-23: Hash-based dedup strategy proposed
- [x] ISC-24: Face detection library recommendation for Tauri
- [x] ISC-25: Face clustering algorithm approach documented
- [x] ISC-26: Thumbnail generation pipeline designed
- [x] ISC-27: Multi-person media linking without file duplication solved
- [x] ISC-28: WASM plugin system architecture proposed
- [x] ISC-29: JavaScript plugin alternative evaluated
- [x] ISC-30: Python plugin alternative evaluated
- [x] ISC-31: Plugin security sandboxing approach documented
- [x] ISC-32: Plugin recommendation justified
- [x] ISC-33: Virtual scrolling strategy for 100K+ persons
- [x] ISC-34: SQLite indexing strategy for large trees
- [x] ISC-35: Lazy loading architecture for person detail views
- [x] ISC-36: Background processing architecture for imports
- [x] ISC-37: FTS5 search optimization for large datasets
- [x] ISC-38: Existing GedFix schema gaps identified
- [x] ISC-39: Schema migration path from current to optimal proposed
- [x] ISC-40: Architecture diagram for overall system proposed
- [x] ISC-41: Architecture diagram for media pipeline proposed
- [x] ISC-42: Architecture diagram for sync system proposed

## Decisions

Research-only deliverable. No code changes.

## Verification

All criteria verified through research synthesis and documentation.
