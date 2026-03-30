import { getDb } from './db';
import type { Person, Family, GedcomEvent, Source, GedcomMedia } from './types';

export async function exportGedcom(format: '5.5.1' | '7.0' = '5.5.1'): Promise<string> {
  const db = await getDb();
  const lines: string[] = [];

  // Header
  lines.push('0 HEAD');
  lines.push('1 SOUR GedFix');
  lines.push('2 VERS 1.0');
  lines.push('2 NAME GedFix Genealogical Research');
  lines.push('1 DEST GEDCOM');
  lines.push('1 DATE ' + formatGedDate(new Date()));
  lines.push('1 SUBM @SUB1@');
  lines.push('1 GEDC');
  lines.push(`2 VERS ${format}`);
  lines.push('2 FORM LINEAGE-LINKED');
  lines.push('1 CHAR UTF-8');
  lines.push('0 @SUB1@ SUBM');
  lines.push('1 NAME GedFix Export');

  // Batch load ALL related data upfront (eliminates N+1 queries)
  const [persons, allEvents, allMediaLinks, allChildLinks, families, sources, media] = await Promise.all([
    db.select<Person[]>('SELECT * FROM person ORDER BY xref'),
    db.select<GedcomEvent[]>('SELECT * FROM event ORDER BY ownerXref, eventType'),
    db.select<{personXref: string; xref: string}[]>(
      `SELECT mpl.personXref, m.xref FROM media m JOIN media_person_link mpl ON mpl.mediaId = m.id WHERE m.xref != ''`
    ),
    db.select<{childXref: string; familyXref: string; childOrder: number}[]>(
      'SELECT * FROM child_link ORDER BY familyXref, childOrder'
    ),
    db.select<Family[]>('SELECT * FROM family ORDER BY xref'),
    db.select<Source[]>('SELECT * FROM source ORDER BY xref'),
    db.select<GedcomMedia[]>("SELECT * FROM media WHERE xref != '' AND filePath != '' ORDER BY xref"),
  ]);

  // Build lookup maps
  const eventsByOwner = new Map<string, GedcomEvent[]>();
  for (const e of allEvents) {
    if (!eventsByOwner.has(e.ownerXref)) eventsByOwner.set(e.ownerXref, []);
    eventsByOwner.get(e.ownerXref)!.push(e);
  }

  const mediaByPerson = new Map<string, string[]>();
  for (const ml of allMediaLinks) {
    if (!mediaByPerson.has(ml.personXref)) mediaByPerson.set(ml.personXref, []);
    mediaByPerson.get(ml.personXref)!.push(ml.xref);
  }

  const childLinksByChild = new Map<string, string[]>();
  const childLinksByFamily = new Map<string, string[]>();
  for (const cl of allChildLinks) {
    if (!childLinksByChild.has(cl.childXref)) childLinksByChild.set(cl.childXref, []);
    childLinksByChild.get(cl.childXref)!.push(cl.familyXref);
    if (!childLinksByFamily.has(cl.familyXref)) childLinksByFamily.set(cl.familyXref, []);
    childLinksByFamily.get(cl.familyXref)!.push(cl.childXref);
  }

  const spouseFamsByPerson = new Map<string, string[]>();
  for (const f of families) {
    if (f.partner1Xref) {
      if (!spouseFamsByPerson.has(f.partner1Xref)) spouseFamsByPerson.set(f.partner1Xref, []);
      spouseFamsByPerson.get(f.partner1Xref)!.push(f.xref);
    }
    if (f.partner2Xref) {
      if (!spouseFamsByPerson.has(f.partner2Xref)) spouseFamsByPerson.set(f.partner2Xref, []);
      spouseFamsByPerson.get(f.partner2Xref)!.push(f.xref);
    }
  }

  // Persons
  for (const p of persons) {
    lines.push(`0 @${p.xref}@ INDI`);
    const nameLine = p.suffix ? `1 NAME ${p.givenName} /${p.surname}/ ${p.suffix}` : `1 NAME ${p.givenName} /${p.surname}/`;
    lines.push(nameLine);
    if (p.givenName) lines.push(`2 GIVN ${p.givenName}`);
    if (p.surname) lines.push(`2 SURN ${p.surname}`);
    if (p.suffix) lines.push(`2 NSFX ${p.suffix}`);
    if (p.sex && p.sex !== 'U') lines.push(`1 SEX ${p.sex}`);

    const events = eventsByOwner.get(p.xref) || [];
    for (const e of events) {
      lines.push(`1 ${e.eventType}`);
      if (e.dateValue) lines.push(`2 DATE ${e.dateValue}`);
      if (e.place) lines.push(`2 PLAC ${e.place}`);
      if (e.description && e.eventType !== 'BIRT' && e.eventType !== 'DEAT') lines.push(`2 NOTE ${e.description}`);
    }
    if (!events.some(e => e.eventType === 'BIRT') && (p.birthDate || p.birthPlace)) {
      lines.push('1 BIRT');
      if (p.birthDate) lines.push(`2 DATE ${p.birthDate}`);
      if (p.birthPlace) lines.push(`2 PLAC ${p.birthPlace}`);
    }
    if (!events.some(e => e.eventType === 'DEAT') && (p.deathDate || p.deathPlace)) {
      lines.push('1 DEAT');
      if (p.deathDate) lines.push(`2 DATE ${p.deathDate}`);
      if (p.deathPlace) lines.push(`2 PLAC ${p.deathPlace}`);
    }
    for (const xref of mediaByPerson.get(p.xref) || []) lines.push(`1 OBJE @${xref}@`);
    for (const fam of childLinksByChild.get(p.xref) || []) lines.push(`1 FAMC @${fam}@`);
    for (const fam of spouseFamsByPerson.get(p.xref) || []) lines.push(`1 FAMS @${fam}@`);
  }

  // Families
  for (const f of families) {
    lines.push(`0 @${f.xref}@ FAM`);
    if (f.partner1Xref) lines.push(`1 HUSB @${f.partner1Xref}@`);
    if (f.partner2Xref) lines.push(`1 WIFE @${f.partner2Xref}@`);
    for (const child of childLinksByFamily.get(f.xref) || []) lines.push(`1 CHIL @${child}@`);
    const famEvents = eventsByOwner.get(f.xref) || [];
    for (const e of famEvents) {
      lines.push(`1 ${e.eventType}`);
      if (e.dateValue) lines.push(`2 DATE ${e.dateValue}`);
      if (e.place) lines.push(`2 PLAC ${e.place}`);
    }
    if (!famEvents.some(e => e.eventType === 'MARR') && (f.marriageDate || f.marriagePlace)) {
      lines.push('1 MARR');
      if (f.marriageDate) lines.push(`2 DATE ${f.marriageDate}`);
      if (f.marriagePlace) lines.push(`2 PLAC ${f.marriagePlace}`);
    }
  }

  // Sources
  for (const s of sources) {
    lines.push(`0 @${s.xref}@ SOUR`);
    if (s.title) lines.push(`1 TITL ${s.title}`);
    if (s.author) lines.push(`1 AUTH ${s.author}`);
    if (s.publisher) lines.push(`1 PUBL ${s.publisher}`);
  }

  // Media objects
  for (const m of media) {
    lines.push(`0 @${m.xref}@ OBJE`);
    lines.push(`1 FILE ${m.filePath}`);
    if (m.format && m.format !== 'PRIMARY') lines.push(`2 FORM ${m.format}`);
    if (m.title) lines.push(`1 TITL ${m.title}`);
  }

  lines.push('0 TRLR');
  return lines.join('\n') + '\n';
}

function formatGedDate(d: Date): string {
  const months = ['JAN','FEB','MAR','APR','MAY','JUN','JUL','AUG','SEP','OCT','NOV','DEC'];
  return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
}
