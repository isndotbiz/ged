# GEDCOM Cleanup Roadmap

This step-by-step guide walks you through resolving the remaining 261 consistency issues flagged in **Tree Consistency Checker – Mallinger Web Site – MyHeritage.pdf** and preparing a publication-ready GEDCOM for upload to FamilySearch.

---
## 1  Establish a Master Workflow
| Stage | Goal | Primary Tool | Key Tips |
|-------|------|--------------|----------|
| 1.1 | Back-up & version control | Git `main` branch in `/ged` | Commit before & after each batch fix |
| 1.2 | Single source of truth | RootsMagic 10 database | Import `final.names_dedup.ged`; **all edits happen here** |
| 1.3 | Sync mirrors |  • Family Tree Maker (for Ancestry sync)  <br>• MyHeritage online tree | Periodically export GEDCOM from RootsMagic and re-import/overwrite |

---
## 2  Bulk Automated Fixes (already done)
1. Conservative fact de-duplication ✔︎  
2. Duplicate ALT NAME removal ✔︎  
3. Duplicate family-link removal (1992 links) ✔︎  
4. Normalized spellings for Blessenbach & Runkle ✔︎  

---
## 3  Targeted Batch Fixes (30-minute passes)
| Batch | Issue Types (PDF codes) | Estimated Records | How-to |
|-------|------------------------|-------------------|--------|
| B-1 | **Suffix / name-field misuse**  (`SUFFIX01`, `NAME01`) | 8 | RootsMagic 👉 search ➜ Name contains " II ", " Jr ", etc. ➜ Move to *Suffix* field |
| B-2 | **Married names in maiden field** | 14 | MR 10 👉 People List ➜ Filter “/ (nee” or known married surnames ➜ Add ALT NAME with `TYPE married` |
| B-3 | **Place variants**  (`PLACE01`) | 47 | Edit ➜ Place List ➜ Merge “Hessen-Nassau” ⇢ “Hessen Nassau”, etc. |
| B-4 | **Impossible dates** (parent age < 13 or > 70) (`DATE01`) | 32 families | RM Problem List ➜ “Parents too young/old” ➜ verify with sources |
| B-5 | **Missing sources** (`MISSING01`) | 160+ facts | RootsMagic “Fact Type List” ➜ Sort by Sources=0 ➜ Add or tag citation |

Batch rule: work top-to-bottom; when finished **export GEDCOM**, commit, import into FTM & MyHeritage, verify diff, then proceed to next batch.

---
## 4  Interactive Validation Loops
| Check | Tool | Frequency |
|-------|------|-----------|
| RootsMagic Problem List | RootsMagic 10 | After each batch |
| GED-Lint (command-line) | `python -m gedlint` | Before every export |
| MyHeritage Consistency Checker | MyHeritage Web | Weekly |
| Ancestry Tree Hints | FTM sync | After major batches |

---
## 5  Source-Enrichment Sprint
Goal: **≥ 1 reliable source per BIRT, DEAT, MARR fact** (FamilySearch upload prerequisite)
1. Run RootsMagic’s *Fact List → Facts with no source* report.  
2. Prioritize earliest generations (hardest).  
3. Use integrated WebHints (Ancestry + MyHeritage) & FamilySearch Search.  
4. Add citation; tag the fact; mark as “Primary”.

---
## 6  FamilySearch Readiness Checklist
- [ ] No duplicate individuals (RootsMagic *Duplicates Search* = 0)  
- [ ] No duplicate family links (script ✔︎)  
- [ ] No unresolved RootsMagic problems  
- [ ] ≥ 1 source per core fact  
- [ ] All place names use FamilySearch Standardized Places  
- [ ] File passes GED-Lint with 0 errors/warnings  

When all boxes are ticked, **export “Mallinger_ReadyForFS.ged”** and upload to FamilySearch (bulk‐add via RootsMagic or GEDCOM upload assistant).

---
## 7  Future Automation Ideas
1. Extend `remove_dup_links.py` to flag impossible date spans.
2. Integrate spell-checker against FamilySearch place-API.
3. Unit-test scripts with pytest + sample GEDCOM fixtures.
4. GitHub Actions CI: run GED-Lint on every commit.

