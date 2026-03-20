# Mac-Native Genealogy App: Architecture & Tech Stack Research

**Date:** 2026-03-19
**Scope:** Comprehensive technical research for building a Mac-native genealogy application with AI features, scraping, plugin system, and privacy guarantees.

---

## Table of Contents

1. [Mac Native Tech Stack Options](#1-mac-native-tech-stack-options)
2. [Database Architecture](#2-database-architecture)
3. [AI Integration Architecture](#3-ai-integration-architecture)
4. [Secure API Key Storage](#4-secure-api-key-storage)
5. [Playwright Scraping Architecture](#5-playwright-scraping-architecture)
6. [Modular Plugin System](#6-modular-plugin-system)
7. [Sync/Merge/Conflict Resolution](#7-syncmergeconflict-resolution)
8. [Privacy and Encryption](#8-privacy-and-encryption)
9. [Testing and CI](#9-testing-and-ci)
10. [Recommended Architecture Summary](#10-recommended-architecture-summary)

---

## 1. Mac Native Tech Stack Options

### Option A: SwiftUI + GRDB/SQLite (RECOMMENDED)

**Libraries:**
- GRDB.swift 7.10.0 (Feb 2026) -- SQLite toolkit with observation
- SharingGRDB (now SQLiteData) 0.6.0+ by Point-Free -- SwiftUI-native `@FetchAll`/`@FetchOne` macros
- GRDBQuery 0.10+ -- SwiftUI companion for database observation

**Pros:**
- Full SQL control over complex genealogical queries (joins across persons, families, events, sources)
- FTS5 integration for searching names, places, and notes
- Performance inversely proportional to abstraction -- GRDB is faster than Core Data which is faster than SwiftData for raw read/write
- SQLite is the proven genealogy database format (RootsMagic, MacFamilyTree, Charting Companion all use it)
- Direct access to SQLite extensions: FTS5, sqlite-vec for embeddings, SQLCipher for encryption
- SharingGRDB/SQLiteData now supports CloudKit sync (as of 2026)
- You already have deep GEDCOM/SQLite domain expertise from the gedfix project

**Cons:**
- Requires SQL knowledge (not a problem given your background)
- No automatic iCloud sync out of the box (SharingGRDB adds this now)
- Manual schema migration management

**Verdict:** Best choice for genealogy. The data model is inherently relational with complex many-to-many relationships that benefit from direct SQL access.

### Option B: SwiftUI + SwiftData/Core Data

**Libraries:**
- SwiftData (built into macOS 14+/iOS 17+)
- Core Data (built into all Apple platforms)

**Pros:**
- Zero dependency -- ships with macOS
- Automatic iCloud sync via CloudKit
- @Query macro for SwiftUI integration
- Apple's long-term investment path

**Cons:**
- SwiftData is still maturing -- performance is notably inferior to direct SQLite/GRDB
- Complex genealogical queries (recursive ancestor/descendant traversal, fuzzy name matching) are awkward in @Query
- No FTS5 access -- you lose full-text search over historical records
- No sqlite-vec -- you lose local vector search for AI embeddings
- No SQLCipher -- you lose at-rest encryption control
- Schema migration is opaque and sometimes buggy
- Cannot share the database file with non-Apple platforms

**Verdict:** Not recommended for genealogy. Too much abstraction hides the SQL power needed for complex family relationship queries.

### Option C: SwiftUI + Rust Backend (via UniFFI)

**Libraries:**
- UniFFI 0.28+ (Mozilla) -- generates Swift bindings from Rust automatically
- swift-bridge 0.1.54+ -- alternative FFI, more manual but tighter integration
- Crux (Red Badger) -- full cross-platform framework, Rust core + SwiftUI shell

**Pros:**
- Rust core logic is cross-platform (share with future Linux/Windows/mobile builds)
- UniFFI auto-generates Swift enums, structs, and function bindings
- Rust enum becomes Swift enum, `make_user()` becomes `makeUser()` automatically
- Memory safety guarantees for core data operations
- GEDCOM parsing in Rust is extremely fast
- Can share the same core with a Tauri web frontend later

**Cons:**
- Two-language complexity increases onboarding cost
- UniFFI has partial Swift 6 support (improving)
- Debugging across FFI boundary is harder
- Build pipeline requires Rust toolchain + Xcode
- Overkill if you are only targeting macOS

**Verdict:** Strong choice if cross-platform is a goal. Consider as a Phase 2 migration path -- start with pure Swift, extract performance-critical GEDCOM parsing to Rust later via UniFFI.

### Option D: SwiftUI + Python Backend (via PythonKit or subprocess)

**Libraries:**
- PythonKit (pvieito/PythonKit) -- call Python from Swift at runtime
- subprocess via `Process` class -- shell out to Python scripts

**Pros:**
- Reuse your existing gedfix Python codebase directly
- PythonKit loads Python modules at runtime
- Access to Python ML/AI ecosystem (transformers, sentence-transformers, etc.)

**Cons:**
- PythonKit requires disabling Hardened Runtime (security concern)
- Embeds ~100MB Python interpreter in the app bundle
- App Store deployment is complex
- Runtime crashes on module import are common in release builds
- Performance overhead of Python FFI for core data operations
- subprocess approach is simpler but slower and harder to manage state

**Verdict:** Use subprocess for specific tasks (calling gedfix CLI for GEDCOM processing) but do not build the core app on PythonKit. Keep Python as an external tool, not an embedded runtime.

### Option E: Tauri (Rust + Web Frontend)

**Libraries:**
- Tauri 2.x (stable since late 2024)
- WRY -- cross-platform webview abstraction
- Frontend: React/Svelte/Vue + TypeScript

**Pros:**
- App size under 10MB (vs 100MB+ Electron)
- Memory ~30-40MB idle (vs hundreds for Electron)
- Startup under 0.5 seconds
- Rust backend for performance-critical operations
- Cross-platform (macOS, Windows, Linux, mobile)
- 35% year-over-year adoption increase

**Cons:**
- Uses system WebView (WebKit on macOS) -- some rendering differences vs Chromium
- Web frontend does not feel native on macOS (no native context menus, drag-drop, etc.)
- No access to macOS-native APIs without Rust bridge plugins
- Genealogy tree visualization may need custom WebGL/Canvas rendering

**Verdict:** Best cross-platform option if you want Windows/Linux simultaneously. But for a Mac-first genealogy app, SwiftUI provides a substantially more native experience.

### Option F: Wails (Go + Web Frontend)

**Libraries:**
- Wails v2.9+

**Pros:**
- Best IPC of any web-to-desktop framework -- Go structs auto-generate TypeScript bindings
- Lightweight like Tauri

**Cons:**
- Go is less suited than Rust for GEDCOM parsing performance
- Smaller ecosystem than Tauri
- Same non-native UI concerns as Tauri

**Verdict:** Only consider if you are a Go shop. Tauri is the better choice in this category.

### OVERALL RECOMMENDATION

**Primary:** SwiftUI + GRDB.swift + SQLite (Option A)
**Secondary consideration:** Extract GEDCOM parsing to Rust via UniFFI later (Option C hybrid)
**Python integration:** subprocess calls to gedfix CLI for batch processing (Option D subprocess only)

---

## 2. Database Architecture

### 2.1 SQLite Schema Design for Genealogy

Based on the GEDCOM 7.0 specification, established genealogy software schemas (RootsMagic, MacFamilyTree, Charting Companion), and the FamilySearch data model:

```sql
-- Core entity tables
CREATE TABLE persons (
    id INTEGER PRIMARY KEY,
    gedcom_id TEXT UNIQUE,          -- INDI @I1@
    given_name TEXT,
    surname TEXT,
    suffix TEXT,
    sex TEXT CHECK(sex IN ('M','F','U','X')),
    is_living INTEGER DEFAULT 1,    -- privacy flag
    privacy_level INTEGER DEFAULT 0, -- 0=public, 1=restricted, 2=private
    notes TEXT,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE families (
    id INTEGER PRIMARY KEY,
    gedcom_id TEXT UNIQUE,          -- FAM @F1@
    husband_id INTEGER REFERENCES persons(id),
    wife_id INTEGER REFERENCES persons(id),
    notes TEXT,
    created_at TEXT DEFAULT (datetime('now')),
    updated_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE family_children (
    family_id INTEGER REFERENCES families(id),
    person_id INTEGER REFERENCES persons(id),
    child_order INTEGER,
    pedigree TEXT,                   -- birth, adopted, foster, sealing
    PRIMARY KEY (family_id, person_id)
);

-- Event system (covers birth, death, marriage, census, etc.)
CREATE TABLE events (
    id INTEGER PRIMARY KEY,
    event_type TEXT NOT NULL,        -- BIRT, DEAT, MARR, CENS, BURI, etc.
    person_id INTEGER REFERENCES persons(id),
    family_id INTEGER REFERENCES families(id),
    date_value TEXT,                 -- raw GEDCOM date string
    date_sortable TEXT,              -- normalized ISO for sorting
    date_calendar TEXT DEFAULT 'GREGORIAN',
    place_id INTEGER REFERENCES places(id),
    description TEXT,
    notes TEXT,
    created_at TEXT DEFAULT (datetime('now'))
);

-- Place hierarchy (GEDCOM 7 enhancement over 5.5.1)
CREATE TABLE places (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,              -- full place string
    normalized_name TEXT,            -- standardized for matching
    latitude REAL,
    longitude REAL,
    parent_place_id INTEGER REFERENCES places(id),
    place_type TEXT,                 -- country, state, county, city, etc.
    created_at TEXT DEFAULT (datetime('now'))
);

-- Source and citation system
CREATE TABLE sources (
    id INTEGER PRIMARY KEY,
    gedcom_id TEXT UNIQUE,
    title TEXT,
    author TEXT,
    publisher TEXT,
    repository_id INTEGER REFERENCES repositories(id),
    notes TEXT,
    created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE citations (
    id INTEGER PRIMARY KEY,
    source_id INTEGER REFERENCES sources(id),
    page TEXT,                       -- "page 42, entry 7"
    quality INTEGER CHECK(quality BETWEEN 0 AND 3),
    notes TEXT
);

-- Link citations to any entity
CREATE TABLE citation_links (
    citation_id INTEGER REFERENCES citations(id),
    entity_type TEXT NOT NULL,       -- 'person', 'event', 'family'
    entity_id INTEGER NOT NULL,
    PRIMARY KEY (citation_id, entity_type, entity_id)
);

CREATE TABLE repositories (
    id INTEGER PRIMARY KEY,
    name TEXT,
    address TEXT,
    url TEXT
);

-- Media/multimedia
CREATE TABLE media (
    id INTEGER PRIMARY KEY,
    file_path TEXT,                  -- relative path within app bundle/documents
    mime_type TEXT,
    title TEXT,
    description TEXT,
    hash TEXT,                       -- SHA-256 for dedup
    created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE media_links (
    media_id INTEGER REFERENCES media(id),
    entity_type TEXT NOT NULL,
    entity_id INTEGER NOT NULL,
    PRIMARY KEY (media_id, entity_type, entity_id)
);

-- Notes (shared, referenceable)
CREATE TABLE notes (
    id INTEGER PRIMARY KEY,
    gedcom_id TEXT UNIQUE,
    content TEXT,
    mime_type TEXT DEFAULT 'text/plain', -- GEDCOM 7 supports text/html
    created_at TEXT DEFAULT (datetime('now'))
);

CREATE TABLE note_links (
    note_id INTEGER REFERENCES notes(id),
    entity_type TEXT NOT NULL,
    entity_id INTEGER NOT NULL,
    PRIMARY KEY (note_id, entity_type, entity_id)
);

-- AI/research metadata
CREATE TABLE ai_suggestions (
    id INTEGER PRIMARY KEY,
    entity_type TEXT NOT NULL,
    entity_id INTEGER NOT NULL,
    suggestion_type TEXT,            -- 'merge', 'correction', 'hint', 'transcription'
    confidence REAL,
    payload TEXT,                    -- JSON
    status TEXT DEFAULT 'pending',   -- pending, accepted, rejected
    model TEXT,                      -- 'claude-4', 'local-mlx', etc.
    created_at TEXT DEFAULT (datetime('now'))
);

-- Version history for undo/redo
CREATE TABLE change_log (
    id INTEGER PRIMARY KEY,
    entity_type TEXT NOT NULL,
    entity_id INTEGER NOT NULL,
    operation TEXT NOT NULL,          -- INSERT, UPDATE, DELETE
    old_values TEXT,                  -- JSON snapshot
    new_values TEXT,                  -- JSON snapshot
    change_group_id TEXT,            -- groups related changes for undo
    user_note TEXT,
    created_at TEXT DEFAULT (datetime('now'))
);
```

### 2.2 Core Data vs Raw SQLite

| Factor | Core Data / SwiftData | GRDB + Raw SQLite |
|--------|----------------------|-------------------|
| Complex joins (person -> events -> places -> sources) | Awkward, N+1 query risk | Native SQL joins |
| Recursive queries (ancestors to Nth generation) | Not supported natively | WITH RECURSIVE CTE |
| Fuzzy name matching | Not available | Custom SQL functions, FTS5 |
| Full-text search | Not available | FTS5 with custom tokenizers |
| Vector search | Not available | sqlite-vec extension |
| Encryption at rest | FileProtection only | SQLCipher (AES-256) |
| Schema migrations | Automatic but opaque | Manual but transparent |
| iCloud sync | Built-in CloudKit | SharingGRDB/SQLiteData adds CloudKit |
| Performance | Slowest | Fastest |
| File portability | Opaque format | Standard .sqlite, shareable |

**Recommendation:** GRDB + raw SQLite. Genealogy data is fundamentally relational and query-heavy.

### 2.3 GEDCOM Import/Export Layer

Architecture as a bidirectional adapter:

```
GEDCOM 5.5.1 File  <-->  GEDCOMParser  <-->  SQLite Database
GEDCOM 7.0 File    <-->  GEDCOMParser  <-->  SQLite Database
```

**Import strategy:**
1. Parse GEDCOM into an intermediate representation (IR) -- array of typed records
2. Validate IR against GEDCOM spec (reuse gedfix validation logic via subprocess)
3. Map IR to database entities in a single transaction
4. Run post-import integrity checks (orphan detection, circular references)
5. Generate import report with statistics

**Export strategy:**
1. Query database with privacy filters applied (living person filtering)
2. Generate GEDCOM records with proper cross-references
3. Support both GEDCOM 5.5.1 (compatibility) and 7.0 (modern) output
4. GEDCOM 7.0 ZIP packaging with embedded media

**Swift libraries for GEDCOM parsing:**
- No mature Swift GEDCOM library exists. Best approach: write a Swift GEDCOM parser or bridge to gedfix Python via subprocess for import, and write native Swift export.
- Alternative: Use Rust GEDCOM crate (`gedcom` 1.4.1) via UniFFI

### 2.4 Full-Text Search (FTS5)

GRDB provides first-class FTS5 support:

```swift
// Schema setup
try db.create(virtualTable: "persons_fts", using: FTS5()) { t in
    t.tokenizer = .unicode61()  // handles diacritics
    t.column("given_name")
    t.column("surname")
    t.content = "persons"       // content table sync
    t.contentRowID = "id"
}

try db.create(virtualTable: "places_fts", using: FTS5()) { t in
    t.tokenizer = .unicode61(removeDiacritics: false)
    t.column("name")
    t.column("normalized_name")
    t.content = "places"
    t.contentRowID = "id"
}

try db.create(virtualTable: "notes_fts", using: FTS5()) { t in
    t.tokenizer = .porter()     // stemming for notes
    t.column("content")
    t.content = "notes"
    t.contentRowID = "id"
}
```

Custom tokenizer for genealogy name variants (handles "Mc" / "Mac", "burg" / "burgh", etc.) can be written using GRDB's FTS5 custom tokenizer API.

### 2.5 Versioning and Undo/Redo

**Approach: Change Log with Group IDs**

Rather than CRDTs (overkill for a single-user desktop app), use a change log table:

1. Every mutation writes to `change_log` with old/new JSON snapshots
2. Related changes share a `change_group_id` (UUID)
3. Undo replays the group in reverse
4. Redo replays the group forward
5. SQLite triggers can automate change capture:

```sql
CREATE TRIGGER persons_update_log AFTER UPDATE ON persons
BEGIN
    INSERT INTO change_log (entity_type, entity_id, operation, old_values, new_values, change_group_id)
    VALUES ('person', OLD.id, 'UPDATE',
            json_object('given_name', OLD.given_name, 'surname', OLD.surname, ...),
            json_object('given_name', NEW.given_name, 'surname', NEW.surname, ...),
            NULL);  -- set by application before mutation batch
END;
```

**Undo stack in Swift:**
```swift
class UndoManager {
    private var undoStack: [String] = []  // change_group_ids
    private var redoStack: [String] = []

    func undo(db: Database) throws {
        guard let groupId = undoStack.popLast() else { return }
        // Replay changes in reverse from change_log
        redoStack.append(groupId)
    }
}
```

---

## 3. AI Integration Architecture

### 3.1 Local LLM Integration

**MLX (Apple's framework -- RECOMMENDED for Apple Silicon):**
- MLX was purpose-built for Apple Silicon unified memory architecture
- Zero memory copies between CPU and GPU
- WWDC 2025 elevated MLX to the official framework for custom LLM integration
- Performance: ~40 tokens/sec for 7B models on M2 16GB; higher on M3/M4/M5
- Use MLX-LM for running/fine-tuning models locally
- Library: `mlx-lm` (Python), or `mlx-swift` for native Swift integration

**llama.cpp (cross-platform alternative):**
- Metal GPU acceleration: 50-100+ tokens/sec on M3/M4
- Broad model support (GGUF format)
- Can be compiled as a C library and called from Swift via C interop
- Library: llama.cpp (ggml-org/llama.cpp), llamafile for single-binary distribution

**Recommended local models for genealogy tasks:**
| Task | Model | Size | Notes |
|------|-------|------|-------|
| Record transcription | Qwen 2.5 7B | 4GB | Good at OCR post-processing |
| Relationship inference | Mistral 7B | 4GB | Strong reasoning |
| Date parsing/normalization | Phi-3.5 Mini 3.8B | 2GB | Fast, good at structured output |
| General assistant | Llama 3.3 8B | 5GB | Best general-purpose local |
| Summarization | Gemma 2 9B | 6GB | Good for source summaries |

**Integration architecture:**
```
SwiftUI App
    |
    +-- MLXService (local inference)
    |       Uses mlx-swift or shells to mlx_lm.generate
    |       For: transcription, date parsing, name normalization
    |
    +-- LlamaCppService (alternative local inference)
    |       C interop via llama.h
    |       For: when MLX models unavailable
    |
    +-- CloudAIService (remote inference)
            HTTP client to Claude/GPT/Gemini APIs
            For: complex reasoning, large context analysis
```

### 3.2 Cloud API Integration

**Providers and current models (March 2026):**
| Provider | Model | Best For | Pricing Tier |
|----------|-------|----------|-------------|
| Anthropic | Claude Opus 4 | Complex genealogical reasoning, source analysis | Premium |
| Anthropic | Claude Sonnet 4 | General tasks, good cost/quality | Standard |
| OpenAI | GPT-5 | Broad knowledge, good at historical context | Premium |
| Google | Gemini 2.5 Pro | Multimodal (photo analysis, document OCR) | Standard |
| Google | Gemini 2.5 Flash | Fast, cheap structured output | Budget |

**Xcode 26 / Foundation Models API:**
Apple's new Foundation Models API (WWDC 2025) allows native integration with on-device and cloud models. This provides a unified interface that can route between Apple Intelligence on-device models and third-party cloud APIs (Claude, GPT, Gemini).

### 3.3 Multi-API-Key Management and Rotation

```swift
struct APIKeyManager {
    struct ProviderConfig {
        let provider: String          // "anthropic", "openai", "google"
        var keys: [APIKey]
        var activeKeyIndex: Int = 0
        var circuitBreakerCooldown: Date?
    }

    struct APIKey {
        let keyReference: String      // "op://Research/Claude-API/credential"
        var requestCount: Int = 0
        var lastUsed: Date?
        var isExhausted: Bool = false
        var cooldownUntil: Date?
    }

    // Round-robin with circuit breaking
    func nextKey(for provider: String) -> APIKey? {
        guard var config = providers[provider] else { return nil }
        // Skip exhausted/cooling keys
        // Rotate to next available
        // If all exhausted, return nil (trigger fallback to local model)
    }
}
```

**Key rotation features:**
- Round-robin cycling across multiple keys per provider
- Automatic cooldown on 429 (rate limit) responses
- Circuit breaker: if a key fails 3x in 5 minutes, cool down for 15 minutes
- Fallback chain: Claude -> GPT -> Gemini -> local model
- Usage tracking per key for cost monitoring

### 3.4 AI Features for Genealogy

| Feature | Local vs Cloud | Implementation |
|---------|---------------|----------------|
| Record transcription (OCR post-processing) | Local (MLX) | Image -> Apple Vision OCR -> LLM cleanup |
| Handwriting recognition | Cloud (Gemini multimodal) | Send image to Gemini 2.5 Pro |
| Relationship inference | Local or Cloud | Given facts, infer missing connections |
| Source analysis & quality scoring | Cloud (Claude) | Analyze source reliability, identify conflicts |
| Smart hints ("Did you know?") | Cloud (Claude) | Compare tree against public databases |
| Date normalization | Local (small model) | Parse "abt. 1842" -> structured date |
| Name variant generation | Local | Generate spelling variants for search |
| Obituary/record summarization | Local or Cloud | Extract key facts from text |
| Duplicate detection | Local (embeddings) | Embed person records, cosine similarity |
| Research assistant chat | Cloud (Claude) | Conversational genealogy helper |

### 3.5 Embedding-Based Semantic Search

**Architecture:**

```
Person/Event/Source Record
        |
        v
    Embedding Model (local)
        |
        v
    sqlite-vec table (stored alongside main data)
        |
        v
    KNN search for similar records
```

**Libraries:**
- `sqlite-vec` 0.1.6+ -- vector search SQLite extension, runs everywhere including macOS
- Uses only ~30MB memory, supports SIMD acceleration
- Embedding models: `all-MiniLM-L6-v2` (384 dims, fast) or `nomic-embed-text` (768 dims, better quality)
- Run embedding model locally via MLX or sentence-transformers (Python subprocess)

```sql
-- Vector storage
CREATE VIRTUAL TABLE person_embeddings USING vec0(
    person_id INTEGER PRIMARY KEY,
    embedding float[384]
);

-- Find similar persons
SELECT p.*, distance
FROM person_embeddings AS pe
JOIN persons AS p ON p.id = pe.person_id
WHERE pe.embedding MATCH ?  -- query vector
ORDER BY distance
LIMIT 10;
```

### 3.6 Privacy: Local vs Cloud Data Boundaries

**MUST stay local (never sent to cloud APIs):**
- Living person details (names, dates, addresses)
- DNA/genetic data
- Medical information
- Social Security numbers or government IDs
- Private family notes marked as sensitive
- Media files of living persons

**Can go to cloud (with user consent):**
- Deceased person records (names, dates, places)
- Historical source text
- OCR'd document images (with PII redacted)
- Place names and geographical queries
- General genealogical questions

**Implementation:**
```swift
protocol AIService {
    func isDataSafeForCloud(_ data: GenealogyData) -> Bool
}

extension AIService {
    func isDataSafeForCloud(_ data: GenealogyData) -> Bool {
        // Check: no living persons referenced
        // Check: no PII fields populated
        // Check: user has granted cloud consent
        return !data.containsLivingPersons
            && !data.containsPII
            && UserPreferences.cloudAIConsent
    }
}
```

---

## 4. Secure API Key Storage

### 4.1 macOS Keychain (RECOMMENDED for app-stored keys)

**Libraries:**
- `KeychainAccess` 4.2.2+ (kishikawakatsumi) -- Swift wrapper, SPM compatible
- `keychain-swift` 24.0+ (evgenyneu) -- lighter alternative
- Native `Security.framework` -- no dependency, more verbose

**Implementation pattern:**
```swift
import KeychainAccess

let keychain = Keychain(service: "com.yourapp.genealogy")
    .accessibility(.whenUnlocked)
    .authenticationPolicy(.biometryAny)  // Require Touch ID/Face ID

// Store
try keychain.set(apiKey, key: "anthropic-api-key")

// Retrieve
let key = try keychain.get("anthropic-api-key")

// With biometric prompt
let key = try keychain
    .authenticationPrompt("Authenticate to access AI services")
    .get("anthropic-api-key")
```

**Security features:**
- Encrypted by Secure Enclave hardware
- Biometric (Touch ID/Face ID) gating
- `kSecAttrAccessibleWhenUnlocked` -- only accessible when device unlocked
- iCloud Keychain sync (optional, disable for API keys)
- Per-app isolation via `kSecAttrAccessGroup`

### 4.2 1Password CLI Integration

For development and power users who use 1Password:

```swift
// Shell out to op CLI
func getSecretFrom1Password(vault: String, item: String, field: String) -> String? {
    let process = Process()
    process.executableURL = URL(fileURLWithPath: "/opt/homebrew/bin/op")
    process.arguments = ["read", "op://\(vault)/\(item)/\(field)"]

    let pipe = Pipe()
    process.standardOutput = pipe
    try? process.run()
    process.waitUntilExit()

    let data = pipe.fileHandleForReading.readDataToEndOfFile()
    return String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines)
}

// Usage
let claudeKey = getSecretFrom1Password(vault: "Research", item: "Claude-API", field: "credential")
```

**1Password Connect (for server-side/automation):**
Per your existing infrastructure at `http://100.67.89.29:8100`, use the Connect REST API for automated workflows.

### 4.3 Best Practice: Layered Approach

```
Priority 1: macOS Keychain (primary, in-app)
    |
    v
Priority 2: 1Password CLI (developer/power-user fallback)
    |
    v
Priority 3: Environment variables (CI/CD only, never in production app)
```

**For multiple API keys:**
- Store each key in Keychain with a structured key name: `ai.anthropic.key.1`, `ai.anthropic.key.2`, etc.
- Store metadata (usage count, last rotation date) in the main SQLite database
- Rotate keys monthly; Keychain makes this seamless

---

## 5. Playwright Scraping Architecture

### 5.1 Architecture Overview

```
┌─────────────────────────────────────────────┐
│               Scraping Orchestrator         │
│  (rate limiter, queue, session manager)     │
└──────┬──────────────────────────────────────┘
       │
       ├── SiteAdapter: Ancestry
       │     ├── auth handler (cookie-based login)
       │     ├── search page scraper
       │     ├── record detail extractor
       │     └── rate config: 3 req/min
       │
       ├── SiteAdapter: FamilySearch
       │     ├── auth handler (OAuth/session)
       │     ├── tree navigator (SPA handling)
       │     ├── record extractor
       │     └── rate config: 5 req/min
       │
       ├── SiteAdapter: FindAGrave
       │     ├── no auth (public)
       │     ├── memorial page extractor
       │     └── rate config: 10 req/min
       │
       └── SiteAdapter: MyHeritage
             ├── auth handler
             ├── smart match extractor
             └── rate config: 3 req/min
```

### 5.2 Rate Limiting and Politeness

```python
# Per-domain configuration
SITE_CONFIGS = {
    "ancestry.com": {
        "requests_per_minute": 3,
        "min_delay_seconds": 15,
        "max_delay_seconds": 45,
        "concurrent_pages": 1,
        "respect_robots_txt": True,
        "session_duration_minutes": 30,
        "cooldown_between_sessions_minutes": 60,
    },
    "familysearch.org": {
        "requests_per_minute": 5,
        "min_delay_seconds": 8,
        "max_delay_seconds": 25,
        "concurrent_pages": 1,
        "respect_robots_txt": True,
        "session_duration_minutes": 45,
        "cooldown_between_sessions_minutes": 30,
    },
    "findagrave.com": {
        "requests_per_minute": 10,
        "min_delay_seconds": 4,
        "max_delay_seconds": 15,
        "concurrent_pages": 2,
        "respect_robots_txt": True,
        "session_duration_minutes": 60,
        "cooldown_between_sessions_minutes": 15,
    },
}
```

**Politeness principles:**
- Randomized delays between requests (human-like pacing)
- Session duration limits to avoid long scraping sessions
- Mandatory cooldowns between sessions
- Global concurrency limit (never more than 1 browser per site)
- Respect `robots.txt` directives
- Identify as a legitimate user agent (no spoofing Googlebot)

### 5.3 Anti-Detection Strategy

**Libraries:**
- `playwright-extra` + `playwright-stealth` plugin -- patches browser fingerprint
- `playwright` 1.50+ (core browser automation)

**Key techniques:**
1. Disable `navigator.webdriver` flag
2. Match user agent to actual platform (macOS Safari or Chrome)
3. Match locale to timezone
4. Randomize viewport size within realistic ranges
5. Simulate human scroll patterns and mouse movements
6. Use `browser.newContext()` for isolation (multiple contexts, single browser)
7. Persistent cookies via `storageState` save/load

```python
from playwright.sync_api import sync_playwright
from playwright_stealth import stealth_sync

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)  # headed for auth
    context = browser.new_context(
        viewport={"width": 1440, "height": 900},
        locale="en-US",
        timezone_id="America/Chicago",
        storage_state="ancestry_session.json"  # persistent auth
    )
    page = context.new_page()
    stealth_sync(page)
    # ... scraping logic
    context.storage_state(path="ancestry_session.json")  # save session
```

### 5.4 Legal Considerations

**CFAA (Computer Fraud and Abuse Act):**
- hiQ v. LinkedIn (9th Circuit): scraping publicly accessible pages is NOT "unauthorized access"
- Meta v. Bright Data (2024): scraping behind contractual restrictions (login walls) MAY breach ToS
- Key distinction: public pages vs. authenticated/login-required content

**Terms of Service:**
- Ancestry.com ToS explicitly prohibits automated data collection
- FamilySearch ToS prohibits scraping but provides an official API (FamilySearch API)
- FindAGrave (owned by Ancestry) has similar restrictions
- MyHeritage prohibits automated access

**Recommended approach:**
1. **Use official APIs first** -- FamilySearch has a free API; use it
2. **Personal use only** -- scraping your own paid account data for personal backup is lower risk
3. **Never redistribute** scraped data
4. **Rate limit aggressively** -- if you scrape, be indistinguishable from normal browsing
5. **Document consent** -- only scrape data you have a right to access
6. **Consider legal counsel** before any large-scale scraping

**robots.txt compliance:**
- Not legally binding in most jurisdictions, but ignoring it undermines "good faith" defense
- In Texas, ignoring robots.txt may support DMCA claims
- Always check and respect robots.txt as a baseline

### 5.5 Data Extraction Patterns

**GenScrape** (rootsdev/genscrape) is an existing open-source library for genealogy site extraction:
- Supports Ancestry.com, FamilySearch.org, FindMyPast
- Returns data in GEDCOM X JSON format
- Handles SPA navigation (FamilySearch tree)
- Emits `data` events as user browses

**Pattern for each site adapter:**
```python
class SiteAdapter:
    async def authenticate(self, page, credentials):
        """Login and establish session"""

    async def search_person(self, page, name, birth_year, place):
        """Search for a person, return list of matches"""

    async def extract_record(self, page, url):
        """Extract structured data from a record page"""
        return {
            "source_site": "ancestry.com",
            "source_url": url,
            "persons": [...],
            "events": [...],
            "sources": [...],
            "raw_html": "...",
            "extracted_at": datetime.now().isoformat(),
        }

    async def extract_tree_person(self, page, person_url):
        """Extract person details from a family tree"""
```

### 5.6 Headless vs Headed Mode

| Mode | Use Case |
|------|----------|
| Headed (visible browser) | Initial authentication, CAPTCHA solving, debugging |
| Headless | Batch extraction after auth is established |
| Semi-headless | Authentication headed, then switch to headless |

**Recommended:** Start headed for authentication (let user log in manually), save session state, then use headless for subsequent data extraction.

### 5.7 Proxy Rotation

**When needed:** Only if IP-based rate limiting is encountered after aggressive politeness measures fail.

**Options:**
- BrightData (per your existing Scraping skill) -- residential proxies, CAPTCHA solving
- ScraperAPI -- proxy + anti-detection bundled
- Self-hosted via Tailscale exit nodes (your infrastructure supports this)

**When NOT needed:** For personal-use scraping at polite rates (3-10 req/min), proxy rotation is unnecessary and adds complexity.

---

## 6. Modular Plugin System

### 6.1 Gramps Plugin Architecture (Study)

Gramps uses a well-proven Python plugin system with 20+ years of development:

**Plugin Registration:**
1. Each plugin has a `.gpr.py` registration file and a main `.py` file
2. `PluginRegister` scans directories for `.gpr.py` files
3. Registration files declare metadata: type, name, version, description, author
4. Plugin directories are added to `PYTHONPATH` automatically

**Plugin Categories (12 types):**
| Type Constant | Description |
|--------------|-------------|
| `REPORT` | Output generation (PDF, HTML, text reports) |
| `TOOL` | Data processing utilities |
| `IMPORT` | File format importers (GEDCOM, CSV, etc.) |
| `EXPORT` | File format exporters |
| `QUICKREPORT` | Right-click mini-reports |
| `GRAMPLET` | Dashboard widgets |
| `VIEW` | Full UI panels |
| `DOCGEN` | Document generators |
| `MAPSERVICE` | Map providers |
| `DATABASE` | Database backends |
| `RULE` | Filter rules |
| `GENERAL` | General purpose |

**Plugin Lifecycle:**
1. Discovery: scan directories for `.gpr.py` files
2. Registration: parse metadata, store in `PluginRegister`
3. Lazy loading: plugin code loaded only when first needed
4. Execution: plugin manager creates instance, runs it
5. Cleanup: plugin resources released

### 6.2 Recommended Plugin Architecture for Swift/macOS

```swift
// Plugin Protocol
protocol GenealogyPlugin {
    static var metadata: PluginMetadata { get }
    func activate(context: PluginContext)
    func deactivate()
}

struct PluginMetadata {
    let id: String                    // "com.example.ancestry-import"
    let name: String
    let version: String               // semver
    let category: PluginCategory
    let description: String
    let author: String
    let minimumAppVersion: String
}

enum PluginCategory {
    case importer           // GEDCOM, CSV, Ancestry export
    case exporter           // GEDCOM, PDF, HTML
    case siteAdapter        // Ancestry, FamilySearch, etc.
    case aiProvider         // Claude, GPT, local model
    case report             // Charts, family group sheets
    case tool               // Data cleanup, merge, validate
    case visualization      // Tree views, maps, timelines
    case widget             // Dashboard components
}

// Plugin Context provides access to app services
struct PluginContext {
    let database: DatabaseAccess
    let aiService: AIServiceAccess
    let keychain: KeychainAccess
    let preferences: PreferencesAccess
    let logger: Logger
}

// Service Adapter Protocol (for genealogy sites)
protocol GenealogyServiceAdapter: GenealogyPlugin {
    func search(query: PersonQuery) async throws -> [SearchResult]
    func fetchRecord(id: String) async throws -> GenealogyRecord
    func authenticate(credentials: Credentials) async throws -> Session
    var supportedRecordTypes: [RecordType] { get }
}
```

### 6.3 Swift Package Manager for Plugin Distribution

```swift
// Package.swift for a plugin
let package = Package(
    name: "AncestryAdapter",
    platforms: [.macOS(.v14)],
    products: [
        .library(name: "AncestryAdapter", type: .dynamic, targets: ["AncestryAdapter"]),
    ],
    dependencies: [
        .package(url: "https://github.com/you/GenealogyPluginSDK", from: "1.0.0"),
    ],
    targets: [
        .target(name: "AncestryAdapter", dependencies: ["GenealogyPluginSDK"]),
    ]
)
```

**Plugin loading at runtime:**
- Use `Bundle.load()` to load dynamic frameworks at runtime
- Scan `~/Library/Application Support/YourApp/Plugins/` for `.bundle` files
- Each bundle exports a class conforming to `GenealogyPlugin`
- Sandboxing: plugins run in a restricted context with explicit capability grants

### 6.4 Hybrid Approach: Swift Plugins + Python Scripts

Given your existing gedfix Python codebase:

```
Plugins/
├── swift/              # Native Swift plugins (.bundle)
│   ├── AncestryAdapter.bundle
│   └── FamilySearchAdapter.bundle
├── python/             # Python script plugins
│   ├── gedcom_fixer/   # Wraps gedfix
│   └── name_normalizer/
└── plugin-manifest.json
```

Python plugins are invoked via subprocess with JSON I/O:
```swift
func runPythonPlugin(script: String, input: [String: Any]) async throws -> [String: Any] {
    let process = Process()
    process.executableURL = URL(fileURLWithPath: "/usr/bin/python3")
    process.arguments = [script, "--json"]
    // Write input JSON to stdin, read output JSON from stdout
}
```

---

## 7. Sync/Merge/Conflict Resolution

### 7.1 Tree Merging Algorithms

**Phase 1: Person Matching**
```
For each person in Tree B:
    1. Search Tree A using weighted scoring:
       - Name similarity (Levenshtein/Jaro-Winkler): 40%
       - Birth date proximity: 20%
       - Birth place match: 15%
       - Death date proximity: 10%
       - Parent names match: 15%
    2. Score > threshold (e.g., 85%): auto-match candidate
    3. Score 60-85%: present for manual review
    4. Score < 60%: treat as new person
```

**Phase 2: Graph Walking**
Once anchor matches are established:
1. Walk up/down family links from matched persons
2. Match relatives by position + attributes
3. Propagate confidence scores through the graph

**Phase 3: Conflict Resolution**
When matched persons have conflicting facts:
```swift
enum ConflictResolution {
    case keepLocal          // Trust existing data
    case keepRemote         // Trust incoming data
    case keepBoth           // Store both with source attribution
    case manual             // Flag for user review
    case merge              // Combine (e.g., merge notes)
}

struct MergeConflict {
    let field: String       // "birth_date", "given_name", etc.
    let localValue: String
    let remoteValue: String
    let localSource: Source?
    let remoteSource: Source?
    var resolution: ConflictResolution = .manual
}
```

### 7.2 Conflict Detection and Resolution UX

**Three-pane merge view:**
```
┌─────────────┬─────────────┬─────────────┐
│  Your Tree  │   Merged    │ Other Tree  │
│  (local)    │  (result)   │  (remote)   │
├─────────────┼─────────────┼─────────────┤
│ John Smith  │ John Smith  │ John H Smith│
│ b. 1842     │ b. abt 1842 │ b. abt 1842│
│ d. 1910     │ d. 1910     │ d. ?       │
│             │ [CONFLICT]  │             │
│ Springfield │ Springfield │ Springfield │
│ IL          │ IL          │ Illinois    │
└─────────────┴─────────────┴─────────────┘
```

**UX principles:**
- Side-by-side comparison for every conflicting field
- One-click "keep left" / "keep right" / "keep both"
- Batch operations: "Accept all from source X for this person"
- Undo support for every merge decision
- Preview mode before committing merge

### 7.3 Version Control for Genealogy Data

**Recommendation: Change Log (not CRDT, not OT)**

CRDTs and OT are designed for real-time multi-user collaboration. A genealogy desktop app is primarily single-user with occasional import/export. The correct model is:

1. **Change log** (Section 2.5 above) for local undo/redo
2. **Snapshot-based versioning** for major milestones:
   ```sql
   CREATE TABLE snapshots (
       id INTEGER PRIMARY KEY,
       name TEXT,              -- "Before Ancestry merge", "v2.1"
       description TEXT,
       created_at TEXT,
       database_hash TEXT      -- SHA-256 of entire DB at snapshot time
   );
   ```
3. **SQLite backup** for each snapshot (copy the .sqlite file)
4. **Export to GEDCOM** as a portable version checkpoint

**If real-time collaboration is needed later:**
- Consider Automerge (a mature CRDT library with Swift bindings)
- Library: `automerge-swift` 0.5+ -- Swift bindings for the Automerge CRDT library
- Would require significant schema rethinking

### 7.4 Sync with Cloud Services

**FamilySearch API** (official, free):
- REST API with OAuth 2.0
- Read/write access to the shared FamilySearch tree
- Conflict resolution is server-side (FamilySearch manages the canonical tree)
- Rate limited but generous for personal use

**iCloud Sync (via SharingGRDB/SQLiteData):**
- SharingGRDB now supports CloudKit sync as of 2026
- Sync your local SQLite database across your own Mac/iPhone/iPad
- Not for sharing with other users

---

## 8. Privacy and Encryption

### 8.1 At-Rest Encryption for Local Database

**SQLCipher (RECOMMENDED):**
- Library: `SQLCipher.swift` (sqlcipher/SQLCipher.swift) -- official Swift Package
- Transparent 256-bit AES encryption of entire database file
- Compatible with GRDB.swift (GRDB supports SQLCipher as a backend)
- Zero code changes to queries -- encryption is transparent
- Key derived from user's password + macOS Keychain storage

```swift
// GRDB + SQLCipher setup
var config = Configuration()
config.prepareDatabase { db in
    try db.usePassphrase("encryption-key-from-keychain")
}
let dbPool = try DatabasePool(path: dbPath, configuration: config)
```

**Alternative: macOS FileVault + File Protection**
- If the Mac has FileVault enabled, the entire disk is encrypted
- Additional file-level protection via `NSFileProtectionComplete`
- Less control but zero dependency

### 8.2 Field-Level Encryption for Sensitive Data

SQLCipher encrypts the entire database. For field-level encryption of specific columns (e.g., living person details visible only with biometric auth):

```swift
import CryptoKit

struct FieldEncryptor {
    private let key: SymmetricKey  // Derived from Keychain-stored key

    func encrypt(_ plaintext: String) throws -> Data {
        let data = Data(plaintext.utf8)
        let sealed = try AES.GCM.seal(data, using: key)
        return sealed.combined!
    }

    func decrypt(_ ciphertext: Data) throws -> String {
        let box = try AES.GCM.SealedBox(combined: ciphertext)
        let data = try AES.GCM.open(box, using: key)
        return String(data: data, encoding: .utf8)!
    }
}
```

**Which fields to encrypt at field level:**
- Living person addresses
- Phone numbers / email
- Government IDs (SSN)
- DNA test results
- Medical notes
- Financial information

### 8.3 Secure Export/Sharing

**GEDCOM export with privacy filtering:**
```swift
func exportGEDCOM(db: Database, options: ExportOptions) -> String {
    var persons = try Person.fetchAll(db)

    if options.privacyFilter == .excludeLiving {
        persons = persons.filter { !$0.isLiving }
    } else if options.privacyFilter == .redactLiving {
        persons = persons.map { person in
            guard person.isLiving else { return person }
            var redacted = person
            redacted.givenName = "Living"
            redacted.surname = person.surname  // keep surname for context
            redacted.birthDate = nil
            redacted.birthPlace = nil
            // Remove all events, notes, media for living
            return redacted
        }
    }
    // Generate GEDCOM from filtered/redacted set
}

enum PrivacyFilter {
    case none              // Full export (personal backup only)
    case redactLiving      // Replace living person details with "Living"
    case excludeLiving     // Omit living persons entirely
    case deceased110Years  // Treat anyone born < 110 years ago as potentially living
}
```

### 8.4 GDPR Compliance for European Relatives

**Requirements applicable to genealogy apps:**
1. **Right to erasure (Art. 17):** Must be able to delete all data about a specific person on request
2. **Data minimization (Art. 5):** Only collect data necessary for genealogy purposes
3. **Consent (Art. 6):** Document consent for storing living person data
4. **Data portability (Art. 20):** GEDCOM export satisfies this
5. **Encryption:** Not explicitly required, but strongly recommended as a "technical measure"

**Implementation:**
```swift
// GDPR data management
func deletePersonCompletely(personId: Int, db: Database) throws {
    // Delete in dependency order
    try db.execute(sql: "DELETE FROM media_links WHERE entity_type='person' AND entity_id=?", arguments: [personId])
    try db.execute(sql: "DELETE FROM citation_links WHERE entity_type='person' AND entity_id=?", arguments: [personId])
    try db.execute(sql: "DELETE FROM note_links WHERE entity_type='person' AND entity_id=?", arguments: [personId])
    try db.execute(sql: "DELETE FROM events WHERE person_id=?", arguments: [personId])
    try db.execute(sql: "DELETE FROM ai_suggestions WHERE entity_type='person' AND entity_id=?", arguments: [personId])
    try db.execute(sql: "DELETE FROM change_log WHERE entity_type='person' AND entity_id=?", arguments: [personId])
    try db.execute(sql: "DELETE FROM person_embeddings WHERE person_id=?", arguments: [personId])
    try db.execute(sql: "DELETE FROM persons WHERE id=?", arguments: [personId])
    // Also remove from any snapshots/backups (document this as a limitation)
}
```

### 8.5 Living Person Detection

```swift
struct LivingPersonDetector {
    let currentYear = Calendar.current.component(.year, from: Date())
    let presumedMaxAge = 110

    func isPresumablyLiving(_ person: Person) -> Bool {
        // Explicitly marked
        if person.isLiving { return true }

        // Has death date -> not living
        if person.deathDate != nil { return false }

        // Has burial/cremation event -> not living
        if person.events.contains(where: { $0.type == .burial || $0.type == .cremation }) {
            return false
        }

        // Birth date known and > 110 years ago -> probably not living
        if let birthYear = person.birthYear, currentYear - birthYear > presumedMaxAge {
            return false
        }

        // No death info and born within 110 years (or no birth date) -> assume living
        return true
    }
}
```

---

## 9. Testing and CI

### 9.1 XCTest for Swift

```swift
// Unit tests for core logic
import XCTest
@testable import GenealogyApp

final class PersonMatchingTests: XCTestCase {
    var db: DatabaseQueue!

    override func setUp() async throws {
        db = try DatabaseQueue()  // in-memory for tests
        try AppDatabase.migrate(db)
    }

    func testFuzzyNameMatch() throws {
        let score = NameMatcher.score("Johann", "John")
        XCTAssertGreaterThan(score, 0.7)
    }

    func testLivingPersonDetection() throws {
        let person = Person(givenName: "Test", birthYear: 1990, deathDate: nil)
        XCTAssertTrue(LivingPersonDetector().isPresumablyLiving(person))
    }

    func testGEDCOMImport() throws {
        let gedcom = """
        0 HEAD
        1 GEDC
        2 VERS 5.5.1
        0 @I1@ INDI
        1 NAME John /Smith/
        1 BIRT
        2 DATE 1 JAN 1842
        0 TRLR
        """
        let records = try GEDCOMParser.parse(gedcom)
        XCTAssertEqual(records.persons.count, 1)
        XCTAssertEqual(records.persons[0].surname, "Smith")
    }
}
```

### 9.2 pytest for Python Components

```python
# tests/test_gedcom_bridge.py
import pytest
import subprocess
import json

def test_gedfix_scan():
    """Test that gedfix scan produces valid JSON output"""
    result = subprocess.run(
        ["gedfix", "scan", "tests/fixtures/sample.ged", "--report", "/dev/stdout"],
        capture_output=True, text=True
    )
    assert result.returncode == 0
    report = json.loads(result.stdout)
    assert "persons" in report

def test_gedfix_check():
    """Test GEDCOM validation"""
    result = subprocess.run(
        ["gedfix", "check", "tests/fixtures/sample.ged", "--level", "standard"],
        capture_output=True, text=True
    )
    assert result.returncode == 0
```

### 9.3 Snapshot Testing for SwiftUI Views

**Library:** `swift-snapshot-testing` 1.17+ (pointfreeco/swift-snapshot-testing)

```swift
import SnapshotTesting
import SwiftUI
@testable import GenealogyApp

final class PersonDetailViewTests: XCTestCase {
    func testPersonDetailView() {
        let person = Person.fixture(name: "John Smith", birthYear: 1842)
        let view = PersonDetailView(person: person)
            .frame(width: 600, height: 800)

        assertSnapshot(of: view, as: .image(precision: 0.99))
    }

    func testFamilyTreeView() {
        let tree = FamilyTree.fixture(generations: 4)
        let view = FamilyTreeView(tree: tree)
            .frame(width: 1200, height: 800)

        assertSnapshot(of: view, as: .image(precision: 0.99))
    }

    func testLivingPersonRedacted() {
        let person = Person.fixture(name: "Living Person", isLiving: true)
        let view = PersonDetailView(person: person)
            .frame(width: 600, height: 800)
            .environment(\.privacyMode, .redacted)

        assertSnapshot(of: view, as: .image(precision: 0.99))
    }
}
```

**Also consider:** `SnapshotPreviews` (EmergeTools) -- generates snapshots automatically from Xcode SwiftUI previews.

### 9.4 CI with GitHub Actions for Mac Builds

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  swift-tests:
    runs-on: macos-15       # macOS 15 (Sequoia) runners
    steps:
      - uses: actions/checkout@v4

      - name: Select Xcode
        run: sudo xcode-select -s /Applications/Xcode_16.2.app

      - name: Resolve dependencies
        run: swift package resolve

      - name: Run unit tests
        run: swift test --parallel

      - name: Run snapshot tests
        run: swift test --filter SnapshotTests

      - name: Build release
        run: swift build -c release

  python-tests:
    runs-on: macos-15
    steps:
      - uses: actions/checkout@v4

      - name: Set up Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.11'

      - name: Install gedfix
        run: pip install -e .

      - name: Run pytest
        run: pytest tests/ -q --tb=short

  gedcom-validation:
    runs-on: macos-15
    needs: [swift-tests, python-tests]
    steps:
      - uses: actions/checkout@v4

      - name: Validate GEDCOM test fixtures
        run: |
          pip install -e .
          gedfix check tests/fixtures/sample_5.5.1.ged --level standard
          gedfix check tests/fixtures/sample_7.0.ged --level standard

      - name: Round-trip test
        run: |
          # Import -> Export -> Import -> Compare
          swift run GenealogyApp import tests/fixtures/sample.ged --db /tmp/test.sqlite
          swift run GenealogyApp export /tmp/test.sqlite --out /tmp/exported.ged
          diff <(gedfix scan tests/fixtures/sample.ged) <(gedfix scan /tmp/exported.ged)
```

### 9.5 GEDCOM Validation Test Suite

```swift
// GEDCOM round-trip and compliance tests
final class GEDCOMComplianceTests: XCTestCase {

    // Test fixtures directory
    let fixturesDir = "Tests/Fixtures/GEDCOM/"

    func testGEDCOM551Compliance() throws {
        let files = [
            "minimal.ged",           // Bare minimum valid GEDCOM
            "torture_test.ged",      // Edge cases: Unicode, long notes, deep nesting
            "dates_variety.ged",     // All date formats: exact, range, period, approx
            "large_tree.ged",        // 10,000+ persons
            "circular_reference.ged" // Should be caught and rejected
        ]
        for file in files {
            let path = fixturesDir + file
            let records = try GEDCOMParser.parse(contentsOfFile: path)
            XCTAssertFalse(records.hasErrors, "Errors in \(file): \(records.errors)")
        }
    }

    func testRoundTrip() throws {
        let original = try String(contentsOfFile: fixturesDir + "complete.ged")
        let records = try GEDCOMParser.parse(original)
        let exported = try GEDCOMExporter.export(records, version: .v551)
        let reimported = try GEDCOMParser.parse(exported)

        XCTAssertEqual(records.persons.count, reimported.persons.count)
        XCTAssertEqual(records.families.count, reimported.families.count)
        XCTAssertEqual(records.events.count, reimported.events.count)
    }
}
```

---

## 10. Recommended Architecture Summary

### Primary Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| **UI Framework** | SwiftUI | macOS 14+ (Sonoma) |
| **Database** | SQLite via GRDB.swift | GRDB 7.10.0 |
| **SwiftUI DB Binding** | SharingGRDB (SQLiteData) | 0.6.0+ |
| **Full-Text Search** | SQLite FTS5 (via GRDB) | Built-in |
| **Vector Search** | sqlite-vec | 0.1.6+ |
| **Encryption** | SQLCipher | 4.6+ |
| **Keychain** | KeychainAccess | 4.2.2+ |
| **Local AI** | MLX / mlx-swift | Latest |
| **Cloud AI** | Anthropic Swift SDK, Google AI SDK | Latest |
| **Snapshot Testing** | swift-snapshot-testing | 1.17+ |
| **GEDCOM Processing** | gedfix (Python, via subprocess) | Existing |
| **Web Scraping** | Playwright (Python) | 1.50+ |
| **Scraping Stealth** | playwright-stealth | Latest |
| **CI** | GitHub Actions (macos-15 runner) | -- |

### Architecture Diagram

```
┌──────────────────────────────────────────────────────────┐
│                    SwiftUI Frontend                       │
│  PersonListView | TreeView | MapView | SearchView        │
│  ResearchAssistantView | MergeView | ReportView          │
├──────────────────────────────────────────────────────────┤
│                    View Models                            │
│  @Observable models with SharingGRDB @FetchAll           │
├──────────┬───────────┬───────────┬───────────────────────┤
│ Database │ AI Engine │ Scraping  │ Plugin System          │
│ Layer    │           │ Engine    │                        │
│          │           │           │                        │
│ GRDB     │ MLX       │ Playwright│ Swift Plugin Protocol  │
│ SQLCipher│ Cloud APIs│ (Python)  │ Python Script Plugins  │
│ FTS5     │ Embeddings│           │ SPM Distribution       │
│ sqlite-  │ (sqlite-  │           │                        │
│ vec      │  vec)     │           │                        │
├──────────┴───────────┴───────────┴───────────────────────┤
│              SQLite Database (encrypted)                   │
│  persons | families | events | places | sources           │
│  media | notes | citations | ai_suggestions               │
│  change_log | person_embeddings | *_fts                   │
├──────────────────────────────────────────────────────────┤
│           macOS Keychain (API keys, encryption keys)      │
└──────────────────────────────────────────────────────────┘
```

### Build Phases

**Phase 1 -- Core (MVP):**
- SwiftUI app shell with GRDB + SQLCipher database
- GEDCOM 5.5.1 import/export (bridge to gedfix)
- Person/Family/Event CRUD with undo/redo
- FTS5 search
- macOS Keychain for API key storage
- Basic privacy filtering (living person detection)

**Phase 2 -- AI:**
- MLX local inference for date/name normalization
- Cloud API integration (Claude for research assistant)
- Multi-key management with rotation
- sqlite-vec embeddings for duplicate detection

**Phase 3 -- Scraping & Plugins:**
- Playwright scraping adapters (FamilySearch API first, then others)
- Plugin SDK and first-party plugins
- Site adapter plugins for Ancestry, MyHeritage, FindAGrave

**Phase 4 -- Collaboration:**
- SharingGRDB CloudKit sync across devices
- Tree merge UI with conflict resolution
- GEDCOM 7.0 support
- Snapshot/versioning system

**Phase 5 -- Cross-Platform (Optional):**
- Extract core logic to Rust via UniFFI
- Tauri frontend for Windows/Linux
- Shared Rust GEDCOM parser

---

## Sources

### Mac Native Tech Stack
- [SwiftData Considerations - BrightDigit](https://brightdigit.com/articles/swiftdata-considerations/)
- [Key Considerations Before Using SwiftData - Fat Bob Man](https://fatbobman.com/en/posts/key-considerations-before-using-swiftdata/)
- [GRDB.swift - GitHub](https://github.com/groue/GRDB.swift)
- [SharingGRDB/SQLiteData - Point-Free](https://www.pointfree.co/blog/posts/168-sharinggrdb-a-swiftdata-alternative)
- [UniFFI Swift Bindings](https://mozilla.github.io/uniffi-rs/latest/swift/overview.html)
- [Bridging Rust and Swift](https://boehs.org/node/uniffi)
- [swift-bridge - GitHub](https://github.com/chinedufn/swift-bridge)
- [PythonKit - GitHub](https://github.com/pvieito/PythonKit)

### Desktop Frameworks
- [Electron vs Tauri - DoltHub](https://www.dolthub.com/blog/2025-11-13-electron-vs-tauri/)
- [Web-to-Desktop Framework Comparison - GitHub](https://github.com/Elanis/web-to-desktop-framework-comparison)
- [Wails as Electron Alternative](https://dev.to/kartik_patel/wails-as-electron-alternative-4dmn)

### Database & GEDCOM
- [SQLite for Genealogy Software - Behold Blog](https://www.beholdgenealogy.com/blog/?p=1692)
- [Database Design for Genealogy - Behold Blog](https://www.beholdgenealogy.com/blog/?p=1560)
- [Charting Companion Database Schema](https://progenygenealogy.com/blogs/charting-companion-database-schema/)
- [FamilySearch GEDCOM 7.0 Specification](https://gedcom.io/specifications/FamilySearchGEDCOMv7.html)
- [GRDB Full-Text Search Documentation](https://github.com/groue/GRDB.swift/blob/master/Documentation/FullTextSearch.md)
- [sqlite-vec - GitHub](https://github.com/asg017/sqlite-vec)

### AI Integration
- [Local LLMs Apple Silicon 2026 Guide](https://www.sitepoint.com/local-llms-apple-silicon-mac-2026/)
- [MLX on Apple Silicon](https://www.markus-schall.de/en/2025/09/mlx-on-apple-silicon-as-local-ki-compared-with-ollama-co/)
- [WWDC 2025 MLX Session](https://developer.apple.com/videos/play/wwdc2025/298/)
- [Running LLMs Locally on macOS 2026 Comparison](https://dev.to/bspann/running-llms-locally-on-macos-the-complete-2026-comparison-48fc)
- [SQLite-Vector for Embeddings](https://www.sqlite.ai/sqlite-vector)

### Security & Keys
- [macOS Keychain in Swift](https://oneuptime.com/blog/post/2026-02-02-swift-keychain-secure-storage/view)
- [KeychainAccess - GitHub](https://github.com/kishikawakatsumi/KeychainAccess)
- [Apple Keychain Services Documentation](https://developer.apple.com/documentation/security/keychain-services)
- [1Password CLI Developer Docs](https://developer.1password.com/docs/cli/)
- [Secure API Keys with 1Password CLI](https://rizwan.dev/blog/secure-api-keys-with-1password-cli/)
- [SQLCipher.swift - GitHub](https://github.com/sqlcipher/SQLCipher.swift)

### Scraping
- [Playwright Scraping Guide 2025 - ScraperAPI](https://www.scraperapi.com/web-scraping/playwright/)
- [Stealth Scraping with Playwright](https://www.browserless.io/blog/stealth-scraping-puppeteer-playwright)
- [GenScrape - Genealogy Scraping Library](https://github.com/rootsdev/genscrape)
- [Web Scraping Legal Guide 2025](https://mccarthylg.com/is-web-scraping-legal-a-2025-breakdown-of-what-you-need-to-know/)

### Plugin System
- [Gramps Plugin Development](https://www.gramps-project.org/wiki/index.php/Addons_development)
- [Gramps gen.plug API](https://gramps-project.org/api_5_1_x/gen/gen_plug.html)
- [GEPS 014: Plugin Registration](https://www.gramps-project.org/wiki/index.php/GEPS_014:_Plugin_registration_and_management)

### Merge & Sync
- [GEDCOM Match and Merge](https://gdbi.sourceforge.net/merge.html)
- [Gramps Import Merge Tool](https://gramps-project.org/wiki/index.php/Import_Merge_Tool)
- [CRDTs vs OT](https://systemdr.substack.com/p/crdts-vs-operational-transformation)
- [Automerge CRDT Library](https://github.com/alangibson/awesome-crdt)

### Privacy
- [GDPR Encryption Guide](https://thecyphere.com/blog/gdpr-encryption/)
- [SQLCipher Encryption Tutorial](https://oneuptime.com/blog/post/2026-02-02-sqlcipher-encryption/view)
- [Living Person Privacy in GEDCOM](https://whoareyoumadeof.com/blog/how-to-protect-the-privacy-of-living-relatives-on-your-gedmatch-gedcom/)

### Testing & CI
- [swift-snapshot-testing - GitHub](https://github.com/pointfreeco/swift-snapshot-testing)
- [SnapshotPreviews - EmergeTools](https://github.com/EmergeTools/SnapshotPreviews)
