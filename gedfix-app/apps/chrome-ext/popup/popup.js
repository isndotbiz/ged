import initSqlJs from 'sql.js';

const statsEl = document.getElementById('stats');
const recentEl = document.getElementById('recent');
const searchInput = document.getElementById('search');
const openSide = document.getElementById('open-side');
const openFull = document.getElementById('open-full');
const importBtn = document.getElementById('import-gedcom');
const fileInput = document.getElementById('file');

const recentKey = 'gedfix_ext_recent_people';
const dbKey = 'gedfix_ext_popup_db';

let db;

async function initDb() {
  const SQL = await initSqlJs({ locateFile: (f) => `https://sql.js.org/dist/${f}` });
  const bytes = localStorage.getItem(dbKey);
  db = bytes ? new SQL.Database(Uint8Array.from(JSON.parse(bytes))) : new SQL.Database();
  db.run(`
    CREATE TABLE IF NOT EXISTS person (id INTEGER PRIMARY KEY AUTOINCREMENT, xref TEXT UNIQUE, givenName TEXT, surname TEXT);
    CREATE TABLE IF NOT EXISTS family (id INTEGER PRIMARY KEY AUTOINCREMENT, xref TEXT UNIQUE);
  `);
  saveDb();
}

function saveDb() {
  localStorage.setItem(dbKey, JSON.stringify(Array.from(db.export())));
}

function updateStats() {
  const p = db.exec('SELECT COUNT(*) as c FROM person');
  const f = db.exec('SELECT COUNT(*) as c FROM family');
  const people = p[0]?.values?.[0]?.[0] || 0;
  const families = f[0]?.values?.[0]?.[0] || 0;
  statsEl.innerHTML = `<strong>Tree stats</strong><div class="muted">People: ${people} • Families: ${families}</div>`;
}

function updateRecent() {
  const rows = JSON.parse(localStorage.getItem(recentKey) || '[]').slice(0, 3);
  recentEl.innerHTML = rows.length
    ? `<strong>Recent</strong>${rows.map((r) => `<div class="muted">${r}</div>`).join('')}`
    : 'No recent people.';
}

function parseGedcomToRows(text) {
  const lines = text.split(/\r?\n/);
  const people = [];
  const families = [];
  let current = null;

  for (const line of lines) {
    const personStart = line.match(/^0\s+(@I[^@]+@)\s+INDI$/);
    const familyStart = line.match(/^0\s+(@F[^@]+@)\s+FAM$/);
    const nameLine = line.match(/^1\s+NAME\s+(.+)$/);

    if (personStart) {
      if (current) people.push(current);
      current = { xref: personStart[1], givenName: '', surname: '' };
      continue;
    }
    if (familyStart) {
      families.push({ xref: familyStart[1] });
      continue;
    }
    if (nameLine && current) {
      const raw = nameLine[1].replace(/\//g, ' ').trim();
      const parts = raw.split(/\s+/);
      current.givenName = parts.slice(0, -1).join(' ') || parts[0] || '';
      current.surname = parts.slice(-1)[0] || '';
    }
  }

  if (current) people.push(current);
  return { people, families };
}

openSide.onclick = async () => {
  const query = encodeURIComponent(searchInput.value || '');
  await chrome.runtime.sendMessage({ type: 'gedfix:open-sidepanel', query });
};

openFull.onclick = async () => {
  await chrome.tabs.create({ url: 'https://gedfix.isn.biz' });
};

importBtn.onclick = () => fileInput.click();
fileInput.onchange = async () => {
  const file = fileInput.files?.[0];
  if (!file) return;
  const text = await file.text();
  const { people, families } = parseGedcomToRows(text);

  db.run('BEGIN');
  try {
    for (const p of people) {
      db.run('INSERT OR IGNORE INTO person (xref, givenName, surname) VALUES (?, ?, ?)', [p.xref, p.givenName, p.surname]);
    }
    for (const f of families) {
      db.run('INSERT OR IGNORE INTO family (xref) VALUES (?)', [f.xref]);
    }
    db.run('COMMIT');
  } catch {
    db.run('ROLLBACK');
  }

  saveDb();
  updateStats();
};

(async () => {
  await initDb();
  updateStats();
  updateRecent();
})();
