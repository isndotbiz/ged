# DNA Analysis Tools & Genetic Genealogy Techniques Research

**Date:** 2026-03-19
**Researcher:** Ava Sterling
**Purpose:** Evaluate algorithms and techniques for local-first genealogy app integration

---

## 1. GEDmatch

### Overview
GEDmatch is a free third-party DNA analysis platform (now owned by Verogen Inc., acquired 2020). Genesis was introduced in 2017 to handle newer chip formats and lower SNP counts, then merged into the main GEDmatch platform.

### Key Features
- **One-to-Many**: Searches database for matching DNA segments across all uploaded kits
- **One-to-One**: Detailed comparison between two specific kits showing chromosome-by-chromosome segment matches
- **Chromosome Browser**: Most flexible implementation -- compares any kit to any kit, shows both FIR (fully identical regions) and HIR (half-identical regions)
- **Admixture Calculators**: Multiple ethnicity estimation models (Eurogenes, Dodecad, HarappaWorld, etc.)
- **AutoCluster**: Groups matches into clusters by shared matching patterns (endogamy-aware version available)
- **Triangulation**: Built-in tools for segment triangulation across multiple matches
- **Phasing**: Are You Related tool, Lazarus tool for reconstructing deceased relative's DNA

### API Access
- **No official public API.** GEDmatch is web-interface only.
- Community tool `gedmatch-tools` (Python) exists but uses Selenium web scraping, not a REST API
- For a local-first app: replicate the algorithms locally rather than depending on GEDmatch access

### Strategic Insight
GEDmatch's value is its database (1.5M+ kits), not its algorithms. The algorithms (one-to-one comparison, admixture, triangulation) are all implementable locally. The database network effect cannot be replicated, but the analytical tools can.

---

## 2. DNA Painter

### Overview
Award-winning web tool by Jonny Perl for chromosome mapping and relationship probability analysis.

### Core Features
- **Chromosome Mapping ("Painting")**: Users paste segment data (chromosome, start, end, cM, SNPs) to visually map which ancestors contributed which DNA segments
- **Shared cM Project Tool**: Enter total shared cM, get probability distribution across all possible relationships (uses Blaine Bettinger's crowd-sourced data, ~60,000 known relationships)
- **WATO (What Are the Odds?)**: Probability calculator for unknown parentage cases (see #3)
- **Tree Completeness**: Visualization of pedigree completeness per generation with X-DNA, Y-DNA, mtDNA inheritance overlays
- **Trait Mapping**: Overlay known trait loci onto painted chromosomes

### How Chromosome Painting Works
1. User identifies a match with known relationship
2. Exports segment data from testing company (chromosome, start position, end position, cM, SNP count)
3. Pastes into DNA Painter, assigns to known ancestor
4. Repeat for multiple matches -- overlapping segments confirm ancestral assignments
5. Visual map shows which grandparent/ancestor contributed each chromosome region

### Replicability for Local App
- **Highly replicable.** DNA Painter works with segment data, not raw DNA. The visualization is SVG/canvas-based chromosome rendering.
- Core data model: segments (chr, start, end, cM) mapped to ancestor assignments
- Relationship probability lookup table is the Shared cM Project data (publicly available)
- Could build a local implementation using any charting library

---

## 3. What Are The Odds? (WATO)

### Overview
Developed by Leah Larkin (The DNA Geek) and implemented by Jonny Perl on DNA Painter. Used primarily for unknown parentage / adoption cases.

### Algorithm
1. **Input**: A family tree of known DNA matches, with shared cM amounts for each match
2. **Hypothesis placement**: User places the unknown person at various positions in the tree
3. **Probability computation**: For each hypothesis:
   - Determine the genealogical relationship between the unknown person and each known match
   - Look up the probability that the observed shared cM amount corresponds to that relationship (using AncestryDNA simulation data / Shared cM Project)
   - Multiply probabilities across all matches to get compound probability
4. **Odds ratio**: Compare all hypotheses by their compound probabilities, normalize to odds ratios
5. **Scoring**: Higher score = more likely position in tree. Score of 0 = impossible.

### Technical Foundation
- Uses probability distributions from AncestryDNA's white paper on relationship prediction
- Underlying probability-to-odds conversion developed by Dr. Andrew Millard
- Essentially a Bayesian inference problem: P(position | observed cM values across matches)

### Limitations
- Does NOT work with endogamous populations (multiple relationship paths inflate cM)
- Does NOT handle double cousins or 3/4 siblings
- Requires multiple matches from the same family tree to be effective

### Local Implementation
- **Fully implementable locally.** Core algorithm is probability lookup + multiplication + normalization
- Need: shared cM probability distributions per relationship type (publicly available)
- Need: tree structure with relationship path calculator
- Computational complexity is low -- this is lookup tables and arithmetic, not heavy computation

---

## 4. Leeds Method

### Overview
Developed by Dana Leeds in 2018. A technique for sorting DNA matches into grandparent-line clusters using shared match patterns.

### Algorithm
1. Start with matches in the 2nd-4th cousin range (roughly 90-400 cM)
2. Pick the first uncolored match
3. Check shared matches -- all people who match BOTH you and this match
4. Assign a color to this match and all shared matches
5. Pick next uncolored match, repeat with a new color
6. Result: 4 color groups corresponding to 4 grandparent lines

### Why It Works
- 2nd-4th cousins typically connect through only one grandparent line
- People who share matches with each other likely share the same ancestral line
- The method exploits transitivity: if A matches B and B matches C on the same segments, A-B-C likely share a common ancestor

### Automation: AutoCluster
The Leeds Method inspired automated implementations:
- **Genetic Affairs AutoCluster**: First automated implementation (2018)
- **GEDmatch AutoCluster**: Built-in tool with endogamy mode
- **MyHeritage AutoClusters**: Integrated into the platform
- **AncestryDNA color dot system**: Inspired by Leeds

### AutoCluster Algorithm
1. Build an N x N shared-match matrix (rows = matches, columns = same matches)
2. Cell (i,j) = 1 if match_i and match_j appear in each other's shared match lists
3. Apply hierarchical clustering or community detection to identify groups
4. Visualize as a colored matrix where clusters appear as blocks along the diagonal

### Local Implementation
- **Highly automatable.** The algorithm is essentially graph clustering.
- Input: list of matches with their shared match lists (or pairwise "in common with" data)
- Algorithm: build adjacency matrix, apply spectral clustering / Louvain community detection / hierarchical clustering
- Libraries: scikit-learn, networkx, or igraph all provide suitable clustering algorithms
- Output: cluster assignments mapping to ancestral lines

---

## 5. Shared cM Calculation

### The Shared cM Project (Blaine Bettinger)
The gold standard dataset, crowd-sourced since 2015 with ~60,000 known relationships (version 4.0, March 2020).

### Key Relationship Ranges (cM)
| Relationship | Average cM | Range |
|---|---|---|
| Parent/Child | 3,400 | 3,330-3,720 |
| Full Sibling | 2,613 | 1,613-3,488 |
| Half Sibling | 1,759 | 1,160-2,346 |
| Grandparent/Grandchild | 1,766 | 1,156-2,311 |
| Aunt/Uncle | 1,741 | 1,349-2,175 |
| First Cousin | ~866 | 553-1,225 |
| Half First Cousin | ~425 | 137-856 |
| Second Cousin | ~229 | 41-592 |
| Third Cousin | ~73 | 0-234 |
| Fourth Cousin | ~35 | 0-139 |

### Relationship Prediction Algorithms

**Basic approach (Shared cM Project tool):**
1. Input: total shared cM
2. For each relationship type, check if the input falls within the known range
3. Calculate probability based on where it falls in the distribution
4. Return ranked list of possible relationships with probabilities

**Enhanced approach (MyHeritage cM Explainer):**
1. Input: total shared cM + ages of both individuals
2. Use age difference distributions to eliminate impossible relationships
3. Example: 1,750 cM shared -- could be half sibling or grandparent. Age difference of 2 years eliminates grandparent.
4. Dramatically improves prediction accuracy

### Local Implementation
- The Shared cM Project data (ranges + averages per relationship) is publicly available
- Age-based filtering is straightforward conditional logic
- Could also incorporate: number of segments, longest segment, X-DNA sharing for additional discrimination
- The probability distributions can be modeled as empirical distributions or fitted to beta distributions

---

## 6. Chromosome Browsers

### Platform Comparison
| Platform | Has Browser | Shows FIR | Shows HIR | Triangulation | Multi-compare |
|---|---|---|---|---|---|
| GEDmatch | Yes | Yes | Yes | Yes | Yes |
| FamilyTreeDNA | Yes | No | Yes | No | Yes (up to 5) |
| MyHeritage | Yes | No | Yes | Yes | Yes (up to 7) |
| 23andMe | Discontinued 2023 | Was Yes | Was Yes | No | Was Yes |
| AncestryDNA | No | - | - | - | - |

### Best Implementation: GEDmatch
- Most flexible: compare any kit to any kit
- Shows both FIR and HIR regions
- Configurable minimum segment size and SNP count thresholds
- Multiple comparison modes (one-to-one, segment search)

### Open Source Implementations

**yulvil/chromosome-browser** (GitHub):
- Visualizes segments from GEDmatch, FTDNA, MyHeritage, 23andMe, or custom CSV
- Filter by cM, include/exclude kits
- Multiple view modes: compact, by kit, ordered by segment
- Good starting point for a local implementation

**eweitz/ideogram.js** (GitHub):
- JavaScript library for chromosome visualization
- Supports histograms, heatmaps, overlays, annotations
- D3.js + SVG rendering
- Embeddable component -- excellent for a web-based local app
- Supports human and many other organisms

### Local Implementation
- Use ideogram.js as the visualization layer
- Data model: segments with (chromosome, start_bp, end_bp, cM, match_id, ancestor_assignment)
- Overlap detection: interval tree or sorted interval merge
- Coloring by ancestor/match/cluster

---

## 7. Raw DNA File Formats

### 23andMe Format
```
# Header comments starting with #
# rsid    chromosome    position    genotype
rs3094315    1    742429    AG
rs12124819   1    766409    AA
rs11240777   1    788822    AG
```
- Tab-delimited, 4 columns
- Genotype: combined alleles (e.g., "AG")
- No-calls: "--"
- ~600,000-700,000 SNPs depending on chip version

### AncestryDNA Format
```
# Header comments starting with #
rsid    chromosome    position    allele1    allele2
rs3094315    1    742429    A    G
rs12124819   1    766409    A    A
rs11240777   1    788822    A    G
```
- Tab-delimited, 5 columns (separate allele columns)
- No-calls: "0 0"
- Chromosome numbering: X=23/25, Y=24, MT=26
- ~700,000 SNPs

### MyHeritage Format
```
# Header with metadata
RSID,CHROMOSOME,POSITION,RESULT
rs3094315,1,742429,AG
```
- CSV format (comma-separated)
- Combined genotype like 23andMe
- Similar SNP count

### Key Differences for Parsing
| Feature | 23andMe | AncestryDNA | MyHeritage |
|---|---|---|---|
| Delimiter | Tab | Tab | Comma |
| Allele columns | 1 (combined) | 2 (separate) | 1 (combined) |
| No-call | -- | 00 | -- |
| X chromosome | X | 23/25 | X |
| MT | MT | 26 | MT |

### Cross-Referencing
- Match on rsID (Reference SNP cluster ID) -- the universal SNP identifier
- Different chips test different SNPs -- intersection may be 300K-500K common SNPs
- Build/assembly differences (GRCh36/hg18, GRCh37/hg19, GRCh38/hg38) require liftover for position matching
- The `snps` Python library handles all of this automatically

### Python Parsing Libraries
**`snps` (apriha/snps)**: Best option for a local app
- Auto-detects source format (23andMe, Ancestry, MyHeritage, FTDNA, LivingDNA, etc.)
- Normalizes to consistent DataFrame (rsid index, chromosome, position, genotype columns)
- Build detection and remapping (36/37/38)
- Merge multiple files with discrepancy detection
- Read/write VCF format
- Python 3.8+, available on PyPI

---

## 8. Segment Triangulation

### Definition
Three or more people sharing an overlapping DNA segment on the same chromosome, confirming a common ancestor.

### Algorithm
1. **Pairwise comparison**: For persons A, B, C -- verify A-B share a segment, B-C share a segment, A-C share a segment
2. **Overlap verification**: All three segments must overlap on the SAME chromosome at the SAME position range
3. **Minimum segment**: Typically require >= 7 cM to avoid false IBS (identical by state) matches
4. **Ancestor assignment**: If all three share a documented common ancestor, the triangulated segment is confirmed as inherited from that ancestor

### Implementation
```
For each chromosome:
  Build interval list of all shared segments between all pairs
  For each pair of segments sharing person A:
    If segment(A,B) overlaps segment(A,C):
      Check if B and C also share a segment in the overlap region
      If yes: triangulated group = {A, B, C} on overlap(seg_AB, seg_AC, seg_BC)
```

### Data Structures
- Interval trees for efficient overlap queries (O(log n + k) per query)
- Adjacency lists for match-pair relationships
- Sorted segment lists per chromosome for sweep-line algorithms

### Tools
- GEDmatch segment search + triangulation tools
- DNAGedcom Client (extracts segment data for offline analysis)
- Genome Mate Pro (desktop tool for segment management)

### Local Implementation
- Core algorithm is computational geometry (interval intersection)
- Python: `intervaltree` library or `pandas` interval operations
- Scale: typical user has 1,000-30,000 matches, each with 1-20 segments per chromosome
- Performance: easily handled locally, no cloud compute needed

---

## 9. Ethnicity Estimate Algorithms

### How They Work (High Level)
1. **Reference panel**: Curate DNA samples from individuals with deep roots in specific regions (e.g., 50+ reference populations)
2. **Ancestry-informative markers (AIMs)**: Identify SNPs that differ in frequency between populations
3. **Model fitting**: For each test individual, estimate the proportion of DNA from each reference population that best explains the observed genotype pattern
4. **Output**: Percentage breakdown by region/population

### ADMIXTURE Algorithm (Open Source, Gold Standard)
- **Model**: Same as STRUCTURE (Bayesian clustering) but much faster
- **Optimization**: Two-stage process:
  1. Fast EM (Expectation-Maximization) steps for initial estimate
  2. Sequential Quadratic Programming (SQP) with quasi-Newton acceleration for refinement
- **Input**: SNP genotype matrix (individuals x SNPs) + number of ancestral populations (K)
- **Output**: Admixture proportions (Q matrix) + allele frequencies per population (P matrix)
- **Cross-validation**: Built-in CV to determine optimal K
- **Supervised mode**: Can use known-ancestry individuals to anchor estimates
- **Source**: https://dalexander.github.io/admixture/ (C++ implementation, open source)

### Other Open Source Tools
| Tool | Language | Notes |
|---|---|---|
| ADMIXTURE | C++ | Gold standard, fastest |
| OpenADMIXTURE | Julia | Modern reimplementation, biobank-scale |
| STRUCTURE | Java/C | Original, very slow |
| fastSTRUCTURE | Python | Variational Bayes approximation |
| Ohana | C++ | Active Set algorithm, GitHub |
| TeraStructure | C++ | Scalable to millions of individuals |

### Reference Population Data
- **1000 Genomes Project**: 2,504 individuals from 26 populations (free, public)
- **HGDP (Human Genome Diversity Project)**: 1,043 individuals from 51 populations
- **gnomAD**: Broader population data for allele frequencies

### Local Implementation
- Use ADMIXTURE binary (command-line tool) or fastSTRUCTURE (Python)
- Reference panel: 1000 Genomes data is freely downloadable
- For a consumer-facing app: pre-compute reference allele frequencies, then classify test individual against them
- Simpler approach: PCA-based projection onto reference populations (scikit-learn)

---

## 10. Genetic Genealogy Python Libraries

### Primary Libraries

**`lineage` (apriha/lineage)** -- Most relevant for genealogy app
- Find shared DNA between individuals
- Compute cM using HapMap/1000 Genomes genetic maps
- Detect IBD1 (half-identical) and IBD2 (fully identical) regions
- Visualize shared segments across chromosomes
- Find discordant SNPs (Mendelian inheritance violations)
- Generate synthetic genotype data for testing
- Python 3.9+, PyPI: `pip install lineage`
- GitHub: https://github.com/apriha/lineage

**`snps` (apriha/snps)** -- File I/O foundation
- Read raw DNA files from all major DTC companies
- Auto-detect format, normalize to DataFrame
- Build detection and remapping
- Merge files with discrepancy tracking
- VCF read/write
- Python 3.8+, PyPI: `pip install snps`
- GitHub: https://github.com/apriha/snps

**`GenoTools`** -- Quality control
- Sample and variant-level QC
- Ancestry estimation
- Population genetics research
- Academic-grade rigor
- GitHub: Oxford Academic publication

### Supporting Libraries

| Library | Purpose |
|---|---|
| `PySnpTools` | Efficient SNP data reading/manipulation (Microsoft Research) |
| `ipcoal` | Genealogy/sequence simulation on species trees |
| `scikit-allel` | General population genetics analysis |
| `plink` (command-line) | Industry standard for genotype analysis, IBD detection |
| `hail` | Scalable genomic data analysis (Broad Institute) |

### Open Source Applications

**OS Genome** (mentatpsi/OSGenome): Open source web app for personal genomic analysis from 23andMe data

**dnamatch-tools**: Python scripts for working with raw DNA files for genetic genealogy

### Recommended Stack for Local App
```
snps          -- File parsing and normalization
lineage       -- Shared DNA computation and visualization
pandas        -- Data manipulation
intervaltree  -- Segment overlap/triangulation
networkx      -- Match clustering (Leeds/AutoCluster)
scikit-learn  -- Relationship prediction, PCA-based ethnicity
matplotlib    -- Visualization
ideogram.js   -- Browser-based chromosome visualization (if web UI)
```

---

## 11. Endogamy Detection

### The Problem
In endogamous populations (Ashkenazi Jewish, Low-German Mennonite, French-Canadian, island populations), individuals are related through multiple ancestral paths. This causes:
- Inflated total shared cM (makes distant cousins look closer than they are)
- Many small shared segments from deep background relatedness
- Match counts 3-10x higher than outbred populations
- Relationship prediction tools give incorrect results
- Leeds Method may produce fewer than 4 clusters (grandparent lines merge)

### Detection Signals
1. **Elevated match count**: 30,000+ matches vs typical 10,000-15,000
2. **Segment size distribution**: Many small segments (< 10 cM) vs fewer large segments
3. **Inflated total cM with many segments**: e.g., 150 cM across 15 segments (endogamous) vs 150 cM across 3 segments (non-endogamous)
4. **Population-specific markers**: Known AIMs for endogamous populations

### Mitigation Techniques
- **Segment size filtering**: Only consider segments > 15-20 cM (or even > 45 cM for high-confidence relationships)
- **Longest segment emphasis**: The longest shared segment is more reliable than total cM in endogamous populations
- **Adjusted relationship prediction**: Use segment count + longest segment + total cM together
- **Timber algorithm** (AncestryDNA): Identifies and filters "pile-up" regions where many people in a population share the same segment (population-level IBD)
- **Phase-aware matching**: If phased data is available, matching on phased haplotypes reduces false matches

### Tools
- GEDmatch AutoCluster Endogamy mode
- AncestryDNA's Timber algorithm (proprietary, but concept is publishable)
- Ancestry's maternal/paternal hypothesis sorting for enhanced shared matches

### Local Implementation
- Build a segment frequency database: for each genomic region, track how many users share segments there
- Flag "pile-up" regions where > X% of matches share segments (likely population-level IBD, not genealogical)
- Implement segment-size-weighted relationship prediction
- For clustering: use weighted edges based on longest shared segment rather than total cM

---

## 12. Visual Phasing

### Overview
Technique developed by Kathy Johnston and Randy Whited for reconstructing grandparent chromosomes from sibling DNA data WITHOUT testing parents or grandparents.

### Requirements
- Minimum 3 full siblings (2-sibling variant exists but is less complete)
- Raw DNA data for all siblings (uploaded to a platform with chromosome browser, typically GEDmatch)
- Pairwise comparison data for all sibling pairs

### Algorithm Steps

**Step 1: Pairwise Comparison**
For 3 siblings (A, B, C), generate 3 pairwise comparisons:
- A vs B
- A vs C
- B vs C

Each comparison produces regions of:
- **FIR (Fully Identical Region)**: Both siblings inherited the same alleles from BOTH parents
- **HIR (Half Identical Region)**: Both siblings inherited the same allele from ONE parent
- **NIR (No Identical Region)**: Siblings inherited different alleles from both parents

**Step 2: Recombination Point Identification**
Wherever the match type changes (e.g., FIR to HIR, or HIR to NIR), a recombination event occurred. Mark these transition points.

**Step 3: Owner Assignment**
Determine which sibling had the recombination event. The "owner" is the sibling who appears in BOTH pairwise comparisons that show a state change at that position.

**Step 4: Segment Assignment**
Using the recombination points and FIR/HIR/NIR states:
- In an FIR region: both siblings have the same paternal AND maternal segments
- In an HIR region: determine which parent's segment they share (requires logic from all 3 comparisons)
- In an NIR region: siblings have different segments from both parents

**Step 5: Grandparent Assignment**
Label segments as M1/M2 (maternal grandparent 1/2) and P1/P2 (paternal grandparent 1/2). Use known matches to assign M1 to a specific maternal grandparent.

### Automation Challenges
- Boundary detection is noisy (SNP-level data has errors, requiring smoothing)
- FIR/HIR/NIR state assignment from raw comparisons requires threshold tuning
- Edge cases at centromeres and telomeres
- Louis Kessler (Behold Genealogy) has explored automation but notes that humans handle the visual pattern recognition better than simple algorithms
- However, with modern ML smoothing techniques, automation is feasible

### Local Implementation Approach
1. Parse pairwise comparison data (segment lists with match types)
2. Merge into a unified chromosome map with state columns for each pair
3. Detect state transitions (sliding window with noise filtering)
4. Apply ownership logic (combinatorial rules on which pair shows the transition)
5. Assign to grandparent labels (M1/M2/P1/P2)
6. Use known matches to anchor labels to actual grandparents
7. Visualize with chromosome painting (ideogram.js or similar)

---

## Strategic Assessment: Local-First DNA App Architecture

### What Can Be Built Locally (No External API Dependencies)
1. Raw DNA file parsing and normalization (snps library)
2. Shared DNA computation between local files (lineage library)
3. Chromosome browser visualization (ideogram.js)
4. Relationship prediction from shared cM (lookup tables)
5. Leeds/AutoCluster match clustering (graph algorithms)
6. WATO probability calculations (arithmetic on lookup tables)
7. Segment triangulation (interval intersection)
8. Visual phasing (pairwise comparison analysis)
9. Ethnicity estimation (ADMIXTURE + reference panels)
10. Endogamy detection and mitigation (segment analysis)
11. DNA Painter-style chromosome mapping (segment-to-ancestor assignment)

### What Requires External Data/Services
1. **Match databases**: Cannot replicate GEDmatch/Ancestry/23andMe databases locally -- these require network effects
2. **Shared match lists**: Require platform access to know who matches whom
3. **Updated reference panels**: For ethnicity estimation, need periodic updates
4. **Shared cM Project updates**: New versions with more data points

### Recommended Implementation Priority
1. **Phase 1**: File parsing (snps) + shared DNA computation (lineage) + chromosome visualization
2. **Phase 2**: Relationship prediction + Leeds clustering + triangulation
3. **Phase 3**: WATO implementation + visual phasing
4. **Phase 4**: Ethnicity estimation + endogamy handling

### Second-Order Effects to Consider
- Privacy: Local-first approach is a major differentiator as DNA data is extremely sensitive
- Data portability: Supporting all major file formats makes the app a universal tool
- Offline capability: All core algorithms work without internet
- Accuracy: Without large match databases, the app serves as an analytical tool for data the user exports from commercial platforms, not a replacement for those platforms

---

## Sources

- [GEDmatch Official](https://www.gedmatch.com/)
- [GEDmatch Applications](https://www.gedmatch.com/applications/)
- [GEDmatch Genesis Review - Nebula](https://nebula.org/blog/gedmatch-genesis-review/)
- [DNA Painter](https://dnapainter.com/)
- [DNA Painter - How to Use](https://dnapainter.com/help/how-to-use)
- [DNA Painter Shared cM Tool v4](https://dnapainter.com/tools/sharedcmv4)
- [WATO Tool](https://dnapainter.com/tools/probability)
- [WATO v2](https://dnapainter.com/tools/wato)
- [WATO Major Update - The DNA Geek](https://thednageek.com/a-major-update-to-what-are-the-odds/)
- [The Leeds Method](https://www.danaleeds.com/the-leeds-method/)
- [Leeds Method Color Clustering](https://www.danaleeds.com/dna-color-clustering-the-leeds-method-for-easily-visualizing-matches/)
- [Understanding Cluster Matrices - Dana Leeds](https://www.danaleeds.com/understanding-cluster-matrices/)
- [Shared cM Project 4.0 Update](https://thegeneticgenealogist.com/2020/03/27/version-4-0-march-2020-update-to-the-shared-cm-project/)
- [cM Explainer - MyHeritage](https://blog.myheritage.com/2023/03/introducing-cm-explainer-to-predict-relationships-between-dna-matches-with-greater-accuracy/)
- [Relationship Prediction Comparison - DNA Geek](https://thednageek.com/relationship-prediction-tools-which-is-best/)
- [Chromosome Browser - ISOGG Wiki](https://isogg.org/wiki/Chromosome_browser)
- [yulvil/chromosome-browser (GitHub)](https://github.com/yulvil/chromosome-browser)
- [eweitz/ideogram.js (GitHub)](https://github.com/eweitz/ideogram)
- [23andMe Raw Data Format](http://justsolve.archiveteam.org/wiki/23andMe)
- [23andMe Raw Data Technical Details](https://eu.customercare.23andme.com/hc/en-us/articles/115002090907-Raw-Genotype-Data-Technical-Details)
- [AncestryDNA vs 23andMe Format Comparison](https://www.beholdgenealogy.com/blog/?p=2700)
- [Triangulation - ISOGG Wiki](https://isogg.org/wiki/Triangulation)
- [Segment Overlap - GEDmatch](https://www.gedmatch.com/blog/understanding-segment-overlap-in-genetic-genealogy/)
- [ADMIXTURE Software](https://dalexander.github.io/admixture/)
- [ADMIXTURE Enhancements Paper](https://link.springer.com/article/10.1186/1471-2105-12-246)
- [Admixture Analyses - ISOGG Wiki](https://isogg.org/wiki/Admixture_analyses)
- [lineage Python Library (GitHub)](https://github.com/apriha/lineage)
- [lineage Documentation](https://lineage.readthedocs.io/en/stable/readme.html)
- [snps Python Library (GitHub)](https://github.com/apriha/snps)
- [snps Documentation](https://snps.readthedocs.io/en/stable/readme.html)
- [Endogamy - DNAeXplained](https://dna-explained.com/2022/08/11/dna-in-search-ofsigns-of-endogamy/)
- [Dealing with Endogamy - Legacy Tree](https://www.legacytree.com/blog/dealing-endogamy-part-exploring-amounts-shared-dna)
- [Visual Phasing - ISOGG Wiki](https://isogg.org/wiki/Visual_phasing)
- [Can Visual Phasing Be Programmed? - Behold Blog](https://www.beholdgenealogy.com/blog/?p=3878)
- [Visual Phasing Example Series](https://thegeneticgenealogist.com/2016/11/21/visual-phasing-an-example-part-1-of-5/)
- [Visual Phasing Basics - Genetic Genealogy Girl](https://geneticgenealogygirl.com/en/the-basics-of-genetic-genealogy/visual-phasing/)
