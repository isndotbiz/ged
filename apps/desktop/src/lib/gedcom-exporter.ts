import { getAllPersons, getFamilies, getEvents, getNotes, getChildren, getDb } from './db';
import type { Family, Person, GedcomEvent, Source, GedcomMedia } from './types';

function normalizeXref(xref: string, fallbackPrefix: 'I' | 'F', idx: number): string {
  const cleaned = (xref || '').replace(/@/g, '').trim();
  return cleaned || `${fallbackPrefix}${idx + 1}`;
}

function toGedName(person: Person): string {
  const given = (person.givenName || '').trim();
  const surname = (person.surname || '').trim();
  const suffix = (person.suffix || '').trim();
  const base = `${given} /${surname}/`.trim();
  return suffix ? `${base} ${suffix}` : base;
}

function tryDateParts(value: string): { day: number; month: number; year: number } | null {
  const raw = value.trim();
  if (!raw) return null;

  const monIdx: Record<string, number> = { JAN: 0, FEB: 1, MAR: 2, APR: 3, MAY: 4, JUN: 5, JUL: 6, AUG: 7, SEP: 8, OCT: 9, NOV: 10, DEC: 11 };
  const upper = raw.toUpperCase();
  const gedMatch = upper.match(/^(\d{1,2})\s+([A-Z]{3})\s+(\d{4})$/);
  if (gedMatch && monIdx[gedMatch[2]] !== undefined) {
    return { day: Number(gedMatch[1]), month: monIdx[gedMatch[2]], year: Number(gedMatch[3]) };
  }

  const isoMatch = raw.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/);
  if (isoMatch) {
    return { day: Number(isoMatch[3]), month: Number(isoMatch[2]) - 1, year: Number(isoMatch[1]) };
  }

  const usMatch = raw.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
  if (usMatch) {
    return { day: Number(usMatch[2]), month: Number(usMatch[1]) - 1, year: Number(usMatch[3]) };
  }

  const parsed = new Date(raw);
  if (!Number.isNaN(parsed.getTime())) {
    return { day: parsed.getDate(), month: parsed.getMonth(), year: parsed.getFullYear() };
  }
  return null;
}

function toGedDate(value: string): string {
  const parts = tryDateParts(value);
  if (!parts) return value.trim();
  const months = ['JAN','FEB','MAR','APR','MAY','JUN','JUL','AUG','SEP','OCT','NOV','DEC'];
  return `${parts.day} ${months[parts.month] ?? 'JAN'} ${parts.year}`;
}

function escapeGedText(value: string): string {
  return value.replace(/@/g, '@@');
}

function pushNote(lines: string[], level: number, note: string): void {
  const content = (note || '').trim();
  if (!content) return;
  const chunks = content.split(/\r?\n/);
  lines.push(`${level} NOTE ${escapeGedText(chunks[0] || '')}`);
  for (let i = 1; i < chunks.length; i++) {
    lines.push(`${level + 1} CONT ${escapeGedText(chunks[i] || '')}`);
  }
}

export async function exportGedcom(format: '5.5.1' | '7.0' = '5.5.1'): Promise<string> {
  const lines: string[] = [];
  lines.push('0 HEAD');
  lines.push('1 SOUR GedFix');
  lines.push('1 GEDC');
  lines.push(`2 VERS ${format}`);
  lines.push('2 FORM LINEAGE-LINKED');
  lines.push('1 CHAR UTF-8');
  lines.push(`1 DATE ${toGedDate(new Date().toISOString().slice(0, 10))}`);

  const persons = await getAllPersons();
  for (let i = 0; i < persons.length; i++) {
    const person = persons[i];
    const xref = normalizeXref(person.xref, 'I', i);
    lines.push(`0 @${xref}@ INDI`);
    lines.push(`1 NAME ${toGedName(person)}`);
    if (person.sex && person.sex !== 'U') lines.push(`1 SEX ${person.sex}`);

    let birthDate = (person.birthDate || '').trim();
    let birthPlace = (person.birthPlace || '').trim();
    let deathDate = (person.deathDate || '').trim();
    let deathPlace = (person.deathPlace || '').trim();
    const personEvents = await getEvents(xref);
    for (const event of personEvents) {
      if (event.eventType === 'BIRT') {
        if (!birthDate) birthDate = (event.dateValue || '').trim();
        if (!birthPlace) birthPlace = (event.place || '').trim();
      } else if (event.eventType === 'DEAT') {
        if (!deathDate) deathDate = (event.dateValue || '').trim();
        if (!deathPlace) deathPlace = (event.place || '').trim();
      }
    }

    if (birthDate || birthPlace) {
      lines.push('1 BIRT');
      if (birthDate) lines.push(`2 DATE ${toGedDate(birthDate)}`);
      if (birthPlace) lines.push(`2 PLAC ${escapeGedText(birthPlace)}`);
    }
    if (deathDate || deathPlace) {
      lines.push('1 DEAT');
      if (deathDate) lines.push(`2 DATE ${toGedDate(deathDate)}`);
      if (deathPlace) lines.push(`2 PLAC ${escapeGedText(deathPlace)}`);
    }

    const notes = await getNotes(xref);
    for (const note of notes) {
      const content = [note.title?.trim(), note.content?.trim()].filter(Boolean).join('\n').trim();
      if (content) pushNote(lines, 1, content);
    }
  }

  const families = await getFamilies();
  for (let i = 0; i < families.length; i++) {
    const family: Family = families[i];
    const famXref = normalizeXref(family.xref, 'F', i);
    lines.push(`0 @${famXref}@ FAM`);
    if (family.partner1Xref) lines.push(`1 HUSB @${normalizeXref(family.partner1Xref, 'I', 0)}@`);
    if (family.partner2Xref) lines.push(`1 WIFE @${normalizeXref(family.partner2Xref, 'I', 0)}@`);
    const children = await getChildren(famXref);
    for (const child of children) {
      lines.push(`1 CHIL @${normalizeXref(child.xref, 'I', 0)}@`);
    }
  }

  lines.push('0 TRLR');
  return `${lines.join('\n')}\n`;
}

export async function exportSubsetGedcom(xrefs: string[], format: '5.5.1' | '7.0' = '5.5.1'): Promise<string> {
  if (xrefs.length === 0) return '';
  const db = await getDb();
  const placeholders = xrefs.map((_, i) => `$${i + 1}`).join(', ');
  const persons = await db.select<Person[]>(`SELECT * FROM person WHERE xref IN (${placeholders}) ORDER BY xref`, xrefs);
  const families = await db.select<Family[]>(
    `SELECT * FROM family WHERE partner1Xref IN (${placeholders}) OR partner2Xref IN (${placeholders}) ORDER BY xref`,
    xrefs
  );
  const events = await db.select<GedcomEvent[]>(`SELECT * FROM event WHERE ownerXref IN (${placeholders})`, xrefs);
  const sources = await db.select<Source[]>(`SELECT * FROM source ORDER BY xref`);
  const media = await db.select<GedcomMedia[]>(`SELECT * FROM media WHERE ownerXref IN (${placeholders})`, xrefs);

  const lines: string[] = [];
  lines.push('0 HEAD');
  lines.push('1 SOUR GedFix');
  lines.push(`1 GEDC\n2 VERS ${format}\n2 FORM LINEAGE-LINKED`);
  lines.push('1 CHAR UTF-8');

  for (const p of persons) {
    lines.push(`0 @${p.xref}@ INDI`);
    lines.push(`1 NAME ${p.givenName} /${p.surname}/`);
    if (p.sex && p.sex !== 'U') lines.push(`1 SEX ${p.sex}`);
  }

  const xrefSet = new Set(xrefs);
  for (const f of families) {
    lines.push(`0 @${f.xref}@ FAM`);
    if (f.partner1Xref) lines.push(`1 HUSB @${f.partner1Xref}@`);
    if (f.partner2Xref) lines.push(`1 WIFE @${f.partner2Xref}@`);
    const children = await db.select<{ childXref: string }[]>(`SELECT childXref FROM child_link WHERE familyXref = $1`, [f.xref]);
    for (const child of children) {
      if (xrefSet.has(child.childXref)) lines.push(`1 CHIL @${child.childXref}@`);
    }
  }

  for (const e of events) {
    lines.push(`0 NOTE ${e.ownerXref}:${e.eventType}:${e.dateValue}`);
  }
  for (const s of sources) {
    if (!s.xref) continue;
    lines.push(`0 @${s.xref}@ SOUR`);
    if (s.title) lines.push(`1 TITL ${s.title}`);
  }
  for (const m of media) {
    if (!m.filePath) continue;
    lines.push(`0 @${m.xref || `M${m.id}`}@ OBJE`);
    lines.push(`1 FILE ${m.filePath}`);
  }
  lines.push('0 TRLR');
  return lines.join('\n') + '\n';
}

function formatGedDate(d: Date): string {
  const months = ['JAN','FEB','MAR','APR','MAY','JUN','JUL','AUG','SEP','OCT','NOV','DEC'];
  return `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}`;
}
