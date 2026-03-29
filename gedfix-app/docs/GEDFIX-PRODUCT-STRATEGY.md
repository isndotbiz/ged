# GedFix Product Strategy — Executive Summary
### Compiled 2026-03-25 from 6 parallel research agents

---

## THE OPPORTUNITY

**Market:** $4.5-6.6B globally (2024), growing 10-12% CAGR → $8-10B by 2030. DNA testing alone: $2.45B → $21.85B by 2034.

**The gap nobody is filling:** No product focuses AI on GEDCOM data quality, validation, or cleanup. Ancestry/MyHeritage invest AI in record processing and photo effects. The #1 user complaint category across ALL platforms is data quality — and that's exactly where GedFix sits.

**The contrarian thesis:** The market isn't mature — it's trapped. Ancestry has 1.6 stars on SmartCustomer. Users describe the subscription model as "a greedy ripoff." Data portability is actively being reduced. The most-requested features have gone unbuilt for a decade.

---

## COMPETITIVE POSITIONING

### What GedFix Already Has That Others Don't
| Feature | Ancestry | MyHeritage | RootsMagic | FamilySearch | **GedFix** |
|---------|----------|------------|------------|--------------|------------|
| GEDCOM data quality AI | No | No | No | No | **Yes** |
| Contradiction detection | No | No | No | No | **Yes** |
| Multi-provider AI chat | No | No | No | No | **Yes (7 providers)** |
| AI biographical stories | No | MyHeritage only | No | No | **Yes** |
| Local-first / no lock-in | No (cloud) | No (cloud) | Yes | No (cloud) | **Yes** |
| Lightweight (3MB binary) | N/A (web) | N/A (web) | 80MB | N/A (web) | **Yes (Tauri)** |
| Research log (GPS) | No | No | Partial | No | **Yes** |
| DNA cM calculator | No | Built-in | No | No | **Yes** |

### Pricing Sweet Spot
- **RootsMagic:** $39.95 perpetual
- **MacFamilyTree:** $69.99 perpetual
- **Ancestry:** $25-50/month subscription
- **GedFix recommended:** $49.95 perpetual + $7.99/mo AI subscription (or $59.99/year)
- **Launch bundle:** $99.95 = perpetual + 1 year AI

---

## TOP 5 UNMET NEEDS (from user pain point research)

1. **Data portability & ownership** (60-70% of power users) — GEDCOM exports lose 60-70% of research. GedFix = 100% local, 100% exportable.

2. **End the subscription trap** (70-80% pricing frustration) — Perpetual license + optional AI subscription. Never lose access.

3. **Research workflow tools** (100% of serious genealogists) — Contradiction detection, research logging, negative evidence, version history. GedFix already has most of these.

4. **AI that assists without fabricating** (50-60% want AI, 20-30% trust it) — Users want OCR, transcription, photo enhancement. Distrust AI conclusions. GedFix's approach (AI chat with citations, human-in-loop) is exactly right.

5. **Media as first-class objects** (50-60%) — Face clustering, dedup, bulk ops. GedFix's new junction table + face detection pipeline addresses this.

---

## AI & AGENTIC ARCHITECTURE

### Autonomous Research Agent (Roadmap)
**Tier 1 — API-accessible databases (build first):**
- FamilySearch (free API, 22.7B names, SDKs in 7 languages)
- NARA National Archives (10K queries/month free)
- Chronicling America newspapers (no key needed)
- JewishGen (partial API)

**Tier 2 — Structured web forms (build second):**
- Ancestry hints (no public API — scraping required)
- MyHeritage (FamilyGraph API available)
- Geneteka, Szukajwarchiwach (Polish archives)

**Tier 3 — Manual access (agent-assisted):**
- Yad Vashem physical archives
- Polish state archives
- European church records

### Key AI Capabilities to Build
1. **OCR:** Transkribus API (90-95% accuracy on historical handwriting, Kurrentschrift, Hebrew)
2. **Face detection:** @vladmandic/face-api (128-dim embeddings, clustering, 80%+ on B&W studio portraits)
3. **Narrative generation:** Two-tier: Maverick draft ($0.19/batch) + Claude polish ($0.38/batch)
4. **Endogamy-aware DNA:** Major underserved gap for Ashkenazi genealogy — high-value differentiator

---

## TECHNICAL ARCHITECTURE

### Confirmed: Tauri 2 + SvelteKit is Optimal
- 3MB binary vs 346MB Electron
- SQLite WAL mode, 32MB page cache, FTS5 — already implemented
- Only tradeoff: longer Rust compile times (mitigated by incremental builds)

### Three Schema Upgrades Needed
1. **`media_person_link` junction table** — DONE (implemented today)
2. **`name` table for alternate names** — transliterations, married names, Hebrew/Yiddish variants
3. **`event_ref` junction table** — census records and shared events link to multiple people

### Sync Strategy
- **Phase 1:** Turso embedded replicas (drop-in libSQL replacement)
- **Phase 2:** Column-level CRDTs only if multi-device editing creates conflicts
- **Phase 3:** Cloud sync with end-to-end encryption for privacy

### Plugin System
- Start with JavaScript plugins in iframe sandboxes
- Add WASM (Wasmtime) for CPU-intensive plugins later

---

## GO-TO-MARKET

### Launch Checklist
1. **Legal:** Delaware/Wyoming LLC ($39-300 + fees). FinCEN BOI reporting mandatory.
2. **Payment:** Paddle or Lemon Squeezy (5% + $0.50, handles global tax as Merchant of Record)
3. **GDPR:** Deceased exempt, but data about dead that reveals living = covered. Living-person detection needed.
4. **License:** Open Core — open source GEDCOM parsing lib (credibility), proprietary AI + UI.

### Partnerships (Priority Order)
1. **FamilySearch API** — Apply NOW. Free, billions of records. https://developers.familysearch.org/
2. **MyHeritage FamilyGraph API** — Apply NOW. Free, no NDA. https://github.com/myheritage
3. **FindMyPast Hints API** — Available for third-party integration
4. **NARA Catalog API** — Free, public access
5. **Ancestry FamilySync** — Long-term goal (currently restricted to FTM and RootsMagic)

### Marketing Channels
| Channel | Size | Priority |
|---------|------|----------|
| r/Genealogy | 185K members | HIGH |
| RootsTech (virtual) | Millions virtual | HIGH |
| Ancestry YouTube | 582K subs | MEDIUM |
| Genealogy TV YouTube | 100K subs | MEDIUM |
| Genetic Genealogy Tips FB | 54K members | MEDIUM |
| NGS Conference | In-person | HIGH (May 27-30, 2026, Fort Wayne IN) |
| SEO: "jewish genealogy software" | Low competition | HIGH |
| SEO: "GEDCOM cleaner" | Low competition | HIGH |

### Underserved Niches to Target First
1. **Jewish genealogy** — 80 societies worldwide, 20M+ records, zero dedicated desktop software
2. **Eastern European** — Cyrillic/Hebrew/Yiddish handling, Polish/Austrian archive integration
3. **Holocaust research** — Arolsen Archives (17.5M people), USHMM, no automated cross-referencing
4. **DNA-focused researchers** — Endogamy analysis underserved for Ashkenazi population

---

## PRODUCT ROADMAP

### Phase 1: Foundation (Now → 3 months)
- [x] Archival atlas UI redesign
- [x] Multi-provider AI chat with research modes
- [x] AI story generator with batch mode
- [x] Media junction table (many-to-many)
- [ ] Re-import from fresh Ancestry GEDCOM
- [ ] Content-addressable media dedup (SHA-256)
- [ ] Face detection pipeline (@vladmandic/face-api)
- [ ] FamilySearch API integration (search + hints)
- [ ] GEDCOM 7.0 export support

### Phase 2: AI Agents (3-6 months)
- [ ] Autonomous research agent (FamilySearch + NARA + Chronicling America)
- [ ] Transkribus OCR integration
- [ ] Face clustering + person suggestion workflow
- [ ] Endogamy-aware DNA analysis
- [ ] Research log → automatic source citations

### Phase 3: Launch (6-9 months)
- [ ] Turso cloud sync
- [ ] Plugin system (JS sandboxed)
- [ ] Website + marketing site
- [ ] Beta program (target: genealogy societies)
- [ ] Paddle payment integration
- [ ] App Store / direct distribution
- [ ] RootsTech 2027 virtual exhibit

### Phase 4: Growth (9-18 months)
- [ ] MyHeritage + FindMyPast integration
- [ ] Mobile companion app
- [ ] Collaborative trees (CRDT-based)
- [ ] Data marketplace
- [ ] Ancestry FamilySync (when/if approved)

---

## DETAILED REPORTS

| Report | Location |
|--------|----------|
| Competitive Landscape | `MEMORY/WORK/20260325.../COMPETITIVE_LANDSCAPE.md` |
| User Pain Points | `MEMORY/WORK/20260325.../genealogy-software-complaints-research.md` |
| Go-to-Market Strategy | `MEMORY/WORK/20260325.../genealogy-gtm-research-report.md` |
| AI & Agentic Workflows | `docs/genealogy-ai-agentic-research.md` |
| Technical Architecture | `docs/architecture-research.md` |
| Media Schema (implemented) | Junction table in `src/lib/db.ts` |

---

## THE BOTTOM LINE

GedFix is positioned in the exact gap the market needs filled: **AI-powered data quality for genealogy, with full user ownership, at a fair price.** No competitor does this. The technical foundation (Tauri, SQLite, multi-provider AI) is sound. The market is $4.5B+ and growing. Users are actively frustrated with incumbents. The Jewish/Eastern European niche is completely underserved and is your personal domain expertise.

**Next immediate actions:**
1. Re-import data from fresh Ancestry GEDCOM (media links fixed)
2. Apply for FamilySearch developer API key
3. Apply for MyHeritage FamilyGraph API access
4. Register LLC
5. Build FamilySearch search integration
