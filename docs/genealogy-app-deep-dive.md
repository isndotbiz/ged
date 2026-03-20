# Genealogy App Deep Dive: Analysis, Migration, and MVP Roadmap

*Generated 2026-03-19 for Jonathan Mallinger*

---

## Table of Contents

1. [Commercial Software Comparison](#1-commercial-software-comparison)
2. [Recommendation: Interim Source of Truth](#2-recommendation-interim-source-of-truth)
3. [Open Source Project Evaluation](#3-open-source-project-evaluation)
4. [Data Format Comparison](#4-data-format-comparison)
5. [Mac Native Tech Stack Recommendation](#5-mac-native-tech-stack-recommendation)
6. [AI Integration Architecture](#6-ai-integration-architecture)
7. [Playwright Scraping Architecture](#7-playwright-scraping-architecture)
8. [Plugin System Architecture](#8-plugin-system-architecture)
9. [Sync/Merge and Conflict Resolution](#9-syncmerge-and-conflict-resolution)
10. [Privacy, Encryption, and Security](#10-privacy-encryption-and-security)
11. [Testing and CI/CD](#11-testing-and-cicd)
12. [Repository Audit](#12-repository-audit)
13. [Migration Plan](#13-migration-plan)
14. [MVP Roadmap](#14-mvp-roadmap)
15. [License and Contributor Guidelines](#15-license-and-contributor-guidelines)
16. [Learning Resources](#16-learning-resources)
17. [Your Source Files Assessment](#17-your-source-files-assessment)
18. [DNA Data Integration](#18-dna-data-integration)
19. [Next Actions Checklist](#19-next-actions-checklist)

---

## 1. Commercial Software Comparison

### Family Tree Maker 2024

| Aspect | Detail |
|--------|--------|
| **Platform** | Mac native (Catalina+), Windows |
| **Price** | ~$80 one-time |
| **GEDCOM Export** | 5.5.1 — good fidelity but Ancestry-specific extensions (`_TREE`, `_ENV`, `RIN`) may not import elsewhere |
| **Ancestry Sync** | TreeSync with Ancestry.com (the killer feature) — bidirectional sync of people, facts, media |
| **Database** | Proprietary `.ftm` format (SQLite-based internally) |
| **API Access** | None. No scripting, no plugins, no automation |
| **DNA Integration** | View Ancestry DNA matches within the app, link to tree |
| **Media Handling** | Good — stores/links photos, documents, audio |

**Pros:**
- Only desktop app with real-time Ancestry.com sync
- Familiar UI for Ancestry users
- Decent reporting and charting
- Mac native with reasonable performance

**Cons:**
- Closed ecosystem — no plugins, no API, no scripting
- GEDCOM export includes Ancestry-specific tags that other software may ignore
- Owned by Software MacKiev (small company) — uncertain long-term future
- No command-line access for batch operations
- TreeSync can be fragile — conflicts resolved opaquely

### RootsMagic 11

| Aspect | Detail |
|--------|--------|
| **Platform** | Native macOS since v8 (64-bit, High Sierra through current). Also Windows. |
| **Price** | ~$40 one-time (RootsMagic Essentials is free) |
| **GEDCOM Export** | Excellent 5.5.1 fidelity — widely considered the cleanest GEDCOM exporter |
| **Database** | SQLite (`.rmtree` file is a SQLite database you can query directly) |
| **API Access** | No official API, but the SQLite database is fully accessible and documented by the community |
| **FamilySearch Sync** | Direct FamilySearch integration (the other killer feature) |
| **DNA Integration** | No built-in DNA; relies on external tools |

**Pros:**
- SQLite database = you can query, script, and automate directly
- Cleanest GEDCOM export in the industry
- FamilySearch integration
- Affordable, active development
- Huge power-user community with SQL recipes
- WebHints from Ancestry, MyHeritage, FamilySearch, FindAGrave

**Cons:**
- Some macOS features lag the Windows version
- No Ancestry TreeSync (only manual GEDCOM round-trips)
- UI feels dated compared to FTM
- No plugin system (though SQLite access compensates)

### Ancestry.com

| Aspect | Detail |
|--------|--------|
| **Platform** | Web only. Mobile apps (iOS/Android) |
| **Price** | $25-50/month (All Access ~$50/mo) |
| **GEDCOM Export** | 5.5.1 — decent but includes proprietary extensions. Export is manual (Settings > Family Tree > Manage > Export) |
| **Database** | Cloud-only. No local database access |
| **API Access** | No public API since 2014. The old Ancestry API was shut down. No developer access |
| **DNA Integration** | Best in class — 25M+ DNA tests, ThruLines, shared matches |
| **Record Collection** | Largest in the world — billions of records |

**Pros:**
- Largest record collection and DNA database
- Hints/ThruLines are genuinely useful for discovery
- Shared trees enable collaboration with relatives
- Regular record additions

**Cons:**
- Complete vendor lock-in — no API, limited export, cloud-only
- GEDCOM export is a manual, full-tree dump (no incremental export)
- Subscription model ($300-600/year)
- Living person data stored on Ancestry servers
- Export loses Ancestry-specific links, media URLs, hint connections
- No automation possible

### MyHeritage

| Aspect | Detail |
|--------|--------|
| **Platform** | Web + desktop app (Family Tree Builder, free) |
| **Price** | Free basic; $13-35/month for records/DNA |
| **GEDCOM Export** | 5.5.1 via Family Tree Builder — good fidelity |
| **Database** | Proprietary (Family Tree Builder) + cloud sync |
| **API Access** | No public API |
| **DNA Integration** | 7M+ DNA tests, DNA matches, chromosome browser |
| **Smart Matches** | Cross-references your tree with other users' trees globally |

**Pros:**
- Family Tree Builder is free and runs locally (Windows, Wine on Mac)
- Smart Matches find connections across millions of trees
- Good European and international coverage
- Tree Consistency Checker identifies data quality issues (you already have this PDF)
- DNA chromosome browser is best-in-class
- GEDCOM import/export is solid

**Cons:**
- Aggressive upselling in free tier
- Desktop app (Family Tree Builder) is Windows-only
- No API for automation
- Sync between desktop and web can have conflicts
- DNA database smaller than Ancestry's

### Comparison Matrix

| Feature | FTM 2024 | RootsMagic 11 | Ancestry | MyHeritage |
|---------|----------|---------------|----------|------------|
| Mac Native | Yes | Yes (since v8) | Web | No (Wine) |
| GEDCOM Export Quality | Good | Excellent | Decent | Good |
| Database Accessible | No | Yes (SQLite) | No | No |
| API/Scripting | None | SQLite queries | None | None |
| Ancestry Sync | Yes | No | N/A | No |
| FamilySearch Sync | No | Yes | No | No |
| DNA Built-in | Yes | No | Yes | Yes |
| Price | $80 once | $40 once | $300-600/yr | $156-420/yr |
| Automation Friendly | No | Yes | No | No |
| Plugin/Extension | No | No | No | No |

---

## 2. Recommendation: Interim Source of Truth

**Use RootsMagic 11 as the interim source of truth** during migration to a custom app.

**Rationale:**
1. **SQLite database** — You can directly query, script, and validate data with Python/SQL
2. **Cleanest GEDCOM export** — When you need GEDCOM, RM produces the most standards-compliant output
3. **$40 one-time** — No subscription pressure
4. **FamilySearch integration** — Access to the largest free genealogy database
5. **Community SQL recipes** — Extensive documentation of the `.rmtree` schema

**Migration workflow:**
1. Export from Ancestry via GEDCOM (you already have `Mallinger Family Tree Cleaned.ged`)
2. Import into RootsMagic 11 — it handles Ancestry GEDCOM well
3. Run gedfix validation/cleaning on the GEDCOM before import
4. Use RootsMagic as working database while building the custom app
5. Query the SQLite database directly for data validation and AI experiments

**Keep FTM 2024** for Ancestry TreeSync — use it as a read-only mirror of your Ancestry.com tree. Export from Ancestry via FTM periodically.

**Keep MyHeritage** for Smart Matches and the DNA chromosome browser — it finds connections that Ancestry misses, especially for European lines.

---

## 3. Open Source Project Evaluation

### Gramps (gramps-project.org)

| Aspect | Detail |
|--------|--------|
| **Language** | Python 3, GTK 3/4 |
| **License** | GPL v2+ |
| **GEDCOM** | 5.5.1 full support, experimental GEDCOM 7 |
| **Database** | BSDDB (Berkeley DB) or SQLite backend |
| **Plugin System** | Excellent — 400+ community plugins (reports, tools, gramplets) |
| **Activity** | Active — regular releases, large community |
| **Lines of Code** | ~200k+ Python |

**What to borrow:**
- Relationship calculator algorithms (`gen.relationship`)
- Date handling (supports ranges, approximations, dual dating, calendar systems)
- Place hierarchy model
- Report generation framework
- GEDCOM import/export with error recovery
- Narrative web report generator

**What NOT to fork:**
- GTK UI layer is not portable to Mac without significant effort
- BSDDB storage layer is outdated
- The codebase is mature but carries 20+ years of design decisions

### webtrees (webtrees.net)

| Aspect | Detail |
|--------|--------|
| **Language** | PHP 8.1+ |
| **License** | GPL v3 |
| **GEDCOM** | 5.5.1 full, GEDCOM 7.0 partial support |
| **Database** | MySQL/MariaDB |
| **Plugin System** | Module system with well-defined interfaces |
| **Activity** | Very active — maintained by Greg Roach (original developer of phpGedView) |

**What to borrow:**
- GEDCOM 7.0 parsing logic (one of the first to implement)
- Census assistant module (structured census data entry)
- Privacy controls (living person filtering, access levels)
- Media management approach
- The module/plugin interface design

**Not useful for:** Desktop app (it's purely web-based)

### GEDKeeper (gedkeeper.net)

| Aspect | Detail |
|--------|--------|
| **Language** | C#/.NET |
| **License** | GPL v2 |
| **GEDCOM** | 5.5.1 full support |
| **Platform** | Windows native, Linux/Mac via Mono/Avalonia |
| **Activity** | Active development |

**What to borrow:**
- Clean GEDCOM parser in a statically-typed language (reference for Swift port)
- Tree visualization algorithms
- Pedigree chart rendering

### Ancestris (ancestris.org)

| Aspect | Detail |
|--------|--------|
| **Language** | Java, NetBeans Platform |
| **License** | GPL v3 |
| **GEDCOM** | 5.5.1 with GEDCOM 5.5.1 editor |
| **Activity** | Moderate — French community primarily |

**What to borrow:**
- Genealogical editor concept (edit raw GEDCOM with validation)
- Geographic visualization of family movements

### python-gedcom (github.com/nickreynke/python-gedcom)

| Aspect | Detail |
|--------|--------|
| **Language** | Python |
| **License** | GPL v2 |
| **Purpose** | Lightweight GEDCOM parser/writer |
| **Activity** | Low — last commit 2023 |

**Alternative:** `ged4py` (already used in gedfix) is more robust.

### GRAMPS Web (gramps-project.github.io/web)

| Aspect | Detail |
|--------|--------|
| **Language** | Python (Gramps backend) + JavaScript (Lit frontend) |
| **License** | AGPL v3 |
| **Purpose** | Modern web UI for Gramps database |
| **Activity** | Active |

**What to borrow:**
- REST API design for Gramps data (could inform your own API layer)
- Modern web component approach to genealogy UI

### AI-Enabled Genealogy Projects

| Project | What It Does |
|---------|-------------|
| **MyHeritage AI Time Machine** | Commercial — AI-generated historical portraits |
| **DeepNostalgia** | Commercial (MyHeritage) — animates old photos |
| **RecordSearch (Trove)** | Australian National Library — AI-assisted record search |
| **Transkribus** | Handwritten text recognition for historical documents |
| **FamilySearch Indexing** | Uses ML for automated record indexing |

**Projects with AI features already built:**

| Project | What It Does |
|---------|-------------|
| **TreePilot** | FastAPI + GitHub Copilot SDK + React + D3.js — agentic research across Wikipedia, Wikidata, Library of Congress. Streaming SSE. GEDCOM enrichment. |
| **Autoresearch-Genealogy** | Claude Code-driven autonomous genealogy research with structured verification emphasis |
| **Gramps Web** | AI Chat Assistant — "chat with your family tree" in natural language. External search connectors (FamilySearch, Ancestry, Geneanet, WikiTree, FindAGrave) |

No significant **Swift** genealogy project exists on GitHub. This is a genuine gap and opportunity.

### Verdict: Best Codebases to Borrow From

1. **Gramps** — Relationship calculation, date handling, merge algorithms, GEDCOM import/export (20+ years of edge cases)
2. **Gramps Web API** — REST API design patterns, AI chat integration as reference implementation
3. **webtrees** — Privacy controls, GEDCOM 7 support (most complete implementation), plugin architecture
4. **TreePilot** — Agentic AI research architecture, GEDCOM enrichment workflow
5. **rust-gedcom / gedcomx-rs** — Clean Rust GEDCOM parsers if building performance-critical components

---

## 4. Data Format Comparison

| Format | Standard | Strengths | Weaknesses | Adoption |
|--------|----------|-----------|------------|----------|
| **GEDCOM 5.5.1** | FamilySearch (1999) | Universal, every tool supports it | Limited relationship types, no JSON, encoding issues, media by reference only | 99% of tools |
| **GEDCOM 7.0** | FamilySearch (2021) | UTF-8 mandatory, better multimedia, extensible tags, IRI-based | Almost no tool support yet. Spec still maturing | webtrees (partial), Gramps (experimental) |
| **GEDCOM X** | FamilySearch (JSON) | Modern JSON/XML, rich relationship model, built for APIs | Not a file format — designed for API interchange. No tool support for files | FamilySearch API only |
| **Gramps XML** | Gramps project | Full fidelity (lossless round-trip with Gramps), rich model | Gramps-specific, no other tools read it natively | Gramps only |
| **RootsMagic SQLite** | RootsMagic Inc | Queryable, full fidelity, directly scriptable | Proprietary schema, changes between versions | RootsMagic only |
| **CSV/TSV** | N/A | Simple, spreadsheet-compatible | Loses all relationships and hierarchy | Import/export utilities |

**Recommendation for your app:**
- **Primary storage:** SQLite with a well-documented schema (not GEDCOM, not Core Data)
- **Import/Export:** GEDCOM 5.5.1 as the interchange format (universal compatibility)
- **Future-proofing:** Implement GEDCOM 7.0 export when adoption reaches 10%+ of tools
- **API layer:** JSON/REST inspired by GEDCOM X concepts for AI integration

---

## 5. Mac Native Tech Stack Recommendation

### The Verdict: Tauri 2.0 + Python Backend

After first principles analysis, the optimal stack is:

| Layer | Technology | Rationale |
|-------|-----------|-----------|
| **Frontend** | Tauri 2.0 + SvelteKit | Native Mac window, ~5MB binary, system WebView (no Electron bloat) |
| **Backend** | Python (extend gedfix) | Already built, GEDCOM expertise, rich AI/ML ecosystem |
| **Database** | SQLite via `sqlite3` + FTS5 | Direct query, full-text search, no ORM overhead |
| **AI Runtime** | MLX (Apple Silicon local) + Cloud APIs | Local embeddings/small models via MLX, Claude/GPT for complex reasoning |
| **Scraping** | Playwright (Python) | Already in your stack, headless Chromium |

### Why NOT SwiftUI + Core Data

| Concern | SwiftUI/Core Data | Tauri + Python |
|---------|-------------------|----------------|
| Time to MVP | 6+ months (learn SwiftUI, rebuild GEDCOM processing) | Weeks (gedfix already works) |
| AI ecosystem | Limited Swift ML libraries | Rich Python AI ecosystem (langchain, llama-cpp-python, anthropic SDK) |
| GEDCOM processing | Would need to rewrite gedfix in Swift | Already done |
| Native feel | Best | Very good (system WebView, native menu bar, dock icon) |
| Cross-platform | Apple only | Mac + Linux + Windows |

### Why NOT Electron

Tauri 2.0 uses the system WebView (WebKit on Mac), resulting in ~5MB app size vs Electron's 200MB+. Same web frontend development experience, fraction of the resource usage.

### Why NOT Kotlin Compose Multiplatform

While Jonathan has Kotlin/Compose experience from SpiritAtlas, the Python AI ecosystem is irreplaceable for this project. Kotlin-Python interop adds complexity. Tauri gives a native-feeling Mac app while keeping the Python backend.

### Alternative: Pure CLI + TUI First

Before investing in any GUI framework, consider:

```
gedfix (existing CLI)
  + Textual (Python TUI framework)
  + Rich (terminal formatting)
  = Interactive terminal app with tree views, search, and AI features
```

This ships in days, not months, and proves the AI features work before building a GUI.

### Database Schema (SQLite)

```sql
-- Core entities
CREATE TABLE persons (
    id TEXT PRIMARY KEY,           -- Internal UUID
    gedcom_xref TEXT,              -- Original GEDCOM xref (e.g., @I123@)
    sex TEXT CHECK(sex IN ('M','F','U')),
    living INTEGER DEFAULT 0,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE names (
    id INTEGER PRIMARY KEY,
    person_id TEXT REFERENCES persons(id),
    given TEXT,
    surname TEXT,
    suffix TEXT,
    prefix TEXT,
    name_type TEXT DEFAULT 'birth', -- birth, married, aka
    sort_key TEXT,                   -- computed for sorting
    UNIQUE(person_id, name_type, given, surname)
);

CREATE TABLE events (
    id INTEGER PRIMARY KEY,
    entity_id TEXT,                 -- person or family ID
    entity_type TEXT CHECK(entity_type IN ('person','family')),
    event_type TEXT,                -- BIRT, DEAT, MARR, BURI, etc.
    date_value TEXT,                -- Raw GEDCOM date string
    date_sort INTEGER,             -- Computed Julian day for sorting
    place_id INTEGER REFERENCES places(id),
    description TEXT,
    source_citations TEXT           -- JSON array of source_id references
);

CREATE TABLE families (
    id TEXT PRIMARY KEY,
    gedcom_xref TEXT,
    partner1_id TEXT REFERENCES persons(id),
    partner2_id TEXT REFERENCES persons(id),
    relationship_type TEXT DEFAULT 'married'
);

CREATE TABLE children (
    family_id TEXT REFERENCES families(id),
    person_id TEXT REFERENCES persons(id),
    child_order INTEGER,
    PRIMARY KEY(family_id, person_id)
);

CREATE TABLE places (
    id INTEGER PRIMARY KEY,
    name TEXT,                      -- Full place string
    normalized TEXT,                -- Standardized form
    latitude REAL,
    longitude REAL,
    hierarchy TEXT                  -- JSON: {city, county, state, country}
);

CREATE TABLE sources (
    id INTEGER PRIMARY KEY,
    title TEXT,
    author TEXT,
    publisher TEXT,
    repository TEXT,
    url TEXT,
    source_type TEXT                -- census, vital, church, newspaper, etc.
);

CREATE TABLE media (
    id INTEGER PRIMARY KEY,
    file_path TEXT,
    mime_type TEXT,
    title TEXT,
    description TEXT,
    entity_id TEXT,
    entity_type TEXT
);

-- Full-text search
CREATE VIRTUAL TABLE persons_fts USING fts5(
    given, surname, content=names, content_rowid=id
);

-- AI features
CREATE TABLE ai_suggestions (
    id INTEGER PRIMARY KEY,
    entity_id TEXT,
    entity_type TEXT,
    suggestion_type TEXT,           -- merge, research_lead, transcription, correction
    content TEXT,                   -- JSON payload
    confidence REAL,
    model TEXT,                     -- which AI model generated this
    status TEXT DEFAULT 'pending',  -- pending, accepted, rejected
    created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE embeddings (
    id INTEGER PRIMARY KEY,
    entity_id TEXT,
    entity_type TEXT,
    vector BLOB,                    -- Stored embedding vector
    model TEXT,
    created_at TEXT DEFAULT (datetime('now'))
);

-- Change tracking for undo/redo
CREATE TABLE changelog (
    id INTEGER PRIMARY KEY,
    entity_id TEXT,
    entity_type TEXT,
    field_name TEXT,
    old_value TEXT,
    new_value TEXT,
    changed_by TEXT DEFAULT 'user',  -- user, ai, import, merge
    changed_at TEXT DEFAULT (datetime('now'))
);
```

---

## 6. AI Integration Architecture

### Feature Matrix

| AI Feature | Local LLM | Cloud API | Feasibility | MVP? |
|-----------|-----------|-----------|-------------|------|
| **Record Transcription** | OCR + local model | Claude vision for handwriting | High | Yes |
| **Name Variant Detection** | Embedding similarity | Not needed | High | Yes |
| **Date Interpretation** | Rule-based (already in gedfix) | Fallback for ambiguous dates | High | Already built |
| **Source Analysis** | Local embeddings | Claude for reasoning about source reliability | Medium | M2 |
| **Research Suggestions** | Local RAG over tree data | Claude for "what should I look for next?" | Medium | M2 |
| **Relationship Inference** | Graph algorithms | Claude for "could these be the same person?" | Medium | M2 |
| **Duplicate Detection** | Embedding + fuzzy match (already in gedfix) | Not needed | High | Already built |
| **Smart Merge** | Rule-based + confidence scoring | Claude for conflict resolution suggestions | Medium | M3 |
| **DNA Correlation** | Statistical matching | Claude for explaining relationships | Low | M3 |
| **Document Summarization** | Local summarization | Claude for census/vital record extraction | High | M2 |

### Multi-API Key Management

```python
# gedfix/ai_config.py
from dataclasses import dataclass
from typing import Optional
import subprocess
import json

@dataclass
class AIProvider:
    name: str           # claude, openai, gemini, local
    model: str          # claude-sonnet-4-20250514, gpt-4o, etc.
    api_key_ref: str    # 1Password reference or env var name
    rate_limit: int     # requests per minute
    cost_per_1k: float  # cost per 1k tokens (0 for local)
    supports_vision: bool
    supports_embeddings: bool

class AIKeyManager:
    """Manages multiple AI API keys via 1Password Connect."""

    def __init__(self):
        self.providers: dict[str, AIProvider] = {}
        self._key_cache: dict[str, str] = {}

    def get_key(self, provider_name: str) -> str:
        """Retrieve API key from 1Password Connect."""
        if provider_name in self._key_cache:
            return self._key_cache[provider_name]

        provider = self.providers[provider_name]

        if provider.api_key_ref.startswith("op://"):
            # 1Password Connect
            result = subprocess.run(
                ["op", "read", provider.api_key_ref],
                capture_output=True, text=True
            )
            key = result.stdout.strip()
        else:
            # Environment variable fallback
            import os
            key = os.environ.get(provider.api_key_ref, "")

        self._key_cache[provider_name] = key
        return key

    def select_provider(self, task: str) -> AIProvider:
        """Route task to optimal provider based on capability and cost."""
        if task in ("transcribe", "vision"):
            # Need vision capability
            return next(p for p in self.providers.values()
                       if p.supports_vision)
        elif task == "embeddings":
            # Prefer local for embeddings (free, fast, private)
            local = [p for p in self.providers.values() if p.cost_per_1k == 0]
            return local[0] if local else next(
                p for p in self.providers.values() if p.supports_embeddings
            )
        else:
            # Default to cheapest available
            return min(self.providers.values(), key=lambda p: p.cost_per_1k)
```

### Local LLM Integration (Apple Silicon)

```python
# Using MLX for Apple Silicon optimization
# pip install mlx-lm

from mlx_lm import load, generate

class LocalAI:
    """Local LLM inference on Apple Silicon via MLX."""

    def __init__(self, model_name: str = "mlx-community/Mistral-7B-v0.3-4bit"):
        self.model, self.tokenizer = load(model_name)

    def suggest_merge(self, person_a: dict, person_b: dict) -> str:
        prompt = f"""You are a genealogy expert. Compare these two person records and determine if they are the same person.

Person A: {json.dumps(person_a, indent=2)}
Person B: {json.dumps(person_b, indent=2)}

Are these the same person? Explain your reasoning and confidence level."""

        return generate(self.model, self.tokenizer, prompt=prompt, max_tokens=500)

    def research_suggestions(self, person: dict) -> str:
        prompt = f"""Given this person's genealogy record, suggest the next research steps:

{json.dumps(person, indent=2)}

What records should be searched? What gaps exist?"""

        return generate(self.model, self.tokenizer, prompt=prompt, max_tokens=500)
```

### Secure API Key Storage

**Primary:** 1Password Connect (already configured in your infrastructure)

```bash
# Store keys
op item create --category=API\ Credential \
  --title="Claude API Key - Genealogy" \
  --vault="Research" \
  password="sk-ant-..."

op item create --category=API\ Credential \
  --title="OpenAI API Key - Genealogy" \
  --vault="Research" \
  password="sk-..."

# Retrieve in code
op read "op://Research/Claude API Key - Genealogy/password"
```

**Fallback:** macOS Keychain (for when 1Password Connect is unavailable)

```python
import subprocess

def keychain_get(service: str, account: str) -> str:
    result = subprocess.run(
        ["security", "find-generic-password", "-s", service, "-a", account, "-w"],
        capture_output=True, text=True
    )
    return result.stdout.strip()

def keychain_set(service: str, account: str, password: str):
    subprocess.run([
        "security", "add-generic-password",
        "-s", service, "-a", account, "-w", password, "-U"
    ])
```

**Never:** Store API keys in `.env` files, config files, or git-tracked locations.

---

## 7. Playwright Scraping Architecture

### Architecture

```
gedfix scrape <site> <person-query>
    |
    v
ScrapingOrchestrator
    |
    +-- SiteAdapter (per-site logic)
    |       +-- AncestryAdapter
    |       +-- FamilySearchAdapter
    |       +-- FindAGraveAdapter
    |       +-- NewspapersAdapter
    |
    +-- SessionManager
    |       +-- Cookie/auth persistence
    |       +-- Rate limiter
    |       +-- Proxy rotation (optional)
    |
    +-- DataExtractor
    |       +-- Record parser (HTML -> structured data)
    |       +-- AI-assisted extraction (for unstructured pages)
    |
    +-- ResultStore
            +-- Cache layer (avoid re-scraping)
            +-- Dedup against existing tree data
```

### Implementation Sketch

```python
# gedfix/scraping/orchestrator.py
from playwright.async_api import async_playwright
import asyncio
from pathlib import Path

class ScrapingOrchestrator:
    def __init__(self, config_path: Path):
        self.config = load_config(config_path)
        self.rate_limiter = RateLimiter(
            requests_per_minute=self.config.get("rate_limit", 10)
        )
        self.cache = ScrapingCache(Path("~/.gedfix/scraping_cache.db"))

    async def scrape(self, site: str, query: dict) -> list[dict]:
        adapter = self._get_adapter(site)

        # Check cache first
        cached = self.cache.get(site, query)
        if cached:
            return cached

        async with async_playwright() as p:
            browser = await p.chromium.launch(
                headless=self.config.get("headless", True)
            )
            context = await browser.new_context(
                user_agent=self._random_user_agent(),
                viewport={"width": 1280, "height": 800}
            )

            # Load saved session cookies
            cookies = self._load_cookies(site)
            if cookies:
                await context.add_cookies(cookies)

            page = await context.new_page()

            try:
                await self.rate_limiter.wait()
                results = await adapter.search(page, query)

                # Cache results
                self.cache.store(site, query, results)

                # Save cookies for session persistence
                cookies = await context.cookies()
                self._save_cookies(site, cookies)

                return results
            finally:
                await browser.close()

class RateLimiter:
    """Token bucket rate limiter with per-site limits."""

    def __init__(self, requests_per_minute: int = 10):
        self.rpm = requests_per_minute
        self.interval = 60.0 / requests_per_minute
        self.last_request = 0

    async def wait(self):
        import time
        elapsed = time.time() - self.last_request
        if elapsed < self.interval:
            await asyncio.sleep(self.interval - elapsed)
        self.last_request = time.time()
```

### Rate Limiting Strategy

| Site | Recommended RPM | Notes |
|------|----------------|-------|
| Ancestry.com | 5-10 | Aggressive bot detection; use saved sessions |
| FamilySearch | 15-20 | More tolerant; respect robots.txt |
| FindAGrave | 10-15 | Moderate tolerance; memorial pages are public |
| Newspapers.com | 5 | Very strict; consider API if available |

### Legal Considerations

**CRITICAL: Scraping genealogy sites carries real legal and account risks.**

| Site | ToS on Scraping | Risk Level | Recommendation |
|------|----------------|------------|----------------|
| **Ancestry.com** | Explicitly prohibited. "You may not use any robot, spider, scraper..." | **HIGH** | Use GEDCOM export + FTM TreeSync instead. Only scrape record images you've already paid for access to. |
| **FamilySearch** | Free site, more permissive. API available. | **LOW** | Use the official FamilySearch API (free, well-documented). Only scrape what the API doesn't cover. |
| **FindAGrave** | Part of Ancestry. ToS prohibits automated access. | **MEDIUM** | Owned by Ancestry. Same ToS. Use carefully for memorial data. |
| **Newspapers.com** | Paid site, ToS prohibits scraping. | **HIGH** | Use manual clipping or API if available. |

**Best practices:**
1. **API first** — Always prefer official APIs (FamilySearch has one, it's free)
2. **Your own data** — Scraping records you've paid for is more defensible than bulk scraping
3. **Respect robots.txt** — Check and honor `robots.txt` directives
4. **Session-based** — Use your logged-in session, not anonymous scraping
5. **Cache aggressively** — Never re-scrape the same page
6. **Rate limit conservatively** — Be a polite client
7. **No redistribution** — Scraped data is for personal research only

**CFAA (Computer Fraud and Abuse Act):** The legal landscape changed after *hiQ Labs v. LinkedIn* (2022) — scraping publicly available data is generally permissible, but scraping behind a login wall with a bot when the ToS prohibits it is riskier. Genealogy sites behind paywalls (Ancestry, Newspapers.com) are higher risk.

**Practical recommendation:** Focus scraping on FamilySearch (API available, free) and FindAGrave (public memorials). For Ancestry, use GEDCOM export and FTM TreeSync. For MyHeritage, use Family Tree Builder desktop export.

---

## 8. Plugin System Architecture

### Design Pattern: Service Adapters

```python
# gedfix/plugins/base.py
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional

@dataclass
class SearchResult:
    """Standardized result from any genealogy service."""
    source_service: str        # "ancestry", "familysearch", "findagrave"
    record_type: str           # "census", "vital", "military", "immigration"
    title: str
    date: Optional[str]
    place: Optional[str]
    persons: list[dict]        # [{name, role, age, ...}]
    url: Optional[str]
    confidence: float          # 0-1 match confidence
    raw_data: dict             # Service-specific fields

class GenealogyServicePlugin(ABC):
    """Base class for genealogy service plugins."""

    @property
    @abstractmethod
    def name(self) -> str: ...

    @property
    @abstractmethod
    def requires_auth(self) -> bool: ...

    @abstractmethod
    async def search_person(self, name: str, birth_year: int = None,
                           place: str = None) -> list[SearchResult]: ...

    @abstractmethod
    async def get_record(self, record_id: str) -> dict: ...

    def authenticate(self, credentials: dict) -> bool:
        """Override for services requiring auth."""
        return True

# gedfix/plugins/familysearch.py
class FamilySearchPlugin(GenealogyServicePlugin):
    name = "familysearch"
    requires_auth = True  # Free account required

    async def search_person(self, name, birth_year=None, place=None):
        # Uses FamilySearch API (official, free)
        ...

# gedfix/plugins/findagrave.py
class FindAGravePlugin(GenealogyServicePlugin):
    name = "findagrave"
    requires_auth = False

    async def search_person(self, name, birth_year=None, place=None):
        # Searches FindAGrave via Playwright
        ...
```

### Plugin Discovery

```python
# gedfix/plugins/__init__.py
import importlib
import pkgutil
from pathlib import Path

def discover_plugins() -> dict[str, GenealogyServicePlugin]:
    """Auto-discover plugins in the plugins directory and user plugins."""
    plugins = {}

    # Built-in plugins
    package_path = Path(__file__).parent
    for importer, modname, ispkg in pkgutil.iter_modules([str(package_path)]):
        if modname.startswith("_"):
            continue
        module = importlib.import_module(f".{modname}", __package__)
        for attr_name in dir(module):
            attr = getattr(module, attr_name)
            if (isinstance(attr, type) and
                issubclass(attr, GenealogyServicePlugin) and
                attr is not GenealogyServicePlugin):
                instance = attr()
                plugins[instance.name] = instance

    # User plugins (from ~/.gedfix/plugins/)
    user_plugin_dir = Path.home() / ".gedfix" / "plugins"
    if user_plugin_dir.exists():
        for py_file in user_plugin_dir.glob("*.py"):
            spec = importlib.util.spec_from_file_location(py_file.stem, py_file)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            # Same discovery logic as above

    return plugins
```

---

## 9. Sync/Merge and Conflict Resolution

### Tree Merging Algorithm

```
1. IDENTIFY — Find matching persons across two trees
   - Name similarity (rapidfuzz, already in gedfix)
   - Birth year proximity (+/- 2 years)
   - Place similarity
   - Family structure (shared parents/spouses/children)

2. SCORE — Compute confidence for each potential match
   - Name match: 0-40 points
   - Birth date match: 0-20 points
   - Place match: 0-15 points
   - Family structure match: 0-25 points

3. CLASSIFY — Auto-merge (>95), suggest (70-95), skip (<70)

4. MERGE — For confirmed matches:
   - Keep the more detailed record as primary
   - Append unique facts from secondary
   - Merge source citations (union)
   - Flag conflicts for review

5. CONFLICT RESOLUTION — For conflicting facts:
   - Record both values with provenance
   - Let user choose via interactive review
   - AI can suggest which is more likely correct
```

### Conflict Resolution UX

```
┌──────────────────────────────────────────────────────┐
│  CONFLICT: Birth Date for John Mallinger              │
│                                                       │
│  Source A (Ancestry):     15 MAR 1892                │
│  Source B (MyHeritage):   MAR 1892                   │
│  Source C (Census 1900):  ABT 1892                   │
│                                                       │
│  AI Analysis: Source A is most specific and           │
│  consistent with census age. Recommend Source A.      │
│  Confidence: 87%                                      │
│                                                       │
│  [A] Accept Source A    [B] Accept Source B           │
│  [C] Accept Source C    [M] Manual entry              │
│  [S] Skip (keep all)   [?] Show evidence             │
└──────────────────────────────────────────────────────┘
```

### Version Control for Genealogy Data

Use the `changelog` table (defined in schema above) for undo/redo. Every change records:
- What changed (entity, field, old value, new value)
- Who changed it (user, AI, import, merge)
- When it changed

This is simpler and more practical than CRDT or OT for a single-user local-first app.

---

## 10. Privacy, Encryption, and Security

### Living Person Filtering

```python
from datetime import date

def is_living(person: dict) -> bool:
    """Determine if a person should be treated as living (private)."""
    # Explicit death record
    if person.get("death_date") or person.get("burial_date"):
        return False

    # Born more than 110 years ago — presumed deceased
    birth_year = person.get("birth_year")
    if birth_year and (date.today().year - birth_year) > 110:
        return False

    # No birth date and no death date — assume living (conservative)
    return True

def filter_living(tree: dict, include_living: bool = False) -> dict:
    """Remove or redact living persons from export."""
    if include_living:
        return tree

    filtered = tree.copy()
    for person_id, person in filtered["persons"].items():
        if is_living(person):
            person["names"] = [{"given": "Living", "surname": person.get("surname", "")}]
            person["events"] = {}  # Remove all events
            person["notes"] = []

    return filtered
```

### At-Rest Encryption

```python
# SQLite encryption via SQLCipher
# pip install pysqlcipher3

import sqlite3  # or pysqlcipher3 for encrypted databases

def open_encrypted_db(path: str, passphrase: str):
    """Open an encrypted SQLite database."""
    conn = sqlite3.connect(path)
    conn.execute(f"PRAGMA key = '{passphrase}'")
    conn.execute("PRAGMA cipher_compatibility = 4")
    return conn
```

**Key derivation:** Derive the database encryption key from the user's macOS Keychain password or 1Password vault.

### GDPR Compliance

For European relatives in the tree:
- Living persons' data stays local by default
- Cloud AI calls redact PII before sending (names replaced with placeholders)
- Export functions include GDPR filter (remove EU-resident living persons)
- Right to erasure: `gedfix gdpr-erase <person-id>`

### Data Classification

| Data Type | Classification | Can Send to Cloud AI? | Storage |
|-----------|---------------|----------------------|---------|
| Deceased persons (100+ years) | Public | Yes | Unencrypted |
| Deceased persons (recent) | Semi-private | Redacted names only | Encrypted |
| Living persons | Private | Never | Encrypted |
| DNA data | Highly private | Never | Encrypted |
| API keys | Secret | Never | Keychain/1Password |

---

## 11. Testing and CI/CD

### Test Strategy

```
tests/
├── test_gedcom_parser.py      # GEDCOM 5.5.1 parsing (extend existing)
├── test_dates.py              # Date normalization (extend existing)
├── test_names.py              # Name normalization (extend existing)
├── test_places.py             # Place normalization
├── test_deduplication.py      # Duplicate detection
├── test_validation.py         # Structural validation
├── test_merge.py              # Tree merging
├── test_ai_integration.py     # AI feature tests (mocked)
├── test_scraping.py           # Scraping adapter tests (mocked)
├── test_privacy.py            # Living person filtering
├── test_import_export.py      # Round-trip GEDCOM tests
├── test_plugin_system.py      # Plugin discovery and loading
├── fixtures/
│   ├── minimal.ged            # Tiny valid GEDCOM
│   ├── complex.ged            # Full-featured GEDCOM
│   ├── malformed.ged          # Error handling tests
│   └── ancestry_export.ged    # Ancestry-specific tags
```

### CI/CD Pipeline (GitHub Actions)

```yaml
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]

jobs:
  test:
    runs-on: macos-latest
    strategy:
      matrix:
        python-version: ["3.11", "3.12", "3.13"]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: ${{ matrix.python-version }}
      - run: pip install -e ".[dev]"
      - run: pytest tests/ -v --cov=gedfix --cov-report=xml
      - uses: codecov/codecov-action@v4

  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
      - run: pip install ruff mypy
      - run: ruff check gedfix/
      - run: mypy gedfix/ --ignore-missing-imports

  gedcom-validation:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
      - run: pip install -e .
      - run: |
          for f in tests/fixtures/*.ged; do
            gedfix check "$f" --level standard
          done
```

---

## 12. Repository Audit

### Files to KEEP (core library, high value)

| File | Size | Purpose | Action |
|------|------|---------|--------|
| `gedfix/api.py` | 1.5K | Public API facade | Keep, extend |
| `gedfix/checker.py` | 14K | GEDCOM parser with Person/Family models | Keep — core data model |
| `gedfix/cli.py` | 6.6K | Click CLI with scan/check/fix/dedupe/review | Keep, extend with AI commands |
| `gedfix/config.py` | 15K | Dataclass-based configuration | Keep |
| `gedfix/config.yaml` | 7.6K | Default configuration | Keep |
| `gedfix/dates.py` | 4.9K | GEDCOM date normalization | Keep — battle-tested |
| `gedfix/deduplication.py` | 10.7K | Duplicate detection with fuzzy matching | Keep — core feature |
| `gedfix/fixer.py` | 3.5K | Main fix pipeline | Keep |
| `gedfix/manual_review.py` | 20K | Interactive review session | Keep — refactor for AI integration |
| `gedfix/names.py` | 5.5K | Name normalization with nickname mappings | Keep |
| `gedfix/places.py` | 6.6K | Place normalization with state/country maps | Keep |
| `gedfix/rules.py` | 236B | Rule loader | Keep |
| `gedfix/rules.yaml` | 767B | Fix level definitions | Keep |
| `gedfix/validation.py` | 10.6K | Structural validation | Keep |
| `gedfix/io_utils.py` | 360B | Backup utility | Keep |
| `pyproject.toml` | — | Package configuration | Keep, extend |
| `tests/test_checker_smoke.py` | — | Existing tests | Keep, extend |
| `tests/test_comprehensive.py` | — | Existing tests | Keep, extend |

### Files to REMOVE (one-off scripts, processed data)

| File | Reason |
|------|--------|
| `fix_helen_births.py` | One-off fix script, specific to a single person |
| `fix_myheritage_properly.py` | One-off MyHeritage fix |
| `myheritage_fixer.py` | One-off MyHeritage fix |
| `start_manual_review.py` | Launcher script, functionality in CLI |
| `new/final.names_dedup.ged` | Processed intermediate files |
| `new/final.names_dedup.ged.bak` | Backup of intermediate |
| `new/final.names_dedup.ged.preGeo` | Backup of intermediate |
| `new/final.names_dedup.ged.preLinkCleanup` | Backup of intermediate |
| `new/final.names_dedup.ged.preMedia` | Backup of intermediate |
| `new/master_geo_media_082725).ged` | Processed intermediate (typo in name) |
| `new/master_geo_media,.ged` | Processed intermediate (typo in name) |
| `new/media_suggestions.txt` | One-time output |
| `new/issues_overview.csv` | One-time output |
| `new/Tree Consistency Checker*.pdf` | MyHeritage export — move to `data/` |
| `scripts/aggressive_fact_cleaner.py` | Superseded by gedfix check/fix |
| `scripts/comprehensive_duplicate_processor.py` | Superseded by gedfix dedupe |
| `scripts/comprehensive_gedcom_processor.py` | Superseded by gedfix fix |
| `scripts/fixed_deduplicate.py` | Superseded by gedfix dedupe |
| `scripts/remove_duplicate_facts.py` | Superseded by gedfix fix |
| `scripts/conservative_fact_deduplicator.py` | Superseded by gedfix fix |
| `scripts/deduplicate_names.py` | Superseded by gedfix dedupe |

### Files to REFACTOR (useful but need modernization)

| File | Current State | Refactor Plan |
|------|--------------|---------------|
| `scripts/merge_rm10_preserve_dates.py` | RootsMagic 10 merge script | Extract merge logic into `gedfix/merge.py` |
| `scripts/rootsmagic_problem_fixer.py` | RM-specific fixes | Extract reusable fixes into gedfix |
| `scripts/safe_duplicate_detector.py` | Standalone duplicate finder | Merge into `gedfix/deduplication.py` |
| `scripts/source_preserving_person_dedupe.py` | Source-aware merge | Key logic for `gedfix/merge.py` |
| `scripts/ultra_safe_person_merger.py` | Conservative merge | Merge strategy into `gedfix/merge.py` |
| `scripts/analyze_myheritage_issues.py` | MyHeritage import analysis | Extract into `gedfix/importers/myheritage.py` |
| `scripts/parse_myheritage_pdf.py` | PDF parsing | Move to `gedfix/importers/myheritage_pdf.py` |
| `scripts/add_media_to_gedcom.py` | Media attachment | Extract into `gedfix/media.py` |
| `new/geocode_places.py` | Place geocoding | Extract into `gedfix/geocoding.py` |
| `new/build_cache_from_geonames.py` | GeoNames cache builder | Extract into `gedfix/geocoding.py` |
| `new/remove_dup_links.py` | Link dedup | Merge into `gedfix/deduplication.py` |
| `new/remove_dup_names.py` | Name dedup | Merge into `gedfix/deduplication.py` |
| `scripts/verify_data_integrity.sh` | Integrity verification | Keep as-is, add to CI |
| `scripts/setup_processing_workspace.sh` | Workspace setup | Keep as-is |
| `scripts/rollback_to_backup.sh` | Rollback utility | Keep as-is |
| `scripts/master_gedcom_workflow.sh` | Master pipeline | Update to use gedfix CLI |

### gedfix Library Reuse Plan

The existing gedfix library is the **foundation** for the new app. It already handles:
- GEDCOM 5.5.1 parsing (checker.py)
- Date normalization with GEDCOM-safe patterns (dates.py)
- Name normalization with nickname resolution (names.py)
- Place normalization with state/country expansion (places.py)
- Duplicate detection with fuzzy matching (deduplication.py)
- Structural validation (validation.py)
- Interactive manual review (manual_review.py)
- Multi-level fix pipelines (fixer.py)
- Configurable rules (rules.yaml, config.py)

**What to add:**
1. `gedfix/ai/` — AI integration module (suggestions, transcription, embeddings)
2. `gedfix/scraping/` — Playwright scraping adapters
3. `gedfix/merge.py` — Tree merging (consolidate from scripts/)
4. `gedfix/importers/` — Format-specific importers (RootsMagic SQLite, MyHeritage, CSV)
5. `gedfix/exporters/` — Format-specific exporters
6. `gedfix/plugins/` — Plugin system
7. `gedfix/db.py` — SQLite database layer (for the new app)
8. `gedfix/privacy.py` — Living person filtering, GDPR

---

## 13. Migration Plan

### Step-by-Step Migration

#### Phase 1: Data Collection and Validation

```bash
# 1. Extract the ZIP-wrapped GEDCOM
cd ~/Downloads
unzip mallinger-family-tree.ged -d mallinger-extracted/
# Note: this file is actually a ZIP containing the cleaned GEDCOM

# 2. Copy source files to workspace
cp "Mallinger Family Tree Cleaned.ged" ~/Workspace/ged/data/master/ancestry_cleaned_20260315.ged
cp dna-data-2026-03-14.zip ~/Workspace/ged/data/master/

# 3. Validate the cleaned GEDCOM
cd ~/Workspace/ged
pip install -e .
gedfix check data/master/ancestry_cleaned_20260315.ged --level standard
gedfix scan data/master/ancestry_cleaned_20260315.ged --report out/ancestry_scan.json

# 4. Run comprehensive check
gedfix check data/master/ancestry_cleaned_20260315.ged --level comprehensive
```

#### Phase 2: Clean and Standardize

```bash
# 5. Fix dates and names (non-destructive, with backups)
gedfix fix data/master/ancestry_cleaned_20260315.ged \
  --out data/processing/ancestry_fixed.ged \
  --backup-dir data/master/backups \
  --level standard

# 6. Verify no data loss
scripts/verify_data_integrity.sh \
  data/master/ancestry_cleaned_20260315.ged \
  data/processing/ancestry_fixed.ged

# 7. Run aggressive fixes (with review)
gedfix fix data/processing/ancestry_fixed.ged \
  --out data/processing/ancestry_aggressive.ged \
  --level aggressive \
  --dry-run  # Review first!

# 8. Deduplicate
gedfix dedupe data/processing/ancestry_fixed.ged \
  --out data/processing/ancestry_deduped.ged \
  --threshold 95.0 \
  --dry-run  # Review first!
```

#### Phase 3: Import into RootsMagic 11

1. Install RootsMagic 11 (via CrossOver on Mac, or on Windows machine)
2. Import `data/processing/ancestry_fixed.ged`
3. Run RootsMagic's built-in problem checker
4. Merge any additional data from MyHeritage exports
5. Use RootsMagic as the working database

```bash
# Query RootsMagic SQLite database directly
sqlite3 ~/path/to/family.rmtree <<EOF
SELECT COUNT(*) FROM PersonTable;
SELECT COUNT(*) FROM FamilyTable;
SELECT NameTable.Given, NameTable.Surname, EventTable.Date
FROM NameTable
JOIN EventTable ON NameTable.OwnerID = EventTable.OwnerID
WHERE EventTable.EventType = 1
LIMIT 10;
EOF
```

#### Phase 4: Extract DNA Data

```bash
# 9. Extract AncestryDNA data
cd ~/Workspace/ged/data/master
unzip dna-data-2026-03-14.zip
head -20 AncestryDNA.txt  # Review format

# AncestryDNA.txt format:
# rsid  chromosome  position  allele1  allele2
# rs3094315  1  752566  A  G
```

#### Phase 5: MyHeritage Integration

1. Export from MyHeritage via Family Tree Builder (GEDCOM 5.5.1)
2. Save to `data/master/myheritage_export_YYYYMMDD.ged`
3. Run gedfix validation:

```bash
gedfix check data/master/myheritage_export_*.ged --level standard
gedfix scan data/master/myheritage_export_*.ged --report out/myheritage_scan.json
```

4. Compare person counts and identify unique records in each tree

### Migration from Each Platform

| Platform | Export Method | Format | Notes |
|----------|-------------|--------|-------|
| **Ancestry.com** | Settings > Trees > Manage > Export Tree | GEDCOM 5.5.1 | Already done — you have `Mallinger Family Tree Cleaned.ged` |
| **FTM 2024** | File > Export > GEDCOM | GEDCOM 5.5.1 | Export from FTM after syncing with Ancestry for latest data |
| **RootsMagic 11** | File > Export > GEDCOM | GEDCOM 5.5.1 | Cleanest export; also direct SQLite access via `.rmtree` |
| **MyHeritage** | Family Tree Builder > File > Export > GEDCOM | GEDCOM 5.5.1 | Must use desktop app, not web |

---

## 14. MVP Roadmap

### Milestone 1: Enhanced CLI (gedfix 1.0)

**Goal:** Ship a polished CLI that replaces all standalone scripts.

**Scope:**
- Consolidate scripts into gedfix commands (merge, geocode, media)
- Add `gedfix import-rmtree <file.rmtree>` for direct RootsMagic import
- Add `gedfix merge <tree1.ged> <tree2.ged> --out merged.ged`
- Add `gedfix stats <file.ged>` for tree statistics
- Add `gedfix export --filter-living` for privacy-safe export
- Clean up repo (remove one-off scripts, organize data/)
- 80%+ test coverage on core library
- CI pipeline on GitHub Actions

### Milestone 2: AI-Powered Research Assistant

**Goal:** Add AI features to the CLI for research acceleration.

**Scope:**
- `gedfix ai suggest <person-xref>` — AI research suggestions
- `gedfix ai transcribe <image>` — OCR + AI for record images
- `gedfix ai merge-review` — AI-assisted conflict resolution
- Multi-API key management (1Password Connect)
- Local embeddings via MLX for semantic search
- `gedfix search "maiden name of John's wife"` — natural language search
- FamilySearch API integration (official, free)
- Plugin system for genealogy service adapters

### Milestone 3: Mac GUI (GedFix Desktop)

**Goal:** Lightweight Mac app wrapping the CLI.

**Scope:**
- Tauri 2.0 shell with SvelteKit frontend
- Tree visualization (pedigree chart, descendant chart)
- Person editor with AI suggestions sidebar
- Search with full-text and semantic search
- Merge/conflict resolution UI
- Privacy controls (living person filtering)
- Playwright scraping integration (FamilySearch, FindAGrave)
- DNA data viewer (basic — display matches, no analysis)

---

## 15. License and Contributor Guidelines

### Recommended License: MIT

**Why MIT over GPL:**
- Your existing code is not GPL-encumbered (you wrote it)
- MIT allows maximum flexibility for you and contributors
- If you borrow algorithms from Gramps (GPL), keep them in a separate module with clear attribution
- MIT is the standard for Python CLI tools and libraries

**Alternative:** If you want to ensure improvements stay open source, use **Apache 2.0** (patent protection + permissive).

### Contributor Guidelines (Outline)

```markdown
# Contributing to GedFix

## Getting Started
1. Fork the repo
2. `pip install -e ".[dev]"` (includes pytest, ruff, mypy)
3. Run tests: `pytest tests/ -v`
4. Create a branch: `git checkout -b feature/your-feature`

## Code Standards
- Python 3.11+
- Type hints on all public functions
- Tests for all new features (pytest)
- Linting: `ruff check gedfix/`
- No secrets in code (use 1Password or env vars)

## GEDCOM Handling Rules
- NEVER silently drop data during import/export
- ALWAYS create AutoFix notes when modifying values
- ALWAYS preserve original values in notes
- Test with the fixtures in tests/fixtures/

## Privacy
- Living persons must be filtered from all exports by default
- Never send PII to cloud APIs without explicit user opt-in
- DNA data never leaves the local machine

## Pull Request Process
1. Update tests
2. Run full test suite
3. Update CHANGELOG.md
4. PR description explains what and why
```

---

## 16. Learning Resources

### Open Source Projects to Study

| Project | URL | What to Learn |
|---------|-----|---------------|
| **Gramps** | github.com/gramps-project/gramps | Relationship calculator, date handling, merge algorithms, report generation, plugin system |
| **webtrees** | github.com/fisharebest/webtrees | Privacy controls, GEDCOM 7 support, module architecture, census assistant |
| **GEDKeeper** | github.com/Serg-Norseman/GEDKeeper | Tree visualization, typed GEDCOM parser |
| **GRAMPS Web** | github.com/gramps-project/gramps-web-api | REST API design for genealogy data |
| **python-gedcom** | github.com/nickreynke/python-gedcom | Lightweight GEDCOM parser reference |
| **Tauri** | github.com/tauri-apps/tauri | Desktop app framework |
| **Textual** | github.com/Textualize/textual | Python TUI framework (for M1 interactive CLI) |
| **MLX** | github.com/ml-explore/mlx | Apple Silicon ML framework |

### Specifications and Standards

| Resource | What It Covers |
|----------|---------------|
| **GEDCOM 5.5.1 Spec** | gedcom.org/gedcom.html — The standard your data is in |
| **GEDCOM 7.0 Spec** | gedcom.io — The future standard (read for forward compatibility) |
| **FamilySearch API Docs** | familysearch.org/developers — Free API, well documented |
| **RootsMagic SQLite Schema** | sqlitetoolsforrootsmagic.com — Community-documented RM database schema |

### Books and Courses

| Resource | Topic |
|----------|-------|
| *Professional Genealogy* (BCG) | Research methodology standards |
| *Evidence Explained* (Elizabeth Shown Mills) | Source citation standards |
| *Mastering Genealogical Proof* (Thomas W. Jones) | Proof standard methodology |

---

## 17. Your Source Files Assessment

### File: `Mallinger Family Tree Cleaned.ged` (1.9 MB, 65,296 lines)

- **Source:** Ancestry.com export, dated 15 Mar 2026
- **Format:** GEDCOM 5.5.1, UTF-8
- **Software:** Ancestry.com Family Trees v2025.08
- **Status:** This is your primary, most complete tree
- **Action:** Copy to `data/master/`, run gedfix validation, import to RootsMagic

### File: `mallinger-family-tree.ged` (354 KB, 1,305 "lines")

- **Actual format:** ZIP archive (starts with `PK` magic bytes), NOT a raw GEDCOM
- **Contents:** Contains `Mallinger Family Tree Cleaned.ged` (likely an earlier/compressed version)
- **Action:** Extract with `unzip` and compare to the cleaned version. Likely an older snapshot — verify dates.

### File: `dna-data-2026-03-14.zip` (5.9 MB)

- **Contents:** Single file `AncestryDNA.txt` (18.3 MB uncompressed)
- **Format:** Tab-separated: rsid, chromosome, position, allele1, allele2
- **Purpose:** Raw SNP genotype data from AncestryDNA test
- **Action:** Extract to `data/master/dna/`. This is raw genetic data — keep encrypted, never upload to cloud AI. Use for matching against other DNA databases (GEDmatch, MyHeritage).

---

## 18. DNA Data Integration

### AncestryDNA.txt Format

```
rsid        chromosome  position  allele1  allele2
rs3094315   1           752566    A        G
rs12124819  1           776546    A        G
```

### Integration Approach

**For MVP (M3):** Display-only. Show DNA match data alongside tree data.

**DNA features (future):**
1. **SNP comparison** — Compare raw files between family members to confirm relationships
2. **Segment matching** — Parse shared DNA segments from Ancestry/MyHeritage match data
3. **Relationship estimation** — Calculate expected vs actual shared cM for known relationships
4. **GEDmatch upload** — Automate upload to GEDmatch for cross-platform matching
5. **Haplogroup display** — Show Y-DNA and mtDNA haplogroups if available

**Libraries:**
- `biopython` — DNA sequence handling
- `pandas` — Tabular data processing for SNP data
- Custom cM calculation based on published genetic maps

**Privacy:** DNA data is the most sensitive data in the app. It must:
- Never be sent to cloud AI services
- Be stored encrypted at rest
- Require explicit consent before any sharing/export
- Support full deletion (`gedfix dna-erase`)

---

## 19. Next Actions Checklist

### Immediate (run these commands now)

```bash
# 1. Organize source files
cd ~/Workspace/ged
mkdir -p data/master/backups data/master/dna

# 2. Copy source files to workspace
cp ~/Downloads/"Mallinger Family Tree Cleaned.ged" data/master/ancestry_cleaned_20260315.ged

# 3. Extract the ZIP-wrapped GEDCOM
cd ~/Downloads
file mallinger-family-tree.ged  # Confirm it's a ZIP
unzip mallinger-family-tree.ged -d /tmp/mallinger-extracted/
ls /tmp/mallinger-extracted/

# 4. Extract DNA data
cd ~/Workspace/ged/data/master/dna
unzip ~/Downloads/dna-data-2026-03-14.zip
head -5 AncestryDNA.txt

# 5. Install gedfix and validate
cd ~/Workspace/ged
pip install -e .
gedfix scan data/master/ancestry_cleaned_20260315.ged --report out/ancestry_scan.json
gedfix check data/master/ancestry_cleaned_20260315.ged --level standard

# 6. Run integrity check
gedfix check data/master/ancestry_cleaned_20260315.ged --level comprehensive
```

### This week

- [ ] Run gedfix validation on the cleaned GEDCOM
- [ ] Extract and inspect the ZIP-wrapped GEDCOM (compare to cleaned version)
- [ ] Install RootsMagic 11 (CrossOver on Mac or on Windows machine)
- [ ] Import cleaned GEDCOM into RootsMagic
- [ ] Export from MyHeritage via Family Tree Builder
- [ ] Run gedfix validation on MyHeritage export
- [ ] Decide: TUI-first (Textual) or GUI-first (Tauri)?

### This month

- [ ] Clean up repository (remove one-off scripts per audit above)
- [ ] Consolidate merge scripts into `gedfix/merge.py`
- [ ] Add `gedfix merge` CLI command
- [ ] Add `gedfix import-rmtree` command
- [ ] Set up GitHub Actions CI
- [ ] Add test coverage for core modules
- [ ] Research FamilySearch API and get developer key

### Next quarter

- [ ] Implement AI suggestion engine (`gedfix ai suggest`)
- [ ] Set up MLX for local embeddings
- [ ] Build FamilySearch plugin
- [ ] Build interactive TUI for merge/conflict resolution
- [ ] Begin Tauri prototype (if TUI validates the workflow)

---

---

## Companion Documents

- **`docs/state/MAC_APP_ARCHITECTURE_RESEARCH.md`** — 900+ line deep dive on SwiftUI + GRDB.swift + SQLCipher architecture, sqlite-vec for embeddings, circuit breaker pattern for API key rotation, 15-table schema design, and 5-phase build plan. Read this if you decide to go the native Swift route instead of Tauri.

---

*This document is a living reference. Update it as decisions are made and milestones are reached.*
