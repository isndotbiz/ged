#!/usr/bin/env bun
/**
 * gedcom-import.ts — Standalone GEDCOM importer using bun:sqlite
 * Imports a GEDCOM 5.5.1 file directly into the GedFix SQLite database.
 * Backs up the existing DB before clearing.
 *
 * Usage: bun gedcom-import.ts <gedcom-file>
 */

import { Database } from "bun:sqlite";
import { readFileSync, copyFileSync, existsSync, mkdirSync } from "fs";

const DB_PATH = `${process.env.HOME}/Library/Application Support/com.gedfix.app/gedfix.db`;
const GED_PATH = process.argv[2];

if (!GED_PATH) {
  console.error("Usage: bun gedcom-import.ts <gedcom-file>");
  process.exit(1);
}

if (!existsSync(GED_PATH)) {
  console.error(`GEDCOM file not found: ${GED_PATH}`);
  process.exit(1);
}

// ── Backup ──────────────────────────────────────────────────────────────────
const backupDir = `${process.env.HOME}/Library/Application Support/com.gedfix.app/backups`;
mkdirSync(backupDir, { recursive: true });
const ts = new Date().toISOString().slice(0, 10);
const backupPath = `${backupDir}/gedfix-pre-import-${ts}.db`;
if (existsSync(DB_PATH)) {
  copyFileSync(DB_PATH, backupPath);
  console.log(`✅ Backed up DB to: ${backupPath}`);
}

// ── Open DB ─────────────────────────────────────────────────────────────────
const db = new Database(DB_PATH);
db.exec("PRAGMA journal_mode = WAL");
db.exec("PRAGMA synchronous = NORMAL");
db.exec("PRAGMA foreign_keys = OFF"); // off during bulk insert

// ── Schema ensure ───────────────────────────────────────────────────────────
db.exec(`
  CREATE TABLE IF NOT EXISTS person (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xref TEXT UNIQUE NOT NULL,
    givenName TEXT DEFAULT '', surname TEXT DEFAULT '', suffix TEXT DEFAULT '',
    sex TEXT DEFAULT 'U', isLiving INTEGER DEFAULT 0,
    birthDate TEXT DEFAULT '', birthPlace TEXT DEFAULT '',
    deathDate TEXT DEFAULT '', deathPlace TEXT DEFAULT '',
    sourceCount INTEGER DEFAULT 0, mediaCount INTEGER DEFAULT 0,
    personColor TEXT DEFAULT '', proofStatus TEXT DEFAULT 'UNKNOWN',
    validationStatus TEXT DEFAULT 'unvalidated'
  );
  CREATE TABLE IF NOT EXISTS family (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xref TEXT UNIQUE NOT NULL,
    partner1Xref TEXT DEFAULT '', partner2Xref TEXT DEFAULT '',
    marriageDate TEXT DEFAULT '', marriagePlace TEXT DEFAULT ''
  );
  CREATE TABLE IF NOT EXISTS event (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ownerXref TEXT NOT NULL, ownerType TEXT NOT NULL,
    eventType TEXT NOT NULL, dateValue TEXT DEFAULT '',
    place TEXT DEFAULT '', description TEXT DEFAULT ''
  );
  CREATE TABLE IF NOT EXISTS source (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xref TEXT UNIQUE NOT NULL, title TEXT DEFAULT '',
    author TEXT DEFAULT '', publisher TEXT DEFAULT '',
    sourceType TEXT DEFAULT 'unknown'
  );
  CREATE TABLE IF NOT EXISTS citation (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sourceXref TEXT DEFAULT '', personXref TEXT DEFAULT '',
    eventId TEXT DEFAULT '', page TEXT DEFAULT '',
    quality TEXT DEFAULT 'UNKNOWN', text TEXT DEFAULT '', note TEXT DEFAULT ''
  );
  CREATE TABLE IF NOT EXISTS media (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    xref TEXT DEFAULT '', ownerXref TEXT DEFAULT '',
    filePath TEXT DEFAULT '', format TEXT DEFAULT '',
    title TEXT DEFAULT '', category TEXT NOT NULL DEFAULT 'uncategorized'
  );
  CREATE TABLE IF NOT EXISTS media_person_link (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    mediaId INTEGER NOT NULL, personXref TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'tagged', isPrimary INTEGER NOT NULL DEFAULT 0,
    sortOrder INTEGER NOT NULL DEFAULT 0,
    UNIQUE(mediaId, personXref)
  );
  CREATE TABLE IF NOT EXISTS child_link (
    familyXref TEXT NOT NULL, childXref TEXT NOT NULL,
    childOrder INTEGER DEFAULT 0, PRIMARY KEY (familyXref, childXref)
  );
  CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT DEFAULT '');
  CREATE INDEX IF NOT EXISTS idx_citation_person ON citation(personXref);
  CREATE INDEX IF NOT EXISTS idx_citation_source ON citation(sourceXref);
  CREATE INDEX IF NOT EXISTS idx_person_surname ON person(surname);
  CREATE INDEX IF NOT EXISTS idx_event_owner ON event(ownerXref);
`);

// ── Clear existing genealogy data (keep settings, notes, tasks, etc.) ───────
console.log("🗑  Clearing existing genealogy data...");
db.exec(`
  DELETE FROM citation;
  DELETE FROM child_link;
  DELETE FROM media_person_link;
  DELETE FROM event;
  DELETE FROM media;
  DELETE FROM family;
  DELETE FROM person;
  DELETE FROM source;
`);

// ── Parse GEDCOM ─────────────────────────────────────────────────────────────
console.log(`📖 Reading ${GED_PATH}...`);
const text = readFileSync(GED_PATH, "utf-8");
const lines = text.split(/\r?\n/);

interface GedLine {
  level: number;
  xref: string;
  tag: string;
  value: string;
}

function parseLine(raw: string): GedLine | null {
  const m = raw.match(/^(\d+)\s+(@[^@]+@)?\s*(\w+)\s*(.*)?$/);
  if (!m) return null;
  return {
    level: parseInt(m[1]),
    xref: (m[2] || "").replace(/@/g, ""),
    tag: m[3],
    value: (m[4] || "").trim(),
  };
}

function parseName(raw: string): { given: string; surname: string; suffix: string } {
  const m = raw.match(/^(.*?)\s*\/([^/]*)\/(.*)?$/);
  if (m) {
    return {
      given: m[1].trim(),
      surname: m[2].trim(),
      suffix: (m[3] || "").trim(),
    };
  }
  return { given: raw.trim(), surname: "", suffix: "" };
}

function isLivingPerson(given: string, birt: string, deat: string): boolean {
  if (deat) return false;
  if (!birt) return true;
  const m = birt.match(/(\d{4})/);
  if (!m) return true;
  return parseInt(m[1]) > new Date().getFullYear() - 100;
}

function qualityLabel(q: string): string {
  const map: Record<string, string> = {
    "0": "QUESTIONABLE", "1": "SECONDARY", "2": "PRIMARY", "3": "PRIMARY",
  };
  return map[q] || "UNKNOWN";
}

// Group lines into top-level records
interface GedRecord {
  xref: string;
  tag: string;
  lines: GedLine[];
}

const records: GedRecord[] = [];
let current: GedRecord | null = null;

for (const raw of lines) {
  const parsed = parseLine(raw);
  if (!parsed) continue;
  if (parsed.level === 0) {
    if (current) records.push(current);
    current = { xref: parsed.xref, tag: parsed.tag, lines: [] };
    // For records without xref (HEAD, TRLR), tag is the xref field
    if (!parsed.xref && parsed.tag) {
      current.tag = parsed.tag;
    }
  }
  if (current) current.lines.push(parsed);
}
if (current) records.push(current);

console.log(`📊 Found ${records.filter(r => r.tag === "INDI").length} persons, ${records.filter(r => r.tag === "FAM").length} families, ${records.filter(r => r.tag === "SOUR").length} sources`);

// ── Prepared statements ──────────────────────────────────────────────────────
const insertPerson = db.prepare(`
  INSERT OR IGNORE INTO person (xref, givenName, surname, suffix, sex, isLiving, birthDate, birthPlace, deathDate, deathPlace, validationStatus)
  VALUES ($xref, $given, $surname, $suffix, $sex, $living, $birt, $birthPlace, $deat, $deathPlace, 'unvalidated')
`);

const insertFamily = db.prepare(`
  INSERT OR IGNORE INTO family (xref, partner1Xref, partner2Xref, marriageDate, marriagePlace)
  VALUES ($xref, $p1, $p2, $marr, $marrPlace)
`);

const insertChildLink = db.prepare(`
  INSERT OR IGNORE INTO child_link (familyXref, childXref, childOrder) VALUES ($fam, $child, $order)
`);

const insertEvent = db.prepare(`
  INSERT INTO event (ownerXref, ownerType, eventType, dateValue, place, description)
  VALUES ($owner, $ownerType, $type, $date, $place, $desc)
`);

const insertSource = db.prepare(`
  INSERT OR IGNORE INTO source (xref, title, author, publisher)
  VALUES ($xref, $title, $author, $pub)
`);

const insertCitation = db.prepare(`
  INSERT INTO citation (sourceXref, personXref, page, quality)
  VALUES ($src, $person, $page, $quality)
`);

const insertMedia = db.prepare(`
  INSERT OR IGNORE INTO media (xref, ownerXref, filePath, format, title)
  VALUES ($xref, $owner, $path, $format, $title)
`);

// ── Import transaction ───────────────────────────────────────────────────────
let personCount = 0, famCount = 0, srcCount = 0, citCount = 0, evtCount = 0;

db.transaction(() => {
  for (const rec of records) {
    // ── INDI ──
    if (rec.tag === "INDI" && rec.xref) {
      let given = "", surname = "", suffix = "", sex = "U";
      let birt = "", birthPlace = "", deat = "", deathPlace = "";
      const citations: { src: string; page: string; quality: string }[] = [];

      let i = 0;
      while (i < rec.lines.length) {
        const l = rec.lines[i];

        if (l.level === 1 && l.tag === "NAME") {
          const n = parseName(l.value);
          given = n.given;
          surname = n.surname;
          suffix = n.suffix;
        }
        if (l.level === 1 && l.tag === "SEX") sex = l.value || "U";

        if (l.level === 1 && (l.tag === "BIRT" || l.tag === "CHR")) {
          // collect DATE and PLAC from sub-lines
          let j = i + 1;
          while (j < rec.lines.length && rec.lines[j].level > 1) {
            if (rec.lines[j].level === 2 && rec.lines[j].tag === "DATE" && !birt)
              birt = rec.lines[j].value;
            if (rec.lines[j].level === 2 && rec.lines[j].tag === "PLAC" && !birthPlace)
              birthPlace = rec.lines[j].value;
            // source on birth event
            if (rec.lines[j].level === 2 && rec.lines[j].tag === "SOUR") {
              const src = rec.lines[j].value.replace(/@/g, "");
              let page = "", quality = "UNKNOWN";
              let k = j + 1;
              while (k < rec.lines.length && rec.lines[k].level > 2) {
                if (rec.lines[k].level === 3 && rec.lines[k].tag === "PAGE") page = rec.lines[k].value;
                if (rec.lines[k].level === 3 && rec.lines[k].tag === "QUAY") quality = qualityLabel(rec.lines[k].value);
                k++;
              }
              if (src) citations.push({ src, page, quality });
            }
            j++;
          }
        }

        if (l.level === 1 && (l.tag === "DEAT" || l.tag === "BURI")) {
          let j = i + 1;
          while (j < rec.lines.length && rec.lines[j].level > 1) {
            if (rec.lines[j].level === 2 && rec.lines[j].tag === "DATE" && !deat)
              deat = rec.lines[j].value;
            if (rec.lines[j].level === 2 && rec.lines[j].tag === "PLAC" && !deathPlace)
              deathPlace = rec.lines[j].value;
            if (rec.lines[j].level === 2 && rec.lines[j].tag === "SOUR") {
              const src = rec.lines[j].value.replace(/@/g, "");
              let page = "", quality = "UNKNOWN";
              let k = j + 1;
              while (k < rec.lines.length && rec.lines[k].level > 2) {
                if (rec.lines[k].level === 3 && rec.lines[k].tag === "PAGE") page = rec.lines[k].value;
                if (rec.lines[k].level === 3 && rec.lines[k].tag === "QUAY") quality = qualityLabel(rec.lines[k].value);
                k++;
              }
              if (src) citations.push({ src, page, quality });
            }
            j++;
          }
        }

        // Record-level SOUR on INDI
        if (l.level === 1 && l.tag === "SOUR") {
          const src = l.value.replace(/@/g, "");
          let page = "", quality = "UNKNOWN";
          let j = i + 1;
          while (j < rec.lines.length && rec.lines[j].level > 1) {
            if (rec.lines[j].level === 2 && rec.lines[j].tag === "PAGE") page = rec.lines[j].value;
            if (rec.lines[j].level === 2 && rec.lines[j].tag === "QUAY") quality = qualityLabel(rec.lines[j].value);
            j++;
          }
          if (src) citations.push({ src, page, quality });
        }

        // Events (OCCU, RESI, EMIG, IMMI, CENS, NATU, GRAD, RETI, EVEN)
        const eventTags = ["OCCU","RESI","EMIG","IMMI","CENS","NATU","GRAD","RETI","EVEN","MARR","DIV"];
        if (l.level === 1 && eventTags.includes(l.tag)) {
          let evtDate = "", evtPlace = "", evtDesc = l.value || "";
          let j = i + 1;
          while (j < rec.lines.length && rec.lines[j].level > 1) {
            if (rec.lines[j].level === 2 && rec.lines[j].tag === "DATE") evtDate = rec.lines[j].value;
            if (rec.lines[j].level === 2 && rec.lines[j].tag === "PLAC") evtPlace = rec.lines[j].value;
            if (rec.lines[j].level === 2 && rec.lines[j].tag === "SOUR") {
              const src = rec.lines[j].value.replace(/@/g, "");
              let page = "", quality = "UNKNOWN";
              let k = j + 1;
              while (k < rec.lines.length && rec.lines[k].level > 2) {
                if (rec.lines[k].level === 3 && rec.lines[k].tag === "PAGE") page = rec.lines[k].value;
                if (rec.lines[k].level === 3 && rec.lines[k].tag === "QUAY") quality = qualityLabel(rec.lines[k].value);
                k++;
              }
              if (src) citations.push({ src, page, quality });
            }
            j++;
          }
          if (evtDate || evtPlace) {
            insertEvent.run(rec.xref, "INDI", l.tag, evtDate, evtPlace, evtDesc);
            evtCount++;
          }
        }

        i++;
      }

      insertPerson.run(
        rec.xref, given, surname, suffix, sex,
        isLivingPerson(given, birt, deat) ? 1 : 0,
        birt, birthPlace, deat, deathPlace,
      );

      // Birth event
      if (birt || birthPlace) {
        insertEvent.run(rec.xref, "INDI", "BIRT", birt, birthPlace, "");
        evtCount++;
      }
      // Death event
      if (deat || deathPlace) {
        insertEvent.run(rec.xref, "INDI", "DEAT", deat, deathPlace, "");
        evtCount++;
      }

      // Citations
      for (const c of citations) {
        if (c.src) {
          insertCitation.run(c.src, rec.xref, c.page, c.quality);
          citCount++;
        }
      }

      personCount++;
    }

    // ── FAM ──
    if (rec.tag === "FAM" && rec.xref) {
      let p1 = "", p2 = "", marrDate = "", marrPlace = "";
      const children: string[] = [];

      for (const l of rec.lines) {
        if (l.level === 1 && l.tag === "HUSB") p1 = l.value.replace(/@/g, "");
        if (l.level === 1 && l.tag === "WIFE") p2 = l.value.replace(/@/g, "");
        if (l.level === 1 && l.tag === "CHIL") children.push(l.value.replace(/@/g, ""));
        if (l.level === 2 && l.tag === "DATE" && rec.lines[rec.lines.indexOf(l) - 1]?.tag === "MARR") marrDate = l.value;
        if (l.level === 2 && l.tag === "PLAC" && rec.lines[rec.lines.indexOf(l) - 1]?.tag === "MARR") marrPlace = l.value;
      }

      // Better marriage date/place extraction
      let inMarr = false;
      for (const l of rec.lines) {
        if (l.level === 1 && l.tag === "MARR") { inMarr = true; continue; }
        if (l.level === 1 && l.tag !== "MARR") inMarr = false;
        if (inMarr && l.level === 2 && l.tag === "DATE") marrDate = l.value;
        if (inMarr && l.level === 2 && l.tag === "PLAC") marrPlace = l.value;
      }

      insertFamily.run(rec.xref, p1, p2, marrDate, marrPlace);
      for (let ci = 0; ci < children.length; ci++) {
        insertChildLink.run(rec.xref, children[ci], ci);
      }
      famCount++;
    }

    // ── SOUR ──
    if (rec.tag === "SOUR" && rec.xref) {
      let title = "", author = "", pub = "";
      for (const l of rec.lines) {
        if (l.level === 1 && l.tag === "TITL") title = l.value;
        if (l.level === 1 && l.tag === "AUTH") author = l.value;
        if (l.level === 1 && l.tag === "PUBL") pub = l.value;
      }
      insertSource.run(rec.xref, title, author, pub);
      srcCount++;
    }
  }
})();

console.log(`✅ Imported: ${personCount} persons, ${famCount} families, ${srcCount} sources, ${citCount} citations, ${evtCount} events`);

// ── Classify sources ─────────────────────────────────────────────────────────
console.log("🏷  Classifying sources...");
db.exec(`
  UPDATE source SET sourceType = 'online_tree' WHERE sourceType = 'unknown'
    AND (LOWER(title) LIKE '%ancestry%' OR LOWER(title) LIKE '%familysearch%'
      OR LOWER(title) LIKE '%myheritage%' OR LOWER(title) LIKE '%geni%'
      OR LOWER(title) LIKE '%wikitree%' OR LOWER(publisher) LIKE '%ancestry%'
      OR LOWER(publisher) LIKE '%familysearch%');
  UPDATE source SET sourceType = 'vital_record' WHERE sourceType = 'unknown'
    AND (LOWER(title) LIKE '%birth certificate%' OR LOWER(title) LIKE '%death certificate%'
      OR LOWER(title) LIKE '%marriage certificate%' OR LOWER(title) LIKE '%vital record%');
  UPDATE source SET sourceType = 'census' WHERE sourceType = 'unknown'
    AND LOWER(title) LIKE '%census%';
  UPDATE source SET sourceType = 'newspaper' WHERE sourceType = 'unknown'
    AND (LOWER(title) LIKE '%newspaper%' OR LOWER(publisher) LIKE '%newspaper%'
      OR LOWER(title) LIKE '%chronicle%' OR LOWER(title) LIKE '%gazette%'
      OR LOWER(title) LIKE '%herald%');
  UPDATE source SET sourceType = 'church_record' WHERE sourceType = 'unknown'
    AND (LOWER(title) LIKE '%church%' OR LOWER(title) LIKE '%parish%'
      OR LOWER(title) LIKE '%baptis%' OR LOWER(title) LIKE '%christening%');
  UPDATE source SET sourceType = 'military' WHERE sourceType = 'unknown'
    AND (LOWER(title) LIKE '%military%' OR LOWER(title) LIKE '%draft%'
      OR LOWER(title) LIKE '%enlistment%' OR LOWER(title) LIKE '%pension%'
      OR LOWER(title) LIKE '%service record%');
  UPDATE source SET sourceType = 'immigration' WHERE sourceType = 'unknown'
    AND (LOWER(title) LIKE '%immigration%' OR LOWER(title) LIKE '%passenger%'
      OR LOWER(title) LIKE '%naturalization%' OR LOWER(title) LIKE '%ship%manifest%');
`);

const classified = db.query("SELECT COUNT(*) as c FROM source WHERE sourceType != 'unknown'").get() as { c: number };
console.log(`✅ Classified ${classified.c} sources`);

// ── Compute validation status ─────────────────────────────────────────────────
console.log("🔍 Computing validation status...");
db.exec(`
  UPDATE person SET validationStatus = 'unvalidated';

  UPDATE person SET validationStatus = 'validated'
  WHERE xref IN (
    SELECT DISTINCT c.personXref FROM citation c
    JOIN source s ON s.xref = c.sourceXref
    WHERE s.sourceType NOT IN ('online_tree', 'unknown')
  );

  UPDATE person SET validationStatus = 'tree_only'
  WHERE validationStatus = 'unvalidated'
  AND xref IN (
    SELECT DISTINCT c.personXref FROM citation c
    JOIN source s ON s.xref = c.sourceXref
    WHERE s.sourceType = 'online_tree'
  );
`);

const stats = db.query(`
  SELECT
    SUM(CASE WHEN validationStatus='validated' THEN 1 ELSE 0 END) as validated,
    SUM(CASE WHEN validationStatus='tree_only' THEN 1 ELSE 0 END) as tree_only,
    SUM(CASE WHEN validationStatus='unvalidated' THEN 1 ELSE 0 END) as unvalidated
  FROM person
`).get() as { validated: number; tree_only: number; unvalidated: number };

console.log(`✅ Validation: ${stats.validated} validated, ${stats.tree_only} tree_only, ${stats.unvalidated} unvalidated`);

// ── Store last import date ────────────────────────────────────────────────────
db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('last_import_date', ?)").run(new Date().toISOString());

db.exec("PRAGMA optimize");
db.close();

console.log(`\n🎉 Import complete! DB ready at: ${DB_PATH}`);
console.log(`   Backup at: ${backupPath}`);
