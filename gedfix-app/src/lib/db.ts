import { isTauri, type PlatformDatabase } from './platform';
import type { Person, Family, GedcomEvent, Source, GedcomMedia, ChildLink, AppStats, Place, PersonGroup, Association, AlternateName, ResearchLogEntry, Citation, ResearchNote, ResearchTask, Bookmark, AIChatMessage, TreeIssue, GeneratedStory, Proposal, ChangeLogEntry, QualityRule, AgentRun } from './types';

let db: PlatformDatabase | null = null;

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
    title TEXT DEFAULT ''
  )`);

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
    createdAt TEXT DEFAULT ''
  )`);

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
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_mpl_person ON media_person_link(personXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_mpl_media ON media_person_link(mediaId)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_mpl_primary ON media_person_link(personXref, isPrimary)`);
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
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_ai_chat_timestamp ON ai_chat(timestamp)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_stories_person ON stories(personXref)`);
  await d.execute(`CREATE INDEX IF NOT EXISTS idx_stories_family ON stories(familyXref)`);

  await d.execute(`CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT DEFAULT '')`);

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

  // FTS5 is created after import via rebuildFTS()
}

// ===== Person queries =====

export async function getPersons(search?: string, limit = 500): Promise<Person[]> {
  const d = await getDb();
  if (search && search.trim()) {
    const term = search.trim();
    // Try FTS5 first (sub-millisecond), fall back to LIKE
    try {
      const ftsQuery = term.split(/\s+/).map(w => `"${w}"*`).join(' ');
      return await d.select<Person[]>(
        `SELECT p.* FROM person p JOIN person_fts f ON p.id = f.rowid
         WHERE person_fts MATCH $1 ORDER BY rank LIMIT $2`,
        [ftsQuery, limit]
      );
    } catch {
      const q = `%${term}%`;
      return await d.select<Person[]>(
        `SELECT * FROM person WHERE givenName LIKE $1 OR surname LIKE $1 OR xref LIKE $1 ORDER BY surname, givenName LIMIT $2`,
        [q, limit]
      );
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
  const validFields = SAFE_FIELDS.person;
  const sets: string[] = [];
  const vals: any[] = [];
  let i = 1;
  for (const [k, v] of Object.entries(data)) {
    if (k === 'id' || k === 'xref') continue;
    if (!validFields.has(k)) continue;
    sets.push(`${k} = $${i}`);
    vals.push(v);
    i++;
  }
  if (sets.length === 0) return;
  vals.push(xref);
  await d.execute(`UPDATE person SET ${sets.join(', ')} WHERE xref = $${i}`, vals);
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
  await d.execute(`
    INSERT OR IGNORE INTO media_person_link (mediaId, personXref, isPrimary, role, addedBy, createdAt)
    VALUES ($1, $2, $3, $4, 'gedcom_import', datetime('now'))
  `, [mediaId, personXref, isPrimary ? 1 : 0, role]);
}

export async function setMediaPrimary(mediaId: number, personXref: string): Promise<void> {
  const d = await getDb();
  // Clear old primary for this person
  await d.execute(`UPDATE media_person_link SET isPrimary = 0 WHERE personXref = $1`, [personXref]);
  // Set new primary
  await d.execute(`UPDATE media_person_link SET isPrimary = 1 WHERE mediaId = $1 AND personXref = $2`, [mediaId, personXref]);
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

// ===== Group queries =====

export async function getGroups(): Promise<PersonGroup[]> {
  const d = await getDb();
  return await d.select<PersonGroup[]>(`SELECT * FROM custom_group ORDER BY name`);
}

export async function insertGroup(g: Omit<PersonGroup, 'id'>): Promise<void> {
  const d = await getDb();
  await d.execute(
    `INSERT INTO custom_group (name, color, description, createdAt) VALUES ($1,$2,$3,$4)`,
    [g.name, g.color, g.description, g.createdAt]
  );
}

export async function deleteGroup(id: number): Promise<void> {
  const d = await getDb();
  await d.execute(`DELETE FROM group_member WHERE groupId = $1`, [id]);
  await d.execute(`DELETE FROM custom_group WHERE id = $1`, [id]);
}

export async function getGroupMembers(groupId: number): Promise<string[]> {
  const d = await getDb();
  const rows = await d.select<{ personXref: string }[]>(`SELECT personXref FROM group_member WHERE groupId = $1`, [groupId]);
  return rows.map(r => r.personXref);
}

export async function addGroupMember(groupId: number, personXref: string): Promise<void> {
  const d = await getDb();
  await d.execute(`INSERT OR IGNORE INTO group_member (groupId, personXref, addedAt) VALUES ($1,$2,$3)`, [groupId, personXref, new Date().toISOString()]);
}

export async function removeGroupMember(groupId: number, personXref: string): Promise<void> {
  const d = await getDb();
  await d.execute(`DELETE FROM group_member WHERE groupId = $1 AND personXref = $2`, [groupId, personXref]);
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
  await d.execute(
    `INSERT INTO research_log (personXref, repository, searchTerms, recordsViewed, conclusion, sourceXref, resultType, searchDate, createdAt) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
    [entry.personXref, entry.repository, entry.searchTerms, entry.recordsViewed, entry.conclusion, entry.sourceXref, entry.resultType, entry.searchDate, entry.createdAt]
  );
}

export async function deleteResearchLog(id: number): Promise<void> {
  const d = await getDb();
  await d.execute(`DELETE FROM research_log WHERE id = $1`, [id]);
}

// ===== Notes queries =====

export async function getNotes(ownerXref?: string): Promise<ResearchNote[]> {
  const d = await getDb();
  if (ownerXref) {
    return await d.select<ResearchNote[]>(`SELECT * FROM note WHERE ownerXref = $1 ORDER BY updatedAt DESC`, [ownerXref]);
  }
  return await d.select<ResearchNote[]>(`SELECT * FROM note ORDER BY updatedAt DESC`);
}

export async function insertNote(n: Omit<ResearchNote, 'id'>): Promise<void> {
  const d = await getDb();
  await d.execute(
    `INSERT INTO note (ownerXref, ownerType, title, content, createdAt, updatedAt) VALUES ($1,$2,$3,$4,$5,$6)`,
    [n.ownerXref, n.ownerType, n.title, n.content, n.createdAt, n.updatedAt]
  );
}

export async function deleteNote(id: number): Promise<void> {
  const d = await getDb();
  await d.execute(`DELETE FROM note WHERE id = $1`, [id]);
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
  await d.execute(
    `INSERT INTO research_task (personXref, title, description, status, priority, dueDate, createdAt, completedAt) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
    [t.personXref, t.title, t.description, t.status, t.priority, t.dueDate, t.createdAt, t.completedAt]
  );
}

export async function updateTask(id: number, status: string, completedAt: string): Promise<void> {
  const d = await getDb();
  await d.execute(`UPDATE research_task SET status = $1, completedAt = $2 WHERE id = $3`, [status, completedAt, id]);
}

export async function deleteTask(id: number): Promise<void> {
  const d = await getDb();
  await d.execute(`DELETE FROM research_task WHERE id = $1`, [id]);
}

// ===== Bookmark queries =====

export async function getBookmarks(): Promise<Bookmark[]> {
  const d = await getDb();
  return await d.select<Bookmark[]>(`SELECT * FROM bookmark ORDER BY createdAt DESC`);
}

export async function insertBookmark(b: Omit<Bookmark, 'id'>): Promise<void> {
  const d = await getDb();
  await d.execute(`INSERT INTO bookmark (personXref, label, createdAt) VALUES ($1,$2,$3)`, [b.personXref, b.label, b.createdAt]);
}

export async function deleteBookmark(id: number): Promise<void> {
  const d = await getDb();
  await d.execute(`DELETE FROM bookmark WHERE id = $1`, [id]);
}

export async function isBookmarked(personXref: string): Promise<boolean> {
  const d = await getDb();
  const r = await d.select<[{ c: number }]>(`SELECT COUNT(*) as c FROM bookmark WHERE personXref = $1`, [personXref]);
  return (r[0]?.c ?? 0) > 0;
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
  await d.execute(
    `INSERT INTO ai_chat (provider, model, role, content, personXref, timestamp) VALUES ($1,$2,$3,$4,$5,$6)`,
    [m.provider, m.model, m.role, m.content, m.personXref, m.timestamp]
  );
}

export async function clearAIChatHistory(): Promise<void> {
  const d = await getDb();
  await d.execute(`DELETE FROM ai_chat`);
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
  await db.execute(
    `INSERT INTO stories (personXref, familyXref, storyType, title, content, provider, model, tokensUsed, costEstimate, createdAt)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
    [story.personXref, story.familyXref, story.storyType, story.title, story.content, story.provider, story.model, story.tokensUsed, story.costEstimate, story.createdAt]
  );
}

export async function deleteStory(id: number): Promise<void> {
  const db = await getDb();
  await db.execute('DELETE FROM stories WHERE id = $1', [id]);
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
    `INSERT INTO media (xref, ownerXref, filePath, format, title) VALUES ($1,$2,$3,$4,$5)`,
    [m.xref, m.ownerXref, m.filePath, m.format, m.title]
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
  await d.execute(
    `INSERT OR REPLACE INTO dismissed_issue (issueKey, dismissedAt) VALUES ($1, $2)`,
    [issueKey, new Date().toISOString()]
  );
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

const SAFE_FIELDS: Record<string, Set<string>> = {
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
    await d.execute(
      `INSERT INTO change_log (proposalId, entityType, entityId, fieldName, oldValue, newValue, actor)
       VALUES ($1,$2,$3,$4,$5,$6,$7)`,
      [proposalId, p.entityType, p.entityId, p.fieldName, p.oldValue, p.newValue, 'user']
    );

    // Mark proposal as approved
    await d.execute(`UPDATE proposal SET status = 'approved', resolvedAt = datetime('now') WHERE id = $1`, [proposalId]);

    return { success: true };
  } catch (e) {
    return { success: false, error: String(e) };
  }
}

export async function rejectProposal(proposalId: number): Promise<void> {
  const d = await getDb();
  await d.execute(`UPDATE proposal SET status = 'rejected', resolvedAt = datetime('now') WHERE id = $1`, [proposalId]);
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
  await d.execute(`UPDATE quality_rule SET isActive = $1 WHERE id = $2`, [isActive, id]);
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
