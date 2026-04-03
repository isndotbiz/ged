export function cleanText(el) {
  if (!el) return '';
  return (el.textContent || '').replace(/\s+/g, ' ').trim();
}

export function parseNameFromText(text) {
  const cleaned = (text || '').replace(/\s+/g, ' ').trim();
  if (!cleaned) return { given: '', surname: '' };
  const parts = cleaned.split(' ');
  if (parts.length === 1) return { given: parts[0], surname: '' };
  return { given: parts.slice(0, -1).join(' '), surname: parts[parts.length - 1] };
}

export function parseDateFromText(text) {
  const value = (text || '').trim();
  if (!value) return '';

  const monthMap = {
    jan: 'JAN', feb: 'FEB', mar: 'MAR', apr: 'APR', may: 'MAY', jun: 'JUN',
    jul: 'JUL', aug: 'AUG', sep: 'SEP', oct: 'OCT', nov: 'NOV', dec: 'DEC',
  };

  const full = value.match(/(\d{1,2})\s+([A-Za-z]{3,9})\s+(\d{4})/);
  if (full) {
    const month = monthMap[full[2].slice(0, 3).toLowerCase()] || full[2].toUpperCase().slice(0, 3);
    return `${full[1]} ${month} ${full[3]}`;
  }

  const yearOnly = value.match(/(\d{4})/);
  if (yearOnly) return yearOnly[1];

  const mdY = value.match(/(\d{1,2})\/(\d{1,2})\/(\d{4})/);
  if (mdY) {
    const month = Number(mdY[1]);
    const day = Number(mdY[2]);
    const year = mdY[3];
    const months = ['JAN','FEB','MAR','APR','MAY','JUN','JUL','AUG','SEP','OCT','NOV','DEC'];
    return `${day} ${months[Math.max(0, Math.min(11, month - 1))]} ${year}`;
  }

  return value;
}
