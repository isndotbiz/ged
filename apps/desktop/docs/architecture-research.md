# Optimal Architecture for a Best-in-Class Genealogy Desktop Application

**Research Date:** March 2026
**Current Stack:** Tauri 2 + SvelteKit 5 + Svelte 5 + SQLite (via tauri-plugin-sql) + Tailwind CSS 4
**Context:** GedFix is already a working Tauri app with 20+ screens, GEDCOM 5.5.1 parser, FTS5 search, and a rich feature set

---

## 1. Stack Comparison for Genealogy Desktop Apps

### 1.1 Benchmark Data (Elanis/web-to-desktop-framework-comparison, March 2026)

| Metric | Tauri 2 | Electron | Flutter Desktop | Wails | Neutralino |
|--------|---------|----------|----------------|-------|------------|
| **Binary Size (Win x64)** | ~3 MB | ~346 MB | ~26 MB | ~10 MB | ~2 MB |
| **Binary Size (macOS arm64)** | ~4 MB | ~464 MB | N/A | ~7 MB | ~1 MB |
| **Binary Size (Linux x64)** | ~3 MB | ~302 MB | N/A | ~8 MB | ~1 MB |
| **Memory (Win Release)** | ~189 MB | ~107 MB | ~42 MB | ~192 MB | ~312 MB |
| **Startup (Win Release)** | ~724 ms | ~303 ms | ~104 ms | ~597 ms | N/A |
| **Startup (Linux Release)** | ~25 sec* | ~270 ms | N/A | ~212 ms | N/A |

*Note: The 25-second Linux startup in CI is a known anomaly in GitHub Actions — real-world Linux startup is sub-second.*

**Supplementary benchmarks (Hopp.app, real-world app with 6 windows):**

| Metric | Tauri 2 | Electron |
|--------|---------|----------|
| Bundle Size | 8.6 MB | 244 MB |
| Memory (6 windows) | ~172 MB | ~409 MB |
| Build Time | 80.9 sec | 15.8 sec |

### 1.2 Framework Analysis

**Tauri 2 (Current Choice — RECOMMENDED)**

Strengths for genealogy:
- Binary size 100x smaller than Electron — critical for a utility app users install alongside their main genealogy software
- Rust backend provides native SQLite access, image processing (the `image` crate is already in your Cargo.toml), and CPU-intensive GEDCOM parsing without Node.js overhead
- OS-native WebView means the UI respects system fonts, accessibility features, and platform conventions
- Tauri 2 plugin ecosystem includes sql, dialog, fs, shell — all already in your project
- Security model with fine-grained capability permissions is ideal for a data-sensitive genealogy app
- Active development: v2.10.3 as of March 2026, ~104K GitHub stars, 35% YoY growth

Weaknesses:
- Longer initial build times (Rust compilation), mitigated by incremental builds
- WebView rendering inconsistencies across OS versions (though increasingly negligible)
- Smaller ecosystem than Electron (but growing rapidly)

**Electron**

Strengths: Mature ecosystem, consistent Chromium rendering, extensive npm packages. Weaknesses: 346 MB binary for a genealogy app is absurd — users already have RootsMagic, Ancestry, FamilySearch open. Memory hog at 200-400 MB idle. Bundling Chromium is wasteful when all target OSes ship with capable WebViews.

**Flutter Desktop**

Strengths: Fastest startup at ~104ms, consistent cross-platform UI, strong on mobile. Weaknesses: Desktop support still maturing. Custom rendering engine means no native text selection, no browser devtools for debugging, no CSS/HTML ecosystem. Dart has a thin genealogy library ecosystem. The 26 MB binary is respectable but the development experience for data-heavy desktop apps is suboptimal.

**Native Swift/Kotlin**

Strengths: Best possible platform integration, smallest binary, lowest memory. Weaknesses: Two completely separate codebases. No code sharing for the 90% of logic that is platform-independent. SwiftUI's macOS support has improved significantly but still lacks some AppKit features. Kotlin Desktop (Compose Multiplatform) is viable but the ecosystem is Android-first. For a solo developer or small team, maintaining two native codebases is unsustainable.

### 1.3 Verdict

**Tauri 2 + SvelteKit is the optimal choice for GedFix.** The 100x binary advantage, Rust backend for performance-critical operations, and the existing 20+ screen investment make switching frameworks counterproductive. The only scenario where a different choice wins is if mobile becomes a priority (then Flutter or Kotlin Multiplatform warrants evaluation).

---

## 2. Database Schema for Genealogy

### 2.1 How RootsMagic Structures Its SQLite Database

RootsMagic (v8/9/10) uses a `.rmtree` SQLite file with these core tables:

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| **PersonTable** | One row per individual | PersonID, UniqueID, Sex, ParentID, SpouseID, Color1-Color10, Relate1, Relate2 |
| **NameTable** | Names (multiple per person) | OwnerID, Surname, Given, Prefix, Suffix, Nickname, NameType, IsPrimary, Date, SortDate |
| **FamilyTable** | Family units | FamilyID, FatherID, MotherID, ChildID, HusbOrder, WifeOrder |
| **ChildTable** | Children-to-family links | RecID, ChildID, FamilyID, RelFather, RelMother, ChildOrder |
| **EventTable** | All facts/events | OwnerType, OwnerID, FamilyID, PlaceID, SiteID, Date, SortDate, Details, EventType |
| **FactTypeTable** | Event type definitions | FactTypeID, OwnerType, Name, Abbrev, GedcomTag |
| **SourceTable** | Sources | SourceID, Name, RefNumber, ActualText, Comments, Fields (template-based) |
| **CitationTable** | Citations linking sources to facts | CitationID, SourceID, OwnerType, OwnerID, Quality, Comments, ActualText, RefNumber |
| **PlaceTable** | Places (hierarchical) | PlaceID, PlaceType, Name, Abbrev, Normalized, Latitude, Longitude, MasterID |
| **MultimediaTable** | Media files | MediaID, MediaType, MediaPath, MediaFile, URL, Thumbnail, Caption, Date, Description |
| **MediaLinkTable** | Links media to persons/events | MediaID, OwnerType, OwnerID, IsPrimary, Include, SortOrder |
| **URLTable** | Web URLs | OwnerType, OwnerID, URL, Name |
| **TaskLinkTable** | Task/to-do items | TaskID, OwnerType, OwnerID |

**Key design insights from RootsMagic:**
- Names are in a separate table (supports multiple names per person with IsPrimary flag)
- Events use OwnerType/OwnerID polymorphism (events can belong to persons OR families)
- MediaLinkTable is a proper junction table enabling many-to-many media-to-entity linking
- PlaceTable has MasterID for hierarchical place resolution
- No foreign keys in the database — relationships managed in application code
- SortDate is a computed column for fast date-based ordering (separate from display Date)
- FactTypeTable maps event types to GEDCOM tags, allowing custom fact types

### 2.2 How Gramps Structures Its Data

Gramps (since 5.1) uses SQLite with a hybrid approach:

**Primary Objects:** Person, Family, Event, Place, Source, Citation, Media, Note, Repository, Tag

**Table Structure (26+ tables):**
- Core entity tables: `person`, `family`, `event`, `place`, `source`, `citation`, `media`, `note`, `repository`, `tag`
- Reference/linking tables: `person_ref`, `event_ref`, `media_ref`, `child_ref`, `source_ref`, `repository_ref`
- Attribute tables: `attribute`, `address`, `url`, `name`, `date`, `location`, `datamap`
- Metadata: `link`, `markup`, `lds`

**Key columns per table:**
- `handle` (TEXT) — unique identifier (like GEDCOM xref)
- `gramps_id` (TEXT) — user-visible ID
- `blob_data` (BLOB) — pickled Python tuple containing full object data
- `change` (REAL) — modification timestamp
- `private` (INTEGER) — privacy flag

**Gramps design insights:**
- blob_data stores the full object as a serialized tuple — fast for full-object reads but prevents SQL-level querying of individual fields
- "Flat" columns duplicate key fields from the blob for indexing (e.g., `order_by` for sorting)
- The Event model is truly shared — events are first-class objects referenced by both persons and families via `event_ref`
- Citations are separate from Sources (matching the genealogical proof standard)
- Media references use `media_ref` junction table with crop coordinates (x1, y1, x2, y2) for face regions

### 2.3 Optimal Schema for GedFix (Recommended)

This schema synthesizes the best patterns from RootsMagic, Gramps, and the GEDCOM 7.0 data model while addressing the gaps in GedFix's current schema.

```sql
-- ============================================================
-- PERSONS
-- ============================================================
CREATE TABLE person (
    id          INTEGER PRIMARY KEY,
    xref        TEXT UNIQUE NOT NULL,       -- GEDCOM xref or UUID
    sex         TEXT DEFAULT 'U',           -- M, F, X, U
    is_living   INTEGER DEFAULT 0,
    proof_status TEXT DEFAULT 'UNKNOWN',    -- PROVEN, DISPROVEN, DISPUTED, PROPOSED, UNKNOWN
    color       TEXT DEFAULT '',            -- UI color tag
    sort_date   INTEGER DEFAULT 0,          -- birth sort key (Julian day number)
    created_at  TEXT DEFAULT (datetime('now')),
    updated_at  TEXT DEFAULT (datetime('now')),
    -- Denormalized for performance (from primary name)
    given_name  TEXT DEFAULT '',
    surname     TEXT DEFAULT '',
    suffix      TEXT DEFAULT ''
);

-- ============================================================
-- NAMES (multiple per person, like RootsMagic NameTable)
-- ============================================================
CREATE TABLE name (
    id          INTEGER PRIMARY KEY,
    person_id   INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    given_name  TEXT DEFAULT '',
    surname     TEXT DEFAULT '',
    prefix      TEXT DEFAULT '',            -- "Dr.", "Sir"
    suffix      TEXT DEFAULT '',
    nickname    TEXT DEFAULT '',
    name_type   TEXT DEFAULT 'BIRTH',       -- BIRTH, MARRIED, ALSO_KNOWN_AS, IMMIGRANT, RELIGIOUS, etc.
    is_primary  INTEGER DEFAULT 0,
    sort_key    TEXT DEFAULT '',             -- computed: "SURNAME, GivenName" for fast sorting
    -- GEDCOM 7.0: transliterations
    lang        TEXT DEFAULT '',            -- BCP 47 language tag
    source_text TEXT DEFAULT ''             -- original source text
);
CREATE INDEX idx_name_person ON name(person_id);
CREATE INDEX idx_name_primary ON name(person_id, is_primary);

-- ============================================================
-- FAMILIES
-- ============================================================
CREATE TABLE family (
    id          INTEGER PRIMARY KEY,
    xref        TEXT UNIQUE NOT NULL,
    partner1_id INTEGER REFERENCES person(id) ON DELETE SET NULL,
    partner2_id INTEGER REFERENCES person(id) ON DELETE SET NULL,
    rel_type    TEXT DEFAULT 'MARRIED',     -- MARRIED, UNMARRIED, CIVIL_UNION, UNKNOWN
    sort_date   INTEGER DEFAULT 0,          -- marriage sort key
    created_at  TEXT DEFAULT (datetime('now')),
    updated_at  TEXT DEFAULT (datetime('now'))
);
CREATE INDEX idx_family_partner1 ON family(partner1_id);
CREATE INDEX idx_family_partner2 ON family(partner2_id);

-- ============================================================
-- CHILD LINKS (children to families, like RootsMagic ChildTable)
-- ============================================================
CREATE TABLE child_link (
    family_id   INTEGER NOT NULL REFERENCES family(id) ON DELETE CASCADE,
    person_id   INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    rel_father  TEXT DEFAULT 'BIRTH',       -- BIRTH, ADOPTED, FOSTER, STEP, UNKNOWN
    rel_mother  TEXT DEFAULT 'BIRTH',
    child_order INTEGER DEFAULT 0,
    PRIMARY KEY (family_id, person_id)
);
CREATE INDEX idx_child_person ON child_link(person_id);

-- ============================================================
-- EVENTS (first-class objects, like Gramps)
-- ============================================================
CREATE TABLE event (
    id          INTEGER PRIMARY KEY,
    event_type  TEXT NOT NULL,              -- BIRT, DEAT, MARR, BURI, CHR, RESI, OCCU, CENS, etc.
    date_value  TEXT DEFAULT '',            -- Display format: "15 MAR 1892"
    sort_date   INTEGER DEFAULT 0,         -- Julian day number for sorting/filtering
    date_mod    TEXT DEFAULT '',            -- ABT, BEF, AFT, BET, CAL, EST
    place_id    INTEGER REFERENCES place(id) ON DELETE SET NULL,
    description TEXT DEFAULT '',
    -- GEDCOM 7.0 fields
    cause       TEXT DEFAULT '',            -- Cause of death, etc.
    agency      TEXT DEFAULT '',            -- Responsible agency
    created_at  TEXT DEFAULT (datetime('now')),
    updated_at  TEXT DEFAULT (datetime('now'))
);
CREATE INDEX idx_event_type ON event(event_type);
CREATE INDEX idx_event_sort ON event(sort_date);
CREATE INDEX idx_event_place ON event(place_id);

-- Event-to-entity linking (many-to-many: one census event -> multiple people)
CREATE TABLE event_ref (
    event_id    INTEGER NOT NULL REFERENCES event(id) ON DELETE CASCADE,
    owner_type  TEXT NOT NULL,              -- 'INDI' or 'FAM'
    owner_id    INTEGER NOT NULL,           -- person.id or family.id
    role        TEXT DEFAULT 'PRIMARY',     -- PRIMARY, WITNESS, CELEBRANT, INFORMANT, etc.
    PRIMARY KEY (event_id, owner_type, owner_id)
);
CREATE INDEX idx_eventref_owner ON event_ref(owner_type, owner_id);

-- ============================================================
-- PLACES (hierarchical, like RootsMagic PlaceTable)
-- ============================================================
CREATE TABLE place (
    id          INTEGER PRIMARY KEY,
    name        TEXT NOT NULL,              -- "Pittsburgh, Allegheny, Pennsylvania, USA"
    normalized  TEXT DEFAULT '',            -- Standardized form
    place_type  TEXT DEFAULT '',            -- CITY, COUNTY, STATE, COUNTRY, etc.
    parent_id   INTEGER REFERENCES place(id) ON DELETE SET NULL,  -- Hierarchical
    latitude    REAL,
    longitude   REAL,
    -- GEDCOM 7.0: transliterations
    lang        TEXT DEFAULT '',
    created_at  TEXT DEFAULT (datetime('now'))
);
CREATE INDEX idx_place_name ON place(name);
CREATE INDEX idx_place_parent ON place(parent_id);
CREATE INDEX idx_place_geo ON place(latitude, longitude);

-- ============================================================
-- SOURCES
-- ============================================================
CREATE TABLE source (
    id          INTEGER PRIMARY KEY,
    xref        TEXT UNIQUE,
    title       TEXT DEFAULT '',
    author      TEXT DEFAULT '',
    publisher   TEXT DEFAULT '',
    abbrev      TEXT DEFAULT '',            -- Short title
    repo_id     INTEGER REFERENCES repository(id) ON DELETE SET NULL,
    source_type TEXT DEFAULT '',            -- BOOK, CENSUS, VITAL_RECORD, CHURCH_RECORD, etc.
    url         TEXT DEFAULT '',
    created_at  TEXT DEFAULT (datetime('now')),
    updated_at  TEXT DEFAULT (datetime('now'))
);
CREATE INDEX idx_source_title ON source(title);

-- ============================================================
-- CITATIONS (link sources to specific facts, like RootsMagic CitationTable)
-- ============================================================
CREATE TABLE citation (
    id          INTEGER PRIMARY KEY,
    source_id   INTEGER NOT NULL REFERENCES source(id) ON DELETE CASCADE,
    owner_type  TEXT NOT NULL,              -- 'INDI', 'FAM', 'EVENT', 'NAME', etc.
    owner_id    INTEGER NOT NULL,
    page        TEXT DEFAULT '',            -- "Page 42, Line 7"
    quality     INTEGER DEFAULT 0,         -- 0=unreliable, 1=questionable, 2=secondary, 3=primary
    text        TEXT DEFAULT '',            -- Transcription from source
    note        TEXT DEFAULT '',
    date_value  TEXT DEFAULT '',            -- When was this source accessed
    confidence  TEXT DEFAULT 'UNKNOWN',     -- HIGH, MEDIUM, LOW, UNKNOWN
    created_at  TEXT DEFAULT (datetime('now'))
);
CREATE INDEX idx_citation_source ON citation(source_id);
CREATE INDEX idx_citation_owner ON citation(owner_type, owner_id);

-- ============================================================
-- REPOSITORIES
-- ============================================================
CREATE TABLE repository (
    id          INTEGER PRIMARY KEY,
    xref        TEXT UNIQUE,
    name        TEXT DEFAULT '',
    address     TEXT DEFAULT '',
    url         TEXT DEFAULT '',
    repo_type   TEXT DEFAULT '',            -- LIBRARY, ARCHIVE, CHURCH, etc.
    created_at  TEXT DEFAULT (datetime('now'))
);

-- ============================================================
-- MEDIA (content-addressable, with many-to-many linking)
-- ============================================================
CREATE TABLE media (
    id          INTEGER PRIMARY KEY,
    xref        TEXT DEFAULT '',
    file_path   TEXT NOT NULL,              -- Absolute or relative path
    file_hash   TEXT DEFAULT '',            -- SHA-256 content hash for dedup
    mime_type   TEXT DEFAULT '',            -- image/jpeg, application/pdf, etc.
    file_size   INTEGER DEFAULT 0,
    width       INTEGER DEFAULT 0,
    height      INTEGER DEFAULT 0,
    title       TEXT DEFAULT '',
    description TEXT DEFAULT '',
    date_value  TEXT DEFAULT '',            -- Date of the media (photo taken, document created)
    thumb_path  TEXT DEFAULT '',            -- Path to generated thumbnail
    created_at  TEXT DEFAULT (datetime('now')),
    updated_at  TEXT DEFAULT (datetime('now'))
);
CREATE UNIQUE INDEX idx_media_hash ON media(file_hash) WHERE file_hash != '';
CREATE INDEX idx_media_path ON media(file_path);

-- Many-to-many: media linked to persons, families, events, sources
CREATE TABLE media_ref (
    media_id    INTEGER NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    owner_type  TEXT NOT NULL,              -- 'INDI', 'FAM', 'EVENT', 'SOURCE'
    owner_id    INTEGER NOT NULL,
    is_primary  INTEGER DEFAULT 0,         -- Primary photo for this entity
    sort_order  INTEGER DEFAULT 0,
    -- Face crop region (for person tagging within a photo)
    crop_x1     REAL,
    crop_y1     REAL,
    crop_x2     REAL,
    crop_y2     REAL,
    note        TEXT DEFAULT '',
    PRIMARY KEY (media_id, owner_type, owner_id)
);
CREATE INDEX idx_mediaref_owner ON media_ref(owner_type, owner_id);

-- Face detection results
CREATE TABLE face (
    id          INTEGER PRIMARY KEY,
    media_id    INTEGER NOT NULL REFERENCES media(id) ON DELETE CASCADE,
    person_id   INTEGER REFERENCES person(id) ON DELETE SET NULL,  -- NULL = unidentified
    cluster_id  INTEGER DEFAULT 0,         -- Face clustering group
    confidence  REAL DEFAULT 0.0,          -- Detection confidence 0-1
    -- Bounding box (normalized 0-1)
    bbox_x      REAL NOT NULL,
    bbox_y      REAL NOT NULL,
    bbox_w      REAL NOT NULL,
    bbox_h      REAL NOT NULL,
    -- 128-dim face embedding (stored as blob for similarity search)
    embedding   BLOB,
    created_at  TEXT DEFAULT (datetime('now'))
);
CREATE INDEX idx_face_media ON face(media_id);
CREATE INDEX idx_face_person ON face(person_id);
CREATE INDEX idx_face_cluster ON face(cluster_id);

-- ============================================================
-- NOTES (attachable to any entity)
-- ============================================================
CREATE TABLE note (
    id          INTEGER PRIMARY KEY,
    owner_type  TEXT DEFAULT '',            -- 'INDI', 'FAM', 'EVENT', 'SOURCE', or '' for standalone
    owner_id    INTEGER DEFAULT 0,
    note_type   TEXT DEFAULT 'GENERAL',     -- GENERAL, RESEARCH, ANALYSIS, TODO
    title       TEXT DEFAULT '',
    content     TEXT DEFAULT '',            -- Supports markdown (GEDCOM 7.0 formatted notes)
    is_shared   INTEGER DEFAULT 0,         -- GEDCOM 7.0 SNOTE
    created_at  TEXT DEFAULT (datetime('now')),
    updated_at  TEXT DEFAULT (datetime('now'))
);
CREATE INDEX idx_note_owner ON note(owner_type, owner_id);

-- ============================================================
-- RESEARCH SUPPORT TABLES
-- ============================================================
CREATE TABLE research_log (
    id          INTEGER PRIMARY KEY,
    person_id   INTEGER REFERENCES person(id) ON DELETE SET NULL,
    repository  TEXT DEFAULT '',
    search_terms TEXT DEFAULT '',
    records_viewed TEXT DEFAULT '',
    conclusion  TEXT DEFAULT '',
    source_id   INTEGER REFERENCES source(id) ON DELETE SET NULL,
    result_type TEXT DEFAULT 'NEGATIVE',   -- POSITIVE, NEGATIVE, INCONCLUSIVE
    search_date TEXT DEFAULT '',
    created_at  TEXT DEFAULT (datetime('now'))
);

CREATE TABLE research_task (
    id          INTEGER PRIMARY KEY,
    person_id   INTEGER REFERENCES person(id) ON DELETE SET NULL,
    title       TEXT NOT NULL,
    description TEXT DEFAULT '',
    status      TEXT DEFAULT 'TODO',        -- TODO, IN_PROGRESS, DONE
    priority    TEXT DEFAULT 'MEDIUM',      -- HIGH, MEDIUM, LOW
    due_date    TEXT DEFAULT '',
    created_at  TEXT DEFAULT (datetime('now')),
    completed_at TEXT DEFAULT ''
);

CREATE TABLE association (
    id          INTEGER PRIMARY KEY,
    person1_id  INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    person2_id  INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    rel_type    TEXT DEFAULT '',            -- NEIGHBOR, GODPARENT, BUSINESS_PARTNER, etc.
    description TEXT DEFAULT '',
    source_id   INTEGER REFERENCES source(id) ON DELETE SET NULL,
    created_at  TEXT DEFAULT (datetime('now'))
);

CREATE TABLE custom_group (
    id          INTEGER PRIMARY KEY,
    name        TEXT NOT NULL,
    color       TEXT DEFAULT '',
    description TEXT DEFAULT '',
    created_at  TEXT DEFAULT (datetime('now'))
);

CREATE TABLE group_member (
    group_id    INTEGER NOT NULL REFERENCES custom_group(id) ON DELETE CASCADE,
    person_id   INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    added_at    TEXT DEFAULT (datetime('now')),
    PRIMARY KEY (group_id, person_id)
);

CREATE TABLE bookmark (
    id          INTEGER PRIMARY KEY,
    person_id   INTEGER NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    label       TEXT DEFAULT '',
    created_at  TEXT DEFAULT (datetime('now'))
);

-- ============================================================
-- FULL-TEXT SEARCH
-- ============================================================
CREATE VIRTUAL TABLE person_fts USING fts5(
    given_name, surname, birth_place, death_place,
    content='person', content_rowid='id',
    tokenize='porter unicode61'
);

-- ============================================================
-- SYNC METADATA (for local-first sync)
-- ============================================================
CREATE TABLE sync_meta (
    id          INTEGER PRIMARY KEY,
    table_name  TEXT NOT NULL,
    row_id      INTEGER NOT NULL,
    clock       INTEGER DEFAULT 0,         -- Lamport timestamp
    node_id     TEXT NOT NULL,             -- Device identifier
    operation   TEXT NOT NULL,             -- INSERT, UPDATE, DELETE
    changed_at  TEXT DEFAULT (datetime('now')),
    synced      INTEGER DEFAULT 0          -- 0=pending, 1=synced
);
CREATE INDEX idx_sync_pending ON sync_meta(synced, changed_at);
CREATE INDEX idx_sync_table ON sync_meta(table_name, row_id);
```

### 2.4 Key Schema Improvements Over Current GedFix

| Current Gap | Proposed Fix |
|-------------|-------------|
| Media uses `ownerXref` (one-to-one) | `media_ref` junction table (many-to-many) |
| No separate names table | `name` table with `is_primary`, multiple names per person |
| Events owned by one entity | `event_ref` junction table — shared events (census, family events) |
| No hierarchical places | `place.parent_id` self-referencing for hierarchy |
| No face detection storage | `face` table with bounding boxes and embeddings |
| No content-addressable media | `media.file_hash` with unique index |
| No sync metadata | `sync_meta` table for CRDT-style change tracking |
| No repositories | `repository` table linked to sources |
| No crop regions for faces in photos | `media_ref.crop_x1/y1/x2/y2` fields |
| Dates stored as text only | `sort_date` as Julian day number for fast range queries |
| No foreign keys enforced in schema | Full FK constraints with ON DELETE CASCADE/SET NULL |
| Citations not linked to events | `citation.owner_type` supports EVENT ownership |

### 2.5 GEDCOM 5.5.1 vs GEDCOM 7.0

| Feature | 5.5.1 | 7.0 |
|---------|-------|-----|
| **Encoding** | ANSEL, ASCII, UTF-8, Unicode | UTF-8 only (mandatory) |
| **Notes** | NOTE_RECORD (ambiguous) | SNOTE (shared note, unambiguous) |
| **Media** | Inline or pointer OBJE | Pointer OBJE only, IANA media types |
| **Identifiers** | AFN, RFN, RIN (separate) | EXID (unified, with TYPE for external DBs) |
| **Transliterations** | FONE, ROMN (limited) | TRAN with LANG (BCP 47, unlimited) |
| **Ages** | Years/months/days + keywords | Weeks added, keywords removed (use PHRASE) |
| **Extensions** | Underscore tags (_CUSTOM) | Standard extension mechanism with schema URIs |
| **Associations** | Limited | Full event/attribute associations with ROLE |
| **Formatted notes** | Not standardized | Official support for formatted text |
| **File packaging** | None | GEDZIP (.gdz) — ZIP archive with media |
| **Calendar** | ROMAN/UNKNOWN allowed | Removed (undefined meaning) |
| **Enumerations** | Mixed case | Uppercase only |

**Migration Path (5.5.1 to 7.0):**
1. Convert encoding to UTF-8
2. Replace NOTE_RECORD with SNOTE (shared notes)
3. Convert inline OBJE to pointer-based with separate OBJE records
4. Map FORM values to IANA media types (e.g., "jpeg" to "image/jpeg")
5. Replace AFN/RFN/RIN with EXID + TYPE
6. Replace FONE/ROMN with TRAN + LANG
7. Replace RELA with ROLE (or ROLE OTHER + PHRASE)
8. Remove SUBN records (TempleReady-specific, obsolete)
9. Uppercase all enumeration values
10. Ensure BET/AND date ranges are chronologically ordered
11. Convert dual dates ("1648/9") to single dates with PHRASE

**GEDZIP Format:**
A standard ZIP archive containing one `.ged` file and associated media files. The GEDCOM file references media with relative paths within the archive. File extension: `.gdz`. This solves the long-standing problem of sharing genealogy data with media intact.

---

## 3. Local-First Architecture with Cloud Sync

### 3.1 CRDT Options Comparison

| Library | Approach | Language | SQLite Integration | Maturity | Best For |
|---------|----------|----------|-------------------|----------|----------|
| **cr-sqlite** | Column-level CRDTs on SQLite | Rust/C | Native extension | Beta (vlcn.io) | Direct SQLite CRDT replication |
| **SQLite Sync** | CRDT-powered SQLite extension | C | Native extension | New (2025) | Zero-config SQLite sync |
| **Automerge** | JSON CRDT document model | Rust + WASM | Via sync layer | Stable | Rich document collaboration |
| **Yjs** | Composable shared types | JS | Via sync layer | Stable | Real-time collaborative editing |
| **sql_crdt** | CRDT tables in SQL | Dart | SQLite/Postgres | Niche | Flutter apps |

### 3.2 SQLite Cloud Replication Options

| Solution | Sync Model | Backend Required | Status (2026) | Cost |
|----------|-----------|-----------------|---------------|------|
| **Turso** | libSQL fork, embedded replicas | Turso Cloud | Production | Free tier + paid |
| **PowerSync** | Bi-directional sync to Postgres | Postgres + PowerSync | Production | Free tier + paid |
| **LiteFS** | WAL-level filesystem replication | Fly.io | Sunsetted (Oct 2024) | N/A |
| **ElectricSQL** | Postgres-to-SQLite sync | Postgres + Electric | Active | Open source |
| **cr-sqlite** | P2P CRDT merge | None (P2P) | Beta | Open source |

### 3.3 Genealogy-Specific Merge Conflict Strategies

Genealogy data has unique conflict characteristics:

**Low-conflict data (Last-Write-Wins is fine):**
- Person color tags, bookmarks, UI preferences
- Research log entries, notes, tasks
- Custom groups

**Medium-conflict data (Column-level CRDT):**
- Person name corrections (two researchers fix the same name differently)
- Event date refinements
- Place standardization

**High-conflict data (Needs human review):**
- Merge of duplicate persons (cannot be auto-resolved)
- Conflicting birth/death dates from different sources
- Family relationship changes (reparenting)

**Recommended conflict resolution strategy:**

```
                    +-----------------+
                    |  Change arrives |
                    +--------+--------+
                             |
                    +--------v--------+
                    | Classify change |
                    +--------+--------+
                             |
              +--------------+--------------+
              |              |              |
     +--------v------+ +----v------+ +-----v--------+
     | Additive      | | Metadata  | | Structural   |
     | (notes, media,| | (names,   | | (family      |
     |  citations)   | |  dates,   | |  links,      |
     |               | |  places)  | |  merges)     |
     +--------+------+ +----+------+ +-----+--------+
              |              |              |
     +--------v------+ +----v------+ +-----v--------+
     | Auto-merge    | | LWW per   | | Queue for    |
     | (append)      | | column    | | human review |
     +---------------+ +-----------+ +--------------+
```

### 3.4 Recommended Sync Architecture for GedFix

**Phase 1 (Now): Change tracking foundation**
```sql
-- Add to every mutable table
ALTER TABLE person ADD COLUMN _clock INTEGER DEFAULT 0;
ALTER TABLE person ADD COLUMN _node TEXT DEFAULT '';
ALTER TABLE person ADD COLUMN _deleted INTEGER DEFAULT 0;
```
Plus the `sync_meta` table from the schema above. Every write increments `_clock` and records the `_node`. Soft deletes via `_deleted` flag.

**Phase 2 (Near-term): Turso embedded replicas**
- Use Turso's libSQL as a drop-in SQLite replacement
- Each device gets an embedded replica that syncs to Turso Cloud
- Read queries hit the local replica (zero latency)
- Writes sync to cloud and propagate to other devices
- Turso handles replication at the WAL level — minimal code changes

**Phase 3 (Future): Column-level CRDT for advanced merge**
- Adopt cr-sqlite or SQLite Sync for column-level conflict resolution
- Add CRDT metadata columns to high-conflict tables (person, name, event)
- Build a "sync conflicts" UI for human review of structural changes

**Why Turso over PowerSync:** Turso is SQLite-native (libSQL fork), so it requires minimal architecture changes. PowerSync requires a Postgres backend, adding unnecessary infrastructure complexity for a desktop app. Turso's embedded replica model maps perfectly to Tauri's architecture.

---

## 4. Media Management Architecture

### 4.1 Content-Addressable Storage

```
GedFix Media Directory Structure:
~/.gedfix/media/
  ├── store/                    # Content-addressable storage
  │   ├── a1/                   # First 2 chars of SHA-256
  │   │   ├── a1b2c3d4e5...     # Full hash as filename
  │   │   └── a1f7e8d9c0...
  │   ├── b3/
  │   │   └── b3c4d5e6f7...
  │   └── ...
  ├── thumbs/                   # Generated thumbnails
  │   ├── a1b2c3d4e5_256.webp   # 256px wide
  │   ├── a1b2c3d4e5_64.webp    # 64px (avatar size)
  │   └── ...
  └── imports/                  # Temporary import staging
```

**Import pipeline:**

```
  File selected/dropped
         |
  +------v-------+
  | SHA-256 hash  |  <-- Compute hash (Rust backend, ~50ms for 10MB file)
  +------+-------+
         |
  +------v-------+
  | Hash exists?  |--YES--> Skip copy, create media_ref link only
  +------+-------+
         |NO
  +------v-------+
  | Copy to store |  <-- store/{hash[0:2]}/{hash}
  +------+-------+
         |
  +------v-------+
  | Generate      |  <-- 256px + 64px WebP thumbnails (Rust `image` crate)
  | thumbnails    |
  +------+-------+
         |
  +------v-------+
  | Extract EXIF  |  <-- Date, GPS, camera info
  +------+-------+
         |
  +------v-------+
  | Face detection|  <-- Queue for background processing
  +------+-------+
         |
  +------v-------+
  | Insert media  |  <-- media table + media_ref link
  | + media_ref   |
  +------+-------+
```

### 4.2 Face Detection and Clustering

**Recommended approach for Tauri:**

The face detection pipeline should run in the **Rust backend**, not the WebView:

1. **Detection:** Use the `ort` crate (ONNX Runtime for Rust) with a pre-trained model:
   - **RetinaFace** or **SCRFD** for face detection (~5ms per image on CPU)
   - **ArcFace** or **FaceNet** for face embedding generation (~10ms per face)
   - Models are ~5-20MB ONNX files bundled with the app

2. **Clustering:** Chinese Whispers algorithm or DBSCAN on the 128/512-dim embeddings:
   ```
   For each new face embedding:
     1. Compare against all existing cluster centroids
     2. If cosine similarity > 0.6 with any cluster → assign to that cluster
     3. If no match → create new cluster
     4. Periodically re-cluster all unassigned faces with DBSCAN
   ```

3. **Person assignment:**
   - User confirms or corrects cluster-to-person assignments
   - Confirmed assignments improve future clustering (semi-supervised learning)
   - Store embeddings as BLOBs in the `face` table for fast comparison

**Alternative (simpler, less accurate):** Use `face-api.js` (vladmandic fork) in the WebView with TensorFlow.js. Runs entirely in JavaScript, no Rust code needed, but 3-5x slower and less accurate.

### 4.3 Thumbnail Generation Pipeline

```rust
// In Rust backend (src-tauri/src/lib.rs)
// Already has `image` crate in Cargo.toml

fn generate_thumbnails(source_path: &Path, hash: &str) -> Result<()> {
    let img = image::open(source_path)?;

    // 256px wide for gallery view
    let thumb_256 = img.resize(256, 256, image::imageops::FilterType::Lanczos3);
    thumb_256.save(format!("thumbs/{}_256.webp", hash))?;

    // 64px for avatar/list view
    let thumb_64 = img.resize(64, 64, image::imageops::FilterType::Lanczos3);
    thumb_64.save(format!("thumbs/{}_64.webp", hash))?;

    Ok(())
}
```

Use WebP format for thumbnails — 30% smaller than JPEG at equivalent quality, supported by all modern WebViews.

### 4.4 Multi-Person Media Linking (Without File Duplication)

The `media_ref` junction table solves this completely:

```
One photo of a family gathering:

media table:
  id=1, file_path="store/a1/a1b2c3...", title="Family Reunion 1952"

media_ref table:
  media_id=1, owner_type='INDI', owner_id=42, is_primary=1, crop_x1=0.1, crop_y1=0.1, crop_x2=0.3, crop_y2=0.4
  media_id=1, owner_type='INDI', owner_id=43, is_primary=0, crop_x1=0.4, crop_y1=0.1, crop_x2=0.6, crop_y2=0.4
  media_id=1, owner_type='INDI', owner_id=44, is_primary=0, crop_x1=0.7, crop_y1=0.1, crop_x2=0.9, crop_y2=0.4
  media_id=1, owner_type='FAM',  owner_id=15, is_primary=1
  media_id=1, owner_type='EVENT', owner_id=200, is_primary=0
```

One file on disk, five references. Crop coordinates define face regions per person. The face detection pipeline automatically creates these references.

---

## 5. Plugin/Extension System

### 5.1 Architecture Comparison

| Approach | Sandbox | Speed | Language Support | Complexity | Best For |
|----------|---------|-------|-----------------|------------|----------|
| **WASM (Component Model)** | Strong | Near-native | Rust, C, Go, TS, Python (compiled) | High | Performance-critical, untrusted plugins |
| **JavaScript (V8 isolate)** | Medium | Fast | JS/TS only | Medium | Web-familiar devs, UI extensions |
| **Python (subprocess)** | Weak | Slow startup | Python | Low | Data science, existing genealogy libs |
| **Lua (embedded)** | Strong | Very fast | Lua | Low | Simple scripting, config |

### 5.2 Recommended: Hybrid WASM + JavaScript

**Tier 1: JavaScript plugins (easy, web-familiar)**
- Run in the WebView's existing JS context (or a sandboxed iframe)
- Access a controlled API surface via `window.gedfix` bridge
- Perfect for: UI customizations, report templates, custom views, import format adapters
- Example plugin manifest:
  ```json
  {
    "name": "ancestry-hints-plugin",
    "version": "1.0.0",
    "type": "js",
    "permissions": ["read:persons", "read:sources", "network:ancestry.com"],
    "entry": "index.js",
    "ui": { "sidebar": "panel.svelte" }
  }
  ```

**Tier 2: WASM plugins (performance, safety)**
- Run via Wasmtime embedded in the Rust backend
- Use the WASM Component Model with WIT interfaces
- Perfect for: GEDCOM format converters, DNA analysis algorithms, large-scale data processing
- Example WIT interface:
  ```wit
  package gedfix:plugin@1.0.0;

  interface types {
    record person {
      id: u64,
      given-name: string,
      surname: string,
      birth-date: string,
    }
  }

  interface data-access {
    use types.{person};
    get-person: func(id: u64) -> option<person>;
    search-persons: func(query: string, limit: u32) -> list<person>;
    get-events: func(person-id: u64) -> list<event>;
  }

  world gedfix-plugin {
    import data-access;
    export run: func() -> result<string, string>;
  }
  ```

**Tier 3: Python plugins (optional, subprocess-based)**
- Only if there is demand for Python genealogy libraries (ged4py, gramps APIs)
- Run as a subprocess with JSON-RPC communication
- Highest latency, weakest sandboxing — use sparingly

### 5.3 Plugin Security Sandboxing

```
+------------------+     +-------------------+     +------------------+
|   Plugin Store   |     |   Manifest Check  |     |   Permission     |
|   (download)     +---->|   (verify sig)    +---->|   Prompt (user)  |
+------------------+     +-------------------+     +--------+---------+
                                                            |
                         +----------------------------------+
                         |
              +----------v-----------+
              |   Plugin Type?       |
              +----------+-----------+
                         |
              +----------+-----------+----------+
              |                      |          |
     +--------v------+    +---------v---+  +---v--------+
     |   JS Plugin   |    | WASM Plugin |  | Python     |
     |   (iframe     |    | (Wasmtime   |  | (subprocess|
     |    sandbox)   |    |  sandbox)   |  |  + JSON)   |
     +--------+------+    +------+------+  +---+--------+
              |                  |              |
     +--------v------------------v--------------v--------+
     |              GedFix Plugin API Bridge              |
     |   (only exposes permitted operations per manifest) |
     +---------------------------------------------------+
```

### 5.4 Recommendation

Start with **JavaScript plugins only** (Tier 1). The development cost is minimal — the WebView already runs JS, and Svelte components can be loaded dynamically. Add WASM (Tier 2) when you have a concrete use case for CPU-intensive plugins. Skip Python unless users demand it.

---

## 6. Performance at Scale (100K+ People)

### 6.1 Virtual Scrolling

GedFix already uses `virtua` (v0.48.8) for virtual scrolling. This is a good choice. Key principles:

- **Render budget:** Keep DOM nodes under 300 for smooth 60fps scrolling
- **Overscan:** Render 5-10 items above/below the viewport for smooth scroll
- **Row height:** Fixed heights are 10x faster than variable; if variable is needed, cache measured heights
- **Key optimization:** When scrolling through 100K persons, the SQL query should use `LIMIT/OFFSET` or cursor-based pagination

**Recommended query pattern for paginated person list:**
```sql
-- Cursor-based pagination (faster than OFFSET for large datasets)
SELECT id, xref, given_name, surname, birth_date, death_date, is_living
FROM person
WHERE (surname, given_name, id) > (?, ?, ?)  -- cursor from last visible row
ORDER BY surname, given_name, id
LIMIT 100;
```

### 6.2 SQLite Indexing Strategy for Large Trees

Your existing PRAGMAs are well-tuned. Additional indexes for scale:

```sql
-- Composite indexes for common query patterns
CREATE INDEX idx_person_sort ON person(surname, given_name, id);
CREATE INDEX idx_person_living ON person(is_living, surname, given_name);
CREATE INDEX idx_event_owner_type ON event_ref(owner_type, owner_id, event_id);
CREATE INDEX idx_citation_quality ON citation(owner_type, owner_id, quality DESC);

-- Partial indexes for filtered views
CREATE INDEX idx_person_has_issues ON person(id) WHERE proof_status = 'DISPUTED';
CREATE INDEX idx_event_undated ON event(id) WHERE sort_date = 0;
CREATE INDEX idx_media_untagged ON media(id) WHERE id NOT IN (SELECT media_id FROM media_ref);

-- Covering indexes for list views (avoid table lookups)
CREATE INDEX idx_person_list ON person(surname, given_name, birth_date, death_date, sex, is_living);
```

**ANALYZE strategy:** Run `PRAGMA optimize` after every import and weekly during normal use.

### 6.3 Lazy Loading Architecture

```
Person List View                 Person Detail View
+------------------+            +---------------------------+
| Virtual scroll   |   click    |                           |
| showing 50 of    +----------->| Phase 1 (immediate):      |
| 100K persons     |            |   Name, dates, sex        |
|                  |            |   (from person table)     |
| Loads: id, name, |            |                           |
|   birth, death   |            | Phase 2 (50ms):           |
|   only           |            |   Events, families        |
+------------------+            |   (JOIN queries)          |
                                |                           |
                                | Phase 3 (200ms):          |
                                |   Media, sources, notes   |
                                |   (lazy-loaded on scroll) |
                                |                           |
                                | Phase 4 (background):     |
                                |   Face detection results  |
                                |   AI-generated content    |
                                +---------------------------+
```

### 6.4 Background Processing Architecture

```
+------------------+     +-----------------------+     +------------------+
|   Tauri Rust     |     |   Background Workers  |     |   WebView UI     |
|   Backend        |     |   (Rust threads)      |     |   (Svelte)       |
+------------------+     +-----------------------+     +------------------+
|                  |     |                       |     |                  |
| GEDCOM Import    +---->| Worker 1: Parse       +---->| Progress bar     |
|   command        |     |   GEDCOM lines        |     |   updates via    |
|                  |     |                       |     |   Tauri events   |
| Media Import     +---->| Worker 2: Hash files, |     |                  |
|   command        |     |   generate thumbs     |     | "Importing       |
|                  |     |                       |     |  3,247 of 12,500 |
| Face Detection   +---->| Worker 3: ONNX model  |     |  persons..."     |
|   command        |     |   inference           |     |                  |
|                  |     |                       |     |                  |
| FTS Rebuild      +---->| Worker 4: Populate    |     |                  |
|   command        |     |   FTS5 index          |     |                  |
+------------------+     +-----------------------+     +------------------+
                              |
                              | All workers use SQLite WAL mode
                              | (concurrent reads + one writer)
                              | Report progress via tauri::emit()
```

**Key implementation details:**
- Use Rust's `tokio::spawn_blocking` or `rayon` for CPU-bound work
- SQLite WAL mode allows concurrent reads during background writes (already configured in GedFix)
- Batch inserts in transactions of 1000 rows (already done in GEDCOM parser)
- Report progress via Tauri event system: `app.emit("import-progress", { pct: 45, msg: "..." })`

### 6.5 FTS5 Optimization for Large Datasets

Current FTS5 setup is solid. Enhancements for 100K+:

```sql
-- Extended FTS covering more searchable fields
CREATE VIRTUAL TABLE search_fts USING fts5(
    given_name, surname, birth_place, death_place,
    event_places, source_titles, note_content,
    content='', -- external content mode (manual population)
    tokenize='porter unicode61 remove_diacritics 2'
);

-- Prefix index for autocomplete (searches like "Mal*")
-- Already supported by porter tokenizer, but add:
CREATE VIRTUAL TABLE autocomplete_fts USING fts5(
    surname,
    tokenize='unicode61',
    prefix='2,3,4'  -- index 2,3,4 character prefixes
);
```

**Search priority for genealogy:**
1. Exact xref match (instant)
2. FTS5 full-text search (sub-millisecond with index)
3. Soundex/metaphone for phonetic surname matching (add a `surname_soundex` column)
4. Fuzzy match with Levenshtein distance (only for "Did you mean?" suggestions)

---

## 7. Overall System Architecture

```
+------------------------------------------------------------------+
|                        GedFix Desktop App                         |
+------------------------------------------------------------------+
|                                                                    |
|  +---------------------------+    +----------------------------+   |
|  |       WebView (UI)        |    |     Rust Backend           |   |
|  |  +---------------------+  |    |  +----------------------+  |   |
|  |  |  Svelte 5 + Kit     |  |    |  | Tauri 2 Core         |  |   |
|  |  |  - Virtual scroll    |  |    |  | - Command handlers   |  |   |
|  |  |  - Lazy loading      |  |    |  | - File system access |  |   |
|  |  |  - State management  |  |    |  | - Media processing   |  |   |
|  |  +---------------------+  |    |  +----------------------+  |   |
|  |  |  Tailwind CSS 4      |  |    |  | SQLite (WAL mode)    |  |   |
|  |  +---------------------+  |    |  | - FTS5 search         |  |   |
|  |  |  JS Plugin Host      |  |    |  | - CRDT sync meta     |  |   |
|  |  |  (iframe sandbox)    |  |    |  +----------------------+  |   |
|  |  +---------------------+  |    |  | Background Workers    |  |   |
|  +---------------------------+    |  | - GEDCOM import        |  |   |
|                                    |  | - Media pipeline       |  |   |
|  +---------------------------+    |  | - Face detection       |  |   |
|  |    Content-Addressable    |    |  | - FTS rebuild          |  |   |
|  |    Media Store            |    |  +----------------------+  |   |
|  |  ~/.gedfix/media/store/   |    |  | WASM Plugin Runtime   |  |   |
|  |  ~/.gedfix/media/thumbs/  |    |  | (Wasmtime, future)    |  |   |
|  +---------------------------+    +----------------------------+   |
|                                                                    |
+----------------------------+---------------------------------------+
                             |
                    +--------v--------+
                    |   Turso Cloud   |
                    |   (optional)    |
                    |  libSQL replica |
                    +-----------------+
```

---

## 8. Schema Migration Path (Current to Optimal)

Given that GedFix already has a working schema with data, here is the recommended migration sequence:

**Migration 1: Add missing tables (non-breaking)**
- Add `repository` table
- Add `face` table
- Add `sync_meta` table
- Add `media_ref` table (parallel to existing `media`)
- Add `name` table (parallel to existing `person.givenName`)
- Add `event_ref` table (parallel to existing `event.ownerXref`)

**Migration 2: Populate new tables from existing data**
- Populate `media_ref` from existing `media.ownerXref` data
- Populate `name` table from `person.givenName/surname/suffix` with `is_primary=1`
- Populate `event_ref` from `event.ownerXref/ownerType`
- Add `sort_date` columns and compute Julian day numbers from date strings
- Add `file_hash` to media and compute hashes for existing files

**Migration 3: Add indexes and FTS enhancements**
- Add composite and partial indexes
- Expand FTS5 to cover more fields
- Add `surname_soundex` computed column

**Migration 4: Deprecate old patterns**
- Mark `media.ownerXref` as deprecated (keep for backward compat)
- Mark `event.ownerXref/ownerType` as deprecated
- New code uses `media_ref` and `event_ref` exclusively

**Migration 5: (Future) Sync infrastructure**
- Add `_clock`, `_node`, `_deleted` columns to all mutable tables
- Switch from `@tauri-apps/plugin-sql` to Turso's libSQL when ready

---

## Sources

- [Elanis/web-to-desktop-framework-comparison](https://github.com/Elanis/web-to-desktop-framework-comparison) — Framework benchmarks
- [Tauri vs Electron: Real Trade-offs (Hopp.app)](https://www.gethopp.app/blog/tauri-vs-electron) — Real-world benchmarks
- [Cross-Platform Tools Comparison 2026](https://codenote.net/en/posts/cross-platform-dev-tools-comparison-2026/) — Framework landscape
- [SQLite Tools for RootsMagic](https://sqlitetoolsforrootsmagic.com/) — RootsMagic schema documentation
- [RootsMagic 10 SQLite Deep Dive](https://sqlitetoolsforrootsmagic.com/unlocking-your-rootsmagic-10-data-a-deep-dive-into-sqlite-queries-with-ai/) — RM10 table structure
- [Database Design for Genealogy Data (Louis Kessler)](https://www.beholdgenealogy.com/blog/?p=1560) — Schema design philosophy
- [Gramps SQL Database](https://www.gramps-project.org/wiki/index.php/Gramps_SQL_Database) — Gramps schema
- [Gramps Database Backends](https://www.gramps-project.org/wiki/index.php/Database_Backends) — BSDDB to SQLite evolution
- [Gramps Database Implementations (DeepWiki)](https://deepwiki.com/gramps-project/gramps/3.2-database-implementations) — Technical deep dive
- [GEDCOM 5.5.1 to 7.0 Migration Guide](https://gedcom.io/migrate/) — Official FamilySearch migration
- [FamilySearch GEDCOM 7.0 Specification](https://gedcom.io/specifications/FamilySearchGEDCOMv7.html) — Full spec
- [GEDCOM FAQ](https://www.gedcom.org/faq.html) — Version differences
- [cr-sqlite (vlcn.io)](https://github.com/vlcn-io/cr-sqlite) — CRDT SQLite extension
- [SQLite Sync](https://www.sqlite.ai/sqlite-sync) — CRDT-powered SQLite sync
- [Local-First Apps in 2025 (debugg.ai)](https://debugg.ai/resources/local-first-apps-2025-crdts-replication-edge-storage-offline-sync) — CRDT landscape
- [Turso: Databases Anywhere](https://turso.tech/blog/introducing-databases-anywhere-with-turso-sync) — Turso Sync
- [PowerSync](https://www.powersync.com/) — SQLite sync engine
- [SQLite Renaissance 2026](https://dev.to/pockit_tools/the-sqlite-renaissance-why-the-worlds-most-deployed-database-is-taking-over-production-in-2026-3jcc) — SQLite ecosystem overview
- [WASM Component Model Plugin System](https://dev.to/topheman/webassembly-component-model-building-a-plugin-system-58o0) — WASM plugins
- [WASM Plugins (Sy Brand)](https://tartanllama.xyz/posts/wasm-plugins/) — Native plugin systems with WASM
- [VS Code WASM Extensions](https://code.visualstudio.com/blogs/2024/05/08/wasm) — Microsoft's WASM extension approach
- [face-api.js (vladmandic)](https://github.com/vladmandic/face-api) — Face detection in JS
- [Virtual Scrolling for Billions of Rows (HighTable)](https://blog.hyperparam.app/hightable-scrolling-billions-of-rows/) — Scrolling techniques
- [SQLite Performance Tuning (phiresky)](https://phiresky.github.io/blog/2020/sqlite-performance-tuning/) — 100K+ SELECTs per second
