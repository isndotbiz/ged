# AI & Agentic Workflow Capabilities for Genealogy Research

**Research Date:** 2026-03-25
**Researcher:** Ava Sterling (PAI ClaudeResearcher)

---

## Table of Contents

1. [Existing AI Features in Major Genealogy Platforms](#1-existing-ai-features-in-major-genealogy-platforms)
2. [Autonomous Genealogy Research Agents](#2-autonomous-genealogy-research-agents)
3. [OCR & Document Analysis](#3-ocr--document-analysis)
4. [DNA Analysis Automation](#4-dna-analysis-automation)
5. [OSINT for Genealogy](#5-osint-for-genealogy)
6. [Face Recognition in Historical Photos](#6-face-recognition-in-historical-photos)
7. [Strategic Assessment & Second-Order Effects](#7-strategic-assessment--second-order-effects)

---

## 1. Existing AI Features in Major Genealogy Platforms

### Ancestry.com

| Feature | Description | Status |
|---------|-------------|--------|
| **AI Stories** | Generates narrated audio stories from lineage data. Reads census/record documents and writes narrative paragraphs explaining what a record meant for an ancestor's life. Video component expected H1 2026. | Live (Dec 2025) |
| **NLP Record Matching** | Cross-references records across languages and eras. NLP indexes birth certificates, marriage licenses, newspapers at scale — linking immigrant manifests to modern DNA matches. | Live |
| **Photo Recognition** | After acquiring iMemories, scans all public photos for "possible matches" using facial recognition. | Rolling out |
| **Micro-Region Ethnicity** | Moving beyond "Southern Italy" to specific villages/valleys with AI-driven migration route "Journey" maps. | In development |
| **API Access** | **No public developer API.** Undocumented REST API exists for DNA features. Family Tree Maker has a private API. Third-party tools exist (e.g., `ancestry-tools` on GitHub for GEDCOM/media export, `ancestryDnaWrapper` Python wrapper for DNA API). | Private/Undocumented |

### MyHeritage

| Feature | Description | Status |
|---------|-------------|--------|
| **AI Record Finder** | Chat-based natural language search engine for historical records. First of its kind (launched Dec 2023). Ask questions like "Find immigration records for my great-grandfather from Poland." | Live |
| **AI Biographer** | Generates "Wikipedia-like" biographical articles from family tree data and historical records. | Live |
| **Deep Nostalgia** | Animates faces in still photos using deep learning video reenactment. Works on B&W, color, and colorized photos. | Live |
| **LiveMemory** | Next-gen Deep Nostalgia — animates entire scenes, not just faces. | Live |
| **In Color** | Automatic photo colorization and color restoration. | Live |
| **AI Time Machine** | Creates themed images of a person in different historical eras. | Live |
| **Photo Repair** | AI-powered scratch removal, resolution enhancement, face sharpening. | Live |
| **Smart Matches** | Automated matching of tree profiles across MyHeritage users. | Live |
| **Record Matches** | Automated matching of historical records to tree profiles. | Live |
| **DNA AutoClusters** | Visually organizes DNA matches into color-coded groups revealing family connections. | Live |

### FamilySearch (Free, Non-profit)

| Feature | Description | Status |
|---------|-------------|--------|
| **Full-Text Search** | AI handwriting recognition converts historical document images into searchable text BEFORE volunteer indexing. Expanding to additional languages in 2026. | Live (expanding) |
| **Computer-Assisted Indexing** | AI performs initial read-through of records, identifies names/dates/places. Volunteers verify. Massively accelerates indexing. | Live |
| **AI Research Assistant** | Appears on homepage showing source records the AI found as close matches for people in your family line. | Live (late 2025) |
| **Suggested Parents/Spouses** | AI suggests potential parents and spouses at the ends of family lines. | Live (late 2025) |
| **Compare-a-Face** | Facial recognition comparing your face to ancestor photos. | Live |
| **Scale** | 22.7 billion searchable names, 1.8 billion people in Family Tree. | As of end-2025 |
| **Developer API** | **Free, publicly documented REST API.** Endpoints: Genealogies, Pedigree, Names, Search and Match, Places. SDKs for Java, C#, PHP, JavaScript, C, Ruby, Objective-C. OAuth 2.0 auth. Free dev/testing environment. Production access after compatibility review. | Live |

---

## 2. Autonomous Genealogy Research Agents

### Architecture for an Autonomous Research Agent

An agent that takes a person (name, dates, places) and searches all available databases would look like:

```
INPUT: Person {name, birth_date, birth_place, death_date, death_place, parents, spouse, ethnicity_hints}
  |
  v
QUERY DECOMPOSITION
  |-- Name variants (transliterations, spellings, maiden names)
  |-- Date ranges (+/- 5 years for fuzzy matching)
  |-- Place hierarchies (village -> county -> state -> country, historical names)
  |
  v
PARALLEL DATABASE SEARCH (rate-limited per source)
  |-- Tier 1: API-accessible (programmatic)
  |-- Tier 2: Structured web (searchable forms)
  |-- Tier 3: Manual/restricted access
  |
  v
RESULT SCORING & DEDUPLICATION
  |-- Fuzzy name matching (Levenshtein, Soundex, Daitch-Mokotoff)
  |-- Date proximity scoring
  |-- Place correlation
  |-- Cross-source confirmation
  |
  v
EVIDENCE CLUSTERING
  |-- Group results by likely identity
  |-- Flag contradictions
  |-- Generate confidence scores
  |
  v
OUTPUT: ResearchReport {matches[], evidence_clusters[], suggested_next_searches[]}
```

### Database Access Tiers

#### Tier 1: APIs Available (Programmatic Access)

| Database | API Type | Rate Limits | Notes |
|----------|----------|-------------|-------|
| **FamilySearch** | REST API (free) | Reasonable (undisclosed specific limits) | Best genealogy API. Pedigree, search, match, places. OAuth 2.0. SDKs in 7 languages. Free dev environment. |
| **NARA (National Archives)** | REST API | 10,000 queries/month per key (can request increase via Catalog_API@nara.gov) | Catalog records, digitized documents. Good for immigration, military, census metadata. |
| **Chronicling America** | REST API (loc.gov) | No key required, no published limits | Historical newspapers (1777-1963). Full OCR text search. Bulk download available. Migrated to loc.gov API in 2025. |
| **Geni.com** | REST API | Undisclosed | Tree data, profiles, relationships. Owned by MyHeritage. |
| **Europeana** | REST API | Free with key | Aggregates European cultural heritage objects. Images, documents, metadata. |
| **OpenArchives.nl** | OAI-PMH API | Standard OAI limits | Dutch genealogical records. |

#### Tier 2: Structured Web (Searchable Forms, No Official API)

| Database | Access Method | Notes |
|----------|---------------|-------|
| **Ancestry.com** | Web scraping only (ToS prohibits) | No public API. Private DNA API exists (undocumented). |
| **MyHeritage** | Web interface only | Smart Matches, Record Matches via UI. No public API for records. |
| **Ellis Island / Statue of Liberty** | Web forms via JewishGen proxy | JewishGen submits to Ellis Island search engine. Can time out on large result sets. |
| **JewishGen Databases** | Web forms | Unified search across: Holocaust, Yizkor Books, Community Databases, Burial Registry, ShtetlSeeker, etc. No public API. |
| **Geneteka** | Web interface | ~25 million Polish vital records indexed by surname. No API. |
| **Szukajwarchiwach.gov.pl** | Web interface | 55+ million scans from Polish state archives. No API. |
| **Newspapers.com** | Web interface (subscription) | Owned by Ancestry. No API. Massive newspaper archive. |
| **FindAGrave** | Web scraping | Owned by Ancestry. Burial/memorial records. |
| **BillionGraves** | Web interface | GPS-tagged headstone photos. |

#### Tier 3: Manual/Restricted Access

| Database | Access Method | Notes |
|----------|---------------|-------|
| **Yad Vashem** | Web interface, physical archives | 210M+ pages, 4.5M victim names. No public API. Internal IDEA API. Partners with JewishGen. |
| **USHMM (US Holocaust Memorial Museum)** | Web + physical | Holocaust Survivors and Victims Database. Web search only. |
| **Polish State Archives (physical)** | In-person or email requests | Some digitized on Szukajwarchiwach. Many records still undigitized. |
| **Austrian State Archives** | Web + in-person | Matricula Online has some parish registers. |
| **German Federal Archives** | Web + in-person | Arolsen Archives (formerly ITS) has Holocaust-era documents with online search. |
| **UK Census (1841-1921)** | Via FindMyPast or Ancestry | Subscription services. No free API. |
| **Canadian Census** | Via Library and Archives Canada | Some free online, some via Ancestry. |

### Existing Open Source Agent Projects

**autoresearch-genealogy** (github.com/mattprusak/autoresearch-genealogy)
- Built for Claude Code
- 12 structured autoresearch prompts with Goal/Metric/Direction/Verify/Guard/Iterations/Protocol
- Obsidian vault starter kit
- Archive-specific guides (including Russia/Ukraine archives)
- Real-world example: produced 105 files spanning 9 generations across 6 family lines
- Methodology extracted from actual research effort

**TreePilot** (github.com/smestern/treepilot)
- Open-source autonomous genealogy research agent
- FastAPI backend with GitHub Copilot SDK
- React 18 + TypeScript frontend with D3.js tree visualization
- Parses GEDCOM via python-gedcom library
- Searches: Wikipedia, Wikidata, Chronicling America, Google Books
- Enriches tree data through intelligent multi-source queries

**GEDCOM-to-WhatsApp Bot** (Medium, Piotr Brudny, Feb 2026)
- Imports GEDCOM into a graph database (Neo4j)
- WhatsApp interface for querying family tree
- AI assistant for relationship queries

### Building Your Own Agent: Key Technical Decisions

1. **Name Variant Generation**: Use Daitch-Mokotoff Soundex (designed for Slavic/Germanic/Jewish names) over standard Soundex. Libraries: `fuzzy` (Python), or implement D-M algorithm directly.

2. **Place Name Resolution**: Historical place names change constantly (Galicia -> Poland/Ukraine, Kalush/Kalusz). Maintain a mapping table: `{historical_name, modern_name, lat, lon, date_range}`. GeoNames API is free and useful.

3. **Rate Limiting Strategy**: Implement per-source rate limiters with exponential backoff. Respect robots.txt. For Tier 2 sources, consider running searches overnight in batch mode.

4. **Result Deduplication**: Use your existing GedFix fuzzy matching (rapidfuzz) with Daitch-Mokotoff for name comparison, date proximity scoring, and place correlation.

---

## 3. OCR & Document Analysis

### Primary Platform: Transkribus

**What it is:** The leading AI platform for historical document transcription, developed by the READ project at University of Innsbruck.

| Capability | Details |
|------------|---------|
| **Languages** | 100+ languages, 300+ public AI models |
| **Historical Scripts** | Kurrent, Sutterlin, Fraktur (1500s-1940s), Hebrew, Arabic, Cyrillic, Greek |
| **Accuracy** | 5-10% Character Error Rate (90-95% accuracy) on typical historical documents |
| **API** | Full REST API for integration. Programmatic access to all models, layout analysis, batch processing with structured JSON output. |
| **Pricing** | 100 free credits/month. 1 credit/page (handwritten), 0.5 credits/page (printed). 5MB upload limit per image. |
| **Genealogy Models** | 300+ public models, many trained specifically on parish registers, church records by genealogists/archivists. Understand abbreviations, column layouts, script variations. |
| **Custom Training** | Train your own HTR models on specific document types. |

### Script-Specific Tools

| Script | Tool | Notes |
|--------|------|-------|
| **German Kurrentschrift** | Transkribus (dedicated Kurrent models) | Best option. Handles Kurrent, Sutterlin, Fraktur natively. |
| **Hebrew** | Transkribus, Calfa OCR, HebrewManuscriptsMNIST (HuggingFace) | Calfa specializes in manuscripts from 9th century onward. Transfer learning from medieval Hebrew texts improves performance. |
| **Yiddish** | Jochre 3 (open source) | OCR tool suite covering 12,000+ historical books. Universal Yiddish Library project. |
| **Polish** | Transkribus (with appropriate models) | Polish vital records often in Latin/Polish/Russian depending on partition era. |
| **Multi-language** | Google Cloud Vision API, Azure AI Document Intelligence | Good for printed text, less reliable for historical handwriting. |

### Document Classification Pipeline

For automating document analysis:

```
1. IMAGE PREPROCESSING
   - Deskewing, noise removal, binarization
   - Tools: OpenCV, ScanTailor, unpaper

2. LAYOUT ANALYSIS
   - Detect columns, tables, margins, text regions
   - Tools: Transkribus (built-in), Kraken, dhSegment

3. SCRIPT DETECTION
   - Classify script type (Latin, Hebrew, Cyrillic, Kurrent, etc.)
   - Tools: Custom classifier or Transkribus auto-detection

4. TEXT RECOGNITION (HTR/OCR)
   - Apply appropriate model per script/language
   - Tools: Transkribus API, Tesseract (printed), Kraken (open-source HTR)

5. NAMED ENTITY RECOGNITION
   - Extract: names, dates, places, relationships, occupations
   - Tools: spaCy (with custom NER models), Stanza, or fine-tuned transformers

6. STRUCTURED DATA EXTRACTION
   - Map extracted entities to genealogical fields
   - Output: JSON/GEDCOM-compatible records
```

### Key Research: Multi-Language Historical OCR

A 2025 paper (arXiv:2508.10356) addresses improving OCR for historical texts of multiple languages using deep learning, with specific attention to data scarcity and script variation challenges in manuscripts.

---

## 4. DNA Analysis Automation

### Available Tools & Platforms

| Tool | What It Does | Access |
|------|--------------|--------|
| **GEDmatch** | Upload raw DNA data, compare across platforms. One-to-many matching, admixture analysis. | Web interface. Unofficial Python API (`gedmatch-tools`) exists but unmaintained since 2020. Owned by Verogen. Basic tools remain free. |
| **DNAGedcom (GWorks)** | Compares all trees of DNA matches at Ancestry, finds common ancestors, builds database. AutoTree reconstructs trees from FTDNA matches. | Web + desktop client. |
| **DNA Painter** | Chromosome mapping tool. Paint segments to ancestors. WATO (What Are The Odds?) calculates probability of relationship hypotheses. | Web app. Manual input. |
| **Genetic Affairs** | AutoClusters — groups matches by who they also match. Visual cluster charts. | Web service. Works with Ancestry, 23andMe, FTDNA, MyHeritage data. |
| **Collins Leeds Method 3D** | Automated Leeds Method clustering. Creates colored cluster charts. | Works with data from Ancestry, 23andMe, FTDNA, MyHeritage, GEDmatch. |
| **MyHeritage AutoClusters** | Built-in DNA match clustering. Color-coded grouping. | Built into MyHeritage platform. |
| **DNA Genics** | 500+ analysis reports from raw DNA data. Deep ancestry analysis. | Web service. |

### What AI Can Automate

1. **Match Clustering**: Auto-group DNA matches into family clusters (Leeds Method, AutoClusters). Identify which grandparent line each cluster belongs to.

2. **Shared Match Analysis**: For each significant match, pull their shared matches, build network graph, identify triangulation groups.

3. **Common Ancestor Prediction**: Given a set of matches sharing a segment, predict most likely common ancestor using tree data + cM values + relationship probabilities.

4. **Segment Triangulation**: Map shared DNA segments to specific ancestors. Paint chromosomes automatically.

5. **Endogamy Detection**: Critical for Jewish genealogy. Identify when high cM sharing is due to multiple relationship paths rather than a single close relationship.

### AI-Assisted DNA Analysis Workflow

```
1. EXPORT DNA DATA
   - Download match lists, shared matches, segment data
   - Sources: AncestryDNA, 23andMe, FTDNA, MyHeritage

2. UPLOAD TO ANALYSIS PLATFORMS
   - GEDmatch (cross-platform comparison)
   - DNAGedcom (tree comparison)

3. AUTOMATED CLUSTERING
   - Leeds Method (automated via Collins Leeds 3D)
   - AutoClusters (Genetic Affairs or MyHeritage)

4. AI INTERPRETATION
   - Feed cluster results + tree data to LLM
   - Ask: "Given these clusters and known tree, which ancestors likely connect these match groups?"
   - Use WATO for probability analysis

5. TARGETED RESEARCH
   - Agent searches records for predicted common ancestors
   - Feeds back into tree, re-runs clustering
```

### Important Caution

AI tools can misinterpret centimorgan values, oversimplify segment analysis, and occasionally hallucinate relationships. Always verify AI-generated DNA interpretations against established genetic genealogy reference tables (ISOGG wiki, Shared cM Project by Blaine Bettinger).

---

## 5. OSINT for Genealogy

### Image-Based Research

| Tool | Use Case | Notes |
|------|----------|-------|
| **Google Reverse Image Search** | Find copies of ancestor photos posted elsewhere. Identify people in unlabeled photos. | Free. Upload photo or paste URL. |
| **TinEye** | Track where an image appears online. Find higher-resolution versions. | Free tier available. Good for finding original sources. |
| **Yandex Images** | Reverse image search with strong face matching. Often finds results Google misses. | Free. Particularly good for Eastern European content. |
| **PimEyes** | Face search engine. Find where a face appears across the internet. | Paid. Privacy concerns — use ethically. |

### Public Records & People Finding

| Resource | Type | Notes |
|----------|------|-------|
| **Dead Fred** | Photo archive | 100,000+ family history photos. Free, searchable by name. |
| **The-Osint-Toolbox/Ancestry-Genealogy-OSINT** | GitHub toolkit | Curated OSINT tools specifically for genealogy. |
| **FamilyHistoryDaily photo collections** | Aggregator | Lists major online photo collections for genealogy. |
| **Find A Grave** | Cemetery records | Burial records, memorial photos, GPS locations. |
| **Newspapers.com** | Historical newspapers | Obituaries, birth/marriage notices, family mentions. (Subscription) |
| **Chronicling America** | Historical newspapers | Free via LoC. API available. 1777-1963. |

### Cross-Referencing Strategy

```
For a target person:
1. Search name variants across genealogy databases (Tier 1-3 from Section 2)
2. Reverse image search any known photos
3. Search newspaper archives for name mentions (obituaries, social items)
4. Cross-reference address books, city directories, phone books
5. Check immigration/naturalization records for associates
6. Search cemetery databases for burial location + nearby family burials
7. For living relatives: public records, social media (with ethical constraints)
```

### Ethical Considerations

1. **Living Persons**: Do not publish personal information about living individuals without consent. Most genealogy standards require privacy protection for anyone born within the last 100 years.

2. **Photo Uploads**: Before uploading photos to reverse image search engines or AI tools, consider that you may be creating searchable records. PimEyes and similar tools raise significant privacy concerns.

3. **Data Aggregation Risk**: Combining public records across sources can reveal sensitive information (addresses, family relationships) that individuals may not want publicly linked.

4. **Holocaust Records**: Treat with particular sensitivity. Records of victims are memorial in nature. Records of survivors may contain information they chose not to share.

5. **DNA Privacy**: Never upload someone else's DNA data without their explicit consent. Genetic information reveals data about entire family lines, not just individuals.

6. **Legal Compliance**: GDPR (Europe), state privacy laws (US). Some archives restrict redistribution of records. Always check terms of use.

---

## 6. Face Recognition in Historical Photos

### Available Tools

| Tool | How It Works | Best For |
|------|-------------|----------|
| **Related Faces** | Geometric mapping of facial features (eyes, nose, mouth). Converts faces to numerical signatures. Calculates "Resemblance Numbers" for likelihood of same person. | Comparing faces across group photos. Detecting same person at different ages. |
| **Google Photos** | Automatic face clustering. Groups same person across photos. Takes up to 24h for initial clustering. | Large photo collections. Automatic grouping without manual tagging. |
| **Civil War Photo Sleuth** | Compares 27 facial landmarks. Crowdsourced database of identified portraits. Combines AI + human expertise. | Historical portrait identification. Academic-grade methodology. |
| **FamilySearch Compare-a-Face** | Compares your face to ancestor photos in FamilySearch collections. | Fun engagement tool. Not research-grade. |
| **FacePair.com** | Online face comparison tool. Similarity scoring. | Quick ad-hoc comparisons. |
| **Amazon Rekognition** | Cloud API for face detection, comparison, search. | Building custom face-matching pipelines. Pay-per-use. |
| **DeepFace (open source)** | Python library wrapping multiple face recognition models (VGG-Face, Google FaceNet, OpenFace, DeepID, ArcFace, Dlib, SFace). | Custom applications. Free. Best open-source option. |
| **InsightFace (open source)** | State-of-the-art face analysis. Detection, recognition, alignment, age estimation. | Building custom face recognition systems. |

### Technical Approach for Historical Photo Matching

```python
# Using DeepFace (Python) for historical photo comparison
from deepface import DeepFace

# Compare two photos
result = DeepFace.verify(
    img1_path="ancestor_1890.jpg",
    img2_path="ancestor_1920.jpg",
    model_name="ArcFace",        # Best for cross-age matching
    detector_backend="retinaface" # Best face detection
)
# result["verified"] = True/False
# result["distance"] = similarity score

# Search across a collection
results = DeepFace.find(
    img_path="unknown_person.jpg",
    db_path="family_photos/",
    model_name="ArcFace"
)
```

### Challenges with Historical Photos

1. **Image Quality**: Low resolution, fading, damage, sepia toning all reduce accuracy.
2. **Age Variation**: Matching the same person across decades (child to elderly) is significantly harder.
3. **Photographic Style**: Formal poses, hats, beards, period clothing create occlusions.
4. **Limited Training Data**: Most face recognition models are trained on modern photos. Historical photo performance degrades.

### Best Practices

- Use face recognition as a **screening tool**, not a definitive identifier. It narrows possibilities.
- ArcFace model performs best for cross-age and cross-quality comparisons.
- Pre-process historical photos: enhance contrast, upscale resolution (using AI super-resolution like Real-ESRGAN), convert to grayscale for consistency.
- Build a reference database of all identified family photos for comparison.
- The Programming Historian has a peer-reviewed tutorial on facial recognition in historical photographs with Python.

---

## 7. Strategic Assessment & Second-Order Effects

### Three Scenarios for GedFix Integration

**Scenario A: Minimal Integration (3-6 months)**
- Integrate FamilySearch API for automated record matching
- Add Chronicling America newspaper search
- Integrate Transkribus API for document OCR
- Use DeepFace for photo matching within user's collection
- Estimated value: Significant productivity gain for individual researchers

**Scenario B: Agent-Based Research (6-12 months)**
- Build autonomous research agent using architecture from Section 2
- Implement Tier 1 API integrations (FamilySearch, NARA, Chronicling America, Europeana)
- Add structured web search for Tier 2 sources (JewishGen, Geneteka, Szukajwarchiwach)
- DNA import and automated clustering
- Estimated value: 10x research velocity, continuous background research

**Scenario C: Full Platform (12-24 months)**
- All of Scenario B
- Custom HTR models trained on specific document types (Polish vital records, Hebrew manuscripts)
- Face recognition across all family photos with automatic identity suggestions
- DNA network analysis with endogamy-aware common ancestor prediction
- Multi-language NER for automatic GEDCOM record generation from raw documents
- Estimated value: Paradigm shift in genealogy research capability

### Second-Order Effects to Watch

1. **Data Quality Cascade**: As AI indexes more records, the matching quality for less-common names improves, which disproportionately benefits Jewish/Eastern European research where name transliteration is a major obstacle.

2. **Archive Digitization Acceleration**: FamilySearch's computer-assisted indexing will make previously unsearchable archives discoverable. Polish/Ukrainian/Austrian archives are being digitized rapidly.

3. **API Consolidation Risk**: Ancestry's refusal to publish a public API creates a walled garden. If MyHeritage or FamilySearch gain market share through openness, Ancestry may be forced to open up — or double down on lock-in.

4. **Privacy Regulation Impact**: GDPR and emerging US privacy laws may restrict face recognition and OSINT capabilities. Build with privacy-by-design now.

5. **Endogamy Problem**: For Ashkenazi Jewish genealogy, DNA analysis tools that don't account for endogamy will produce misleading results. This is an underserved technical problem with high value for the right solution.

---

## Sources

- [Ancestry AI Stories](https://www.semafor.com/article/12/12/2025/ancestrys-new-ai-feature-narrates-ancestors-stories)
- [MyHeritage AI Record Finder](https://blog.myheritage.com/2023/12/introducing-ai-record-finder-the-worlds-first-ai-chat-based-search-engine-for-historical-records/)
- [FamilySearch 2026 Plans](https://www.familysearch.org/en/blog/what-to-expect-from-familysearch-in-2026)
- [FamilySearch Full-Text Search AI](https://www.genealogyexplained.com/blog/familysearch-full-text-search-ai/)
- [FamilySearch Developer API](https://developers.familysearch.org/)
- [NARA Catalog API](https://www.archives.gov/research/catalog/help/api)
- [Chronicling America API](https://www.loc.gov/apis/additional-apis/chronicling-america-api/)
- [Transkribus Platform](https://www.transkribus.org/)
- [Transkribus for Genealogy](https://www.transkribus.org/genealogy)
- [Transkribus Kurrent Recognition](https://www.transkribus.org/kurrent-transcription)
- [Jochre 3 Yiddish OCR](https://arxiv.org/html/2501.08442v1)
- [Multi-Language Historical OCR](https://arxiv.org/html/2508.10356v1)
- [autoresearch-genealogy](https://github.com/mattprusak/autoresearch-genealogy)
- [TreePilot](https://github.com/smestern/treepilot)
- [GEDCOM-to-WhatsApp Bot](https://medium.com/@pbrudny/building-a-family-tree-ai-assistant-from-gedcom-to-whatsapp-bot-with-a-graph-database-b1fcf0b3cc9e)
- [Civil War Photo Sleuth](https://crowd.cs.vt.edu/wp-content/uploads/2019/11/Mohanty_TiiS_PhotoSleuth_final.pdf)
- [Facial Recognition for Historical Photos (Programming Historian)](https://programminghistorian.org/en/lessons/facial-recognition-ai-python)
- [DeepFace Library](https://github.com/serengil/deepface)
- [GEDmatch Tools (Python)](https://github.com/nh13/gedmatch-tools)
- [DNA Evidence Analysis with AI (Nicole Dyer, 2026)](https://cms-z-assets.familysearch.org/cd/5a/7db5e8534c6ab74f656f52e452cb/syllabus-for-dna-evidence-analysis-with-ai-updated-4-march-2026.pdf)
- [The-Osint-Toolbox/Ancestry-Genealogy-OSINT](https://github.com/The-Osint-Toolbox/Ancestry-Genealogy-OSINT)
- [Leeds Method](https://www.danaleeds.com/the-leeds-method/)
- [WATO (What Are the Odds)](https://dnapainter.com/tools/wato)
- [Genetic Affairs AutoClusters](https://geneticaffairs.com/)
- [Szukajwarchiwach (Polish Archives)](https://www.szukajwarchiwach.gov.pl/)
- [Geneteka Database](https://geneteka.genealodzy.pl/)
- [JewishGen Databases](https://www.jewishgen.org/databases/)
- [Yad Vashem Digital Collections](https://www.yadvashem.org/collections.html)
- [MyHeritage AI & Genealogy Guide](https://education.myheritage.com/article/ai-genealogy-harnessing-the-power-of-artificial-intelligence-for-family-history-research/)
- [FamilySearch AI Help Features](https://www.familysearch.org/en/help/helpcenter/article/familysearch-ai-help-features)
- [FamilySearch MCP Server](https://mcpmarket.com/server/familysearch)
