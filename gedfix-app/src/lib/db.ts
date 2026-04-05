import { isTauri, type PlatformDatabase } from './platform';
import type { Person, Family, GedcomEvent, Source, GedcomMedia, ChildLink, AppStats, Place, PersonGroup, Association, AlternateName, ResearchLogEntry, Citation, ResearchNote, ResearchTask, Bookmark, AIChatMessage, TreeIssue, GeneratedStory, Proposal, ChangeLogEntry, QualityRule, AgentRun } from './types';

let db: PlatformDatabase | null = null;

type UndoMetadata = {
  tableName?: string;
  rowId?: string;
  oldData?: unknown;
  newData?: unknown;
};

async function pushUndoAction(
  description: string,
  undo: () => Promise<void>,
  redo: () => Promise<void>,
  metadata?: UndoMetadata
): Promise<void> {
  const { undoManager } = await import('./undo-manager');
  await undoManager.push(
    {
      id: crypto.randomUUID(),
      description,
      timestamp: new Date().toISOString(),
      undo,
      redo,
    },
    metadata
  );
}

export function parseDateForSort(dateValue: string | null | undefined): number {
  if (!dateValue) return 0;
  const trimmed = dateValue.trim();
  if (!trimmed) return 0;
  const match = trimmed.match(/(\d{4})/);
  return match ? parseInt(match[1], 10) : 0;
}

export async function getDb(): Promise<PlatformDatabase> {
  if (!db) {
    if (isTauri()) {
      const { default: Database } = await import('@tauri-apps/plugin-sql');
      db = await Database.load('sqlite:gedfix.db');
    } else {
      const { loadWebDatabase } = await import('./platform-db');
      db = await loadWebDatabase();
    }
    await createTables();
    if (isTauri()) await optimizeDb();
  }
  return db;
}

async function optimizeDb() {
  const d = db!;
  // Performance PRAGMAs — researched optimal values for desktop genealogy app
  await d.execute("PRAGMA journal_mode = WAL");          // 4x write throughput
  await d.execute("PRAGMA synchronous = NORMAL");        // Safe in WAL, 2x faster
  await d.execute("PRAGMA cache_size = -32000");         // 32MB page cache
  await d.execute("PRAGMA mmap_size = 268435456");       // 256MB memory-mapped I/O
  await d.execute("PRAGMA temp_store = MEMORY");         // Temp tables in RAM
  await d.execute("PRAGMA foreign_keys = ON");
  await d.execute("PRAGMA busy_timeout = 5000");         // 5s retry on lock
}

async function createTables() {
  const d = db!;

  async function ensureColumn(table: string, column: string, definition: string): Promise<void> {
    const cols = await d.select<{ name: string }[]>(`PRAGMA table_info(${table})`);
    if (!cols.some(c => c.name === column)) {
      try {
        await d.execute(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        console.warn(`Failed to add column ${table}.${column}: ${message}`);
      }
    }
  }

  await d.execute(`CREATE TABLE IF NOT EXISTS person (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xref TEXT UNIQUE NOT NULL,
    givenName TEXT DEFAULT '',
    surname TEXT DEFAULT '',
    suffix TEXT DEFAULT '',
    sex TEXT DEFAULT 'U',
    isLiving INTEGER DEFAULT 0,
    birthDate TEXT DEFAULT '',
    birthPlace TEXT DEFAULT '',
    deathDate TEXT DEFAULT '',
    deathPlace TEXT DEFAULT '',
    sourceCount INTEGER DEFAULT 0,
    mediaCount INTEGER DEFAULT 0,
    personColor TEXT DEFAULT '',
    proofStatus TEXT DEFAULT 'UNKNOWN'
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS family (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xref TEXT UNIQUE NOT NULL,
    partner1Xref TEXT DEFAULT '',
    partner2Xref TEXT DEFAULT '',
    marriageDate TEXT DEFAULT '',
    marriagePlace TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ownerXref TEXT NOT NULL,
    ownerType TEXT NOT NULL,
    eventType TEXT NOT NULL,
    dateValue TEXT DEFAULT '',
    place TEXT DEFAULT '',
    description TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS source (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xref TEXT UNIQUE NOT NULL,
    title TEXT DEFAULT '',
    author TEXT DEFAULT '',
    publisher TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS media (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xref TEXT DEFAULT '',
    ownerXref TEXT DEFAULT '',
    filePath TEXT DEFAULT '',
    format TEXT DEFAULT '',
    title TEXT DEFAULT '',
    category TEXT NOT NULL DEFAULT 'uncategorized'
  )`);
  await ensureColumn('media', 'category', `TEXT NOT NULL DEFAULT 'uncategorized'`);

  await d.execute(`CREATE TABLE IF NOT EXISTS media_person_link (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mediaId INTEGER NOT NULL,
    personXref TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'tagged',
    isPrimary INTEGER NOT NULL DEFAULT 0,
    sortOrder INTEGER NOT NULL DEFAULT 0,
    faceX REAL,
    faceY REAL,
    faceW REAL,
    faceH REAL,
    caption TEXT DEFAULT '',
    addedBy TEXT DEFAULT 'gedcom_import',
    verified INTEGER NOT NULL DEFAULT 0,
    createdAt TEXT NOT NULL DEFAULT '',
    UNIQUE(mediaId, personXref)
  )`);

  // Upgrade older databases in place to match current media/person link shape.
  await ensureColumn('media_person_link', 'role', `TEXT NOT NULL DEFAULT 'tagged'`);
  await ensureColumn('media_person_link', 'isPrimary', `INTEGER NOT NULL DEFAULT 0`);
  await ensureColumn('media_person_link', 'sortOrder', `INTEGER NOT NULL DEFAULT 0`);
  await ensureColumn('media_person_link', 'faceX', `REAL`);
  await ensureColumn('media_person_link', 'faceY', `REAL`);
  await ensureColumn('media_person_link', 'faceW', `REAL`);
  await ensureColumn('media_person_link', 'faceH', `REAL`);
  await ensureColumn('media_person_link', 'caption', `TEXT DEFAULT ''`);
  await ensureColumn('media_person_link', 'addedBy', `TEXT DEFAULT 'gedcom_import'`);
  await ensureColumn('media_person_link', 'verified', `INTEGER NOT NULL DEFAULT 0`);
  await ensureColumn('media_person_link', 'createdAt', `TEXT NOT NULL DEFAULT ''`);

  await d.execute(`CREATE TABLE IF NOT EXISTS child_link (
    familyXref TEXT NOT NULL,
    childXref TEXT NOT NULL,
    childOrder INTEGER DEFAULT 0,
    PRIMARY KEY (familyXref, childXref)
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS place (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    normalized TEXT DEFAULT '',
    latitude REAL,
    longitude REAL,
    eventCount INTEGER DEFAULT 0
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS custom_group (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    color TEXT DEFAULT '',
    description TEXT DEFAULT '',
    createdAt TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS group_member (
    groupId INTEGER NOT NULL,
    personXref TEXT NOT NULL,
    addedAt TEXT DEFAULT '',
    PRIMARY KEY (groupId, personXref)
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS association (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    person1Xref TEXT NOT NULL,
    person2Xref TEXT NOT NULL,
    relationshipType TEXT DEFAULT '',
    description TEXT DEFAULT '',
    createdAt TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS alternate_name (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    personXref TEXT NOT NULL,
    givenName TEXT DEFAULT '',
    surname TEXT DEFAULT '',
    suffix TEXT DEFAULT '',
    nameType TEXT DEFAULT '',
    source TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS research_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    personXref TEXT DEFAULT '',
    repository TEXT DEFAULT '',
    searchTerms TEXT DEFAULT '',
    recordsViewed TEXT DEFAULT '',
    conclusion TEXT DEFAULT '',
    sourceXref TEXT DEFAULT '',
    resultType TEXT DEFAULT 'NEGATIVE',
    searchDate TEXT DEFAULT '',
    createdAt TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS citation (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sourceXref TEXT DEFAULT '',
    personXref TEXT DEFAULT '',
    eventId TEXT DEFAULT '',
    page TEXT DEFAULT '',
    quality TEXT DEFAULT 'UNKNOWN',
    text TEXT DEFAULT '',
    note TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS note (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ownerXref TEXT DEFAULT '',
    ownerType TEXT DEFAULT 'INDI',
    title TEXT DEFAULT '',
    content TEXT DEFAULT '',
    createdAt TEXT DEFAULT '',
    updatedAt TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS research_task (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    personXref TEXT DEFAULT '',
    title TEXT DEFAULT '',
    description TEXT DEFAULT '',
    status TEXT DEFAULT 'TODO',
    priority TEXT DEFAULT 'MEDIUM',
    dueDate TEXT DEFAULT '',
    createdAt TEXT DEFAULT '',
    completedAt TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS bookmark (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    personXref TEXT NOT NULL,
    label TEXT DEFAULT '',
    category TEXT DEFAULT 'General',
    sortOrder INTEGER DEFAULT 0,
    createdAt TEXT DEFAULT ''
  )`);
  await ensureColumn('bookmark', 'category', `TEXT DEFAULT 'General'`);
  await ensureColumn('bookmark', 'sortOrder', `INTEGER DEFAULT 0`);

  await d.execute(`CREATE TABLE IF NOT EXISTS ai_chat (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    provider TEXT NOT NULL,
    model TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    personXref TEXT DEFAULT '',
    timestamp TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS dismissed_issue (
    issueKey TEXT PRIMARY KEY,
    dismissedAt TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS stories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    personXref TEXT DEFAULT '',
    familyXref TEXT DEFAULT '',
    storyType TEXT DEFAULT 'single',
    title TEXT DEFAULT '',
    content TEXT DEFAULT '',
    provider TEXT DEFAULT '',
    model TEXT DEFAULT '',
    tokensUsed INTEGER DEFAULT 0,
    costEstimate REAL DEFAULT 0,
    createdAt TEXT DEFAULT ''
  )`);

  // Indexes
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_person_surname ON person(surname)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_event_owner ON event(ownerXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_media_owner ON media(ownerXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_media_category ON media(category)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_mpl_person ON media_person_link(personXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_mpl_media ON media_person_link(mediaId)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_mpl_primary ON media_person_link(personXref, isPrimary)`);
  // Ensure one link per media/person pair
  await d.execute(`DELETE FROM media_person_link WHERE rowid NOT IN (
    SELECT MIN(rowid) FROM media_person_link GROUP BY mediaId, personXref
  )`);
  await d.execute(`CREATE UNIQUE INDEX IF NOT EXISTS idx_mpl_unique ON media_person_link(mediaId, personXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_family_partner1 ON family(partner1Xref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_family_partner2 ON family(partner2Xref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_child_link_family ON child_link(familyXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_child_link_child ON child_link(childXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_place_name ON place(name)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_citation_person ON citation(personXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_citation_source ON citation(sourceXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_note_owner ON note(ownerXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_task_person ON research_task(personXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_rlog_person ON research_log(personXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_assoc_p1 ON association(person1Xref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_assoc_p2 ON association(person2Xref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_altname_person ON alternate_name(personXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_gm_group ON group_member(groupId)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_bookmark_person ON bookmark(personXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_bookmark_category ON bookmark(category)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_bookmark_sort ON bookmark(sortOrder)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_ai_chat_timestamp ON ai_chat(timestamp)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_stories_person ON stories(personXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_stories_family ON stories(familyXref)`);

  await d.execute(`CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT DEFAULT '')`);
  await d.execute(`CREATE TABLE IF NOT EXISTS undo_log (
    id TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    tableName TEXT NOT NULL,
    rowId TEXT NOT NULL,
    oldData TEXT DEFAULT '',
    newData TEXT DEFAULT '',
    createdAt TEXT DEFAULT (datetime('now'))
  )`);
  await d.execute(`CREATE TABLE IF NOT EXISTS evidence (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    personXref TEXT NOT NULL,
    factType TEXT NOT NULL,
    factValue TEXT NOT NULL,
    sourceXref TEXT DEFAULT '',
    informationType TEXT NOT NULL DEFAULT 'undetermined',
    evidenceType TEXT NOT NULL DEFAULT 'indirect',
    quality TEXT NOT NULL DEFAULT 'undetermined',
    analysisNotes TEXT DEFAULT '',
    createdAt TEXT DEFAULT (datetime('now'))
  )`);
  await d.execute(`CREATE TABLE IF NOT EXISTS gps_checklist (
    personXref TEXT NOT NULL,
    item INTEGER NOT NULL,
    completed INTEGER DEFAULT 0,
    notes TEXT DEFAULT '',
    PRIMARY KEY (personXref, item)
  )`);

  // ===== Agentic Proposal System =====
  await d.execute(`CREATE TABLE IF NOT EXISTS proposal (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    agentRunId TEXT NOT NULL,
    entityType TEXT NOT NULL,
    entityId TEXT NOT NULL,
    fieldName TEXT NOT NULL,
    oldValue TEXT DEFAULT '',
    newValue TEXT DEFAULT '',
    confidence REAL DEFAULT 0.5,
    reasoning TEXT DEFAULT '',
    evidenceSource TEXT DEFAULT '',
    status TEXT DEFAULT 'pending' CHECK(status IN ('pending','approved','rejected','superseded')),
    createdAt TEXT DEFAULT (datetime('now')),
    resolvedAt TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS change_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    proposalId INTEGER REFERENCES proposal(id),
    entityType TEXT NOT NULL,
    entityId TEXT NOT NULL,
    fieldName TEXT NOT NULL,
    oldValue TEXT DEFAULT '',
    newValue TEXT DEFAULT '',
    actor TEXT NOT NULL,
    appliedAt TEXT DEFAULT (datetime('now')),
    undoneAt TEXT DEFAULT ''
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS quality_rule (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    description TEXT DEFAULT '',
    ruleType TEXT NOT NULL CHECK(ruleType IN ('reject','warn','require_evidence')),
    ruleKey TEXT UNIQUE NOT NULL,
    isActive INTEGER DEFAULT 1
  )`);

  await d.execute(`CREATE TABLE IF NOT EXISTS agent_run (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    runId TEXT UNIQUE NOT NULL,
    provider TEXT DEFAULT '',
    model TEXT DEFAULT '',
    personXref TEXT DEFAULT '',
    proposalCount INTEGER DEFAULT 0,
    status TEXT DEFAULT 'running',
    startedAt TEXT DEFAULT (datetime('now')),
    completedAt TEXT DEFAULT ''
  )`);

  // Merge log — stores pre-merge snapshots for undo
  await d.execute(`CREATE TABLE IF NOT EXISTS merge_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    keepXref TEXT NOT NULL,
    removeXref TEXT NOT NULL,
    removedPersonJson TEXT NOT NULL,
    removedEventsJson TEXT DEFAULT '[]',
    removedMediaLinksJson TEXT DEFAULT '[]',
    removedChildLinksJson TEXT DEFAULT '[]',
    mergedAt TEXT DEFAULT (datetime('now')),
    undoneAt TEXT DEFAULT ''
  )`);

  // Proposal system indexes
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_proposal_entity ON proposal(entityId)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_proposal_status ON proposal(status)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_proposal_run ON proposal(agentRunId)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_changelog_proposal ON change_log(proposalId)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_agentrun_person ON agent_run(personXref)`);

  // Seed default quality rules
  await d.execute(`INSERT OR IGNORE INTO quality_rule (name, description, ruleType, ruleKey) VALUES
    ('No future dates', 'Reject proposals that set dates in the future', 'reject', 'no_future_dates'),
    ('Birth before death', 'Reject if proposed birth date is after death date', 'reject', 'birth_before_death'),
    ('Low confidence requires evidence', 'Require evidence source when confidence is below 0.6', 'require_evidence', 'low_confidence_evidence'),
    ('No deleting cited fields', 'Warn when clearing a field that has source citations', 'warn', 'no_delete_cited'),
    ('Reasonable age', 'Reject if proposed dates imply age over 120 years', 'reject', 'reasonable_age'),
    ('No duplicate proposals', 'Reject if an identical pending proposal already exists', 'reject', 'no_duplicates')
  `);

  // Source type classification + person validation status
  await ensureColumn('source', 'sourceType', `TEXT DEFAULT 'unknown'`);
  await ensureColumn('person', 'validationStatus', `TEXT DEFAULT 'unvalidated'`);
  await classifySources();
  await computeValidationStatus();

  // FTS5 is created after import via rebuildFTS()
}

// ===== Source classification =====

export async function classifySources(): Promise<number> {
  const d = await getDb();
  // Online trees
  const treePatterns = [
    '%ancestry%tree%', '%ancestry%member%', '%familysearch%family%tree%',
    '%myheritage%tree%', '%findmypast%tree%', '%geni.com%',
    '%wikitree%', '%family tree maker%'
  ];
  for (const pattern of treePatterns) {
    await d.execute(
      `UPDATE source SET sourceType = 'online_tree' WHERE sourceType = 'unknown' AND (LOWER(title) LIKE $1 OR LOWER(publisher) LIKE $1)`,
      [pattern]
    );
  }
  // Vital records
  const vitalPatterns = ['%birth%record%', '%death%record%', '%marriage%record%',
    '%divorce%', '%certificate%', '%vital%record%', '%civil%registration%'];
  for (const pattern of vitalPatterns) {
    await d.execute(
      `UPDATE source SET sourceType = 'vital_record' WHERE sourceType = 'unknown' AND LOWER(title) LIKE $1`,
      [pattern]
    );
  }
  // Census
  await d.execute(`UPDATE source SET sourceType = 'census' WHERE sourceType = 'unknown' AND LOWER(title) LIKE '%census%'`);
  // Newspaper
  await d.execute(
    `UPDATE source SET sourceType = 'newspaper' WHERE sourceType = 'unknown' AND (LOWER(title) LIKE '%newspaper%' OR LOWER(publisher) LIKE '%newspaper%' OR LOWER(title) LIKE '%chronicle%' OR LOWER(title) LIKE '%gazette%' OR LOWER(title) LIKE '%herald%')`
  );
  // Church records
  await d.execute(
    `UPDATE source SET sourceType = 'church_record' WHERE sourceType = 'unknown' AND (LOWER(title) LIKE '%church%' OR LOWER(title) LIKE '%parish%' OR LOWER(title) LIKE '%baptis%' OR LOWER(title) LIKE '%christening%')`
  );
  // Military
  await d.execute(
    `UPDATE source SET sourceType = 'military' WHERE sourceType = 'unknown' AND (LOWER(title) LIKE '%military%' OR LOWER(title) LIKE '%draft%' OR LOWER(title) LIKE '%enlistment%' OR LOWER(title) LIKE '%pension%' OR LOWER(title) LIKE '%service record%')`
  );
  // Immigration
  await d.execute(
    `UPDATE source SET sourceType = 'immigration' WHERE sourceType = 'unknown' AND (LOWER(title) LIKE '%immigration%' OR LOWER(title) LIKE '%passenger%' OR LOWER(title) LIKE '%naturalization%' OR LOWER(title) LIKE '%ship%manifest%')`
  );
  const result = await d.select<{ c: number }[]>(`SELECT COUNT(*) as c FROM source WHERE sourceType != 'unknown'`);
  return result[0]?.c ?? 0;
}

export async function computeValidationStatus(): Promise<void> {
  const d = await getDb();
  // Reset all to unvalidated first
  await d.execute(`UPDATE person SET validationStatus = 'unvalidated' WHERE validationStatus IS NULL OR validationStatus = ''`);
  // Persons with at least one non-tree source citation → validated
  await d.execute(`
    UPDATE person SET validationStatus = 'validated'
    WHERE xref IN (
      SELECT DISTINCT c.personXref FROM citation c
      JOIN source s ON s.xref = c.sourceXref
      WHERE s.sourceType NOT IN ('online_tree', 'unknown')
    )
  `);
  // Persons with only tree sources → tree_only
  await d.execute(`
    UPDATE person SET validationStatus = 'tree_only'
    WHERE validationStatus = 'unvalidated'
    AND xref IN (
      SELECT DISTINCT c.personXref FROM citation c
      JOIN source s ON s.xref = c.sourceXref
      WHERE s.sourceType = 'online_tree'
    )
  `);
}

export async function updateSourceType(xref: string, sourceType: string): Promise<void> {
  const d = await getDb();
  await d.execute(`UPDATE source SET sourceType = $1 WHERE xref = $2`, [sourceType, xref]);
}

// ===== Person queries =====

export async function getPersons(search?: string, limit = 500): Promise<Person[]> {
  const d = await getDb();
  if (search && search.trim()) {
    const term = search.trim();
    // Try FTS5 first (sub-millisecond), fall back to LIKE
    try {
      const ftsQuery = term.split(/\s+/).map(w => `"${w}"*`).join(' ');
      const base = await d.select<Person[]>(
        `SELECT p.* FROM person p JOIN person_fts f ON p.id = f.rowid
         WHERE person_fts MATCH $1 ORDER BY rank LIMIT $2`,
        [ftsQuery, limit]
      );
      const q = `%${term}%`;
      const alt = await d.select<Person[]>(
        `SELECT DISTINCT p.* FROM person p
         JOIN alternate_name a ON a.personXref = p.xref
         WHERE a.givenName LIKE $1 OR a.surname LIKE $1 OR a.nameType LIKE $1
         LIMIT $2`,
        [q, limit]
      );
      const map = new Map<string, Person>();
      for (const p of base) map.set(p.xref, p);
      for (const p of alt) map.set(p.xref, p);
      return Array.from(map.values()).slice(0, limit);
    } catch {
      const q = `%${term}%`;
      const base = await d.select<Person[]>(
        `SELECT * FROM person WHERE givenName LIKE $1 OR surname LIKE $1 OR xref LIKE $1 ORDER BY surname, givenName LIMIT $2`,
        [q, limit]
      );
      const alt = await d.select<Person[]>(
        `SELECT DISTINCT p.* FROM person p
         JOIN alternate_name a ON a.personXref = p.xref
         WHERE a.givenName LIKE $1 OR a.surname LIKE $1 OR a.nameType LIKE $1
         ORDER BY p.surname, p.givenName LIMIT $2`,
        [q, limit]
      );
      const map = new Map<string, Person>();
      for (const p of base) map.set(p.xref, p);
      for (const p of alt) map.set(p.xref, p);
      return Array.from(map.values()).slice(0, limit);
    }
  }
  return await d.select<Person[]>(
    `SELECT * FROM person ORDER BY surname, givenName LIMIT $1`,
    [limit]
  );
}

export async function getAllPersons(): Promise<Person[]> {
  const d = await getDb();
  return await d.select<Person[]>(`SELECT * FROM person ORDER BY surname, givenName`);
}

export async function getPerson(xref: string): Promise<Person | null> {
  const d = await getDb();
  const rows = await d.select<Person[]>(`SELECT * FROM person WHERE xref = $1`, [xref]);
  return rows.length > 0 ? rows[0] : null;
}

export async function updatePerson(xref: string, data: Partial<Person>): Promise<void> {
  const d = await getDb();
  const before = await getPerson(xref);
  if (!before) return;
  const validFields = SAFE_FIELDS.person;
  const sets: string[] = [];
  const vals: any[] = [];
  const oldData: Record<string, unknown> = {};
  const newData: Record<string, unknown> = {};
  let i = 1;
  for (const [k, v] of Object.entries(data)) {
    if (k === 'id' || k === 'xref') continue;
    if (!validFields.has(k)) continue;
    sets.push(`${k} = $${i}`);
    vals.push(v);
    oldData[k] = (before as any)[k];
    newData[k] = v;
    i++;
  }
  if (sets.length === 0) return;
  vals.push(xref);
  await d.execute(`UPDATE person SET ${sets.join(', ')} WHERE xref = $${i}`, vals);
  await pushUndoAction(
    `Updated person ${xref}`,
    async () => {
      const db = await getDb();
      const undoSets = Object.keys(oldData).map((k, idx) => `${k} = $${idx + 1}`);
      await db.execute(
        `UPDATE person SET ${undoSets.join(', ')} WHERE xref = $${undoSets.length + 1}`,
        [...Object.values(oldData), xref]
      );
    },
    async () => {
      const db = await getDb();
      const redoSets = Object.keys(newData).map((k, idx) => `${k} = $${idx + 1}`);
      await db.execute(
        `UPDATE person SET ${redoSets.join(', ')} WHERE xref = $${redoSets.length + 1}`,
        [...Object.values(newData), xref]
      );
    },
    { tableName: 'person', rowId: xref, oldData, newData }
  );
}

export async function updatePersonField(xref: string, field: string, value: string): Promise<void> {
  const d = await getDb();
  if (!SAFE_FIELDS.person.has(field)) throw new Error(`Invalid person field ${field}`);
  const before = await getPerson(xref);
  if (!before) return;
  const oldValue = String((before as any)[field] ?? '');
  await d.execute(`UPDATE person SET ${field} = $1 WHERE xref = $2`, [value, xref]);
  const { undoManager } = await import('./undo-manager');
  await undoManager.push({
    id: crypto.randomUUID(),
    description: `Changed ${field} for ${before.givenName} ${before.surname}`.trim(),
    timestamp: new Date().toISOString(),
    undo: async () => {
      const db = await getDb();
      await db.execute(`UPDATE person SET ${field} = $1 WHERE xref = $2`, [oldValue, xref]);
    },
    redo: async () => {
      const db = await getDb();
      await db.execute(`UPDATE person SET ${field} = $1 WHERE xref = $2`, [value, xref]);
    },
  }, { tableName: 'person', rowId: xref, oldData: { [field]: oldValue }, newData: { [field]: value } });
}

// ===== Event queries =====

export async function getEvents(ownerXref: string): Promise<GedcomEvent[]> {
  const d = await getDb();
  return await d.select<GedcomEvent[]>(
    `SELECT * FROM event WHERE ownerXref = $1 ORDER BY dateValue`,
    [ownerXref]
  );
}

export async function getAllEvents(): Promise<GedcomEvent[]> {
  const d = await getDb();
  return await d.select<GedcomEvent[]>(`SELECT * FROM event ORDER BY dateValue`);
}

// ===== Family queries =====

export async function getFamilies(): Promise<Family[]> {
  const d = await getDb();
  return await d.select<Family[]>(`SELECT * FROM family ORDER BY xref`);
}

export async function getFamily(xref: string): Promise<Family | null> {
  const d = await getDb();
  const rows = await d.select<Family[]>(`SELECT * FROM family WHERE xref = $1`, [xref]);
  return rows.length > 0 ? rows[0] : null;
}

export async function getSpouseFamilies(personXref: string): Promise<Family[]> {
  const d = await getDb();
  return await d.select<Family[]>(
    `SELECT * FROM family WHERE partner1Xref = $1 OR partner2Xref = $1`,
    [personXref]
  );
}

export async function getParentFamily(childXref: string): Promise<Family | null> {
  const d = await getDb();
  const links = await d.select<ChildLink[]>(
    `SELECT * FROM child_link WHERE childXref = $1 LIMIT 1`,
    [childXref]
  );
  if (links.length === 0) return null;
  return await getFamily(links[0].familyXref);
}

export async function getParents(personXref: string): Promise<{ father: Person | null; mother: Person | null }> {
  const d = await getDb();
  const rows = await d.select<(Person & { _role: string })[]>(
    `SELECT p.*, CASE WHEN f.partner1Xref = p.xref THEN 'father' ELSE 'mother' END as _role
     FROM child_link cl
     JOIN family f ON cl.familyXref = f.xref
     JOIN person p ON p.xref IN (f.partner1Xref, f.partner2Xref)
     WHERE cl.childXref = $1`,
    [personXref]
  );
  let father: Person | null = null;
  let mother: Person | null = null;
  for (const r of rows) {
    if (r._role === 'father') father = r;
    else mother = r;
  }
  return { father, mother };
}

export async function getChildren(familyXref: string): Promise<Person[]> {
  const d = await getDb();
  return await d.select<Person[]>(
    `SELECT p.* FROM person p JOIN child_link cl ON p.xref = cl.childXref WHERE cl.familyXref = $1 ORDER BY cl.childOrder`,
    [familyXref]
  );
}

// ===== Source queries =====

export async function getSources(): Promise<Source[]> {
  const d = await getDb();
  return await d.select<Source[]>(`SELECT * FROM source ORDER BY title`);
}

// ===== Media queries =====

export async function getMediaForPerson(ownerXref: string): Promise<(GedcomMedia & { isPrimary?: boolean; role?: string })[]> {
  const d = await getDb();
  // Try junction table first
  const junction = await d.select<(GedcomMedia & { isPrimary: boolean; role: string })[]>(`
    SELECT m.*, mpl.isPrimary, mpl.role
    FROM media m
    JOIN media_person_link mpl ON m.id = mpl.mediaId
    WHERE mpl.personXref = $1
    ORDER BY mpl.isPrimary DESC, mpl.sortOrder
  `, [ownerXref]);
  if (junction.length > 0) return junction;
  // Fallback to old ownerXref pattern
  return await d.select<GedcomMedia[]>(`SELECT * FROM media WHERE ownerXref = $1`, [ownerXref]);
}

export async function getAllMedia(): Promise<GedcomMedia[]> {
  const d = await getDb();
  return await d.select<GedcomMedia[]>(`SELECT * FROM media WHERE filePath != '' ORDER BY title`);
}

export async function getAllMediaWithPersons(): Promise<(GedcomMedia & { personGivenName: string; personSurname: string; personSex: string; personBirthDate: string; personDeathDate: string })[]> {
  const d = await getDb();
  return await d.select(
    `SELECT m.*, COALESCE(p.givenName, '') as personGivenName, COALESCE(p.surname, '') as personSurname,
            COALESCE(p.sex, 'U') as personSex, COALESCE(p.birthDate, '') as personBirthDate, COALESCE(p.deathDate, '') as personDeathDate
     FROM media m LEFT JOIN person p ON m.ownerXref = p.xref
     WHERE m.filePath != '' ORDER BY m.title`
  );
}

export async function getPrimaryPhoto(ownerXref: string): Promise<GedcomMedia | null> {
  const d = await getDb();
  const rows = await d.select<(GedcomMedia & { linkCaption: string })[]>(
    `SELECT m.*, COALESCE(mpl.caption, '') as linkCaption FROM media m
     JOIN media_person_link mpl ON mpl.mediaId = m.id
     WHERE mpl.personXref = $1 AND m.filePath != ''
     ORDER BY mpl.isPrimary DESC, m.id ASC
     LIMIT 1`,
    [ownerXref]
  );
  if (rows.length > 0) {
    const row = rows[0];
    // Per-person crop in junction caption takes priority over media title
    if (row.linkCaption?.startsWith('crop:')) {
      row.title = row.linkCaption;
    }
    return row;
  }
  // Fallback: legacy format = 'PRIMARY' on media table
  const legacy = await d.select<GedcomMedia[]>(
    `SELECT * FROM media WHERE ownerXref = $1 AND filePath != '' AND (format = 'PRIMARY' OR filePath LIKE '%.jpg' OR filePath LIKE '%.jpeg' OR filePath LIKE '%.png' OR filePath LIKE '%.gif') ORDER BY CASE WHEN format = 'PRIMARY' THEN 0 ELSE 1 END LIMIT 1`,
    [ownerXref]
  );
  return legacy.length > 0 ? legacy[0] : null;
}

export async function getMediaWithPaths(ownerXref: string): Promise<GedcomMedia[]> {
  const d = await getDb();
  // Try junction table first
  const junction = await d.select<GedcomMedia[]>(`
    SELECT m.* FROM media m
    JOIN media_person_link mpl ON m.id = mpl.mediaId
    WHERE mpl.personXref = $1 AND m.filePath != ''
    ORDER BY mpl.isPrimary DESC, mpl.sortOrder, m.title
  `, [ownerXref]);
  if (junction.length > 0) return junction;
  // Fallback to old ownerXref pattern
  return await d.select<GedcomMedia[]>(
    `SELECT * FROM media WHERE ownerXref = $1 AND filePath != '' ORDER BY CASE WHEN format = 'PRIMARY' THEN 0 ELSE 1 END, title`,
    [ownerXref]
  );
}

export async function getPeopleForMedia(mediaId: number): Promise<{ personXref: string; givenName: string; surname: string; isPrimary: boolean }[]> {
  const d = await getDb();
  return await d.select<{ personXref: string; givenName: string; surname: string; isPrimary: boolean }[]>(`
    SELECT p.xref as personXref, p.givenName, p.surname, mpl.isPrimary
    FROM media_person_link mpl
    JOIN person p ON p.xref = mpl.personXref
    WHERE mpl.mediaId = $1
    ORDER BY mpl.isPrimary DESC
  `, [mediaId]);
}

export async function linkMediaToPerson(mediaId: number, personXref: string, isPrimary: boolean = false, role: string = 'tagged'): Promise<void> {
  const d = await getDb();
  const before = await d.select<any[]>(
    `SELECT * FROM media_person_link WHERE mediaId = $1 AND personXref = $2`,
    [mediaId, personXref]
  );
  await d.execute(`
    INSERT OR IGNORE INTO media_person_link (mediaId, personXref, isPrimary, role, addedBy, createdAt)
    VALUES ($1, $2, $3, $4, 'gedcom_import', datetime('now'))
  `, [mediaId, personXref, isPrimary ? 1 : 0, role]);
  const after = await d.select<any[]>(
    `SELECT * FROM media_person_link WHERE mediaId = $1 AND personXref = $2`,
    [mediaId, personXref]
  );
  if (before.length === 0 && after.length > 0) {
    await pushUndoAction(
      `Linked media ${mediaId} to ${personXref}`,
      async () => {
        const db = await getDb();
        await db.execute(`DELETE FROM media_person_link WHERE mediaId = $1 AND personXref = $2`, [mediaId, personXref]);
      },
      async () => {
        const db = await getDb();
        await db.execute(
          `INSERT OR IGNORE INTO media_person_link (mediaId, personXref, isPrimary, role, addedBy, createdAt)
           VALUES ($1, $2, $3, $4, $5, $6)`,
          [mediaId, personXref, isPrimary ? 1 : 0, role, after[0].addedBy || 'gedcom_import', after[0].createdAt || new Date().toISOString()]
        );
      },
      { tableName: 'media_person_link', rowId: `${mediaId}:${personXref}`, newData: after[0] }
    );
  }
}

export async function unlinkMediaFromPerson(mediaId: number, personXref: string): Promise<void> {
  const d = await getDb();
  const before = await d.select<any[]>(
    `SELECT * FROM media_person_link WHERE mediaId = $1 AND personXref = $2`,
    [mediaId, personXref]
  );
  if (before.length === 0) return;
  await d.execute(`DELETE FROM media_person_link WHERE mediaId = $1 AND personXref = $2`, [mediaId, personXref]);
  const row = before[0];
  await pushUndoAction(
    `Unlinked media ${mediaId} from ${personXref}`,
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT OR IGNORE INTO media_person_link (mediaId, personXref, role, isPrimary, sortOrder, faceX, faceY, faceW, faceH, caption, addedBy, verified, createdAt)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)`,
        [row.mediaId, row.personXref, row.role, row.isPrimary, row.sortOrder, row.faceX, row.faceY, row.faceW, row.faceH, row.caption, row.addedBy, row.verified, row.createdAt]
      );
    },
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM media_person_link WHERE mediaId = $1 AND personXref = $2`, [mediaId, personXref]);
    },
    { tableName: 'media_person_link', rowId: `${mediaId}:${personXref}`, oldData: row }
  );
}

export async function setMediaPrimary(mediaId: number, personXref: string): Promise<void> {
  const d = await getDb();
  const before = await d.select<{ mediaId: number; isPrimary: number }[]>(
    `SELECT mediaId, isPrimary FROM media_person_link WHERE personXref = $1`,
    [personXref]
  );
  const oldPrimaryId = before.find((row) => row.isPrimary === 1)?.mediaId ?? null;
  // Clear old primary for this person
  await d.execute(`UPDATE media_person_link SET isPrimary = 0 WHERE personXref = $1`, [personXref]);
  // Set new primary
  await d.execute(`UPDATE media_person_link SET isPrimary = 1 WHERE mediaId = $1 AND personXref = $2`, [mediaId, personXref]);
  if (oldPrimaryId === mediaId) return;
  await pushUndoAction(
    `Changed primary media for ${personXref}`,
    async () => {
      const db = await getDb();
      await db.execute(`UPDATE media_person_link SET isPrimary = 0 WHERE personXref = $1`, [personXref]);
      if (oldPrimaryId !== null) {
        await db.execute(`UPDATE media_person_link SET isPrimary = 1 WHERE mediaId = $1 AND personXref = $2`, [oldPrimaryId, personXref]);
      }
    },
    async () => {
      const db = await getDb();
      await db.execute(`UPDATE media_person_link SET isPrimary = 0 WHERE personXref = $1`, [personXref]);
      await db.execute(`UPDATE media_person_link SET isPrimary = 1 WHERE mediaId = $1 AND personXref = $2`, [mediaId, personXref]);
    },
    {
      tableName: 'media_person_link',
      rowId: personXref,
      oldData: { primaryMediaId: oldPrimaryId },
      newData: { primaryMediaId: mediaId },
    }
  );
}

export type MediaCategory = 'headshots' | 'other' | 'delete-queue' | 'uncategorized';

export function normalizeMediaPath(filePath: string): string {
  return filePath.trim().replace(/\\/g, '/').toLowerCase();
}

export async function getMediaForManagement(): Promise<(GedcomMedia & {
  category: MediaCategory;
  linkCount: number;
  peopleLabel: string;
})[]> {
  const d = await getDb();
  return await d.select<(GedcomMedia & { category: MediaCategory; linkCount: number; peopleLabel: string })[]>(`
    SELECT
      m.*,
      COALESCE(m.category, 'uncategorized') as category,
      COUNT(DISTINCT mpl.personXref) as linkCount,
      COALESCE(GROUP_CONCAT(DISTINCT TRIM(COALESCE(p.givenName, '') || ' ' || COALESCE(p.surname, ''))), '') as peopleLabel
    FROM media m
    LEFT JOIN media_person_link mpl ON mpl.mediaId = m.id
    LEFT JOIN person p ON p.xref = mpl.personXref
    WHERE m.filePath != ''
    GROUP BY m.id
    ORDER BY m.id DESC
  `);
}

export async function updateMediaCategory(mediaIds: number[], category: MediaCategory): Promise<number> {
  if (mediaIds.length === 0) return 0;
  const d = await getDb();
  const selectPlaceholders = mediaIds.map((_, i) => `$${i + 1}`).join(', ');
  const before = await d.select<{ id: number; category: string }[]>(
    `SELECT id, COALESCE(category, 'uncategorized') as category FROM media WHERE id IN (${selectPlaceholders})`,
    mediaIds
  );
  const oldById = new Map(before.map((row) => [row.id, row.category]));
  const updatePlaceholders = mediaIds.map((_, i) => `$${i + 2}`).join(', ');
  const result = await d.execute(
    `UPDATE media SET category = $1 WHERE id IN (${updatePlaceholders})`,
    [category, ...mediaIds]
  );
  if (result.rowsAffected > 0) {
    await pushUndoAction(
      `Updated media category to ${category}`,
      async () => {
        const db = await getDb();
        for (const id of mediaIds) {
          await db.execute(`UPDATE media SET category = $1 WHERE id = $2`, [oldById.get(id) || 'uncategorized', id]);
        }
      },
      async () => {
        const db = await getDb();
        const redoPlaceholders = mediaIds.map((_, i) => `$${i + 2}`).join(', ');
        await db.execute(`UPDATE media SET category = $1 WHERE id IN (${redoPlaceholders})`, [category, ...mediaIds]);
      },
      { tableName: 'media', rowId: mediaIds.join(','), oldData: Object.fromEntries(oldById), newData: { category } }
    );
  }
  return result.rowsAffected;
}

export async function processDeleteQueue(): Promise<{ mediaRemoved: number; linksRemoved: number }> {
  const d = await getDb();
  await d.execute('BEGIN');
  try {
    const queued = await d.select<{ id: number }[]>(`SELECT id FROM media WHERE category = 'delete-queue'`);
    if (queued.length === 0) {
      await d.execute('COMMIT');
      return { mediaRemoved: 0, linksRemoved: 0 };
    }
    const ids = queued.map(r => r.id);
    const placeholders = ids.map((_, i) => `$${i + 1}`).join(', ');
    const linkResult = await d.execute(`DELETE FROM media_person_link WHERE mediaId IN (${placeholders})`, ids);
    const mediaResult = await d.execute(`DELETE FROM media WHERE id IN (${placeholders})`, ids);
    await d.execute('COMMIT');
    return { mediaRemoved: mediaResult.rowsAffected, linksRemoved: linkResult.rowsAffected };
  } catch (e) {
    await d.execute('ROLLBACK');
    throw e;
  }
}

export async function deduplicateMediaByNormalizedPath(): Promise<{
  duplicateGroups: number;
  entriesRemoved: number;
  linksTransferred: number;
}> {
  const d = await getDb();
  const rows = await d.select<{ id: number; filePath: string }[]>(`SELECT id, filePath FROM media WHERE filePath != '' ORDER BY id ASC`);
  const groups = new Map<string, { id: number; filePath: string }[]>();
  for (const row of rows) {
    const key = normalizeMediaPath(row.filePath);
    if (!key) continue;
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key)!.push(row);
  }

  let duplicateGroups = 0;
  let entriesRemoved = 0;
  let linksTransferred = 0;

  await d.execute('BEGIN');
  try {
    for (const [, groupRows] of groups) {
      if (groupRows.length < 2) continue;
      duplicateGroups++;

      const keep = groupRows.reduce((lowest, row) => row.id < lowest.id ? row : lowest, groupRows[0]);
      const removals = groupRows.filter(row => row.id !== keep.id);

      for (const rm of removals) {
        const links = await d.select<{
          personXref: string;
          role: string;
          isPrimary: number;
          sortOrder: number;
          faceX: number | null;
          faceY: number | null;
          faceW: number | null;
          faceH: number | null;
          caption: string;
          verified: number;
        }[]>(
          `SELECT personXref, role, isPrimary, sortOrder, faceX, faceY, faceW, faceH, caption, verified
           FROM media_person_link WHERE mediaId = $1`,
          [rm.id]
        );
        for (const link of links) {
          const ins = await d.execute(
            `INSERT OR IGNORE INTO media_person_link
             (mediaId, personXref, role, isPrimary, sortOrder, faceX, faceY, faceW, faceH, caption, addedBy, verified, createdAt)
             VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,'dedup_merge',$11,datetime('now'))`,
            [keep.id, link.personXref, link.role || 'tagged', link.isPrimary ?? 0, link.sortOrder ?? 0, link.faceX, link.faceY, link.faceW, link.faceH, link.caption || '', link.verified ?? 0]
          );
          linksTransferred += ins.rowsAffected;
        }
        await d.execute(`DELETE FROM media_person_link WHERE mediaId = $1`, [rm.id]);
        await d.execute(`DELETE FROM media WHERE id = $1`, [rm.id]);
        entriesRemoved++;
      }
    }
    await d.execute('COMMIT');
  } catch (e) {
    await d.execute('ROLLBACK');
    throw e;
  }

  return { duplicateGroups, entriesRemoved, linksTransferred };
}

export async function autoCategorizeMediaAfterDedup(): Promise<{ updated: number }> {
  const d = await getDb();
  const rows = await d.select<{
    id: number;
    filePath: string;
    linkCount: number;
    category: string;
  }[]>(
    `SELECT m.id, m.filePath, COALESCE(m.category, 'uncategorized') as category, COUNT(DISTINCT mpl.personXref) as linkCount
     FROM media m
     LEFT JOIN media_person_link mpl ON mpl.mediaId = m.id
     GROUP BY m.id`
  );
  let updated = 0;
  for (const row of rows) {
    const normalized = normalizeMediaPath(row.filePath || '');
    let next: MediaCategory = 'uncategorized';
    if (normalized.includes('headshot') || normalized.includes('portrait')) next = 'headshots';
    else if (row.linkCount === 0) next = 'other';
    if (row.category !== next) {
      const result = await d.execute(`UPDATE media SET category = $1 WHERE id = $2`, [next, row.id]);
      updated += result.rowsAffected;
    }
  }
  return { updated };
}

export async function moveNonPortraitHeadshotsToOther(): Promise<number> {
  const d = await getDb();
  const result = await d.execute(
    `UPDATE media
     SET category = 'other'
     WHERE category = 'headshots'
       AND id NOT IN (SELECT DISTINCT mediaId FROM media_person_link)`
  );
  return result.rowsAffected;
}

export async function updateMediaFilePath(mediaId: number, filePath: string): Promise<void> {
  const d = await getDb();
  const rows = await d.select<{ filePath: string }[]>(`SELECT filePath FROM media WHERE id = $1`, [mediaId]);
  if (rows.length === 0) return;
  const oldPath = rows[0].filePath || '';
  if (oldPath === filePath) return;
  await d.execute(`UPDATE media SET filePath = $1 WHERE id = $2`, [filePath, mediaId]);
  await pushUndoAction(
    `Updated media path for ${mediaId}`,
    async () => {
      const db = await getDb();
      await db.execute(`UPDATE media SET filePath = $1 WHERE id = $2`, [oldPath, mediaId]);
    },
    async () => {
      const db = await getDb();
      await db.execute(`UPDATE media SET filePath = $1 WHERE id = $2`, [filePath, mediaId]);
    },
    {
      tableName: 'media',
      rowId: String(mediaId),
      oldData: { filePath: oldPath },
      newData: { filePath },
    }
  );
}

// ===== Place queries =====

export async function getPlaces(): Promise<Place[]> {
  const d = await getDb();
  return await d.select<Place[]>(`SELECT * FROM place ORDER BY eventCount DESC`);
}

export async function getPlaceCount(): Promise<number> {
  const d = await getDb();
  const r = await d.select<[{ c: number }]>(`SELECT COUNT(*) as c FROM place`);
  return r[0]?.c ?? 0;
}

// ===== Alternate name queries =====

export async function getAlternateNames(personXref: string): Promise<AlternateName[]> {
  const d = await getDb();
  return await d.select<AlternateName[]>(
    `SELECT * FROM alternate_name WHERE personXref = $1 ORDER BY nameType, surname, givenName`,
    [personXref]
  );
}

export async function insertAlternateName(n: Omit<AlternateName, 'id'>): Promise<void> {
  const d = await getDb();
  const result = await d.execute(
    `INSERT INTO alternate_name (personXref, givenName, surname, suffix, nameType, source)
     VALUES ($1,$2,$3,$4,$5,$6)`,
    [n.personXref, n.givenName, n.surname, n.suffix, n.nameType, n.source]
  );
  const id = result.lastInsertId ?? 0;
  await pushUndoAction(
    `Added alternate name for ${n.personXref}`,
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM alternate_name WHERE id = $1`, [id]);
    },
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO alternate_name (id, personXref, givenName, surname, suffix, nameType, source)
         VALUES ($1,$2,$3,$4,$5,$6,$7)`,
        [id, n.personXref, n.givenName, n.surname, n.suffix, n.nameType, n.source]
      );
    },
    { tableName: 'alternate_name', rowId: String(id), newData: n }
  );
}

export async function updateAlternateName(id: number, data: Partial<AlternateName>): Promise<void> {
  const d = await getDb();
  const beforeRows = await d.select<AlternateName[]>(`SELECT * FROM alternate_name WHERE id = $1`, [id]);
  if (beforeRows.length === 0) return;
  const before = beforeRows[0];
  const sets: string[] = [];
  const vals: any[] = [];
  const oldData: Record<string, unknown> = {};
  const newData: Record<string, unknown> = {};
  let i = 1;
  for (const [k, v] of Object.entries(data)) {
    if (k === 'id' || k === 'personXref') continue;
    if (!['givenName','surname','suffix','nameType','source'].includes(k)) continue;
    sets.push(`${k} = $${i}`);
    vals.push(v);
    oldData[k] = (before as any)[k];
    newData[k] = v;
    i++;
  }
  if (sets.length === 0) return;
  vals.push(id);
  await d.execute(`UPDATE alternate_name SET ${sets.join(', ')} WHERE id = $${i}`, vals);
  await pushUndoAction(
    `Updated alternate name ${id}`,
    async () => {
      const db = await getDb();
      const undoSets = Object.keys(oldData).map((k, idx) => `${k} = $${idx + 1}`);
      await db.execute(
        `UPDATE alternate_name SET ${undoSets.join(', ')} WHERE id = $${undoSets.length + 1}`,
        [...Object.values(oldData), id]
      );
    },
    async () => {
      const db = await getDb();
      const redoSets = Object.keys(newData).map((k, idx) => `${k} = $${idx + 1}`);
      await db.execute(
        `UPDATE alternate_name SET ${redoSets.join(', ')} WHERE id = $${redoSets.length + 1}`,
        [...Object.values(newData), id]
      );
    },
    { tableName: 'alternate_name', rowId: String(id), oldData, newData }
  );
}

export async function deleteAlternateName(id: number): Promise<void> {
  const d = await getDb();
  const rows = await d.select<AlternateName[]>(`SELECT * FROM alternate_name WHERE id = $1`, [id]);
  if (rows.length === 0) return;
  const old = rows[0];
  await d.execute(`DELETE FROM alternate_name WHERE id = $1`, [id]);
  await pushUndoAction(
    `Deleted alternate name ${id}`,
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO alternate_name (id, personXref, givenName, surname, suffix, nameType, source)
         VALUES ($1,$2,$3,$4,$5,$6,$7)`,
        [old.id, old.personXref, old.givenName, old.surname, old.suffix, old.nameType, old.source]
      );
    },
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM alternate_name WHERE id = $1`, [id]);
    },
    { tableName: 'alternate_name', rowId: String(id), oldData: old }
  );
}

// ===== Group queries =====

export async function getGroups(): Promise<PersonGroup[]> {
  const d = await getDb();
  return await d.select<PersonGroup[]>(`SELECT * FROM custom_group ORDER BY name`);
}

export async function insertGroup(g: Omit<PersonGroup, 'id'>): Promise<void> {
  const d = await getDb();
  const result = await d.execute(
    `INSERT INTO custom_group (name, color, description, createdAt) VALUES ($1,$2,$3,$4)`,
    [g.name, g.color, g.description, g.createdAt]
  );
  const id = result.lastInsertId ?? 0;
  await pushUndoAction(
    `Added group ${g.name}`,
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM custom_group WHERE id = $1`, [id]);
      await db.execute(`DELETE FROM group_member WHERE groupId = $1`, [id]);
    },
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO custom_group (id, name, color, description, createdAt) VALUES ($1,$2,$3,$4,$5)`,
        [id, g.name, g.color, g.description, g.createdAt]
      );
    },
    { tableName: 'custom_group', rowId: String(id), newData: g }
  );
}

export async function deleteGroup(id: number): Promise<void> {
  const d = await getDb();
  const groups = await d.select<PersonGroup[]>(`SELECT * FROM custom_group WHERE id = $1`, [id]);
  if (groups.length === 0) return;
  const group = groups[0];
  const members = await d.select<{ groupId: number; personXref: string; addedAt: string }[]>(
    `SELECT groupId, personXref, addedAt FROM group_member WHERE groupId = $1`,
    [id]
  );
  await d.execute(`DELETE FROM group_member WHERE groupId = $1`, [id]);
  await d.execute(`DELETE FROM custom_group WHERE id = $1`, [id]);
  await pushUndoAction(
    `Deleted group ${group.name}`,
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO custom_group (id, name, color, description, createdAt) VALUES ($1,$2,$3,$4,$5)`,
        [group.id, group.name, group.color, group.description, group.createdAt]
      );
      for (const m of members) {
        await db.execute(
          `INSERT OR IGNORE INTO group_member (groupId, personXref, addedAt) VALUES ($1,$2,$3)`,
          [m.groupId, m.personXref, m.addedAt]
        );
      }
    },
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM group_member WHERE groupId = $1`, [id]);
      await db.execute(`DELETE FROM custom_group WHERE id = $1`, [id]);
    },
    { tableName: 'custom_group', rowId: String(id), oldData: { group, members } }
  );
}

export async function getGroupMembers(groupId: number): Promise<string[]> {
  const d = await getDb();
  const rows = await d.select<{ personXref: string }[]>(`SELECT personXref FROM group_member WHERE groupId = $1`, [groupId]);
  return rows.map(r => r.personXref);
}

export async function addGroupMember(groupId: number, personXref: string): Promise<void> {
  const d = await getDb();
  const before = await d.select<{ groupId: number; personXref: string; addedAt: string }[]>(
    `SELECT groupId, personXref, addedAt FROM group_member WHERE groupId = $1 AND personXref = $2`,
    [groupId, personXref]
  );
  const addedAt = new Date().toISOString();
  await d.execute(`INSERT OR IGNORE INTO group_member (groupId, personXref, addedAt) VALUES ($1,$2,$3)`, [groupId, personXref, addedAt]);
  if (before.length === 0) {
    await pushUndoAction(
      `Added ${personXref} to group ${groupId}`,
      async () => {
        const db = await getDb();
        await db.execute(`DELETE FROM group_member WHERE groupId = $1 AND personXref = $2`, [groupId, personXref]);
      },
      async () => {
        const db = await getDb();
        await db.execute(`INSERT OR IGNORE INTO group_member (groupId, personXref, addedAt) VALUES ($1,$2,$3)`, [groupId, personXref, addedAt]);
      },
      { tableName: 'group_member', rowId: `${groupId}:${personXref}`, newData: { groupId, personXref, addedAt } }
    );
  }
}

export async function removeGroupMember(groupId: number, personXref: string): Promise<void> {
  const d = await getDb();
  const before = await d.select<{ groupId: number; personXref: string; addedAt: string }[]>(
    `SELECT groupId, personXref, addedAt FROM group_member WHERE groupId = $1 AND personXref = $2`,
    [groupId, personXref]
  );
  if (before.length === 0) return;
  const old = before[0];
  await d.execute(`DELETE FROM group_member WHERE groupId = $1 AND personXref = $2`, [groupId, personXref]);
  await pushUndoAction(
    `Removed ${personXref} from group ${groupId}`,
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT OR IGNORE INTO group_member (groupId, personXref, addedAt) VALUES ($1,$2,$3)`,
        [old.groupId, old.personXref, old.addedAt]
      );
    },
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM group_member WHERE groupId = $1 AND personXref = $2`, [groupId, personXref]);
    },
    { tableName: 'group_member', rowId: `${groupId}:${personXref}`, oldData: old }
  );
}

export async function getGroupMemberCount(groupId: number): Promise<number> {
  const d = await getDb();
  const r = await d.select<[{ c: number }]>(`SELECT COUNT(*) as c FROM group_member WHERE groupId = $1`, [groupId]);
  return r[0]?.c ?? 0;
}

// ===== Research Log queries =====

export async function getResearchLogs(personXref?: string): Promise<ResearchLogEntry[]> {
  const d = await getDb();
  if (personXref) {
    return await d.select<ResearchLogEntry[]>(`SELECT * FROM research_log WHERE personXref = $1 ORDER BY createdAt DESC`, [personXref]);
  }
  return await d.select<ResearchLogEntry[]>(`SELECT * FROM research_log ORDER BY createdAt DESC`);
}

export async function insertResearchLog(entry: Omit<ResearchLogEntry, 'id'>): Promise<void> {
  const d = await getDb();
  const result = await d.execute(
    `INSERT INTO research_log (personXref, repository, searchTerms, recordsViewed, conclusion, sourceXref, resultType, searchDate, createdAt) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
    [entry.personXref, entry.repository, entry.searchTerms, entry.recordsViewed, entry.conclusion, entry.sourceXref, entry.resultType, entry.searchDate, entry.createdAt]
  );
  const id = result.lastInsertId ?? 0;
  await pushUndoAction(
    `Added research log entry`,
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM research_log WHERE id = $1`, [id]);
    },
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO research_log (id, personXref, repository, searchTerms, recordsViewed, conclusion, sourceXref, resultType, searchDate, createdAt)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
        [id, entry.personXref, entry.repository, entry.searchTerms, entry.recordsViewed, entry.conclusion, entry.sourceXref, entry.resultType, entry.searchDate, entry.createdAt]
      );
    },
    { tableName: 'research_log', rowId: String(id), newData: entry }
  );
}

export async function deleteResearchLog(id: number): Promise<void> {
  const d = await getDb();
  const rows = await d.select<ResearchLogEntry[]>(`SELECT * FROM research_log WHERE id = $1`, [id]);
  if (rows.length === 0) return;
  const old = rows[0];
  await d.execute(`DELETE FROM research_log WHERE id = $1`, [id]);
  await pushUndoAction(
    `Deleted research log entry`,
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO research_log (id, personXref, repository, searchTerms, recordsViewed, conclusion, sourceXref, resultType, searchDate, createdAt)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
        [old.id, old.personXref, old.repository, old.searchTerms, old.recordsViewed, old.conclusion, old.sourceXref, old.resultType, old.searchDate, old.createdAt]
      );
    },
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM research_log WHERE id = $1`, [id]);
    },
    { tableName: 'research_log', rowId: String(id), oldData: old }
  );
}

// ===== Notes queries =====

export async function getNotes(ownerXref?: string): Promise<ResearchNote[]> {
  const d = await getDb();
  if (ownerXref) {
    return await d.select<ResearchNote[]>(`SELECT * FROM note WHERE ownerXref = $1 ORDER BY updatedAt DESC`, [ownerXref]);
  }
  return await d.select<ResearchNote[]>(`SELECT * FROM note ORDER BY updatedAt DESC`);
}

export async function searchNotes(query: string): Promise<ResearchNote[]> {
  const d = await getDb();
  const q = `%${query.trim()}%`;
  if (!query.trim()) return getNotes();
  return await d.select<ResearchNote[]>(
    `SELECT * FROM note
     WHERE title LIKE $1 OR content LIKE $1
     ORDER BY updatedAt DESC`,
    [q]
  );
}

export async function insertNote(n: Omit<ResearchNote, 'id'>): Promise<void> {
  const d = await getDb();
  const result = await d.execute(
    `INSERT INTO note (ownerXref, ownerType, title, content, createdAt, updatedAt) VALUES ($1,$2,$3,$4,$5,$6)`,
    [n.ownerXref, n.ownerType, n.title, n.content, n.createdAt, n.updatedAt]
  );
  const id = result.lastInsertId ?? 0;
  await pushUndoAction(
    `Added note`,
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM note WHERE id = $1`, [id]);
    },
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO note (id, ownerXref, ownerType, title, content, createdAt, updatedAt) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
        [id, n.ownerXref, n.ownerType, n.title, n.content, n.createdAt, n.updatedAt]
      );
    },
    { tableName: 'note', rowId: String(id), newData: n }
  );
}

export async function deleteNote(id: number): Promise<void> {
  const d = await getDb();
  const rows = await d.select<ResearchNote[]>(`SELECT * FROM note WHERE id = $1`, [id]);
  if (rows.length === 0) return;
  const old = rows[0];
  await d.execute(`DELETE FROM note WHERE id = $1`, [id]);
  await pushUndoAction(
    `Deleted note`,
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO note (id, ownerXref, ownerType, title, content, createdAt, updatedAt) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
        [old.id, old.ownerXref, old.ownerType, old.title, old.content, old.createdAt, old.updatedAt]
      );
    },
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM note WHERE id = $1`, [id]);
    },
    { tableName: 'note', rowId: String(id), oldData: old }
  );
}

// ===== Task queries =====

export async function getTasks(personXref?: string): Promise<ResearchTask[]> {
  const d = await getDb();
  if (personXref) {
    return await d.select<ResearchTask[]>(`SELECT * FROM research_task WHERE personXref = $1 ORDER BY CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 WHEN 'LOW' THEN 2 END, createdAt DESC`, [personXref]);
  }
  return await d.select<ResearchTask[]>(`SELECT * FROM research_task ORDER BY CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 WHEN 'LOW' THEN 2 END, createdAt DESC`);
}

export async function insertTask(t: Omit<ResearchTask, 'id'>): Promise<void> {
  const d = await getDb();
  const result = await d.execute(
    `INSERT INTO research_task (personXref, title, description, status, priority, dueDate, createdAt, completedAt) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
    [t.personXref, t.title, t.description, t.status, t.priority, t.dueDate, t.createdAt, t.completedAt]
  );
  const id = result.lastInsertId ?? 0;
  await pushUndoAction(
    `Added task`,
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM research_task WHERE id = $1`, [id]);
    },
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO research_task (id, personXref, title, description, status, priority, dueDate, createdAt, completedAt) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
        [id, t.personXref, t.title, t.description, t.status, t.priority, t.dueDate, t.createdAt, t.completedAt]
      );
    },
    { tableName: 'research_task', rowId: String(id), newData: t }
  );
}

export async function updateTask(id: number, status: string, completedAt: string): Promise<void> {
  const d = await getDb();
  const rows = await d.select<ResearchTask[]>(`SELECT * FROM research_task WHERE id = $1`, [id]);
  if (rows.length === 0) return;
  const old = rows[0];
  await d.execute(`UPDATE research_task SET status = $1, completedAt = $2 WHERE id = $3`, [status, completedAt, id]);
  await pushUndoAction(
    `Updated task ${id}`,
    async () => {
      const db = await getDb();
      await db.execute(`UPDATE research_task SET status = $1, completedAt = $2 WHERE id = $3`, [old.status, old.completedAt, id]);
    },
    async () => {
      const db = await getDb();
      await db.execute(`UPDATE research_task SET status = $1, completedAt = $2 WHERE id = $3`, [status, completedAt, id]);
    },
    {
      tableName: 'research_task',
      rowId: String(id),
      oldData: { status: old.status, completedAt: old.completedAt },
      newData: { status, completedAt },
    }
  );
}

export async function deleteTask(id: number): Promise<void> {
  const d = await getDb();
  const rows = await d.select<ResearchTask[]>(`SELECT * FROM research_task WHERE id = $1`, [id]);
  if (rows.length === 0) return;
  const old = rows[0];
  await d.execute(`DELETE FROM research_task WHERE id = $1`, [id]);
  await pushUndoAction(
    `Deleted task`,
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO research_task (id, personXref, title, description, status, priority, dueDate, createdAt, completedAt)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
        [old.id, old.personXref, old.title, old.description, old.status, old.priority, old.dueDate, old.createdAt, old.completedAt]
      );
    },
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM research_task WHERE id = $1`, [id]);
    },
    { tableName: 'research_task', rowId: String(id), oldData: old }
  );
}

// ===== Bookmark queries =====

export async function getBookmarks(): Promise<Bookmark[]> {
  const d = await getDb();
  return await d.select<Bookmark[]>(`SELECT * FROM bookmark ORDER BY sortOrder ASC, createdAt DESC, id DESC`);
}

export async function insertBookmark(b: Omit<Bookmark, 'id'>): Promise<void> {
  const d = await getDb();
  let nextSortOrder = b.sortOrder ?? 0;
  if (b.sortOrder === undefined) {
    const row = await d.select<[{ maxSort: number | null }]>(`SELECT MAX(sortOrder) as maxSort FROM bookmark`);
    nextSortOrder = (row[0]?.maxSort ?? -1) + 1;
  }
  const result = await d.execute(
    `INSERT INTO bookmark (personXref, label, category, sortOrder, createdAt) VALUES ($1,$2,$3,$4,$5)`,
    [b.personXref, b.label, b.category || 'General', nextSortOrder, b.createdAt]
  );
  const id = result.lastInsertId ?? 0;
  await pushUndoAction(
    `Added bookmark for ${b.personXref}`,
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM bookmark WHERE id = $1`, [id]);
    },
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO bookmark (id, personXref, label, category, sortOrder, createdAt) VALUES ($1,$2,$3,$4,$5,$6)`,
        [id, b.personXref, b.label, b.category || 'General', nextSortOrder, b.createdAt]
      );
    },
    {
      tableName: 'bookmark',
      rowId: String(id),
      newData: { personXref: b.personXref, label: b.label, category: b.category || 'General', sortOrder: nextSortOrder, createdAt: b.createdAt },
    }
  );
}

export async function deleteBookmark(id: number): Promise<void> {
  const d = await getDb();
  const rows = await d.select<Bookmark[]>(`SELECT * FROM bookmark WHERE id = $1`, [id]);
  if (rows.length === 0) return;
  const old = rows[0];
  await d.execute(`DELETE FROM bookmark WHERE id = $1`, [id]);
  await pushUndoAction(
    `Deleted bookmark`,
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO bookmark (id, personXref, label, category, sortOrder, createdAt) VALUES ($1,$2,$3,$4,$5,$6)`,
        [old.id, old.personXref, old.label, old.category || 'General', old.sortOrder ?? 0, old.createdAt]
      );
    },
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM bookmark WHERE id = $1`, [id]);
    },
    { tableName: 'bookmark', rowId: String(id), oldData: old }
  );
}

export async function isBookmarked(personXref: string): Promise<boolean> {
  const d = await getDb();
  const r = await d.select<[{ c: number }]>(`SELECT COUNT(*) as c FROM bookmark WHERE personXref = $1`, [personXref]);
  return (r[0]?.c ?? 0) > 0;
}

export async function updateBookmarkCategory(id: number, category: string): Promise<void> {
  const d = await getDb();
  const rows = await d.select<Bookmark[]>(`SELECT * FROM bookmark WHERE id = $1`, [id]);
  if (rows.length === 0) return;
  const old = rows[0];
  const next = category.trim() || 'General';
  await d.execute(`UPDATE bookmark SET category = $1 WHERE id = $2`, [next, id]);
  await pushUndoAction(
    `Updated bookmark category`,
    async () => {
      const db = await getDb();
      await db.execute(`UPDATE bookmark SET category = $1 WHERE id = $2`, [old.category || 'General', id]);
    },
    async () => {
      const db = await getDb();
      await db.execute(`UPDATE bookmark SET category = $1 WHERE id = $2`, [next, id]);
    },
    { tableName: 'bookmark', rowId: String(id), oldData: { category: old.category }, newData: { category: next } }
  );
}

export async function reorderBookmarks(orderedIds: number[]): Promise<void> {
  if (orderedIds.length === 0) return;
  const d = await getDb();
  const oldRows = await d.select<{ id: number; sortOrder: number }[]>(
    `SELECT id, sortOrder FROM bookmark WHERE id IN (${orderedIds.map((_, i) => `$${i + 1}`).join(', ')})`,
    orderedIds
  );
  const oldById = new Map(oldRows.map((row) => [row.id, row.sortOrder]));
  await d.execute('BEGIN');
  try {
    for (let i = 0; i < orderedIds.length; i++) {
      await d.execute(`UPDATE bookmark SET sortOrder = $1 WHERE id = $2`, [i, orderedIds[i]]);
    }
    await d.execute('COMMIT');
  } catch (e) {
    await d.execute('ROLLBACK');
    throw e;
  }
  await pushUndoAction(
    `Reordered bookmarks`,
    async () => {
      const db = await getDb();
      for (const id of orderedIds) {
        if (!oldById.has(id)) continue;
        await db.execute(`UPDATE bookmark SET sortOrder = $1 WHERE id = $2`, [oldById.get(id), id]);
      }
    },
    async () => {
      const db = await getDb();
      for (let i = 0; i < orderedIds.length; i++) {
        await db.execute(`UPDATE bookmark SET sortOrder = $1 WHERE id = $2`, [i, orderedIds[i]]);
      }
    },
    {
      tableName: 'bookmark',
      rowId: orderedIds.join(','),
      oldData: Object.fromEntries(oldById),
      newData: { orderedIds },
    }
  );
}

export async function batchAddToGroup(xrefs: string[], groupId: number): Promise<void> {
  if (xrefs.length === 0) return;
  const d = await getDb();
  const before = await d.select<{ personXref: string }[]>(
    `SELECT personXref FROM group_member WHERE groupId = $1`,
    [groupId]
  );
  const beforeSet = new Set(before.map((r) => r.personXref));
  await d.execute('BEGIN');
  try {
    for (const xref of xrefs) {
      await d.execute(
        `INSERT OR IGNORE INTO group_member (groupId, personXref, addedAt) VALUES ($1,$2,$3)`,
        [groupId, xref, new Date().toISOString()]
      );
    }
    await d.execute('COMMIT');
    const added = xrefs.filter((xref) => !beforeSet.has(xref));
    if (added.length > 0) {
      await pushUndoAction(
        `Added ${added.length} people to group ${groupId}`,
        async () => {
          const db = await getDb();
          const placeholders = added.map((_, i) => `$${i + 2}`).join(', ');
          await db.execute(`DELETE FROM group_member WHERE groupId = $1 AND personXref IN (${placeholders})`, [groupId, ...added]);
        },
        async () => {
          const db = await getDb();
          for (const xref of added) {
            await db.execute(
              `INSERT OR IGNORE INTO group_member (groupId, personXref, addedAt) VALUES ($1,$2,$3)`,
              [groupId, xref, new Date().toISOString()]
            );
          }
        },
        { tableName: 'group_member', rowId: String(groupId), newData: { added } }
      );
    }
  } catch (e) {
    await d.execute('ROLLBACK');
    throw e;
  }
}

export async function batchBookmark(xrefs: string[], label = ''): Promise<void> {
  if (xrefs.length === 0) return;
  const d = await getDb();
  const before = await d.select<{ personXref: string }[]>(`SELECT DISTINCT personXref FROM bookmark`);
  const beforeSet = new Set(before.map((r) => r.personXref));
  await d.execute('BEGIN');
  try {
    for (const xref of xrefs) {
      await d.execute(
        `INSERT OR IGNORE INTO bookmark (personXref, label, category, sortOrder, createdAt)
         VALUES ($1,$2,'General',0,$3)`,
        [xref, label, new Date().toISOString()]
      );
    }
    await d.execute('COMMIT');
    const added = xrefs.filter((xref) => !beforeSet.has(xref));
    if (added.length > 0) {
      await pushUndoAction(
        `Bookmarked ${added.length} people`,
        async () => {
          const db = await getDb();
          const placeholders = added.map((_, i) => `$${i + 1}`).join(', ');
          await db.execute(`DELETE FROM bookmark WHERE personXref IN (${placeholders})`, added);
        },
        async () => {
          const db = await getDb();
          for (const xref of added) {
            await db.execute(
              `INSERT OR IGNORE INTO bookmark (personXref, label, category, sortOrder, createdAt)
               VALUES ($1,$2,'General',0,$3)`,
              [xref, label, new Date().toISOString()]
            );
          }
        },
        { tableName: 'bookmark', rowId: added.join(','), newData: { added, label } }
      );
    }
  } catch (e) {
    await d.execute('ROLLBACK');
    throw e;
  }
}

export async function batchRemoveBookmarks(xrefs: string[]): Promise<void> {
  if (xrefs.length === 0) return;
  const d = await getDb();
  const lookupPlaceholders = xrefs.map((_, i) => `$${i + 1}`).join(', ');
  const removed = await d.select<Bookmark[]>(`SELECT * FROM bookmark WHERE personXref IN (${lookupPlaceholders})`, xrefs);
  await d.execute(`DELETE FROM bookmark WHERE personXref IN (${lookupPlaceholders})`, xrefs);
  if (removed.length > 0) {
    await pushUndoAction(
      `Removed ${removed.length} bookmarks`,
      async () => {
        const db = await getDb();
        for (const row of removed) {
          await db.execute(
            `INSERT OR IGNORE INTO bookmark (id, personXref, label, category, sortOrder, createdAt)
             VALUES ($1,$2,$3,$4,$5,$6)`,
            [row.id, row.personXref, row.label, row.category || 'General', row.sortOrder ?? 0, row.createdAt]
          );
        }
      },
      async () => {
        const db = await getDb();
        const redoPlaceholders = xrefs.map((_, i) => `$${i + 1}`).join(', ');
        await db.execute(`DELETE FROM bookmark WHERE personXref IN (${redoPlaceholders})`, xrefs);
      },
      { tableName: 'bookmark', rowId: xrefs.join(','), oldData: removed }
    );
  }
}

export async function batchDeletePersons(xrefs: string[]): Promise<void> {
  if (xrefs.length === 0) return;
  const d = await getDb();
  const placeholders = xrefs.map((_, i) => `$${i + 1}`).join(', ');
  const people = await d.select<Person[]>(`SELECT * FROM person WHERE xref IN (${placeholders})`, xrefs);
  const events = await d.select<GedcomEvent[]>(`SELECT * FROM event WHERE ownerXref IN (${placeholders})`, xrefs);
  const childLinks = await d.select<ChildLink[]>(`SELECT * FROM child_link WHERE childXref IN (${placeholders})`, xrefs);
  const mediaLinks = await d.select<any[]>(`SELECT * FROM media_person_link WHERE personXref IN (${placeholders})`, xrefs);
  const bookmarks = await d.select<Bookmark[]>(`SELECT * FROM bookmark WHERE personXref IN (${placeholders})`, xrefs);
  await d.execute('BEGIN');
  try {
    await d.execute(`DELETE FROM media_person_link WHERE personXref IN (${placeholders})`, xrefs);
    await d.execute(`DELETE FROM child_link WHERE childXref IN (${placeholders})`, xrefs);
    await d.execute(`DELETE FROM event WHERE ownerXref IN (${placeholders})`, xrefs);
    await d.execute(`DELETE FROM bookmark WHERE personXref IN (${placeholders})`, xrefs);
    await d.execute(`DELETE FROM person WHERE xref IN (${placeholders})`, xrefs);
    await d.execute('COMMIT');
    if (people.length > 0) {
      await pushUndoAction(
        `Deleted ${people.length} people`,
        async () => {
          const db = await getDb();
          for (const p of people) {
            await db.execute(
              `INSERT OR IGNORE INTO person (id, xref, givenName, surname, suffix, sex, isLiving, birthDate, birthPlace, deathDate, deathPlace, sourceCount, mediaCount, personColor, proofStatus)
               VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)`,
              [p.id, p.xref, p.givenName, p.surname, p.suffix, p.sex, p.isLiving ? 1 : 0, p.birthDate, p.birthPlace, p.deathDate, p.deathPlace, p.sourceCount, p.mediaCount, p.personColor, p.proofStatus]
            );
          }
          for (const e of events) {
            await db.execute(
              `INSERT OR IGNORE INTO event (id, ownerXref, ownerType, eventType, dateValue, place, description)
               VALUES ($1,$2,$3,$4,$5,$6,$7)`,
              [e.id, e.ownerXref, e.ownerType, e.eventType, e.dateValue, e.place, e.description]
            );
          }
          for (const cl of childLinks) {
            await db.execute(
              `INSERT OR IGNORE INTO child_link (familyXref, childXref, childOrder) VALUES ($1,$2,$3)`,
              [cl.familyXref, cl.childXref, cl.childOrder]
            );
          }
          for (const ml of mediaLinks) {
            await db.execute(
              `INSERT OR IGNORE INTO media_person_link (id, mediaId, personXref, role, isPrimary, sortOrder, faceX, faceY, faceW, faceH, caption, addedBy, verified, createdAt)
               VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)`,
              [ml.id, ml.mediaId, ml.personXref, ml.role, ml.isPrimary, ml.sortOrder, ml.faceX, ml.faceY, ml.faceW, ml.faceH, ml.caption, ml.addedBy, ml.verified, ml.createdAt]
            );
          }
          for (const b of bookmarks) {
            await db.execute(
              `INSERT OR IGNORE INTO bookmark (id, personXref, label, category, sortOrder, createdAt) VALUES ($1,$2,$3,$4,$5,$6)`,
              [b.id, b.personXref, b.label, b.category || 'General', b.sortOrder ?? 0, b.createdAt]
            );
          }
        },
        async () => {
          const db = await getDb();
          const redoPlaceholders = xrefs.map((_, i) => `$${i + 1}`).join(', ');
          await db.execute(`DELETE FROM media_person_link WHERE personXref IN (${redoPlaceholders})`, xrefs);
          await db.execute(`DELETE FROM child_link WHERE childXref IN (${redoPlaceholders})`, xrefs);
          await db.execute(`DELETE FROM event WHERE ownerXref IN (${redoPlaceholders})`, xrefs);
          await db.execute(`DELETE FROM bookmark WHERE personXref IN (${redoPlaceholders})`, xrefs);
          await db.execute(`DELETE FROM person WHERE xref IN (${redoPlaceholders})`, xrefs);
        },
        { tableName: 'person', rowId: xrefs.join(','), oldData: { people, events, childLinks, mediaLinks, bookmarks } }
      );
    }
  } catch (e) {
    await d.execute('ROLLBACK');
    throw e;
  }
}

// ===== AI Chat queries =====

export async function getAIChatHistory(personXref?: string): Promise<AIChatMessage[]> {
  const d = await getDb();
  if (personXref) {
    return await d.select<AIChatMessage[]>(`SELECT * FROM ai_chat WHERE personXref = $1 ORDER BY timestamp ASC`, [personXref]);
  }
  return await d.select<AIChatMessage[]>(`SELECT * FROM ai_chat ORDER BY timestamp ASC`);
}

export async function insertAIChatMessage(m: Omit<AIChatMessage, 'id'>): Promise<void> {
  const d = await getDb();
  const result = await d.execute(
    `INSERT INTO ai_chat (provider, model, role, content, personXref, timestamp) VALUES ($1,$2,$3,$4,$5,$6)`,
    [m.provider, m.model, m.role, m.content, m.personXref, m.timestamp]
  );
  const id = result.lastInsertId ?? 0;
  await pushUndoAction(
    `Added chat message`,
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM ai_chat WHERE id = $1`, [id]);
    },
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO ai_chat (id, provider, model, role, content, personXref, timestamp) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
        [id, m.provider, m.model, m.role, m.content, m.personXref, m.timestamp]
      );
    },
    { tableName: 'ai_chat', rowId: String(id), newData: m }
  );
}

export async function clearAIChatHistory(): Promise<void> {
  const d = await getDb();
  const rows = await d.select<AIChatMessage[]>(`SELECT * FROM ai_chat`);
  await d.execute(`DELETE FROM ai_chat`);
  if (rows.length > 0) {
    await pushUndoAction(
      `Cleared chat history`,
      async () => {
        const db = await getDb();
        for (const row of rows) {
          await db.execute(
            `INSERT OR IGNORE INTO ai_chat (id, provider, model, role, content, personXref, timestamp)
             VALUES ($1,$2,$3,$4,$5,$6,$7)`,
            [row.id, row.provider, row.model, row.role, row.content, row.personXref, row.timestamp]
          );
        }
      },
      async () => {
        const db = await getDb();
        await db.execute(`DELETE FROM ai_chat`);
      },
      { tableName: 'ai_chat', rowId: 'all', oldData: rows }
    );
  }
}

// ===== Story queries =====

export async function getStories(): Promise<GeneratedStory[]> {
  const db = await getDb();
  return db.select<GeneratedStory[]>('SELECT * FROM stories ORDER BY createdAt DESC');
}

export async function getStoriesForPerson(xref: string): Promise<GeneratedStory[]> {
  const db = await getDb();
  return db.select<GeneratedStory[]>('SELECT * FROM stories WHERE personXref = $1 ORDER BY createdAt DESC', [xref]);
}

export async function insertStory(story: Omit<GeneratedStory, 'id'>): Promise<void> {
  const db = await getDb();
  const result = await db.execute(
    `INSERT INTO stories (personXref, familyXref, storyType, title, content, provider, model, tokensUsed, costEstimate, createdAt)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
    [story.personXref, story.familyXref, story.storyType, story.title, story.content, story.provider, story.model, story.tokensUsed, story.costEstimate, story.createdAt]
  );
  const id = result.lastInsertId ?? 0;
  await pushUndoAction(
    `Added story`,
    async () => {
      const d = await getDb();
      await d.execute(`DELETE FROM stories WHERE id = $1`, [id]);
    },
    async () => {
      const d = await getDb();
      await d.execute(
        `INSERT INTO stories (id, personXref, familyXref, storyType, title, content, provider, model, tokensUsed, costEstimate, createdAt)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
        [id, story.personXref, story.familyXref, story.storyType, story.title, story.content, story.provider, story.model, story.tokensUsed, story.costEstimate, story.createdAt]
      );
    },
    { tableName: 'stories', rowId: String(id), newData: story }
  );
}

export async function deleteStory(id: number): Promise<void> {
  const db = await getDb();
  const rows = await db.select<GeneratedStory[]>(`SELECT * FROM stories WHERE id = $1`, [id]);
  if (rows.length === 0) return;
  const old = rows[0];
  await db.execute('DELETE FROM stories WHERE id = $1', [id]);
  await pushUndoAction(
    `Deleted story`,
    async () => {
      const d = await getDb();
      await d.execute(
        `INSERT INTO stories (id, personXref, familyXref, storyType, title, content, provider, model, tokensUsed, costEstimate, createdAt)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
        [old.id, old.personXref, old.familyXref, old.storyType, old.title, old.content, old.provider, old.model, old.tokensUsed, old.costEstimate, old.createdAt]
      );
    },
    async () => {
      const d = await getDb();
      await d.execute(`DELETE FROM stories WHERE id = $1`, [id]);
    },
    { tableName: 'stories', rowId: String(id), oldData: old }
  );
}

// ===== Stats =====

export async function getStats(): Promise<AppStats> {
  const d = await getDb();
  const r = await d.select<[{
    personCount: number; familyCount: number; sourceCount: number;
    mediaCount: number; placeCount: number; eventCount: number;
    groupCount: number; researchLogCount: number;
  }]>(`SELECT
    (SELECT COUNT(*) FROM person) as personCount,
    (SELECT COUNT(*) FROM family) as familyCount,
    (SELECT COUNT(*) FROM source) as sourceCount,
    (SELECT COUNT(*) FROM media) as mediaCount,
    (SELECT COUNT(*) FROM place) as placeCount,
    (SELECT COUNT(*) FROM event) as eventCount,
    (SELECT COUNT(*) FROM custom_group) as groupCount,
    (SELECT COUNT(*) FROM research_log) as researchLogCount
  `);
  const s = r[0];

  const surnames = await d.select<{ surname: string; count: number }[]>(
    `SELECT surname, COUNT(*) as count FROM person WHERE surname != '' GROUP BY surname ORDER BY count DESC LIMIT 15`
  );

  return {
    personCount: s.personCount,
    familyCount: s.familyCount,
    eventCount: s.eventCount,
    sourceCount: s.sourceCount,
    mediaCount: s.mediaCount,
    placeCount: s.placeCount,
    groupCount: s.groupCount,
    researchLogCount: s.researchLogCount,
    surnameDistribution: surnames,
  };
}

export async function isDbEmpty(): Promise<boolean> {
  const d = await getDb();
  const rows = await d.select<[{ c: number }]>(`SELECT COUNT(*) as c FROM person`);
  return (rows[0]?.c ?? 0) === 0;
}

// ===== Bulk import (called from parser) =====

export async function insertPerson(p: Omit<Person, 'id'>): Promise<void> {
  const d = await getDb();
  await d.execute(
    `INSERT OR IGNORE INTO person (xref, givenName, surname, suffix, sex, isLiving, birthDate, birthPlace, deathDate, deathPlace, sourceCount, mediaCount, personColor, proofStatus)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)`,
    [p.xref, p.givenName, p.surname, p.suffix, p.sex, p.isLiving ? 1 : 0, p.birthDate, p.birthPlace, p.deathDate, p.deathPlace, p.sourceCount, p.mediaCount, p.personColor || '', p.proofStatus || 'UNKNOWN']
  );
}

export async function insertFamily(f: Omit<Family, 'id'>): Promise<void> {
  const d = await getDb();
  await d.execute(
    `INSERT OR IGNORE INTO family (xref, partner1Xref, partner2Xref, marriageDate, marriagePlace) VALUES ($1,$2,$3,$4,$5)`,
    [f.xref, f.partner1Xref, f.partner2Xref, f.marriageDate, f.marriagePlace]
  );
}

export async function insertEvent(e: Omit<GedcomEvent, 'id'>): Promise<void> {
  const d = await getDb();
  await d.execute(
    `INSERT INTO event (ownerXref, ownerType, eventType, dateValue, place, description) VALUES ($1,$2,$3,$4,$5,$6)`,
    [e.ownerXref, e.ownerType, e.eventType, e.dateValue, e.place, e.description]
  );
}

export async function addEventAction(e: Omit<GedcomEvent, 'id'>): Promise<number> {
  const d = await getDb();
  const result = await d.execute(
    `INSERT INTO event (ownerXref, ownerType, eventType, dateValue, place, description) VALUES ($1,$2,$3,$4,$5,$6)`,
    [e.ownerXref, e.ownerType, e.eventType, e.dateValue, e.place, e.description]
  );
  const id = result.lastInsertId ?? 0;
  const { undoManager } = await import('./undo-manager');
  await undoManager.push({
    id: crypto.randomUUID(),
    description: `Added ${e.eventType} event`,
    timestamp: new Date().toISOString(),
    undo: async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM event WHERE id = $1`, [id]);
    },
    redo: async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO event (id, ownerXref, ownerType, eventType, dateValue, place, description) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
        [id, e.ownerXref, e.ownerType, e.eventType, e.dateValue, e.place, e.description]
      );
    },
  }, { tableName: 'event', rowId: String(id), newData: e });
  return id;
}

export async function deleteEventById(id: number): Promise<void> {
  const d = await getDb();
  const rows = await d.select<GedcomEvent[]>(`SELECT * FROM event WHERE id = $1`, [id]);
  if (rows.length === 0) return;
  const old = rows[0];
  await d.execute(`DELETE FROM event WHERE id = $1`, [id]);
  const { undoManager } = await import('./undo-manager');
  await undoManager.push({
    id: crypto.randomUUID(),
    description: `Deleted ${old.eventType} event`,
    timestamp: new Date().toISOString(),
    undo: async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO event (id, ownerXref, ownerType, eventType, dateValue, place, description) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
        [old.id, old.ownerXref, old.ownerType, old.eventType, old.dateValue, old.place, old.description]
      );
    },
    redo: async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM event WHERE id = $1`, [id]);
    },
  }, { tableName: 'event', rowId: String(id), oldData: old });
}

export async function insertSource(s: Omit<Source, 'id'>): Promise<void> {
  const d = await getDb();
  await d.execute(
    `INSERT OR IGNORE INTO source (xref, title, author, publisher) VALUES ($1,$2,$3,$4)`,
    [s.xref, s.title, s.author, s.publisher]
  );
}

export async function insertMedia(m: Omit<GedcomMedia, 'id'>): Promise<void> {
  const d = await getDb();
  await d.execute(
    `INSERT INTO media (xref, ownerXref, filePath, format, title, category) VALUES ($1,$2,$3,$4,$5,$6)`,
    [m.xref, m.ownerXref, m.filePath, m.format, m.title, m.category || 'uncategorized']
  );
}

export async function insertChildLink(cl: ChildLink): Promise<void> {
  const d = await getDb();
  await d.execute(
    `INSERT OR IGNORE INTO child_link (familyXref, childXref, childOrder) VALUES ($1,$2,$3)`,
    [cl.familyXref, cl.childXref, cl.childOrder]
  );
}

export async function insertPlace(p: Omit<Place, 'id'>): Promise<void> {
  const d = await getDb();
  await d.execute(
    `INSERT INTO place (name, normalized, latitude, longitude, eventCount) VALUES ($1,$2,$3,$4,$5)`,
    [p.name, p.normalized, p.latitude, p.longitude, p.eventCount]
  );
}

// ===== Issue Fix Engine =====

export async function applyIssueFix(issue: TreeIssue): Promise<{ success: boolean; error?: string }> {
  const d = await getDb();
  try {
    switch (issue.title) {
      case 'Death before birth': {
        const person = await getPerson(issue.personXref);
        if (!person) return { success: false, error: 'Person not found' };
        await d.execute(
          `UPDATE person SET birthDate = $1, deathDate = $2 WHERE xref = $3`,
          [person.deathDate, person.birthDate, issue.personXref]
        );
        // Also swap events
        await d.execute(
          `UPDATE event SET eventType = CASE eventType WHEN 'BIRT' THEN 'DEAT' WHEN 'DEAT' THEN 'BIRT' ELSE eventType END
           WHERE ownerXref = $1 AND eventType IN ('BIRT', 'DEAT')`,
          [issue.personXref]
        );
        return { success: true };
      }
      case 'Future birth date': {
        await d.execute(`UPDATE person SET birthDate = '' WHERE xref = $1`, [issue.personXref]);
        await d.execute(`DELETE FROM event WHERE ownerXref = $1 AND eventType = 'BIRT'`, [issue.personXref]);
        return { success: true };
      }
      case 'Person married to self': {
        await d.execute(`UPDATE family SET partner2Xref = '' WHERE xref = $1`, [issue.familyXref]);
        return { success: true };
      }
      case 'Family with no partners': {
        await d.execute(`DELETE FROM child_link WHERE familyXref = $1`, [issue.familyXref]);
        await d.execute(`DELETE FROM event WHERE ownerXref = $1`, [issue.familyXref]);
        await d.execute(`DELETE FROM family WHERE xref = $1`, [issue.familyXref]);
        return { success: true };
      }
      default:
        return { success: false, error: 'No auto-fix available for this issue type' };
    }
  } catch (e) {
    return { success: false, error: String(e) };
  }
}

export async function dismissIssue(issueKey: string): Promise<void> {
  const d = await getDb();
  const existed = await d.select<{ issueKey: string }[]>(`SELECT issueKey FROM dismissed_issue WHERE issueKey = $1`, [issueKey]);
  await d.execute(
    `INSERT OR REPLACE INTO dismissed_issue (issueKey, dismissedAt) VALUES ($1, $2)`,
    [issueKey, new Date().toISOString()]
  );
  if (!existed.length) {
    await pushUndoAction(
      `Dismissed issue: ${issueKey}`,
      async () => { const db = await getDb(); await db.execute(`DELETE FROM dismissed_issue WHERE issueKey = $1`, [issueKey]); },
      async () => { const db = await getDb(); await db.execute(`INSERT OR REPLACE INTO dismissed_issue (issueKey, dismissedAt) VALUES ($1, $2)`, [issueKey, new Date().toISOString()]); },
      { tableName: 'dismissed_issue', rowId: issueKey }
    );
  }
}

export async function getDismissedIssueKeys(): Promise<Set<string>> {
  const d = await getDb();
  const rows = await d.select<{ issueKey: string }[]>(`SELECT issueKey FROM dismissed_issue`);
  return new Set(rows.map(r => r.issueKey));
}

export async function clearDismissedIssues(): Promise<void> {
  const d = await getDb();
  await d.execute(`DELETE FROM dismissed_issue`);
}

// ===== Proposal System =====

export async function insertProposal(p: Omit<Proposal, 'id'>): Promise<number> {
  const d = await getDb();
  const result = await d.execute(
    `INSERT INTO proposal (agentRunId, entityType, entityId, fieldName, oldValue, newValue, confidence, reasoning, evidenceSource, status, createdAt)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
    [p.agentRunId, p.entityType, p.entityId, p.fieldName, p.oldValue, p.newValue, p.confidence, p.reasoning, p.evidenceSource, p.status || 'pending', p.createdAt || new Date().toISOString()]
  );
  return result.lastInsertId ?? 0;
}

export async function getProposals(filters?: { status?: string; entityId?: string; agentRunId?: string }): Promise<Proposal[]> {
  const d = await getDb();
  let query = 'SELECT * FROM proposal WHERE 1=1';
  const params: any[] = [];
  let i = 1;
  if (filters?.status) { query += ` AND status = $${i++}`; params.push(filters.status); }
  if (filters?.entityId) { query += ` AND entityId = $${i++}`; params.push(filters.entityId); }
  if (filters?.agentRunId) { query += ` AND agentRunId = $${i++}`; params.push(filters.agentRunId); }
  query += ' ORDER BY createdAt DESC';
  return await d.select<Proposal[]>(query, params);
}

export async function getPendingProposalCount(): Promise<number> {
  const d = await getDb();
  const r = await d.select<[{ c: number }]>(`SELECT COUNT(*) as c FROM proposal WHERE status = 'pending'`);
  return r[0]?.c ?? 0;
}

export const SAFE_FIELDS: Record<string, Set<string>> = {
  person: new Set(['givenName','surname','suffix','sex','birthDate','birthPlace','deathDate','deathPlace','personColor','proofStatus']),
  family: new Set(['partner1Xref','partner2Xref','marriageDate','marriagePlace']),
  event: new Set(['eventType','dateValue','place','description']),
  source: new Set(['title','author','publisher']),
};

export async function approveProposal(proposalId: number): Promise<{ success: boolean; error?: string }> {
  const d = await getDb();
  try {
    const rows = await d.select<Proposal[]>(`SELECT * FROM proposal WHERE id = $1`, [proposalId]);
    if (rows.length === 0) return { success: false, error: 'Proposal not found' };
    const p = rows[0];
    if (p.status !== 'pending') return { success: false, error: 'Proposal is not pending' };

    const table = p.entityType === 'person' ? 'person' : p.entityType === 'family' ? 'family' : p.entityType === 'event' ? 'event' : p.entityType === 'source' ? 'source' : null;
    if (!table) return { success: false, error: `Unknown entity type: ${p.entityType}` };
    if (!SAFE_FIELDS[table]?.has(p.fieldName)) return { success: false, error: `Invalid field: ${p.fieldName}` };

    const idCol = table === 'person' || table === 'family' || table === 'source' ? 'xref' : 'id';
    await d.execute(`UPDATE ${table} SET ${p.fieldName} = $1 WHERE ${idCol} = $2`, [p.newValue, p.entityId]);

    // Write change_log
    const changeResult = await d.execute(
      `INSERT INTO change_log (proposalId, entityType, entityId, fieldName, oldValue, newValue, actor)
       VALUES ($1,$2,$3,$4,$5,$6,$7)`,
      [proposalId, p.entityType, p.entityId, p.fieldName, p.oldValue, p.newValue, 'user']
    );
    const changeLogId = changeResult.lastInsertId ?? 0;

    // Mark proposal as approved
    await d.execute(`UPDATE proposal SET status = 'approved', resolvedAt = datetime('now') WHERE id = $1`, [proposalId]);

    await pushUndoAction(
      `Approved proposal ${proposalId}`,
      async () => {
        const db = await getDb();
        await db.execute(`UPDATE ${table} SET ${p.fieldName} = $1 WHERE ${idCol} = $2`, [p.oldValue, p.entityId]);
        if (changeLogId) await db.execute(`DELETE FROM change_log WHERE id = $1`, [changeLogId]);
        await db.execute(`UPDATE proposal SET status = 'pending', resolvedAt = NULL WHERE id = $1`, [proposalId]);
      },
      async () => {
        const db = await getDb();
        await db.execute(`UPDATE ${table} SET ${p.fieldName} = $1 WHERE ${idCol} = $2`, [p.newValue, p.entityId]);
        if (changeLogId) {
          await db.execute(
            `INSERT OR IGNORE INTO change_log (id, proposalId, entityType, entityId, fieldName, oldValue, newValue, actor)
             VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
            [changeLogId, proposalId, p.entityType, p.entityId, p.fieldName, p.oldValue, p.newValue, 'user']
          );
        }
        await db.execute(`UPDATE proposal SET status = 'approved', resolvedAt = datetime('now') WHERE id = $1`, [proposalId]);
      },
      {
        tableName: 'proposal',
        rowId: String(proposalId),
        oldData: { status: 'pending', value: p.oldValue },
        newData: { status: 'approved', value: p.newValue, changeLogId },
      }
    );

    return { success: true };
  } catch (e) {
    return { success: false, error: String(e) };
  }
}

export async function rejectProposal(proposalId: number): Promise<void> {
  const d = await getDb();
  const rows = await d.select<Proposal[]>(`SELECT * FROM proposal WHERE id = $1`, [proposalId]);
  if (rows.length === 0) return;
  const old = rows[0];
  await d.execute(`UPDATE proposal SET status = 'rejected', resolvedAt = datetime('now') WHERE id = $1`, [proposalId]);
  await pushUndoAction(
    `Rejected proposal ${proposalId}`,
    async () => {
      const db = await getDb();
      await db.execute(`UPDATE proposal SET status = $1, resolvedAt = $2 WHERE id = $3`, [old.status, old.resolvedAt || null, proposalId]);
    },
    async () => {
      const db = await getDb();
      await db.execute(`UPDATE proposal SET status = 'rejected', resolvedAt = datetime('now') WHERE id = $1`, [proposalId]);
    },
    { tableName: 'proposal', rowId: String(proposalId), oldData: { status: old.status }, newData: { status: 'rejected' } }
  );
}

export async function undoChange(changeLogId: number): Promise<{ success: boolean; error?: string }> {
  const d = await getDb();
  try {
    const rows = await d.select<ChangeLogEntry[]>(`SELECT * FROM change_log WHERE id = $1`, [changeLogId]);
    if (rows.length === 0) return { success: false, error: 'Change log entry not found' };
    const cl = rows[0];
    if (cl.undoneAt) return { success: false, error: 'Already undone' };

    const table = cl.entityType === 'person' ? 'person' : cl.entityType === 'family' ? 'family' : cl.entityType === 'event' ? 'event' : 'source';
    if (!SAFE_FIELDS[table]?.has(cl.fieldName)) return { success: false, error: `Invalid field: ${cl.fieldName}` };
    const idCol = table === 'person' || table === 'family' || table === 'source' ? 'xref' : 'id';
    await d.execute(`UPDATE ${table} SET ${cl.fieldName} = $1 WHERE ${idCol} = $2`, [cl.oldValue, cl.entityId]);

    // Mark as undone
    await d.execute(`UPDATE change_log SET undoneAt = datetime('now') WHERE id = $1`, [changeLogId]);

    // Mark proposal as superseded
    if (cl.proposalId) {
      await d.execute(`UPDATE proposal SET status = 'superseded' WHERE id = $1`, [cl.proposalId]);
    }

    return { success: true };
  } catch (e) {
    return { success: false, error: String(e) };
  }
}

export async function validateProposal(p: Omit<Proposal, 'id'>): Promise<{ valid: boolean; violations: string[] }> {
  const d = await getDb();
  const rules = await d.select<QualityRule[]>(`SELECT * FROM quality_rule WHERE isActive = 1`);
  const violations: string[] = [];

  for (const rule of rules) {
    switch (rule.ruleKey) {
      case 'no_future_dates': {
        if (['birthDate', 'deathDate', 'marriageDate', 'dateValue'].includes(p.fieldName)) {
          const yearMatch = p.newValue.match(/(\d{4})/);
          if (yearMatch && parseInt(yearMatch[1]) > new Date().getFullYear()) {
            if (rule.ruleType === 'reject') violations.push(`${rule.name}: ${p.newValue} is in the future`);
          }
        }
        break;
      }
      case 'birth_before_death': {
        if (p.entityType === 'person' && (p.fieldName === 'birthDate' || p.fieldName === 'deathDate')) {
          const person = await getPerson(p.entityId);
          if (person) {
            const birth = p.fieldName === 'birthDate' ? p.newValue : person.birthDate;
            const death = p.fieldName === 'deathDate' ? p.newValue : person.deathDate;
            const byear = birth.match(/(\d{4})/)?.[1];
            const dyear = death.match(/(\d{4})/)?.[1];
            if (byear && dyear && parseInt(byear) > parseInt(dyear)) {
              violations.push(`${rule.name}: birth ${birth} is after death ${death}`);
            }
          }
        }
        break;
      }
      case 'low_confidence_evidence': {
        if (p.confidence < 0.6 && !p.evidenceSource) {
          violations.push(`${rule.name}: confidence ${p.confidence} requires evidence source`);
        }
        break;
      }
      case 'reasonable_age': {
        if (p.entityType === 'person' && (p.fieldName === 'birthDate' || p.fieldName === 'deathDate')) {
          const person = await getPerson(p.entityId);
          if (person) {
            const birth = p.fieldName === 'birthDate' ? p.newValue : person.birthDate;
            const death = p.fieldName === 'deathDate' ? p.newValue : person.deathDate;
            const byear = birth.match(/(\d{4})/)?.[1];
            const dyear = death.match(/(\d{4})/)?.[1];
            if (byear && dyear && (parseInt(dyear) - parseInt(byear)) > 120) {
              violations.push(`${rule.name}: implied age ${parseInt(dyear) - parseInt(byear)} exceeds 120`);
            }
          }
        }
        break;
      }
      case 'no_duplicates': {
        const existing = await d.select<Proposal[]>(
          `SELECT id FROM proposal WHERE entityId = $1 AND fieldName = $2 AND newValue = $3 AND status = 'pending' LIMIT 1`,
          [p.entityId, p.fieldName, p.newValue]
        );
        if (existing.length > 0) {
          violations.push(`${rule.name}: identical pending proposal already exists`);
        }
        break;
      }
    }
  }

  return { valid: violations.length === 0, violations };
}

// ===== Agent Run =====

export async function insertAgentRun(run: Omit<AgentRun, 'id'>): Promise<number> {
  const d = await getDb();
  const result = await d.execute(
    `INSERT INTO agent_run (runId, provider, model, personXref, proposalCount, status, startedAt)
     VALUES ($1,$2,$3,$4,$5,$6,$7)`,
    [run.runId, run.provider, run.model, run.personXref, 0, 'running', run.startedAt || new Date().toISOString()]
  );
  return result.lastInsertId ?? 0;
}

export async function updateAgentRun(runId: string, data: { status?: string; proposalCount?: number; completedAt?: string }): Promise<void> {
  const d = await getDb();
  const sets: string[] = [];
  const vals: any[] = [];
  let i = 1;
  if (data.status) { sets.push(`status = $${i++}`); vals.push(data.status); }
  if (data.proposalCount !== undefined) { sets.push(`proposalCount = $${i++}`); vals.push(data.proposalCount); }
  if (data.completedAt) { sets.push(`completedAt = $${i++}`); vals.push(data.completedAt); }
  if (sets.length === 0) return;
  vals.push(runId);
  await d.execute(`UPDATE agent_run SET ${sets.join(', ')} WHERE runId = $${i}`, vals);
}

export async function getAgentRuns(personXref?: string): Promise<AgentRun[]> {
  const d = await getDb();
  if (personXref) {
    return await d.select<AgentRun[]>(`SELECT * FROM agent_run WHERE personXref = $1 ORDER BY startedAt DESC`, [personXref]);
  }
  return await d.select<AgentRun[]>(`SELECT * FROM agent_run ORDER BY startedAt DESC LIMIT 50`);
}

export async function getQualityRules(): Promise<QualityRule[]> {
  const d = await getDb();
  return await d.select<QualityRule[]>(`SELECT * FROM quality_rule ORDER BY name`);
}

export async function toggleQualityRule(id: number, isActive: number): Promise<void> {
  const d = await getDb();
  const old = await d.select<{ isActive: number }[]>(`SELECT isActive FROM quality_rule WHERE id = $1`, [id]);
  const oldActive = old.length ? old[0].isActive : isActive;
  await d.execute(`UPDATE quality_rule SET isActive = $1 WHERE id = $2`, [isActive, id]);
  await pushUndoAction(
    `Toggled quality rule #${id} ${isActive ? 'on' : 'off'}`,
    async () => { const db = await getDb(); await db.execute(`UPDATE quality_rule SET isActive = $1 WHERE id = $2`, [oldActive, id]); },
    async () => { const db = await getDb(); await db.execute(`UPDATE quality_rule SET isActive = $1 WHERE id = $2`, [isActive, id]); },
    { tableName: 'quality_rule', rowId: String(id), oldData: { isActive: oldActive }, newData: { isActive } }
  );
}

export async function getChangeLog(entityId?: string): Promise<ChangeLogEntry[]> {
  const d = await getDb();
  if (entityId) {
    return await d.select<ChangeLogEntry[]>(`SELECT * FROM change_log WHERE entityId = $1 ORDER BY appliedAt DESC`, [entityId]);
  }
  return await d.select<ChangeLogEntry[]>(`SELECT * FROM change_log ORDER BY appliedAt DESC LIMIT 100`);
}

export async function clearAll(): Promise<void> {
  const d = await getDb();
  await d.execute('BEGIN TRANSACTION');
  try {
    await d.execute(`DROP TRIGGER IF EXISTS person_fts_ai`);
    await d.execute(`DROP TRIGGER IF EXISTS person_fts_ad`);
    await d.execute(`DROP TRIGGER IF EXISTS person_fts_au`);
    await d.execute(`DROP TABLE IF EXISTS person_fts`);
    // Delete in dependency order
    await d.execute(`DELETE FROM change_log`);
    await d.execute(`DELETE FROM proposal`);
    await d.execute(`DELETE FROM agent_run`);
    await d.execute(`DELETE FROM media_person_link`);
    await d.execute(`DELETE FROM child_link`);
    await d.execute(`DELETE FROM event`);
    await d.execute(`DELETE FROM media`);
    await d.execute(`DELETE FROM source`);
    await d.execute(`DELETE FROM family`);
    await d.execute(`DELETE FROM person`);
    await d.execute(`DELETE FROM place`);
    await d.execute(`DELETE FROM citation`);
    await d.execute(`DELETE FROM dismissed_issue`);
    await d.execute('COMMIT');
  } catch (e) {
    await d.execute('ROLLBACK');
    throw e;
  }
}

export async function getSetting(key: string): Promise<string | null> {
  const d = await getDb();
  const rows = await d.select<{ value: string }[]>('SELECT value FROM settings WHERE key = $1', [key]);
  return rows.length > 0 ? rows[0].value : null;
}

export async function setSetting(key: string, value: string): Promise<void> {
  const d = await getDb();
  await d.execute('INSERT INTO settings (key, value) VALUES ($1, $2) ON CONFLICT(key) DO UPDATE SET value = $2', [key, value]);
}

export async function getSettingsLike(prefix: string): Promise<{ key: string; value: string }[]> {
  const d = await getDb();
  return d.select<{ key: string; value: string }[]>('SELECT key, value FROM settings WHERE key LIKE $1', [prefix + '%']);
}

export async function getEventsForPlace(placeName: string): Promise<(GedcomEvent & { personName?: string })[]> {
  const d = await getDb();
  const events = await d.select<GedcomEvent[]>(
    'SELECT * FROM event WHERE place = $1 ORDER BY dateValue', [placeName]
  );
  const results: (GedcomEvent & { personName?: string })[] = [];
  for (const e of events) {
    const person = await getPerson(e.ownerXref);
    results.push({ ...e, personName: person ? `${person.givenName} ${person.surname}`.trim() : e.ownerXref });
  }
  return results;
}

export async function getCitationsForSource(sourceXref: string): Promise<{ personXref: string; personName: string; page: string; quality: string }[]> {
  const d = await getDb();
  const citations = await d.select<{ personXref: string; page: string; quality: string }[]>(
    'SELECT personXref, page, quality FROM citation WHERE sourceXref = $1', [sourceXref]
  );
  const results: { personXref: string; personName: string; page: string; quality: string }[] = [];
  for (const c of citations) {
    const person = c.personXref ? await getPerson(c.personXref) : null;
    results.push({
      ...c,
      personName: person ? `${person.givenName} ${person.surname}`.trim() : c.personXref || '—',
    });
  }
  return results;
}

export async function findSameNameGenerations(): Promise<{ elder: Person; younger: Person; yearGap: number }[]> {
  const d = await getDb();
  const persons = await d.select<Person[]>('SELECT * FROM person WHERE givenName != "" AND surname != "" AND birthDate != ""');
  const byName = new Map<string, Person[]>();
  for (const p of persons) {
    const key = `${p.givenName.toLowerCase().trim()}|${p.surname.toLowerCase().trim()}`;
    if (!byName.has(key)) byName.set(key, []);
    byName.get(key)!.push(p);
  }
  const results: { elder: Person; younger: Person; yearGap: number }[] = [];
  for (const [, group] of byName) {
    if (group.length < 2) continue;
    const withYears = group.map(p => {
      const m = p.birthDate.match(/(\d{4})/);
      return { person: p, year: m ? parseInt(m[1]) : null };
    }).filter(x => x.year !== null).sort((a, b) => a.year! - b.year!);
    for (let i = 0; i < withYears.length; i++) {
      for (let j = i + 1; j < withYears.length; j++) {
        const gap = withYears[j].year! - withYears[i].year!;
        if (gap >= 20 && gap <= 50) {
          results.push({ elder: withYears[i].person, younger: withYears[j].person, yearGap: gap });
        }
      }
    }
  }
  return results;
}

export async function findCollateralRelatives(rootXref: string): Promise<{ person: Person; relationship: string }[]> {
  // Walk up the direct line from root, then find siblings at each generation
  const d = await getDb();
  const directLine = new Set<string>();
  const collaterals: { person: Person; relationship: string }[] = [];

  async function walkUp(xref: string, gen: number) {
    if (directLine.has(xref)) return;
    directLine.add(xref);
    // Find parent family
    const links = await d.select<{familyXref: string}[]>('SELECT familyXref FROM child_link WHERE childXref = $1', [xref]);
    for (const link of links) {
      const fam = await d.select<{partner1Xref: string; partner2Xref: string}[]>('SELECT partner1Xref, partner2Xref FROM family WHERE xref = $1', [link.familyXref]);
      if (fam.length === 0) continue;
      const f = fam[0];
      // Parents are direct line
      if (f.partner1Xref) await walkUp(f.partner1Xref, gen + 1);
      if (f.partner2Xref) await walkUp(f.partner2Xref, gen + 1);
    }
  }

  await walkUp(rootXref, 0);

  // Now find siblings of direct line who are NOT in direct line
  for (const directXref of directLine) {
    const links = await d.select<{familyXref: string}[]>('SELECT familyXref FROM child_link WHERE childXref = $1', [directXref]);
    for (const link of links) {
      const siblings = await d.select<{childXref: string}[]>('SELECT childXref FROM child_link WHERE familyXref = $1 AND childXref != $2', [link.familyXref, directXref]);
      for (const sib of siblings) {
        if (!directLine.has(sib.childXref)) {
          const person = await d.select<Person[]>('SELECT * FROM person WHERE xref = $1', [sib.childXref]);
          if (person.length > 0) {
            collaterals.push({ person: person[0], relationship: 'Collateral' });
          }
        }
      }
    }
  }

  return collaterals;
}

export async function rebuildFTS(): Promise<void> {
  const d = await getDb();
  // Recreate FTS table and populate from existing data
  await d.execute(`CREATE VIRTUAL TABLE IF NOT EXISTS person_fts USING fts5(
    givenName, surname, birthPlace, deathPlace,
    content='person', content_rowid='id',
    tokenize='porter unicode61'
  )`);
  await d.execute(`INSERT INTO person_fts(person_fts) VALUES('rebuild')`);

  // Recreate triggers
  await d.execute(`CREATE TRIGGER IF NOT EXISTS person_fts_ai AFTER INSERT ON person BEGIN
    INSERT INTO person_fts(rowid, givenName, surname, birthPlace, deathPlace)
    VALUES (new.id, new.givenName, new.surname, new.birthPlace, new.deathPlace);
  END`);
  await d.execute(`CREATE TRIGGER IF NOT EXISTS person_fts_ad AFTER DELETE ON person BEGIN
    INSERT INTO person_fts(person_fts, rowid, givenName, surname, birthPlace, deathPlace)
    VALUES ('delete', old.id, old.givenName, old.surname, old.birthPlace, old.deathPlace);
  END`);
  await d.execute(`CREATE TRIGGER IF NOT EXISTS person_fts_au AFTER UPDATE ON person BEGIN
    INSERT INTO person_fts(person_fts, rowid, givenName, surname, birthPlace, deathPlace)
    VALUES ('delete', old.id, old.givenName, old.surname, old.birthPlace, old.deathPlace);
    INSERT INTO person_fts(rowid, givenName, surname, birthPlace, deathPlace)
    VALUES (new.id, new.givenName, new.surname, new.birthPlace, new.deathPlace);
  END`);
}

// ===== JSON Backup/Restore =====

const BACKUP_TABLES = [
  'person',
  'family',
  'event',
  'source',
  'media',
  'media_person_link',
  'child_link',
  'place',
  'custom_group',
  'group_member',
  'association',
  'alternate_name',
  'research_log',
  'citation',
  'note',
  'research_task',
  'bookmark',
  'ai_chat',
  'dismissed_issue',
  'stories',
  'settings',
  'undo_log',
  'evidence',
  'gps_checklist',
  'proposal',
  'change_log',
  'quality_rule',
  'agent_run',
  'merge_log',
];

export type DbBackup = {
  version: 1;
  exportedAt: string;
  tables: Record<string, any[]>;
};

export async function exportDbAsJson(): Promise<DbBackup> {
  const d = await getDb();
  const tables: Record<string, any[]> = {};
  for (const table of BACKUP_TABLES) {
    tables[table] = await d.select<any[]>(`SELECT * FROM ${table}`);
  }
  return {
    version: 1,
    exportedAt: new Date().toISOString(),
    tables,
  };
}

export async function importDbFromJson(backup: DbBackup): Promise<void> {
  const d = await getDb();
  await d.execute('BEGIN');
  try {
    await d.execute('PRAGMA foreign_keys = OFF');
    for (const table of BACKUP_TABLES) {
      await d.execute(`DELETE FROM ${table}`);
    }
    for (const table of BACKUP_TABLES) {
      const rows = backup.tables?.[table] ?? [];
      if (rows.length === 0) continue;
      const cols = Object.keys(rows[0]);
      const placeholders = cols.map((_, i) => `$${i + 1}`).join(', ');
      const sql = `INSERT INTO ${table} (${cols.join(', ')}) VALUES (${placeholders})`;
      for (const row of rows) {
        const vals = cols.map((c) => row[c]);
        await d.execute(sql, vals);
      }
    }
    await d.execute('PRAGMA foreign_keys = ON');
    await d.execute('COMMIT');
  } catch (e) {
    await d.execute('ROLLBACK');
    throw e;
  }
}

export async function getBackupJson(): Promise<string> {
  const backup = await exportDbAsJson();
  return JSON.stringify({
    version: 1,
    appVersion: '0.1.0',
    exportedAt: backup.exportedAt,
    tables: backup.tables,
  });
}

export async function exportDatabase(): Promise<Uint8Array> {
  const json = await getBackupJson();
  return new TextEncoder().encode(json);
}

export async function importDatabase(data: Uint8Array): Promise<void> {
  const json = new TextDecoder().decode(data);
  const parsed = JSON.parse(json) as DbBackup;
  await importDbFromJson(parsed);
}

export type EvidenceRecord = {
  id: number;
  personXref: string;
  factType: string;
  factValue: string;
  sourceXref: string;
  informationType: string;
  evidenceType: string;
  quality: string;
  analysisNotes: string;
  createdAt: string;
};

export async function getEvidence(personXref: string, factType?: string): Promise<EvidenceRecord[]> {
  const d = await getDb();
  if (factType) {
    return d.select<EvidenceRecord[]>(
      `SELECT * FROM evidence WHERE personXref = $1 AND factType = $2 ORDER BY createdAt DESC`,
      [personXref, factType]
    );
  }
  return d.select<EvidenceRecord[]>(`SELECT * FROM evidence WHERE personXref = $1 ORDER BY createdAt DESC`, [personXref]);
}

export async function addEvidence(e: Omit<EvidenceRecord, 'id' | 'createdAt'>): Promise<number> {
  const d = await getDb();
  const result = await d.execute(
    `INSERT INTO evidence (personXref, factType, factValue, sourceXref, informationType, evidenceType, quality, analysisNotes)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
    [e.personXref, e.factType, e.factValue, e.sourceXref, e.informationType, e.evidenceType, e.quality, e.analysisNotes]
  );
  const id = result.lastInsertId ?? 0;
  await pushUndoAction(
    `Added evidence`,
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM evidence WHERE id = $1`, [id]);
    },
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO evidence (id, personXref, factType, factValue, sourceXref, informationType, evidenceType, quality, analysisNotes)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
        [id, e.personXref, e.factType, e.factValue, e.sourceXref, e.informationType, e.evidenceType, e.quality, e.analysisNotes]
      );
    },
    { tableName: 'evidence', rowId: String(id), newData: e }
  );
  return id;
}

export async function updateEvidence(id: number, updates: Partial<EvidenceRecord>): Promise<void> {
  const d = await getDb();
  const rows = await d.select<EvidenceRecord[]>(`SELECT * FROM evidence WHERE id = $1`, [id]);
  if (rows.length === 0) return;
  const before = rows[0];
  const sets: string[] = [];
  const vals: any[] = [];
  const oldData: Record<string, unknown> = {};
  const newData: Record<string, unknown> = {};
  let i = 1;
  for (const [k, v] of Object.entries(updates)) {
    if (k === 'id' || k === 'createdAt') continue;
    sets.push(`${k} = $${i++}`);
    vals.push(v);
    oldData[k] = (before as any)[k];
    newData[k] = v;
  }
  if (sets.length === 0) return;
  vals.push(id);
  await d.execute(`UPDATE evidence SET ${sets.join(', ')} WHERE id = $${i}`, vals);
  await pushUndoAction(
    `Updated evidence ${id}`,
    async () => {
      const db = await getDb();
      const undoSets = Object.keys(oldData).map((k, idx) => `${k} = $${idx + 1}`);
      await db.execute(
        `UPDATE evidence SET ${undoSets.join(', ')} WHERE id = $${undoSets.length + 1}`,
        [...Object.values(oldData), id]
      );
    },
    async () => {
      const db = await getDb();
      const redoSets = Object.keys(newData).map((k, idx) => `${k} = $${idx + 1}`);
      await db.execute(
        `UPDATE evidence SET ${redoSets.join(', ')} WHERE id = $${redoSets.length + 1}`,
        [...Object.values(newData), id]
      );
    },
    { tableName: 'evidence', rowId: String(id), oldData, newData }
  );
}

export async function deleteEvidence(id: number): Promise<void> {
  const d = await getDb();
  const rows = await d.select<EvidenceRecord[]>(`SELECT * FROM evidence WHERE id = $1`, [id]);
  if (rows.length === 0) return;
  const old = rows[0];
  await d.execute(`DELETE FROM evidence WHERE id = $1`, [id]);
  await pushUndoAction(
    `Deleted evidence`,
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO evidence (id, personXref, factType, factValue, sourceXref, informationType, evidenceType, quality, analysisNotes, createdAt)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
        [old.id, old.personXref, old.factType, old.factValue, old.sourceXref, old.informationType, old.evidenceType, old.quality, old.analysisNotes, old.createdAt]
      );
    },
    async () => {
      const db = await getDb();
      await db.execute(`DELETE FROM evidence WHERE id = $1`, [id]);
    },
    { tableName: 'evidence', rowId: String(id), oldData: old }
  );
}

export async function getFactSummary(personXref: string): Promise<{ factType: string; valueCount: number; evidenceCount: number }[]> {
  const d = await getDb();
  return d.select<{ factType: string; valueCount: number; evidenceCount: number }[]>(
    `SELECT factType, COUNT(DISTINCT factValue) as valueCount, COUNT(*) as evidenceCount
     FROM evidence WHERE personXref = $1 GROUP BY factType ORDER BY factType`,
    [personXref]
  );
}

export async function getGpsChecklist(personXref: string): Promise<{ item: number; completed: number; notes: string }[]> {
  const d = await getDb();
  return d.select<{ item: number; completed: number; notes: string }[]>(
    `SELECT item, completed, notes FROM gps_checklist WHERE personXref = $1 ORDER BY item`,
    [personXref]
  );
}

export async function setGpsChecklist(personXref: string, item: number, completed: number, notes: string): Promise<void> {
  const d = await getDb();
  const beforeRows = await d.select<{ personXref: string; item: number; completed: number; notes: string }[]>(
    `SELECT personXref, item, completed, notes FROM gps_checklist WHERE personXref = $1 AND item = $2`,
    [personXref, item]
  );
  const hadBefore = beforeRows.length > 0;
  const before = beforeRows[0];
  await d.execute(
    `INSERT INTO gps_checklist (personXref, item, completed, notes)
     VALUES ($1,$2,$3,$4)
     ON CONFLICT(personXref, item) DO UPDATE SET completed = $3, notes = $4`,
    [personXref, item, completed, notes]
  );
  await pushUndoAction(
    `Updated GPS checklist item ${item}`,
    async () => {
      const db = await getDb();
      if (hadBefore) {
        await db.execute(
          `UPDATE gps_checklist SET completed = $1, notes = $2 WHERE personXref = $3 AND item = $4`,
          [before.completed, before.notes, personXref, item]
        );
      } else {
        await db.execute(`DELETE FROM gps_checklist WHERE personXref = $1 AND item = $2`, [personXref, item]);
      }
    },
    async () => {
      const db = await getDb();
      await db.execute(
        `INSERT INTO gps_checklist (personXref, item, completed, notes)
         VALUES ($1,$2,$3,$4)
         ON CONFLICT(personXref, item) DO UPDATE SET completed = $3, notes = $4`,
        [personXref, item, completed, notes]
      );
    },
    {
      tableName: 'gps_checklist',
      rowId: `${personXref}:${item}`,
      oldData: hadBefore ? before : null,
      newData: { personXref, item, completed, notes },
    }
  );
}
