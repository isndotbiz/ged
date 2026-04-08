import { cleanText, parseDateFromText, parseNameFromText } from './extractor.js';

function sendRecord(record) {
  chrome.runtime.sendMessage({ type: 'gedfix:extracted-record', record });
}

function ensureBadge(onImport) {
  if (document.getElementById('gedfix-detect-badge')) return;

  const badge = document.createElement('div');
  badge.id = 'gedfix-detect-badge';
  badge.style.cssText = [
    'position:fixed',
    'right:16px',
    'bottom:16px',
    'width:200px',
    'background:#0d1117',
    'color:#f0f4f8',
    'padding:10px',
    'z-index:2147483647',
    'border:1px solid rgba(95,223,223,.35)',
    'border-radius:8px',
    'font:12px/1.3 system-ui,sans-serif',
    'box-shadow:0 12px 24px rgba(0,0,0,.35)',
  ].join(';');

  badge.innerHTML = `
    <div style="display:flex;align-items:center;gap:6px;margin-bottom:8px;">
      <span style="font-weight:700;">GedFix</span>
      <span style="opacity:.75;">Record detected</span>
    </div>
    <button id="gedfix-import-btn" style="width:100%;padding:7px;border-radius:6px;border:0;background:#1e9ff2;color:#fff;cursor:pointer;">Import</button>
  `;

  document.body.appendChild(badge);
  badge.querySelector('#gedfix-import-btn')?.addEventListener('click', onImport);
}

function extractFamilySearchArk() {
  const name = cleanText(document.querySelector('h1'));
  const info = cleanText(document.querySelector('.person-info'));
  const breadcrumb = cleanText(document.querySelector('[aria-label="Breadcrumb"], .breadcrumb'));
  const parsed = parseNameFromText(name);

  return {
    source: 'FamilySearch',
    url: location.href,
    name,
    given: parsed.given,
    surname: parsed.surname,
    birthDate: parseDateFromText(info),
    recordType: breadcrumb || 'ARK Record',
    raw: { info },
  };
}

function extractFamilySearchTree() {
  const profile = document.querySelector('[data-testid="person-profile"], main');
  const name = cleanText(profile?.querySelector('h1') || document.querySelector('h1'));
  const born = cleanText(document.querySelector('[data-testid="birth"], .birth-date'));
  const died = cleanText(document.querySelector('[data-testid="death"], .death-date'));
  const parsed = parseNameFromText(name);
  return {
    source: 'FamilySearch',
    url: location.href,
    name,
    given: parsed.given,
    surname: parsed.surname,
    birthDate: parseDateFromText(born),
    deathDate: parseDateFromText(died),
    recordType: 'Tree Person',
  };
}

function extractFindAGrave() {
  const name = cleanText(document.querySelector('#bio-name'));
  const dateText = cleanText(document.querySelector('.memorial-date'));
  const cemetery = cleanText(document.querySelector('.memorial-cemetery'));
  const parsed = parseNameFromText(name);
  const dates = dateText.split('-').map((part) => parseDateFromText(part));

  return {
    source: 'FindAGrave',
    url: location.href,
    name,
    given: parsed.given,
    surname: parsed.surname,
    birthDate: dates[0] || '',
    deathDate: dates[1] || '',
    recordType: 'Memorial',
    raw: { cemetery },
  };
}

function extractAncestry() {
  const name = cleanText(document.querySelector('.userCardTitle, h1.name'));
  const birthText = cleanText(document.querySelector('[data-testid="birth"] .factItemDate, .birth-date'));
  const deathText = cleanText(document.querySelector('[data-testid="death"] .factItemDate, .death-date'));
  const parsed = parseNameFromText(name);

  return {
    source: 'Ancestry',
    url: location.href,
    name,
    given: parsed.given,
    surname: parsed.surname,
    birthDate: parseDateFromText(birthText),
    deathDate: parseDateFromText(deathText),
    recordType: 'Tree Person',
  };
}

function detectAndExtract() {
  const host = location.hostname;
  let record = null;

  if (host.includes('familysearch.org')) {
    if (location.pathname.startsWith('/ark:')) record = extractFamilySearchArk();
    if (location.pathname.startsWith('/tree/person/')) record = extractFamilySearchTree();
  }

  if (host.includes('findagrave.com') && location.pathname.startsWith('/memorial/')) {
    record = extractFindAGrave();
  }

  if (host.includes('ancestry.com') && location.pathname.includes('/person/')) {
    record = extractAncestry();
  }

  if (!record) return;

  sendRecord(record);
  ensureBadge(() => sendRecord(record));
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', detectAndExtract, { once: true });
} else {
  detectAndExtract();
}

window.addEventListener('copy', () => {
  const text = String(window.getSelection() || '').trim();
  if (!text) return;
  chrome.runtime.sendMessage({ type: 'gedfix:clipboard', text });
});
