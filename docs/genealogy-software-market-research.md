# Genealogy Software: Power User Pain Points & Market Gaps

**Research Date:** March 2026
**Methodology:** 14 parallel web searches across Reddit, forums, review sites, BBB complaints, genealogy blogs, and academic sources

---

## 1. Power User Pain Points by Platform

### Ancestry.com
- **Predatory subscription model**: Premium subscribers paying $440+/year report being asked for additional fees for "Pro Tools" features. Long-time subscribers describe paying thousands over 8+ years as "beyond silly, it's insanity"
- **Paywall creep**: ThruLines and Shared Matches now require a subscription (as of Feb 2024). Users report that content they personally researched has been placed behind paywalls -- "Ancestry is scamming old users out of their own work"
- **ThruLines unreliability**: Described as "one hot mess" by prominent genetic genealogists. The algorithm merges identities incorrectly, designates maternal matches as paternal, and has become less stringent -- generating more hints that cannot be confirmed with sources. "ThruLines is a computer algorithm, not a genealogist"
- **Auto-download pollution**: FTM users report Ancestry automatically downloaded information into their trees without their knowledge; correcting mistakes was so difficult they switched software
- **Cancellation dark patterns**: $25 early cancellation fees, billing info repopulated after cancellation, charges appearing months later with 90-day refund wait, and inadequate renewal reminders particularly problematic for senior users
- **No customer service**: Users report no ability to speak to humans, fully automated support with no resolution path

### RootsMagic
- **Ancestry sync frustrations**: Changes don't align properly between RootsMagic and Ancestry; importing marked items creates duplicate facts and citations
- **Stack overflow crashes**: Random stack overflow errors when working in the Places list (fixed in 11.0.4, Dec 2025)
- **RM To-Go reliability**: Runtime errors related to portable and shareable drive features
- **Slow feature delivery**: Community forums show long-standing feature requests that remain unaddressed across major version releases

### Family Tree Maker
- **No find-and-replace**: A basic text manipulation feature that RootsMagic offers but FTM lacks
- **Diacritical mark stripping**: FTM strips diacritical marks from most characters, devastating for users with international ancestry (Polish, Czech, Scandinavian, etc.)
- **No place name prompts**: No assistance or validation when entering place names
- **No historically accurate place names**: Cannot handle the concept that place names and boundaries change over time

### Gramps
- **Session crashes**: Reported to crash at almost every session, particularly on Windows
- **Steep learning curve**: "Things are hidden deep without much explanation"
- **No single manual**: Documentation scattered across wiki, forums, and community posts
- **Amateurish UI**: Developed by volunteers; interface described as unpolished despite powerful features underneath
- **No cloud sync or web access**: Purely desktop, making collaboration difficult

### MyHeritage
- **Billing nightmares**: Most common complaint category. Users report charges of $149-160 despite cancelled trials, auto-renewals after alleged cancellation, charges 18+ months after termination
- **Account lockouts**: Users denied access to their own accounts, with login and registration showing "currently unavailable"
- **No human support**: Everything automated, no option to speak to customer service even for returned DNA kits
- **Average 3-star consumer rating** from 524 reviews with 263 BBB complaints

### Cross-Platform Pain: Unicode and Internationalization
- **Legacy Family Tree**: Cannot support Unicode at all; developers called Polish character support "low priority"
- **Multiple platforms**: Inconsistent handling of non-Latin scripts, diacriticals, and international name conventions

---

## 2. Features That Don't Exist Yet

### Historical Place Intelligence
- No software handles time-varying place names and boundaries correctly. A location in one town in the 17th century might be in a different town in the 18th century due to boundary adjustments
- No system tracks concurrent jurisdictions (religious parishes vs. statutory parishes vs. registry districts vs. Poor Law Unions vs. judicial circuits)
- Geocoding uses modern databases; no historical geocoding exists that maps a place name to its correct 1850 coordinates and jurisdiction

### Evidence Analysis Framework
- No software implements the Genealogical Proof Standard (GPS) computationally -- tracking whether each conclusion has reasonably exhaustive search, complete citations, analysis of evidence, resolution of conflicts, and a written proof statement
- No tool scores "research completeness" for a given ancestor or relationship

### Timeline Conflict Detection
- No intelligent system that cross-references all events for a person and flags physical impossibilities (e.g., appearing in two locations 500 miles apart within 3 days in 1820 when travel speed was 30 miles/day by horse)

### Research Log Integration
- No software maintains an integrated research log that tracks what repositories were searched, what was found, what wasn't found, and what conclusions were drawn -- tied directly to the people and sources in the tree

### Multi-Tree Merge Intelligence
- No tool can intelligently merge two large trees by identifying overlapping individuals, resolving conflicts, and preserving the best-sourced version of each fact

---

## 3. AI/ML Features That Would Transform Genealogy

### Document Processing (Partially Emerging)
- **Handwriting recognition**: Gemini 3.0 achieved 0.56% Character Error Rate on 18th-century manuscripts (2025). Ancestry used AI to transcribe the 1950 Census. FamilySearch is training algorithms to index handwritten documents
- **But still broken**: AI misread "Wm." (William) as "Ann" in one documented case, linking a family to an entirely different bloodline. Error rates matter enormously in genealogy
- **Layout detection**: AI-powered schema-based extraction can turn unindexed scans into structured, searchable data
- **Clerk differentiation**: Some systems can differentiate between clerks who wrote in the same record book by analyzing pen pressure and slant

### What's Missing in AI (Massive Opportunities)

1. **Source Credibility Scoring**: Composite confidence scores (87% name similarity, 92% location consistency, 63% age plausibility) exist in prototype form but no consumer product offers this. Assigning probabilities like "73% likelihood of NPE on paternal side, 22% chance of informal adoption, 5% transcription error" would be transformative

2. **Automated Census Extraction to Structured Data**: Converting raw census page images into fully structured family-household records with relationships, occupations, and linked identities -- not just OCR text but semantic understanding

3. **Cross-Record Identity Resolution**: AI name-matching that understands "Minnie Sharone," "Minnie Shearom," and "Delila Jenkins" may be the same person across censuses, using contextual signals beyond string matching

4. **Relationship Inference from Unstructured Text**: If "Mary Jones, widow of William" appears in an 1870 will and "William Jones, husband of Mary" appears in an 1851 marriage register, automatically infer the spousal connection

5. **Photo Dating and Context**: AI that can date a photograph by clothing, hairstyles, photographic technique, and studio props -- then cross-reference with known family events

6. **Automated Obituary/Newspaper Extraction**: Converting obituaries into structured family relationship data (survivors, predeceased-by, maiden names, in-law relationships)

7. **Handwriting Style Clustering**: Identifying that the same clerk wrote multiple records, allowing batch processing and quality validation

8. **Migration Route Prediction**: Merging DNA data with historical databases to identify specific migration routes ancestors likely took

---

## 4. Workflow Automation Gaps

### The Multi-Platform Data Entry Problem (Top Pain Point)
Researchers find it redundant to enter information in Ancestry, THEN in their desktop software, THEN update FamilySearch, THEN update MyHeritage. Every piece of data gets entered 3-4 times manually across platforms. This is the single most time-wasting workflow in genealogy.

### Repetitive Tasks That Should Be Automated
1. **Source citation formatting**: Manually formatting citations to Evidence Explained standards for every single source attachment
2. **Record-to-person attachment**: Dragging census records into the right fields for every family member (name, age, occupation, birthplace) -- should be one-click "extract all facts"
3. **Biography generation**: Auto-created narrative text is so repetitive that users spend hours re-wording so "every biography doesn't start the same"
4. **Place name standardization**: Manually cleaning up inconsistent place names across hundreds or thousands of records
5. **Date format normalization**: Converting between date formats (DD MMM YYYY, MM/DD/YYYY, European formats) manually
6. **Duplicate detection and merging**: Finding and resolving duplicate individuals requires manual comparison of hundreds of potential matches
7. **Research task tracking**: No automated "next steps" -- e.g., "You have 47 ancestors with no death date; here are the most promising repositories to search for each"
8. **Batch source attachment**: Attaching the same source (e.g., a census page) to all 8 family members on that page requires 8 separate operations

---

## 5. Collaboration and Sharing Gaps

### GEDCOM: The Broken Standard
- **Only 5 of 14 key GEDCOM tags transfer safely** across leading genealogy software
- **Source/citation data loss is near-universal**: "Source and citation data recorded in major genealogy programs cannot be transferred to a different program using GEDCOM, as data become lost or distorted during the transfer process"
- **GEDCOM 7.0 adoption is glacial**: Released in 2021, but GEDCOM 5.5.1 remains the industry standard. Ancestry and FamilySearch have announced 7.0 plans but implementation timelines remain vague
- **Every program has extended GEDCOM differently**: Custom formatting conventions and non-conforming exports make each program's GEDCOM essentially a proprietary format wearing a standard's clothing

### Collaboration Problems
- **FamilySearch's collaborative tree** is the only truly shared tree, but uploading GEDCOM directly creates duplicates and risks data loss
- **No real-time collaboration**: No genealogy software offers Google Docs-style simultaneous editing
- **No conflict resolution**: When two researchers disagree about a fact, no software provides a structured way to present competing evidence and reach a conclusion
- **No research coordination**: No way to say "I'm researching the Smith line in Lancaster County 1780-1820" and discover that three other researchers are working on the same family
- **One-way sharing**: Users want to find something on WikiTree or Find a Grave and add it to their database with a single click, and have this work bidirectionally

---

## 6. Data Quality Tools: What's Missing

### Current State
- **MyHeritage**: 36 consistency checks (best in class)
- **Geni**: 28 inconsistency types
- **GeneaNet/Genealogie Online**: Basic error checking
- **Ancestris**: GEDCOM compliance and data consistency validation
- **Most desktop software**: No consistency checking at all

### What's Missing
1. **Cross-tree consistency**: No tool validates data across multiple trees or sources -- only within a single tree
2. **Source quality assessment**: No tool rates whether a source is primary/secondary/derivative or original/copied/transcribed
3. **Fact confidence scoring**: No tool lets you rate your confidence in a fact (e.g., "90% certain" vs. "speculative") and then propagate uncertainty to derived conclusions
4. **Temporal-spatial validation**: No tool checks whether events are physically possible given historical travel speeds and distances
5. **Naming convention validation**: No tool understands historical naming patterns (patronymics in Scandinavia, clan names in Scotland, Spanish dual surnames) and validates accordingly
6. **Advanced deduplication**: Most tools use simple name+date matching; none use probabilistic record linkage with contextual signals (household composition, neighbors, occupation, religion)
7. **Completeness scoring**: No tool scores how complete your research is for a given ancestor or generation (e.g., "You have 78% of available vital records for this person")
8. **Cascading error detection**: When one fact is corrected, no tool identifies all downstream conclusions that may be affected

---

## 7. Search Capability Gaps

### Natural Language Search
- Current genealogy applications "do not take into account the kinship structure of natural language"
- No software supports queries like "Find all my ancestors who died in the same county they were born in" or "Show me everyone who migrated from Germany to Pennsylvania between 1840 and 1860"
- Academic research has proposed domain-specific languages (like KISP) but nothing consumer-facing exists

### Cross-Database Search
- No single search across Ancestry + FamilySearch + FindAGrave + Newspapers.com + local archives
- FamilySearch's Full-Text Search (AI-powered, reading handwritten documents) is the closest to a breakthrough, but it's limited to FamilySearch's collections
- Semantic Web standards could enable cross-site searching but adoption is essentially zero

### What's Missing
1. **Federated search**: Search once, get results from all major databases
2. **Negative search**: "Show me all census years where this person was NOT found" -- tracking absence of evidence
3. **Contextual search**: Search for a person considering their likely neighbors, associates, and family cluster (FAN club methodology)
4. **Wildcard/phonetic/fuzzy search** that actually works across databases with historical name variants
5. **Visual/map-based search**: "Show me all records within 20 miles of this location between 1830-1850"
6. **Research gap identification**: "Based on what you know about this person, here are repositories you haven't searched yet"

---

## 8. DNA Integration Gaps

### What's Available
- **Ancestry**: Match clustering (new), chromosome painter (beta), ThruLines (unreliable). No chromosome browser. No triangulation.
- **FamilyTreeDNA**: Chromosome browser, chromosome painter, segment data. Most complete but smallest database.
- **MyHeritage**: Chromosome browser, some clustering, triangulation tools
- **23andMe**: Chromosome browser, chromosome painter. Company in financial distress (2025), future uncertain.
- **GEDmatch**: Segment search, Q-matching, clustering kits. Third-party tool requiring data upload.
- **DNA Painter**: Manual chromosome mapping -- powerful but labor-intensive

### Critical Gaps
1. **No unified cross-platform analysis**: Cannot compare matches across Ancestry + FTDNA + MyHeritage + 23andMe in a single view
2. **Ancestry refuses triangulation**: The largest database in the world won't let you triangulate segments -- you must transfer to GEDmatch or FTDNA
3. **No automated segment-to-ancestor mapping**: Manual chromosome mapping via DNA Painter works but requires enormous manual effort
4. **No AI-powered match classification**: No tool automatically categorizes matches by likely relationship path (maternal/paternal, which ancestral line)
5. **No endogamy handling**: For populations with significant endogamy (Jewish, Acadian, Pacific Islander), no tool properly accounts for inflated segment sharing
6. **No shared match network visualization**: No tool shows the full network graph of how all your matches connect to each other
7. **Desktop software DNA integration is minimal**: "Most programs don't analyze DNA directly, but they can record the info you get from test sites" -- desktop genealogy software treats DNA as an afterthought
8. **No phasing automation**: Phasing (determining which segments came from which parent) requires manual work or parent testing, with no AI assistance
9. **No DNA-to-tree hypothesis generation**: No tool says "Based on your shared DNA with these 5 matches who all descend from John Smith, there's a 78% probability you also descend from John Smith through his daughter Mary"

---

## 9. Top 10 Feature Requests (Synthesized from Community Sources, 2025-2026)

1. **Cross-platform sync that actually works** -- One-click sync between desktop software and Ancestry/FamilySearch/MyHeritage without data loss, duplicates, or citation destruction

2. **AI-powered handwriting recognition integrated into search** -- Not just OCR, but semantic understanding of historical documents with context-aware extraction (FamilySearch's Full-Text Search is the closest, but still limited)

3. **Real cross-database search** -- Federated search across all major record repositories from a single interface

4. **Chromosome browser and triangulation on Ancestry** -- The #1 DNA feature request for the platform with the largest database

5. **Historical place name intelligence** -- Software that understands place names and boundaries change over time, with automatic historical-to-modern mapping

6. **Automated multi-platform data entry** -- Enter data once, propagate everywhere, with intelligent conflict resolution

7. **Source credibility and research completeness scoring** -- AI-assisted evaluation of how well-supported each conclusion is, with GPS compliance tracking

8. **Affordable pricing without feature hostage-taking** -- Users want a reasonable one-time purchase or fair subscription without paywalling features they've already been using

9. **GEDCOM that doesn't lose data** -- True interoperability between programs, especially for sources, citations, and multimedia

10. **Smart research suggestions** -- AI that analyzes your tree and tells you exactly what to search for next, in what repository, with what search terms -- not generic "hints" but strategic research guidance based on what's missing

---

## Strategic Analysis: Second-Order Effects

### The Vendor Lock-In Trap
Ancestry's strategy of paywalling previously-free features and making GEDCOM export lossy creates a vicious cycle: users feel trapped, but their data is too valuable to abandon. This creates enormous latent demand for any platform that credibly promises data portability and ownership.

### The AI Trust Deficit
ThruLines' unreliability has created skepticism about AI in genealogy. Any new AI features must lead with transparency (showing confidence scores, explaining reasoning) rather than black-box suggestions. The genealogy community values evidence-based methodology and will reject AI that can't show its work.

### The Collaboration Paradox
FamilySearch is the only truly collaborative tree, but its "anyone can edit" model creates data quality problems. There's an unsolved design problem: how to enable collaboration while maintaining data quality. The answer likely involves source-weighted conflict resolution and researcher reputation scoring.

### The Desktop-Cloud Divide
Power users want desktop control and offline access but also want cloud sync and collaboration. No product successfully bridges this divide. The winning approach is likely local-first with selective cloud sync -- similar to how modern note-taking apps (Obsidian, Logseq) handle this.

### Three Moves Ahead
The platform that wins genealogy's next decade will:
1. Own the data quality layer (validation, deduplication, consistency checking)
2. Build AI that shows its work (confidence scores, evidence chains, GPS compliance)
3. Solve interoperability (become the "universal translator" between platforms)
4. Make DNA genealogy accessible (automated segment mapping, AI relationship classification)
5. Enable collaboration without chaos (structured conflict resolution, research coordination)

---

## Sources

- [RootsMagic Community - Frustration Comparing Changes](https://community.rootsmagic.com/t/frustration-comparing-changes-with-rootsmagic-ancestry/10386)
- [Genealogy Tools - Source/Citation Data Loss in GEDCOM](https://genealogytools.com/your-source-and-citation-information-is-in-danger/)
- [FamilySearch - AI Developments in Genealogy](https://www.familysearch.org/en/blog/ai-developments-genealogy)
- [DNA Explained - ThruLines Are a Hot Mess](https://dna-explained.com/2023/09/03/ancestrys-thrulines-are-a-hot-mess-right-now-but-here-are-some-great-alternatives/)
- [DNA Explained - ThruLines Paywall](https://dna-explained.com/2024/02/05/ancestrys-thrulines-and-shared-matches-now-require-a-subscription/)
- [DNA Explained - 2025 Genetic Genealogy Retrospective](https://dna-explained.com/2025/12/31/2025-genetic-genealogy-retrospective-wow-what-a-year/)
- [DNA Explained - Ancestry Updates Ethnicity](https://dna-explained.com/2024/10/22/ancestry-updates-ethnicity-renames-features-and-rearranges-the-room/)
- [Ancestry - Handwriting Recognition AI for 1950 Census](https://www.ancestry.com/corporate/blog/ancestry-apply-handwriting-recognition-artificial-intelligence-create-searchable-index-1950-us)
- [Family History Foundation - AI and Genealogy Pros/Cons](https://familyhistoryfoundation.com/2025/10/31/ai-and-genealogy/)
- [South Central APG - AI and Handwritten Records](https://southcentralapg.org/2025/08/16/unlocking-family-histories-how-ai-is-breathing-new-life-into-handwritten-records/)
- [Genealogy Star - Handwriting Recognition](https://genealogysstar.blogspot.com/2025/11/handwriting-recognition-ancestrycom-and.html)
- [ConsumerAffairs - Ancestry Reviews](https://www.consumeraffairs.com/online/ancestry.html)
- [BBB - Ancestry Complaints](https://www.bbb.org/us/ut/lehi/profile/genealogy/ancestrycom-1166-2003190/complaints)
- [BBB - MyHeritage Complaints](https://www.bbb.org/us/ut/lehi/profile/genealogy/myheritage-usa-inc-1166-22311143/complaints)
- [ComplaintsBoard - MyHeritage Reviews](https://www.complaintsboard.com/myheritage-b123867)
- [Behold Blog - Can GEDCOM 7.0 Succeed?](https://www.beholdgenealogy.com/blog/?p=3826)
- [Behold Blog - Standardizing Sources and Citations](https://www.beholdgenealogy.com/blog/?p=1395)
- [FHISO - BetterGEDCOM Sources and Citations](https://archive.fhiso.org/BetterGEDCOM/Sources%20and%20Citations.html)
- [Tamura Jones - Online Genealogy Consistency Checks](https://www.tamurajones.net/OnlineGenealogyConsistencyChecks.xhtml)
- [Evidentia Software - Future of Genealogy Software](https://evidentiasoftware.com/future-of-genealogy-software/)
- [DNA Painter Blog - Genealogy and DNA Review of 2025](https://blog.dnapainter.com/blog/genealogy-and-dna-a-review-of-2025/)
- [Segment-ology - AncestryDNA Side vs ThruLines Side](https://segmentology.org/2024/02/13/ancestrydna-side-vs-thrulines-side/)
- [Gramps Project - Gramps 6.0.0 Released](https://gramps-project.org/blog/2025/03/gramps-6-0-0-released/)
- [SourceForge - Gramps Reviews](https://sourceforge.net/projects/gramps/reviews/)
- [Genea-Musings - Standardizing Place Names](https://www.geneamusings.com/2016/01/standardized-historical-and-current_28.html)
- [Family History Fanatics - Place Name Cleanup](https://www.familyhistoryfanatics.com/genealogy_software_cleanup)
- [Martin Roe - Best Genealogy Software 2025](https://martinroe.com/blog/best-genealogy-software-in-2025-a-practical-comparison/)
- [Yenra - AI Genealogical Research Automation 2025](https://yenra.com/ai20/genealogical-research-automation/)
- [AI Genealogy Insights - Crafting Better Research Prompts](https://aigenealogyinsights.com/2025/11/03/crafting-better-research-prompts-a-complete-walk-through/)
- [Denyse Allen - AI Tool Comparison for Genealogy 2025](https://denyseallen.substack.com/p/ai-tool-comparison-genealogy-2025)
