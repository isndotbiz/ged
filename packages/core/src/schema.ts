import type { PlatformDatabase } from './platform';

export async function createSchema(d: PlatformDatabase): Promise<void> {
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

  // FTS5 is created after import via rebuildFTS()
}
