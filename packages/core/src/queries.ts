import type { PlatformDatabase } from './platform';
import type {
  Person, Family, GedcomEvent, Source, GedcomMedia, ChildLink, AppStats, Place,
  PersonGroup, AlternateName, ResearchLogEntry, Citation, ResearchNote,
  ResearchTask, Bookmark, AIChatMessage, TreeIssue, GeneratedStory, Proposal,
  ChangeLogEntry, QualityRule, AgentRun
} from './types';

// ===== Pure helpers (no db param needed) =====

export function parseDateForSort(dateValue: string | null | undefined): number {
  if (!dateValue) return 0;
  const trimmed = dateValue.trim();
  if (!trimmed) return 0;
  const match = trimmed.match(/(\d{4})/);
  return match ? parseInt(match[1], 10) : 0;
}

export function normalizeMediaPath(filePath: string): string {
  return filePath.trim().replace(/\\/g, '/').toLowerCase();
}

export type MediaCategory = 'headshots' | 'other' | 'delete-queue' | 'uncategorized';

export type DbBackup = {
  version: 1;
  exportedAt: string;
  tables: Record<string, any[]>;
};

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

export const SAFE_FIELDS: Record<string, Set<string>> = {
  person: new Set(['givenName','surname','suffix','sex','birthDate','birthPlace','deathDate','deathPlace','personColor','proofStatus']),
  family: new Set(['partner1Xref','partner2Xref','marriageDate','marriagePlace']),
  event: new Set(['eventType','dateValue','place','description']),
  source: new Set(['title','author','publisher']),
};

// ===== Person queries =====

export async function getPersons(d: PlatformDatabase, search?: string, limit = 500): Promise<Person[]> {
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

export async function getAllPersons(d: PlatformDatabase): Promise<Person[]> {
  return await d.select<Person[]>(`SELECT * FROM person ORDER BY surname, givenName`);
}

export async function getPerson(d: PlatformDatabase, xref: string): Promise<Person | null> {
  const rows = await d.select<Person[]>(`SELECT * FROM person WHERE xref = $1`, [xref]);
  return rows.length > 0 ? rows[0] : null;
}

export async function updatePerson(d: PlatformDatabase, xref: string, data: Partial<Person>): Promise<void> {
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

export async function updatePersonField(d: PlatformDatabase, xref: string, field: string, value: string): Promise<void> {
  if (!SAFE_FIELDS.person.has(field)) throw new Error(`Invalid person field ${field}`);
  await d.execute(`UPDATE person SET ${field} = $1 WHERE xref = $2`, [value, xref]);
}

// ===== Event queries =====

export async function getEvents(d: PlatformDatabase, ownerXref: string): Promise<GedcomEvent[]> {
  return await d.select<GedcomEvent[]>(
    `SELECT * FROM event WHERE ownerXref = $1 ORDER BY dateValue`,
    [ownerXref]
  );
}

export async function getAllEvents(d: PlatformDatabase): Promise<GedcomEvent[]> {
  return await d.select<GedcomEvent[]>(`SELECT * FROM event ORDER BY dateValue`);
}

// ===== Family queries =====

export async function getFamilies(d: PlatformDatabase): Promise<Family[]> {
  return await d.select<Family[]>(`SELECT * FROM family ORDER BY xref`);
}

export async function getFamily(d: PlatformDatabase, xref: string): Promise<Family | null> {
  const rows = await d.select<Family[]>(`SELECT * FROM family WHERE xref = $1`, [xref]);
  return rows.length > 0 ? rows[0] : null;
}

export async function getSpouseFamilies(d: PlatformDatabase, personXref: string): Promise<Family[]> {
  return await d.select<Family[]>(
    `SELECT * FROM family WHERE partner1Xref = $1 OR partner2Xref = $1`,
    [personXref]
  );
}

export async function getParentFamily(d: PlatformDatabase, childXref: string): Promise<Family | null> {
  const links = await d.select<ChildLink[]>(
    `SELECT * FROM child_link WHERE childXref = $1 LIMIT 1`,
    [childXref]
  );
  if (links.length === 0) return null;
  return await getFamily(d, links[0].familyXref);
}

export async function getParents(d: PlatformDatabase, personXref: string): Promise<{ father: Person | null; mother: Person | null }> {
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

export async function getChildren(d: PlatformDatabase, familyXref: string): Promise<Person[]> {
  return await d.select<Person[]>(
    `SELECT p.* FROM person p JOIN child_link cl ON p.xref = cl.childXref WHERE cl.familyXref = $1 ORDER BY cl.childOrder`,
    [familyXref]
  );
}

// ===== Source queries =====

export async function getSources(d: PlatformDatabase): Promise<Source[]> {
  return await d.select<Source[]>(`SELECT * FROM source ORDER BY title`);
}

// ===== Media queries =====

export async function getMediaForPerson(d: PlatformDatabase, ownerXref: string): Promise<(GedcomMedia & { isPrimary?: boolean; role?: string })[]> {
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

export async function getAllMedia(d: PlatformDatabase): Promise<GedcomMedia[]> {
  return await d.select<GedcomMedia[]>(`SELECT * FROM media WHERE filePath != '' ORDER BY title`);
}

export async function getAllMediaWithPersons(d: PlatformDatabase): Promise<(GedcomMedia & { personGivenName: string; personSurname: string; personSex: string; personBirthDate: string; personDeathDate: string })[]> {
  return await d.select(
    `SELECT m.*, COALESCE(p.givenName, '') as personGivenName, COALESCE(p.surname, '') as personSurname,
            COALESCE(p.sex, 'U') as personSex, COALESCE(p.birthDate, '') as personBirthDate, COALESCE(p.deathDate, '') as personDeathDate
     FROM media m LEFT JOIN person p ON m.ownerXref = p.xref
     WHERE m.filePath != '' ORDER BY m.title`
  );
}

export async function getPrimaryPhoto(d: PlatformDatabase, ownerXref: string): Promise<GedcomMedia | null> {
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

export async function getMediaWithPaths(d: PlatformDatabase, ownerXref: string): Promise<GedcomMedia[]> {
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

export async function getPeopleForMedia(d: PlatformDatabase, mediaId: number): Promise<{ personXref: string; givenName: string; surname: string; isPrimary: boolean }[]> {
  return await d.select<{ personXref: string; givenName: string; surname: string; isPrimary: boolean }[]>(`
    SELECT p.xref as personXref, p.givenName, p.surname, mpl.isPrimary
    FROM media_person_link mpl
    JOIN person p ON p.xref = mpl.personXref
    WHERE mpl.mediaId = $1
    ORDER BY mpl.isPrimary DESC
  `, [mediaId]);
}

export async function linkMediaToPerson(d: PlatformDatabase, mediaId: number, personXref: string, isPrimary: boolean = false, role: string = 'tagged'): Promise<void> {
  await d.execute(`
    INSERT OR IGNORE INTO media_person_link (mediaId, personXref, isPrimary, role, addedBy, createdAt)
    VALUES ($1, $2, $3, $4, 'gedcom_import', datetime('now'))
  `, [mediaId, personXref, isPrimary ? 1 : 0, role]);
}

export async function unlinkMediaFromPerson(d: PlatformDatabase, mediaId: number, personXref: string): Promise<void> {
  await d.execute(`DELETE FROM media_person_link WHERE mediaId = $1 AND personXref = $2`, [mediaId, personXref]);
}

export async function setMediaPrimary(d: PlatformDatabase, mediaId: number, personXref: string): Promise<void> {
  // Clear old primary for this person
  await d.execute(`UPDATE media_person_link SET isPrimary = 0 WHERE personXref = $1`, [personXref]);
  // Set new primary
  await d.execute(`UPDATE media_person_link SET isPrimary = 1 WHERE mediaId = $1 AND personXref = $2`, [mediaId, personXref]);
}

export async function getMediaForManagement(d: PlatformDatabase): Promise<(GedcomMedia & {
  category: MediaCategory;
  linkCount: number;
  peopleLabel: string;
})[]> {
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

export async function updateMediaCategory(d: PlatformDatabase, mediaIds: number[], category: MediaCategory): Promise<number> {
  if (mediaIds.length === 0) return 0;
  const updatePlaceholders = mediaIds.map((_, i) => `$${i + 2}`).join(', ');
  const result = await d.execute(
    `UPDATE media SET category = $1 WHERE id IN (${updatePlaceholders})`,
    [category, ...mediaIds]
  );
  return result.rowsAffected;
}

export async function processDeleteQueue(d: PlatformDatabase): Promise<{ mediaRemoved: number; linksRemoved: number }> {
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

export async function deduplicateMediaByNormalizedPath(d: PlatformDatabase): Promise<{
  duplicateGroups: number;
  entriesRemoved: number;
  linksTransferred: number;
}> {
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

export async function autoCategorizeMediaAfterDedup(d: PlatformDatabase): Promise<{ updated: number }> {
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

export async function moveNonPortraitHeadshotsToOther(d: PlatformDatabase): Promise<number> {
  const result = await d.execute(
    `UPDATE media
     SET category = 'other'
     WHERE category = 'headshots'
       AND id NOT IN (SELECT DISTINCT mediaId FROM media_person_link)`
  );
  return result.rowsAffected;
}

export async function updateMediaFilePath(d: PlatformDatabase, mediaId: number, filePath: string): Promise<void> {
  await d.execute(`UPDATE media SET filePath = $1 WHERE id = $2`, [filePath, mediaId]);
}

// ===== Place queries =====

export async function getPlaces(d: PlatformDatabase): Promise<Place[]> {
  return await d.select<Place[]>(`SELECT * FROM place ORDER BY eventCount DESC`);
}

export async function getPlaceCount(d: PlatformDatabase): Promise<number> {
  const r = await d.select<[{ c: number }]>(`SELECT COUNT(*) as c FROM place`);
  return r[0]?.c ?? 0;
}

// ===== Alternate name queries =====

export async function getAlternateNames(d: PlatformDatabase, personXref: string): Promise<AlternateName[]> {
  return await d.select<AlternateName[]>(
    `SELECT * FROM alternate_name WHERE personXref = $1 ORDER BY nameType, surname, givenName`,
    [personXref]
  );
}

export async function insertAlternateName(d: PlatformDatabase, n: Omit<AlternateName, 'id'>): Promise<void> {
  await d.execute(
    `INSERT INTO alternate_name (personXref, givenName, surname, suffix, nameType, source)
     VALUES ($1,$2,$3,$4,$5,$6)`,
    [n.personXref, n.givenName, n.surname, n.suffix, n.nameType, n.source]
  );
}

export async function updateAlternateName(d: PlatformDatabase, id: number, data: Partial<AlternateName>): Promise<void> {
  const sets: string[] = [];
  const vals: any[] = [];
  let i = 1;
  for (const [k, v] of Object.entries(data)) {
    if (k === 'id' || k === 'personXref') continue;
    if (!['givenName','surname','suffix','nameType','source'].includes(k)) continue;
    sets.push(`${k} = $${i}`);
    vals.push(v);
    i++;
  }
  if (sets.length === 0) return;
  vals.push(id);
  await d.execute(`UPDATE alternate_name SET ${sets.join(', ')} WHERE id = $${i}`, vals);
}

export async function deleteAlternateName(d: PlatformDatabase, id: number): Promise<void> {
  await d.execute(`DELETE FROM alternate_name WHERE id = $1`, [id]);
}

// ===== Group queries =====

export async function getGroups(d: PlatformDatabase): Promise<PersonGroup[]> {
  return await d.select<PersonGroup[]>(`SELECT * FROM custom_group ORDER BY name`);
}

export async function insertGroup(d: PlatformDatabase, g: Omit<PersonGroup, 'id'>): Promise<void> {
  await d.execute(
    `INSERT INTO custom_group (name, color, description, createdAt) VALUES ($1,$2,$3,$4)`,
    [g.name, g.color, g.description, g.createdAt]
  );
}

export async function deleteGroup(d: PlatformDatabase, id: number): Promise<void> {
  await d.execute(`DELETE FROM group_member WHERE groupId = $1`, [id]);
  await d.execute(`DELETE FROM custom_group WHERE id = $1`, [id]);
}

export async function getGroupMembers(d: PlatformDatabase, groupId: number): Promise<string[]> {
  const rows = await d.select<{ personXref: string }[]>(`SELECT personXref FROM group_member WHERE groupId = $1`, [groupId]);
  return rows.map(r => r.personXref);
}

export async function addGroupMember(d: PlatformDatabase, groupId: number, personXref: string): Promise<void> {
  const addedAt = new Date().toISOString();
  await d.execute(`INSERT OR IGNORE INTO group_member (groupId, personXref, addedAt) VALUES ($1,$2,$3)`, [groupId, personXref, addedAt]);
}

export async function removeGroupMember(d: PlatformDatabase, groupId: number, personXref: string): Promise<void> {
  await d.execute(`DELETE FROM group_member WHERE groupId = $1 AND personXref = $2`, [groupId, personXref]);
}

export async function getGroupMemberCount(d: PlatformDatabase, groupId: number): Promise<number> {
  const r = await d.select<[{ c: number }]>(`SELECT COUNT(*) as c FROM group_member WHERE groupId = $1`, [groupId]);
  return r[0]?.c ?? 0;
}

// ===== Research Log queries =====

export async function getResearchLogs(d: PlatformDatabase, personXref?: string): Promise<ResearchLogEntry[]> {
  if (personXref) {
    return await d.select<ResearchLogEntry[]>(`SELECT * FROM research_log WHERE personXref = $1 ORDER BY createdAt DESC`, [personXref]);
  }
  return await d.select<ResearchLogEntry[]>(`SELECT * FROM research_log ORDER BY createdAt DESC`);
}

export async function insertResearchLog(d: PlatformDatabase, entry: Omit<ResearchLogEntry, 'id'>): Promise<void> {
  await d.execute(
    `INSERT INTO research_log (personXref, repository, searchTerms, recordsViewed, conclusion, sourceXref, resultType, searchDate, createdAt) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
    [entry.personXref, entry.repository, entry.searchTerms, entry.recordsViewed, entry.conclusion, entry.sourceXref, entry.resultType, entry.searchDate, entry.createdAt]
  );
}

export async function deleteResearchLog(d: PlatformDatabase, id: number): Promise<void> {
  await d.execute(`DELETE FROM research_log WHERE id = $1`, [id]);
}

// ===== Notes queries =====

export async function getNotes(d: PlatformDatabase, ownerXref?: string): Promise<ResearchNote[]> {
  if (ownerXref) {
    return await d.select<ResearchNote[]>(`SELECT * FROM note WHERE ownerXref = $1 ORDER BY updatedAt DESC`, [ownerXref]);
  }
  return await d.select<ResearchNote[]>(`SELECT * FROM note ORDER BY updatedAt DESC`);
}

export async function searchNotes(d: PlatformDatabase, query: string): Promise<ResearchNote[]> {
  const q = `%${query.trim()}%`;
  if (!query.trim()) return getNotes(d);
  return await d.select<ResearchNote[]>(
    `SELECT * FROM note
     WHERE title LIKE $1 OR content LIKE $1
     ORDER BY updatedAt DESC`,
    [q]
  );
}

export async function insertNote(d: PlatformDatabase, n: Omit<ResearchNote, 'id'>): Promise<void> {
  await d.execute(
    `INSERT INTO note (ownerXref, ownerType, title, content, createdAt, updatedAt) VALUES ($1,$2,$3,$4,$5,$6)`,
    [n.ownerXref, n.ownerType, n.title, n.content, n.createdAt, n.updatedAt]
  );
}

export async function deleteNote(d: PlatformDatabase, id: number): Promise<void> {
  await d.execute(`DELETE FROM note WHERE id = $1`, [id]);
}

// ===== Task queries =====

export async function getTasks(d: PlatformDatabase, personXref?: string): Promise<ResearchTask[]> {
  if (personXref) {
    return await d.select<ResearchTask[]>(`SELECT * FROM research_task WHERE personXref = $1 ORDER BY CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 WHEN 'LOW' THEN 2 END, createdAt DESC`, [personXref]);
  }
  return await d.select<ResearchTask[]>(`SELECT * FROM research_task ORDER BY CASE priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 WHEN 'LOW' THEN 2 END, createdAt DESC`);
}

export async function insertTask(d: PlatformDatabase, t: Omit<ResearchTask, 'id'>): Promise<void> {
  await d.execute(
    `INSERT INTO research_task (personXref, title, description, status, priority, dueDate, createdAt, completedAt) VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
    [t.personXref, t.title, t.description, t.status, t.priority, t.dueDate, t.createdAt, t.completedAt]
  );
}

export async function updateTask(d: PlatformDatabase, id: number, status: string, completedAt: string): Promise<void> {
  await d.execute(`UPDATE research_task SET status = $1, completedAt = $2 WHERE id = $3`, [status, completedAt, id]);
}

export async function deleteTask(d: PlatformDatabase, id: number): Promise<void> {
  await d.execute(`DELETE FROM research_task WHERE id = $1`, [id]);
}

// ===== Bookmark queries =====

export async function getBookmarks(d: PlatformDatabase): Promise<Bookmark[]> {
  return await d.select<Bookmark[]>(`SELECT * FROM bookmark ORDER BY sortOrder ASC, createdAt DESC, id DESC`);
}

export async function insertBookmark(d: PlatformDatabase, b: Omit<Bookmark, 'id'>): Promise<void> {
  let nextSortOrder = b.sortOrder ?? 0;
  if (b.sortOrder === undefined) {
    const row = await d.select<[{ maxSort: number | null }]>(`SELECT MAX(sortOrder) as maxSort FROM bookmark`);
    nextSortOrder = (row[0]?.maxSort ?? -1) + 1;
  }
  await d.execute(
    `INSERT INTO bookmark (personXref, label, category, sortOrder, createdAt) VALUES ($1,$2,$3,$4,$5)`,
    [b.personXref, b.label, b.category || 'General', nextSortOrder, b.createdAt]
  );
}

export async function deleteBookmark(d: PlatformDatabase, id: number): Promise<void> {
  await d.execute(`DELETE FROM bookmark WHERE id = $1`, [id]);
}

export async function isBookmarked(d: PlatformDatabase, personXref: string): Promise<boolean> {
  const r = await d.select<[{ c: number }]>(`SELECT COUNT(*) as c FROM bookmark WHERE personXref = $1`, [personXref]);
  return (r[0]?.c ?? 0) > 0;
}

export async function updateBookmarkCategory(d: PlatformDatabase, id: number, category: string): Promise<void> {
  const next = category.trim() || 'General';
  await d.execute(`UPDATE bookmark SET category = $1 WHERE id = $2`, [next, id]);
}

export async function reorderBookmarks(d: PlatformDatabase, orderedIds: number[]): Promise<void> {
  if (orderedIds.length === 0) return;
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
}

export async function batchAddToGroup(d: PlatformDatabase, xrefs: string[], groupId: number): Promise<void> {
  if (xrefs.length === 0) return;
  await d.execute('BEGIN');
  try {
    for (const xref of xrefs) {
      await d.execute(
        `INSERT OR IGNORE INTO group_member (groupId, personXref, addedAt) VALUES ($1,$2,$3)`,
        [groupId, xref, new Date().toISOString()]
      );
    }
    await d.execute('COMMIT');
  } catch (e) {
    await d.execute('ROLLBACK');
    throw e;
  }
}

export async function batchBookmark(d: PlatformDatabase, xrefs: string[], label = ''): Promise<void> {
  if (xrefs.length === 0) return;
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
  } catch (e) {
    await d.execute('ROLLBACK');
    throw e;
  }
}

export async function batchRemoveBookmarks(d: PlatformDatabase, xrefs: string[]): Promise<void> {
  if (xrefs.length === 0) return;
  const lookupPlaceholders = xrefs.map((_, i) => `$${i + 1}`).join(', ');
  await d.execute(`DELETE FROM bookmark WHERE personXref IN (${lookupPlaceholders})`, xrefs);
}

export async function batchDeletePersons(d: PlatformDatabase, xrefs: string[]): Promise<void> {
  if (xrefs.length === 0) return;
  const placeholders = xrefs.map((_, i) => `$${i + 1}`).join(', ');
  await d.execute('BEGIN');
  try {
    await d.execute(`DELETE FROM media_person_link WHERE personXref IN (${placeholders})`, xrefs);
    await d.execute(`DELETE FROM child_link WHERE childXref IN (${placeholders})`, xrefs);
    await d.execute(`DELETE FROM event WHERE ownerXref IN (${placeholders})`, xrefs);
    await d.execute(`DELETE FROM bookmark WHERE personXref IN (${placeholders})`, xrefs);
    await d.execute(`DELETE FROM person WHERE xref IN (${placeholders})`, xrefs);
    await d.execute('COMMIT');
  } catch (e) {
    await d.execute('ROLLBACK');
    throw e;
  }
}

// ===== AI Chat queries =====

export async function getAIChatHistory(d: PlatformDatabase, personXref?: string): Promise<AIChatMessage[]> {
  if (personXref) {
    return await d.select<AIChatMessage[]>(`SELECT * FROM ai_chat WHERE personXref = $1 ORDER BY timestamp ASC`, [personXref]);
  }
  return await d.select<AIChatMessage[]>(`SELECT * FROM ai_chat ORDER BY timestamp ASC`);
}

export async function insertAIChatMessage(d: PlatformDatabase, m: Omit<AIChatMessage, 'id'>): Promise<void> {
  await d.execute(
    `INSERT INTO ai_chat (provider, model, role, content, personXref, timestamp) VALUES ($1,$2,$3,$4,$5,$6)`,
    [m.provider, m.model, m.role, m.content, m.personXref, m.timestamp]
  );
}

export async function clearAIChatHistory(d: PlatformDatabase): Promise<void> {
  await d.execute(`DELETE FROM ai_chat`);
}

// ===== Story queries =====

export async function getStories(d: PlatformDatabase): Promise<GeneratedStory[]> {
  return d.select<GeneratedStory[]>('SELECT * FROM stories ORDER BY createdAt DESC');
}

export async function getStoriesForPerson(d: PlatformDatabase, xref: string): Promise<GeneratedStory[]> {
  return d.select<GeneratedStory[]>('SELECT * FROM stories WHERE personXref = $1 ORDER BY createdAt DESC', [xref]);
}

export async function insertStory(d: PlatformDatabase, story: Omit<GeneratedStory, 'id'>): Promise<void> {
  await d.execute(
    `INSERT INTO stories (personXref, familyXref, storyType, title, content, provider, model, tokensUsed, costEstimate, createdAt)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
    [story.personXref, story.familyXref, story.storyType, story.title, story.content, story.provider, story.model, story.tokensUsed, story.costEstimate, story.createdAt]
  );
}

export async function deleteStory(d: PlatformDatabase, id: number): Promise<void> {
  await d.execute('DELETE FROM stories WHERE id = $1', [id]);
}

// ===== Stats =====

export async function getStats(d: PlatformDatabase): Promise<AppStats> {
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

export async function isDbEmpty(d: PlatformDatabase): Promise<boolean> {
  const rows = await d.select<[{ c: number }]>(`SELECT COUNT(*) as c FROM person`);
  return (rows[0]?.c ?? 0) === 0;
}

// ===== Bulk import (called from parser) =====

export async function insertPerson(d: PlatformDatabase, p: Omit<Person, 'id'>): Promise<void> {
  await d.execute(
    `INSERT OR IGNORE INTO person (xref, givenName, surname, suffix, sex, isLiving, birthDate, birthPlace, deathDate, deathPlace, sourceCount, mediaCount, personColor, proofStatus)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)`,
    [p.xref, p.givenName, p.surname, p.suffix, p.sex, p.isLiving ? 1 : 0, p.birthDate, p.birthPlace, p.deathDate, p.deathPlace, p.sourceCount, p.mediaCount, p.personColor || '', p.proofStatus || 'UNKNOWN']
  );
}

export async function insertFamily(d: PlatformDatabase, f: Omit<Family, 'id'>): Promise<void> {
  await d.execute(
    `INSERT OR IGNORE INTO family (xref, partner1Xref, partner2Xref, marriageDate, marriagePlace) VALUES ($1,$2,$3,$4,$5)`,
    [f.xref, f.partner1Xref, f.partner2Xref, f.marriageDate, f.marriagePlace]
  );
}

export async function insertEvent(d: PlatformDatabase, e: Omit<GedcomEvent, 'id'>): Promise<void> {
  await d.execute(
    `INSERT INTO event (ownerXref, ownerType, eventType, dateValue, place, description) VALUES ($1,$2,$3,$4,$5,$6)`,
    [e.ownerXref, e.ownerType, e.eventType, e.dateValue, e.place, e.description]
  );
}

export async function addEventAction(d: PlatformDatabase, e: Omit<GedcomEvent, 'id'>): Promise<number> {
  const result = await d.execute(
    `INSERT INTO event (ownerXref, ownerType, eventType, dateValue, place, description) VALUES ($1,$2,$3,$4,$5,$6)`,
    [e.ownerXref, e.ownerType, e.eventType, e.dateValue, e.place, e.description]
  );
  return result.lastInsertId ?? 0;
}

export async function deleteEventById(d: PlatformDatabase, id: number): Promise<void> {
  await d.execute(`DELETE FROM event WHERE id = $1`, [id]);
}

export async function insertSource(d: PlatformDatabase, s: Omit<Source, 'id'>): Promise<void> {
  await d.execute(
    `INSERT OR IGNORE INTO source (xref, title, author, publisher) VALUES ($1,$2,$3,$4)`,
    [s.xref, s.title, s.author, s.publisher]
  );
}

export async function insertMedia(d: PlatformDatabase, m: Omit<GedcomMedia, 'id'>): Promise<void> {
  await d.execute(
    `INSERT INTO media (xref, ownerXref, filePath, format, title, category) VALUES ($1,$2,$3,$4,$5,$6)`,
    [m.xref, m.ownerXref, m.filePath, m.format, m.title, m.category || 'uncategorized']
  );
}

export async function insertChildLink(d: PlatformDatabase, cl: ChildLink): Promise<void> {
  await d.execute(
    `INSERT OR IGNORE INTO child_link (familyXref, childXref, childOrder) VALUES ($1,$2,$3)`,
    [cl.familyXref, cl.childXref, cl.childOrder]
  );
}

export async function insertPlace(d: PlatformDatabase, p: Omit<Place, 'id'>): Promise<void> {
  await d.execute(
    `INSERT INTO place (name, normalized, latitude, longitude, eventCount) VALUES ($1,$2,$3,$4,$5)`,
    [p.name, p.normalized, p.latitude, p.longitude, p.eventCount]
  );
}

// ===== Issue Fix Engine =====

export async function applyIssueFix(d: PlatformDatabase, issue: TreeIssue): Promise<{ success: boolean; error?: string }> {
  try {
    switch (issue.title) {
      case 'Death before birth': {
        const person = await getPerson(d, issue.personXref);
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

export async function dismissIssue(d: PlatformDatabase, issueKey: string): Promise<void> {
  await d.execute(
    `INSERT OR REPLACE INTO dismissed_issue (issueKey, dismissedAt) VALUES ($1, $2)`,
    [issueKey, new Date().toISOString()]
  );
}

export async function getDismissedIssueKeys(d: PlatformDatabase): Promise<Set<string>> {
  const rows = await d.select<{ issueKey: string }[]>(`SELECT issueKey FROM dismissed_issue`);
  return new Set(rows.map(r => r.issueKey));
}

export async function clearDismissedIssues(d: PlatformDatabase): Promise<void> {
  await d.execute(`DELETE FROM dismissed_issue`);
}

// ===== Proposal System =====

export async function insertProposal(d: PlatformDatabase, p: Omit<Proposal, 'id'>): Promise<number> {
  const result = await d.execute(
    `INSERT INTO proposal (agentRunId, entityType, entityId, fieldName, oldValue, newValue, confidence, reasoning, evidenceSource, status, createdAt)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)`,
    [p.agentRunId, p.entityType, p.entityId, p.fieldName, p.oldValue, p.newValue, p.confidence, p.reasoning, p.evidenceSource, p.status || 'pending', p.createdAt || new Date().toISOString()]
  );
  return result.lastInsertId ?? 0;
}

export async function getProposals(d: PlatformDatabase, filters?: { status?: string; entityId?: string; agentRunId?: string }): Promise<Proposal[]> {
  let query = 'SELECT * FROM proposal WHERE 1=1';
  const params: any[] = [];
  let i = 1;
  if (filters?.status) { query += ` AND status = $${i++}`; params.push(filters.status); }
  if (filters?.entityId) { query += ` AND entityId = $${i++}`; params.push(filters.entityId); }
  if (filters?.agentRunId) { query += ` AND agentRunId = $${i++}`; params.push(filters.agentRunId); }
  query += ' ORDER BY createdAt DESC';
  return await d.select<Proposal[]>(query, params);
}

export async function getPendingProposalCount(d: PlatformDatabase): Promise<number> {
  const r = await d.select<[{ c: number }]>(`SELECT COUNT(*) as c FROM proposal WHERE status = 'pending'`);
  return r[0]?.c ?? 0;
}

export async function approveProposal(d: PlatformDatabase, proposalId: number): Promise<{ success: boolean; error?: string }> {
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

export async function rejectProposal(d: PlatformDatabase, proposalId: number): Promise<void> {
  await d.execute(`UPDATE proposal SET status = 'rejected', resolvedAt = datetime('now') WHERE id = $1`, [proposalId]);
}

export async function undoChange(d: PlatformDatabase, changeLogId: number): Promise<{ success: boolean; error?: string }> {
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

export async function validateProposal(d: PlatformDatabase, p: Omit<Proposal, 'id'>): Promise<{ valid: boolean; violations: string[] }> {
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
          const person = await getPerson(d, p.entityId);
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
          const person = await getPerson(d, p.entityId);
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

export async function insertAgentRun(d: PlatformDatabase, run: Omit<AgentRun, 'id'>): Promise<number> {
  const result = await d.execute(
    `INSERT INTO agent_run (runId, provider, model, personXref, proposalCount, status, startedAt)
     VALUES ($1,$2,$3,$4,$5,$6,$7)`,
    [run.runId, run.provider, run.model, run.personXref, 0, 'running', run.startedAt || new Date().toISOString()]
  );
  return result.lastInsertId ?? 0;
}

export async function updateAgentRun(d: PlatformDatabase, runId: string, data: { status?: string; proposalCount?: number; completedAt?: string }): Promise<void> {
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

export async function getAgentRuns(d: PlatformDatabase, personXref?: string): Promise<AgentRun[]> {
  if (personXref) {
    return await d.select<AgentRun[]>(`SELECT * FROM agent_run WHERE personXref = $1 ORDER BY startedAt DESC`, [personXref]);
  }
  return await d.select<AgentRun[]>(`SELECT * FROM agent_run ORDER BY startedAt DESC LIMIT 50`);
}

export async function getQualityRules(d: PlatformDatabase): Promise<QualityRule[]> {
  return await d.select<QualityRule[]>(`SELECT * FROM quality_rule ORDER BY name`);
}

export async function toggleQualityRule(d: PlatformDatabase, id: number, isActive: number): Promise<void> {
  await d.execute(`UPDATE quality_rule SET isActive = $1 WHERE id = $2`, [isActive, id]);
}

export async function getChangeLog(d: PlatformDatabase, entityId?: string): Promise<ChangeLogEntry[]> {
  if (entityId) {
    return await d.select<ChangeLogEntry[]>(`SELECT * FROM change_log WHERE entityId = $1 ORDER BY appliedAt DESC`, [entityId]);
  }
  return await d.select<ChangeLogEntry[]>(`SELECT * FROM change_log ORDER BY appliedAt DESC LIMIT 100`);
}

export async function clearAll(d: PlatformDatabase): Promise<void> {
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

export async function getSetting(d: PlatformDatabase, key: string): Promise<string | null> {
  const rows = await d.select<{ value: string }[]>('SELECT value FROM settings WHERE key = $1', [key]);
  return rows.length > 0 ? rows[0].value : null;
}

export async function setSetting(d: PlatformDatabase, key: string, value: string): Promise<void> {
  await d.execute('INSERT INTO settings (key, value) VALUES ($1, $2) ON CONFLICT(key) DO UPDATE SET value = $2', [key, value]);
}

export async function getSettingsLike(d: PlatformDatabase, prefix: string): Promise<{ key: string; value: string }[]> {
  return d.select<{ key: string; value: string }[]>('SELECT key, value FROM settings WHERE key LIKE $1', [prefix + '%']);
}

export async function getEventsForPlace(d: PlatformDatabase, placeName: string): Promise<(GedcomEvent & { personName?: string })[]> {
  const events = await d.select<GedcomEvent[]>(
    'SELECT * FROM event WHERE place = $1 ORDER BY dateValue', [placeName]
  );
  const results: (GedcomEvent & { personName?: string })[] = [];
  for (const e of events) {
    const person = await getPerson(d, e.ownerXref);
    results.push({ ...e, personName: person ? `${person.givenName} ${person.surname}`.trim() : e.ownerXref });
  }
  return results;
}

export async function getCitationsForSource(d: PlatformDatabase, sourceXref: string): Promise<{ personXref: string; personName: string; page: string; quality: string }[]> {
  const citations = await d.select<{ personXref: string; page: string; quality: string }[]>(
    'SELECT personXref, page, quality FROM citation WHERE sourceXref = $1', [sourceXref]
  );
  const results: { personXref: string; personName: string; page: string; quality: string }[] = [];
  for (const c of citations) {
    const person = c.personXref ? await getPerson(d, c.personXref) : null;
    results.push({
      ...c,
      personName: person ? `${person.givenName} ${person.surname}`.trim() : c.personXref || '—',
    });
  }
  return results;
}

export async function findSameNameGenerations(d: PlatformDatabase): Promise<{ elder: Person; younger: Person; yearGap: number }[]> {
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

export async function findCollateralRelatives(d: PlatformDatabase, rootXref: string): Promise<{ person: Person; relationship: string }[]> {
  // Walk up the direct line from root, then find siblings at each generation
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

export async function rebuildFTS(d: PlatformDatabase): Promise<void> {
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

export async function exportDbAsJson(d: PlatformDatabase): Promise<DbBackup> {
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

export async function importDbFromJson(d: PlatformDatabase, backup: DbBackup): Promise<void> {
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

export async function getBackupJson(d: PlatformDatabase): Promise<string> {
  const backup = await exportDbAsJson(d);
  return JSON.stringify({
    version: 1,
    appVersion: '0.1.0',
    exportedAt: backup.exportedAt,
    tables: backup.tables,
  });
}

export async function exportDatabase(d: PlatformDatabase): Promise<Uint8Array> {
  const json = await getBackupJson(d);
  return new TextEncoder().encode(json);
}

export async function importDatabase(d: PlatformDatabase, data: Uint8Array): Promise<void> {
  const json = new TextDecoder().decode(data);
  const parsed = JSON.parse(json) as DbBackup;
  await importDbFromJson(d, parsed);
}

// ===== Evidence =====

export async function getEvidence(d: PlatformDatabase, personXref: string, factType?: string): Promise<EvidenceRecord[]> {
  if (factType) {
    return d.select<EvidenceRecord[]>(
      `SELECT * FROM evidence WHERE personXref = $1 AND factType = $2 ORDER BY createdAt DESC`,
      [personXref, factType]
    );
  }
  return d.select<EvidenceRecord[]>(`SELECT * FROM evidence WHERE personXref = $1 ORDER BY createdAt DESC`, [personXref]);
}

export async function addEvidence(d: PlatformDatabase, e: Omit<EvidenceRecord, 'id' | 'createdAt'>): Promise<number> {
  const result = await d.execute(
    `INSERT INTO evidence (personXref, factType, factValue, sourceXref, informationType, evidenceType, quality, analysisNotes)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
    [e.personXref, e.factType, e.factValue, e.sourceXref, e.informationType, e.evidenceType, e.quality, e.analysisNotes]
  );
  return result.lastInsertId ?? 0;
}

export async function updateEvidence(d: PlatformDatabase, id: number, updates: Partial<EvidenceRecord>): Promise<void> {
  const sets: string[] = [];
  const vals: any[] = [];
  let i = 1;
  for (const [k, v] of Object.entries(updates)) {
    if (k === 'id' || k === 'createdAt') continue;
    sets.push(`${k} = $${i++}`);
    vals.push(v);
  }
  if (sets.length === 0) return;
  vals.push(id);
  await d.execute(`UPDATE evidence SET ${sets.join(', ')} WHERE id = $${i}`, vals);
}

export async function deleteEvidence(d: PlatformDatabase, id: number): Promise<void> {
  await d.execute(`DELETE FROM evidence WHERE id = $1`, [id]);
}

export async function getFactSummary(d: PlatformDatabase, personXref: string): Promise<{ factType: string; valueCount: number; evidenceCount: number }[]> {
  return d.select<{ factType: string; valueCount: number; evidenceCount: number }[]>(
    `SELECT factType, COUNT(DISTINCT factValue) as valueCount, COUNT(*) as evidenceCount
     FROM evidence WHERE personXref = $1 GROUP BY factType ORDER BY factType`,
    [personXref]
  );
}

export async function getGpsChecklist(d: PlatformDatabase, personXref: string): Promise<{ item: number; completed: number; notes: string }[]> {
  return d.select<{ item: number; completed: number; notes: string }[]>(
    `SELECT item, completed, notes FROM gps_checklist WHERE personXref = $1 ORDER BY item`,
    [personXref]
  );
}

export async function setGpsChecklist(d: PlatformDatabase, personXref: string, item: number, completed: number, notes: string): Promise<void> {
  await d.execute(
    `INSERT INTO gps_checklist (personXref, item, completed, notes)
     VALUES ($1,$2,$3,$4)
     ON CONFLICT(personXref, item) DO UPDATE SET completed = $3, notes = $4`,
    [personXref, item, completed, notes]
  );
}
