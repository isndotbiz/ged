import initSqlJs, { type Database } from 'sql.js';

type ExtractedRecord = {
  source: string;
  url: string;
  name?: string;
  given?: string;
  surname?: string;
  sex?: 'M' | 'F' | 'U';
  birthDate?: string;
  deathDate?: string;
  recordType?: string;
  raw?: Record<string, unknown>;
};

type Tab = 'search' | 'quick-add' | 'import' | 'recent' | 'clipboard';

const app = document.getElementById('app');
if (!app) throw new Error('Missing app root');

let db: Database;
let tab: Tab = 'search';
let records: ExtractedRecord[] = [];
let clipboardItems: string[] = [];

const recentKey = 'gedfix_ext_recent_people';
const queueKey = 'gedfix_ext_import_queue';

async function initDb(): Promise<void> {
  const SQL = await initSqlJs({ locateFile: (f) => `https://sql.js.org/dist/${f}` });
  db = new SQL.Database();
  db.run(`
    CREATE TABLE IF NOT EXISTS person (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      xref TEXT UNIQUE,
      givenName TEXT DEFAULT '',
      surname TEXT DEFAULT '',
      sex TEXT DEFAULT 'U',
      birthDate TEXT DEFAULT '',
      deathDate TEXT DEFAULT ''
    );
    CREATE TABLE IF NOT EXISTS family (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      xref TEXT UNIQUE,
      partner1Xref TEXT DEFAULT '',
      partner2Xref TEXT DEFAULT ''
    );
    CREATE TABLE IF NOT EXISTS proposal (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      entityType TEXT,
      entityId TEXT,
      fieldName TEXT,
      oldValue TEXT,
      newValue TEXT,
      status TEXT DEFAULT 'pending'
    );
  `);
}

function persistQueue() {
  localStorage.setItem(queueKey, JSON.stringify(records));
}

function loadQueue() {
  try {
    records = JSON.parse(localStorage.getItem(queueKey) || '[]');
  } catch {
    records = [];
  }
}

function getRecent(): string[] {
  try {
    return JSON.parse(localStorage.getItem(recentKey) || '[]');
  } catch {
    return [];
  }
}

function addRecent(value: string) {
  const next = [value, ...getRecent().filter((x) => x !== value)].slice(0, 10);
  localStorage.setItem(recentKey, JSON.stringify(next));
}

function runSearch(q: string): Array<{ xref: string; label: string }> {
  if (!q.trim()) return [];
  const stmt = db.prepare(
    `SELECT xref, givenName, surname, birthDate, deathDate
     FROM person
     WHERE lower(givenName || ' ' || surname) LIKE ?
     ORDER BY surname, givenName LIMIT 50`
  );
  stmt.bind([`%${q.toLowerCase()}%`]);
  const rows: Array<{ xref: string; label: string }> = [];
  while (stmt.step()) {
    const row = stmt.getAsObject() as Record<string, string>;
    const label = `${row.givenName || ''} ${row.surname || ''}`.trim();
    rows.push({ xref: row.xref || '', label: `${label} (${row.birthDate || '?'}-${row.deathDate || '?'})` });
  }
  stmt.free();
  return rows;
}

function render() {
  const tabs: Array<[Tab, string]> = [
    ['search', 'Search'],
    ['quick-add', 'Quick Add'],
    ['import', 'Import'],
    ['recent', 'Recent'],
    ['clipboard', 'Clipboard'],
  ];

  app.innerHTML = `
    <div class="tabs">
      ${tabs
        .map(([id, label]) => `<button class="tab ${tab === id ? 'active' : ''}" data-tab="${id}">${label}</button>`)
        .join('')}
    </div>
    <div class="panel" id="panel"></div>
  `;

  app.querySelectorAll<HTMLButtonElement>('[data-tab]').forEach((button) => {
    button.onclick = () => {
      tab = button.dataset.tab as Tab;
      render();
    };
  });

  const panel = app.querySelector<HTMLDivElement>('#panel');
  if (!panel) return;

  if (tab === 'search') renderSearch(panel);
  if (tab === 'quick-add') renderQuickAdd(panel);
  if (tab === 'import') renderImport(panel);
  if (tab === 'recent') renderRecent(panel);
  if (tab === 'clipboard') renderClipboard(panel);
}

function renderSearch(panel: HTMLDivElement) {
  panel.innerHTML = `<input id="search-input" placeholder="Search person" /><div id="search-results" class="muted">Type to search.</div>`;
  const input = panel.querySelector<HTMLInputElement>('#search-input');
  const results = panel.querySelector<HTMLDivElement>('#search-results');
  if (!input || !results) return;
  input.oninput = () => {
    const rows = runSearch(input.value);
    if (!rows.length) {
      results.innerHTML = '<div class="muted">No people found.</div>';
      return;
    }
    results.innerHTML = rows
      .map((row) => `<button class="card" data-xref="${row.xref}">${row.label}</button>`)
      .join('');
    results.querySelectorAll<HTMLButtonElement>('[data-xref]').forEach((button) => {
      button.onclick = () => addRecent(button.textContent || button.dataset.xref || '');
    });
  };
}

function renderQuickAdd(panel: HTMLDivElement) {
  panel.innerHTML = `
    <input id="given" placeholder="Given name" />
    <input id="surname" placeholder="Surname" />
    <div class="row">
      <input id="birth" placeholder="Birth date" />
      <input id="death" placeholder="Death date" />
    </div>
    <select id="sex">
      <option value="U">Unknown</option>
      <option value="M">Male</option>
      <option value="F">Female</option>
    </select>
    <div class="row">
      <select id="father"><option value="">Father (optional)</option></select>
      <select id="mother"><option value="">Mother (optional)</option></select>
    </div>
    <button id="add-person">Add Person</button>
    <div id="quick-add-msg" class="muted"></div>
  `;

  const people = runSearch('a').slice(0, 300);
  ['father', 'mother'].forEach((id) => {
    const select = panel.querySelector<HTMLSelectElement>(`#${id}`);
    if (!select) return;
    select.innerHTML += people.map((p) => `<option value="${p.xref}">${p.label}</option>`).join('');
  });

  const btn = panel.querySelector<HTMLButtonElement>('#add-person');
  const msg = panel.querySelector<HTMLDivElement>('#quick-add-msg');
  if (!btn || !msg) return;

  btn.onclick = () => {
    const given = (panel.querySelector<HTMLInputElement>('#given')?.value || '').trim();
    const surname = (panel.querySelector<HTMLInputElement>('#surname')?.value || '').trim();
    if (!given && !surname) {
      msg.textContent = 'Name is required.';
      return;
    }

    const birthDate = panel.querySelector<HTMLInputElement>('#birth')?.value || '';
    const deathDate = panel.querySelector<HTMLInputElement>('#death')?.value || '';
    const sex = panel.querySelector<HTMLSelectElement>('#sex')?.value || 'U';
    const father = panel.querySelector<HTMLSelectElement>('#father')?.value || '';
    const mother = panel.querySelector<HTMLSelectElement>('#mother')?.value || '';

    const xref = `I${Date.now()}`;
    db.run(
      `INSERT INTO person (xref, givenName, surname, sex, birthDate, deathDate) VALUES (?, ?, ?, ?, ?, ?)`,
      [xref, given, surname, sex, birthDate, deathDate]
    );

    if (father || mother) {
      const famXref = `F${Date.now()}`;
      db.run(`INSERT INTO family (xref, partner1Xref, partner2Xref) VALUES (?, ?, ?)`, [famXref, father, mother]);
      db.run(`INSERT INTO proposal (entityType, entityId, fieldName, oldValue, newValue, status) VALUES ('person', ?, 'familyXref', '', ?, 'pending')`, [xref, famXref]);
    }

    msg.textContent = `Added ${given} ${surname}`.trim();
  };
}

function renderImport(panel: HTMLDivElement) {
  panel.innerHTML = records.length
    ? records
        .map(
          (record, index) => `
            <div class="card">
              <div><strong>${record.name || `${record.given || ''} ${record.surname || ''}`.trim() || 'Unknown record'}</strong></div>
              <div class="muted">${record.source} • ${record.recordType || 'Record'}</div>
              <button data-proposal="${index}">Create Proposal</button>
            </div>
          `
        )
        .join('')
    : '<div class="muted">No extracted records queued.</div>';

  panel.querySelectorAll<HTMLButtonElement>('[data-proposal]').forEach((button) => {
    button.onclick = () => {
      const idx = Number(button.dataset.proposal || '-1');
      const record = records[idx];
      if (!record) return;
      db.run(
        `INSERT INTO proposal (entityType, entityId, fieldName, oldValue, newValue, status)
         VALUES ('person', ?, 'importedRecord', '', ?, 'pending')`,
        [`EXT-${Date.now()}`, JSON.stringify(record)]
      );
      button.textContent = 'Queued';
      button.disabled = true;
    };
  });
}

function renderRecent(panel: HTMLDivElement) {
  const recent = getRecent().slice(0, 10);
  panel.innerHTML = recent.length
    ? recent.map((value) => `<div class="card">${value}</div>`).join('')
    : '<div class="muted">No recently viewed people.</div>';
}

function renderClipboard(panel: HTMLDivElement) {
  panel.innerHTML = clipboardItems.length
    ? clipboardItems.map((value) => `<div class="card">${value}</div>`).join('')
    : '<div class="muted">Clipboard is empty.</div>';
}

chrome.runtime.onMessage.addListener((message) => {
  if (message?.type === 'gedfix:extracted-record' && message.record) {
    records = [message.record as ExtractedRecord, ...records].slice(0, 100);
    persistQueue();
    if (tab === 'import') render();
  }

  if (message?.type === 'gedfix:clipboard' && typeof message.text === 'string') {
    clipboardItems = [message.text, ...clipboardItems].slice(0, 40);
    if (tab === 'clipboard') render();
  }
});

(async () => {
  await initDb();
  loadQueue();
  render();
})();
